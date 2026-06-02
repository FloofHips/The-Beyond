package com.thebeyond.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.block.MirrorBlock;
import com.thebeyond.common.block.blockentities.MirrorBlockEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.common.registry.BeyondShaders;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-plane mirror reflection: mirrors are grouped by plane, the nearest {@link #MAX_PLANES} each captured into
 * an FBO (front entities reflected across the plane, drawn with the main camera's perspective) and sampled
 * projectively by that plane's faces — gl_FragCoord without a shader pack, per-corner screen UVs under one.
 */
public final class MirrorReflection {
    // The real limiter is the visible-face frustum cull in capture(); this is only a runaway guard.
    private static final int MAX_PLANES = 32;

    private static final int TINT_R = 202, TINT_G = 222, TINT_B = 234;
    private static final float ALPHA_NEAR = 0.9f;
    private static final float ALPHA_FAR = 0.3f;
    private static final int MAX_REFLECTED_ENTITIES = 16;
    // Keep each sub-quad small on screen so affine UVs ≈ projective.
    private static final int MAX_SUBDIV = 32;
    private static final float SUBDIV_TARGET_PX = 24.0f;

    private static final float[] LOD_FRACS = {1.0f, 0.5f, 0.25f, 0.125f};
    private static final double[] LOD_BANDS = {5.0, 6.5, 8.0}; // upper bound (blocks) of fracs 0,1,2
    private static final float RENDER_DIST = 11.0f;

    private static final double OCCLUDER_MARGIN = 2.5;
    private static final int MAX_OCCLUDER_BLOCKS = 512;

    private static final int BORDER_DIM = 32;          // max occluder-box side (cells)
    private static final boolean[] BORDER_OCC = new boolean[BORDER_DIM * BORDER_DIM * BORDER_DIM];
    private static final float BORDER_SHADE = 0.05f;
    private static final int BLOB_TESS = 8;
    private static final float[] BLOB_AL = new float[(BLOB_TESS + 1) * (BLOB_TESS + 1)]; // reused, render-thread only
    private static final float BLOB_PEAK = 0.27f;
    private static final float BLOB_R_NEAR = 1.4f;
    private static final float BLOB_R_FAR = 0.7f;      // smaller than near, so the blob grows on approach
    private static final float BLOB_GROW_DIST = 5.0f;
    private static final int MAX_BLOB_FACES = 600;
    private static final double NEAR_ENTITY = 0.85;    // shade only cells this close to the entity footprint (kills terrain scatter)
    private static final boolean DEBUG_SHADE = false;

    // LOS raycast is the costly part of capture; refresh the selection every few ticks, not every frame.
    private static final int ENTITY_REFRESH_TICKS = 4;

    private static Slot[] slots;
    private static boolean loggedBuildTag;
    private static boolean loggedShadowSkip;

    // Cached per state: CTM/dynamic models vary only texture, not these positions, so the cache stays correct.
    private static final java.util.Map<BlockState, float[]> OCCLUDER_MODEL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    // Frame-shared so each chunk is fetched once per frame, not once per ray. Cleared per frame; render-thread only.
    private static final Long2ObjectOpenHashMap<net.minecraft.world.level.chunk.LevelChunk> LOS_CHUNK_CACHE =
            new Long2ObjectOpenHashMap<>();
    // Frame-shared so the (possibly mod-intercepted) light lookup runs once per entity, not per entity per plane.
    private static final Object2IntOpenHashMap<Entity> LIGHT_CACHE = new Object2IntOpenHashMap<>();

    private MirrorReflection() {
    }

    public record PlaneKey(Direction facing, int coord) {
    }

    public static final class Slot {
        private final ResourceLocation texture;
        private final FboTexture fboTexture;
        private TextureTarget target;
        private boolean valid;
        private boolean packPath;
        private float fade;
        private final Matrix4f sampleVP = new Matrix4f();
        private PlaneKey key;
        private List<Entity> reflectedCache;
        private long reflectedTick = Long.MIN_VALUE;
        private PlaneKey reflectedCacheKey;         // the plane the cache was built for (guards slot reuse)
        private int heldBoost;

        private Slot(int index) {
            this.texture = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "mirror_reflection_" + index);
            this.fboTexture = new FboTexture();
        }

        public ResourceLocation texture() {
            return texture;
        }

        public boolean packPath() {
            return packPath;
        }

        public Matrix4f sampleVP() {
            return sampleVP;
        }
    }

    private static final class PlaneInfo {
        Vec3 normal;
        Vec3 point;
        double minDistSq;
        boolean inFrustum;

        PlaneInfo(Vec3 normal, Vec3 point, double minDistSq) {
            this.normal = normal;
            this.point = point;
            this.minDistSq = minDistSq;
        }
    }

    public static PlaneKey planeKey(BlockPos pos, Direction facing) {
        int coord = switch (facing) {
            case DOWN -> pos.getY();
            case UP -> pos.getY() + 1;
            case NORTH -> pos.getZ();
            case SOUTH -> pos.getZ() + 1;
            case WEST -> pos.getX();
            case EAST -> pos.getX() + 1;
        };
        return new PlaneKey(facing, coord);
    }

    public static Slot slotFor(PlaneKey key) {
        if (slots == null) {
            return null;
        }
        for (Slot s : slots) {
            if (s.valid && key.equals(s.key)) {
                return s;
            }
        }
        return null;
    }

    /** RenderLevelStageEvent at AFTER_SOLID_BLOCKS. */
    public static void capture(Matrix4f projIn, Matrix4f viewIn, Camera camera, Frustum frustum, float partialTick) {
        // Iris fires stage events during the shadow pass too, whose orthographic view degenerates the oblique clip (NaN).
        if (ShaderCompatLib.isShadowPass()) {
            if (!loggedShadowSkip) {
                loggedShadowSkip = true;
                TheBeyond.LOGGER.info("[TheBeyond] mirror: skipping reflection capture/draw during Iris shadow pass");
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LOS_CHUNK_CACHE.clear();
        LIGHT_CACHE.clear();
        if (slots == null) {
            slots = new Slot[MAX_PLANES];
            for (int i = 0; i < MAX_PLANES; i++) {
                slots[i] = new Slot(i);
            }
        }
        for (Slot s : slots) {
            s.valid = false;
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (!loggedBuildTag) {
            loggedBuildTag = true;
            TheBeyond.LOGGER.info("[TheBeyond] mirror reflection active — occluder=" + (com.thebeyond.BeyondConfig.MIRROR_OCCLUSION_MODEL_BASED.get() ? "model" : "AABB") + " (configurable), AABB shade incl. foliage; passable mount-support; door-shade, lightweight LOS");
        }

        double px = Mth.lerp(partialTick, player.xOld, player.getX());
        double py = Mth.lerp(partialTick, player.yOld, player.getY());
        double pz = Mth.lerp(partialTick, player.zOld, player.getZ());
        Vec3 pp = new Vec3(px, py, pz);

        Map<PlaneKey, PlaneInfo> planes = new HashMap<>();
        for (MirrorBlockEntity be : MirrorBlockEntity.LOADED) {
            if (be.isRemoved()) {
                continue;
            }
            BlockState st = be.getBlockState();
            if (!(st.getBlock() instanceof MirrorBlock)) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            boolean visible = frustum.isVisible(new AABB(pos).inflate(0.5));
            for (Direction f : MirrorBlock.reflectiveFaces(st)) {
                Vec3 normal = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
                Vec3 faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.5));
                double dsq = faceCenter.distanceToSqr(pp);
                PlaneKey key = planeKey(pos, f);
                PlaneInfo info = planes.get(key);
                if (info == null) {
                    info = new PlaneInfo(normal, faceCenter, dsq);
                    planes.put(key, info);
                } else if (dsq < info.minDistSq) {
                    info.minDistSq = dsq;
                    info.point = faceCenter;
                }
                if (visible) {
                    info.inFrustum = true;
                }
            }
        }
        if (planes.isEmpty()) {
            return;
        }

        List<Map.Entry<PlaneKey, PlaneInfo>> sorted = new ArrayList<>(planes.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> e.getValue().minDistSq));

        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) {
            return;
        }

        double renderDistSq = (double) RENDER_DIST * RENDER_DIST;
        Vec3 eyePos = player.getEyePosition(partialTick);
        List<Map.Entry<PlaneKey, PlaneInfo>> selected = new ArrayList<>();
        for (Map.Entry<PlaneKey, PlaneInfo> e : sorted) {
            if (selected.size() >= MAX_PLANES || e.getValue().minDistSq > renderDistSq) {
                break;
            }
            PlaneInfo info = e.getValue();
            if (!info.inFrustum) {
                continue;
            }
            // Player must be on the reflective (+normal) side.
            if (eyePos.subtract(info.point).dot(info.normal) <= 0.0) {
                continue;
            }
            // No plane-wide LOS test: per-block occlusion is the depth buffer's job in draw().
            selected.add(e);
        }
        if (selected.isEmpty()) {
            return;
        }

        // Keep each plane on last frame's slot, so an FBO only resizes when its plane crosses a LOD band.
        Slot[] assign = new Slot[selected.size()];
        boolean[] taken = new boolean[MAX_PLANES];
        for (int i = 0; i < selected.size(); i++) {
            PlaneKey k = selected.get(i).getKey();
            for (int j = 0; j < MAX_PLANES; j++) {
                if (!taken[j] && k.equals(slots[j].key)) {
                    assign[i] = slots[j];
                    taken[j] = true;
                    break;
                }
            }
        }
        for (int i = 0; i < selected.size(); i++) {
            if (assign[i] == null) {
                for (int j = 0; j < MAX_PLANES; j++) {
                    if (!taken[j]) {
                        assign[i] = slots[j];
                        taken[j] = true;
                        break;
                    }
                }
            }
        }

        Vec3 camPos = camera.getPosition();
        boolean packPath = ShaderCompatLib.isShaderPackActive();

        // No-pack path samples gl_FragCoord/ScreenSize — one global uniform, same for all planes.
        ShaderInstance mirrorShader = BeyondShaders.getMirror();
        if (mirrorShader != null) {
            mirrorShader.safeGetUniform("ScreenSize").set((float) w, (float) h);
        }

        RenderSystem.backupProjectionMatrix();
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        EntityRenderDispatcher disp = mc.getEntityRenderDispatcher();

        try {
            for (int i = 0; i < selected.size(); i++) {
                Slot slot = assign[i];
                if (slot == null) {
                    continue;
                }
                PlaneInfo info = selected.get(i).getValue();
                float frac = lodFracFor(Math.sqrt(info.minDistSq));
                int fw = Math.max(16, Math.round(w * frac));
                int fh = Math.max(16, Math.round(h * frac));
                // Per-plane try/catch so one failing plane invalidates only its own slot, not the whole frame.
                try {
                    ensureSized(slot, fw, fh, mc);
                    capturePlane(slot, selected.get(i).getKey(), info, packPath, mc, disp,
                            projIn, viewIn, camPos, partialTick);
                } catch (Throwable t) {
                    TheBeyond.LOGGER.error("Mirror reflection capture failed for one plane", t);
                    slot.valid = false;
                }
            }
        } finally {
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            main.bindWrite(true);
        }
    }

    private static void ensureSized(Slot slot, int fw, int fh, Minecraft mc) {
        if (slot.target == null) {
            slot.target = new TextureTarget(fw, fh, true, Minecraft.ON_OSX);
            slot.target.setFilterMode(GL11.GL_NEAREST);
            mc.getTextureManager().register(slot.texture, slot.fboTexture);
        } else if (slot.target.width != fw || slot.target.height != fh) {
            slot.target.resize(fw, fh, Minecraft.ON_OSX);
            slot.target.setFilterMode(GL11.GL_NEAREST);
        }
    }

    private static void capturePlane(Slot slot, PlaneKey key, PlaneInfo info, boolean packPath,
                                     Minecraft mc, EntityRenderDispatcher disp,
                                     Matrix4f projIn, Matrix4f viewIn, Vec3 camPos, float partialTick) {
        slot.packPath = packPath;
        slot.key = key;
        slot.fade = fadeFor(Math.sqrt(info.minDistSq));

        Vec3 n = info.normal;
        Vec3 p0rel = info.point.subtract(camPos);
        float dd = (float) -(n.x * p0rel.x + n.y * p0rel.y + n.z * p0rel.z);
        Matrix4f reflect = new Matrix4f().reflection((float) n.x, (float) n.y, (float) n.z, dd);
        Matrix4f proj = new Matrix4f(projIn);
        Matrix4f modelView = new Matrix4f(viewIn).mul(reflect);

        // Clip at the mirror plane, else the first-person player smears its underside across every mirror.
        applyObliqueNearClip(proj, viewIn, n, p0rel);

        if (packPath) {
            // sampleVP keeps the unclipped projIn so the face still samples the FBO at its true screen position.
            slot.sampleVP.set(projIn).mul(viewIn).translate(
                    (float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        }

        // Refresh every ENTITY_REFRESH_TICKS; keyed by plane so a reused slot never serves a stale plane's list.
        long nowTick = mc.level.getGameTime();
        if (slot.reflectedCache == null || !key.equals(slot.reflectedCacheKey)
                || nowTick - slot.reflectedTick >= ENTITY_REFRESH_TICKS) {
            slot.reflectedCache = collectReflectedEntities(mc, info, partialTick);
            slot.reflectedTick = nowTick;
            slot.reflectedCacheKey = key;
        } else {
            slot.reflectedCache.removeIf(Entity::isRemoved);
        }
        List<Entity> reflected = slot.reflectedCache;
        slot.heldBoost = packPath ? heldLightBoost(reflected, info.point, partialTick) : 0; // handheld light is a pack-only feature

        slot.target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        slot.target.clear(Minecraft.ON_OSX);
        slot.target.bindWrite(true);

        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.set(modelView);
        RenderSystem.applyModelViewMatrix();

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        renderOccluderDepth(buf, reflected, camPos, mc, info.normal, info.point);

        disp.setRenderShadow(false);
        // The reflection flips winding; invert the front face so culled parts don't render inside-out.
        GL11.glFrontFace(GL11.GL_CW);
        for (Entity e : reflected) {
            double ex = Mth.lerp(partialTick, e.xOld, e.getX());
            double ey = Mth.lerp(partialTick, e.yOld, e.getY());
            double ez = Mth.lerp(partialTick, e.zOld, e.getZ());
            float eyaw = Mth.rotLerp(partialTick, e.yRotO, e.getYRot());
            int light;
            if (LIGHT_CACHE.containsKey(e)) {
                light = LIGHT_CACHE.getInt(e);
            } else {
                light = disp.getPackedLightCoords(e, partialTick);
                LIGHT_CACHE.put(e, light);
            }
            disp.render(e, ex - camPos.x, ey - camPos.y, ez - camPos.z, eyaw, partialTick,
                    new PoseStack(), buf, light);
        }
        buf.endBatch();
        GL11.glFrontFace(GL11.GL_CCW);
        disp.setRenderShadow(true);

        // After the entities: depth-tested (VIEW_OFFSET wins coplanar) but not depth-written.
        renderOcclusionShade(buf, reflected, info.normal, info.point, camPos, mc);

        slot.target.unbindWrite();
        slot.fboTexture.setId(slot.target.getColorTextureId());
        slot.valid = true;
    }

    /** Depth-only prepass. Only FRONT-of-plane blocks qualify — a block behind the plane reflects to the wrong side. */
    public static void renderOccluderDepth(MultiBufferSource.BufferSource buf, List<Entity> reflected,
                                           Vec3 camPos, Minecraft mc, Vec3 n, Vec3 p0) {
        if (reflected.isEmpty()) {
            return;
        }
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double dMax = 0.0; // farthest entity extent along +normal — the slab's outer bound
        for (Entity e : reflected) {
            AABB b = e.getBoundingBox();
            minX = Math.min(minX, b.minX);
            minY = Math.min(minY, b.minY);
            minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX);
            maxY = Math.max(maxY, b.maxY);
            maxZ = Math.max(maxZ, b.maxZ);
            double far = n.x * ((n.x > 0 ? b.maxX : b.minX) - p0.x)
                    + n.y * ((n.y > 0 ? b.maxY : b.minY) - p0.y)
                    + n.z * ((n.z > 0 ? b.maxZ : b.minZ) - p0.z);
            dMax = Math.max(dMax, far);
        }
        var level = mc.level;
        VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.MIRROR_OCCLUDER);
        boolean modelBased = com.thebeyond.BeyondConfig.MIRROR_OCCLUSION_MODEL_BASED.get();
        BlockPos min = BlockPos.containing(minX - OCCLUDER_MARGIN, minY - OCCLUDER_MARGIN, minZ - OCCLUDER_MARGIN);
        BlockPos max = BlockPos.containing(maxX + OCCLUDER_MARGIN, maxY + OCCLUDER_MARGIN, maxZ + OCCLUDER_MARGIN);
        // MAX_OCCLUDER_BLOCKS caps rendered blocks, not iterated cells; bail before betweenClosed walks a huge box.
        if (max.getX() - min.getX() > BORDER_DIM || max.getY() - min.getY() > BORDER_DIM
                || max.getZ() - min.getZ() > BORDER_DIM) {
            return;
        }
        int rendered = 0;
        net.minecraft.world.level.chunk.LevelChunk chunk = null;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (rendered >= MAX_OCCLUDER_BLOCKS) {
                break;
            }
            // Front-of-plane slab only.
            double d = n.x * (pos.getX() + 0.5 - p0.x)
                    + n.y * (pos.getY() + 0.5 - p0.y)
                    + n.z * (pos.getZ() + 0.5 - p0.z);
            if (d <= 0.0 || d >= dMax) {
                continue;
            }
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
            if (chunk == null || cx != chunkX || cz != chunkZ) {
                chunk = level.getChunk(cx, cz);
                chunkX = cx;
                chunkZ = cz;
            }
            BlockState bs = chunk.getBlockState(pos);
            // Mirrors are noOcclusion but must occlude another plane's reflection, hence the explicit include.
            if (!(bs.getBlock() instanceof MirrorBlock) && !bs.canOcclude()) {
                continue;
            }
            if (modelBased) {
                float[] quads = occluderModelQuads(bs);
                if (quads.length == 0) {
                    continue;
                }
                float ox = (float) (pos.getX() - camPos.x);
                float oy = (float) (pos.getY() - camPos.y);
                float oz = (float) (pos.getZ() - camPos.z);
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
                double ox = pos.getX() - camPos.x;
                double oy = pos.getY() - camPos.y;
                double oz = pos.getZ() - camPos.z;
                for (AABB box : shape.toAabbs()) {
                    addBox(vc, box, ox, oy, oz);
                }
            }
            rendered++;
        }
        buf.endBatch(); // flush occluder depth before the entities so they depth-test against it
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

    /** Local-space quad positions of {@code state}'s model, cached per state (CTM/dynamic models vary texture not geometry). */
    public static float[] occluderModelQuads(BlockState state) {
        float[] cached = OCCLUDER_MODEL_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create();
        List<float[]> list = new ArrayList<>();
        int floats = 0;
        Direction[] dirs = Direction.values();
        for (int di = -1; di < dirs.length; di++) {
            Direction face = di < 0 ? null : dirs[di];
            rand.setSeed(42L);
            for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(state, face, rand)) {
                int[] v = q.getVertices();
                int stride = v.length / 4; // ints per vertex; position is the first 3
                float[] qp = new float[12];
                for (int k = 0; k < 4; k++) {
                    qp[k * 3] = Float.intBitsToFloat(v[k * stride]);
                    qp[k * 3 + 1] = Float.intBitsToFloat(v[k * stride + 1]);
                    qp[k * 3 + 2] = Float.intBitsToFloat(v[k * stride + 2]);
                }
                list.add(qp);
                floats += 12;
            }
        }
        float[] arr = new float[floats];
        int o = 0;
        for (float[] qp : list) {
            System.arraycopy(qp, 0, arr, o, 12);
            o += 12;
        }
        OCCLUDER_MODEL_CACHE.put(state, arr);
        return arr;
    }

    /** Call on resource reload — models are re-baked. */
    public static void clearModelCache() {
        OCCLUDER_MODEL_CACHE.clear();
    }

    /** getBlockState memoized per chunk; same result as level.getBlockState. */
    private static final class ChunkMemo {
        private net.minecraft.world.level.chunk.LevelChunk chunk;
        private int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;

        BlockState get(net.minecraft.world.level.Level level, BlockPos pos) {
            int ncx = pos.getX() >> 4, ncz = pos.getZ() >> 4;
            if (chunk == null || ncx != cx || ncz != cz) {
                chunk = level.getChunk(ncx, ncz);
                cx = ncx;
                cz = ncz;
            }
            return chunk.getBlockState(pos);
        }
    }

    /** Soft dark blob over the coverage-limit corner of each occluder. POSITION_COLOR with no shader, so Iris-safe. */
    public static void renderOcclusionShade(MultiBufferSource.BufferSource buf, List<Entity> reflected,
                                            Vec3 n, Vec3 p0, Vec3 camPos, Minecraft mc) {
        if (reflected.isEmpty()) {
            return;
        }
        var level = mc.level;
        if (level == null) {
            return;
        }
        ChunkMemo memo = new ChunkMemo();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double dMax = 0.0;
        for (Entity e : reflected) {
            AABB b = e.getBoundingBox();
            minX = Math.min(minX, b.minX); minY = Math.min(minY, b.minY); minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX); maxY = Math.max(maxY, b.maxY); maxZ = Math.max(maxZ, b.maxZ);
            double far = n.x * ((n.x > 0 ? b.maxX : b.minX) - p0.x)
                    + n.y * ((n.y > 0 ? b.maxY : b.minY) - p0.y)
                    + n.z * ((n.z > 0 ? b.maxZ : b.minZ) - p0.z);
            dMax = Math.max(dMax, far);
        }
        int bx = Mth.floor(minX - OCCLUDER_MARGIN), by = Mth.floor(minY - OCCLUDER_MARGIN), bz = Mth.floor(minZ - OCCLUDER_MARGIN);
        int dx = Mth.floor(maxX + OCCLUDER_MARGIN) - bx + 1;
        int dy = Mth.floor(maxY + OCCLUDER_MARGIN) - by + 1;
        int dz = Mth.floor(maxZ + OCCLUDER_MARGIN) - bz + 1;
        if (dx < 1 || dy < 1 || dz < 1 || dx > BORDER_DIM || dy > BORDER_DIM || dz > BORDER_DIM) {
            return;
        }
        java.util.Arrays.fill(BORDER_OCC, 0, dx * dy * dz, false);
        boolean any = false;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < dx; ix++) {
            for (int iz = 0; iz < dz; iz++) {
                for (int iy = 0; iy < dy; iy++) {
                    int wx = bx + ix, wy = by + iy, wz = bz + iz;
                    double d = n.x * (wx + 0.5 - p0.x) + n.y * (wy + 0.5 - p0.y) + n.z * (wz + 0.5 - p0.z);
                    if (d <= 0.0 || d >= dMax) {
                        continue; // outside the body→plane slab
                    }
                    mp.set(wx, wy, wz);
                    BlockState bs = memo.get(level, mp);
                    if (bs.getBlock() instanceof MirrorBlock || bs.isAir()) {
                        continue;
                    }
                    // Shade any block with a shape, even non-occluding (leaves) — this is depth cueing, not occlusion.
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
        VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.MIRROR_OUTLINE);
        float c = BORDER_SHADE;
        int blobs = 0;
        for (int ix = 0; ix < dx; ix++) {
            for (int iy = 0; iy < dy; iy++) {
                for (int iz = 0; iz < dz; iz++) {
                    if (!bcell(ix, iy, iz, dx, dy, dz) || blobs >= MAX_BLOB_FACES) {
                        continue;
                    }
                    int wx = bx + ix, wy = by + iy, wz = bz + iz;
                    // The nearest reflected entity decides which edge is the coverage limit.
                    Entity ne = null;
                    double best = Double.MAX_VALUE;
                    for (Entity e : reflected) {
                        double dd = e.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                        if (dd < best) {
                            best = dd;
                            ne = e;
                        }
                    }
                    if (ne == null) {
                        continue;
                    }
                    AABB eb = ne.getBoundingBox();
                    double hcx = wx + 0.5, hcz = wz + 0.5;
                    double hdx = Math.max(Math.max(eb.minX - hcx, hcx - eb.maxX), 0.0);
                    double hdz = Math.max(Math.max(eb.minZ - hcz, hcz - eb.maxZ), 0.0);
                    if (hdx * hdx + hdz * hdz > NEAR_ENTITY * NEAR_ENTITY) {
                        continue; // not under/around the entity — don't shade distant terrain
                    }
                    double eCy = (eb.minY + eb.maxY) * 0.5, eCx = (eb.minX + eb.maxX) * 0.5, eCz = (eb.minZ + eb.maxZ) * 0.5;
                    mp.set(wx, wy, wz);
                    BlockState bs = memo.get(level, mp);
                    VoxelShape vs = bs.getShape(level, mp);
                    AABB sb = vs.bounds();
                    float sMinX = wx + (float) sb.minX, sMaxX = wx + (float) sb.maxX;
                    float sMinY = wy + (float) sb.minY, sMaxY = wy + (float) sb.maxY;
                    float sMinZ = wz + (float) sb.minZ, sMaxZ = wz + (float) sb.maxZ;
                    // Shape-aware open test: air within a cell (above a bottom-slab, below a top-slab) counts as open.
                    boolean topOpen;
                    if (sb.maxY < 1.0 - 1.0e-4) {
                        topOpen = true;
                    } else {
                        BlockState up = memo.get(level, mp.set(wx, wy + 1, wz));
                        topOpen = !up.canOcclude() || up.getShape(level, mp).bounds().minY > 1.0e-4;
                        mp.set(wx, wy, wz);
                    }
                    boolean botOpen;
                    if (sb.minY > 1.0e-4) {
                        botOpen = true;
                    } else {
                        BlockState dn = memo.get(level, mp.set(wx, wy - 1, wz));
                        botOpen = !dn.canOcclude() || dn.getShape(level, mp).bounds().maxY < 1.0 - 1.0e-4;
                        mp.set(wx, wy, wz);
                    }
                    if (!topOpen && !botOpen) {
                        continue; // interior cell
                    }
                    // Cut on the side facing the entity's visible half when that side is exposed; else the other.
                    boolean coverLower = (wy + 0.5) < eCy;
                    boolean cutTop = coverLower ? topOpen : !botOpen;
                    float cornerY = cutTop ? sMaxY : sMinY;
                    float cZ = (eCz < wz + 0.5) ? sMinZ : sMaxZ;
                    float cX = (eCx < wx + 0.5) ? sMinX : sMaxX;
                    if (DEBUG_SHADE) {
                        if (!bcell(ix - 1, iy, iz, dx, dy, dz)) debugVFace(vc, true, wx, wz, wz + 1, wy, wy + 1, camPos, 0f, 0.8f, 0.9f);
                        if (!bcell(ix + 1, iy, iz, dx, dy, dz)) debugVFace(vc, true, wx + 1, wz, wz + 1, wy, wy + 1, camPos, 0f, 0.8f, 0.9f);
                        if (!bcell(ix, iy, iz - 1, dx, dy, dz)) debugVFace(vc, false, wz, wx, wx + 1, wy, wy + 1, camPos, 0f, 0.8f, 0.9f);
                        if (!bcell(ix, iy, iz + 1, dx, dy, dz)) debugVFace(vc, false, wz + 1, wx, wx + 1, wy, wy + 1, camPos, 0f, 0.8f, 0.9f);
                        if (!bcell(ix, iy + 1, iz, dx, dy, dz)) debugHFace(vc, wy + 1, wx, wx + 1, wz, wz + 1, camPos, 1f, 0.9f, 0f);
                        if (!bcell(ix, iy - 1, iz, dx, dy, dz)) debugHFace(vc, wy, wx, wx + 1, wz, wz + 1, camPos, 1f, 0.1f, 0.1f);
                        debugMark(vc, cX, cornerY, cZ, camPos);
                        continue;
                    }
                    boolean exNX = !bcell(ix - 1, iy, iz, dx, dy, dz);
                    boolean exPX = !bcell(ix + 1, iy, iz, dx, dy, dz);
                    boolean exNZ = !bcell(ix, iy, iz - 1, dx, dy, dz);
                    boolean exPZ = !bcell(ix, iy, iz + 1, dx, dy, dz);
                    if (!(exNX || exPX || exNZ || exPZ)) {
                        continue; // interior face
                    }
                    // Falloff centred on the entity, shared by every cell picking it, so the blob is one seamless shape.
                    for (AABB a : vs.toAabbs()) {
                        if (blobs >= MAX_BLOB_FACES) {
                            break;
                        }
                        float aMinX = wx + (float) a.minX, aMaxX = wx + (float) a.maxX;
                        float aMinY = wy + (float) a.minY, aMaxY = wy + (float) a.maxY;
                        float aMinZ = wz + (float) a.minZ, aMaxZ = wz + (float) a.maxZ;
                        float cy = cutTop ? aMaxY : aMinY;
                        double distCam = camPos.distanceTo(new Vec3(eCx, cy, eCz));
                        float rad = Mth.lerp(smootherstep((float) Mth.clamp(distCam / BLOB_GROW_DIST, 0.0, 1.0)), BLOB_R_NEAR, BLOB_R_FAR);
                        blobFace3D(vc, 0, cy, aMinX, aMaxX, aMinZ, aMaxZ, eCx, cy, eCz, rad, camPos, c); blobs++;
                        if (exNX) { blobFace3D(vc, 1, aMinX, aMinZ, aMaxZ, aMinY, aMaxY, eCx, cy, eCz, rad, camPos, c); blobs++; }
                        if (exPX) { blobFace3D(vc, 1, aMaxX, aMinZ, aMaxZ, aMinY, aMaxY, eCx, cy, eCz, rad, camPos, c); blobs++; }
                        if (exNZ) { blobFace3D(vc, 2, aMinZ, aMinX, aMaxX, aMinY, aMaxY, eCx, cy, eCz, rad, camPos, c); blobs++; }
                        if (exPZ) { blobFace3D(vc, 2, aMaxZ, aMinX, aMaxX, aMinY, aMaxY, eCx, cy, eCz, rad, camPos, c); blobs++; }
                    }
                    // A passable block (button, torch, plant) shades the surface it's mounted on, not just itself.
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
                            if (!memo.get(level, mp.set(wx + sd.getStepX(), wy + sd.getStepY(), wz + sd.getStepZ())).canOcclude()) {
                                continue; // not a solid surface to shade
                            }
                            int orient;
                            float fx, fa0, fa1, fb0, fb1;
                            double ccx, ccy, ccz;
                            switch (sd) {
                                case DOWN -> { orient = 0; fx = wy;     fa0 = wx; fa1 = wx + 1; fb0 = wz; fb1 = wz + 1; ccx = eCx;     ccy = wy;     ccz = eCz;     }
                                case UP ->   { orient = 0; fx = wy + 1; fa0 = wx; fa1 = wx + 1; fb0 = wz; fb1 = wz + 1; ccx = eCx;     ccy = wy + 1; ccz = eCz;     }
                                case WEST -> { orient = 1; fx = wx;     fa0 = wz; fa1 = wz + 1; fb0 = wy; fb1 = wy + 1; ccx = wx;      ccy = eCy;    ccz = eCz;     }
                                case EAST -> { orient = 1; fx = wx + 1; fa0 = wz; fa1 = wz + 1; fb0 = wy; fb1 = wy + 1; ccx = wx + 1;  ccy = eCy;    ccz = eCz;     }
                                case NORTH -> { orient = 2; fx = wz;    fa0 = wx; fa1 = wx + 1; fb0 = wy; fb1 = wy + 1; ccx = eCx;     ccy = eCy;    ccz = wz;      }
                                default ->   { orient = 2; fx = wz + 1; fa0 = wx; fa1 = wx + 1; fb0 = wy; fb1 = wy + 1; ccx = eCx;     ccy = eCy;    ccz = wz + 1;  } // SOUTH
                            }
                            double dcb = camPos.distanceTo(new Vec3(ccx, ccy, ccz));
                            float radb = Mth.lerp(smootherstep((float) Mth.clamp(dcb / BLOB_GROW_DIST, 0.0, 1.0)), BLOB_R_NEAR, BLOB_R_FAR);
                            blobFace3D(vc, orient, fx, fa0, fa1, fb0, fb1, ccx, ccy, ccz, radb, camPos, c);
                            blobs++;
                        }
                    }
                }
            }
        }
        buf.endBatch();
    }

    private static int borderIdx(int ix, int iy, int iz, int dy, int dz) {
        return (ix * dy + iy) * dz + iz;
    }

    /** Out-of-box is treated as air. */
    private static boolean bcell(int ix, int iy, int iz, int dx, int dy, int dz) {
        if (ix < 0 || iy < 0 || iz < 0 || ix >= dx || iy >= dy || iz >= dz) {
            return false;
        }
        return BORDER_OCC[borderIdx(ix, iy, iz, dy, dz)];
    }

    /** {@code orient}: 0 = horizontal (fixed y), 1 = X-face (fixed x), 2 = Z-face (fixed z); falloff from the shared centre {@code px,py,pz} so it wraps across faces. */
    private static void blobFace3D(VertexConsumer vc, int orient, float fixed,
                                   float a0, float a1, float b0, float b1,
                                   double px, double py, double pz, float r, Vec3 camPos, float c) {
        int n = BLOB_TESS, w = n + 1;
        float[] al = BLOB_AL;
        for (int i = 0; i <= n; i++) {
            float a = Mth.lerp((float) i / n, a0, a1);
            for (int j = 0; j <= n; j++) {
                float b = Mth.lerp((float) j / n, b0, b1);
                double vx = orient == 1 ? fixed : a;
                double vy = orient == 0 ? fixed : b;
                double vz = orient == 2 ? fixed : (orient == 1 ? a : b);
                double dx = vx - px, dy = vy - py, dz = vz - pz;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                al[i * w + j] = BLOB_PEAK * (1.0f - smootherstep(Mth.clamp(dist / r, 0.0f, 1.0f)));
            }
        }
        float ox = (float) camPos.x, oy = (float) camPos.y, oz = (float) camPos.z;
        for (int i = 0; i < n; i++) {
            float aA = Mth.lerp((float) i / n, a0, a1), aB = Mth.lerp((float) (i + 1) / n, a0, a1);
            for (int j = 0; j < n; j++) {
                float bA = Mth.lerp((float) j / n, b0, b1), bB = Mth.lerp((float) (j + 1) / n, b0, b1);
                float a00 = al[i * w + j], a10 = al[(i + 1) * w + j], a11 = al[(i + 1) * w + j + 1], a01 = al[i * w + j + 1];
                if (a00 + a10 + a11 + a01 < 0.004f) {
                    continue; // fully transparent
                }
                if (a00 + a11 > a10 + a01) { // flip the diagonal so the radial gradient has no triangle seam
                    emit3D(vc, orient, fixed, aB, bA, ox, oy, oz, c, a10);
                    emit3D(vc, orient, fixed, aB, bB, ox, oy, oz, c, a11);
                    emit3D(vc, orient, fixed, aA, bB, ox, oy, oz, c, a01);
                    emit3D(vc, orient, fixed, aA, bA, ox, oy, oz, c, a00);
                } else {
                    emit3D(vc, orient, fixed, aA, bA, ox, oy, oz, c, a00);
                    emit3D(vc, orient, fixed, aB, bA, ox, oy, oz, c, a10);
                    emit3D(vc, orient, fixed, aB, bB, ox, oy, oz, c, a11);
                    emit3D(vc, orient, fixed, aA, bB, ox, oy, oz, c, a01);
                }
            }
        }
    }

    private static void emit3D(VertexConsumer vc, int orient, float fixed, float a, float b,
                               float ox, float oy, float oz, float c, float alpha) {
        float vx = orient == 1 ? fixed : a;
        float vy = orient == 0 ? fixed : b;
        float vz = orient == 2 ? fixed : (orient == 1 ? a : b);
        vc.addVertex(vx - ox, vy - oy, vz - oz).setColor(c, c, c, alpha);
    }

    /** C² smootherstep — zero end derivatives, so the coarse tessellated gradient has no Mach-band ridges. */
    private static float smootherstep(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    // ---- Debug overlay (gated by DEBUG_SHADE) ----
    private static void debugV(VertexConsumer vc, float x, float y, float z, Vec3 camPos,
                               float r, float g, float b, float a) {
        vc.addVertex(x - (float) camPos.x, y - (float) camPos.y, z - (float) camPos.z).setColor(r, g, b, a);
    }

    private static void debugVFace(VertexConsumer vc, boolean isXFace, float fixed,
                                   float o0, float o1, float y0, float y1, Vec3 camPos,
                                   float r, float g, float b) {
        float a = 0.30f;
        debugV(vc, isXFace ? fixed : o0, y0, isXFace ? o0 : fixed, camPos, r, g, b, a);
        debugV(vc, isXFace ? fixed : o1, y0, isXFace ? o1 : fixed, camPos, r, g, b, a);
        debugV(vc, isXFace ? fixed : o1, y1, isXFace ? o1 : fixed, camPos, r, g, b, a);
        debugV(vc, isXFace ? fixed : o0, y1, isXFace ? o0 : fixed, camPos, r, g, b, a);
    }

    private static void debugHFace(VertexConsumer vc, float y, float x0, float x1, float z0, float z1,
                                   Vec3 camPos, float r, float g, float b) {
        float a = 0.30f;
        debugV(vc, x0, y, z0, camPos, r, g, b, a);
        debugV(vc, x1, y, z0, camPos, r, g, b, a);
        debugV(vc, x1, y, z1, camPos, r, g, b, a);
        debugV(vc, x0, y, z1, camPos, r, g, b, a);
    }

    private static void debugMark(VertexConsumer vc, float x, float y, float z, Vec3 camPos) {
        float s = 0.12f;
        debugV(vc, x - s, y - s, z, camPos, 1.0f, 0.0f, 1.0f, 0.95f);
        debugV(vc, x + s, y - s, z, camPos, 1.0f, 0.0f, 1.0f, 0.95f);
        debugV(vc, x + s, y + s, z, camPos, 1.0f, 0.0f, 1.0f, 0.95f);
        debugV(vc, x - s, y + s, z, camPos, 1.0f, 0.0f, 1.0f, 0.95f);
    }

    /** Brightest BlockItem light-emission held by the entity, matching how a shader pack derives handheld light. */
    public static int heldLightEmission(Entity e) {
        if (!(e instanceof LivingEntity le)) {
            return 0;
        }
        int max = 0;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = le.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem bi) {
                int em = bi.getBlock().defaultBlockState().getLightEmission();
                if (em > max) {
                    max = em;
                }
            }
        }
        return max;
    }

    /** Brightest reflected holder's emission, attenuated one level per block to the face; a pack's handheld light never reaches our static lightmap. */
    public static int heldLightBoost(List<Entity> reflected, Vec3 facePoint, float partialTick) {
        int boost = 0;
        for (Entity e : reflected) {
            int em = heldLightEmission(e);
            if (em <= 0) {
                continue;
            }
            double ex = Mth.lerp(partialTick, e.xOld, e.getX());
            double ey = Mth.lerp(partialTick, e.yOld, e.getY());
            double ez = Mth.lerp(partialTick, e.zOld, e.getZ());
            int contrib = em - (int) Math.floor(Math.sqrt(facePoint.distanceToSqr(ex, ey, ez)));
            if (contrib > boost) {
                boost = contrib;
            }
        }
        return boost;
    }

    /** LOS test is essential — the FBO holds no world geometry, so without it an entity behind a wall shows through. */
    private static List<Entity> collectReflectedEntities(Minecraft mc, PlaneInfo info, float partialTick) {
        double r = RENDER_DIST;
        AABB box = new AABB(info.point.x - r, info.point.y - r, info.point.z - r,
                info.point.x + r, info.point.y + r, info.point.z + r);
        List<Entity> front = new ArrayList<>();
        for (Entity e : mc.level.getEntities((Entity) null, box, ent -> !ent.isSpectator())) {
            // Eye, not feet: a body on a floor mirror sits exactly on the plane (dot == 0) and would be excluded.
            if (e.getEyePosition(partialTick).subtract(info.point).dot(info.normal) <= 0.0) {
                continue;
            }
            front.add(e);
        }
        front.sort(Comparator.comparingDouble(e -> e.distanceToSqr(info.point.x, info.point.y, info.point.z)));

        // Bounded by a check budget then the entity cap, so a crowd of occluded mobs can't blow up the cost.
        List<Entity> visible = new ArrayList<>();
        int budget = Math.min(front.size(), MAX_REFLECTED_ENTITIES * 3);
        for (int i = 0; i < budget && visible.size() < MAX_REFLECTED_ENTITIES; i++) {
            Entity e = front.get(i);
            if (hasAnyLineOfSight(e, info, partialTick)) {
                visible.add(e);
            }
        }
        return visible;
    }

    /** Samples several heights, not just the eye, so a block covering one part (e.g. the head) doesn't cull the whole body. */
    private static boolean hasAnyLineOfSight(Entity e, PlaneInfo info, float partialTick) {
        double ex = Mth.lerp(partialTick, e.xOld, e.getX());
        double ey = Mth.lerp(partialTick, e.yOld, e.getY());
        double ez = Mth.lerp(partialTick, e.zOld, e.getZ());
        double h = e.getBbHeight();
        // Surface point at the mirror's own height; the perpendicular foot alone culls short bodies below a mirror.
        Vec3 surface = info.point.add(info.normal.scale(0.1));
        for (double f : new double[]{0.1, 0.4, 0.7, 1.0}) {
            Vec3 from = new Vec3(ex, ey + h * f, ez);
            double dd = from.subtract(info.point).dot(info.normal);
            if (dd <= 0.0) {
                continue; // behind the plane
            }
            Vec3 foot = from.subtract(info.normal.scale(dd)).add(info.normal.scale(0.1));
            if (hasLineOfSight(e, from, foot) || hasLineOfSight(e, from, surface)) {
                return true;
            }
        }
        return false;
    }

    /** Voxel walk, not {@link net.minecraft.world.level.BlockGetter#clip}, to sidestep mods that wrap clip(); mirrors are transparent. */
    private static boolean hasLineOfSight(Entity e, Vec3 eye, Vec3 target) {
        var level = e.level();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-4) {
            return true;
        }
        // An entity that changed reference frame this frame has xOld a grid away; don't march millions of steps.
        if (dist > RENDER_DIST * 2.0) {
            return false;
        }
        double inv = 1.0 / dist;
        double sx = dx * inv, sy = dy * inv, sz = dz * inv;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long lastEnc = Long.MIN_VALUE;
        int steps = (int) Math.ceil(dist / 0.5);
        // The local chunk var is an L1 that skips the LOS_CHUNK_CACHE map lookup while the ray stays in one chunk.
        net.minecraft.world.level.chunk.LevelChunk chunk = null;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = Math.min(i * 0.5, dist);
            mp.set(Mth.floor(eye.x + sx * t), Mth.floor(eye.y + sy * t), Mth.floor(eye.z + sz * t));
            long enc = mp.asLong();
            if (enc == lastEnc) {
                continue;
            }
            lastEnc = enc;
            int cx = mp.getX() >> 4, cz = mp.getZ() >> 4;
            if (chunk == null || cx != chunkX || cz != chunkZ) {
                long ckey = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                chunk = LOS_CHUNK_CACHE.get(ckey);
                if (chunk == null) {
                    chunk = level.getChunk(cx, cz);
                    LOS_CHUNK_CACHE.put(ckey, chunk);
                }
                chunkX = cx;
                chunkZ = cz;
            }
            BlockState st = chunk.getBlockState(mp);
            if (st.getBlock() instanceof MirrorBlock) {
                continue; // mirrors are transparent to another plane's reflection
            }
            if (st.canOcclude() && st.isCollisionShapeFullBlock(level, mp)) {
                return false; // only a full opaque cube culls; partial blocks pass, the depth occluder covers them
            }
        }
        return true;
    }

    private static float fadeFor(double dist) {
        float t = (float) Mth.clamp(dist / RENDER_DIST, 0.0, 1.0);
        return Mth.lerp(t, ALPHA_NEAR, ALPHA_FAR);
    }

    /** RenderLevelStageEvent at AFTER_TRANSLUCENT_BLOCKS: draws each captured face over the pearl block. */
    public static void draw(Camera camera, PoseStack poseStack, float partialTick) {
        if (ShaderCompatLib.isShadowPass()) {
            return; // see capture()
        }
        if (slots == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderTarget rt = mc.getMainRenderTarget();

        for (MirrorBlockEntity be : MirrorBlockEntity.LOADED) {
            if (be.isRemoved()) {
                continue;
            }
            BlockState st = be.getBlockState();
            if (!(st.getBlock() instanceof MirrorBlock)) {
                continue;
            }
            List<Direction> faces = MirrorBlock.reflectiveFaces(st);
            if (faces.isEmpty()) {
                continue;
            }
            BlockPos bp = be.getBlockPos();

            poseStack.pushPose();
            poseStack.translate(bp.getX() - camPos.x, bp.getY() - camPos.y, bp.getZ() - camPos.z);
            PoseStack.Pose last = poseStack.last();
            Matrix4f pose = last.pose();

            for (Direction facing : faces) {
                Slot slot = slotFor(planeKey(bp, facing));
                if (slot == null) {
                    continue;
                }
                int alpha = (int) (Mth.clamp(slot.fade, 0.0f, 1.0f) * 255.0f);
                float[][] corners = faceQuad(facing, 0.002f);

                if (slot.packPath()) {
                    VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.mirrorPack(slot.texture()));
                    // The FBO holds only unlit albedo under a pack, so light the face with the world light in front of it.
                    int ambient = LevelRenderer.getLightColor(mc.level, bp.relative(facing));
                    if (slot.heldBoost > 0) {
                        ambient = (Math.max((ambient >> 4) & 0xF, slot.heldBoost) << 4) | (((ambient >> 20) & 0xF) << 20);
                    }
                    Matrix4f vp = slot.sampleVP();
                    int n = chooseSubdiv(vp, bp, corners, rt.width, rt.height);
                    for (int i = 0; i < n; i++) {
                        for (int j = 0; j < n; j++) {
                            float u0 = (float) i / n, u1 = (float) (i + 1) / n;
                            float v0 = (float) j / n, v1 = (float) (j + 1) / n;
                            addSubVertex(vc, pose, last, facing, corners, bp, vp, alpha, ambient, u0, v0);
                            addSubVertex(vc, pose, last, facing, corners, bp, vp, alpha, ambient, u1, v0);
                            addSubVertex(vc, pose, last, facing, corners, bp, vp, alpha, ambient, u1, v1);
                            addSubVertex(vc, pose, last, facing, corners, bp, vp, alpha, ambient, u0, v1);
                        }
                    }
                } else {
                    VertexConsumer vc = buf.getBuffer(BeyondRenderTypes.mirror(slot.texture()));
                    for (float[] c : corners) {
                        vc.addVertex(pose, c[0], c[1], c[2]).setColor(TINT_R, TINT_G, TINT_B, alpha);
                    }
                }
            }
            poseStack.popPose();
        }
        buf.endBatch();
    }

    private static final Vector4f SCRATCH_V4 = new Vector4f(); // render-thread only, set then read within one call

    /** Grid resolution keeping each sub-quad ~{@link #SUBDIV_TARGET_PX}px so affine UVs stay ≈ projective. */
    private static int chooseSubdiv(Matrix4f vp, BlockPos bp, float[][] corners, int sw, int sh) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] c : corners) {
            Vector4f v = SCRATCH_V4.set(bp.getX() + c[0], bp.getY() + c[1], bp.getZ() + c[2], 1.0f);
            vp.transform(v);
            if (v.w < 1.0e-4f) {
                return MAX_SUBDIV;
            }
            float x = (v.x / v.w * 0.5f + 0.5f) * sw;
            float y = (v.y / v.w * 0.5f + 0.5f) * sh;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        return Mth.clamp((int) Math.ceil(extent / SUBDIV_TARGET_PX), 1, MAX_SUBDIV);
    }

    private static void addSubVertex(VertexConsumer vc, Matrix4f pose, PoseStack.Pose last, Direction facing,
                                     float[][] corners, BlockPos bp, Matrix4f vp, int alpha, int light, float u, float v) {
        float x = bilerp(corners[0][0], corners[1][0], corners[2][0], corners[3][0], u, v);
        float y = bilerp(corners[0][1], corners[1][1], corners[2][1], corners[3][1], u, v);
        float z = bilerp(corners[0][2], corners[1][2], corners[2][2], corners[3][2], u, v);
        SCRATCH_V4.set(bp.getX() + x, bp.getY() + y, bp.getZ() + z, 1.0f);
        vp.transform(SCRATCH_V4);
        float iw = (Math.abs(SCRATCH_V4.w) < 1.0e-5f) ? 0.0f : 1.0f / SCRATCH_V4.w;
        vc.addVertex(pose, x, y, z)
                .setColor(TINT_R, TINT_G, TINT_B, alpha)
                .setUv(SCRATCH_V4.x * iw * 0.5f + 0.5f, SCRATCH_V4.y * iw * 0.5f + 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(last, facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private static float bilerp(float c0, float c1, float c2, float c3, float u, float v) {
        float bottom = c0 + (c1 - c0) * u;
        float top = c3 + (c2 - c3) * u;
        return bottom + (top - bottom) * v;
    }


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

    private static float lodFracFor(double dist) {
        if (dist <= LOD_BANDS[0]) {
            return LOD_FRACS[0];
        }
        if (dist <= LOD_BANDS[1]) {
            return LOD_FRACS[1];
        }
        if (dist <= LOD_BANDS[2]) {
            return LOD_FRACS[2];
        }
        return LOD_FRACS[3];
    }

    /**
     * Lengyel oblique near-plane clip: rewrites {@code proj}'s near plane to the mirror plane, drives the
     * hardware clip (no shader, holds under a pack), keeping the half-space behind the mirror. Assumes GL z in [-1,1].
     */
    public static void applyObliqueNearClip(Matrix4f proj, Matrix4f viewIn, Vec3 n, Vec3 p0rel) {
        // Eye space: scene is camera-relative; normal points away from the kept side, so the camera is on w < 0.
        Vector4f nEye = new Vector4f((float) n.x, (float) n.y, (float) n.z, 0.0f);
        viewIn.transform(nEye); // w = 0 → direction only
        float cx = -nEye.x, cy = -nEye.y, cz = -nEye.z;
        float cw = (float) (n.x * p0rel.x + n.y * p0rel.y + n.z * p0rel.z);
        // Q: clip-space corner most opposite the clip plane.
        float qx = (sgn(cx) + proj.m20()) / proj.m00();
        float qy = (sgn(cy) + proj.m21()) / proj.m11();
        float qw = (1.0f + proj.m22()) / proj.m32();
        float denom = cx * qx + cy * qy - cz + cw * qw; // qz = −1
        float s = 2.0f / denom;
        // Degenerate (camera on/grazing the plane) → s non-finite; keep plain proj so the reflection survives uncut.
        if (!Float.isFinite(qx) || !Float.isFinite(qy) || !Float.isFinite(qw)
                || !Float.isFinite(s) || Math.abs(denom) < 1.0e-4f) {
            return;
        }
        proj.m02(cx * s);
        proj.m12(cy * s);
        proj.m22(cz * s + 1.0f);
        proj.m32(cw * s);
    }

    private static float sgn(float a) {
        return a > 0.0f ? 1.0f : (a < 0.0f ? -1.0f : 0.0f);
    }
}
