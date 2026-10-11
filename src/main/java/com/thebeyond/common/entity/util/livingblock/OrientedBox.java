package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class OrientedBox {
    private static final int AXIS_COUNT = 3;
    private static final int SAT_FACE_AXES = 6;
    private static final int SAT_AXIS_COUNT = 15;
    private static final int CORNERS_PER_BOX = 8;

    private static final float SAT_EPSILON = 1.0E-6F;
    private static final double SWEEP_STATIONARY = 1.0E-12;
    private static final double SWEEP_CONTACT_SLOP = 1.0E-7;
    private static final double VERTICAL_SLOPE_EPSILON = 1.0e-7;
    private static final double EDGE_EPSILON = 1.0E-12;

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double extentX;
    private final double extentY;
    private final double extentZ;
    private final double spanX;
    private final double spanY;
    private final double spanZ;
    private final Vector3f[] axes;
    private final AABB worldAABB;
    private final AABB localAABB;

    public OrientedBox(AABB localBox, Vec3 worldAnchor, Vec3 pivot, Quaternionf rotation) {
        this.localAABB = localBox;

        this.extentX = (localBox.maxX - localBox.minX) * 0.5;
        this.extentY = (localBox.maxY - localBox.minY) * 0.5;
        this.extentZ = (localBox.maxZ - localBox.minZ) * 0.5;

        double localCenterX = (localBox.minX + localBox.maxX) * 0.5 - pivot.x;
        double localCenterY = (localBox.minY + localBox.maxY) * 0.5 - pivot.y;
        double localCenterZ = (localBox.minZ + localBox.maxZ) * 0.5 - pivot.z;

        Vector3f rotatedCenter = new Vector3f((float) localCenterX, (float) localCenterY, (float) localCenterZ);
        rotatedCenter.rotate(rotation);

        this.centerX = worldAnchor.x + rotatedCenter.x();
        this.centerY = worldAnchor.y + rotatedCenter.y();
        this.centerZ = worldAnchor.z + rotatedCenter.z();

        Matrix3f rotMatrix = new Matrix3f().rotation(rotation);
        Vector3f right = rotMatrix.getColumn(0, new Vector3f());
        Vector3f up = rotMatrix.getColumn(1, new Vector3f());
        Vector3f forward = rotMatrix.getColumn(2, new Vector3f());
        this.axes = new Vector3f[] {right, up, forward};

        this.spanX = Math.abs(right.x) * extentX + Math.abs(up.x) * extentY + Math.abs(forward.x) * extentZ;
        this.spanY = Math.abs(right.y) * extentX + Math.abs(up.y) * extentY + Math.abs(forward.y) * extentZ;
        this.spanZ = Math.abs(right.z) * extentX + Math.abs(up.z) * extentY + Math.abs(forward.z) * extentZ;
        this.worldAABB = new AABB(centerX - spanX, centerY - spanY, centerZ - spanZ,
                centerX + spanX, centerY + spanY, centerZ + spanZ);
    }

    public OrientedBox(AABB worldBox) {
        this.localAABB = worldBox;
        this.worldAABB = worldBox;
        this.extentX = (worldBox.maxX - worldBox.minX) * 0.5;
        this.extentY = (worldBox.maxY - worldBox.minY) * 0.5;
        this.extentZ = (worldBox.maxZ - worldBox.minZ) * 0.5;
        this.spanX = this.extentX;
        this.spanY = this.extentY;
        this.spanZ = this.extentZ;
        this.centerX = (worldBox.minX + worldBox.maxX) * 0.5;
        this.centerY = (worldBox.minY + worldBox.maxY) * 0.5;
        this.centerZ = (worldBox.minZ + worldBox.maxZ) * 0.5;
        this.axes = new Vector3f[] {
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F)
        };
    }

    private static boolean satAxis(final OrientedBox a, final OrientedBox b, final int i, final Vector3f out) {
        if (i < AXIS_COUNT) {
            out.set(a.axes[i]);
            return true;
        }
        if (i < SAT_FACE_AXES) {
            out.set(b.axes[i - AXIS_COUNT]);
            return true;
        }
        a.axes[(i - SAT_FACE_AXES) / AXIS_COUNT].cross(b.axes[(i - SAT_FACE_AXES) % AXIS_COUNT], out);
        if (out.lengthSquared() < SAT_EPSILON) {
            return false;
        }
        out.normalize();
        return true;
    }

    public static double penetration(final OrientedBox a, final OrientedBox b, final Vector3f outAxis) {
        Vector3f bestAxis = new Vector3f();
        double best = mtvAt(a, 0.0, 0.0, 0.0, b, bestAxis);
        if (best <= 0.0) {
            return 0.0;
        }
        outAxis.set(bestAxis);
        return best;
    }

    public static double separation(final OrientedBox a, final OrientedBox b) {
        double deltaX = b.centerX - a.centerX;
        double deltaY = b.centerY - a.centerY;
        double deltaZ = b.centerZ - a.centerZ;
        Vector3f candidate = new Vector3f();
        double best = 0.0;

        for (int i = 0; i < SAT_AXIS_COUNT; i++) {
            if (!satAxis(a, b, i, candidate)) {
                continue;
            }
            double reach = a.project(candidate) + b.project(candidate);
            double along = candidate.x() * deltaX + candidate.y() * deltaY + candidate.z() * deltaZ;
            best = Math.max(best, Math.abs(along) - reach);
        }
        return best;
    }

    public static double sweep(final OrientedBox a, final double offsetX, final double offsetY, final double offsetZ,
                               final OrientedBox b,
                               final double motionX, final double motionY, final double motionZ) {
        double deltaX = b.centerX - a.centerX - offsetX;
        double deltaY = b.centerY - a.centerY - offsetY;
        double deltaZ = b.centerZ - a.centerZ - offsetZ;
        double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (length <= 0.0) {
            return 1.0;
        }
        if (Math.abs(deltaX) - Math.abs(motionX) >= a.spanX + b.spanX
                || Math.abs(deltaY) - Math.abs(motionY) >= a.spanY + b.spanY
                || Math.abs(deltaZ) - Math.abs(motionZ) >= a.spanZ + b.spanZ) {
            return 1.0;
        }

        Vector3f candidate = new Vector3f();
        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;

        for (int i = 0; i < SAT_AXIS_COUNT; i++) {
            if (!satAxis(a, b, i, candidate)) {
                continue;
            }
            double reach = a.project(candidate) + b.project(candidate);
            double along = candidate.x() * deltaX + candidate.y() * deltaY + candidate.z() * deltaZ;
            double closing = candidate.x() * motionX + candidate.y() * motionY + candidate.z() * motionZ;

            if (Math.abs(closing) <= SWEEP_STATIONARY) {
                if (Math.abs(along) >= reach - SWEEP_CONTACT_SLOP) {
                    return 1.0;
                }
                continue;
            }

            double first = (along - reach) / closing;
            double second = (along + reach) / closing;
            entry = Math.max(entry, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            if (entry >= exit) {
                return 1.0;
            }
        }

        if (entry == Double.NEGATIVE_INFINITY || entry >= 1.0 || exit <= 0.0) {
            return 1.0;
        }
        if (entry * length > 0.0) {
            return entry;
        }

        Vector3f escape = new Vector3f();
        if (mtvAt(a, offsetX, offsetY, offsetZ, b, escape) <= 0.0) {
            return 0.0;
        }
        double towardsEscape = escape.x() * motionX + escape.y() * motionY + escape.z() * motionZ;
        if (towardsEscape < -SWEEP_CONTACT_SLOP) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, exit));
    }

    static double mtvAt(final OrientedBox a, final double offsetX, final double offsetY,
                        final double offsetZ, final OrientedBox b, final Vector3f outAxis) {
        double deltaX = b.centerX - a.centerX - offsetX;
        double deltaY = b.centerY - a.centerY - offsetY;
        double deltaZ = b.centerZ - a.centerZ - offsetZ;
        Vector3f candidate = new Vector3f();
        double best = Double.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < SAT_AXIS_COUNT; i++) {
            if (!satAxis(a, b, i, candidate)) {
                continue;
            }
            double reach = a.project(candidate) + b.project(candidate);
            double along = candidate.x() * deltaX + candidate.y() * deltaY + candidate.z() * deltaZ;
            double overlap = reach - Math.abs(along);
            if (overlap <= 0.0) {
                return 0.0;
            }
            if (overlap < best) {
                best = overlap;
                found = true;
                outAxis.set(candidate);
                if (along > 0.0) {
                    outAxis.negate();
                }
            }
        }
        return found ? best : 0.0;
    }

    private double project(final Vector3f axis) {
        return Math.abs(axis.dot(axes[0])) * extentX
                + Math.abs(axis.dot(axes[1])) * extentY
                + Math.abs(axis.dot(axes[2])) * extentZ;
    }

    public boolean verticalSpanAt(double worldX, double worldZ, double[] outMinMax) {
        double px = worldX - centerX;
        double py = -centerY;
        double pz = worldZ - centerZ;

        double low = Double.NEGATIVE_INFINITY;
        double high = Double.POSITIVE_INFINITY;

        for (int i = 0; i < AXIS_COUNT; i++) {
            Vector3f axis = axes[i];
            double slope = axis.y;
            double offset = px * axis.x + py * axis.y + pz * axis.z;
            double extent = switch (i) {
                case 0 -> extentX;
                case 1 -> extentY;
                default -> extentZ;
            };

            if (Math.abs(slope) < VERTICAL_SLOPE_EPSILON) {
                if (Math.abs(offset) > extent) {
                    return false;
                }
                continue;
            }

            double a = (-extent - offset) / slope;
            double b = (extent - offset) / slope;
            low = Math.max(low, Math.min(a, b));
            high = Math.min(high, Math.max(a, b));
            if (low > high) {
                return false;
            }
        }

        if (low == Double.NEGATIVE_INFINITY || high == Double.POSITIVE_INFINITY) {
            return false;
        }
        outMinMax[0] = low;
        outMinMax[1] = high;
        return true;
    }

    public double highestSurfaceUnder(final AABB footprint) {
        Vector3f right = axes[0];
        Vector3f up = axes[1];
        Vector3f forward = axes[2];

        double best = Double.NEGATIVE_INFINITY;
        double[] span = new double[2];
        double[][] points = new double[CORNERS_PER_BOX][AXIS_COUNT];
        for (int i = 0; i < CORNERS_PER_BOX; i++) {
            double sx = (i & 1) == 0 ? -extentX : extentX;
            double sy = (i & 2) == 0 ? -extentY : extentY;
            double sz = (i & 4) == 0 ? -extentZ : extentZ;
            double[] point = points[i];
            point[0] = centerX + right.x * sx + up.x * sy + forward.x * sz;
            point[1] = centerY + right.y * sx + up.y * sy + forward.y * sz;
            point[2] = centerZ + right.z * sx + up.z * sy + forward.z * sz;
            if (point[0] >= footprint.minX && point[0] <= footprint.maxX
                    && point[2] >= footprint.minZ && point[2] <= footprint.maxZ) {
                best = Math.max(best, point[1]);
            }
        }
        double[] xs = {footprint.minX, footprint.maxX};
        double[] zs = {footprint.minZ, footprint.maxZ};
        for (double x : xs) {
            for (double z : zs) {
                if (this.verticalSpanAt(x, z, span)) {
                    best = Math.max(best, span[1]);
                }
            }
        }
        for (int i = 0; i < CORNERS_PER_BOX; i++) {
            for (int axis = 0; axis < AXIS_COUNT; axis++) {
                int bit = 1 << axis;
                if ((i & bit) != 0) {
                    continue;
                }
                double[] a = points[i];
                double[] b = points[i | bit];
                double dx = b[0] - a[0];
                double dz = b[2] - a[2];
                if (Math.abs(dx) > EDGE_EPSILON) {
                    for (double x : xs) {
                        double t = (x - a[0]) / dx;
                        double z = a[2] + dz * t;
                        if (t >= 0.0 && t <= 1.0 && z >= footprint.minZ && z <= footprint.maxZ
                                && this.verticalSpanAt(x, z, span)) {
                            best = Math.max(best, span[1]);
                        }
                    }
                }
                if (Math.abs(dz) > EDGE_EPSILON) {
                    for (double z : zs) {
                        double t = (z - a[2]) / dz;
                        double x = a[0] + dx * t;
                        if (t >= 0.0 && t <= 1.0 && x >= footprint.minX && x <= footprint.maxX
                                && this.verticalSpanAt(x, z, span)) {
                            best = Math.max(best, span[1]);
                        }
                    }
                }
            }
        }
        return best;
    }

    public double centerX() { return centerX; }
    public double centerY() { return centerY; }
    public double centerZ() { return centerZ; }
    public Vector3f getCenter() { return new Vector3f((float) centerX, (float) centerY, (float) centerZ); }
    public Vector3f getExtents() { return new Vector3f((float) extentX, (float) extentY, (float) extentZ); }
    public Vector3f[] getAxes() { return axes; }
    public AABB getWorldAABB() { return worldAABB; }
    public AABB getLocalAABB() { return localAABB; }
}
