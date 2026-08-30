package com.thebeyond.common.entity.util.livingblock;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class LivingBlockPivot {

    private static final int CORNERS_PER_BOX = 8;
    private static final int EDGE_SAMPLE_COUNT = 7;
    private static final int[][] BOX_EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private static final double QUARTER_TURN_DEGREES = 90.0;
    private static final double FULL_TURN_DEGREES = 360.0;
    private static final double ROLL_STEPS_PER_FACE = 7.0;
    public static final double DEGREES = QUARTER_TURN_DEGREES / ROLL_STEPS_PER_FACE;
    private static final int QUARTER_STEPS = 8;
    private static final double LEDGE_DEGREES = QUARTER_TURN_DEGREES / QUARTER_STEPS;
    private static final double FACE_SWING_SLOP_DEGREES = 1.5;
    private static final double FACE_SWING_LIMIT_SLACK_DEGREES = 1.0E-3;
    private static final double MIN_FACE_SWING_DEGREES = 1.0E-4;
    private static final double BISECT_MIN_DEGREES = 0.05;
    public static final double SETTLE_ANGLE_EPSILON = 0.02;
    public static final double UNSETTLE_ANGLE_EPSILON = 0.35;
    public static final double WALL_RESIDUE_DEGREES = Math.toDegrees(SETTLE_ANGLE_EPSILON);

    public static final double CONTACT = 0.08;
    public static final double SLOP = 1.0E-3;
    private static final double WALL_BAND_EPSILON = 1.0E-6;
    public static final double STEP_EPSILON = 1.0E-3;
    public static final double LATTICE_TOLERANCE = 0.25;
    private static final double TIE_SLACK = 1.0E-6;
    private static final double PLANE_EPSILON = 1.0E-9;
    private static final double DEGENERATE_SPAN_EPSILON = 1.0E-12;
    private static final float DEGENERATE_AXIS_EPSILON = 1.0E-9F;
    private static final double TWIST_LENGTH_EPSILON = 1.0E-8;

    private static final double WALL_RANK_EPSILON = 1.0E-9;
    private static final double WALL_SAMPLE_OFFSET = 1.0E-3;
    private static final double PIVOT_SNAP_EPSILON = 1.0E-12;

    private static final double HOLD_HEIGHT_TOLERANCE = 0.06;
    private static final double HOLD_SIDE_TOLERANCE = 0.06;
    private static final double SUPPORT_HEIGHT_TOLERANCE = 0.06;
    private static final double SUPPORT_PROBE_INSET = 0.05;
    private static final double SUPPORT_PROBE_DROP = 0.02;
    private static final double EDGE_MARCH_STEP = 0.05;
    private static final double EDGE_MARCH_TOP_TOLERANCE = 1.0E-3;
    private static final double AHEAD_PROBE_OFFSET = 0.15;
    private static final double AHEAD_TOP_EPSILON = 1.0E-6;
    private static final int FACE_BOTTOM_PROBE_DEPTH = 4;

    private static final double LANDING_HEIGHT_SLOP = 1.0E-3;
    private static final double STEP_CAP_EPSILON = 1.0E-6;
    private static final int DESCENT_BISECT_STEPS = 12;
    private static final double FLOOR_TOUCH_EPSILON = 1.0E-6;
    private static final int FLOOR_BISECT_STEPS = 14;

    private LivingBlockPivot() {
    }

    public record Turn(Quaternionf rotation, Vec3 position, double ledge, double rise) {
    }

    public record WallSurface(double plane, double minY, double maxY,
                              double minAcross, double maxAcross) {
    }

    public record WallContact(WallSurface surface, double gap, double overlap) {
    }

    public record WallPivot(Vec3 local, Vec3 world, WallSurface surface) {
        public WallPivot(final Vec3 local, final Vec3 world) {
            this(local, world, new WallSurface(world.x, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY));
        }
    }

    public record WallTurn(Quaternionf rotation, Vec3 position) {
    }

    public static boolean preferWallClimb(final boolean pivotBody, final boolean climbing,
                                          final boolean clearOfEntity, final boolean wallCapturable) {
        return pivotBody && !climbing && clearOfEntity && wallCapturable;
    }

    public static WallContact wallContact(final AABB hull, final List<AABB> terrain,
                                          final Direction direction, final double reach) {
        if (!direction.getAxis().isHorizontal()) {
            return null;
        }
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        boolean forward = direction == Direction.EAST || direction == Direction.SOUTH;
        double edge = axisX
                ? (forward ? hull.maxX : hull.minX)
                : (forward ? hull.maxZ : hull.minZ);
        double hullMinAcross = axisX ? hull.minZ : hull.minX;
        double hullMaxAcross = axisX ? hull.maxZ : hull.maxX;
        WallContact best = null;
        lastWallOverlap = Double.NaN;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestOverlap = Double.NEGATIVE_INFINITY;
        double bestAcross = Double.NEGATIVE_INFINITY;
        rejNoTerrain = terrain.isEmpty() ? 1 : 0;
        rejYBand = 0;
        rejAcross = 0;
        rejReach = 0;
        rejNearestGap = Double.NaN;
        for (AABB block : terrain) {
            if (block.maxY < hull.minY - WALL_BAND_EPSILON
                    || block.minY > hull.maxY + WALL_BAND_EPSILON) {
                rejYBand++;
                continue;
            }
            double minAcross = axisX ? block.minZ : block.minX;
            double maxAcross = axisX ? block.maxZ : block.maxX;
            double acrossOverlap = Math.min(maxAcross, hullMaxAcross)
                    - Math.max(minAcross, hullMinAcross);
            if (acrossOverlap <= SLOP) {
                rejAcross++;
                continue;
            }
            double plane = axisX
                    ? (forward ? block.minX : block.maxX)
                    : (forward ? block.minZ : block.maxZ);
            double gap = forward ? plane - edge : edge - plane;
            double distance = Math.abs(gap);
            double overlap = Math.max(0.0,
                    Math.min(block.maxY, hull.maxY) - Math.max(block.minY, hull.minY));
            if (Double.isNaN(rejNearestGap) || distance < Math.abs(rejNearestGap)) {
                rejNearestGap = gap;
            }
            if (gap < -reach || gap > reach) {
                rejReach++;
                continue;
            }
            if (distance > bestDistance + WALL_RANK_EPSILON
                    || Math.abs(distance - bestDistance) <= WALL_RANK_EPSILON
                            && (acrossOverlap < bestAcross - WALL_RANK_EPSILON
                                    || Math.abs(acrossOverlap - bestAcross) <= WALL_RANK_EPSILON
                                            && (overlap < bestOverlap - WALL_RANK_EPSILON
                                                    || Math.abs(overlap - bestOverlap) <= WALL_RANK_EPSILON
                                                            && best != null
                                                            && block.maxY <= best.surface.maxY))) {
                continue;
            }
            bestDistance = distance;
            bestOverlap = overlap;
            lastWallOverlap = overlap;
            bestAcross = acrossOverlap;
            best = new WallContact(new WallSurface(
                    plane, block.minY, block.maxY, minAcross, maxAcross), gap, overlap);
        }
        return best == null ? null : new WallContact(
                spanWall(best.surface(), hull, terrain, direction), best.gap(), best.overlap());
    }

    private static WallSurface spanWall(final WallSurface surface, final AABB hull,
                                        final List<AABB> terrain, final Direction direction) {
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        boolean forward = direction == Direction.EAST || direction == Direction.SOUTH;
        double wallPlane = surface.plane;
        double minAcross = surface.minAcross;
        double maxAcross = surface.maxAcross;
        double minY = surface.minY;
        double maxY = surface.maxY;
        double ceiling = Math.max(surface.maxY, hull.maxY);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (AABB block : terrain) {
                double plane = axisX
                        ? (forward ? block.minX : block.maxX)
                        : (forward ? block.minZ : block.maxZ);
                if (Math.abs(plane - wallPlane) > PLANE_EPSILON) {
                    continue;
                }
                double low = axisX ? block.minZ : block.minX;
                double high = axisX ? block.maxZ : block.maxX;
                if (Math.min(high, maxAcross) - Math.max(low, minAcross) <= SLOP) {
                    continue;
                }
                if (block.minY > maxY + SLOP || block.maxY < minY - SLOP) {
                    continue;
                }
                if (block.minY < minY - SLOP) {
                    minY = block.minY;
                    grew = true;
                }
                if (block.maxY > maxY + SLOP && maxY < ceiling - SLOP) {
                    maxY = Math.min(block.maxY, ceiling);
                    grew = true;
                }
            }
        }
        return new WallSurface(wallPlane, minY, maxY, minAcross, maxAcross);
    }

    public static Direction wallDirection(final AABB hull, final List<AABB> terrain,
                                          final Direction preferred, final double reach,
                                          final boolean fallback) {
        WallContact preferredContact = wallContact(hull, terrain, preferred, reach);
        if (preferredContact != null && preferredContact.overlap() > SLOP) {
            return preferred;
        }
        if (!fallback) {
            return null;
        }
        Direction best = null;
        double bestGap = Double.POSITIVE_INFINITY;
        for (Direction direction : new Direction[] {
                preferred.getClockWise(), preferred.getCounterClockWise(), preferred.getOpposite()}) {
            WallContact contact = wallContact(hull, terrain, direction, reach);
            if (contact != null && contact.overlap() > SLOP
                    && Math.abs(contact.gap()) < bestGap) {
                best = direction;
                bestGap = Math.abs(contact.gap());
            }
        }
        return best;
    }

    public static Vec3 wallSample(final WallSurface surface, final Direction direction) {
        double y = (surface.minY() + surface.maxY()) * 0.5;
        double across = (surface.minAcross() + surface.maxAcross()) * 0.5;
        double x = direction.getAxis() == Direction.Axis.X
                ? surface.plane() + direction.getStepX() * WALL_SAMPLE_OFFSET : across;
        double z = direction.getAxis() == Direction.Axis.Z
                ? surface.plane() + direction.getStepZ() * WALL_SAMPLE_OFFSET : across;
        return new Vec3(x, y, z);
    }

    public static boolean anchorableBody(final boolean settled, final Quaternionf rotation,
                                         final double maxResidueDegrees) {
        return settled && misalignment(rotation) <= maxResidueDegrees;
    }

    public static boolean wallTouched(final double touch, final double gap, final double contact,
                                      final boolean stalled) {
        return touch <= contact || !stalled && gap > contact;
    }

    public static boolean stepCornerHeld(final List<AABB> boxes, final Vec3 shapePivot,
                                         final Quaternionf rotation, final Vec3 position,
                                         final @Nullable WallContact wall,
                                         final Direction direction, final double contact) {
        return wall != null && wallTouched(wallSeparation(boxes, shapePivot, rotation, position,
                wall.surface(), direction), 0.0, contact, true);
    }

    public static boolean wallCapturable(final List<AABB> boxes, final AABB bounds,
                                         final Vec3 shapePivot, final Quaternionf rotation,
                                         final Vec3 position, final Direction direction,
                                         final WallContact wall, final double contact,
                                         final double reach) {
        if (wall == null) {
            return false;
        }
        if (wall.gap() > contact) {
            return wallPivot(boxes, bounds, shapePivot, rotation, position, direction,
                    wall.surface(), reach) != null;
        }
        return wallTouched(wallSeparation(boxes, shapePivot, rotation, position, wall.surface(),
                        direction), wall.gap(), contact, false)
                && wallPivot(boxes, bounds, shapePivot, rotation, position, direction,
                        wall.surface(), contact) != null;
    }

    public static double anchorDrift(final List<AABB> ownerBoxes, final Vec3 ownerShapePivot,
                                     final Quaternionf ownerRotation, final Vec3 ownerPosition,
                                     final Vec3 anchorLocal, final Vec3 held) {
        return worldPoint(ownerBoxes, ownerShapePivot, ownerRotation, ownerPosition, anchorLocal)
                .distanceTo(held);
    }

    public static double wallSeparation(final List<AABB> boxes, final Vec3 shapePivot,
                                        final Quaternionf rotation, final Vec3 position,
                                        final WallSurface surface, final Direction direction) {
        if (!Double.isFinite(surface.plane) || !Double.isFinite(surface.minY)
                || !Double.isFinite(surface.maxY) || !Double.isFinite(surface.minAcross)
                || !Double.isFinite(surface.maxAcross) || boxes.isEmpty()) {
            return 0.0;
        }
        AABB face = direction.getAxis() == Direction.Axis.X
                ? new AABB(surface.plane, surface.minY, surface.minAcross,
                        surface.plane, surface.maxY, surface.maxAcross)
                : new AABB(surface.minAcross, surface.minY, surface.plane,
                        surface.maxAcross, surface.maxY, surface.plane);
        OrientedBox wall = new OrientedBox(face);
        List<OrientedBox> placed = LivingBlockCollisionHandler.anchoredOBBs(boxes,
                silhouette(boxes, shapePivot, rotation), shapePivot, rotation, position, 0);
        double best = Double.POSITIVE_INFINITY;
        for (OrientedBox box : placed) {
            best = Math.min(best, OrientedBox.separation(box, wall));
            if (best <= 0.0) {
                return 0.0;
            }
        }
        return Double.isFinite(best) ? best : 0.0;
    }

    public static double anchorDistance(final List<AABB> terrain, final Vec3 point) {
        double px = point.x;
        double py = point.y;
        double pz = point.z;
        double best = Double.POSITIVE_INFINITY;
        for (AABB box : terrain) {
            double dx = Math.max(0.0, Math.max(box.minX - px, px - box.maxX));
            double dy = Math.max(0.0, Math.max(box.minY - py, py - box.maxY));
            double dz = Math.max(0.0, Math.max(box.minZ - pz, pz - box.maxZ));
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }

    public static boolean anchorHeld(final List<AABB> terrain, final Vec3 point,
                                     final double tolerance) {
        return anchorDistance(terrain, point) <= tolerance;
    }

    public static boolean surfacePresent(final List<AABB> terrain, final WallSurface surface,
                                         final Direction direction, final double tolerance) {
        if (!direction.getAxis().isHorizontal()) {
            return false;
        }
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        boolean forward = direction == Direction.EAST || direction == Direction.SOUTH;
        double wallPlane = surface.plane();
        double minAcross = surface.minAcross();
        double maxAcross = surface.maxAcross();
        double lowY = surface.minY() - tolerance;
        double highY = surface.maxY() + tolerance;
        for (AABB box : terrain) {
            double plane = axisX
                    ? (forward ? box.minX : box.maxX)
                    : (forward ? box.minZ : box.maxZ);
            if (Math.abs(plane - wallPlane) > tolerance
                    || box.maxY < lowY
                    || box.minY > highY) {
                continue;
            }
            double low = axisX ? box.minZ : box.minX;
            double high = axisX ? box.maxZ : box.maxX;
            if (Math.min(high, maxAcross) - Math.max(low, minAcross) <= SLOP) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static int lastCandidates;
    public static double lastAllowed;
    public static double lastBestDepth;
    public static double lastBestRise;
    public static int lastWallCandidates;
    public static int rejNoTerrain;
    public static int rejYBand;
    public static int rejAcross;
    public static int rejReach;
    public static double rejNearestGap;
    public static double lastWallDistance;

    public static double lastWallOverlap;

    public static boolean lastWallContactNull;
    public static int pivRejYBand;
    public static int pivRejAcross;
    public static boolean pivotOutOfReach;

    public static AABB silhouette(final List<AABB> localBoxes, final Vec3 shapePivot,
                                  final Quaternionf rotation) {
        AABBBuilder builder = new AABBBuilder();
        Vector3f corner = new Vector3f();
        for (AABB box : localBoxes) {
            for (int i = 0; i < CORNERS_PER_BOX; i++) {
                corner.set(
                        (float)(((i & 1) == 0 ? box.minX : box.maxX) - shapePivot.x),
                        (float)(((i & 2) == 0 ? box.minY : box.maxY) - shapePivot.y),
                        (float)(((i & 4) == 0 ? box.minZ : box.maxZ) - shapePivot.z));
                corner.rotate(rotation);
                builder.include(corner);
            }
        }
        return builder.build();
    }

    private static Vec3 frameAnchor(final List<AABB> localBoxes, final Vec3 shapePivot,
                                    final Quaternionf rotation, final Vec3 position) {
        AABB silhouette = silhouette(localBoxes, shapePivot, rotation);
        List<OrientedBox> obbs = LivingBlockCollisionHandler.anchoredOBBs(
                localBoxes, silhouette, shapePivot, rotation, position, 0);
        Vec3 firstCentre = localBoxes.getFirst().getCenter();
        OrientedBox placed = obbs.getFirst();
        Vector3f localCenter = new Vector3f(
                (float)(firstCentre.x - shapePivot.x),
                (float)(firstCentre.y - shapePivot.y),
                (float)(firstCentre.z - shapePivot.z));
        localCenter.rotate(rotation);
        return new Vec3(
                placed.centerX() - localCenter.x,
                placed.centerY() - localCenter.y,
                placed.centerZ() - localCenter.z);
    }

    public static Vec3 worldPoint(final List<AABB> localBoxes, final Vec3 shapePivot,
                                  final Quaternionf rotation, final Vec3 position,
                                  final Vec3 localPoint) {
        Vec3 anchor = frameAnchor(localBoxes, shapePivot, rotation, position);
        Vector3f relative = new Vector3f(
                (float)(localPoint.x - shapePivot.x),
                (float)(localPoint.y - shapePivot.y),
                (float)(localPoint.z - shapePivot.z));
        relative.rotate(rotation);
        return anchor.add(relative.x, relative.y, relative.z);
    }

    public static Vec3 localPoint(final List<AABB> localBoxes, final Vec3 shapePivot,
                                  final Quaternionf rotation, final Vec3 position,
                                  final Vec3 world) {
        Vec3 anchor = frameAnchor(localBoxes, shapePivot, rotation, position);
        Vector3f relative = new Vector3f(
                (float)(world.x - anchor.x),
                (float)(world.y - anchor.y),
                (float)(world.z - anchor.z));
        relative.rotate(new Quaternionf(rotation).normalize().conjugate());
        return new Vec3(shapePivot.x + relative.x, shapePivot.y + relative.y,
                shapePivot.z + relative.z);
    }

    public static WallPivot wallPivot(final List<AABB> localBoxes, final AABB bounds,
                                      final Vec3 shapePivot, final Quaternionf rotation,
                                      final Vec3 position, final Direction direction,
                                      final double wallPlane, final double reach) {
        WallSurface surface = new WallSurface(wallPlane, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return wallPivot(localBoxes, bounds, shapePivot, rotation, position, direction, surface, reach);
    }

    public static WallPivot wallPivot(final List<AABB> localBoxes, final AABB bounds,
                                      final Vec3 shapePivot, final Quaternionf rotation,
                                      final Vec3 position, final Direction direction,
                                      final WallSurface surface, final double reach) {
        return wallPivot(localBoxes, bounds, shapePivot, rotation, position, direction, surface,
                reach, false);
    }

    public static WallPivot wallPivot(final List<AABB> localBoxes, final AABB bounds,
                                      final Vec3 shapePivot, final Quaternionf rotation,
                                      final Vec3 position, final Direction direction,
                                      final WallSurface surface, final double reach,
                                      final boolean preferLower) {
        lastWallCandidates = 0;
        lastWallDistance = Double.POSITIVE_INFINITY;
        pivRejYBand = 0;
        pivRejAcross = 0;
        pivotOutOfReach = false;
        if (!direction.getAxis().isHorizontal()) {
            return null;
        }
        Vec3 bestLocal = null;
        Vec3 bestWorld = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int candidates = 0;
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        double wallPlane = surface.plane;
        double minY = surface.minY;
        double maxY = surface.maxY;
        double minAcross = surface.minAcross;
        double maxAcross = surface.maxAcross;
        Vec3 anchor = worldPoint(localBoxes, shapePivot, rotation, position, shapePivot);
        Vector3f relative = new Vector3f();
        Vec3[] local = new Vec3[CORNERS_PER_BOX];
        Vec3[] world = new Vec3[CORNERS_PER_BOX];
        double[] samples = new double[EDGE_SAMPLE_COUNT];
        samples[0] = 0.0;
        samples[1] = 1.0;
        for (AABB box : localBoxes) {
            for (int i = 0; i < CORNERS_PER_BOX; i++) {
                Vec3 corner = new Vec3(
                        (i & 1) == 0 ? box.minX : box.maxX,
                        (i & 2) == 0 ? box.minY : box.maxY,
                        (i & 4) == 0 ? box.minZ : box.maxZ);
                local[i] = corner;
                relative.set(
                        (float)(corner.x - shapePivot.x),
                        (float)(corner.y - shapePivot.y),
                        (float)(corner.z - shapePivot.z)).rotate(rotation);
                world[i] = anchor.add(relative.x, relative.y, relative.z);
            }
            for (int[] edge : BOX_EDGES) {
                Vec3 a = world[edge[0]];
                Vec3 b = world[edge[1]];
                double ah = axisX ? a.x : a.z;
                double bh = axisX ? b.x : b.z;
                double aa = axisX ? a.z : a.x;
                double ba = axisX ? b.z : b.x;
                samples[2] = fraction(wallPlane, ah, bh);
                samples[3] = fraction(minY, a.y, b.y);
                samples[4] = fraction(maxY, a.y, b.y);
                samples[5] = fraction(minAcross, aa, ba);
                samples[6] = fraction(maxAcross, aa, ba);
                for (double raw : samples) {
                    if (!Double.isFinite(raw)) {
                        continue;
                    }
                    double t = Math.max(0.0, Math.min(1.0, raw));
                    Vec3 point = a.lerp(b, t);
                    if (point.y < minY - SLOP || point.y > maxY + SLOP) {
                        pivRejYBand++;
                        continue;
                    }
                    double across = axisX ? point.z : point.x;
                    if (across < minAcross - SLOP || across > maxAcross + SLOP) {
                        pivRejAcross++;
                        continue;
                    }
                    candidates++;
                    double coordinate = axisX ? point.x : point.z;
                    double distance = Math.abs(wallPlane - coordinate);
                    if (distance < bestDistance - TIE_SLACK
                            || Math.abs(distance - bestDistance) <= TIE_SLACK
                                    && (bestWorld == null
                                            || (preferLower ? point.y < bestWorld.y
                                                    : point.y > bestWorld.y))) {
                        bestDistance = distance;
                        bestLocal = local[edge[0]].lerp(local[edge[1]], t);
                        bestWorld = point;
                    }
                }
            }
        }
        lastWallCandidates = candidates;
        lastWallDistance = bestDistance;
        pivotOutOfReach = bestLocal != null && bestDistance > reach;
        if (bestLocal == null || bestDistance > reach) {
            return null;
        }
        Vec3 attached = axisX
                ? new Vec3(wallPlane, bestWorld.y, bestWorld.z)
                : new Vec3(bestWorld.x, bestWorld.y, wallPlane);
        return new WallPivot(bestLocal, attached, surface);
    }

    private static double fraction(final double value, final double start, final double end) {
        double span = end - start;
        return Math.abs(span) < DEGENERATE_SPAN_EPSILON ? Double.NaN : (value - start) / span;
    }

    public static WallTurn wallTurn(final List<AABB> localBoxes, final Vec3 shapePivot,
                                    final Quaternionf rotation, final Vec3 position,
                                    final WallPivot pivot, final Direction direction,
                                    final double degrees) {
        Vector3f axis = new Vector3f(direction.getStepZ(), 0.0F, -direction.getStepX());
        if (axis.lengthSquared() < DEGENERATE_AXIS_EPSILON) {
            return null;
        }
        axis.normalize();
        Quaternionf delta = new Quaternionf().rotateAxis((float)Math.toRadians(degrees), axis);
        Quaternionf turned = delta.mul(rotation, new Quaternionf());
        return new WallTurn(turned,
                positionForPivot(localBoxes, shapePivot, turned, pivot.local, pivot.world));
    }

    public static double degreesToNextFace(final Quaternionf rotation, final Direction direction) {
        return degreesToNextFace(rotation, direction, FACE_SWING_SLOP_DEGREES);
    }

    public static double degreesToNextFace(final Quaternionf rotation, final Direction direction,
                                           final double swingSlopDegrees) {
        Vector3f desired = new Vector3f(direction.getStepZ(), 0.0F, -direction.getStepX());
        if (desired.lengthSquared() < DEGENERATE_AXIS_EPSILON) {
            return Double.NaN;
        }
        desired.normalize();
        Quaternionf inverse = new Quaternionf(rotation).normalize().conjugate();
        Quaternionf relative = new Quaternionf();
        float desiredX = desired.x;
        float desiredY = desired.y;
        float desiredZ = desired.z;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i < LivingBlockOrientation.COUNT; i++) {
            LivingBlockOrientation.of(i).quaternion().mul(inverse, relative).normalize();
            if (relative.w < 0.0F) {
                relative.set(-relative.x, -relative.y, -relative.z, -relative.w);
            }
            double parallel = relative.x * desiredX + relative.y * desiredY
                    + relative.z * desiredZ;
            double twistLength = Math.sqrt(parallel * parallel + (double)relative.w * relative.w);
            if (twistLength < TWIST_LENGTH_EPSILON) {
                continue;
            }
            double twistX = desiredX * parallel / twistLength;
            double twistY = desiredY * parallel / twistLength;
            double twistZ = desiredZ * parallel / twistLength;
            double twistW = relative.w / twistLength;
            double dot = Math.abs(relative.x * twistX + relative.y * twistY
                    + relative.z * twistZ + relative.w * twistW);
            double swing = Math.toDegrees(2.0 * Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
            double degrees = Math.toDegrees(2.0 * Math.atan2(parallel, relative.w));
            if (degrees < 0.0) {
                degrees += FULL_TURN_DEGREES;
            }
            if (swing <= swingSlopDegrees && degrees > MIN_FACE_SWING_DEGREES
                    && degrees <= QUARTER_TURN_DEGREES + FACE_SWING_LIMIT_SLACK_DEGREES
                    && degrees < best) {
                best = degrees;
            }
        }
        return Double.isFinite(best) ? Math.min(QUARTER_TURN_DEGREES, best) : Double.NaN;
    }

    public static List<Vec3> cornerCandidates(final List<AABB> localBoxes) {
        List<Vec3> out = new ArrayList<>(localBoxes.size() * CORNERS_PER_BOX);
        for (AABB box : localBoxes) {
            for (int i = 0; i < CORNERS_PER_BOX; i++) {
                out.add(new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                        (i & 2) == 0 ? box.minY : box.maxY,
                        (i & 4) == 0 ? box.minZ : box.maxZ));
            }
        }
        return out;
    }

    public static @Nullable Vec3 seatByTipping(final List<AABB> localBoxes, final Vec3 shapePivot,
                                               final Quaternionf before, final Quaternionf after,
                                               final Vec3 position, final PoseCheck check) {
        List<Vec3> locals = cornerCandidates(localBoxes);
        Vec3 anchor = frameAnchor(localBoxes, shapePivot, before, position);
        Vector3f relative = new Vector3f();
        List<Vec3> worlds = new ArrayList<>(locals.size());
        for (Vec3 local : locals) {
            relative.set(
                    (float)(local.x - shapePivot.x),
                    (float)(local.y - shapePivot.y),
                    (float)(local.z - shapePivot.z)).rotate(before);
            worlds.add(anchor.add(relative.x, relative.y, relative.z));
        }
        for (int i = 0; i < locals.size(); i++) {
            Vec3 seated = positionForPivot(localBoxes, shapePivot, after, locals.get(i), worlds.get(i));
            if (check.fails(after, seated) == null) {
                return seated;
            }
        }
        return null;
    }

    public static Vec3 positionForPivot(final List<AABB> localBoxes, final Vec3 shapePivot,
                                        final Quaternionf rotation, final Vec3 localPoint,
                                        final Vec3 worldPoint) {
        Vector3f relative = new Vector3f(
                (float)(localPoint.x - shapePivot.x),
                (float)(localPoint.y - shapePivot.y),
                (float)(localPoint.z - shapePivot.z));
        relative.rotate(rotation);
        AABB silhouette = silhouette(localBoxes, shapePivot, rotation);
        Vec3 candidate = new Vec3(
                worldPoint.x - relative.x,
                worldPoint.y - relative.y + silhouette.minY,
                worldPoint.z - relative.z);
        Vec3 actual = worldPoint(localBoxes, shapePivot, rotation, candidate, localPoint);
        return candidate.add(worldPoint.subtract(actual));
    }

    public static Vec3 renderOffset(final List<AABB> localBoxes, final Vec3 shapePivot,
                                    final Quaternionf rotation, final Vec3 localPoint,
                                    final Vec3 worldPoint, final Vec3 linearPosition) {
        return positionForPivot(localBoxes, shapePivot, rotation, localPoint, worldPoint)
                .subtract(linearPosition);
    }

    public static boolean topSupported(final List<AABB> localBoxes, final Vec3 shapePivot,
                                       final Quaternionf rotation, final Vec3 position,
                                       final WallPivot pivot, final Direction direction,
                                       final double slop) {
        List<OrientedBox> obbs = LivingBlockCollisionHandler.anchoredOBBs(
                localBoxes, silhouette(localBoxes, shapePivot, rotation), shapePivot,
                rotation, position, 0);
        AABB envelope = null;
        for (OrientedBox obb : obbs) {
            AABB world = obb.getWorldAABB();
            envelope = envelope == null ? world : envelope.minmax(world);
        }
        WallSurface surface = pivot.surface;
        if (envelope == null || envelope.minY < surface.maxY - slop) {
            return false;
        }
        boolean crossed = switch (direction) {
            case EAST -> envelope.maxX > surface.plane + slop;
            case WEST -> envelope.minX < surface.plane - slop;
            case SOUTH -> envelope.maxZ > surface.plane + slop;
            case NORTH -> envelope.minZ < surface.plane - slop;
            default -> false;
        };
        if (!crossed) {
            return false;
        }
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        double minAcross = axisX ? envelope.minZ : envelope.minX;
        double maxAcross = axisX ? envelope.maxZ : envelope.maxX;
        return Math.min(maxAcross, surface.maxAcross)
                - Math.max(minAcross, surface.minAcross) > slop;
    }

    public static double misalignment(final Quaternionf rotation) {
        double dot = Math.min(1.0,
                LivingBlockOrientation.fromQuaternion(rotation).absDot(rotation));
        return Math.toDegrees(2.0 * Math.acos(dot));
    }

    public static @Nullable Quaternionf nextFaceOrientation(final Quaternionf rotation,
                                                            final Direction direction) {
        double degrees = degreesToNextFace(rotation, direction);
        if (!Double.isFinite(degrees)) {
            return null;
        }
        Vector3f axis = new Vector3f(direction.getStepZ(), 0.0F, -direction.getStepX());
        if (axis.lengthSquared() < DEGENERATE_AXIS_EPSILON) {
            return null;
        }
        axis.normalize();
        return new Quaternionf().rotateAxis((float)Math.toRadians(degrees), axis)
                .mul(rotation, new Quaternionf()).normalize();
    }

    public static boolean faceSettled(final Quaternionf rotation, final double toleranceDegrees) {
        return misalignment(rotation) <= toleranceDegrees;
    }

    public static List<Vec3> ledges(final AABB hull, final List<AABB> terrain, final Direction direction,
                                    final double bodyHeight) {
        List<Vec3> out = new ArrayList<>();
        AABB probe = hull.inflate(CONTACT);
        double reach = hull.minY + bodyHeight;
        double floor = hull.minY + STEP_EPSILON;
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        boolean forward = axisX ? direction.getStepX() > 0 : direction.getStepZ() > 0;
        Vec3 centre = hull.getCenter();
        for (AABB block : terrain) {
            if (!block.intersects(probe)
                    || block.maxY > reach
                    || block.maxY <= floor) {
                continue;
            }
            if (!isAhead(hull, block, direction)) {
                continue;
            }
            out.add(axisX
                    ? new Vec3(forward ? block.minX : block.maxX, block.maxY, centre.z)
                    : new Vec3(centre.x, block.maxY, forward ? block.minZ : block.maxZ));
        }
        return out;
    }

    private static boolean isAhead(final AABB hull, final AABB block, final Direction direction) {
        return switch (direction) {
            case EAST -> block.minX >= hull.maxX - CONTACT;
            case WEST -> block.maxX <= hull.minX + CONTACT;
            case SOUTH -> block.minZ >= hull.maxZ - CONTACT;
            case NORTH -> block.maxZ <= hull.minZ + CONTACT;
            default -> false;
        };
    }

    public static double worstPenetration(final List<AABB> localBoxes, final Vec3 shapePivot,
                                          final Quaternionf rotation, final Vec3 pos,
                                          final List<AABB> terrain) {
        AABB sil = silhouette(localBoxes, shapePivot, rotation);
        List<OrientedBox> mine = LivingBlockCollisionHandler.anchoredOBBs(
                localBoxes, sil, shapePivot, rotation, pos, 0);
        Vector3f axis = new Vector3f();
        double worst = 0.0;
        for (OrientedBox a : mine) {
            AABB box = a.getWorldAABB();
            for (AABB block : terrain) {
                if (box.intersects(block)) {
                    worst = Math.max(worst, OrientedBox.penetration(a, new OrientedBox(block), axis));
                }
            }
        }
        return worst;
    }

    public static Turn turn(final List<AABB> localBoxes, final Vec3 shapePivot,
                            final Quaternionf rotation, final Vec3 pos, final AABB hull,
                            final List<AABB> terrain, final Direction direction) {
        Vector3f axis = new Vector3f(direction.getStepZ(), 0.0F, -direction.getStepX());
        if (axis.lengthSquared() < DEGENERATE_AXIS_EPSILON) {
            return null;
        }
        axis.normalize();
        Quaternionf delta = new Quaternionf().rotateAxis((float)Math.toRadians(LEDGE_DEGREES), axis);
        double allowed = Math.max(worstPenetration(localBoxes, shapePivot, rotation, pos, terrain), SLOP)
                + TIE_SLACK;

        Quaternionf turned = delta.mul(rotation, new Quaternionf());
        AABB turnedSilhouette = silhouette(localBoxes, shapePivot, turned);
        Vec3 turnedCentre = turnedSilhouette.getCenter();
        Vec3 centre = hull.getCenter();
        Turn best = null;

        List<Vec3> candidates = ledges(hull, terrain, direction, hull.getYsize());
        lastCandidates = candidates.size();
        lastAllowed = allowed;
        lastBestDepth = Double.POSITIVE_INFINITY;
        lastBestRise = Double.NEGATIVE_INFINITY;

        Vector3f relative = new Vector3f();
        for (Vec3 ledge : candidates) {
            if (best != null && ledge.y <= best.ledge()) {
                continue;
            }
            relative.set((float)(centre.x - ledge.x), (float)(centre.y - ledge.y),
                    (float)(centre.z - ledge.z)).rotate(delta);
            Vec3 moved = new Vec3(
                    ledge.x + relative.x - turnedCentre.x,
                    ledge.y + relative.y - turnedCentre.y + turnedSilhouette.minY,
                    ledge.z + relative.z - turnedCentre.z);
            double rise = moved.y - pos.y;
            double depth = worstPenetration(localBoxes, shapePivot, turned, moved, terrain);
            lastBestDepth = Math.min(lastBestDepth, depth);
            lastBestRise = Math.max(lastBestRise, rise);
            if (rise <= 0.0 || depth > allowed) {
                continue;
            }
            if (!quarterClear(localBoxes, shapePivot, rotation, pos, ledge, delta, terrain, allowed)) {
                continue;
            }
            best = new Turn(turned, moved, ledge.y, rise);
        }
        return best;
    }

    private static boolean quarterClear(final List<AABB> localBoxes, final Vec3 shapePivot,
                                        final Quaternionf rotation, final Vec3 pos, final Vec3 ledge,
                                        final Quaternionf delta, final List<AABB> terrain,
                                        final double allowed) {
        Quaternionf pose = new Quaternionf(rotation);
        Vec3 where = pos;
        AABB box = silhouette(localBoxes, shapePivot, pose);
        Vec3 boxCentre = box.getCenter();
        Vec3 centre = new Vec3(where.x + boxCentre.x,
                where.y - box.minY + boxCentre.y, where.z + boxCentre.z);
        Vector3f relative = new Vector3f();
        for (int step = 0; step < QUARTER_STEPS; step++) {
            relative.set((float)(centre.x - ledge.x), (float)(centre.y - ledge.y),
                    (float)(centre.z - ledge.z)).rotate(delta);
            pose = delta.mul(pose, new Quaternionf());
            box = silhouette(localBoxes, shapePivot, pose);
            boxCentre = box.getCenter();
            centre = new Vec3(ledge.x + relative.x, ledge.y + relative.y, ledge.z + relative.z);
            where = new Vec3(centre.x - boxCentre.x,
                    centre.y - boxCentre.y + box.minY, centre.z - boxCentre.z);
            if (worstPenetration(localBoxes, shapePivot, pose, where, terrain) > allowed) {
                return false;
            }
            if (where.y >= ledge.y) {
                return true;
            }
        }
        return true;
    }

    public static int holdRejHeight;
    public static int holdRejSide;
    public static double holdBestHeight;

    public static @Nullable AABB surfaceHolding(final List<AABB> surfaces, final double x,
                                                final double top, final double z) {
        AABB best = null;
        holdRejHeight = 0;
        holdRejSide = 0;
        holdBestHeight = Double.POSITIVE_INFINITY;
        for (AABB world : surfaces) {
            double offset = Math.abs(world.maxY - top);
            holdBestHeight = Math.min(holdBestHeight, offset);
            if (offset > HOLD_HEIGHT_TOLERANCE) {
                holdRejHeight++;
                continue;
            }
            if (world.maxX < x - HOLD_SIDE_TOLERANCE || world.minX > x + HOLD_SIDE_TOLERANCE
                    || world.maxZ < z - HOLD_SIDE_TOLERANCE || world.minZ > z + HOLD_SIDE_TOLERANCE) {
                holdRejSide++;
                continue;
            }
            if (best == null || world.maxY > best.maxY) {
                best = world;
            }
        }
        return best;
    }

    public static @Nullable AABB supportUnder(final List<AABB> surfaces, final AABB hull,
                                              final double contactY) {
        AABB best = null;
        for (AABB world : surfaces) {
            if (world.maxY > contactY + SUPPORT_HEIGHT_TOLERANCE) {
                continue;
            }
            if (world.maxX < hull.minX || world.minX > hull.maxX
                    || world.maxZ < hull.minZ || world.minZ > hull.maxZ) {
                continue;
            }
            if (best == null || world.maxY > best.maxY) {
                best = world;
            }
        }
        return best;
    }

    public static @Nullable Vec3 descentEdge(final List<AABB> surfaces, final AABB hull,
                                             final Vec3 contact, final Direction direction,
                                             final double reach, final double minDrop) {
        if (!direction.getAxis().isHorizontal()) {
            return null;
        }
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        int stepX = direction.getStepX();
        int stepZ = direction.getStepZ();
        Vec3 centre = hull.getCenter();
        double probeX = contact.x - stepX * SUPPORT_PROBE_INSET;
        double probeZ = contact.z - stepZ * SUPPORT_PROBE_INSET;
        AABB support = surfaceHolding(surfaces, probeX, contact.y, probeZ);
        if (support == null) {
            support = surfaceHolding(surfaces, centre.x, contact.y, centre.z);
        }
        if (support == null) {
            return null;
        }
        double top = support.maxY;
        double bodyFront = face(hull, direction);
        double plane = face(support, direction);
        for (int step = 1; step <= 2; step++) {
            AABB next = surfaceHolding(surfaces,
                    axisX ? plane + stepX * EDGE_MARCH_STEP : probeX, top,
                    axisX ? probeZ : plane + stepZ * EDGE_MARCH_STEP);
            if (next == null || Math.abs(next.maxY - top) > EDGE_MARCH_TOP_TOLERANCE) {
                break;
            }
            double grown = face(next, direction);
            if (beyond(grown, bodyFront, direction) > reach) {
                break;
            }
            plane = grown;
        }
        if (beyond(plane, bodyFront, direction) > reach) {
            return null;
        }
        double aheadX = axisX ? plane + stepX * AHEAD_PROBE_OFFSET : probeX;
        double aheadZ = axisX ? probeZ : plane + stepZ * AHEAD_PROBE_OFFSET;
        double aheadTop = Double.NEGATIVE_INFINITY;
        for (AABB world : surfaces) {
            if (world.maxY > top + AHEAD_TOP_EPSILON) {
                continue;
            }
            if (world.maxX < aheadX || world.minX > aheadX
                    || world.maxZ < aheadZ || world.minZ > aheadZ) {
                continue;
            }
            aheadTop = Math.max(aheadTop, world.maxY);
        }
        if (top - aheadTop < minDrop) {
            return null;
        }
        return axisX
                ? new Vec3(plane, top, centre.z)
                : new Vec3(centre.x, top, plane);
    }

    public record DescentProbe(String reason, @Nullable AABB support, double supportTop,
                               double bodyFront, double plane, int marched, double beyondBody,
                               double aheadTop, double drop, double faceBottom,
                               @Nullable Vec3 edge, @Nullable WallPivot pivot,
                               int pivotCandidates, double pivotDistance, int surfaces,
                               int rejYBand, int rejAcross, int outOfReach, double pivotReach) {

        public boolean ok() {
            return this.pivot != null;
        }

        static DescentProbe refused(final String reason) {
            return new DescentProbe(reason, null, Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN,
                    Double.NaN, Double.NaN, Double.NaN, null, null, 0, Double.NaN, 0, 0, 0, 0,
                    Double.NaN);
        }
    }

    public static DescentProbe descentProbe(final List<AABB> surfaces, final AABB hull,
                                            final Vec3 contact, final Direction direction,
                                            final double reach, final double minDrop,
                                            final List<AABB> localBoxes, final AABB bounds,
                                            final Vec3 shapePivot, final Quaternionf rotation,
                                            final Vec3 position, final double pivotReach) {
        if (!direction.getAxis().isHorizontal()) {
            return DescentProbe.refused("nothorizontal");
        }
        boolean axisX = direction.getAxis() == Direction.Axis.X;
        int stepX = direction.getStepX();
        int stepZ = direction.getStepZ();
        Vec3 centre = hull.getCenter();
        double probeX = contact.x - stepX * SUPPORT_PROBE_INSET;
        double probeZ = contact.z - stepZ * SUPPORT_PROBE_INSET;
        AABB support = surfaceHolding(surfaces, probeX, contact.y, probeZ);
        if (support == null) {
            support = surfaceHolding(surfaces, centre.x, contact.y, centre.z);
        }
        if (support == null) {
            support = supportUnder(surfaces, hull, contact.y);
        }
        if (support == null) {
            return DescentProbe.refused("nosupport");
        }
        double top = support.maxY;
        double bodyFront = face(hull, direction);
        double plane = face(support, direction);
        int marched = 0;
        for (int step = 1; step <= 2; step++) {
            AABB next = surfaceHolding(surfaces,
                    axisX ? plane + stepX * EDGE_MARCH_STEP : probeX, top,
                    axisX ? probeZ : plane + stepZ * EDGE_MARCH_STEP);
            if (next == null || Math.abs(next.maxY - top) > EDGE_MARCH_TOP_TOLERANCE) {
                break;
            }
            double grown = face(next, direction);
            if (beyond(grown, bodyFront, direction) > reach) {
                break;
            }
            plane = grown;
            marched = step;
        }
        double beyondBody = beyond(plane, bodyFront, direction);
        if (beyondBody > reach) {
            return new DescentProbe("edgefar", support, top, bodyFront, plane, marched,
                    beyondBody, Double.NaN, Double.NaN, Double.NaN, null, null, 0, Double.NaN,
                    0, 0, 0, 0, Double.NaN);
        }
        double aheadX = axisX ? plane + stepX * AHEAD_PROBE_OFFSET : probeX;
        double aheadZ = axisX ? probeZ : plane + stepZ * AHEAD_PROBE_OFFSET;
        double aheadTop = Double.NEGATIVE_INFINITY;
        for (AABB world : surfaces) {
            if (world.maxY > top + AHEAD_TOP_EPSILON) {
                continue;
            }
            if (world.maxX < aheadX || world.minX > aheadX
                    || world.maxZ < aheadZ || world.minZ > aheadZ) {
                continue;
            }
            aheadTop = Math.max(aheadTop, world.maxY);
        }
        double drop = top - aheadTop;
        if (drop < minDrop) {
            return new DescentProbe("nodrop", support, top, bodyFront, plane, marched, beyondBody,
                    aheadTop, drop, Double.NaN, null, null, 0, Double.NaN, 0, 0, 0, 0, Double.NaN);
        }
        Vec3 edge = axisX
                ? new Vec3(plane, top, centre.z)
                : new Vec3(centre.x, top, plane);
        double faceBottom = top;
        for (int step = 1; step <= FACE_BOTTOM_PROBE_DEPTH; step++) {
            AABB under = surfaceHolding(surfaces, edge.x + stepX * AHEAD_PROBE_OFFSET, top - step,
                    edge.z + stepZ * AHEAD_PROBE_OFFSET);
            if (under != null) {
                faceBottom = under.maxY;
                break;
            }
            faceBottom = top - step;
        }
        double acrossMin = axisX ? hull.minZ : hull.minX;
        double acrossMax = axisX ? hull.maxZ : hull.maxX;
        WallPivot pivot = wallPivot(localBoxes, bounds, shapePivot, rotation, position, direction,
                new WallSurface(plane, faceBottom, top, acrossMin, acrossMax), pivotReach, true);
        if (pivot != null && Math.abs(pivot.world().y - top) > PIVOT_SNAP_EPSILON) {
            pivot = new WallPivot(pivot.local(),
                    new Vec3(pivot.world().x, top, pivot.world().z), pivot.surface());
        }
        String reason = pivot != null ? "ok"
                : pivotOutOfReach ? "nocontact" : "nocandidate";
        return new DescentProbe(reason, support, top, bodyFront, plane,
                marched, beyondBody, aheadTop, drop, faceBottom, edge, pivot, lastWallCandidates,
                lastWallDistance, surfaces.size(), pivRejYBand, pivRejAcross,
                pivotOutOfReach ? 1 : 0, pivotReach);
    }

    public interface PoseCheck {
        @Nullable String fails(Quaternionf pose, Vec3 position);
    }

    public record DescentStep(int index, double degrees, double bodyY, double pivotToBody,
                              double gapToFloor, double gapToWall, double penetration,
                              boolean supported, boolean landed, String failure) {
    }

    public record DescentPlan(boolean landed, int cleanSteps, double degrees, String stop,
                              List<DescentStep> steps, double worstPenetration,
                              double worstPivotToBody, double worstGapToFloor, double worstGapToWall,
                              double entryShift, double endMisalignment, Quaternionf endPose,
                              Vec3 endPosition) {
    }

    public static DescentPlan descentPlan(final List<AABB> localBoxes, final Vec3 shapePivot,
                                          final Quaternionf rotation, final Vec3 position,
                                          final WallPivot pivot, final Direction direction,
                                          final List<AABB> terrain, final double minDrop,
                                          final double maxDegrees, final PoseCheck check) {
        return descentPlan(localBoxes, shapePivot, rotation, position, pivot, direction, terrain,
                minDrop, maxDegrees, true, check);
    }

    public static DescentPlan descentPlan(final List<AABB> localBoxes, final Vec3 shapePivot,
                                          final Quaternionf rotation, final Vec3 position,
                                          final WallPivot pivot, final Direction direction,
                                          final List<AABB> terrain, final double minDrop,
                                          final double maxDegrees, final boolean stopOnLanding,
                                          final PoseCheck check) {
        Quaternionf pose = new Quaternionf(rotation);
        Vec3 where = position;
        WallSurface surface = pivot.surface();
        double wallPlane = surface.plane();
        double landingY = surface.maxY() - minDrop + LANDING_HEIGHT_SLOP;
        double entryShift = positionForPivot(localBoxes, shapePivot, rotation, pivot.local(),
                pivot.world()).distanceTo(position);
        List<DescentStep> steps = new ArrayList<>();
        int cap = Math.max(1, (int)Math.ceil((maxDegrees - STEP_CAP_EPSILON) / DEGREES));
        double remaining = maxDegrees;
        double turned = 0.0;
        int clean = 0;
        boolean landed = false;
        String stop = "limit";
        double worstPen = 0.0;
        double worstBody = 0.0;
        double worstFloor = 0.0;
        double worstWall = Double.NEGATIVE_INFINITY;
        for (int i = 1; i <= cap && (!landed || !stopOnLanding); i++) {
            double stepDegrees = Math.min(DEGREES, remaining);
            WallTurn midpoint = wallTurn(localBoxes, shapePivot, pose, where, pivot, direction,
                    stepDegrees * 0.5);
            WallTurn turn = wallTurn(localBoxes, shapePivot, pose, where, pivot, direction,
                    stepDegrees);
            String failure = midpoint == null ? "nomid" : turn == null ? "nostep" : null;
            if (failure == null) {
                String mid = check.fails(midpoint.rotation(), midpoint.position());
                failure = mid == null ? null : "mid:" + mid;
            }
            if (failure == null) {
                String end = check.fails(turn.rotation(), turn.position());
                failure = end == null ? null : "end:" + end;
            }
            if (failure != null) {
                double lo = 0.0;
                double hi = stepDegrees;
                WallTurn seated = null;
                for (int b = 0; b < DESCENT_BISECT_STEPS; b++) {
                    double mid = (lo + hi) * 0.5;
                    WallTurn half = wallTurn(localBoxes, shapePivot, pose, where, pivot, direction,
                            mid * 0.5);
                    WallTurn full = wallTurn(localBoxes, shapePivot, pose, where, pivot, direction,
                            mid);
                    if (half != null && full != null
                            && check.fails(half.rotation(), half.position()) == null
                            && check.fails(full.rotation(), full.position()) == null) {
                        lo = mid;
                        seated = full;
                    } else {
                        hi = mid;
                    }
                }
                if (seated == null || lo < BISECT_MIN_DEGREES) {
                    steps.add(new DescentStep(i, turned, where.y, Double.NaN, Double.NaN,
                            Double.NaN, Double.NaN, false, false, failure));
                    stop = failure;
                    break;
                }
                pose = seated.rotation();
                where = seated.position();
                boolean seatedSupport = check.fails(pose, where.subtract(0.0, SUPPORT_PROBE_DROP, 0.0)) != null;
                landed = seatedSupport && where.y <= landingY;
                double seatedPen = worstPenetration(localBoxes, shapePivot, pose, where, terrain);
                double seatedFloor = gapToFloor(localBoxes, shapePivot, pose, where, terrain);
                double seatedWall = gapToWall(localBoxes, shapePivot, pose, where,
                        wallPlane, direction);
                worstPen = Math.max(worstPen, seatedPen);
                worstFloor = Math.max(worstFloor, seatedFloor);
                worstWall = Math.max(worstWall, seatedWall);
                steps.add(new DescentStep(i, turned + lo, where.y,
                        pivotToBody(localBoxes, shapePivot, pose, where, pivot), seatedFloor,
                        seatedWall, seatedPen, seatedSupport, landed, "touched:" + failure));
                stop = landed ? "landed" : "touched";
                clean = i;
                break;
            }
            pose = turn.rotation();
            where = turn.position();
            clean = i;
            remaining -= stepDegrees;
            turned += stepDegrees;
            boolean supported = check.fails(pose, where.subtract(0.0, SUPPORT_PROBE_DROP, 0.0)) != null;
            landed = supported && where.y <= landingY;
            double pen = worstPenetration(localBoxes, shapePivot, pose, where, terrain);
            double toBody = pivotToBody(localBoxes, shapePivot, pose, where, pivot);
            double toFloor = gapToFloor(localBoxes, shapePivot, pose, where, terrain);
            double toWall = gapToWall(localBoxes, shapePivot, pose, where, wallPlane, direction);
            worstPen = Math.max(worstPen, pen);
            worstBody = Math.max(worstBody, toBody);
            worstFloor = Math.max(worstFloor, toFloor);
            worstWall = Math.max(worstWall, toWall);
            steps.add(new DescentStep(i, turned, where.y, toBody, toFloor, toWall, pen,
                    supported, landed, "-"));
        }
        double degrees = steps.isEmpty() ? 0.0 : steps.getLast().degrees();
        return new DescentPlan(landed, clean, degrees, landed ? "landed" : stop, steps,
                worstPen, worstBody, worstFloor, worstWall, entryShift, misalignment(pose),
                pose, where);
    }

    public static double pivotToBody(final List<AABB> localBoxes, final Vec3 shapePivot,
                                     final Quaternionf rotation, final Vec3 position,
                                     final WallPivot pivot) {
        return worldPoint(localBoxes, shapePivot, rotation, position, pivot.local())
                .distanceTo(pivot.world());
    }

    public static List<Vec3> bodyVertices(final List<AABB> localBoxes, final Vec3 shapePivot,
                                          final Quaternionf rotation, final Vec3 position) {
        Vec3 anchor = worldPoint(localBoxes, shapePivot, rotation, position, shapePivot);
        List<Vec3> out = new ArrayList<>(localBoxes.size() * CORNERS_PER_BOX);
        Vector3f corner = new Vector3f();
        for (AABB box : localBoxes) {
            for (int i = 0; i < CORNERS_PER_BOX; i++) {
                corner.set(
                        (float)(((i & 1) == 0 ? box.minX : box.maxX) - shapePivot.x),
                        (float)(((i & 2) == 0 ? box.minY : box.maxY) - shapePivot.y),
                        (float)(((i & 4) == 0 ? box.minZ : box.maxZ) - shapePivot.z));
                corner.rotate(rotation);
                out.add(new Vec3(anchor.x + corner.x, anchor.y + corner.y, anchor.z + corner.z));
            }
        }
        return out;
    }

    public static double gapToFloor(final List<AABB> localBoxes, final Vec3 shapePivot,
                                    final Quaternionf rotation, final Vec3 position,
                                    final List<AABB> terrain) {
        double touch = FLOOR_TOUCH_EPSILON;
        double hi = 0.5;
        if (worstPenetration(localBoxes, shapePivot, rotation,
                position.subtract(0.0, hi, 0.0), terrain) <= touch) {
            return hi;
        }
        double lo = 0.0;
        for (int i = 0; i < FLOOR_BISECT_STEPS; i++) {
            double mid = (lo + hi) * 0.5;
            if (worstPenetration(localBoxes, shapePivot, rotation,
                    position.subtract(0.0, mid, 0.0), terrain) > touch) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return lo;
    }

    public static double gapToWall(final List<AABB> localBoxes, final Vec3 shapePivot,
                                   final Quaternionf rotation, final Vec3 position,
                                   final double plane, final Direction direction) {
        double front = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : bodyVertices(localBoxes, shapePivot, rotation, position)) {
            double along = switch (direction) {
                case EAST -> vertex.x;
                case WEST -> -vertex.x;
                case SOUTH -> vertex.z;
                case NORTH -> -vertex.z;
                default -> Double.NEGATIVE_INFINITY;
            };
            front = Math.max(front, along);
        }
        double signedPlane = switch (direction) {
            case EAST, SOUTH -> plane;
            case WEST, NORTH -> -plane;
            default -> 0.0;
        };
        return signedPlane - front;
    }

    private static double face(final AABB box, final Direction direction) {
        return switch (direction) {
            case EAST -> box.maxX;
            case WEST -> box.minX;
            case SOUTH -> box.maxZ;
            default -> box.minZ;
        };
    }

    private static double beyond(final double plane, final double front, final Direction direction) {
        return (plane - front) * (direction.getStepX() + direction.getStepZ());
    }
}
