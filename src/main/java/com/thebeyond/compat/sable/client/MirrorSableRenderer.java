package com.thebeyond.compat.sable.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.client.renderer.FboTexture;
import com.thebeyond.client.renderer.MirrorReflection;
import com.thebeyond.common.block.MirrorBlock;
import com.thebeyond.common.block.blockentities.MirrorBlockEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Capture lives in {@link #captureAll}: a mid-frame offscreen FBO capture corrupts the in-progress render under Iris/Veil/Sodium. */
public final class MirrorSableRenderer implements BlockEntityRenderer<MirrorBlockEntity> {

    private static final int TINT_R = 202, TINT_G = 222, TINT_B = 234;
    private static final float ALPHA_NEAR = 0.9f;
    private static final float ALPHA_FAR = 0.3f;
    private static final float RENDER_DIST = 11.0f;
    private static final int MAX_REFLECTED_ENTITIES = 16;
    private static final float FACE_OUTSET = 0.003f;
    private static final int MAX_FBOS = 32;      // also the concurrent-plane cap
    private static final long STALE_TICKS = 5;
    private static final int MAX_SUBDIV = 32;
    private static final float[] LOD_FRACS = {1.0f, 0.5f, 0.25f, 0.125f};
    private static final double[] LOD_BANDS = {5.0, 6.5, 8.0};
    private static final double LOD_HYSTERESIS = 0.6;
    private static final double OCCLUDER_MARGIN = 2.5;
    private static final int MAX_OCCLUDER_BLOCKS = 512;
    private static final int BORDER_DIM = 32;
    // skip the contraption's own mounting blocks flush with the face; a real occluder sits deeper
    private static final double NEAR_FACE_SKIP = 1.0;
    private static final float BORDER_SHADE = 0.05f;
    private static final int BLOB_TESS = 8;
    private static final float BLOB_PEAK = 0.27f;
    private static final float BLOB_R_NEAR = 1.4f;
    private static final float BLOB_R_FAR = 0.7f;
    private static final float BLOB_GROW_DIST = 5.0f;
    private static final int MAX_BLOB_FACES = 600;
    private static final double NEAR_ENTITY = 0.85;
    // all scratch/cache statics below: render-thread only, no locking
    private static final boolean[] BORDER_OCC = new boolean[BORDER_DIM * BORDER_DIM * BORDER_DIM];
    private static final Vector3f SCRATCH_V3 = new Vector3f();
    private static final Vector4f SCRATCH_V4 = new Vector4f();
    private static final float[] BLOB_AL = new float[(BLOB_TESS + 1) * (BLOB_TESS + 1)];
    // fetch each chunk once per frame, not per ray (Sable taxes every getChunk)
    private static final Long2ObjectOpenHashMap<LevelChunk> LOS_CHUNK_CACHE = new Long2ObjectOpenHashMap<>();
    private static final int ENTITY_REFRESH_TICKS = 4;
    private static final int MAX_LOS_RECOMPUTES_PER_FRAME = 4;

    private static boolean loggedActive;
    private static final Long2ObjectOpenHashMap<Slot> SLOTS = new Long2ObjectOpenHashMap<>();
    private static final MultiBufferSource.BufferSource FBO_BUFFER =
            MultiBufferSource.immediate(new ByteBufferBuilder(2048));
    // bounded name pool: a counter would leak one memoized RenderType per plane ever seen
    private static final boolean[] TEX_SLOT_USED = new boolean[MAX_FBOS];
    private static final Object2IntOpenHashMap<Entity> LIGHT_CACHE = new Object2IntOpenHashMap<>();
    // identity-keyed: sub-levels aren't value-equal
    private static final java.util.IdentityHashMap<ClientSubLevel, SubTransform> SUBXF_CACHE = new java.util.IdentityHashMap<>();
    private static int losBudget;

    public MirrorSableRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static final class Slot {
        final ResourceLocation texture;
        final FboTexture fboTexture = new FboTexture();
        TextureTarget target;
        Vec3 worldPoint;   // absolute, so captureAll re-derives against the live camera
        Vec3 worldNormal;
        BlockPos mirrorPos;
        Direction facing;
        double planeDist;
        int lodBand = -1;
        long lastSeenTick = Long.MIN_VALUE;
        List<Entity> reflected;
        long reflectedTick = Long.MIN_VALUE;
        boolean packPath;
        final Matrix4f sampleVP = new Matrix4f(); // main proj·view·(−camPos): world point → screen UV
        int texIndex = -1;
        int heldBoost;        // brightest handheld-light level among holders

        Slot(ResourceLocation texture) {
            this.texture = texture;
        }
    }

    @Override
    public void render(MirrorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buf,
                       int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null || mc.level == null) {
            return;
        }
        if (com.thebeyond.client.renderer.BlockCameraCapture.isCapturing()) {
            return;
        }
        SubLevel sub = Sable.HELPER.getContaining(be.getLevel(), be.getBlockPos());
        if (sub == null) {
            return; // main world — handled by the world-event reflection path
        }
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof MirrorBlock)) {
            return;
        }
        List<Direction> faces = MirrorBlock.reflectiveFaces(st);
        if (faces.isEmpty() || !SableReflectionFrame.valid()) {
            return;
        }
        if (!loggedActive) {
            loggedActive = true;
            TheBeyond.LOGGER.info("[TheBeyond] Sable mirror live reflection active (sub-level capture path).");
        }

        Vec3 camPos = SableReflectionFrame.camPos();
        // Sable baked the local->render transform into this pose, so the plane already follows the sub-level
        Matrix4f m = pose.last().pose();
        long now = mc.level.getGameTime();
        long subBits = subLevelKeyBits(sub, partialTick);

        for (Direction facing : faces) {
            float[] fc = faceCenterLocal(facing);
            Vector3f p = m.transformPosition(new Vector3f(fc[0], fc[1], fc[2]));
            Vector3f nf = m.transformDirection(new Vector3f(
                    facing.getStepX(), facing.getStepY(), facing.getStepZ()));
            double nlen = Math.sqrt(nf.x * nf.x + nf.y * nf.y + nf.z * nf.z);
            if (nlen < 1.0e-6) {
                continue;
            }
            double planeDist = Math.sqrt((double) p.x * p.x + (double) p.y * p.y + (double) p.z * p.z);
            if (planeDist > RENDER_DIST) {
                continue;
            }
            // front-of-face cull; a back face z-fights the block body
            if (p.x * nf.x + p.y * nf.y + p.z * nf.z >= 0.0) {
                continue;
            }
            long key = planeKeyLong(subBits, be.getBlockPos(), facing);
            Slot slot = SLOTS.get(key);
            if (slot == null) {
                int texIdx = freeTexSlot();
                if (texIdx < 0) {
                    continue;
                }
                slot = new Slot(ResourceLocation.fromNamespaceAndPath(
                        TheBeyond.MODID, "sable_mirror_reflection_" + texIdx));
                slot.texIndex = texIdx;
                TEX_SLOT_USED[texIdx] = true;
                SLOTS.put(key, slot);
            }
            boolean firstThisFrame = slot.lastSeenTick != now;
            if (firstThisFrame || planeDist < slot.planeDist) {
                slot.worldPoint = new Vec3(camPos.x + p.x, camPos.y + p.y, camPos.z + p.z);
                slot.worldNormal = new Vec3(nf.x / nlen, nf.y / nlen, nf.z / nlen);
                slot.mirrorPos = be.getBlockPos();
                slot.facing = facing;
                slot.planeDist = planeDist;
            }
            slot.lastSeenTick = now;

            if (slot.target == null) {
                continue; // one-frame warm-up: not captured yet
            }
            int alpha = (int) (Mth.clamp(fadeFor(planeDist), 0.0f, 1.0f) * 255.0f);
            PoseStack.Pose ps = pose.last();
            float[][] corners = faceQuad(facing, FACE_OUTSET);
            if (slot.packPath) {
                VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.mirrorPack(slot.texture));
                // FBO is unlit albedo under a pack; light from the front cell, since the face cell reads sky and pins full-bright
                int ambient = LevelRenderer.getLightColor(mc.level, be.getBlockPos().relative(facing));
                if (slot.heldBoost > 0) {
                    ambient = (Math.max((ambient >> 4) & 0xF, slot.heldBoost) << 4) | (((ambient >> 20) & 0xF) << 20);
                }
                // always max-subdivide: per-corner affine UVs diverge from projective at grazing angles and tear
                int subdiv = MAX_SUBDIV;
                for (int i = 0; i < subdiv; i++) {
                    for (int j = 0; j < subdiv; j++) {
                        float u0 = (float) i / subdiv, u1 = (float) (i + 1) / subdiv;
                        float v0 = (float) j / subdiv, v1 = (float) (j + 1) / subdiv;
                        addSubVertex(vc, m, ps, facing, corners, camPos, slot.sampleVP, alpha, ambient, u0, v0);
                        addSubVertex(vc, m, ps, facing, corners, camPos, slot.sampleVP, alpha, ambient, u1, v0);
                        addSubVertex(vc, m, ps, facing, corners, camPos, slot.sampleVP, alpha, ambient, u1, v1);
                        addSubVertex(vc, m, ps, facing, corners, camPos, slot.sampleVP, alpha, ambient, u0, v1);
                    }
                }
            } else {
                VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.mirror(slot.texture));
                for (float[] c : corners) {
                    vc.addVertex(ps, c[0], c[1], c[2]).setColor(TINT_R, TINT_G, TINT_B, alpha);
                }
            }
        }
    }

    /** Runs at AFTER_SOLID_BLOCKS, a safe between-passes point for an offscreen FBO. */
    static void captureAll(Matrix4f mainProj, Matrix4f mainView, Vec3 camPos, float pt, Frustum frustum) {
        if (SLOTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        int w = SableReflectionFrame.screenW();
        int h = SableReflectionFrame.screenH();
        if (w <= 0 || h <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        losBudget = MAX_LOS_RECOMPUTES_PER_FRAME;
        LOS_CHUNK_CACHE.clear();
        LIGHT_CACHE.clear();
        SUBXF_CACHE.clear();
        EntityRenderDispatcher disp = mc.getEntityRenderDispatcher();
        RenderTarget main = mc.getMainRenderTarget();

        RenderSystem.backupProjectionMatrix();
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        try {
            for (Iterator<Slot> it = SLOTS.values().iterator(); it.hasNext(); ) {
                Slot slot = it.next();
                if (slot.worldPoint == null || now - slot.lastSeenTick > STALE_TICKS) {
                    if (slot.target != null) {
                        slot.target.destroyBuffers();
                    }
                    mc.getTextureManager().release(slot.texture);
                    if (slot.texIndex >= 0) {
                        TEX_SLOT_USED[slot.texIndex] = false;
                    }
                    it.remove();
                    continue;
                }
                if (frustum != null && !frustum.isVisible(new AABB(
                        slot.worldPoint.x - 4.0, slot.worldPoint.y - 4.0, slot.worldPoint.z - 4.0,
                        slot.worldPoint.x + 4.0, slot.worldPoint.y + 4.0, slot.worldPoint.z + 4.0))) {
                    continue;
                }
                try {
                    slot.lodBand = lodBandFor(slot.planeDist, slot.lodBand);
                    float frac = LOD_FRACS[slot.lodBand];
                    int fw = Math.max(16, Math.round(w * frac));
                    int fh = Math.max(16, Math.round(h * frac));
                    if (slot.target == null) {
                        slot.target = new TextureTarget(fw, fh, true, Minecraft.ON_OSX);
                        slot.target.setFilterMode(GL11.GL_NEAREST);
                        mc.getTextureManager().register(slot.texture, slot.fboTexture);
                    } else if (slot.target.width != fw || slot.target.height != fh) {
                        slot.target.resize(fw, fh, Minecraft.ON_OSX);
                        slot.target.setFilterMode(GL11.GL_NEAREST);
                    }
                    captureSlot(slot, mainProj, mainView, camPos, pt, disp, mc);
                } catch (Throwable t) {
                    TheBeyond.LOGGER.error("[TheBeyond] Sable mirror capture failed for one plane", t);
                }
            }
        } finally {
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            main.bindWrite(true);
        }
    }

    private static void captureSlot(Slot slot, Matrix4f mainProj, Matrix4f mainView, Vec3 camPos, float pt,
                                    EntityRenderDispatcher disp, Minecraft mc) {
        // recompute the plane from this frame's transform; the BER's is one frame stale and desyncs FBO from face
        SubTransform st = resolveSubTransform(slot, camPos, pt, mc);
        if (st != null) {
            float[] fcp = faceCenterLocal(slot.facing);
            Vector3f pc = st.m().transformPosition(new Vector3f(
                    (float) (slot.mirrorPos.getX() + (double) fcp[0] - st.rpx()),
                    (float) (slot.mirrorPos.getY() + (double) fcp[1] - st.rpy()),
                    (float) (slot.mirrorPos.getZ() + (double) fcp[2] - st.rpz())));
            Vector3f nf = st.m().transformDirection(new Vector3f(
                    slot.facing.getStepX(), slot.facing.getStepY(), slot.facing.getStepZ()));
            double nlen = Math.sqrt((double) nf.x * nf.x + (double) nf.y * nf.y + (double) nf.z * nf.z);
            if (nlen > 1.0e-6) {
                slot.worldPoint = new Vec3(camPos.x + pc.x, camPos.y + pc.y, camPos.z + pc.z);
                slot.worldNormal = new Vec3(nf.x / nlen, nf.y / nlen, nf.z / nlen);
            }
        }
        Vec3 n = slot.worldNormal;
        Vec3 p0rel = slot.worldPoint.subtract(camPos);
        float dd = (float) -(n.x * p0rel.x + n.y * p0rel.y + n.z * p0rel.z);
        Matrix4f reflect = new Matrix4f().reflection((float) n.x, (float) n.y, (float) n.z, dd);
        Matrix4f proj = new Matrix4f(mainProj);
        Matrix4f modelView = new Matrix4f(mainView).mul(reflect);
        MirrorReflection.applyObliqueNearClip(proj, mainView, n, p0rel);

        // pack overrides our gl_FragCoord shader, so sample via explicit UVs from the main (unclipped) VP
        slot.packPath = ShaderCompatLib.isShaderPackActive();
        if (slot.packPath) {
            slot.sampleVP.set(mainProj).mul(mainView)
                    .translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        }

        long now = mc.level.getGameTime();
        // first/empty selection bypasses the per-frame budget, else an empty list latches as the budget moves on
        boolean trulyFirst = slot.reflected == null;
        boolean latchedEmpty = !trulyFirst && slot.reflected.isEmpty();
        boolean dueByTick = now - slot.reflectedTick >= ENTITY_REFRESH_TICKS;
        boolean recompute = trulyFirst || (latchedEmpty && dueByTick) || (dueByTick && losBudget > 0);
        if (recompute) {
            slot.reflected = collectReflectedEntities(mc, camPos.add(p0rel), n, pt);
            slot.reflectedTick = now;
            if (!trulyFirst && !latchedEmpty) {
                losBudget--;
            }
        }
        List<Entity> reflected = slot.reflected != null ? slot.reflected : List.of();
        slot.heldBoost = slot.packPath ? MirrorReflection.heldLightBoost(reflected, slot.worldPoint, pt) : 0;

        SubBlockFrame sf = resolveSubFrame(st, slot, reflected, camPos, modelView);

        slot.target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        slot.target.clear(Minecraft.ON_OSX);
        slot.target.bindWrite(true);

        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();

        if (sf != null) {
            mvStack.set(sf.modelViewB);
            RenderSystem.applyModelViewMatrix();
            renderOccluderDepthSub(sf, mc);
        }

        mvStack.set(modelView);
        RenderSystem.applyModelViewMatrix();
        // world-frame occluders (not modelViewB); self-flushes depth before the entities
        MirrorReflection.renderOccluderDepth(FBO_BUFFER, reflected, camPos, mc, n, slot.worldPoint);
        disp.setRenderShadow(false);
        GL11.glFrontFace(GL11.GL_CW); // reflection flips winding
        for (Entity e : reflected) {
            if (e.isRemoved()) {
                continue; // cached selection can outlive an entity by a few ticks
            }
            double ex = Mth.lerp(pt, e.xOld, e.getX());
            double ey = Mth.lerp(pt, e.yOld, e.getY());
            double ez = Mth.lerp(pt, e.zOld, e.getZ());
            float eyaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
            int light;
            if (LIGHT_CACHE.containsKey(e)) {
                light = LIGHT_CACHE.getInt(e);
            } else {
                light = disp.getPackedLightCoords(e, pt);
                LIGHT_CACHE.put(e, light);
            }
            disp.render(e, ex - camPos.x, ey - camPos.y, ez - camPos.z, eyaw, pt,
                    new PoseStack(), FBO_BUFFER, light);
        }
        FBO_BUFFER.endBatch();
        GL11.glFrontFace(GL11.GL_CCW);
        disp.setRenderShadow(true);

        // after the entities, so reflected bodies still occlude the shade (depth-test, no depth-write)
        if (sf != null) {
            mvStack.set(sf.modelViewB);
            RenderSystem.applyModelViewMatrix();
            renderOcclusionShadeSub(sf, mc);
        }
        // same shade pass, world-frame occluders
        mvStack.set(modelView);
        RenderSystem.applyModelViewMatrix();
        MirrorReflection.renderOcclusionShade(FBO_BUFFER, reflected, n, slot.worldPoint, camPos, mc);

        slot.target.unbindWrite();
        slot.fboTexture.setId(slot.target.getColorTextureId());
    }

    private static int freeTexSlot() {
        for (int i = 0; i < TEX_SLOT_USED.length; i++) {
            if (!TEX_SLOT_USED[i]) {
                return i;
            }
        }
        return -1;
    }

    /** Per-contraption key bits, else two momentarily-coplanar contraptions collide on one slot and ghost. */
    private static long subLevelKeyBits(SubLevel sub, float partialTick) {
        if (!(sub instanceof ClientSubLevel csl)) {
            return 0L;
        }
        csl.renderPose(partialTick);
        Vector3dc rp = csl.renderPose().rotationPoint();
        int h = (Mth.floor(rp.x()) * 31 + Mth.floor(rp.y())) * 31 + Mth.floor(rp.z());
        return h & 0x1FFFFFFFL; // 29 bits, sits above facing(3)+coord(32)
    }

    /** Key = pivot + facing + face-plane coord, so coplanar faces share a slot. */
    private static long planeKeyLong(long subBits, BlockPos pos, Direction facing) {
        int coord = switch (facing) {
            case DOWN -> pos.getY();
            case UP -> pos.getY() + 1;
            case NORTH -> pos.getZ();
            case SOUTH -> pos.getZ() + 1;
            case WEST -> pos.getX();
            case EAST -> pos.getX() + 1;
        };
        return (subBits << 35) | ((long) facing.get3DDataValue() << 32) | (coord & 0xFFFFFFFFL);
    }

    /** Pays Sable's bounds mixin once per chunk, not per cell; valid only within one captureAll. */
    private static BlockState cachedState(net.minecraft.world.level.Level level, BlockPos pos) {
        long ckey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        LevelChunk chunk = LOS_CHUNK_CACHE.get(ckey);
        if (chunk == null) {
            chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            LOS_CHUNK_CACHE.put(ckey, chunk);
        }
        return chunk.getBlockState(pos);
    }

    private static List<Entity> collectReflectedEntities(Minecraft mc, Vec3 point, Vec3 normal, float pt) {
        double r = RENDER_DIST;
        AABB box = new AABB(point.x - r, point.y - r, point.z - r, point.x + r, point.y + r, point.z + r);
        List<Entity> front = new ArrayList<>();
        for (Entity e : mc.level.getEntities((Entity) null, box, ent -> !ent.isSpectator())) {
            if (e.getEyePosition(pt).subtract(point).dot(normal) <= 0.0) {
                continue;
            }
            front.add(e);
        }
        front.sort(Comparator.comparingDouble(e -> e.distanceToSqr(point.x, point.y, point.z)));

        List<Entity> visible = new ArrayList<>();
        int budget = Math.min(front.size(), MAX_REFLECTED_ENTITIES * 3);
        for (int i = 0; i < budget && visible.size() < MAX_REFLECTED_ENTITIES; i++) {
            Entity e = front.get(i);
            if (hasAnyLineOfSight(e, point, normal, pt)) {
                visible.add(e);
            }
        }
        return visible;
    }

    private static boolean hasAnyLineOfSight(Entity e, Vec3 point, Vec3 normal, float pt) {
        double ex = Mth.lerp(pt, e.xOld, e.getX());
        double ey = Mth.lerp(pt, e.yOld, e.getY());
        double ez = Mth.lerp(pt, e.zOld, e.getZ());
        double h = e.getBbHeight();
        Vec3 surface = point.add(normal.scale(0.1));
        for (double f : new double[]{0.1, 0.4, 0.7, 1.0}) {
            Vec3 from = new Vec3(ex, ey + h * f, ez);
            double dd = from.subtract(point).dot(normal);
            if (dd <= 0.0) {
                continue;
            }
            Vec3 foot = from.subtract(normal.scale(dd)).add(normal.scale(0.1));
            if (hasLineOfSight(e, from, foot) || hasLineOfSight(e, from, surface)) {
                return true;
            }
        }
        return false;
    }

    /** Voxel walk (only a non-mirror full opaque cube blocks); level.clip()'s per-ray cost would dominate capture. */
    private static boolean hasLineOfSight(Entity e, Vec3 eye, Vec3 target) {
        var level = e.level();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-4) {
            return true;
        }
        // reject a pathological ray: an entity that switched reference frame this frame spans ~2e7
        if (dist > RENDER_DIST * 2.0) {
            return false;
        }
        double inv = 1.0 / dist;
        double sx = dx * inv, sy = dy * inv, sz = dz * inv;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long lastEnc = Long.MIN_VALUE;
        int steps = (int) Math.ceil(dist / 0.5);
        for (int i = 0; i <= steps; i++) {
            double t = Math.min(i * 0.5, dist);
            mp.set(Mth.floor(eye.x + sx * t), Mth.floor(eye.y + sy * t), Mth.floor(eye.z + sz * t));
            long enc = mp.asLong();
            if (enc == lastEnc) {
                continue;
            }
            lastEnc = enc;
            int cx = mp.getX() >> 4, cz = mp.getZ() >> 4;
            long ckey = ChunkPos.asLong(cx, cz);
            LevelChunk chunk = LOS_CHUNK_CACHE.get(ckey);
            if (chunk == null) {
                chunk = level.getChunk(cx, cz);
                LOS_CHUNK_CACHE.put(ckey, chunk);
            }
            BlockState st = chunk.getBlockState(mp);
            if (st.getBlock() instanceof MirrorBlock) {
                continue;
            }
            if (st.canOcclude() && st.isCollisionShapeFullBlock(level, mp)) {
                return false;
            }
        }
        return true;
    }

    private static float fadeFor(double dist) {
        float t = (float) Mth.clamp(dist / RENDER_DIST, 0.0, 1.0);
        return Mth.lerp(t, ALPHA_NEAR, ALPHA_FAR);
    }

    /** Hysteresis dead-zone so a drifting distance can't resize the FBO every frame at a band edge. */
    private static int lodBandFor(double dist, int prevBand) {
        int band = 0;
        while (band < LOD_BANDS.length && dist > LOD_BANDS[band]) {
            band++;
        }
        if (prevBand >= 0 && band != prevBand) {
            double edge = LOD_BANDS[Math.min(band, prevBand)];
            if (Math.abs(dist - edge) < LOD_HYSTERESIS) {
                return prevBand;
            }
        }
        return band;
    }

    private static float[] faceCenterLocal(Direction f) {
        return switch (f) {
            case NORTH -> new float[]{0.5f, 0.5f, 0.0f};
            case SOUTH -> new float[]{0.5f, 0.5f, 1.0f};
            case WEST -> new float[]{0.0f, 0.5f, 0.5f};
            case EAST -> new float[]{1.0f, 0.5f, 0.5f};
            case UP -> new float[]{0.5f, 1.0f, 0.5f};
            case DOWN -> new float[]{0.5f, 0.0f, 0.5f};
        };
    }

    /** Must match MirrorReflection's face. */
    private static float[][] faceQuad(Direction facing, float e) {
        return switch (facing) {
            case SOUTH -> new float[][]{{0, 0, 1 + e}, {1, 0, 1 + e}, {1, 1, 1 + e}, {0, 1, 1 + e}};
            case WEST -> new float[][]{{-e, 0, 1}, {-e, 0, 0}, {-e, 1, 0}, {-e, 1, 1}};
            case EAST -> new float[][]{{1 + e, 0, 0}, {1 + e, 0, 1}, {1 + e, 1, 1}, {1 + e, 1, 0}};
            case UP -> new float[][]{{0, 1 + e, 0}, {1, 1 + e, 0}, {1, 1 + e, 1}, {0, 1 + e, 1}};
            case DOWN -> new float[][]{{0, -e, 1}, {1, -e, 1}, {1, -e, 0}, {0, -e, 0}};
            default -> new float[][]{{1, 0, -e}, {0, 0, -e}, {0, 1, -e}, {1, 1, -e}}; // NORTH
        };
    }

    private static void addSubVertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose ps, Direction facing,
                                     float[][] corners, Vec3 camPos, Matrix4f vp, int alpha, int light, float u, float v) {
        float x = bilerp(corners[0][0], corners[1][0], corners[2][0], corners[3][0], u, v);
        float y = bilerp(corners[0][1], corners[1][1], corners[2][1], corners[3][1], u, v);
        float z = bilerp(corners[0][2], corners[1][2], corners[2][2], corners[3][2], u, v);
        Vector3f w = m.transformPosition(SCRATCH_V3.set(x, y, z));
        SCRATCH_V4.set((float) (camPos.x + w.x), (float) (camPos.y + w.y), (float) (camPos.z + w.z), 1.0f);
        vp.transform(SCRATCH_V4);
        float iw = (Math.abs(SCRATCH_V4.w) < 1.0e-5f) ? 0.0f : 1.0f / SCRATCH_V4.w;
        vc.addVertex(m, x, y, z)
                .setColor(TINT_R, TINT_G, TINT_B, alpha)
                .setUv(SCRATCH_V4.x * iw * 0.5f + 0.5f, SCRATCH_V4.y * iw * 0.5f + 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(ps, facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private static float bilerp(float c0, float c1, float c2, float c3, float u, float v) {
        float bottom = c0 + (c1 - c0) * u;
        float top = c3 + (c2 - c3) * u;
        return bottom + (top - bottom) * v;
    }


    // emit (gridCoord − rotationPoint): raw grid coords ~2e7 lose sub-block precision as float32. modelViewB = modelView·M maps the de-biased coord; bodies → grid via M⁻¹.

    private static final class SubBlockFrame {
        final Matrix4f modelViewB;
        final Vec3 planePoint;
        final Vec3 planeNormal;
        final double camGx, camGy, camGz;
        final double rpx, rpy, rpz; // rotationPoint
        final List<double[]> boxes; // grid-space AABBs {minX,minY,minZ,maxX,maxY,maxZ}

        SubBlockFrame(Matrix4f modelViewB, Vec3 planePoint, Vec3 planeNormal,
                      double camGx, double camGy, double camGz, double rpx, double rpy, double rpz, List<double[]> boxes) {
            this.modelViewB = modelViewB;
            this.planePoint = planePoint;
            this.planeNormal = planeNormal;
            this.camGx = camGx;
            this.camGy = camGy;
            this.camGz = camGz;
            this.rpx = rpx;
            this.rpy = rpy;
            this.rpz = rpz;
            this.boxes = boxes;
        }
    }

    /** M maps (gridCoord − rotationPoint) → camera-relative visible space. */
    private record SubTransform(Matrix4f m, Matrix4f minv, double rpx, double rpy, double rpz) {
    }

    private static SubTransform resolveSubTransform(Slot slot, Vec3 camPos, float pt, Minecraft mc) {
        if (slot.mirrorPos == null || slot.facing == null) {
            return null;
        }
        SubLevel sub = Sable.HELPER.getContaining(mc.level, slot.mirrorPos);
        if (!(sub instanceof ClientSubLevel csl)) {
            return null;
        }
        SubTransform cached = SUBXF_CACHE.get(csl);
        if (cached != null) {
            return cached;
        }
        csl.renderPose(pt); // side-effect: populate the interpolated-pose cache for this tick
        Vector3dc rp = csl.renderPose().rotationPoint();
        Matrix4f m = csl.getRenderData().getTransformation(camPos.x, camPos.y, camPos.z, new Matrix4f());
        Matrix4f minv = new Matrix4f(m).invert();
        SubTransform st = new SubTransform(m, minv, rp.x(), rp.y(), rp.z());
        SUBXF_CACHE.put(csl, st);
        return st;
    }

    /** Null when it can't be built: block occlusion is skipped, entities still reflect. */
    private static SubBlockFrame resolveSubFrame(SubTransform st, Slot slot, List<Entity> reflected, Vec3 camPos,
                                                 Matrix4f modelView) {
        if (st == null || reflected.isEmpty()) {
            return null;
        }
        Matrix4f m = st.m(), minv = st.minv();
        double rpx = st.rpx(), rpy = st.rpy(), rpz = st.rpz();
        Matrix4f modelViewB = new Matrix4f(modelView).mul(m);

        Vector3f camG = minv.transformPosition(new Vector3f(0.0f, 0.0f, 0.0f));
        double camGx = camG.x + rpx, camGy = camG.y + rpy, camGz = camG.z + rpz;

        float[] fc = faceCenterLocal(slot.facing);
        Vec3 planePoint = new Vec3(slot.mirrorPos.getX() + fc[0], slot.mirrorPos.getY() + fc[1], slot.mirrorPos.getZ() + fc[2]);
        Vec3 planeNormal = new Vec3(slot.facing.getStepX(), slot.facing.getStepY(), slot.facing.getStepZ());

        List<double[]> boxes = new ArrayList<>(reflected.size());
        for (Entity e : reflected) {
            boxes.add(gridAabb(e.getBoundingBox(), minv, camPos, rpx, rpy, rpz));
        }
        // degenerate transform (mid-spawn zero scale → non-finite inverse): skip occlusion
        for (double[] g : boxes) {
            for (double val : g) {
                if (!Double.isFinite(val)) {
                    return null;
                }
            }
        }
        return new SubBlockFrame(modelViewB, planePoint, planeNormal, camGx, camGy, camGz, rpx, rpy, rpz, boxes);
    }

    /** All 8 corners through M⁻¹ since the box rotates. */
    private static double[] gridAabb(AABB b, Matrix4f minv, Vec3 camPos, double rpx, double rpy, double rpz) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        Vector3f v = new Vector3f();
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    v.set((float) (x - camPos.x), (float) (y - camPos.y), (float) (z - camPos.z));
                    minv.transformPosition(v);
                    double gx = v.x + rpx, gy = v.y + rpy, gz = v.z + rpz;
                    if (gx < minX) minX = gx;
                    if (gy < minY) minY = gy;
                    if (gz < minZ) minZ = gz;
                    if (gx > maxX) maxX = gx;
                    if (gy > maxY) maxY = gy;
                    if (gz > maxZ) maxZ = gz;
                }
            }
        }
        return new double[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    private static void renderOccluderDepthSub(SubBlockFrame sf, Minecraft mc) {
        List<double[]> boxes = sf.boxes;
        Vec3 n = sf.planeNormal, p0 = sf.planePoint;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double dMax = 0.0;
        for (double[] b : boxes) {
            minX = Math.min(minX, b[0]); minY = Math.min(minY, b[1]); minZ = Math.min(minZ, b[2]);
            maxX = Math.max(maxX, b[3]); maxY = Math.max(maxY, b[4]); maxZ = Math.max(maxZ, b[5]);
            double far = n.x * ((n.x > 0 ? b[3] : b[0]) - p0.x)
                    + n.y * ((n.y > 0 ? b[4] : b[1]) - p0.y)
                    + n.z * ((n.z > 0 ? b[5] : b[2]) - p0.z);
            dMax = Math.max(dMax, far);
        }
        // bound the scan: a degenerate/oversized box makes betweenClosed iterate astronomically and hang
        if (!Double.isFinite(maxX - minX) || !Double.isFinite(maxY - minY) || !Double.isFinite(maxZ - minZ)
                || (maxX - minX) + 2 * OCCLUDER_MARGIN > BORDER_DIM
                || (maxY - minY) + 2 * OCCLUDER_MARGIN > BORDER_DIM
                || (maxZ - minZ) + 2 * OCCLUDER_MARGIN > BORDER_DIM) {
            return;
        }
        var level = mc.level;
        VertexConsumer vc = FBO_BUFFER.getBuffer(BeyondRenderTypes.MIRROR_OCCLUDER);
        boolean modelBased = com.thebeyond.BeyondConfig.MIRROR_OCCLUSION_MODEL_BASED.get();
        BlockPos min = BlockPos.containing(minX - OCCLUDER_MARGIN, minY - OCCLUDER_MARGIN, minZ - OCCLUDER_MARGIN);
        BlockPos max = BlockPos.containing(maxX + OCCLUDER_MARGIN, maxY + OCCLUDER_MARGIN, maxZ + OCCLUDER_MARGIN);
        int rendered = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (rendered >= MAX_OCCLUDER_BLOCKS) {
                break;
            }
            double d = n.x * (pos.getX() + 0.5 - p0.x) + n.y * (pos.getY() + 0.5 - p0.y) + n.z * (pos.getZ() + 0.5 - p0.z);
            if (d <= NEAR_FACE_SKIP || d >= dMax) {
                continue;
            }
            BlockState bs = cachedState(level, pos);
            if (!(bs.getBlock() instanceof MirrorBlock) && !bs.canOcclude()) {
                continue;
            }
            if (modelBased) {
                float[] quads = MirrorReflection.occluderModelQuads(bs);
                if (quads.length == 0) {
                    continue;
                }
                float ox = (float) (pos.getX() - sf.rpx), oy = (float) (pos.getY() - sf.rpy), oz = (float) (pos.getZ() - sf.rpz);
                for (int qi = 0; qi + 11 < quads.length; qi += 12) {
                    vc.addVertex(quads[qi] + ox, quads[qi + 1] + oy, quads[qi + 2] + oz);
                    vc.addVertex(quads[qi + 3] + ox, quads[qi + 4] + oy, quads[qi + 5] + oz);
                    vc.addVertex(quads[qi + 6] + ox, quads[qi + 7] + oy, quads[qi + 8] + oz);
                    vc.addVertex(quads[qi + 9] + ox, quads[qi + 10] + oy, quads[qi + 11] + oz);
                }
            } else {
                VoxelShape shape = bs.getShape(level, pos);
                if (shape.isEmpty()) {
                    continue;
                }
                double ox = pos.getX() - sf.rpx, oy = pos.getY() - sf.rpy, oz = pos.getZ() - sf.rpz;
                for (AABB box : shape.toAabbs()) {
                    addBox(vc, box, ox, oy, oz);
                }
            }
            rendered++;
        }
        FBO_BUFFER.endBatch();
    }

    private static void renderOcclusionShadeSub(SubBlockFrame sf, Minecraft mc) {
        List<double[]> boxes = sf.boxes;
        var level = mc.level;
        if (level == null) {
            return;
        }
        Vec3 n = sf.planeNormal, p0 = sf.planePoint;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double dMax = 0.0;
        for (double[] b : boxes) {
            minX = Math.min(minX, b[0]); minY = Math.min(minY, b[1]); minZ = Math.min(minZ, b[2]);
            maxX = Math.max(maxX, b[3]); maxY = Math.max(maxY, b[4]); maxZ = Math.max(maxZ, b[5]);
            double far = n.x * ((n.x > 0 ? b[3] : b[0]) - p0.x)
                    + n.y * ((n.y > 0 ? b[4] : b[1]) - p0.y)
                    + n.z * ((n.z > 0 ? b[5] : b[2]) - p0.z);
            dMax = Math.max(dMax, far);
        }
        int bx = Mth.floor(minX - OCCLUDER_MARGIN), by = Mth.floor(minY - OCCLUDER_MARGIN), bz = Mth.floor(minZ - OCCLUDER_MARGIN);
        int dx = Mth.floor(maxX + OCCLUDER_MARGIN) - bx + 1;
        int dy = Mth.floor(maxY + OCCLUDER_MARGIN) - by + 1;
        int dz = Mth.floor(maxZ + OCCLUDER_MARGIN) - bz + 1;
        if (dx < 1 || dy < 1 || dz < 1 || dx > BORDER_DIM || dy > BORDER_DIM || dz > BORDER_DIM) {
            return;
        }
        Arrays.fill(BORDER_OCC, 0, dx * dy * dz, false); // only [0, dx*dy*dz) is ever read
        boolean any = false;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < dx; ix++) {
            for (int iz = 0; iz < dz; iz++) {
                for (int iy = 0; iy < dy; iy++) {
                    int wx = bx + ix, wy = by + iy, wz = bz + iz;
                    double d = n.x * (wx + 0.5 - p0.x) + n.y * (wy + 0.5 - p0.y) + n.z * (wz + 0.5 - p0.z);
                    if (d <= NEAR_FACE_SKIP || d >= dMax) {
                        continue;
                    }
                    mp.set(wx, wy, wz);
                    BlockState bs = cachedState(level, mp);
                    if (bs.getBlock() instanceof MirrorBlock || bs.isAir()) {
                        continue;
                    }
                    if (bs.getShape(level, mp).isEmpty()) {
                        continue;
                    }
                    BORDER_OCC[borderIdx(ix, iy, iz, dy, dz)] = true;
                    any = true;
                }
            }
        }
        if (!any) {
            return;
        }
        VertexConsumer vc = FBO_BUFFER.getBuffer(BeyondRenderTypes.MIRROR_OUTLINE);
        float c = BORDER_SHADE;
        int blobs = 0;
        for (int ix = 0; ix < dx; ix++) {
            for (int iy = 0; iy < dy; iy++) {
                for (int iz = 0; iz < dz; iz++) {
                    if (!bcell(ix, iy, iz, dx, dy, dz) || blobs >= MAX_BLOB_FACES) {
                        continue;
                    }
                    int wx = bx + ix, wy = by + iy, wz = bz + iz;
                    // per-cell nearest body (not a global pick) → multiplayer-safe
                    double[] ne = null;
                    double best = Double.MAX_VALUE;
                    for (double[] b : boxes) {
                        double ecx = (b[0] + b[3]) * 0.5, ecy = (b[1] + b[4]) * 0.5, ecz = (b[2] + b[5]) * 0.5;
                        double ddx = ecx - (wx + 0.5), ddy = ecy - (wy + 0.5), ddz = ecz - (wz + 0.5);
                        double dd2 = ddx * ddx + ddy * ddy + ddz * ddz;
                        if (dd2 < best) {
                            best = dd2;
                            ne = b;
                        }
                    }
                    if (ne == null) {
                        continue;
                    }
                    double hcx = wx + 0.5, hcz = wz + 0.5;
                    double hdx = Math.max(Math.max(ne[0] - hcx, hcx - ne[3]), 0.0);
                    double hdz = Math.max(Math.max(ne[2] - hcz, hcz - ne[5]), 0.0);
                    if (hdx * hdx + hdz * hdz > NEAR_ENTITY * NEAR_ENTITY) {
                        continue;
                    }
                    double eCy = (ne[1] + ne[4]) * 0.5, eCx = (ne[0] + ne[3]) * 0.5, eCz = (ne[2] + ne[5]) * 0.5;
                    mp.set(wx, wy, wz);
                    BlockState bs = cachedState(level, mp);
                    VoxelShape vs = bs.getShape(level, mp);
                    AABB sb = vs.bounds();
                    // shape-aware (air in a cell counts as open) so a stack shows its real silhouette edges
                    boolean topOpen;
                    if (sb.maxY < 1.0 - 1.0e-4) {
                        topOpen = true;
                    } else {
                        BlockState up = cachedState(level, mp.set(wx, wy + 1, wz));
                        topOpen = !up.canOcclude() || up.getShape(level, mp).bounds().minY > 1.0e-4;
                        mp.set(wx, wy, wz);
                    }
                    boolean botOpen;
                    if (sb.minY > 1.0e-4) {
                        botOpen = true;
                    } else {
                        BlockState dn = cachedState(level, mp.set(wx, wy - 1, wz));
                        botOpen = !dn.canOcclude() || dn.getShape(level, mp).bounds().maxY < 1.0 - 1.0e-4;
                        mp.set(wx, wy, wz);
                    }
                    if (!topOpen && !botOpen) {
                        continue;
                    }
                    boolean coverLower = (wy + 0.5) < eCy;
                    boolean cutTop = coverLower ? topOpen : !botOpen;
                    boolean exNX = !bcell(ix - 1, iy, iz, dx, dy, dz);
                    boolean exPX = !bcell(ix + 1, iy, iz, dx, dy, dz);
                    boolean exNZ = !bcell(ix, iy, iz - 1, dx, dy, dz);
                    boolean exPZ = !bcell(ix, iy, iz + 1, dx, dy, dz);
                    if (!(exNX || exPX || exNZ || exPZ)) {
                        continue;
                    }
                    for (AABB a : vs.toAabbs()) {
                        if (blobs >= MAX_BLOB_FACES) {
                            break;
                        }
                        // emitted coords and falloff centre both de-biased so the in-shader distance matches the grid radius
                        double rbx = wx - sf.rpx, rby = wy - sf.rpy, rbz = wz - sf.rpz;
                        float aMinX = (float) (rbx + a.minX), aMaxX = (float) (rbx + a.maxX);
                        float aMinY = (float) (rby + a.minY), aMaxY = (float) (rby + a.maxY);
                        float aMinZ = (float) (rbz + a.minZ), aMaxZ = (float) (rbz + a.maxZ);
                        float cy = cutTop ? aMaxY : aMinY;
                        double cyGrid = wy + (cutTop ? a.maxY : a.minY);
                        double dgx = sf.camGx - eCx, dgy = sf.camGy - cyGrid, dgz = sf.camGz - eCz;
                        float rad = Mth.lerp(smootherstep((float) Mth.clamp(Math.sqrt(dgx * dgx + dgy * dgy + dgz * dgz) / BLOB_GROW_DIST, 0.0, 1.0)), BLOB_R_NEAR, BLOB_R_FAR);
                        float pcx = (float) (eCx - sf.rpx), pcz = (float) (eCz - sf.rpz);
                        blobFace3D(vc, 0, cy, aMinX, aMaxX, aMinZ, aMaxZ, pcx, cy, pcz, rad, c);
                        blobs++;
                        if (exNX) { blobFace3D(vc, 1, aMinX, aMinZ, aMaxZ, aMinY, aMaxY, pcx, cy, pcz, rad, c); blobs++; }
                        if (exPX) { blobFace3D(vc, 1, aMaxX, aMinZ, aMaxZ, aMinY, aMaxY, pcx, cy, pcz, rad, c); blobs++; }
                        if (exNZ) { blobFace3D(vc, 2, aMinZ, aMinX, aMaxX, aMinY, aMaxY, pcx, cy, pcz, rad, c); blobs++; }
                        if (exPZ) { blobFace3D(vc, 2, aMaxZ, aMinX, aMaxX, aMinY, aMaxY, pcx, cy, pcz, rad, c); blobs++; }
                    }
                    // a passable block (empty collision) shades the surface it's mounted on, not just itself
                    if (bs.getCollisionShape(level, mp).isEmpty()) {
                        for (Direction sd : Direction.values()) {
                            if (blobs >= MAX_BLOB_FACES) {
                                break;
                            }
                            boolean touch = switch (sd) {
                                case DOWN -> sb.minY <= 0.02;
                                case UP -> sb.maxY >= 0.98;
                                case WEST -> sb.minX <= 0.02;
                                case EAST -> sb.maxX >= 0.98;
                                case NORTH -> sb.minZ <= 0.02;
                                case SOUTH -> sb.maxZ >= 0.98;
                            };
                            if (!touch) {
                                continue;
                            }
                            if (!cachedState(level, mp.set(wx + sd.getStepX(), wy + sd.getStepY(), wz + sd.getStepZ())).canOcclude()) {
                                continue;
                            }
                            // centre kept in grid space (cc*) for the radius, de-biased only at blobFace3D
                            double rbx = wx - sf.rpx, rby = wy - sf.rpy, rbz = wz - sf.rpz;
                            int orient;
                            float fx, fa0, fa1, fb0, fb1;
                            double ccx, ccy, ccz;
                            switch (sd) {
                                case DOWN -> { orient = 0; fx = (float) rby;       fa0 = (float) rbx; fa1 = (float) (rbx + 1); fb0 = (float) rbz; fb1 = (float) (rbz + 1); ccx = eCx;    ccy = wy;     ccz = eCz;    }
                                case UP ->   { orient = 0; fx = (float) (rby + 1); fa0 = (float) rbx; fa1 = (float) (rbx + 1); fb0 = (float) rbz; fb1 = (float) (rbz + 1); ccx = eCx;    ccy = wy + 1; ccz = eCz;    }
                                case WEST -> { orient = 1; fx = (float) rbx;       fa0 = (float) rbz; fa1 = (float) (rbz + 1); fb0 = (float) rby; fb1 = (float) (rby + 1); ccx = wx;     ccy = eCy;    ccz = eCz;    }
                                case EAST -> { orient = 1; fx = (float) (rbx + 1); fa0 = (float) rbz; fa1 = (float) (rbz + 1); fb0 = (float) rby; fb1 = (float) (rby + 1); ccx = wx + 1; ccy = eCy;    ccz = eCz;    }
                                case NORTH -> { orient = 2; fx = (float) rbz;      fa0 = (float) rbx; fa1 = (float) (rbx + 1); fb0 = (float) rby; fb1 = (float) (rby + 1); ccx = eCx;    ccy = eCy;    ccz = wz;     }
                                default ->   { orient = 2; fx = (float) (rbz + 1); fa0 = (float) rbx; fa1 = (float) (rbx + 1); fb0 = (float) rby; fb1 = (float) (rby + 1); ccx = eCx;    ccy = eCy;    ccz = wz + 1; } // SOUTH
                            }
                            double dgx = sf.camGx - ccx, dgy = sf.camGy - ccy, dgz = sf.camGz - ccz;
                            float radb = Mth.lerp(smootherstep((float) Mth.clamp(Math.sqrt(dgx * dgx + dgy * dgy + dgz * dgz) / BLOB_GROW_DIST, 0.0, 1.0)), BLOB_R_NEAR, BLOB_R_FAR);
                            blobFace3D(vc, orient, fx, fa0, fa1, fb0, fb1, ccx - sf.rpx, ccy - sf.rpy, ccz - sf.rpz, radb, c);
                            blobs++;
                        }
                    }
                }
            }
        }
        FBO_BUFFER.endBatch();
    }

    private static void addBox(VertexConsumer vc, AABB box, double ox, double oy, double oz) {
        float x0 = (float) (box.minX + ox), y0 = (float) (box.minY + oy), z0 = (float) (box.minZ + oz);
        float x1 = (float) (box.maxX + ox), y1 = (float) (box.maxY + oy), z1 = (float) (box.maxZ + oz);
        vc.addVertex(x0, y0, z0); vc.addVertex(x1, y0, z0); vc.addVertex(x1, y0, z1); vc.addVertex(x0, y0, z1);
        vc.addVertex(x0, y1, z0); vc.addVertex(x0, y1, z1); vc.addVertex(x1, y1, z1); vc.addVertex(x1, y1, z0);
        vc.addVertex(x0, y0, z0); vc.addVertex(x0, y1, z0); vc.addVertex(x1, y1, z0); vc.addVertex(x1, y0, z0);
        vc.addVertex(x0, y0, z1); vc.addVertex(x1, y0, z1); vc.addVertex(x1, y1, z1); vc.addVertex(x0, y1, z1);
        vc.addVertex(x0, y0, z0); vc.addVertex(x0, y0, z1); vc.addVertex(x0, y1, z1); vc.addVertex(x0, y1, z0);
        vc.addVertex(x1, y0, z0); vc.addVertex(x1, y1, z0); vc.addVertex(x1, y1, z1); vc.addVertex(x1, y0, z1);
    }

    /** Alpha falls off from a shared 3D centre so the blob wraps seamlessly across adjacent faces.
     *  orient: 0 = horizontal (y fixed), 1 = X-face (x fixed), 2 = Z-face (z fixed). */
    private static void blobFace3D(VertexConsumer vc, int orient, float fixed,
                                   float a0, float a1, float b0, float b1,
                                   double px, double py, double pz, float r, float c) {
        int n = BLOB_TESS, w = n + 1;
        float[] al = BLOB_AL;
        for (int i = 0; i <= n; i++) {
            float a = Mth.lerp((float) i / n, a0, a1);
            for (int j = 0; j <= n; j++) {
                float b = Mth.lerp((float) j / n, b0, b1);
                double vx = orient == 1 ? fixed : a;
                double vy = orient == 0 ? fixed : b;
                double vz = orient == 2 ? fixed : (orient == 1 ? a : b);
                double ddx = vx - px, ddy = vy - py, ddz = vz - pz;
                float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                al[i * w + j] = BLOB_PEAK * (1.0f - smootherstep(Mth.clamp(dist / r, 0.0f, 1.0f)));
            }
        }
        for (int i = 0; i < n; i++) {
            float aA = Mth.lerp((float) i / n, a0, a1), aB = Mth.lerp((float) (i + 1) / n, a0, a1);
            for (int j = 0; j < n; j++) {
                float bA = Mth.lerp((float) j / n, b0, b1), bB = Mth.lerp((float) (j + 1) / n, b0, b1);
                float a00 = al[i * w + j], a10 = al[(i + 1) * w + j], a11 = al[(i + 1) * w + j + 1], a01 = al[i * w + j + 1];
                if (a00 + a10 + a11 + a01 < 0.004f) {
                    continue;
                }
                if (a00 + a11 > a10 + a01) { // flip the diagonal so the radial gradient has no triangle seam
                    emit3D(vc, orient, fixed, aB, bA, c, a10);
                    emit3D(vc, orient, fixed, aB, bB, c, a11);
                    emit3D(vc, orient, fixed, aA, bB, c, a01);
                    emit3D(vc, orient, fixed, aA, bA, c, a00);
                } else {
                    emit3D(vc, orient, fixed, aA, bA, c, a00);
                    emit3D(vc, orient, fixed, aB, bA, c, a10);
                    emit3D(vc, orient, fixed, aB, bB, c, a11);
                    emit3D(vc, orient, fixed, aA, bB, c, a01);
                }
            }
        }
    }

    private static void emit3D(VertexConsumer vc, int orient, float fixed, float a, float b, float c, float alpha) {
        float vx = orient == 1 ? fixed : a;
        float vy = orient == 0 ? fixed : b;
        float vz = orient == 2 ? fixed : (orient == 1 ? a : b);
        vc.addVertex(vx, vy, vz).setColor(c, c, c, alpha);
    }

    private static int borderIdx(int ix, int iy, int iz, int dy, int dz) {
        return (ix * dy + iy) * dz + iz;
    }

    private static boolean bcell(int ix, int iy, int iz, int dx, int dy, int dz) {
        if (ix < 0 || iy < 0 || iz < 0 || ix >= dx || iy >= dy || iz >= dz) {
            return false;
        }
        return BORDER_OCC[borderIdx(ix, iy, iz, dy, dz)];
    }

    private static float smootherstep(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }
}
