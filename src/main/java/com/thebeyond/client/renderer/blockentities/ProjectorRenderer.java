package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.block.ProjectorBlock;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.camera.SnapshotGrade;
import com.thebeyond.common.data.BeyondDataMapTypes;
import com.thebeyond.common.data.ProjectorTexture;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.common.registry.BeyondShaders;
import com.thebeyond.client.renderer.ItemIconTextures;
import com.thebeyond.TheBeyond;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;

import java.util.*;

import static com.thebeyond.client.renderer.blockentities.ProjectorTunables.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.phys.BlockHitResult;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.world.level.block.TransparentBlock;

/**
 * Casts a ray from the lens along the facing; the image lands on the first surface hit, scaled by throw distance. Works
 * in the host world and inside Sable sub-levels (the BER PoseStack carries the contraption frame).
 */
public class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {
    /** Null off the Sable path. */
    public static volatile ProjectorClipFn subLevelClip;

    @FunctionalInterface
    public interface ProjectorClipFn {
        double clip(Level level, BlockPos pos, Vec3 eye, Vec3 forward, double maxThrow);
    }

    /** Sable raw cross-frame clip; plain {@code level.clip} is rewritten by Sable to project rays between frames. Null -> plain clip. */
    public static volatile ProjectorOccFn meshOcc;

    /** Returns 1.0 = visible, 0.0 = occluded. */
    @FunctionalInterface
    public interface ProjectorOccFn {
        double occ(Level level, Vec3 eye, Vec3 p, BlockPos self);
    }

    /** Is the projector at {@code pos} on a contraption this frame? Host-frame projectors keep the deferred path. */
    public static volatile ProjectorOnContraptionFn onContraption;

    @FunctionalInterface
    public interface ProjectorOnContraptionFn {
        boolean test(Level level, BlockPos pos);
    }

    /** Contraption's camera-relative visible transform; consumed by the per-pixel depth capture. Null on the base path. */
    public static volatile ProjectorContraptionFrameFn contraptionFrame;

    @FunctionalInterface
    public interface ProjectorContraptionFrameFn {
        ContraptionFrame resolve(Level level, BlockPos pos, Vec3 camPos, float partialTick);
    }

    /** {@code m}: (gridCoord - rp) -> camera-relative visible; {@code minv}: its inverse; {@code rp*}: rotation point. */
    public record ContraptionFrame(Matrix4f m, Matrix4f minv, double rpx, double rpy, double rpz) {
    }

    /** Visible frames of contraptions intersecting a world AABB, so a host projector can capture them as occluders. Null on the base path. */
    public static volatile ProjectorIntersectingFramesFn intersectingFrames;

    @FunctionalInterface
    public interface ProjectorIntersectingFramesFn {
        List<ContraptionFrame> resolve(Level level, AABB worldBounds, Vec3 camPos, float partialTick);
    }

    static boolean DIAG_CONTRAPTION = true;
    private static final Set<BlockPos> DIAG_LOGGED = ConcurrentHashMap.newKeySet();

    static final double MAX_THROW = 16.0; // beam reach (blocks); shared with the depth-map passes. Cosmetic look knobs live in ProjectorTunables.
    private static final double SURFACE_EPS = 0.003;  // float just off the hit surface
    private static final int FULL_BRIGHT = 15728880;
    private static final int CONFORM_GRID = 4;
    private static final double FLAT_TOL = 0.75;      // corner-distance spread (blocks) under which the surface is "flat"
    private static final double CREASE_FRAC = 0.35;   // cut a cell whose corners bend off-plane by more than this x its size
    static final double NEAR_EPS = 0.1;       // near-plane offset so the projective UV denominator stays > 0
    private static final double PLANE_EPS = 1.0e-6;
    static final int MAX_SPAN = 40;
    static final int MAX_FACES = 4096;
    private static final double OCC_EPS = 0.06;
    private static final int MAX_SHADOW_CASTERS = 16;
    private static final int MAX_SUB = 8;
    private static final int OCC_SUB = 4;             // occlusion-ray grid cap (catches thin mid-face occluders)
    private static final double UV_CELL = 0.035;      // target billboard-UV span per subdivision cell

    public ProjectorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    // ===== Pinhole basis: shared by the BER drape and the per-pixel depth-map/decal passes =====

    /** {@code coneK} is the cone half-spread. */
    record Pinhole(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double coneK) {
    }

    static Pinhole pinhole(ProjectorBlockEntity be) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(ProjectorBlock.FACING);
        Vec3 forward = Vec3.atLowerCornerOf(facing.getNormal()).normalize();
        Vec3 eye = Vec3.atCenterOf(be.getBlockPos()).add(forward.scale(0.5));
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = forward.cross(worldUp).normalize();
        if (be.isFlipped()) {
            right = right.scale(-1);
        }
        Vec3 up = right.cross(forward).normalize();
        for (int k = Math.floorMod(be.getRotation(), 4); k > 0; k--) {
            Vec3 nr = up;
            up = right.scale(-1);
            right = nr;
        }
        return new Pinhole(eye, forward, right, up, BASE_HALF / REF_DIST);
    }

    /** Glass-family blocks let the beam pass through. Deliberately NOT {@code !canOcclude()} — fences/stairs must still block. */
    static boolean isLightTransmitting(BlockState st) {
        return st.getBlock() instanceof TransparentBlock
                || st.is(Tags.Blocks.GLASS_BLOCKS)
                || st.is(Tags.Blocks.GLASS_PANES);
    }

    /** True when a full-block light source sits directly behind the lens (a torch or lantern is not enough). */
    static boolean isLit(ProjectorBlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return false;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ProjectorBlock)) {
            return false;
        }
        Direction facing = state.getValue(ProjectorBlock.FACING);
        BlockPos behind = be.getBlockPos().relative(facing.getOpposite());
        BlockState behindState = level.getBlockState(behind);
        return behindState.getLightEmission(level, behind) > 0
                && behindState.isCollisionShapeFullBlock(level, behind);
    }

    /** World AABB enclosing the full pinhole cone out to {@link #MAX_THROW}. */
    static AABB coneAABB(Pinhole p) {
        Vec3 fc = p.eye().add(p.forward().scale(MAX_THROW));
        double rk = p.coneK() * MAX_THROW;
        Vec3[] corners = {
                p.eye(),
                fc.add(p.right().scale(rk)).add(p.up().scale(rk)),
                fc.add(p.right().scale(rk)).subtract(p.up().scale(rk)),
                fc.subtract(p.right().scale(rk)).add(p.up().scale(rk)),
                fc.subtract(p.right().scale(rk)).subtract(p.up().scale(rk)),
        };
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3 c : corners) {
            minX = Math.min(minX, c.x); minY = Math.min(minY, c.y); minZ = Math.min(minZ, c.z);
            maxX = Math.max(maxX, c.x); maxY = Math.max(maxY, c.y); maxZ = Math.max(maxZ, c.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Kill-switch for the pack-active per-pixel path; false restores BER-mesh-under-pack. */
    private static final boolean PACK_PATH = true;

    private static boolean shadersLoaded() {
        return BeyondShaders.getProjectorDist() != null && BeyondShaders.getProjectorDecal() != null;
    }

    /** Global availability of the per-pixel deferred path. Under a pack: modern Iris only. */
    public static boolean deferredAvailable() {
        return shadersLoaded()
                && (!ShaderCompatLib.isShaderPackActive() || (PACK_PATH && ShaderCompatLib.isIrisProper()));
    }

    public static boolean packPathActive() {
        return PACK_PATH && shadersLoaded()
                && ShaderCompatLib.isShaderPackActive() && ShaderCompatLib.isIrisProper();
    }

    static boolean isOnContraption(ProjectorBlockEntity be) {
        ProjectorOnContraptionFn fn = onContraption;
        Level lvl = be.getLevel();
        return fn != null && lvl != null && fn.test(lvl, be.getBlockPos());
    }

    @Override
    public void render(ProjectorBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (ShaderCompatLib.isShadowPass()) {
            return;
        }
        // Exactly one path paints each projector per frame: the deferred decal owns it only when captured this frame, else the BER.
        if (deferredAvailable() && ProjectorDepthMap.wasCaptured(be)) {
            return;
        }
        Level level = be.getLevel();
        if (level == null || level != Minecraft.getInstance().level) {
            return; // sub-level blocks also live here, at far-away plot coords
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ProjectorBlock)) {
            return;
        }
        NonNullList<ItemStack> items = be.getItems();
        if (allEmpty(items)) {
            return;
        }

        BlockPos pos = be.getBlockPos();
        if (!isLit(be)) {
            return;
        }
        Pinhole ph = pinhole(be);
        Vec3 forward = ph.forward();
        Vec3 eye = ph.eye();
        Vec3 right = ph.right();
        Vec3 up = ph.up();
        Vec3 normal = up.cross(right).normalize();
        double coneK = ph.coneK();

        ProjectorClipFn fn = subLevelClip; // null on the base path

        // Cast lens centre + four image corners; similar distances keep the single-quad path, else conform per-cell.
        double centerDist = clipDist(fn, level, pos, eye, forward);
        double cTL = clipDist(fn, level, pos, eye, coneDir(forward, right, up, -coneK, +coneK));
        double cTR = clipDist(fn, level, pos, eye, coneDir(forward, right, up, +coneK, +coneK));
        double cBL = clipDist(fn, level, pos, eye, coneDir(forward, right, up, -coneK, -coneK));
        double cBR = clipDist(fn, level, pos, eye, coneDir(forward, right, up, +coneK, -coneK));
        double minD = Double.MAX_VALUE, maxD = -1.0;
        boolean anyHit = false, allHit = true;
        for (double d : new double[]{centerDist, cTL, cTR, cBL, cBR}) {
            if (d >= 0.0) {
                anyHit = true;
                minD = Math.min(minD, d);
                maxD = Math.max(maxD, d);
            } else {
                allHit = false;
            }
        }
        if (!anyHit) {
            return; // aimed fully at open air
        }
        boolean flat = allHit && (maxD - minD) < FLAT_TOL;

        // Vertices are emitted relative to (ox,oy,oz) through this pose, so a Sable baked PoseStack rotates the image with the contraption.
        PoseStack.Pose pose = poseStack.last();
        double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();

        int mode = be.getMode();
        ResourceLocation gradeId = be.getGradeId();
        int[] filled = be.filledSlots();
        int f = filled.length;
        if (f == 0) {
            return;
        }
        int carousel = Math.floorMod(be.getCarouselIndex(), f);

        double flatDist = Math.max(0.5, centerDist - SURFACE_EPS);
        Vec3 flatCenter = eye.add(forward.scale(flatDist));
        float flatHalf = BASE_HALF * (float) Mth.clamp(flatDist / REF_DIST, MIN_SCALE, MAX_SCALE);

        // Mesh decal conforms to stairs/slabs; empty -> flat/cone fallback. Sable keeps contraption blocks in mc.level at plot coords.
        List<ClipFace> mesh = buildMeshDecal(level, eye, forward, right, up, coneK);
        boolean canMesh = !mesh.isEmpty();
        if (DIAG_CONTRAPTION && subLevelClip != null && isOnContraption(be) && DIAG_LOGGED.add(pos)) {
            TheBeyond.LOGGER.info(
                    "[Projector M0] contraption be@{} facing={} filled={} anyHit={} centerDist={} meshFaces={} canMesh={} flat={}",
                    pos, state.getValue(ProjectorBlock.FACING), f, anyHit,
                    String.format(Locale.ROOT, "%.2f", centerDist), mesh.size(), canMesh, flat);
        }

        for (int j = 0; j < f; j++) {
            int slot = filled[j];
            Resolved base = resolveTexture(items.get(slot), gradeId);
            if (base == null) {
                continue;
            }
            if (mode == ProjectorBlockEntity.MODE_CAROUSEL && j != carousel) {
                continue;
            }
            ProjectorTexture.Region region = regionFor(mode, j, f, base.region());
            VertexConsumer vc = buffers.getBuffer(BeyondRenderTypes.projectorPack(base.texture()));
            double layerDepth = (mode == ProjectorBlockEntity.MODE_MIXUP) ? j * LAYER_DEPTH : 0.0;
            if (canMesh) {
                emitMeshSlot(vc, pose, mesh, region, base.opacity(), base.flipV(), layerDepth, forward, ox, oy, oz);
            } else if (flat) {
                Vec3 quadCenter = flatCenter;
                if (mode == ProjectorBlockEntity.MODE_MIXUP) {
                    // Push each layer toward the lens (anti z-fight); full-image layers also fan so stacked photos stay visible.
                    quadCenter = quadCenter.subtract(forward.scale(j * LAYER_DEPTH));
                    if (isFull(region)) {
                        double fan = j * LAYER_FAN * flatHalf;
                        quadCenter = quadCenter.add(right.scale(fan)).add(up.scale(fan));
                    }
                }
                emitRegionQuad(vc, pose, quadCenter, right, up, normal, flatHalf, region, base.opacity(), base.flipV(), ox, oy, oz);
            } else {
                emitConformGrid(vc, pose, eye, forward, right, up, normal, coneK, region,
                        base.opacity(), base.flipV(), layerDepth, fn, level, pos, ox, oy, oz);
            }
        }
    }

    private static boolean isFull(ProjectorTexture.Region r) {
        return r.u0() == 0f && r.v0() == 0f && r.u1() == 1f && r.v1() == 1f;
    }

    static ProjectorTexture.Region regionFor(int mode, int j, int f, ProjectorTexture.Region itemRegion) {
        switch (mode) {
            case ProjectorBlockEntity.MODE_MIXUP:
                return itemRegion;
            case ProjectorBlockEntity.MODE_LINE: {
                // Square cells in a row, centered vertically -> each keeps the image's aspect.
                float cell = 1f / f;
                float u0 = j * cell;
                float u1 = (j + 1) * cell;
                float v0 = 0.5f - cell / 2f;
                float v1 = 0.5f + cell / 2f;
                return new ProjectorTexture.Region(u0, v0, u1, v1);
            }
            case ProjectorBlockEntity.MODE_QUADRANT:
                return quadrantRegion(j, f);
            case ProjectorBlockEntity.MODE_CAROUSEL:
            default:
                return ProjectorTexture.Region.FULL;
        }
    }

    /** Quadrant layout: 2x2 row-major; 1-3 filled slots get centered cells so the picture stays balanced. */
    private static ProjectorTexture.Region quadrantRegion(int j, int f) {
        if (f == 1) {
            return new ProjectorTexture.Region(0.25f, 0.25f, 0.75f, 0.75f);
        }
        if (f == 2) {
            return j == 0
                    ? new ProjectorTexture.Region(0f, 0.25f, 0.5f, 0.75f)
                    : new ProjectorTexture.Region(0.5f, 0.25f, 1f, 0.75f);
        }
        if (f == 3) {
            // Triangle: two on top, one centered below -> no blank corner.
            return switch (j) {
                case 0 -> new ProjectorTexture.Region(0f, 0f, 0.5f, 0.5f);
                case 1 -> new ProjectorTexture.Region(0.5f, 0f, 1f, 0.5f);
                default -> new ProjectorTexture.Region(0.25f, 0.5f, 0.75f, 1f);
            };
        }
        int col = j % 2;
        int row = j / 2;
        float u0 = col * 0.5f;
        float v0 = row * 0.5f;
        return new ProjectorTexture.Region(u0, v0, u0 + 0.5f, v0 + 0.5f);
    }

    /** White-keyed ARGB whose alpha is {@code opacity} scaled by the global projection opacity. */
    private static int projectorColor(float opacity) {
        int alpha = Mth.clamp((int) (opacity * PROJECTION_OPACITY * 255f), 0, 255);
        return (alpha << 24) | 0x00FFFFFF;
    }

    /** {@code flipV} for icon sources in an FBO (GL origin bottom-left), unlike the top-first snapshot/data-map textures. */
    private static void emitRegionQuad(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, Vec3 r, Vec3 u, Vec3 n,
                                       float half, ProjectorTexture.Region reg, float opacity, boolean flipV, double ox, double oy, double oz) {
        float qu0 = reg.u0(), qu1 = reg.u1();
        float qTop = 1f - reg.v0(), qBot = 1f - reg.v1();
        int color = projectorColor(opacity);
        float vBot = flipV ? 0f : 1f;
        float vTop = flipV ? 1f : 0f;

        vert(vc, pose, corner(c, r, u, half, qu0, qBot), ox, oy, oz, color, 0f, vBot, n);
        vert(vc, pose, corner(c, r, u, half, qu0, qTop), ox, oy, oz, color, 0f, vTop, n);
        vert(vc, pose, corner(c, r, u, half, qu1, qTop), ox, oy, oz, color, 1f, vTop, n);
        vert(vc, pose, corner(c, r, u, half, qu1, qBot), ox, oy, oz, color, 1f, vBot, n);
    }

    private static Vec3 coneDir(Vec3 forward, Vec3 right, Vec3 up, double su, double sv) {
        return forward.add(right.scale(su)).add(up.scale(sv)).normalize();
    }

    /** Distance to the first surface along {@code dir}, or -1 on a miss. */
    private static double clipDist(ProjectorClipFn fn, Level level, BlockPos pos, Vec3 eye, Vec3 dir) {
        if (fn != null) {
            return fn.clip(level, pos, eye, dir, MAX_THROW);
        }
        try {
            // COLLIDER (not OUTLINE): decoratives have empty colliders and pass through; glass is stepped through to the wall behind.
            Vec3 end = eye.add(dir.scale(MAX_THROW));
            Vec3 from = eye;
            for (int i = 0; i < 5; i++) {
                BlockHitResult hit = level.clip(new ClipContext(from, end,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (hit.getType() == HitResult.Type.MISS) {
                    return -1.0;
                }
                if (!isLightTransmitting(level.getBlockState(hit.getBlockPos()))) {
                    return hit.getLocation().distanceTo(eye);
                }
                from = hit.getLocation().add(dir.scale(1.0e-3));
            }
            return -1.0;
        } catch (RuntimeException e) {
            return -1.0;
        }
    }

    /** Conforms one slot's region to real geometry: an (N+1)^2 grid of cone rays, each cell the quad of its four surface hits. */
    private static void emitConformGrid(VertexConsumer vc, PoseStack.Pose pose, Vec3 eye, Vec3 forward, Vec3 right, Vec3 up,
                                        Vec3 normal, double coneK, ProjectorTexture.Region reg, float opacity, boolean flipV,
                                        double layerDepth, ProjectorClipFn fn, Level level, BlockPos pos,
                                        double ox, double oy, double oz) {
        int color = projectorColor(opacity);
        int n = CONFORM_GRID;
        int stride = n + 1;
        double ru = reg.u1() - reg.u0(), rv = reg.v1() - reg.v0();

        Vec3[] hit = new Vec3[stride * stride]; // null = that ray hit nothing
        for (int gi = 0; gi <= n; gi++) {
            for (int gj = 0; gj <= n; gj++) {
                double iu = reg.u0() + ru * gi / n;
                double iv = reg.v0() + rv * gj / n;
                Vec3 dir = coneDir(forward, right, up, (iu * 2.0 - 1.0) * coneK, (1.0 - iv * 2.0) * coneK);
                double d = clipDist(fn, level, pos, eye, dir);
                if (d >= 0.0) {
                    Vec3 p = eye.add(dir.scale(Math.max(0.25, d - SURFACE_EPS)));
                    if (layerDepth != 0.0) {
                        p = p.subtract(forward.scale(layerDepth));
                    }
                    hit[gi * stride + gj] = p;
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                Vec3 tl = hit[i * stride + j];
                Vec3 tr = hit[(i + 1) * stride + j];
                Vec3 bl = hit[i * stride + (j + 1)];
                Vec3 br = hit[(i + 1) * stride + (j + 1)];
                if (tl == null || tr == null || bl == null || br == null) {
                    continue;
                }
                if (!coplanar(tl, tr, bl, br)) {
                    continue; // corners bridge a crease/step -> don't stretch across air
                }
                float uL = (float) i / n, uR = (float) (i + 1) / n;
                float vT = flipV ? 1f - (float) j / n : (float) j / n;
                float vB = flipV ? 1f - (float) (j + 1) / n : (float) (j + 1) / n;
                Vec3 nrm = quadNormal(tl, tr, bl, eye, normal);
                vert(vc, pose, bl, ox, oy, oz, color, uL, vB, nrm);
                vert(vc, pose, tl, ox, oy, oz, color, uL, vT, nrm);
                vert(vc, pose, tr, ox, oy, oz, color, uR, vT, nrm);
                vert(vc, pose, br, ox, oy, oz, color, uR, vB, nrm);
            }
        }
    }

    /** Cell-quad normal flipped to face the lens; {@code def} when degenerate. */
    private static Vec3 quadNormal(Vec3 tl, Vec3 tr, Vec3 bl, Vec3 eye, Vec3 def) {
        Vec3 nrm = tr.subtract(tl).cross(bl.subtract(tl));
        double len = nrm.length();
        if (len < 1.0e-6) {
            return def;
        }
        nrm = nrm.scale(1.0 / len);
        return nrm.dot(eye.subtract(tl)) < 0.0 ? nrm.scale(-1.0) : nrm;
    }

    private static boolean coplanar(Vec3 tl, Vec3 tr, Vec3 bl, Vec3 br) {
        Vec3 e1 = tr.subtract(tl);
        Vec3 e2 = bl.subtract(tl);
        Vec3 nrm = e1.cross(e2);
        double nl = nrm.length();
        if (nl < 1.0e-6) {
            return true; // degenerate / tiny cell -> keep
        }
        double dev = Math.abs(br.subtract(tl).dot(nrm) / nl);
        double cell = Math.max(e1.length(), e2.length());
        return dev <= CREASE_FRAC * cell;
    }

    // ===== Mesh decal (host path): real VoxelShape faces clipped to the projector frustum, with projective UV =====

    private static final Direction[] DIRS = Direction.values();

    /** Inward-facing frustum plane (inside = signed >= 0). */
    record Plane(Vec3 point, Vec3 normal) {
    }

    /** Per-vertex billboard UV is full-cone [0,1], v down. */
    private record ClipFace(Vec3[] poly, double[] u, double[] v, double[] a, Vec3 normal) {
    }

    /** Host world only (no Sable bridge). */
    private static List<ClipFace> buildMeshDecal(Level level, Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double coneK) {
        List<ClipFace> out = new ArrayList<>();
        Plane[] planes = buildFrustumPlanes(eye, forward, right, up, coneK, MAX_THROW);
        AABB box = coneAABB(new Pinhole(eye, forward, right, up, coneK));
        if (box.maxX - box.minX > MAX_SPAN || box.maxY - box.minY > MAX_SPAN || box.maxZ - box.minZ > MAX_SPAN) {
            return out;
        }
        BlockPos lo = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos hi = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        gatherShadowCasters(level, box, eye, forward);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos np = new BlockPos.MutableBlockPos();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    if (out.size() >= MAX_FACES) {
                        return out;
                    }
                    AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
                    if (aabbOutside(cell, planes)) {
                        continue;
                    }
                    mp.set(x, y, z);
                    BlockState st = level.getBlockState(mp);
                    if (st.isAir() || st.canBeReplaced()) {
                        continue; // pass through air + vegetation/fluids
                    }
                    for (ModelQuad q : modelQuads(st)) {
                        if (q.cull() >= 0) {
                            Direction cf = Direction.from3DDataValue(q.cull());
                            np.set(x + cf.getStepX(), y + cf.getStepY(), z + cf.getStepZ());
                            if (level.getBlockState(np).isFaceSturdy(level, np, cf.getOpposite())) {
                                continue; // face flush against a solid neighbour -> never lit
                            }
                        }
                        emitQuad(level, q, x, y, z, eye, forward, right, up, coneK, out);
                        if (out.size() >= MAX_FACES) {
                            return out;
                        }
                    }
                }
            }
        }
        return out;
    }

    /** One baked-model quad -> subdivided cells with smooth per-vertex alpha, so adjacent quads agree on their shared edge. */
    private static void emitQuad(Level level, ModelQuad mq, int bx, int by, int bz, Vec3 eye, Vec3 forward,
                                 Vec3 right, Vec3 up, double coneK, List<ClipFace> out) {
        float[] m = mq.pos();
        Vec3[] q = {
                new Vec3(m[0] + bx, m[1] + by, m[2] + bz),
                new Vec3(m[3] + bx, m[4] + by, m[5] + bz),
                new Vec3(m[6] + bx, m[7] + by, m[8] + bz),
                new Vec3(m[9] + bx, m[10] + by, m[11] + bz),
        };
        Vec3 n = new Vec3(mq.nx(), mq.ny(), mq.nz());
        Vec3 center = q[0].add(q[1]).add(q[2]).add(q[3]).scale(0.25);
        if (n.dot(center.subtract(eye)) >= -1.0e-6) {
            return; // quad faces away from the lens
        }
        // Drop quads wholly behind the near plane or outside the cone rectangle.
        boolean anyFront = false;
        double uMin = 2, uMax = -1, vMin = 2, vMax = -1;
        for (int i = 0; i < 4; i++) {
            if (q[i].subtract(eye).dot(forward) >= NEAR_EPS) {
                anyFront = true;
            }
            double[] uv = billboardUV(q[i], eye, forward, right, up, coneK);
            uMin = Math.min(uMin, uv[0]); uMax = Math.max(uMax, uv[0]);
            vMin = Math.min(vMin, uv[1]); vMax = Math.max(vMax, uv[1]);
        }
        if (!anyFront || uMax < -0.05 || uMin > 1.05 || vMax < -0.05 || vMin > 1.05) {
            return;
        }
        // Subdivide so affine UV approaches the true projective divide and the alpha gradient is smooth.
        double span = Math.max(uMax - uMin, vMax - vMin);
        int sub = (int) Mth.clamp(Math.ceil(span / UV_CELL), 1, MAX_SUB);
        Vec3 e1 = q[1].subtract(q[0]);
        Vec3 e2 = q[3].subtract(q[0]);
        Vec3 warp = q[2].subtract(q[1]).subtract(q[3]).add(q[0]); // bilinear cross term
        // Occlusion on a capped grid then interpolated: 4 corner rays would miss a thin mid-face occluder.
        BlockPos self = new BlockPos(bx, by, bz);
        int osub = Math.min(sub, OCC_SUB);
        int ogw = osub + 1;
        double[] occ = new double[ogw * ogw];
        for (int oi = 0; oi <= osub; oi++) {
            for (int oj = 0; oj <= osub; oj++) {
                double os = (double) oi / osub, ot = (double) oj / osub;
                Vec3 op = q[0].add(e1.scale(os)).add(e2.scale(ot)).add(warp.scale(os * ot));
                occ[oi * ogw + oj] = cornerOcc(level, eye, op, self);
            }
        }
        int gw = sub + 1;
        Vec3[] gp = new Vec3[gw * gw];
        double[] gu = new double[gw * gw];
        double[] gv = new double[gw * gw];
        double[] ga = new double[gw * gw];
        for (int i = 0; i <= sub; i++) {
            double s = (double) i / sub;
            for (int j = 0; j <= sub; j++) {
                double t = (double) j / sub;
                Vec3 p = q[0].add(e1.scale(s)).add(e2.scale(t)).add(warp.scale(s * t));
                double[] uv = billboardUV(p, eye, forward, right, up, coneK);
                int idx = i * gw + j;
                gp[idx] = p;
                gu[idx] = uv[0];
                gv[idx] = uv[1];
                ga[idx] = vertexAlpha(n, p, eye) * sampleOcc(occ, osub, ogw, s, t);
            }
        }
        for (int i = 0; i < sub; i++) {
            for (int j = 0; j < sub; j++) {
                int i00 = i * gw + j, i10 = (i + 1) * gw + j, i11 = (i + 1) * gw + (j + 1), i01 = i * gw + (j + 1);
                if (ga[i00] + ga[i10] + ga[i11] + ga[i01] < 0.004) {
                    continue; // fully transparent cell
                }
                out.add(new ClipFace(
                        new Vec3[]{gp[i00], gp[i10], gp[i11], gp[i01]},
                        new double[]{gu[i00], gu[i10], gu[i11], gu[i01]},
                        new double[]{gv[i00], gv[i10], gv[i11], gv[i01]},
                        new double[]{ga[i00], ga[i10], ga[i11], ga[i01]},
                        n));
            }
        }
    }

    /** Full strength on any surface the light reaches; only edge-on faces get nothing (no distance fade). */
    private static double vertexAlpha(Vec3 n, Vec3 p, Vec3 eye) {
        Vec3 rel = p.subtract(eye);
        double dist = rel.length();
        if (dist < 1.0e-4) {
            return 0.0;
        }
        double cos = -n.dot(rel) / dist;
        return cos < GRAZE_MIN ? 0.0 : 1.0;
    }

    private static double sampleOcc(double[] occ, int osub, int ogw, double s, double t) {
        double fs = s * osub, ft = t * osub;
        int is = Math.min((int) fs, osub - 1);
        int it = Math.min((int) ft, osub - 1);
        double ds = fs - is, dt = ft - it;
        double a = occ[is * ogw + it];
        double b = occ[(is + 1) * ogw + it];
        double c = occ[(is + 1) * ogw + (it + 1)];
        double d = occ[is * ogw + (it + 1)];
        return (1 - ds) * (1 - dt) * a + ds * (1 - dt) * b + ds * dt * c + (1 - ds) * dt * d;
    }

    /** Gathered once per projector per frame. */
    private static final List<AABB> shadowCasters = new ArrayList<>();

    private static void gatherShadowCasters(Level level, AABB cone, Vec3 eye, Vec3 forward) {
        shadowCasters.clear();
        for (Entity e : level.getEntities((Entity) null, cone, ent -> !ent.isSpectator())) {
            AABB box = e.getBoundingBox();
            if (box.getCenter().subtract(eye).dot(forward) > 0.2) { // in front of the lens
                shadowCasters.add(box);
                if (shadowCasters.size() >= MAX_SHADOW_CASTERS) {
                    return;
                }
            }
        }
    }

    /** 1.0 if the lens can see {@code p}; 0.0 if a nearer solid or an entity blocks it. */
    private static double cornerOcc(Level level, Vec3 eye, Vec3 p, BlockPos self) {
        if (blockOccluded(level, eye, p, self)) {
            return 0.0;
        }
        return entityShadowed(eye, p) ? 0.0 : 1.0;
    }

    private static boolean blockOccluded(Level level, Vec3 eye, Vec3 p, BlockPos self) {
        ProjectorOccFn fn = meshOcc;
        if (fn != null) {
            return fn.occ(level, eye, p, self) <= 0.0; // Sable raw clip on contraptions
        }
        Vec3 rel = p.subtract(eye);
        double dist = rel.length();
        if (dist < 1.0e-4) {
            return false;
        }
        Vec3 dir = rel.scale(1.0 / dist);
        try {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(dist + 0.5)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(self)) {
                return false; // the quad's own collider must not self-occlude
            }
            return hit.getLocation().distanceTo(eye) < dist - OCC_EPS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean entityShadowed(Vec3 eye, Vec3 p) {
        if (shadowCasters.isEmpty()) {
            return false;
        }
        double dist = p.distanceTo(eye);
        for (AABB box : shadowCasters) {
            Optional<Vec3> hit = box.clip(eye, p);
            if (hit.isPresent() && hit.get().distanceTo(eye) < dist - OCC_EPS) {
                return true;
            }
        }
        return false;
    }

    /** {@code cull}: -1 = none, else Direction 3D id; {@code pos}: 12 local-space corner floats. */
    record ModelQuad(int cull, float[] pos, float[] uv, double nx, double ny, double nz) {
    }

    private static final Map<BlockState, ModelQuad[]> MODEL_CACHE = new ConcurrentHashMap<>();

    /** Call on resource reload (block models re-bake). */
    public static void clearModelCache() {
        MODEL_CACHE.clear();
    }

    static ModelQuad[] modelQuads(BlockState state) {
        ModelQuad[] cached = MODEL_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource rand = RandomSource.create();
        List<ModelQuad> list = new ArrayList<>();
        for (int di = -1; di < DIRS.length; di++) {
            Direction face = di < 0 ? null : DIRS[di];
            int cull = face == null ? -1 : face.get3DDataValue();
            rand.setSeed(42L); // stable per state: CTM/dynamic models vary texture, not geometry
            for (BakedQuad bq : model.getQuads(state, face, rand)) {
                int[] v = bq.getVertices();
                int stride = v.length / 4; // ints/vertex; position first 3, atlas UV at 4-5
                float[] qp = new float[12];
                float[] qt = new float[8];
                for (int k = 0; k < 4; k++) {
                    qp[k * 3] = Float.intBitsToFloat(v[k * stride]);
                    qp[k * 3 + 1] = Float.intBitsToFloat(v[k * stride + 1]);
                    qp[k * 3 + 2] = Float.intBitsToFloat(v[k * stride + 2]);
                    qt[k * 2] = Float.intBitsToFloat(v[k * stride + 4]);
                    qt[k * 2 + 1] = Float.intBitsToFloat(v[k * stride + 5]);
                }
                Direction qd = bq.getDirection();
                list.add(new ModelQuad(cull, qp, qt, qd.getStepX(), qd.getStepY(), qd.getStepZ()));
            }
        }
        ModelQuad[] arr = list.toArray(new ModelQuad[0]);
        MODEL_CACHE.put(state, arr);
        return arr;
    }


    private static void emitMeshSlot(VertexConsumer vc, PoseStack.Pose pose, List<ClipFace> faces,
                                     ProjectorTexture.Region reg, float opacity, boolean flipV, double layerDepth,
                                     Vec3 forward, double ox, double oy, double oz) {
        double u0 = reg.u0(), v0 = reg.v0(), u1 = reg.u1(), v1 = reg.v1();
        double du = u1 - u0, dv = v1 - v0;
        boolean fullRegion = u0 == 0.0 && v0 == 0.0 && u1 == 1.0 && v1 == 1.0;
        for (ClipFace cf : faces) {
            List<double[]> poly = faceVerts(cf);
            // Bound to the cone footprint with the SAME boundary for every face, so adjacent faces meet at the cone edge without cracking.
            poly = clipAxis(poly, 3, true, 0.0);
            poly = clipAxis(poly, 3, false, 1.0);
            poly = clipAxis(poly, 4, true, 0.0);
            poly = clipAxis(poly, 4, false, 1.0);
            if (!fullRegion && poly.size() >= 3) {
                // then to the slot's spatial sub-rect
                poly = clipAxis(poly, 3, true, u0);
                poly = clipAxis(poly, 3, false, u1);
                poly = clipAxis(poly, 4, true, v0);
                poly = clipAxis(poly, 4, false, v1);
            }
            if (poly.size() < 3) {
                continue;
            }
            Vec3 nrm = cf.normal();
            // Lift along the face normal, not -forward (which would float upper layers off the block edge).
            Vec3 lift = nrm.scale(SURFACE_EPS + layerDepth);
            double[] a = poly.get(0);
            for (int i = 1; i + 1 < poly.size(); i++) {
                double[] b = poly.get(i);
                double[] c = poly.get(i + 1);
                emitMeshVert(vc, pose, a, u0, v0, du, dv, flipV, opacity, nrm, lift, ox, oy, oz);
                emitMeshVert(vc, pose, b, u0, v0, du, dv, flipV, opacity, nrm, lift, ox, oy, oz);
                emitMeshVert(vc, pose, c, u0, v0, du, dv, flipV, opacity, nrm, lift, ox, oy, oz);
                emitMeshVert(vc, pose, c, u0, v0, du, dv, flipV, opacity, nrm, lift, ox, oy, oz); // degenerate quad (QUADS mode)
            }
        }
    }

    private static void emitMeshVert(VertexConsumer vc, PoseStack.Pose pose, double[] vtx,
                                     double u0, double v0, double du, double dv, boolean flipV, float opacity, Vec3 n,
                                     Vec3 lift, double ox, double oy, double oz) {
        float texU = (float) ((vtx[3] - u0) / (du == 0.0 ? 1.0 : du));
        float vReg = (float) ((vtx[4] - v0) / (dv == 0.0 ? 1.0 : dv));
        float texV = flipV ? 1f - vReg : vReg;
        int alpha = Mth.clamp((int) (vtx[5] * opacity * PROJECTION_OPACITY * 255f), 0, 255);
        int color = (alpha << 24) | 0x00FFFFFF;
        double x = vtx[0] + lift.x, y = vtx[1] + lift.y, z = vtx[2] + lift.z;
        vc.addVertex(pose, (float) (x - ox), (float) (y - oy), (float) (z - oz))
                .setColor(color)
                .setUv(texU, texV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    /** {x,y,z,u,v,a} per vertex. */
    private static List<double[]> faceVerts(ClipFace cf) {
        Vec3[] p = cf.poly();
        List<double[]> l = new ArrayList<>(p.length);
        for (int i = 0; i < p.length; i++) {
            l.add(new double[]{p[i].x, p[i].y, p[i].z, cf.u()[i], cf.v()[i], cf.a()[i]});
        }
        return l;
    }

    /** Sutherland-Hodgman against one image-space edge (comp 3=u, 4=v), interpolating x,y,z,u,v,a. */
    private static List<double[]> clipAxis(List<double[]> in, int comp, boolean keepGreater, double bound) {
        if (in.size() < 3) {
            return in;
        }
        List<double[]> out = new ArrayList<>(in.size() + 2);
        for (int i = 0; i < in.size(); i++) {
            double[] a = in.get(i);
            double[] b = in.get((i + 1) % in.size());
            boolean ina = keepGreater ? a[comp] >= bound : a[comp] <= bound;
            boolean inb = keepGreater ? b[comp] >= bound : b[comp] <= bound;
            if (ina) {
                out.add(a);
            }
            if (ina != inb) {
                double denom = b[comp] - a[comp];
                double t = Math.abs(denom) < 1.0e-12 ? 0.0 : (bound - a[comp]) / denom;
                double[] ip = new double[6];
                for (int k = 0; k < 6; k++) {
                    ip[k] = a[k] + (b[k] - a[k]) * t;
                }
                out.add(ip);
            }
        }
        return out;
    }

    /** Six inward frustum planes: 4 sides, near (offset to keep UV well-conditioned), far. */
    static Plane[] buildFrustumPlanes(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double coneK, double maxThrow) {
        Vec3 interior = eye.add(forward);
        return new Plane[]{
                sidePlane(eye, right.subtract(forward.scale(coneK)), interior),
                sidePlane(eye, right.scale(-1).subtract(forward.scale(coneK)), interior),
                sidePlane(eye, up.subtract(forward.scale(coneK)), interior),
                sidePlane(eye, up.scale(-1).subtract(forward.scale(coneK)), interior),
                new Plane(eye.add(forward.scale(NEAR_EPS)), forward),
                new Plane(eye.add(forward.scale(maxThrow)), forward.scale(-1)),
        };
    }

    private static Plane sidePlane(Vec3 eye, Vec3 raw, Vec3 interior) {
        Vec3 n = raw.normalize();
        if (interior.subtract(eye).dot(n) < 0.0) {
            n = n.scale(-1);
        }
        return new Plane(eye, n);
    }

    /** Conservative frustum reject via the positive-most corner. */
    static boolean aabbOutside(AABB box, Plane[] planes) {
        for (Plane pl : planes) {
            Vec3 n = pl.normal();
            double px = n.x >= 0 ? box.maxX : box.minX;
            double py = n.y >= 0 ? box.maxY : box.minY;
            double pz = n.z >= 0 ? box.maxZ : box.minZ;
            if ((px - pl.point().x) * n.x + (py - pl.point().y) * n.y + (pz - pl.point().z) * n.z < -PLANE_EPS) {
                return true;
            }
        }
        return false;
    }

    /** Projective billboard UV: full cone -> [0,1]^2, v down (matches top-first texture rows). */
    static double[] billboardUV(Vec3 p, Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double coneK) {
        Vec3 rel = p.subtract(eye);
        double den = rel.dot(forward);
        if (den < 1.0e-6) {
            den = 1.0e-6;
        }
        double su = rel.dot(right) / den;
        double sv = rel.dot(up) / den;
        return new double[]{su / coneK * 0.5 + 0.5, 0.5 - sv / coneK * 0.5};
    }

    private static Vec3 corner(Vec3 c, Vec3 r, Vec3 u, float half, float un, float vn) {
        double ro = (un * 2.0 - 1.0) * half;
        double uo = (vn * 2.0 - 1.0) * half;
        return c.add(r.scale(ro)).add(u.scale(uo));
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, double ox, double oy, double oz, int color, float u, float v, Vec3 n) {
        vc.addVertex(pose, (float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz))
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }

    private static boolean allEmpty(NonNullList<ItemStack> items) {
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The display texture + sub-rect + opacity for a slot, or null if there's nothing (yet) to draw. */
    static Resolved resolveTexture(ItemStack stack, ResourceLocation projectorGradeId) {
        // AS_PHOTO defers to each photo's own filter; otherwise the projector's chosen grade overrides.
        ResourceLocation gradeId = Grades.AS_PHOTO.equals(projectorGradeId) ? Grades.photoGrade(stack) : projectorGradeId;
        Components.SnapshotPixelsComponent px = stack.get(BeyondComponents.SNAPSHOT_PIXELS.get());
        if (px != null && px.width() > 0 && px.rgb().length == px.width() * px.height() * 3) {
            return new Resolved(SnapshotTextures.get(px, gradeId), ProjectorTexture.Region.FULL, 1f, false);
        }
        ProjectorTexture pt = BeyondDataMapTypes.getProjectorTexture(stack);
        if (pt != null) {
            return new Resolved(pt.texture(), pt.region(), pt.opacity(), false);
        }
        // Fallback: live inventory icon, null until its FBO renders; flipV since the FBO is bottom-up.
        ResourceLocation icon = ItemIconTextures.get(stack, toGpuGrade(gradeId));
        return icon != null ? new Resolved(icon, ProjectorTexture.Region.FULL, 1f, true) : null;
    }

    /** Maps a grade id to the built-in GPU shader key for the icon path; custom/none -> NONE (no shader). */
    private static SnapshotGrade toGpuGrade(ResourceLocation gradeId) {
        if (Grades.SEPIA.equals(gradeId)) {
            return SnapshotGrade.SEPIA;
        }
        if (Grades.BLUE.equals(gradeId)) {
            return SnapshotGrade.BLUE;
        }
        return SnapshotGrade.NONE;
    }

    record Resolved(ResourceLocation texture, ProjectorTexture.Region region, float opacity, boolean flipV) {
    }

    @Override
    public AABB getRenderBoundingBox(ProjectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(MAX_THROW + 4);
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorBlockEntity be) {
        return true; // the image lands outside the block's own section
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
