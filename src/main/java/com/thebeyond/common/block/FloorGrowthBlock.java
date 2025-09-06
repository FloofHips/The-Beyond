package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FloorGrowthBlock extends TallGrassBlock {
    public FloorGrowthBlock(Properties properties) {
        super(properties);
    }
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BeyondBlocks.ZYMOTE);
    }
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {

    }

    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos blockpos = pos.below();
        BlockState belowBlockState = level.getBlockState(blockpos);
        //replace with tag
        return belowBlockState.is(BeyondBlocks.ZYMOTE);
    }
}
