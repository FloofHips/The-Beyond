package com.thebeyond.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.mixin.client.CameraAccessor;
import com.thebeyond.mixin.client.MinecraftMainTargetAccessor;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Renders a POV offscreen (a camera block's facing, or the player's own first person), reads the FBO back, and uploads it.
 *
 * <p>Sodium/Iris composite into {@code getMainRenderTarget()} and ignore a merely-bound target, so mainRenderTarget
 * must <em>point</em> at the capture FBO. Sodium's visible-chunk graph builds over several frames, so a capture spans
 * up to MAX_WARMUP_FRAMES.
 */
public final class BlockCameraCapture {

    private record Pending(long requestId, Vec3 eye, Vec3 forward, boolean selfPov) {
    }

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static final int MAX_QUEUE = 32;
    private static boolean capturing; // also read by mirror capture to bail

    private static Pending current;
    private static int currentFrames;
    private static final int MAX_WARMUP_FRAMES = 12; // read back by here even if the view never reports "complete"

    private static TextureTarget target;

    public static volatile SecondaryViewRenderHook secondaryViewHook;

    @FunctionalInterface
    public interface SecondaryViewRenderHook {
        boolean render(net.minecraft.client.DeltaTracker delta);
    }

    private BlockCameraCapture() {
    }

    /** Client thread only. Renders the given world POV (a camera block's facing). */
    public static void request(long requestId, Vec3 eye, Vec3 forward) {
        if (QUEUE.size() >= MAX_QUEUE) {
            return;
        }
        QUEUE.addLast(new Pending(requestId, eye, forward, false));
    }

    /** Client thread only. Renders the local player's own first-person POV, ignoring the live F5 camera type. */
    public static void requestSelf(long requestId) {
        if (QUEUE.size() >= MAX_QUEUE) {
            return;
        }
        QUEUE.addLast(new Pending(requestId, null, null, true));
    }

    /** Other render code must bail while true, to avoid nesting renderLevel. */
    public static boolean isCapturing() {
        return capturing;
    }

    /** Call from RenderFrameEvent.Pre, outside {@code renderLevel}. One capture per frame. */
    public static void runQueued() {
        if (capturing) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            current = null;
            return; // renderLevel dereferences mc.player directly
        }
        if (ShaderCompatLib.isShadowPass()) {
            return;
        }
        if (current == null) {
            current = QUEUE.pollFirst();
            if (current == null) {
                return;
            }
            currentFrames = 0;
        }
        boolean finished = capture(current, currentFrames);
        currentFrames++;
        if (finished) {
            current = null;
        }
    }

    private static boolean capture(Pending req, int frameIndex) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gr = mc.gameRenderer;
        int fw = mc.getWindow().getWidth();
        int fh = mc.getWindow().getHeight();
        if (fw <= 0 || fh <= 0) {
            return true;
        }
        try {
            if (target == null) {
                target = new TextureTarget(fw, fh, true /* useDepth */, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_NEAREST);
            } else if (target.width != fw || target.height != fh) {
                target.resize(fw, fh, Minecraft.ON_OSX);
                target.setFilterMode(GL11.GL_NEAREST);
            }
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[camera] block-camera FBO alloc failed", t);
            return true;
        }

        Entity savedCamEntity = mc.cameraEntity; // field, not setter: setter has post-effect side effects
        CameraType savedCamType = mc.options.getCameraType();
        // renderBlockOutline/renderHand have no getters; live they're always their defaults (true)
        CameraAccessor camAcc = (CameraAccessor) gr.getMainCamera();
        float savedEyeHeight = camAcc.the_beyond$getEyeHeight();
        float savedEyeHeightOld = camAcc.the_beyond$getEyeHeightOld();

        RenderTarget realMain = mc.getMainRenderTarget();
        Marker throwaway = null;
        capturing = true;
        try {
            // Both paths force first person and hide the hand/outline so neither leaks into the photo.
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            gr.setRenderBlockOutline(false);
            gr.setRenderHand(false);
            if (!req.selfPov) {
                // Block camera: spoof a throwaway entity at the block POV, eye height zeroed so setup() lands on `eye`.
                double len = req.forward.length();
                Vec3 f = len < 1.0e-6 ? new Vec3(0, 0, 1) : req.forward.scale(1.0 / len);
                float yaw = (float) (Mth.atan2(-f.x, f.z) * (180.0 / Math.PI));
                float pitch = (float) (Math.asin(Mth.clamp(-f.y, -1.0, 1.0)) * (180.0 / Math.PI));
                // moveTo copies current->previous so Camera.setup's lerp is exact for any partialTick
                throwaway = EntityType.MARKER.create(mc.level);
                if (throwaway == null) {
                    return true;
                }
                throwaway.setNoGravity(true);
                throwaway.moveTo(req.eye.x, req.eye.y, req.eye.z, yaw, pitch);
                mc.cameraEntity = throwaway;
                camAcc.the_beyond$setEyeHeight(0.0f);
                camAcc.the_beyond$setEyeHeightOld(0.0f);
            }
            // Handheld (selfPov): keep the player as camera — first person hides the model and uses their eyes/look.

            ((MinecraftMainTargetAccessor) mc).the_beyond$setMainRenderTarget(target);
            target.setClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            DeltaTracker delta = mc.getTimer();
            SecondaryViewRenderHook hook = secondaryViewHook;
            boolean complete;
            if (hook != null) {
                complete = hook.render(delta);
            } else {
                mc.gameRenderer.renderLevel(delta);
                complete = true;
            }
            boolean finished = complete || (frameIndex + 1) >= MAX_WARMUP_FRAMES;
            if (finished) {
                SnapshotCapture.downsampleQuantizeUpload(target, req.requestId);
            }
            return finished;
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[camera] block-camera capture failed", t);
            return true;
        } finally {
            // restore in reverse order; a miss here corrupts the player's live frame
            ((MinecraftMainTargetAccessor) mc).the_beyond$setMainRenderTarget(realMain);
            camAcc.the_beyond$setEyeHeight(savedEyeHeight);
            camAcc.the_beyond$setEyeHeightOld(savedEyeHeightOld);
            gr.setRenderHand(true);
            gr.setRenderBlockOutline(true);
            mc.cameraEntity = savedCamEntity;
            mc.options.setCameraType(savedCamType);
            RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
            realMain.bindWrite(true);
            if (throwaway != null) {
                throwaway.discard();
            }
            capturing = false;
        }
    }

    /** GL-thread only. */
    public static void release() {
        QUEUE.clear();
        current = null;
        if (target != null) {
            RenderTarget t = target;
            target = null;
            t.destroyBuffers();
        }
    }
}
