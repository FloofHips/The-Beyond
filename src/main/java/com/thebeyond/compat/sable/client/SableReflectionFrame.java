package com.thebeyond.compat.sable.client;

import com.thebeyond.common.registry.BeyondShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Snapshots the main camera so a sub-level mirror's BER, which can't read main-camera matrices, can build its reflected view. */
public final class SableReflectionFrame {
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static float partialTick;
    private static int screenW;
    private static int screenH;
    private static boolean valid;

    private SableReflectionFrame() {
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        // Skip Iris' shadow pass: it fires this stage with the light's view, not the main camera.
        if (com.thebeyond.client.compat.ShaderCompatLib.isShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        screenW = mc.getMainRenderTarget().width;
        screenH = mc.getMainRenderTarget().height;
        PROJ.set(event.getProjectionMatrix());
        VIEW.set(event.getModelViewMatrix());
        camPos = event.getCamera().getPosition();
        partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        valid = screenW > 0 && screenH > 0;

        ShaderInstance mirror = BeyondShaders.getMirror();
        if (mirror != null) {
            mirror.safeGetUniform("ScreenSize").set((float) screenW, (float) screenH);
        }

        if (valid) {
            MirrorSableRenderer.captureAll(PROJ, VIEW, camPos, partialTick, event.getFrustum());
        }
    }

    public static boolean valid() {
        return valid;
    }

    public static Matrix4f proj() {
        return PROJ;
    }

    public static Matrix4f view() {
        return VIEW;
    }

    public static Vec3 camPos() {
        return camPos;
    }

    public static float partialTick() {
        return partialTick;
    }

    public static int screenW() {
        return screenW;
    }

    public static int screenH() {
        return screenH;
    }
}
