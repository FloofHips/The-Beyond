package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FerroJellyBlock extends RotatedPillarBlock {
    public FerroJellyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)this.defaultBlockState().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    public boolean canStickTo(BlockState state, BlockState other) {
        return true;
    }

    public boolean canStickTo(Level level, BlockPos relative, BlockState state, BlockPos neighborPos, BlockState neighborState, Direction opposite, Direction dir) {

        if (neighborState.isAir()) return false;

        Direction.Axis axis = state.getValue(AXIS);

        Direction toNeighbor = Direction.fromDelta(
                neighborPos.getX() - relative.getX(),
                neighborPos.getY() - relative.getY(),
                neighborPos.getZ() - relative.getZ()
        );

        if (neighborState.is(this)) {
            return neighborState.getValue(RotatedPillarBlock.AXIS) == state.getValue(RotatedPillarBlock.AXIS);
        }

        return toNeighbor != null && toNeighbor.getAxis() == axis;
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }
}
