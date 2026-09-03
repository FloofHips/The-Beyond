package com.thebeyond.common.entity.util.livingblock;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class OrientedBoxShape extends ArrayVoxelShape {

    private static final double EPSILON = 1.0E-7;

    private final OrientedBox box;
    private final AABB world;
    private final double stepTop;

    public OrientedBoxShape(final OrientedBox box, final AABB world) {
        this(box, world, Double.NaN);
    }

    private OrientedBoxShape(final OrientedBox box, final AABB world, final double stepTop) {
        super(unitCell(),
                new double[] {world.minX, world.maxX},
                new double[] {world.minY, world.maxY},
                new double[] {world.minZ, world.maxZ});
        this.box = box;
        this.world = world;
        this.stepTop = stepTop;
    }

    public OrientedBoxShape forStepFootprint(final AABB footprint) {
        double top = this.box.highestSurfaceUnder(footprint);
        if (!Double.isFinite(top)) {
            top = this.world.minY + EPSILON;
        }
        top = Math.max(this.world.minY + EPSILON, Math.min(this.world.maxY, top));
        return new OrientedBoxShape(this.box, this.world, top);
    }

    @Override
    public DoubleList getCoords(final Direction.Axis axis) {
        if (axis == Direction.Axis.Y && Double.isFinite(this.stepTop)) {
            return DoubleArrayList.wrap(new double[] {this.world.minY, this.stepTop});
        }
        return super.getCoords(axis);
    }

    private static DiscreteVoxelShape unitCell() {
        BitSetDiscreteVoxelShape cell = new BitSetDiscreteVoxelShape(1, 1, 1);
        cell.fill(0, 0, 0);
        return cell;
    }

    @Override
    public double collide(final Direction.Axis axis, final AABB collisionBox, final double desired) {
        if (Math.abs(desired) < EPSILON) {
            return desired;
        }
        double moveX = axis == Direction.Axis.X ? desired : 0.0;
        double moveY = axis == Direction.Axis.Y ? desired : 0.0;
        double moveZ = axis == Direction.Axis.Z ? desired : 0.0;
        double allowed = desired * OrientedBox.sweep(new OrientedBox(collisionBox), 0.0, 0.0, 0.0,
                this.box, moveX, moveY, moveZ);
        return Math.abs(allowed) < EPSILON ? 0.0 : allowed;
    }
}
