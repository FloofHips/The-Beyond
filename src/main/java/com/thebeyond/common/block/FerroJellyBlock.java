package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

    public boolean canStickTo(Level level, BlockState state, BlockPos neighborPos, BlockState neighborState, Direction dir) {
        Direction.Axis axis = state.getValue(AXIS);

        if (axis != dir.getAxis()) return false;
        if (neighborState.is(this)) return axis == neighborState.getValue(AXIS);

        return Block.canSupportCenter(level, neighborPos, dir.getOpposite());
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }
}
