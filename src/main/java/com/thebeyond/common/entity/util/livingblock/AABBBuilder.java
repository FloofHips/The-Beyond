package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3fc;

public final class AABBBuilder {

    private float minX = Float.POSITIVE_INFINITY;
    private float minY = Float.POSITIVE_INFINITY;
    private float minZ = Float.POSITIVE_INFINITY;
    private float maxX = Float.NEGATIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;
    private float maxZ = Float.NEGATIVE_INFINITY;
    private boolean defined;

    public void include(final Vector3fc v) {
        this.minX = Math.min(this.minX, v.x());
        this.minY = Math.min(this.minY, v.y());
        this.minZ = Math.min(this.minZ, v.z());
        this.maxX = Math.max(this.maxX, v.x());
        this.maxY = Math.max(this.maxY, v.y());
        this.maxZ = Math.max(this.maxZ, v.z());
        this.defined = true;
    }

    public boolean isDefined() {
        return this.defined;
    }

    public double min(final Direction.Axis axis) {
        return switch (axis) {
            case X -> this.minX;
            case Y -> this.minY;
            case Z -> this.minZ;
        };
    }

    public double max(final Direction.Axis axis) {
        return switch (axis) {
            case X -> this.maxX;
            case Y -> this.maxY;
            case Z -> this.maxZ;
        };
    }

    public double edge(final Direction face) {
        Direction.Axis axis = face.getAxis();
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? this.max(axis) : this.min(axis);
    }

    public AABB build() {
        if (!this.defined) {
            throw new IllegalStateException("Cannot build an undefined AABB. Include at least one point.");
        }
        return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }
}
