package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Quaternionfc;
import org.joml.Quaternionf;

public final class LivingBlockOrientation {

    private static final int FACE_COUNT = 6;
    private static final int ROTATIONS_PER_FACE = 4;
    private static final int AXIS_COUNT = 3;

    public static final int COUNT = FACE_COUNT * ROTATIONS_PER_FACE;

    private static final Direction.Axis[] AXES = Direction.Axis.values();
    private static final Direction[][] RINGS = new Direction[FACE_COUNT][];
    private static final LivingBlockOrientation[] TABLE = new LivingBlockOrientation[COUNT];

    static {
        Direction[] aroundY = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Direction[] aroundX = {Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH};
        Direction[] aroundZ = {Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST};
        RINGS[Direction.DOWN.ordinal()] = aroundY;
        RINGS[Direction.UP.ordinal()] = aroundY;
        RINGS[Direction.NORTH.ordinal()] = aroundZ;
        RINGS[Direction.SOUTH.ordinal()] = aroundZ;
        RINGS[Direction.WEST.ordinal()] = aroundX;
        RINGS[Direction.EAST.ordinal()] = aroundX;

        for (Direction up : Direction.values()) {
            Direction[] ring = RINGS[up.ordinal()];
            for (int k = 0; k < ROTATIONS_PER_FACE; k++) {
                int index = up.ordinal() * ROTATIONS_PER_FACE + k;
                TABLE[index] = new LivingBlockOrientation((byte) index, up, ring[k]);
            }
        }
    }

    public static final LivingBlockOrientation IDENTITY = TABLE[Direction.UP.ordinal() * ROTATIONS_PER_FACE];

    private final byte index;
    private final Direction up;
    private final Direction north;
    private final int[] sourceAxis = new int[AXIS_COUNT];
    private final int[] sourceSign = new int[AXIS_COUNT];
    private final Quaternionf rotation;

    private LivingBlockOrientation(byte index, Direction up, Direction north) {
        this.index = index;
        this.up = up;
        this.north = north;

        int[] u = {up.getStepX(), up.getStepY(), up.getStepZ()};
        int[] n = {north.getStepX(), north.getStepY(), north.getStepZ()};

        int[] e = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
        int[][] columns = {e, u, {-n[0], -n[1], -n[2]}};

        for (int row = 0; row < AXIS_COUNT; row++) {
            for (int column = 0; column < AXIS_COUNT; column++) {
                int value = columns[column][row];
                if (value != 0) {
                    this.sourceAxis[row] = column;
                    this.sourceSign[row] = value;
                }
            }
        }

        Matrix3f m = new Matrix3f(
                columns[0][0], columns[0][1], columns[0][2],
                columns[1][0], columns[1][1], columns[1][2],
                columns[2][0], columns[2][1], columns[2][2]);
        this.rotation = new Quaternionf().setFromNormalized(m).normalize();
    }

    public static LivingBlockOrientation of(int index) {
        return TABLE[Math.floorMod(index, COUNT)];
    }

    public static LivingBlockOrientation fromQuaternion(Quaternionfc quaternion) {
        float qx = quaternion.x();
        float qy = quaternion.y();
        float qz = quaternion.z();
        float qw = quaternion.w();
        if (!Float.isFinite(qx) || !Float.isFinite(qy) || !Float.isFinite(qz) || !Float.isFinite(qw)) {
            return IDENTITY;
        }

        LivingBlockOrientation best = IDENTITY;
        double bestDot = -1.0;
        for (LivingBlockOrientation candidate : TABLE) {
            Quaternionf c = candidate.rotation;
            double dot = Math.abs((double) qx * c.x + (double) qy * c.y + (double) qz * c.z + (double) qw * c.w);
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    public byte index() {
        return this.index;
    }

    public boolean isIdentity() {
        return this == IDENTITY;
    }

    public Quaternionf quaternion() {
        return new Quaternionf(this.rotation);
    }

    public double absDot(Quaternionfc other) {
        return Math.abs((double) other.x() * this.rotation.x + (double) other.y() * this.rotation.y
                + (double) other.z() * this.rotation.z + (double) other.w() * this.rotation.w);
    }

    public Direction.Axis worldAxisOf(final Direction.Axis local) {
        for (int row = 0; row < AXIS_COUNT; row++) {
            if (this.sourceAxis[row] == local.ordinal()) {
                return AXES[row];
            }
        }
        return local;
    }

    public AABB transform(AABB centreRelativeLocal) {
        return new AABB(
                lower(centreRelativeLocal, 0), lower(centreRelativeLocal, 1), lower(centreRelativeLocal, 2),
                upper(centreRelativeLocal, 0), upper(centreRelativeLocal, 1), upper(centreRelativeLocal, 2));
    }

    private double lower(final AABB box, final int row) {
        int axis = this.sourceAxis[row];
        return this.sourceSign[row] > 0 ? min(box, axis) : -max(box, axis);
    }

    private double upper(final AABB box, final int row) {
        int axis = this.sourceAxis[row];
        return this.sourceSign[row] > 0 ? max(box, axis) : -min(box, axis);
    }

    private static double min(final AABB box, final int axis) {
        return switch (axis) {
            case 0 -> box.minX;
            case 1 -> box.minY;
            default -> box.minZ;
        };
    }

    private static double max(final AABB box, final int axis) {
        return switch (axis) {
            case 0 -> box.maxX;
            case 1 -> box.maxY;
            default -> box.maxZ;
        };
    }

    @Override
    public String toString() {
        return "LivingBlockOrientation[" + this.index + " up=" + this.up + " north=" + this.north + "]";
    }
}
