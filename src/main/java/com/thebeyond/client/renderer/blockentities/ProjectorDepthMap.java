package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.common.registry.BeyondShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProjectorDepthMap {
    static final int BASE_RES = 1024;
    private static final int MAX_SLOTS = 8;
    private static final float SUB_SCALE_EPS = 0.02f; // max scale anisotropy before a contraption falls to the mesh

    private static Slot[] slots;
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final java.util.Set<ProjectorBlockEntity> CAPTURED = new java.util.HashSet<>(); // render-thread only
    private static boolean loggedActive;
    private static final Map<BlockPos, String> DIAG_LAST = new HashMap<>();
    // Never route through shared mc.renderBuffers(): an endBatch() inside the capture corrupts Iris's whole-frame entity
    // batcher (cape/held-item break, stay broken after the pack toggles off). Private immediate source isolates it.
    private static final MultiBufferSource.BufferSource CAPTURE_BUFFER =
            MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    // Entity passes deferred to AFTER_LEVEL: dispatching entities mid-pipeline collides with Iris's entity hooks and
    // persistently corrupts cape/held-item. The slot FBO + its depth survive to the collision-free post-final window.
    private record PendingEntities(TextureTarget target, Matrix4f proj, Matrix4f view,
                                   ProjectorRenderer.Pinhole gather, Vec3 camPos, BlockPos pos) {
    }

    private static final List<PendingEntities> PENDING_ENTITIES = new ArrayList<>();
    private static int skippedGlass;
    private static boolean blocksCapped;

    private ProjectorDepthMap() {
    }

    private static final class Slot {
        TextureTarget target;
        TextureTarget targetFar;    // peel layer: nearest block surface strictly beyond the first
    }

    public record Active(ProjectorBlockEntity be, Matrix4f vp, Vector3f eyeRel, float coneK, int depthColorTexId,
                         int depthFarTexId) {
    }

    public static List<Active> activeThisFrame() {
        return ACTIVE;
    }

    static boolean wasCaptured(ProjectorBlockEntity be) {
        return CAPTURED.contains(be);
    }

    public static void capture(Camera camera, Frustum frustum, float partialTick) {
        ACTIVE.clear();
        CAPTURED.clear();
        PENDING_ENTITIES.clear();
        if (ShaderCompatLib.isShadowPass()) {
            return; // Iris shadow pass: orthographic, no per-pixel projection
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ShaderInstance dist = BeyondShaders.getProjectorDist();
        if (dist == null) {
            return;
        }

        Vec3 camPos = camera.getPosition();
        // HOST projectors are world-frustum-culled here; CONTRAPTION cones sit at plot coords ~2e7 so cannot be, and are
        // resolved (or dropped to the BER mesh) in captureOneSub. Both share the MAX_SLOTS budget.
        List<ProjectorBlockEntity> litHost = new ArrayList<>();
        List<ProjectorBlockEntity> litSub = new ArrayList<>();
        for (ProjectorBlockEntity be : ProjectorBlockEntity.LOADED) {
            if (be.isRemoved() || be.getLevel() != mc.level) {
                continue;
            }
            if (!ProjectorRenderer.isLit(be) || be.filledSlots().length == 0) {
                continue;
            }
            if (ProjectorRenderer.isOnContraption(be)) {
                litSub.add(be);
            } else if (frustum.isVisible(ProjectorRenderer.coneAABB(ProjectorRenderer.pinhole(be)))) {
                litHost.add(be);
            }
        }
        if (litHost.isEmpty() && litSub.isEmpty()) {
            return;
        }
        litHost.sort(Comparator.comparingDouble(be -> Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(camPos)));

        if (slots == null) {
            slots = new Slot[MAX_SLOTS];
            for (int i = 0; i < MAX_SLOTS; i++) {
                slots[i] = new Slot();
            }
        }
        if (!loggedActive) {
            loggedActive = true;
            TheBeyond.LOGGER.info("[TheBeyond] projector per-pixel deferred path active ({}, rev r34)",
                    ShaderCompatLib.isShaderPackActive() ? "shaderpack: decal post-final" : "no shaderpack");
        }

        dist.safeGetUniform("MaxThrow").set((float) ProjectorRenderer.MAX_THROW);
        ShaderInstance distEntity = BeyondShaders.getProjectorDistEntity();
        if (distEntity != null) {
            distEntity.safeGetUniform("MaxThrow").set((float) ProjectorRenderer.MAX_THROW);
        }
        ShaderInstance distPeel = BeyondShaders.getProjectorDistPeel();
        if (distPeel != null) {
            distPeel.safeGetUniform("MaxThrow").set((float) ProjectorRenderer.MAX_THROW);
        }

        RenderTarget main = mc.getMainRenderTarget();
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        try {
            int slotIdx = 0;
            for (ProjectorBlockEntity be : litHost) {
                if (slotIdx >= MAX_SLOTS) {
                    break;
                }
                try {
                    captureOne(slots[slotIdx++], be, camPos, mc, partialTick);
                } catch (Throwable t) {
                    TheBeyond.LOGGER.error("Projector depth capture failed for one projector", t);
                }
            }
            for (ProjectorBlockEntity be : litSub) {
                if (slotIdx >= MAX_SLOTS) {
                    break;
                }
                try {
                    captureOneSub(slots[slotIdx++], be, camPos, mc, partialTick);
                } catch (Throwable t) {
                    TheBeyond.LOGGER.error("Projector contraption depth capture failed for one projector", t);
                }
            }
        } finally {
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            main.bindWrite(true);
        }
    }

    private static void captureOne(Slot slot, ProjectorBlockEntity be, Vec3 camPos, Minecraft mc, float partialTick) {
        skippedGlass = 0;
        blocksCapped = false;
        ProjectorRenderer.Pinhole ph = ProjectorRenderer.pinhole(be);
        Vec3 eye = ph.eye(), forward = ph.forward(), up = ph.up();
        Vector3f eyeRel = new Vector3f((float) (eye.x - camPos.x), (float) (eye.y - camPos.y), (float) (eye.z - camPos.z));

        // Camera-relative VIEW: lookAt from eyeRel, never translate(-camPos).
        float fovy = (float) (2.0 * Math.atan(ph.coneK()));
        Matrix4f proj = new Matrix4f().setPerspective(
                fovy, 1.0f, (float) ProjectorRenderer.NEAR_EPS, (float) ProjectorRenderer.MAX_THROW);
        Matrix4f view = new Matrix4f().lookAt(
                eyeRel.x, eyeRel.y, eyeRel.z,
                eyeRel.x + (float) forward.x, eyeRel.y + (float) forward.y, eyeRel.z + (float) forward.z,
                (float) up.x, (float) up.y, (float) up.z);
        Matrix4f vp = new Matrix4f(proj).mul(view);

        ensureSized(slot);
        slot.target.setClearColor(1.0039f, 0.0f, 1.0039f, 1.0f); // R,B>1.0 = max dist (untouched); G=0 = no entity
        slot.target.clear(Minecraft.ON_OSX);
        slot.target.bindWrite(true);

        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.set(view);
        RenderSystem.applyModelViewMatrix();

        MultiBufferSource.BufferSource buf = CAPTURE_BUFFER;
        VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK);
        int hostBlocks = emitConeBlocks(vc, mc.level, mc.level::getBlockState, ph, camPos);
        buf.endBatch();

        PENDING_ENTITIES.add(new PendingEntities(slot.target, new Matrix4f(proj), new Matrix4f(view), ph, camPos, be.getBlockPos()));

        CrossStats xf = captureCrossContraptions(buf, ph, view, camPos, partialTick, mc, null,
                BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK, false);

        // Peel into FAR (Sampler1, separate target -> no feedback): block surfaces strictly beyond the first layer only.
        slot.targetFar.setClearColor(1.0039f, 0.0f, 1.0039f, 1.0f);
        slot.targetFar.clear(Minecraft.ON_OSX);
        slot.targetFar.bindWrite(true);
        RenderSystem.setShaderTexture(1, slot.target.getColorTextureId());
        mvStack.set(view);
        RenderSystem.applyModelViewMatrix();
        emitConeBlocks(buf.getBuffer(BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK_PEEL), mc.level, mc.level::getBlockState, ph, camPos, true);
        buf.endBatch();
        captureCrossContraptions(buf, ph, view, camPos, partialTick, mc, null,
                BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK_PEEL, true);
        RenderSystem.setShaderTexture(1, 0);
        slot.targetFar.unbindWrite();

        slot.target.unbindWrite();
        ACTIVE.add(new Active(be, vp, eyeRel, (float) ph.coneK(), slot.target.getColorTextureId(),
                slot.targetFar.getColorTextureId()));
        CAPTURED.add(be);
        diag(be.getBlockPos(), "host capture: blocks=" + hostBlocks + " entities=post"
                + " xframes=" + xf.frames() + " xblocks=" + xf.blocks()
                + (skippedGlass > 0 ? " glass=" + skippedGlass : "") + (blocksCapped ? " CAPPED" : ""));
    }

    /** AFTER_LEVEL: deferred entity depth passes, outside Iris's entity hooks, before the decal samples the maps. */
    public static void captureEntitiesPostFinal(float partialTick) {
        if (PENDING_ENTITIES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING_ENTITIES.clear();
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        try {
            for (PendingEntities p : PENDING_ENTITIES) {
                try {
                    p.target().bindWrite(true); // NO clear: solid-stage blocks + depth stay; LEQUAL keeps nearest entity
                    RenderSystem.setProjectionMatrix(p.proj(), VertexSorting.DISTANCE_TO_ORIGIN);
                    mvStack.set(p.view());
                    RenderSystem.applyModelViewMatrix();
                    List<Entity> coneEntities = gatherConeEntities(mc.level, p.gather(), p.camPos());
                    if (coneEntities.size() >= 16) {
                        diag(p.pos(), "entity capture CAPPED at 16 (farther entities cast no shadow)");
                    }
                    renderConeEntities(CAPTURE_BUFFER, coneEntities, p.camPos(), partialTick, mc);
                    p.target().unbindWrite();
                } catch (Throwable t) {
                    TheBeyond.LOGGER.error("Projector deferred entity capture failed", t);
                }
            }
        } finally {
            PENDING_ENTITIES.clear();
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            main.bindWrite(true);
        }
    }

    private record CrossStats(int frames, int blocks) {
    }

    /**
     * Each contraption crossing the WORLD cone is drawn in its own grid frame (cone lifted via {@code minv}, blocks
     * emitted {@code gridCoord-rp}, mapped by {@code view*m}) so LEQUAL keeps the nearest surface across all frames.
     * {@code ownM} skips the projector's own craft (already drawn by the grid pass).
     */
    private static CrossStats captureCrossContraptions(MultiBufferSource.BufferSource buf, ProjectorRenderer.Pinhole phWorld,
                                                       Matrix4f view, Vec3 camPos, float partialTick, Minecraft mc, Matrix4f ownM,
                                                       RenderType type, boolean frontOnly) {
        ProjectorRenderer.ProjectorIntersectingFramesFn ifn = ProjectorRenderer.intersectingFrames;
        if (ifn == null) {
            return new CrossStats(0, 0);
        }
        List<ProjectorRenderer.ContraptionFrame> frames =
                ifn.resolve(mc.level, ProjectorRenderer.coneAABB(phWorld), camPos, partialTick);
        if (frames.isEmpty()) {
            return new CrossStats(0, 0);
        }
        BlockReader reader = gridReader(mc, new HashMap<>());
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        Vec3 eye = phWorld.eye(), forward = phWorld.forward(), up = phWorld.up(), right = phWorld.right();
        int emitted = 0;
        int used = 0;
        for (ProjectorRenderer.ContraptionFrame fr : frames) {
            if (ownM != null && fr.m().equals(ownM, 1.0e-4f)) {
                continue; // own craft: already drawn by the grid pass
            }
            Matrix4f minv = fr.minv();
            Vector3f egv = minv.transformPosition(new Vector3f(
                    (float) (eye.x - camPos.x), (float) (eye.y - camPos.y), (float) (eye.z - camPos.z)));
            Vector3f fgv = minv.transformDirection(new Vector3f((float) forward.x, (float) forward.y, (float) forward.z)).normalize();
            Vector3f ugv = minv.transformDirection(new Vector3f((float) up.x, (float) up.y, (float) up.z)).normalize();
            Vector3f rgv = minv.transformDirection(new Vector3f((float) right.x, (float) right.y, (float) right.z)).normalize();
            if (!finite(egv) || !finite(fgv) || !finite(ugv) || !finite(rgv)) {
                continue;
            }
            Vec3 eyeGrid = new Vec3(egv.x + fr.rpx(), egv.y + fr.rpy(), egv.z + fr.rpz());
            ProjectorRenderer.Pinhole phInGrid = new ProjectorRenderer.Pinhole(
                    eyeGrid, new Vec3(fgv.x, fgv.y, fgv.z), new Vec3(rgv.x, rgv.y, rgv.z), new Vec3(ugv.x, ugv.y, ugv.z), phWorld.coneK());
            mvStack.set(new Matrix4f(view).mul(fr.m()));
            RenderSystem.applyModelViewMatrix();
            emitted += emitConeBlocks(buf.getBuffer(type), mc.level, reader,
                    phInGrid, new Vec3(fr.rpx(), fr.rpy(), fr.rpz()), frontOnly);
            buf.endBatch();
            used++;
        }
        return new CrossStats(used, emitted);
    }

    /**
     * Per-pixel capture for a CONTRAPTION projector: the cone lives in the sub-level grid frame, so the pinhole is lifted
     * into camera-relative VISIBLE space and the map rendered in multiple frames into one FBO (own grid blocks, host
     * blocks, host entities, crossed crafts). Falls back to the BER mesh (no publish) on a missing/anisotropic frame.
     */
    private static void captureOneSub(Slot slot, ProjectorBlockEntity be, Vec3 camPos, Minecraft mc, float partialTick) {
        skippedGlass = 0;
        blocksCapped = false;
        ProjectorRenderer.ProjectorContraptionFrameFn fn = ProjectorRenderer.contraptionFrame;
        if (fn == null) {
            diag(be.getBlockPos(), "sub capture BAIL: no contraption-frame hook -> BER mesh");
            return; // Sable absent
        }
        ProjectorRenderer.ContraptionFrame fr = fn.resolve(be.getLevel(), be.getBlockPos(), camPos, partialTick);
        if (fr == null) {
            diag(be.getBlockPos(), "sub capture BAIL: frame unresolved -> BER mesh");
            return;
        }
        Matrix4f m = fr.m();
        // Near-uniform scale gate: a single scalar coneK can't describe an anisotropically-scaled cone (acne/leak).
        float sx = colLen(m, 0), sy = colLen(m, 1), sz = colLen(m, 2);
        float smin = Math.min(sx, Math.min(sy, sz)), smax = Math.max(sx, Math.max(sy, sz));
        if (smax < 1.0e-4f || (smax - smin) > SUB_SCALE_EPS * smax) {
            diag(be.getBlockPos(), String.format(java.util.Locale.ROOT,
                    "sub capture BAIL: degenerate/anisotropic scale (min=%.3f max=%.3f) -> BER mesh", smin, smax));
            return;
        }

        ProjectorRenderer.Pinhole phGrid = ProjectorRenderer.pinhole(be); // grid frame (plot coords ~2e7)
        Vec3 eGrid = phGrid.eye(), fGrid = phGrid.forward(), uGrid = phGrid.up(), rGrid = phGrid.right();
        double coneK = phGrid.coneK();

        // Lift the grid pinhole into camera-relative VISIBLE space (m bakes in -camPos).
        Vector3f eyeVisRel = m.transformPosition(new Vector3f(
                (float) (eGrid.x - fr.rpx()), (float) (eGrid.y - fr.rpy()), (float) (eGrid.z - fr.rpz())));
        Vector3f fwdVis = m.transformDirection(new Vector3f((float) fGrid.x, (float) fGrid.y, (float) fGrid.z)).normalize();
        Vector3f upVis = m.transformDirection(new Vector3f((float) uGrid.x, (float) uGrid.y, (float) uGrid.z)).normalize();
        Vector3f rgtVis = m.transformDirection(new Vector3f((float) rGrid.x, (float) rGrid.y, (float) rGrid.z)).normalize();
        if (!finite(eyeVisRel) || !finite(fwdVis) || !finite(upVis) || !finite(rgtVis)) {
            diag(be.getBlockPos(), "sub capture BAIL: non-finite visible basis -> BER mesh");
            return;
        }

        float fovy = (float) (2.0 * Math.atan(coneK));
        Matrix4f proj = new Matrix4f().setPerspective(
                fovy, 1.0f, (float) ProjectorRenderer.NEAR_EPS, (float) ProjectorRenderer.MAX_THROW);
        Matrix4f view = new Matrix4f().lookAt(
                eyeVisRel.x, eyeVisRel.y, eyeVisRel.z,
                eyeVisRel.x + fwdVis.x, eyeVisRel.y + fwdVis.y, eyeVisRel.z + fwdVis.z,
                upVis.x, upVis.y, upVis.z);
        Matrix4f vp = new Matrix4f(proj).mul(view);
        Matrix4f modelViewB = new Matrix4f(view).mul(m); // (gridCoord - rp) -> projector-eye space

        Vec3 eyeVisAbs = new Vec3(camPos.x + eyeVisRel.x, camPos.y + eyeVisRel.y, camPos.z + eyeVisRel.z);
        ProjectorRenderer.Pinhole phVis = new ProjectorRenderer.Pinhole(
                eyeVisAbs, new Vec3(fwdVis.x, fwdVis.y, fwdVis.z),
                new Vec3(rgtVis.x, rgtVis.y, rgtVis.z), new Vec3(upVis.x, upVis.y, upVis.z), coneK);
        Vec3 rpOrigin = new Vec3(fr.rpx(), fr.rpy(), fr.rpz());

        ensureSized(slot);
        slot.target.setClearColor(1.0039f, 0.0f, 1.0039f, 1.0f);
        slot.target.clear(Minecraft.ON_OSX);
        slot.target.bindWrite(true);

        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        MultiBufferSource.BufferSource buf = CAPTURE_BUFFER;

        // Own grid blocks then host world blocks into one FBO; LEQUAL keeps the nearer frame per texel.
        mvStack.set(modelViewB);
        RenderSystem.applyModelViewMatrix();
        int ownBlocks = emitConeBlocks(buf.getBuffer(BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK), mc.level, gridReader(mc, new HashMap<>()), phGrid, rpOrigin);
        buf.endBatch();

        mvStack.set(view);
        RenderSystem.applyModelViewMatrix();
        int hostBlocks = emitConeBlocks(buf.getBuffer(BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK), mc.level, mc.level::getBlockState, phVis, camPos);
        buf.endBatch();

        PENDING_ENTITIES.add(new PendingEntities(slot.target, new Matrix4f(proj), new Matrix4f(view), phVis, camPos, be.getBlockPos()));

        CrossStats xf = captureCrossContraptions(buf, phVis, view, camPos, partialTick, mc, m,
                BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK, false);

        // Peel the same block passes against FAR in every frame this projector renders.
        slot.targetFar.setClearColor(1.0039f, 0.0f, 1.0039f, 1.0f);
        slot.targetFar.clear(Minecraft.ON_OSX);
        slot.targetFar.bindWrite(true);
        RenderSystem.setShaderTexture(1, slot.target.getColorTextureId());
        mvStack.set(modelViewB);
        RenderSystem.applyModelViewMatrix();
        emitConeBlocks(buf.getBuffer(BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK_PEEL), mc.level, gridReader(mc, new HashMap<>()), phGrid, rpOrigin, true);
        buf.endBatch();
        mvStack.set(view);
        RenderSystem.applyModelViewMatrix();
        emitConeBlocks(buf.getBuffer(BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK_PEEL), mc.level, mc.level::getBlockState, phVis, camPos, true);
        buf.endBatch();
        captureCrossContraptions(buf, phVis, view, camPos, partialTick, mc, m,
                BeyondRenderTypes.PROJECTOR_DEPTH_BLOCK_PEEL, true);
        RenderSystem.setShaderTexture(1, 0);
        slot.targetFar.unbindWrite();

        slot.target.unbindWrite();
        ACTIVE.add(new Active(be, vp, eyeVisRel, (float) coneK, slot.target.getColorTextureId(),
                slot.targetFar.getColorTextureId()));
        CAPTURED.add(be);
        diag(be.getBlockPos(), "sub capture: own=" + ownBlocks + " host=" + hostBlocks + " entities=post"
                + " xframes=" + xf.frames() + " xblocks=" + xf.blocks()
                + (skippedGlass > 0 ? " glass=" + skippedGlass : "") + (blocksCapped ? " CAPPED" : ""));
    }

    private static float colLen(Matrix4f m, int c) {
        float x, y, z;
        switch (c) {
            case 0 -> { x = m.m00(); y = m.m01(); z = m.m02(); }
            case 1 -> { x = m.m10(); y = m.m11(); z = m.m12(); }
            default -> { x = m.m20(); y = m.m21(); z = m.m22(); }
        }
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean finite(Vector3f v) {
        return Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z);
    }

    /** In-cone entities into the bound FBO via {@link ProjectorDistEntitySource}. PROJ/VIEW + FBO must be set already. */
    private static void renderConeEntities(MultiBufferSource.BufferSource buf, List<Entity> coneEntities,
                                           Vec3 camPos, float partialTick, Minecraft mc) {
        if (BeyondShaders.getProjectorDistEntity() == null || coneEntities.isEmpty()) {
            return;
        }
        EntityRenderDispatcher disp = mc.getEntityRenderDispatcher();
        disp.setRenderShadow(false);
        try {
            for (Entity e : coneEntities) {
                ResourceLocation tex = disp.getRenderer(e).getTextureLocation(e);
                if (tex == null) {
                    continue; // no single atlas -> can't pick a dist type
                }
                double ex = Mth.lerp(partialTick, e.xOld, e.getX());
                double ey = Mth.lerp(partialTick, e.yOld, e.getY());
                double ez = Mth.lerp(partialTick, e.zOld, e.getZ());
                float eyaw = Mth.rotLerp(partialTick, e.yRotO, e.getYRot());
                ProjectorDistEntitySource forceDist = new ProjectorDistEntitySource(buf, tex);
                try {
                    disp.render(e, ex - camPos.x, ey - camPos.y, ez - camPos.z, eyaw, partialTick,
                            new PoseStack(), forceDist, LightTexture.FULL_BRIGHT);
                } catch (Throwable t) {
                    TheBeyond.LOGGER.error("Projector entity-depth render failed for {}", e, t);
                }
            }
            buf.endBatch();
        } finally {
            disp.setRenderShadow(true); // no getter; vanilla default is true
        }
    }

    /** In-cone alive entities that can occlude / catch the beam (cap 16). */
    private static List<Entity> gatherConeEntities(Level level, ProjectorRenderer.Pinhole ph, Vec3 camPos) {
        AABB bounds = ProjectorRenderer.coneAABB(ph);
        ProjectorRenderer.Plane[] planes = ProjectorRenderer.buildFrustumPlanes(
                ph.eye(), ph.forward(), ph.right(), ph.up(), ph.coneK(), ProjectorRenderer.MAX_THROW);
        List<Entity> out = new ArrayList<>();
        final int CAP = 16;
        // Local player included even in 1st person: the body still occludes the beam and casts a shadow behind it.
        for (Entity e : level.getEntities((Entity) null, bounds, Entity::isAlive)) {
            if (ProjectorRenderer.aabbOutside(e.getBoundingBox(), planes)) {
                continue; // precise cone reject (the AABB query is loose)
            }
            out.add(e);
            if (out.size() >= CAP) {
                break;
            }
        }
        return out;
    }

    /** Routes NEW_ENTITY (body) geometry through our entity-dist type; sinks every other part (leash, glint, lines). */
    private static final class ProjectorDistEntitySource implements MultiBufferSource {
        private final MultiBufferSource.BufferSource inner;
        private final ResourceLocation tex;

        ProjectorDistEntitySource(MultiBufferSource.BufferSource inner, ResourceLocation tex) {
            this.inner = inner;
            this.tex = tex;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return type.format() == DefaultVertexFormat.NEW_ENTITY
                    ? inner.getBuffer(BeyondRenderTypes.projectorDepthEntity(typeTexture(type, tex)))
                    : NoOpConsumer.INSTANCE;
        }
    }

    // Each layer's OWN cutout texture: alpha-testing cape/elytra UVs against the body atlas punches holes in the silhouette.
    private static final Map<RenderType, java.util.Optional<ResourceLocation>> TYPE_TEXTURES = new HashMap<>();
    private static java.lang.reflect.Field rtState;          // CompositeRenderType.state
    private static java.lang.reflect.Field rtTextureState;   // CompositeState.textureState
    private static java.lang.reflect.Method rtCutoutTexture; // EmptyTextureStateShard.cutoutTexture()
    private static boolean rtReflectionFailed;

    private static ResourceLocation typeTexture(RenderType type, ResourceLocation fallback) {
        if (rtReflectionFailed) {
            return fallback;
        }
        java.util.Optional<ResourceLocation> cached = TYPE_TEXTURES.get(type);
        if (cached != null) {
            return cached.orElse(fallback);
        }
        try {
            if (rtCutoutTexture == null) {
                Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                rtState = composite.getDeclaredField("state");
                rtState.setAccessible(true);
                Class<?> stateCls = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState");
                rtTextureState = stateCls.getDeclaredField("textureState");
                rtTextureState.setAccessible(true);
                Class<?> shardCls = Class.forName("net.minecraft.client.renderer.RenderStateShard$EmptyTextureStateShard");
                rtCutoutTexture = shardCls.getDeclaredMethod("cutoutTexture");
                rtCutoutTexture.setAccessible(true);
            }
            java.util.Optional<ResourceLocation> resolved = java.util.Optional.empty();
            if (rtState.getDeclaringClass().isInstance(type)
                    && rtCutoutTexture.invoke(rtTextureState.get(rtState.get(type))) instanceof java.util.Optional<?> o
                    && o.isPresent() && o.get() instanceof ResourceLocation rl) {
                resolved = java.util.Optional.of(rl);
            }
            TYPE_TEXTURES.put(type, resolved);
            return resolved.orElse(fallback);
        } catch (Throwable t) {
            rtReflectionFailed = true; // latch off: never retry-spam
            TheBeyond.LOGGER.warn("[Projector DIAG] render-type texture reflection failed — entity layers (cape/elytra) "
                    + "will alpha-test against the body atlas (garbled shadow silhouettes possible)", t);
            return fallback;
        }
    }

    private enum NoOpConsumer implements VertexConsumer {
        INSTANCE;

        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private static void ensureSized(Slot slot) {
        if (slot.target == null) {
            slot.target = new TextureTarget(BASE_RES, BASE_RES, true, Minecraft.ON_OSX);
            // Re-spec RGBA8 -> RGBA16F (R=radial dist, G=entity bit, B=blocks-only dist) so the decal can hardware-LINEAR
            // it: a hi/lo-packed value can't be filtered, and that bilinear blend removes the moving-silhouette snap.
            // Re-speccing the SAME texture id keeps the completed attachment valid (fixed res, no resize).
            GlStateManager._bindTexture(slot.target.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, BASE_RES, BASE_RES, 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, (IntBuffer) null);
            int ifmt = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            GlStateManager._bindTexture(0);
            slot.target.setFilterMode(GL11.GL_LINEAR);
            slot.target.bindWrite(false);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            slot.target.unbindWrite();
            TheBeyond.LOGGER.info("[Projector DIAG] depth-map FBO status=0x{} internalFormat=0x{} (expected status=0x8CD5, format=0x881A RGBA16F)",
                    Integer.toHexString(status).toUpperCase(), Integer.toHexString(ifmt).toUpperCase());
        }
        if (slot.targetFar == null) {
            slot.targetFar = new TextureTarget(BASE_RES, BASE_RES, true, Minecraft.ON_OSX);
            GlStateManager._bindTexture(slot.targetFar.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, BASE_RES, BASE_RES, 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, (IntBuffer) null);
            int ifmtFar = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            GlStateManager._bindTexture(0);
            slot.targetFar.setFilterMode(GL11.GL_NEAREST); // decal reads the peel via texelFetch
            slot.targetFar.bindWrite(false);
            int statusFar = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            slot.targetFar.unbindWrite();
            TheBeyond.LOGGER.info("[Projector DIAG] depth-map FAR FBO status=0x{} internalFormat=0x{} (expected status=0x8CD5, format=0x881A RGBA16F)",
                    Integer.toHexString(statusFar).toUpperCase(), Integer.toHexString(ifmtFar).toUpperCase());
        }
    }

    @FunctionalInterface
    private interface BlockReader {
        BlockState at(BlockPos pos);
    }

    /** Grid-frame reader: Sable's ClientChunkCache mixin returns the sub-level chunk for plot coords; cached per chunk. */
    private static BlockReader gridReader(Minecraft mc, Map<Long, LevelChunk> cache) {
        return p -> {
            long ck = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            LevelChunk c = cache.get(ck);
            if (c == null) {
                c = mc.level.getChunk(p.getX() >> 4, p.getZ() >> 4);
                cache.put(ck, c);
            }
            return c.getBlockState(p);
        };
    }

    /** Cone-walk: emit in-cone solid-block quads de-biased by {@code origin} (camera in world frame, rotation point in
     *  grid frame). {@code level} is the neighbour face-sturdy context. Returns quads emitted. */
    private static int emitConeBlocks(VertexConsumer vc, Level level, BlockReader reader, ProjectorRenderer.Pinhole ph, Vec3 origin) {
        return emitConeBlocks(vc, level, reader, ph, origin, false);
    }

    private static int emitConeBlocks(VertexConsumer vc, Level level, BlockReader reader, ProjectorRenderer.Pinhole ph, Vec3 origin,
                                      boolean frontOnly) {
        Vec3 eye = ph.eye(), forward = ph.forward(), right = ph.right(), up = ph.up();
        double coneK = ph.coneK();
        ProjectorRenderer.Plane[] planes =
                ProjectorRenderer.buildFrustumPlanes(eye, forward, right, up, coneK, ProjectorRenderer.MAX_THROW);
        AABB bounds = ProjectorRenderer.coneAABB(ph);
        if (bounds.maxX - bounds.minX > ProjectorRenderer.MAX_SPAN
                || bounds.maxY - bounds.minY > ProjectorRenderer.MAX_SPAN
                || bounds.maxZ - bounds.minZ > ProjectorRenderer.MAX_SPAN) {
            return 0; // runaway guard
        }
        BlockPos lo = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos hi = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos np = new BlockPos.MutableBlockPos();
        int emitted = 0;
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    if (emitted >= ProjectorRenderer.MAX_FACES) {
                        blocksCapped = true;
                        return emitted;
                    }
                    AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
                    if (ProjectorRenderer.aabbOutside(cell, planes)) {
                        continue;
                    }
                    mp.set(x, y, z);
                    BlockState st = reader.at(mp);
                    if (st.isAir() || st.canBeReplaced()) {
                        continue; // air + vegetation/fluids: beam passes through
                    }
                    if (ProjectorRenderer.isLightTransmitting(st)) {
                        skippedGlass++;
                        continue; // glass-family: beam passes through onto what's behind
                    }
                    for (ProjectorRenderer.ModelQuad q : ProjectorRenderer.modelQuads(st)) {
                        if (q.cull() >= 0) {
                            Direction cf = Direction.from3DDataValue(q.cull());
                            np.set(x + cf.getStepX(), y + cf.getStepY(), z + cf.getStepZ());
                            if (reader.at(np).isFaceSturdy(level, np, cf.getOpposite())) {
                                continue;
                            }
                        }
                        if (frontOnly) {
                            // Peel keeps only lens-facing surfaces: an exit face would read as a "second wall" behind every block.
                            float[] m = q.pos();
                            double dx = x + m[0] - ph.eye().x, dy = y + m[1] - ph.eye().y, dz = z + m[2] - ph.eye().z;
                            if (q.nx() * dx + q.ny() * dy + q.nz() * dz >= -1.0e-6) {
                                continue;
                            }
                        }
                        emitQuad(vc, q, x, y, z, origin);
                        if (++emitted >= ProjectorRenderer.MAX_FACES) {
                            blocksCapped = true;
                            return emitted;
                        }
                    }
                }
            }
        }
        return emitted;
    }

    /** One model quad as 4 POSITION_TEX vertices de-biased by {@code origin}; the atlas UV drives the shader cutout test. */
    private static void emitQuad(VertexConsumer vc, ProjectorRenderer.ModelQuad q, int bx, int by, int bz, Vec3 origin) {
        float[] m = q.pos();
        float[] t = q.uv();
        double ox = bx - origin.x, oy = by - origin.y, oz = bz - origin.z;
        for (int k = 0; k < 4; k++) {
            vc.addVertex((float) (m[k * 3] + ox), (float) (m[k * 3 + 1] + oy), (float) (m[k * 3 + 2] + oz))
                    .setUv(t[k * 2], t[k * 2 + 1]);
        }
    }

    /** Logs a capture summary for {@code pos} on change only; counts bucketed (0/1-9/10-99/100+) so raw jitter isn't spam. */
    private static void diag(BlockPos pos, String summary) {
        String bucketed = BUCKET_NUM.matcher(summary).replaceAll(r -> bucket(Integer.parseInt(r.group(1))));
        if (!bucketed.equals(DIAG_LAST.put(pos, bucketed))) {
            TheBeyond.LOGGER.info("[Projector DIAG] {}: {}", pos.toShortString(), bucketed);
        }
    }

    private static final java.util.regex.Pattern BUCKET_NUM = java.util.regex.Pattern.compile("=(\\d+)");

    private static String bucket(int n) {
        return n == 0 ? "=0" : n < 10 ? "=1-9" : n < 100 ? "=10-99" : "=100+";
    }

    /** Drop per-frame state on logout; pooled FBOs survive the session. */
    public static void clear() {
        ACTIVE.clear();
        CAPTURED.clear();
        DIAG_LAST.clear();
    }
}
