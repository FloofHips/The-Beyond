package com.thebeyond.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ParanoiaBlock extends Block {
    public ParanoiaBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState block = level.getBlockState(pos.below());
        if(!block.isAir()){
            level.playSound(null, pos, block.getSoundType(level, pos.below(), null).getStepSound(), SoundSource.AMBIENT, level.random.nextFloat() * 2, level.random.nextFloat() * 2);
        }
        super.randomTick(state, level, pos, random);
    }
}
