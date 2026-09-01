package com.thebeyond.common.entity.util.livingblock;

import com.mojang.logging.LogUtils;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import javax.annotation.Nullable;

public class LivingBlockCollisionHandler {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    protected static final boolean shouldLog = false;
    private static final float CONTACT_TOLERANCE = 0.01F;

    private static void cornerAt(Vector3f dest, AABB box, int index, double cx, double cy, double cz) {
        dest.set(
                (float) (((index & 1) == 0 ? box.minX : box.maxX) - cx),
                (float) (((index & 2) == 0 ? box.minY : box.maxY) - cy),
                (float) (((index & 4) == 0 ? box.minZ : box.maxZ) - cz)
        );
    }

    public static float groundContactSpan(LivingBlock entity, Quaternionf rotation) {
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return 1.0F;
        }

        AABB bounds = entity.getBaseShapeBounds();
        double cx = (bounds.minX + bounds.maxX) / 2.0;
        double cy = (bounds.minY + bounds.maxY) / 2.0;
        double cz = (bounds.minZ + bounds.maxZ) / 2.0;

        Vector3f corner = new Vector3f();
        float lowest = Float.POSITIVE_INFINITY;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (AABB box : entity.getBaseShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                lowest = Math.min(lowest, corner.y());
                minX = Math.min(minX, corner.x());
                maxX = Math.max(maxX, corner.x());
                minZ = Math.min(minZ, corner.z());
                maxZ = Math.max(maxZ, corner.z());
            }
        }

        float spanX = maxX - minX;
        float spanZ = maxZ - minZ;
        if (spanX < 1.0E-4F || spanZ < 1.0E-4F) {
            return 0.0F;
        }

        float plane = lowest + CONTACT_TOLERANCE;
        float contactMinX = Float.POSITIVE_INFINITY;
        float contactMaxX = Float.NEGATIVE_INFINITY;
        float contactMinZ = Float.POSITIVE_INFINITY;
        float contactMaxZ = Float.NEGATIVE_INFINITY;

        for (AABB box : entity.getBaseShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                if (corner.y() <= plane) {
                    contactMinX = Math.min(contactMinX, corner.x());
                    contactMaxX = Math.max(contactMaxX, corner.x());
                    contactMinZ = Math.min(contactMinZ, corner.z());
                    contactMaxZ = Math.max(contactMaxZ, corner.z());
                }
            }
        }

        if (contactMaxX < contactMinX) {
            return 0.0F;
        }

        return Mth.clamp(((contactMaxX - contactMinX) / spanX) * ((contactMaxZ - contactMinZ) / spanZ), 0.0F, 1.0F);
    }

    public static Vector3f supportCorner(LivingBlock entity, Quaternionf rotation, Vector3f direction) {
        Vector3f best = new Vector3f();
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return best;
        }

        AABB bounds = entity.getBaseShapeBounds();
        double cx = (bounds.minX + bounds.maxX) / 2.0;
        double cy = (bounds.minY + bounds.maxY) / 2.0;
        double cz = (bounds.minZ + bounds.maxZ) / 2.0;

        float bestDot = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();

        for (AABB box : entity.getBaseShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                float dot = corner.dot(direction);
                if (dot > bestDot) {
                    bestDot = dot;
                    best.set(corner);
                }
            }
        }

        return best;
    }

    public static void includeRotatedOBBCorners(LivingBlock entity, Quaternionf rotation, AABBBuilder builder) {
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return;
        }

        AABB bounds = entity.getBaseShapeBounds();
        double cx = (bounds.minX + bounds.maxX) / 2.0;
        double cy = (bounds.minY + bounds.maxY) / 2.0;
        double cz = (bounds.minZ + bounds.maxZ) / 2.0;

        Vector3f corner = new Vector3f();
        for (AABB box : entity.getShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                builder.include(corner);
            }
        }
    }

    public static List<OrientedBox> getOBBs(LivingBlock entity, Quaternionf rotation) {
        return getOBBs(entity, rotation, entity.position());
    }

    public static List<OrientedBox> getOBBs(LivingBlock entity, Quaternionf rotation, Vec3 entityPos) {
        if (!entity.orientedHull()) {
            return entity.axisAlignedGeometry(entityPos).obbs();
        }
        return posedOBBs(entity, rotation, entityPos);
    }

    public static List<OrientedBox> posedOBBs(LivingBlock entity, Quaternionf rotation, Vec3 entityPos) {
        List<AABB> localBoxes = entity.getShapeBoxes();
        AABB bounds = entity.getBaseShapeBounds();
        Vec3 pivot = new Vec3(
                (bounds.minX + bounds.maxX) / 2.0,
                (bounds.minY + bounds.maxY) / 2.0,
                (bounds.minZ + bounds.maxZ) / 2.0
        );

        AABB silhouette = entity.rotatedSilhouette(rotation);
        return anchoredOBBs(localBoxes, silhouette, pivot, rotation, entityPos, entity.getId());
    }

    public static List<OrientedBox> anchoredOBBs(List<AABB> localBoxes, @Nullable AABB silhouette,
                                                 Vec3 pivot, Quaternionf rotation, Vec3 entityPos, int id) {
        double lift = silhouette != null ? -silhouette.minY : 0.0;
        double anchorY = entityPos.y + lift;
        List<OrientedBox> obbs = anchoredAt(localBoxes, entityPos, anchorY, pivot, rotation);

        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < obbs.size(); i++) {
            lowest = Math.min(lowest, obbs.get(i).getWorldAABB().minY);
        }

        double residual = entityPos.y - lowest;
        if (!(residual > 0.0)) {
            return obbs;
        }

        if (residual > anchorWorstResidual * 2.0 + 1.0E-9) {
            anchorWorstResidual = residual;
            if (shouldLog) LOGGER.debug("[livingblock] anchor id={} residual={} lift={} boxes={}",
                    id, residual, lift, obbs.size());
        }

        return anchoredAt(localBoxes, entityPos, anchorY + residual, pivot, rotation);
    }

    private static List<OrientedBox> anchoredAt(List<AABB> localBoxes, Vec3 entityPos, double anchorY,
                                                Vec3 pivot, Quaternionf rotation) {
        Vec3 anchor = new Vec3(entityPos.x, anchorY, entityPos.z);
        List<OrientedBox> obbs = new ArrayList<>(localBoxes.size());
        for (AABB localBox : localBoxes) {
            obbs.add(new OrientedBox(localBox, anchor, pivot, rotation));
        }
        return obbs;
    }

    public static LivingBlockCollisionShapes.Placement liveGeometry(LivingBlock entity, Quaternionf rotation) {
        List<OrientedBox> obbs = getOBBs(entity, rotation);
        List<AABB> boxes = new ArrayList<>(obbs.size());
        List<VoxelShape> shapes = new ArrayList<>(obbs.size());
        List<OrientedBox> kept = new ArrayList<>(obbs.size());

        for (OrientedBox obb : obbs) {
            AABB world = obb.getWorldAABB();
            if (!Shapes.create(world).isEmpty()) {
                boxes.add(world);
                shapes.add(new OrientedBoxShape(obb, world));
                kept.add(obb);
            }
        }

        return new LivingBlockCollisionShapes.Placement(
                List.copyOf(boxes), List.copyOf(shapes), List.copyOf(kept));
    }

    public static OptionalDouble climbWallPlane(final LivingBlock entity, final Direction direction,
                                                final double reach) {
        LivingBlockPivot.WallContact contact = climbWallContact(entity, direction, reach);
        return contact == null ? OptionalDouble.empty() : OptionalDouble.of(contact.surface().plane());
    }

    public record BodyTerrain(List<AABB> boxes, int refused, double worstTilt) {
    }

    private static final BodyTerrain NO_BODIES = new BodyTerrain(List.of(), 0, 0.0);
    private static boolean lastWallFromBody;

    public static boolean lastWallFromBody() {
        return lastWallFromBody;
    }

    public static BodyTerrain bodyTerrain(final LivingBlock entity, final AABB probe) {
        List<LivingBlock> others = entity.level().getEntitiesOfClass(LivingBlock.class, probe,
                candidate -> candidate != entity && candidate.isAlive()
                        && entity.canCollideWith(candidate));
        if (others.isEmpty()) {
            return NO_BODIES;
        }
        List<AABB> boxes = new ArrayList<>();
        int refused = 0;
        double worstTilt = 0.0;
        for (LivingBlock other : others) {
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null || !LivingBlockPivot.anchorableBody(
                    other.isOrientationSettled(), other.getRotation(),
                    LivingBlockPivot.WALL_RESIDUE_DEGREES)) {
                refused++;
                worstTilt = Math.max(worstTilt,
                        LivingBlockPivot.misalignment(other.getRotation()));
                continue;
            }
            boxes.addAll(placement.boxes());
        }
        return new BodyTerrain(boxes, refused, worstTilt);
    }

    public static @Nullable LivingBlock anchorOwner(final LivingBlock entity, final AABB probe,
                                                    final Vec3 point, final double tolerance) {
        if (!entity.usesOrientedCollision()) {
            return null;
        }
        for (LivingBlock other : entity.level().getEntitiesOfClass(LivingBlock.class, probe,
                candidate -> candidate != entity && candidate.isAlive()
                        && entity.canCollideWith(candidate))) {
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null || !LivingBlockPivot.anchorableBody(
                    other.isOrientationSettled(), other.getRotation(),
                    LivingBlockPivot.WALL_RESIDUE_DEGREES)) {
                continue;
            }
            if (LivingBlockPivot.anchorHeld(placement.boxes(), point, tolerance)) {
                return other;
            }
        }
        return null;
    }

    public static @Nullable LivingBlockPivot.WallContact climbWallContact(final LivingBlock entity,
                                                                           final Direction direction,
                                                                           final double reach) {
        if (!direction.getAxis().isHorizontal()) {
            return null;
        }
        AABB hull = entity.getBoundingBox();
        AABB probe = hull.expandTowards(direction.getStepX() * reach, 0.0,
                direction.getStepZ() * reach).inflate(1.0E-6);
        List<AABB> blocks = new ArrayList<>();
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, probe)) {
            blocks.addAll(shape.toAabbs());
        }
        BodyTerrain bodies = entity.usesOrientedCollision()
                ? bodyTerrain(entity, probe)
                : new BodyTerrain(List.of(), 0, 0.0);
        List<AABB> terrain = blocks;
        if (!bodies.boxes().isEmpty()) {
            terrain = new ArrayList<>(blocks);
            terrain.addAll(bodies.boxes());
        }
        LivingBlockPivot.WallContact contact =
                LivingBlockPivot.wallContact(hull, terrain, direction, reach);
        lastWallFromBody = contact != null && !bodies.boxes().isEmpty()
                && LivingBlockPivot.surfacePresent(bodies.boxes(), contact.surface(),
                        direction, 1.0E-9)
                && !LivingBlockPivot.surfacePresent(blocks, contact.surface(), direction, 1.0E-9);
        reportBodyWall(entity, direction, bodies, contact);
        return contact;
    }

    private static void reportBodyWall(final LivingBlock entity, final Direction direction,
                                       final BodyTerrain bodies,
                                       final @Nullable LivingBlockPivot.WallContact contact) {
        if (bodies.boxes().isEmpty() && bodies.refused() == 0) {
            return;
        }
        String gate = lastWallFromBody ? "wallcaught"
                : contact == null ? "wallnocontact" : "wallnotchosen";
        if (!entity.beadWhyDue(gate)) {
            return;
        }
        entity.beadWhy(gate,
                String.format("dir=%s boxes=%d refused=%d worsttilt=%.2f plane=%s gap=%s",
                        direction, bodies.boxes().size(), bodies.refused(), bodies.worstTilt(),
                        contact == null ? "-" : String.format("%.4f", contact.surface().plane()),
                        contact == null ? "-" : String.format("%.4f", contact.gap())));
    }

    public static @Nullable Direction climbWallDirection(final LivingBlock entity,
                                                          final Direction preferred,
                                                          final double reach,
                                                          final boolean fallback) {
        AABB hull = entity.getBoundingBox();
        AABB probe = hull.inflate(reach, 1.0E-6, reach);
        List<AABB> terrain = new ArrayList<>();
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, probe)) {
            terrain.addAll(shape.toAabbs());
        }
        terrain.addAll(bodyTerrain(entity, probe).boxes());
        return LivingBlockPivot.wallDirection(hull, terrain, preferred, reach, fallback);
    }

    public static boolean climbPoseClear(final LivingBlock entity, final Quaternionf rotation,
                                         final Vec3 position, final double slop) {
        return climbPoseFailure(entity, rotation, position, slop) == null;
    }

    public record ClimbPoseFailure(String kind, double depth, String blocker) {
    }

    public static ClimbPoseFailure climbPoseFailure(final LivingBlock entity,
                                                     final Quaternionf rotation,
                                                     final Vec3 position, final double slop) {
        List<OrientedBox> own = posedOBBs(entity, rotation, position);
        Vector3f axis = new Vector3f();
        AABB envelope = null;
        for (OrientedBox mine : own) {
            AABB world = mine.getWorldAABB();
            envelope = envelope == null ? world : envelope.minmax(world);
            for (VoxelShape shape : entity.level().getBlockCollisions(entity, world.inflate(slop))) {
                for (AABB block : shape.toAabbs()) {
                    double depth = OrientedBox.penetration(mine, new OrientedBox(block), axis);
                    if (depth > slop) {
                        return new ClimbPoseFailure("terrain", depth,
                                String.format("%.2f,%.2f,%.2f", block.getCenter().x,
                                        block.getCenter().y, block.getCenter().z));
                    }
                }
            }
        }
        if (envelope == null) {
            return new ClimbPoseFailure("geometry", 0.0, "none");
        }
        for (Entity other : entity.level().getEntities(entity, envelope.inflate(slop),
                e -> e.isAlive() && e.canBeCollidedWith())) {
            LivingBlockCollisionShapes.Placement placement = LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null) {
                OrientedBox theirs = new OrientedBox(other.getBoundingBox());
                for (OrientedBox mine : own) {
                    double depth = OrientedBox.penetration(mine, theirs, axis);
                    if (depth > slop) {
                        return new ClimbPoseFailure("entity", depth,
                                other.getId() + ":" + other.getType().toShortString());
                    }
                }
                continue;
            }
            for (OrientedBox mine : own) {
                for (OrientedBox theirs : placement.obbs()) {
                    double depth = OrientedBox.penetration(mine, theirs, axis);
                    if (depth > slop) {
                        return new ClimbPoseFailure("entity", depth,
                                other.getId() + ":" + other.getType().toShortString());
                    }
                }
            }
        }
        return null;
    }

    private static double anchorWorstResidual;

    private static final List<List<OrientedBox>> EMPTY_PER_BOX = List.of();
    private static final int MAX_STEP_CANDIDATES = 8;
    private static final double SEPARATION_PER_TICK = 0.15;
    private static final double SEPARATION_EPSILON = 1.0E-4;
    private static final double TERRAIN_ESCAPE_MARGIN = 0.1;
    private static final double TERRAIN_FIT_EPSILON = 1.0E-4;
    private static final double ESCAPE_DEGENERATE = 1.0E-4;
    private static final double TERRAIN_CONTACT = 1.0E-3;
    private static final double SOLVER_STALL_WANTED = 2.5E-3;
    private static final double SOLVER_STALL_FRACTION = 0.05;
    private static final double ENTZERO_MIN_WANT_SQR = 1.0E-8;
    private static final double ENTZERO_WORLD_FRACTION = 0.25;
    private static final double SUPPORT_MIN_CONTACT = 0.03125;
    static final double RIM_PROBE = 1.0E-3;

    private static final double CONTACT_FOOT = 0.1;

    private static final double GROUND_ON_BODY = 6.0E-2;
    private static final double SLINGSHOT_THRESHOLD = 0.05;

    private static final double STEP_SOURCE_EPSILON = 1.0E-9;
    private static final double VBLOCK_MIN_FALL = 1.0E-4;
    private static final float LIFT_ESCAPE_SLOP = 1.0E-7F;
    private static final double DRIFT_EPSILON_SQR = 1.0E-8;
    private static final double CARRY_EPSILON_SQR = 1.0E-8;
    private static final double CARRY_SURFACE_SLACK = 0.2;
    private static final double CARRY_LIFT_LIMIT = 0.1;
    private static final int CARRY_HOLD_TICKS = 5;
    private static final double CARRY_SEAT_SLOP = 1.0E-3;
    private static final double CARRY_ESCAPE_LIMIT = 0.6;
    private static final double RIGID_TURN_DEGREES = 1.0E-3;
    private static final double OVERLAP_LOG_THRESHOLD = 0.06;
    private static final int OVERLAP_LOG_INTERVAL = 40;
    private static final int SWEEP_LOG_INTERVAL = 40;
    private static final double YIELD_EPSILON = 0.05;
    private static final double YIELD_MAJOR = 0.8;
    private static final double YIELD_MINOR = 0.2;
    private static final double SEPARATION_DEADLOCK_DEPTH = 0.05;
    private static final double SEPARATION_MIN_SHARE = 0.25;
    private static final double SEPARATION_YIELD_FLOOR = 0.15;
    private static final double SLIDE_PROBE = 1.0E-3;
    private static final double SLIDE_MIN_TANGENT_SQR = 1.0E-8;
    private static final double SLIDE_ENVELOPE = 1.0E-7;
    public static boolean ENTITY_FOOTPRINT = "on".equals(System.getProperty("entity.footprint", "off"));

    public static Vec3 separation(LivingBlock mover) {
        LivingBlockCollisionShapes.Placement own = LivingBlockCollisionShapes.preciseGeometry(mover);
        if (own == null) {
            return Vec3.ZERO;
        }

        AABB hull = mover.getBoundingBox();
        List<LivingBlock> others = mover.level().getEntitiesOfClass(LivingBlock.class,
                hull.inflate(RIM_PROBE), other -> other != mover && other.isAlive());
        boolean sample = mover.tickCount % OVERLAP_LOG_INTERVAL == 0;
        if (others.isEmpty() && sample) {
            if (shouldLog) LOGGER.debug("[livingblock] sep id={} others=0 near={} hull={} boxes={}",
                    mover.getId(),
                    mover.level().getEntitiesOfClass(LivingBlock.class, hull.inflate(1.0),
                            other -> other != mover && other.isAlive()).size(),
                    fmt(hull), fmt(union(own.boxes())));
        }

        double worst = 0.0;
        double phantom = 0.0;
        double pushX = 0.0;
        double pushY = 0.0;
        double pushZ = 0.0;
        LivingBlock worstOther = null;
        int rimmed = -1;
        double rimX = 0.0;
        double rimZ = 0.0;
        Vector3f axis = new Vector3f();

        Vec3 mineTarget = mover.getTargetPosition();
        double mineToTarget = mineTarget == null ? 0.0 : mineTarget.distanceToSqr(mover.position());
        boolean mineTravelling = mineTarget != null;

        for (LivingBlock other : others) {
            LivingBlockCollisionShapes.Placement theirs = LivingBlockCollisionShapes.preciseGeometry(other);
            if (theirs == null) {
                continue;
            }
            Vec3 theirTarget = other.getTargetPosition();
            double theirsToTarget = theirTarget == null ? 0.0 : theirTarget.distanceToSqr(other.position());
            boolean theirsTravelling = theirTarget != null;
            double share = yieldShare(mineTravelling, mineToTarget, theirsTravelling, theirsToTarget);

            double bestDepth = 0.0;
            double bestAmount = 0.0;
            double bestAxisX = 0.0;
            double bestAxisY = 0.0;
            double bestAxisZ = 0.0;

            for (int i = 0; i < own.boxes().size(); i++) {
                for (int j = 0; j < theirs.boxes().size(); j++) {
                    AABB mine = own.boxes().get(i);
                    AABB block = theirs.boxes().get(j);
                    if (!mine.intersects(block)) {
                        continue;
                    }
                    phantom = Math.max(phantom, Math.min(
                            Math.min(mine.maxX - block.minX, block.maxX - mine.minX),
                            Math.min(Math.min(mine.maxY - block.minY, block.maxY - mine.minY),
                                    Math.min(mine.maxZ - block.minZ, block.maxZ - mine.minZ))));

                    double depth = OrientedBox.penetration(own.obbs().get(i), theirs.obbs().get(j), axis);
                    if (depth <= 0.0) {
                        continue;
                    }
                    if (depth > worst) {
                        worst = depth;
                        worstOther = other;
                    }
                    if (depth <= bestDepth) {
                        continue;
                    }
                    bestDepth = depth;
                    double effective = Math.max(share, depth > SEPARATION_DEADLOCK_DEPTH
                            ? SEPARATION_MIN_SHARE : SEPARATION_YIELD_FLOOR);
                    bestAmount = (depth + SEPARATION_EPSILON) * effective;
                    bestAxisX = axis.x();
                    bestAxisY = axis.y();
                    bestAxisZ = axis.z();
                }
            }
            if (bestDepth == 0.0 && mover.isOrientationSettled() && other.isOrientationSettled()) {
                Vec3 rim = rimShove(own.boxes(), theirs.boxes());
                if (rim != null) {
                    rimmed = other.getId();
                    rimX = deepest(rimX, rim.x);
                    rimZ = deepest(rimZ, rim.z);
                }
            }
            pushX = deepest(pushX, bestAxisX * bestAmount);
            pushY = deepest(pushY, bestAxisY * bestAmount);
            pushZ = deepest(pushZ, bestAxisZ * bestAmount);
        }
        if (worst == 0.0) {
            pushX = deepest(pushX, rimX);
            pushZ = deepest(pushZ, rimZ);
        } else {
            rimmed = -1;
        }
        if (mover.recordRim(rimmed) && rimmed >= 0) {
            if (shouldLog) LOGGER.debug("[livingblock] rim id={} over={} push={}", mover.getId(), rimmed,
                    fmt(new Vec3(pushX, 0.0, pushZ)));
        }
        List<OrientedBox> terrain = blockObbs(mover, hull.inflate(TERRAIN_ESCAPE_MARGIN));
        double buried = 0.0;
        double escapeX = 0.0;
        double escapeY = 0.0;
        double escapeZ = 0.0;
        for (OrientedBox box : own.obbs()) {
            for (OrientedBox block : terrain) {
                double depth = OrientedBox.penetration(box, block, axis);
                if (depth <= 0.0) {
                    continue;
                }
                buried = Math.max(buried, depth);
                double amount = depth + SEPARATION_EPSILON;
                escapeX += axis.x() * amount;
                escapeY += axis.y() * amount;
                escapeZ += axis.z() * amount;
            }
        }
        if (buried > TERRAIN_CONTACT) {
            escapeY = Math.max(escapeY, ESCAPE_DEGENERATE);
            double escapeLength = Math.sqrt(escapeX * escapeX + escapeY * escapeY + escapeZ * escapeZ);
            Vec3 out = new Vec3(escapeX / escapeLength, escapeY / escapeLength, escapeZ / escapeLength)
                    .scale(Math.min(SEPARATION_PER_TICK, buried + SEPARATION_EPSILON));
            mover.recordEscape(buried, out, terrain.size());
            return out;
        }
        mover.recordEscape(0.0, Vec3.ZERO, terrain.size());

        Vec3 push = new Vec3(pushX, pushY, pushZ);

        mover.recordOverlap(worst, worstOther == null ? -1 : worstOther.getId());
        if (!others.isEmpty() && (sample || worst > OVERLAP_LOG_THRESHOLD)) {
            if (shouldLog) LOGGER.debug("[livingblock] sep id={} others={} obb={} aabb={} with={} state={} piecesA={} sideB={} piecesB={} roll={} snap={} push={} ground={} hull={}",
                    mover.getId(), others.size(), String.format("%.3f", worst),
                    String.format("%.3f", phantom),
                    worstOther == null ? -1 : worstOther.getId(),
                    (mover.isOrientationSettled() ? "S" : "M")
                            + (worstOther == null ? "-" : worstOther.isOrientationSettled() ? "S" : "M"),
                    own.boxes().size(),
                    worstOther == null ? "none" : extent(worstOther.getBoundingBox()),
                    worstOther == null ? -1 : worstOther.getShapeBoxes().size(),
                    String.format("%.2f", mover.getRollAngle()),
                    String.format("%.2f", snapAngle(mover)),
                    fmt(push), mover.onGround(),
                    fmt(hull));
        }

        if (mover.isClimbing()) {
            Direction cling = mover.getClimbingDirection();
            if (cling.getStepX() != 0) {
                push = new Vec3(0.0, push.y, push.z);
            } else if (cling.getStepZ() != 0) {
                push = new Vec3(push.x, push.y, 0.0);
            }
        }

        double length = push.length();
        if (length < 1.0E-6) {
            return Vec3.ZERO;
        }
        if (length > SEPARATION_PER_TICK) {
            push = push.scale(SEPARATION_PER_TICK / length);
        }

        List<List<OrientedBox>> perBlock = narrow(own.boxes(), terrain, push, 0.0F);
        double outX = sweepAxis(own.obbs(), perBlock, 0.0, 0.0, 0.0, Direction.Axis.X, push.x);
        double outY = sweepAxis(own.obbs(), perBlock, outX, 0.0, 0.0, Direction.Axis.Y, push.y);
        double outZ = sweepAxis(own.obbs(), perBlock, outX, outY, 0.0, Direction.Axis.Z, push.z);
        return new Vec3(outX, outY, outZ);
    }

    public static boolean terrainClearAt(final LivingBlock mover, final AABB destination) {
        LivingBlockCollisionShapes.Placement own = LivingBlockCollisionShapes.preciseGeometry(mover);
        if (own == null || own.obbs().isEmpty()) {
            return false;
        }
        AABB hull = mover.getBoundingBox();
        double shiftX = destination.getCenter().x - hull.getCenter().x;
        double shiftY = destination.minY - hull.minY;
        double shiftZ = destination.getCenter().z - hull.getCenter().z;
        List<OrientedBox> blocks = blockObbs(mover, destination.inflate(TERRAIN_ESCAPE_MARGIN));
        if (blocks.isEmpty()) {
            return true;
        }
        Vector3f axis = new Vector3f();
        for (OrientedBox mine : own.obbs()) {
            AABB moved = mine.getWorldAABB().move(shiftX, shiftY, shiftZ);
            for (OrientedBox block : blocks) {
                if (!moved.intersects(block.getWorldAABB())) {
                    continue;
                }
                if (OrientedBox.sweep(mine, shiftX, shiftY, shiftZ, block, 0.0, 0.0, 0.0) <= 0.0) {
                    return false;
                }
                if (OrientedBox.penetration(new OrientedBox(moved), block, axis) > TERRAIN_FIT_EPSILON) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<OrientedBox> blockObbs(final LivingBlock mover, final AABB area) {
        List<OrientedBox> blocks = new ArrayList<>();
        for (VoxelShape shape : mover.level().getBlockCollisions(mover, area)) {
            if (shape.isEmpty()) {
                continue;
            }
            for (AABB box : shape.toAabbs()) {
                blocks.add(new OrientedBox(box));
            }
        }
        return blocks;
    }

    static double yieldShare(final boolean travelling, final double mine,
                                     final boolean otherTravelling, final double theirs) {
        if (travelling != otherTravelling) {
            return travelling ? 1.0 : 0.0;
        }
        if (!travelling) {
            return 0.5;
        }
        if (mine > theirs + YIELD_EPSILON) {
            return YIELD_MAJOR;
        }
        if (theirs > mine + YIELD_EPSILON) {
            return YIELD_MINOR;
        }
        return 0.5;
    }

    private static AABB union(final List<AABB> boxes) {
        AABB out = null;
        for (AABB box : boxes) {
            out = out == null ? box : out.minmax(box);
        }
        return out;
    }

    private static String fmt(@Nullable final AABB box) {
        return box == null ? "none" : String.format("%.2f,%.2f,%.2f..%.2f,%.2f,%.2f",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static List<LivingBlock> overlapNeighbours(final LivingBlock mover, final double margin) {
        return mover.level().getEntitiesOfClass(LivingBlock.class,
                mover.getBoundingBox().inflate(margin),
                other -> other != mover && other.isAlive());
    }

    public static double worstEntityOverlap(final LivingBlock mover) {
        return worstEntityOverlap(mover, overlapNeighbours(mover, RIM_PROBE));
    }

    public static double worstEntityOverlap(final LivingBlock mover, final List<LivingBlock> others) {
        LivingBlockCollisionShapes.Placement own = LivingBlockCollisionShapes.preciseGeometry(mover);
        if (own == null) {
            return 0.0;
        }
        if (others.isEmpty()) {
            return 0.0;
        }
        Vector3f axis = new Vector3f();
        double worst = 0.0;
        for (LivingBlock other : others) {
            LivingBlockCollisionShapes.Placement theirs = LivingBlockCollisionShapes.preciseGeometry(other);
            if (theirs == null) {
                continue;
            }
            for (OrientedBox mine : own.obbs()) {
                for (OrientedBox block : theirs.obbs()) {
                    worst = Math.max(worst, OrientedBox.penetration(mine, block, axis));
                }
            }
        }
        return worst;
    }

    @Nullable
    public static double[] rollOverlapPair(final LivingBlock mover, final double reach) {
        AABB hull = mover.getBoundingBox().inflate(reach);
        List<OrientedBox> terrain = blockObbs(mover, hull.inflate(TERRAIN_ESCAPE_MARGIN));
        List<LivingBlock> neighbours = mover.level().getEntitiesOfClass(LivingBlock.class, hull,
                candidate -> candidate != mover && candidate.isAlive());
        Vector3f axis = new Vector3f();
        double[] out = new double[2];
        for (OrientedBox mine : posedOBBs(mover, mover.getRotation(), mover.position())) {
            AABB mineBox = mine.getWorldAABB();
            for (OrientedBox block : terrain) {
                if (mineBox.intersects(block.getWorldAABB())) {
                    out[0] = Math.max(out[0], OrientedBox.penetration(mine, block, axis));
                }
            }
            for (LivingBlock other : neighbours) {
                LivingBlockCollisionShapes.Placement theirs =
                        LivingBlockCollisionShapes.preciseGeometry(other);
                if (theirs == null) {
                    continue;
                }
                for (OrientedBox box : theirs.obbs()) {
                    if (mineBox.intersects(box.getWorldAABB())) {
                        out[1] = Math.max(out[1], OrientedBox.penetration(mine, box, axis));
                    }
                }
            }
        }
        return out;
    }

    public static String rollOverlapReport(final LivingBlock mover, final double reach) {
        AABB hull = mover.getBoundingBox().inflate(reach);
        List<OrientedBox> terrain = blockObbs(mover, hull.inflate(TERRAIN_ESCAPE_MARGIN));
        List<LivingBlock> neighbours = mover.level().getEntitiesOfClass(LivingBlock.class, hull,
                candidate -> candidate != mover && candidate.isAlive());
        LivingBlockCollisionShapes.Placement rest = LivingBlockCollisionShapes.preciseGeometry(mover);
        List<List<OrientedBox>> shapes = List.of(
                posedOBBs(mover, mover.getRotation(), mover.position()),
                rest == null ? List.<OrientedBox>of() : rest.obbs());
        Vector3f axis = new Vector3f();
        double[] worst = new double[4];
        int blocker = -1;
        double worstBody = 0.0;
        for (int k = 0; k < shapes.size(); k++) {
            for (OrientedBox mine : shapes.get(k)) {
                AABB mineBox = mine.getWorldAABB();
                for (OrientedBox block : terrain) {
                    if (mineBox.intersects(block.getWorldAABB())) {
                        worst[k * 2] = Math.max(worst[k * 2],
                                OrientedBox.penetration(mine, block, axis));
                    }
                }
                for (LivingBlock other : neighbours) {
                    LivingBlockCollisionShapes.Placement theirs =
                            LivingBlockCollisionShapes.preciseGeometry(other);
                    if (theirs == null) {
                        continue;
                    }
                    for (OrientedBox box : theirs.obbs()) {
                        if (!mineBox.intersects(box.getWorldAABB())) {
                            continue;
                        }
                        double depth = OrientedBox.penetration(mine, box, axis);
                        worst[k * 2 + 1] = Math.max(worst[k * 2 + 1], depth);
                        if (k == 0 && depth > worstBody) {
                            worstBody = depth;
                            blocker = other.getId();
                        }
                    }
                }
            }
        }
        return String.format(
                "posedterrain=%.4f posedbody=%.4f restterrain=%.4f restbody=%.4f"
                        + " blocker=%d neigh=%d terrain=%d posedn=%d restn=%d",
                worst[0], worst[1], worst[2], worst[3], blocker, neighbours.size(),
                terrain.size(), shapes.get(0).size(), shapes.get(1).size());
    }

    public static java.util.function.DoubleSupplier rollOverlapProbe(final LivingBlock mover,
                                                                     final double reach) {
        AABB hull = mover.getBoundingBox().inflate(reach);
        List<OrientedBox> terrain = blockObbs(mover, hull.inflate(TERRAIN_ESCAPE_MARGIN));
        List<LivingBlockCollisionShapes.Placement> others = new ArrayList<>();
        if (mover.usesOrientedCollision()) {
            for (LivingBlock other : mover.level().getEntitiesOfClass(LivingBlock.class, hull,
                    candidate -> candidate != mover && candidate.isAlive())) {
                LivingBlockCollisionShapes.Placement theirs = LivingBlockCollisionShapes.preciseGeometry(other);
                if (theirs != null) {
                    others.add(theirs);
                }
            }
        }
        if (terrain.isEmpty() && others.isEmpty()) {
            return null;
        }
        Vector3f axis = new Vector3f();
        return () -> {
            List<OrientedBox> own = posedOBBs(mover, mover.getRotation(), mover.position());
            if (own.isEmpty()) {
                return 0.0;
            }
            double worst = 0.0;
            for (OrientedBox mine : own) {
                AABB mineBox = mine.getWorldAABB();
                for (OrientedBox block : terrain) {
                    if (mineBox.intersects(block.getWorldAABB())) {
                        worst = Math.max(worst, OrientedBox.penetration(mine, block, axis));
                    }
                }
                for (LivingBlockCollisionShapes.Placement theirs : others) {
                    for (OrientedBox box : theirs.obbs()) {
                        if (mineBox.intersects(box.getWorldAABB())) {
                            worst = Math.max(worst, OrientedBox.penetration(mine, box, axis));
                        }
                    }
                }
            }
            return worst;
        };
    }

    static double deepest(final double current, final double candidate) {
        return Math.abs(candidate) > Math.abs(current) ? candidate : current;
    }

    private static String fmt(final Vec3 vec) {
        return String.format("%.4f,%.4f,%.4f", vec.x, vec.y, vec.z);
    }

    private static double snapAngle(final LivingBlock entity) {
        Quaternionf last = entity.getLastRotation();
        Quaternionf now = entity.getRotation();
        double dot = Math.abs((double) last.x() * now.x() + (double) last.y() * now.y()
                + (double) last.z() * now.z() + (double) last.w() * now.w());
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    @Nullable
    public static Vec3 resolveMovement(LivingBlock mover, Vec3 movement) {
        if (movement.lengthSqr() == 0.0) {
            return null;
        }

        AABB hull = mover.getBoundingBox();
        AABB swept = hull.expandTowards(movement).expandTowards(0.0, mover.maxUpStep(), 0.0);
        Level level = mover.level();

        LivingBlockCollisionShapes.Placement own = LivingBlockCollisionShapes.preciseGeometry(mover);
        if (own == null || own.boxes().isEmpty()) {
            return null;
        }

        int[] drifting = new int[1];
        List<OrientedBox> colliders = entityColliders(mover, swept, drifting);
        List<OrientedBox> blocks = blockObbs(mover, swept);
        List<VoxelShape> border = List.of();
        WorldBorder worldBorder = level.getWorldBorder();
        if (worldBorder.isInsideCloseToBorder(mover, swept)) {
            border = List.of(worldBorder.getCollisionShape());
        }
        if (colliders.isEmpty() && blocks.isEmpty() && border.isEmpty()) {
            return null;
        }

        List<OrientedBox> mine = own.obbs();
        float step = mover.maxUpStep();
        List<List<OrientedBox>> perBlock = narrow(own.boxes(), blocks, movement, step);
        List<List<OrientedBox>> perEntity = narrow(own.boxes(), colliders, movement, step);
        Vec3 resolved = collideCombined(movement, hull, mine, border, perBlock, perEntity, Vec3.ZERO);

        if (!level.isClientSide() && (mover.wantsTrace() || mover.tickCount % SWEEP_LOG_INTERVAL == 0)) {
            Vec3 blocksOnly = collideCombined(movement, hull, mine, border, perBlock, EMPTY_PER_BOX, Vec3.ZERO);
            mover.recordResolve(movement, blocksOnly, resolved, 0.0,
                    mine.size(), blocks.size(), colliders.size());
        }

        if (!level.isClientSide()) {
            double wanted = movement.x * movement.x + movement.z * movement.z;
            double got = resolved.x * resolved.x + resolved.z * resolved.z;
            mover.reportSolverBlocked(wanted > SOLVER_STALL_WANTED && got < wanted * SOLVER_STALL_FRACTION
                    && mover.onGround() && mover.hasMovementTarget());

            boolean entityZeroed = wanted > ENTZERO_MIN_WANT_SQR && resolved.x == 0.0 && resolved.z == 0.0
                    && !colliders.isEmpty();
            if (entityZeroed) {
                Vec3 world = collideCombined(movement, hull, mine, border, perBlock, EMPTY_PER_BOX, Vec3.ZERO);
                entityZeroed = world.x * world.x + world.z * world.z >= wanted * ENTZERO_WORLD_FRACTION;
            }
            mover.reportEntityZero(entityZeroed, Math.sqrt(wanted), colliders.size(), drifting[0]);

            boolean blockedDown = movement.y < -VBLOCK_MIN_FALL && resolved.y > movement.y + VBLOCK_MIN_FALL;
            boolean blockedUp = movement.y > VBLOCK_MIN_FALL && resolved.y < movement.y - VBLOCK_MIN_FALL;
            if ((blockedDown || blockedUp) && mover.shouldLogVerticalBlock()) {
                Vec3 world = collideCombined(movement, hull, mine, border, perBlock, EMPTY_PER_BOX, Vec3.ZERO);
                boolean byBlock = blockedDown
                        ? world.y > movement.y + VBLOCK_MIN_FALL
                        : world.y < movement.y - VBLOCK_MIN_FALL;
                if (shouldLog) LOGGER.debug("[livingblock] vblock id={} dir={} want={} got={} terrain={} by={} climb={} air={} blocks={} colliders={} pos={}",
                        mover.getId(), blockedUp ? "up" : "down",
                        String.format("%.4f", movement.y),
                        String.format("%.4f", resolved.y), String.format("%.4f", world.y),
                        byBlock ? "block" : "entity", mover.isClimbing(),
                        mover.getAirTicks(), blocks.size(), colliders.size(),
                        String.format("%.2f,%.2f,%.2f", mover.getX(), mover.getY(), mover.getZ()));
            }
        }

        boolean blockedHorizontally = movement.x != resolved.x || movement.z != resolved.z;
        boolean falling = movement.y != resolved.y && movement.y < 0.0;
        if (step <= 0.0F || !blockedHorizontally || !(falling || mover.onGround())) {
            return resolved;
        }

        Vector3f escape = new Vector3f();
        if (liftDeepensOverlap(mine, perEntity, escape)) {
            if (!level.isClientSide() && mover.tickCount % SWEEP_LOG_INTERVAL == 0) {
                if (shouldLog) LOGGER.debug("[livingblock] liftveto id={} escape={} step={} colliders={}",
                        mover.getId(),
                        String.format("%.3f,%.3f,%.3f", escape.x(), escape.y(), escape.z()),
                        String.format("%.2f", step), colliders.size());
            }
            return resolved;
        }

        AABB liftedHull = falling ? hull.move(0.0, resolved.y, 0.0) : hull;
        Vec3 lifted = falling ? new Vec3(0.0, resolved.y, 0.0) : Vec3.ZERO;
        double reach = resolved.horizontalDistanceSqr();
        Vec3 stepUpMovement = mover.adjustStepUpMovement(movement);
        if (stepUpMovement.x == 0.0 && stepUpMovement.z == 0.0) {
            return resolved;
        }
        double bestReach = reach;
        double bestHeight = 0.0;
        Vec3 best = null;
        double shyGain = 0.0;
        double shyHeight = 0.0;
        for (double height : stepHeights(liftedHull, blocks, colliders, step, resolved.y)) {
            Vec3 candidate = collideCombined(new Vec3(stepUpMovement.x, height, stepUpMovement.z),
                    liftedHull, mine, border, perBlock, perEntity, lifted);
            double gain = candidate.horizontalDistanceSqr();
            if (gain > bestReach) {
                bestReach = gain;
                bestHeight = height;
                best = candidate;
                break;
            } else if (gain > shyGain) {
                shyGain = gain;
                shyHeight = height;
            }
        }
        if (best == null && shyHeight > 0.0 && !level.isClientSide()) {
            if (shouldLog) LOGGER.debug("[livingblock] stepshy id={} height={} gain={} need={} asked={} blocks={} bodies={}",
                    mover.getId(), String.format("%.4f", shyHeight),
                    String.format("%.5f", Math.sqrt(shyGain)),
                    String.format("%.5f", Math.sqrt(reach)),
                    String.format("%.4f", stepUpMovement.horizontalDistance()),
                    blocks.size(), colliders.size());
        }
        {
            Vec3 candidate = best;
            double height = bestHeight;
            if (candidate != null) {
                Vec3 stepped = candidate.add(0.0, falling ? resolved.y : 0.0, 0.0);
                if (!level.isClientSide()) {
                    mover.recordResolve(movement, resolved, stepped, height,
                            mine.size(), blocks.size(), colliders.size());
                    if (stepped.y > SLINGSHOT_THRESHOLD) {
                        boolean fromEntity = false;
                        for (OrientedBox collider : colliders) {
                            AABB box = collider.getWorldAABB();
                            if (Math.abs(box.minY - liftedHull.minY - height) < STEP_SOURCE_EPSILON
                                    || Math.abs(box.maxY - liftedHull.minY - height) < STEP_SOURCE_EPSILON) {
                                fromEntity = true;
                                break;
                            }
                        }
                        if (shouldLog) LOGGER.debug("[livingblock] slingshot id={} up={} height={} maxUpStep={} from={} ground={} in={} settled={}",
                                mover.getId(), String.format("%.3f", stepped.y),
                                String.format("%.3f", height), step,
                                fromEntity ? "entity" : "block", mover.onGround(),
                                fmt(movement), mover.isOrientationSettled());
                    }
                }
                return stepped;
            }
        }
        return tangentialSlide(mover, movement, resolved, hull, own, border, perEntity,
                colliders.size());
    }

    private static Vec3 tangentialSlide(final LivingBlock mover, final Vec3 movement, final Vec3 resolved,
                                        final AABB hull, final LivingBlockCollisionShapes.Placement own,
                                        final List<VoxelShape> border,
                                        final List<List<OrientedBox>> perEntity,
                                        final int colliderCount) {
        if (resolved.x != 0.0 || resolved.z != 0.0) {
            return resolved;
        }
        double wanted = movement.x * movement.x + movement.z * movement.z;
        if (wanted <= ENTZERO_MIN_WANT_SQR) {
            return resolved;
        }

        List<OrientedBox> mine = own.obbs();
        double lift = resolved.y;
        double probe = SLIDE_PROBE / Math.sqrt(wanted);
        Vector3f axis = new Vector3f();
        Vector3f normal = new Vector3f();
        double deepest = 0.0;
        for (int i = 0; i < mine.size(); i++) {
            OrientedBox box = mine.get(i);
            for (OrientedBox other : perEntity.get(i)) {
                double depth = OrientedBox.mtvAt(box, movement.x * probe, lift, movement.z * probe,
                        other, axis);
                if (depth > deepest) {
                    deepest = depth;
                    normal.set(axis);
                }
            }
        }
        if (deepest <= 0.0) {
            return resolved;
        }

        double into = movement.x * normal.x() + movement.z * normal.z();
        if (into >= 0.0) {
            return resolved;
        }
        double tangentX = movement.x - into * normal.x();
        double tangentZ = movement.z - into * normal.z();
        if (tangentX * tangentX + tangentZ * tangentZ < SLIDE_MIN_TANGENT_SQR) {
            return resolved;
        }

        AABB slideHull = hull.move(0.0, lift, 0.0);
        AABB slideSwept = slideHull.expandTowards(tangentX, 0.0, tangentZ).inflate(SLIDE_ENVELOPE);
        Vec3 envelope = new Vec3(tangentX, lift, tangentZ);
        List<List<OrientedBox>> slideBlocks =
                narrow(own.boxes(), blockObbs(mover, slideSwept), envelope, 0.0F);
        List<List<OrientedBox>> slideEntities =
                narrow(own.boxes(), entityColliders(mover, slideSwept, new int[1]), envelope, 0.0F);
        Vec3 out = collideCombined(new Vec3(tangentX, 0.0, tangentZ), slideHull, mine, border,
                slideBlocks, slideEntities, new Vec3(0.0, lift, 0.0));
        if (out.x == 0.0 && out.z == 0.0) {
            return resolved;
        }
        if (!mover.level().isClientSide() && mover.shouldLogSlide()) {
            if (shouldLog) LOGGER.debug("[livingblock] slide id={} n={} in={} tan={} out={} colliders={} boxes={}",
                    mover.getId(),
                    String.format("%.3f,%.3f,%.3f", normal.x(), normal.y(), normal.z()),
                    String.format("%.4f,%.4f", movement.x, movement.z),
                    String.format("%.4f,%.4f", tangentX, tangentZ),
                    String.format("%.4f,%.4f", out.x, out.z),
                    colliderCount, mine.size());
        }
        return new Vec3(out.x, resolved.y, out.z);
    }

    static boolean liftDeepensOverlap(List<OrientedBox> mine, List<List<OrientedBox>> perBox,
                                              Vector3f outEscape) {
        if (perBox.isEmpty()) {
            return false;
        }
        Vector3f axis = new Vector3f();
        for (int i = 0; i < mine.size(); i++) {
            for (OrientedBox other : perBox.get(i)) {
                if (OrientedBox.penetration(mine.get(i), other, axis) > 0.0
                        && axis.y() < -LIFT_ESCAPE_SLOP) {
                    outEscape.set(axis);
                    return true;
                }
            }
        }
        return false;
    }

    private static List<OrientedBox> entityColliders(LivingBlock mover, AABB swept, int[] drifting) {
        List<OrientedBox> colliders = new ArrayList<>();
        for (Entity other : mover.level().getEntities(mover, swept,
                EntitySelector.NO_SPECTATORS.and(mover::canCollideWith))) {
            LivingBlockCollisionShapes.Placement precise = LivingBlockCollisionShapes.preciseGeometry(other);
            if (precise == null) {
                colliders.add(new OrientedBox(other.getBoundingBox()));
                continue;
            }
            Vec3 drift = other instanceof LivingBlock ? other.getDeltaMovement() : Vec3.ZERO;
            boolean driftingSideways = drift.x * drift.x + drift.z * drift.z > DRIFT_EPSILON_SQR;
            List<OrientedBox> obbs = precise.obbs();
            for (int i = 0; i < obbs.size(); i++) {
                OrientedBox obb = obbs.get(i);
                AABB box = obb.getWorldAABB();
                AABB reach = driftingSideways ? box.expandTowards(drift.x, 0.0, drift.z) : box;
                if (reach.intersects(swept)) {
                    colliders.add(obb);
                    if (driftingSideways) {
                        drifting[0]++;
                    }
                }
            }
        }
        return colliders;
    }

    static List<List<OrientedBox>> narrow(List<AABB> boxes, List<OrientedBox> colliders,
                                                  Vec3 movement, float step) {
        List<List<OrientedBox>> perBox = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            AABB envelope = box.expandTowards(movement).expandTowards(0.0, step, 0.0).inflate(1.0E-7);
            List<OrientedBox> reachable = new ArrayList<>(4);
            for (OrientedBox collider : colliders) {
                if (collider.getWorldAABB().intersects(envelope)) {
                    reachable.add(collider);
                }
            }
            perBox.add(reachable);
        }
        return perBox;
    }

    static Vec3 collideCombined(Vec3 delta, AABB hull, List<OrientedBox> mine,
                                        List<VoxelShape> border, List<List<OrientedBox>> perBlock,
                                        List<List<OrientedBox>> perEntity, Vec3 start) {
        double dx = delta.x;
        double dy = delta.y;
        double dz = delta.z;
        AABB currentHull = hull;
        double offsetX = start.x;
        double offsetY = start.y;
        double offsetZ = start.z;

        if (dy != 0.0) {
            dy = axisPass(mine, border, perBlock, perEntity, currentHull,
                    offsetX, offsetY, offsetZ, Direction.Axis.Y, dy);
            if (dy != 0.0) {
                currentHull = currentHull.move(0.0, dy, 0.0);
                offsetY += dy;
            }
        }

        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0.0) {
            dz = axisPass(mine, border, perBlock, perEntity, currentHull,
                    offsetX, offsetY, offsetZ, Direction.Axis.Z, dz);
            if (dz != 0.0) {
                currentHull = currentHull.move(0.0, 0.0, dz);
                offsetZ += dz;
            }
        }

        if (dx != 0.0) {
            dx = axisPass(mine, border, perBlock, perEntity, currentHull,
                    offsetX, offsetY, offsetZ, Direction.Axis.X, dx);
            if (!zFirst && dx != 0.0) {
                currentHull = currentHull.move(dx, 0.0, 0.0);
                offsetX += dx;
            }
        }

        if (!zFirst && dz != 0.0) {
            dz = axisPass(mine, border, perBlock, perEntity, currentHull,
                    offsetX, offsetY, offsetZ, Direction.Axis.Z, dz);
        }

        return new Vec3(dx, dy, dz);
    }

    private static double axisPass(List<OrientedBox> mine, List<VoxelShape> border,
                                   List<List<OrientedBox>> perBlock, List<List<OrientedBox>> perEntity,
                                   AABB currentHull, double offsetX, double offsetY, double offsetZ,
                                   Direction.Axis axis, double delta) {
        if (!border.isEmpty()) {
            delta = Shapes.collide(axis, currentHull, border, delta);
        }
        delta = sweepAxis(mine, perBlock, offsetX, offsetY, offsetZ, axis, delta);
        return sweepAxis(mine, perEntity, offsetX, offsetY, offsetZ, axis, delta, ENTITY_FOOTPRINT);
    }

    static double sweepAxis(List<OrientedBox> mine, List<List<OrientedBox>> perBox,
                                    double offsetX, double offsetY, double offsetZ,
                                    Direction.Axis axis, double delta) {
        return sweepAxis(mine, perBox, offsetX, offsetY, offsetZ, axis, delta, false);
    }

    private static double sweepAxis(List<OrientedBox> mine, List<List<OrientedBox>> perBox,
                                    double offsetX, double offsetY, double offsetZ,
                                    Direction.Axis axis, double delta, boolean needsFootprint) {
        if (delta == 0.0 || mine.isEmpty() || perBox.isEmpty()) {
            return delta;
        }
        double motionX = axis == Direction.Axis.X ? delta : 0.0;
        double motionY = axis == Direction.Axis.Y ? delta : 0.0;
        double motionZ = axis == Direction.Axis.Z ? delta : 0.0;
        boolean landing = needsFootprint && axis == Direction.Axis.Y && delta < 0.0;

        double fraction = 1.0;
        for (int i = 0; i < mine.size(); i++) {
            List<OrientedBox> reachable = perBox.get(i);
            if (reachable.isEmpty()) {
                continue;
            }
            OrientedBox box = mine.get(i);
            for (int j = 0; j < reachable.size(); j++) {
                OrientedBox other = reachable.get(j);
                if (landing && !overlapsFootprint(box.getWorldAABB(), offsetX, offsetZ,
                        other.getWorldAABB())) {
                    continue;
                }
                double allowed = OrientedBox.sweep(box, offsetX, offsetY, offsetZ, other,
                        motionX, motionY, motionZ);
                if (allowed < fraction) {
                    fraction = allowed;
                    if (fraction <= 0.0) {
                        return 0.0;
                    }
                }
            }
        }

        double result = delta * fraction;
        return Math.abs(result) < 1.0E-7 ? 0.0 : result;
    }

    static boolean overlapsFootprint(final AABB mine, final double offsetX, final double offsetZ,
                                     final AABB other) {
        double spanX = Math.min(mine.maxX + offsetX, other.maxX) - Math.max(mine.minX + offsetX, other.minX);
        double spanZ = Math.min(mine.maxZ + offsetZ, other.maxZ) - Math.max(mine.minZ + offsetZ, other.minZ);
        return spanX >= SUPPORT_MIN_CONTACT && spanZ >= SUPPORT_MIN_CONTACT;
    }

    private static final double RAMP_BLOCKED_FRACTION = 0.25;
    private static final double RAMP_MIN_WANT_SQR = 1.0E-6;
    private static final double RAMP_MIN_UP = 0.05;
    private static final double RAMP_MAX_UP = 0.999;
    private static final double RAMP_PROBE = 0.1;
    private static final double RAMP_SLACK = 1.0E-6;

    public static @Nullable Vec3 slideOnBody(final Entity mover, final Vec3 wanted, final Vec3 got) {
        double wantH = wanted.x * wanted.x + wanted.z * wanted.z;
        if (wantH < RAMP_MIN_WANT_SQR || !mover.onGround() || mover instanceof LivingBlock) {
            return null;
        }
        if (got.x * got.x + got.z * got.z > wantH * RAMP_BLOCKED_FRACTION) {
            return null;
        }
        AABB box = mover.getBoundingBox();
        AABB probe = box.expandTowards(wanted.x, 0.0, wanted.z).inflate(RAMP_PROBE);
        Vec3 flat = new Vec3(wanted.x, 0.0, wanted.z);
        Vec3 dir = flat.normalize();

        Vec3 face = null;
        double best = 0.0;
        int neighbours = 0;
        int boxes = 0;
        double bestY = 0.0;
        for (LivingBlock other : mover.level().getEntitiesOfClass(LivingBlock.class, probe,
                candidate -> candidate.isAlive() && candidate != mover)) {
            neighbours++;
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null) {
                continue;
            }
            for (OrientedBox obb : placement.obbs()) {
                if (!obb.getWorldAABB().intersects(probe)) {
                    continue;
                }
                boxes++;
                for (Vector3f axis : obb.getAxes()) {
                    for (int sign = -1; sign <= 1; sign += 2) {
                        Vec3 normal = new Vec3(axis.x() * sign, axis.y() * sign, axis.z() * sign);
                        if (normal.y <= RAMP_MIN_UP || normal.y >= RAMP_MAX_UP) {
                            continue;
                        }
                        bestY = Math.max(bestY, Math.abs(normal.y));
                        double facing = -normal.dot(dir);
                        if (facing > best) {
                            best = facing;
                            face = normal;
                        }
                    }
                }
            }
        }
        if (face == null) {
            if (shouldLog) LOGGER.debug("[livingblock] bodyslide who={} gate=noface neighbours={} boxes={} maxY={}",
                    mover.getType().toShortString(), neighbours, boxes,
                    String.format("%.4f", bestY));
            return null;
        }
        Vec3 slide = flat.subtract(face.scale(flat.dot(face)));
        if (slide.y <= 0.0) {
            return null;
        }
        double step = mover.maxUpStep();
        if (slide.y > step) {
            slide = slide.scale(step / slide.y);
        }
        double now = bodyOverlapAt(mover, box);
        if (destinationFree(mover, box.move(slide), now)) {
            return slide;
        }
        Vec3 half = slide.scale(0.5);
        if (destinationFree(mover, box.move(half), now)) {
            return half;
        }
        if (shouldLog) LOGGER.debug("[livingblock] bodyslide who={} gate=desttaken face={} slide={} now={} dest={} half={} blocks={}",
                mover.getType().toShortString(), fmt(face), fmt(slide),
                String.format("%.7f", now),
                String.format("%.7f", bodyOverlapAt(mover, box.move(slide))),
                String.format("%.7f", bodyOverlapAt(mover, box.move(half))),
                mover.level().noBlockCollision(mover, box.move(slide)));
        return null;
    }

    private static boolean destinationFree(final Entity mover, final AABB dest, final double now) {
        return mover.level().noBlockCollision(mover, dest)
                && bodyOverlapAt(mover, dest) <= now + RAMP_SLACK;
    }

    private static double bodyOverlapAt(final Entity mover, final AABB box) {
        OrientedBox target = new OrientedBox(box);
        Vector3f axis = new Vector3f();
        double worst = 0.0;
        for (LivingBlock other : mover.level().getEntitiesOfClass(LivingBlock.class, box.inflate(RAMP_PROBE),
                candidate -> candidate.isAlive() && candidate != mover)) {
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null) {
                continue;
            }
            for (OrientedBox obb : placement.obbs()) {
                worst = Math.max(worst, OrientedBox.penetration(target, obb, axis));
            }
        }
        return worst;
    }

    static int floorBody(final LivingBlock mover) {
        LivingBlockCollisionShapes.Placement own =
                LivingBlockCollisionShapes.preciseGeometry(mover);
        if (own == null) {
            return -1;
        }
        Vec3 low = lowestPoint(own.obbs());
        if (low == null) {
            return -1;
        }
        AABB foot = new AABB(low.x - CONTACT_FOOT, low.y - GROUND_ON_BODY, low.z - CONTACT_FOOT,
                low.x + CONTACT_FOOT, low.y + GROUND_ON_BODY, low.z + CONTACT_FOOT);
        for (LivingBlock other : mover.level().getEntitiesOfClass(LivingBlock.class, foot,
                candidate -> candidate != mover && candidate.isAlive()
                        && mover.canCollideWith(candidate))) {
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null) {
                continue;
            }
            if (highestSurfaceUnder(placement.obbs(), foot,
                    low.y - GROUND_ON_BODY, low.y + GROUND_ON_BODY).isPresent()) {
                return other.getId();
            }
        }
        return -1;
    }

    private static @Nullable Vec3 lowestPoint(final List<OrientedBox> boxes) {
        Vec3 best = null;
        for (OrientedBox obb : boxes) {
            Vector3f c = obb.getCenter();
            Vector3f e = obb.getExtents();
            Vector3f[] a = obb.getAxes();
            for (int corner = 0; corner < 8; corner++) {
                float sx = (corner & 1) == 0 ? -1.0F : 1.0F;
                float sy = (corner & 2) == 0 ? -1.0F : 1.0F;
                float sz = (corner & 4) == 0 ? -1.0F : 1.0F;
                double py = c.y() + a[0].y() * e.x() * sx + a[1].y() * e.y() * sy
                        + a[2].y() * e.z() * sz;
                if (best != null && py >= best.y) {
                    continue;
                }
                best = new Vec3(
                        c.x() + a[0].x() * e.x() * sx + a[1].x() * e.y() * sy + a[2].x() * e.z() * sz,
                        py,
                        c.z() + a[0].z() * e.x() * sx + a[1].z() * e.y() * sy + a[2].z() * e.z() * sz);
            }
        }
        return best;
    }

    static Vec3 rimShove(final List<AABB> mine, final List<AABB> theirs) {
        double thinnest = SUPPORT_MIN_CONTACT;
        double shoveX = 0.0;
        double shoveZ = 0.0;
        for (AABB box : mine) {
            for (AABB under : theirs) {
                if (Math.abs(box.minY - under.maxY) > RIM_PROBE) {
                    continue;
                }
                double spanX = Math.min(box.maxX, under.maxX) - Math.max(box.minX, under.minX);
                double spanZ = Math.min(box.maxZ, under.maxZ) - Math.max(box.minZ, under.minZ);
                if (spanX <= 0.0 || spanZ <= 0.0) {
                    continue;
                }
                double thin = Math.min(spanX, spanZ);
                if (thin >= SUPPORT_MIN_CONTACT) {
                    return null;
                }
                if (thin >= thinnest) {
                    continue;
                }
                thinnest = thin;
                double amount = thin + SEPARATION_EPSILON;
                boolean alongX = spanX <= spanZ;
                shoveX = alongX ? Math.copySign(amount, box.getCenter().x - under.getCenter().x) : 0.0;
                shoveZ = alongX ? 0.0 : Math.copySign(amount, box.getCenter().z - under.getCenter().z);
            }
        }
        return thinnest < SUPPORT_MIN_CONTACT ? new Vec3(shoveX, 0.0, shoveZ) : null;
    }

    static double[] stepHeights(AABB hull, List<OrientedBox> blocks, List<OrientedBox> colliders,
                                        float step, double taken) {
        double lowest = hull.minY;

        List<Double> heights = new ArrayList<>();
        for (OrientedBox collider : blocks) {
            AABB box = collider.getWorldAABB();
            collectStepHeight(box.minY, lowest, step, taken, heights);
            collectStepHeight(box.maxY, lowest, step, taken, heights);
        }
        for (OrientedBox collider : colliders) {
            AABB box = collider.getWorldAABB();
            collectStepHeight(box.minY, lowest, step, taken, heights);
            collectStepHeight(box.maxY, lowest, step, taken, heights);
        }

        heights.sort(Double::compare);
        int kept = Math.min(heights.size(), MAX_STEP_CANDIDATES);
        double[] result = new double[kept];
        for (int i = 0; i < kept; i++) {
            result[i] = heights.get(i);
        }
        return result;
    }

    private static void collectStepHeight(double coord, double lowest, float step,
                                          double taken, List<Double> heights) {
        double height = coord - lowest;
        double ceiling = step;
        if (height > 0.0 && height <= ceiling && height != taken && !heights.contains(height)) {
            heights.add(height);
        }
    }

    public static OptionalDouble highestSurfaceUnder(List<OrientedBox> obbs, AABB footprint,
                                                     double floor, double ceiling) {
        double best = Double.NEGATIVE_INFINITY;
        boolean found = false;

        for (OrientedBox obb : obbs) {
            AABB world = obb.getWorldAABB();
            if (world.maxY < floor || world.minY > ceiling) {
                continue;
            }
            if (world.maxX <= footprint.minX || world.minX >= footprint.maxX
                    || world.maxZ <= footprint.minZ || world.minZ >= footprint.maxZ) {
                continue;
            }
            double top = obb.highestSurfaceUnder(footprint);
            if (top >= floor && top <= ceiling && top > best) {
                best = top;
                found = true;
            }
        }

        return found ? OptionalDouble.of(best) : OptionalDouble.empty();
    }

    public record RigidStep(Vec3 from, Vec3 to, Quaternionf turn) {

        public Vec3 move(final Vec3 point) {
            Vector3f local = new Vector3f(
                    (float)(point.x - this.from.x),
                    (float)(point.y - this.from.y),
                    (float)(point.z - this.from.z));
            local.rotate(this.turn);
            return new Vec3(this.to.x + local.x, this.to.y + local.y, this.to.z + local.z);
        }

        public Vec3 carry(final Vec3 point) {
            return this.move(point).subtract(point);
        }
    }

    private static double turnDegrees(final Quaternionf turn) {
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(turn.w()))));
    }

    @Nullable
    public static RigidStep rigidStep(final LivingBlock mob) {
        boolean oriented = mob.usesOrientedCollision();
        Quaternionf before = new Quaternionf(mob.getTickStartRotation()).normalize();
        Quaternionf after = new Quaternionf(mob.getRotation()).normalize();
        Quaternionf turn = oriented
                ? new Quaternionf(after).mul(new Quaternionf(before).conjugate()).normalize()
                : new Quaternionf();
        if (oriented && turnDegrees(turn) < RIGID_TURN_DEGREES) {
            return null;
        }
        Vec3 was = new Vec3(mob.xo, mob.yo, mob.zo);
        List<OrientedBox> from = getOBBs(mob, before, was);
        List<OrientedBox> to = getOBBs(mob, after, mob.position());
        if (from.isEmpty() || from.size() != to.size()) {
            return null;
        }
        return new RigidStep(centre(from.get(0)), centre(to.get(0)), turn);
    }

    private static Vec3 centre(final OrientedBox box) {
        return new Vec3(box.centerX(), box.centerY(), box.centerZ());
    }

    public static Vec3 riderEscape(final List<OrientedBox> obbs, final AABB box, final double limit) {
        OrientedBox theirs = new OrientedBox(box);
        Vector3f axis = new Vector3f();
        Vector3f best = new Vector3f();
        double worst = 0.0;
        for (OrientedBox obb : obbs) {
            double depth = OrientedBox.penetration(theirs, obb, axis);
            if (depth > worst) {
                worst = depth;
                best.set(axis);
            }
        }
        if (worst <= CARRY_SEAT_SLOP) {
            return Vec3.ZERO;
        }
        OptionalDouble top = highestSurfaceUnder(obbs, box, box.minY, box.maxY);
        if (top.isPresent()) {
            double lift = top.getAsDouble() - box.minY + SEPARATION_EPSILON;
            if (lift > 0.0) {
                return new Vec3(0.0, Math.min(lift, limit), 0.0);
            }
        }
        double push = Math.min(worst + SEPARATION_EPSILON, limit);
        return new Vec3(best.x() * push, best.y() * push, best.z() * push);
    }

    private static final ThreadLocal<Entity[]> CARRY_PAIR = ThreadLocal.withInitial(() -> new Entity[2]);

    public static boolean carrying(@Nullable final Entity asker, final Entity other) {
        Entity[] pair = CARRY_PAIR.get();
        return pair[0] != null && pair[0] == asker && pair[1] == other;
    }

    private static String extent(final AABB box) {
        return String.format("%.2fx%.2fx%.2f", box.getXsize(), box.getYsize(), box.getZsize());
    }

    public static void carryRiders(LivingBlock mob) {
        Vec3 carry = mob.position().subtract(mob.xo, mob.yo, mob.zo);
        boolean shifted = carry.lengthSqr() > CARRY_EPSILON_SQR;
        boolean clientSide = mob.level().isClientSide();
        long now = mob.level().getGameTime();
        List<OrientedBox> obbs = null;
        boolean stepResolved = false;
        RigidStep step = null;

        AABB searchArea = mob.getBoundingBox().inflate(0.5, 1.0, 0.5);
        for (Entity rider : mob.level().getEntities(mob, searchArea,
                e -> !e.isPassenger()
                        && !(e instanceof LivingBlock body && !body.usesOrientedCollision())
                        && (clientSide
                                ? e instanceof Player player && player.isLocalPlayer()
                                : !(e instanceof Player)))) {
            if (obbs == null) {
                LivingBlockCollisionShapes.Placement placement =
                        LivingBlockCollisionShapes.preciseGeometry(mob);
                obbs = placement == null ? List.of() : placement.obbs();
                if (obbs.isEmpty()) {
                    return;
                }
            }
            if (!stepResolved) {
                stepResolved = true;
                step = rigidStep(mob);
            }

            int riderId = rider.getId();
            AABB box = rider.getBoundingBox();
            boolean rigidRider = step != null && !(rider instanceof LivingBlock);
            Vec3 base = rigidRider ? step.carry(rider.position()) : shifted ? carry : Vec3.ZERO;
            AABB seat = base.lengthSqr() > CARRY_EPSILON_SQR
                    ? box.move(base.x, base.y, base.z) : box;
            double slack = CARRY_SURFACE_SLACK + Math.abs(base.y);

            OptionalDouble surface = highestSurfaceUnder(obbs, seat,
                    seat.minY - slack, seat.minY + slack);
            boolean seated = surface.isPresent();
            if (seated) {
                mob.holdRider(riderId, now);
                if (rider instanceof LivingBlock) {
                    mob.holdForRider(now);
                }
            }
            boolean held = seated || mob.heldRider(riderId, now, CARRY_HOLD_TICKS);

            Vec3 delta = held ? base : Vec3.ZERO;
            double gap = seated ? surface.getAsDouble() - seat.minY : 0.0;
            if (gap > 0.0 && !(rider instanceof LivingBlock)) {
                double limit = Math.max(CARRY_LIFT_LIMIT, Math.abs(base.y));
                delta = delta.add(0.0, Math.min(limit, gap), 0.0);
                if (mob.tickCount % OVERLAP_LOG_INTERVAL == 0) {
                    if (shouldLog) LOGGER.debug("[livingblock] lift id={} rider={} gap={}",
                            mob.getId(), rider.getType().toShortString(), String.format("%.3f", gap));
                }
            }
            if (rider instanceof LivingEntity) {
                Vec3 escape = riderEscape(obbs,
                        box.move(delta.x, delta.y, delta.z), CARRY_ESCAPE_LIMIT);
                delta = delta.add(escape);
                if (escape.lengthSqr() > CARRY_EPSILON_SQR) {
                    if (shouldLog) LOGGER.debug("[livingblock] riderout side={} id={} rider={} escape={} pieces={} turned={}",
                            clientSide ? "C" : "S", mob.getId(), rider.getType().toShortString(),
                            String.format("%.3f,%.3f,%.3f", escape.x, escape.y, escape.z),
                            obbs.size(),
                            String.format("%.2f", step == null ? 0.0 : turnDegrees(step.turn())));
                }
            }
            if (!held && delta.lengthSqr() <= CARRY_EPSILON_SQR) {
                continue;
            }
            OptionalDouble exact = highestSurfaceUnder(obbs, box,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
            boolean spike = exact.isPresent()
                    && mob.raiseRiderMissBar(Math.abs(box.minY - exact.getAsDouble()));
            if (exact.isPresent() && (spike || rigidRider || step != null
                    || mob.tickCount % OVERLAP_LOG_INTERVAL == 0)) {
                if (shouldLog) LOGGER.debug("[livingblock] riderobb side={} id={} rider={} pieces={} footprint={} feet={} exact={} hull={} rigid={} carry={} turned={}",
                        clientSide ? "C" : "S", mob.getId(), rider.getType().toShortString(),
                        obbs.size(), extent(box),
                        String.format("%.3f", box.minY),
                        String.format("%.3f", exact.getAsDouble()),
                        String.format("%.3f", mob.getBoundingBox().maxY),
                        String.format("%.4f,%.4f,%.4f", base.x, base.y, base.z),
                        String.format("%.4f,%.4f,%.4f", carry.x, carry.y, carry.z),
                        String.format("%.2f", step == null ? 0.0 : turnDegrees(step.turn())));
            }
            if (delta.lengthSqr() <= CARRY_EPSILON_SQR) {
                continue;
            }

            if (rider instanceof LivingBlock carried && !carried.claimCarry(mob.level().getGameTime())) {
                continue;
            }

            Vec3 before = rider.position();
            Entity[] pair = CARRY_PAIR.get();
            if (held) {
                pair[0] = rider;
                pair[1] = mob;
            }
            try {
                rider.move(net.minecraft.world.entity.MoverType.SELF, delta);
            } finally {
                pair[0] = null;
                pair[1] = null;
            }
            if (rider instanceof LivingBlock carried) {
                carried.discountCarriedMotion(rider.position().subtract(before));
            }
        }
    }
}
