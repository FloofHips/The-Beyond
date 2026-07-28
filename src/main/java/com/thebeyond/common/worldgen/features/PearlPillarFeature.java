package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

public class PearlPillarFeature extends Feature<NoneFeatureConfiguration> {
    public PearlPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        if (worldgenlevel.isEmptyBlock(blockpos) && !worldgenlevel.isEmptyBlock(blockpos.above())) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos.mutable();

            int counter = 0;
            boolean X = randomsource.nextBoolean();

            placeRing(worldgenlevel, randomsource, blockpos$mutableblockpos);

            while((worldgenlevel.isEmptyBlock(blockpos$mutableblockpos) || worldgenlevel.getBlockState(blockpos$mutableblockpos).is(BeyondBlocks.PEARL.get())) && counter < 40) {
                if (worldgenlevel.isOutsideBuildHeight(blockpos$mutableblockpos)) {
                    return true;
                }

                counter++;
                blockpos$mutableblockpos.move(Direction.DOWN);
            }

            //blockpos$mutableblockpos.move(Direction.UP);
            //this.placeBaseHangOff(worldgenlevel, randomsource, blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos, Direction.NORTH));
            //this.placeBaseHangOff(worldgenlevel, randomsource, blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos, Direction.SOUTH));
            //this.placeBaseHangOff(worldgenlevel, randomsource, blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos, Direction.WEST));
            //this.placeBaseHangOff(worldgenlevel, randomsource, blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos, Direction.EAST));
            //blockpos$mutableblockpos.move(Direction.DOWN);
            //BlockPos.MutableBlockPos blockpos$mutableblockpos2 = new BlockPos.MutableBlockPos();

            //for(int i = -3; i < 4; ++i) {
            //    for(int j = -3; j < 4; ++j) {
            //        int k = Mth.abs(i) * Mth.abs(j);
            //        if (randomsource.nextInt(10) < 10 - k) {
            //            blockpos$mutableblockpos2.set(blockpos$mutableblockpos.offset(i, 0, j));
            //            int l = 3;
//
            //            while(worldgenlevel.isEmptyBlock(blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos2, Direction.DOWN))) {
            //                blockpos$mutableblockpos2.move(Direction.DOWN);
            //                --l;
            //                if (l <= 0) {
            //                    break;
            //                }
            //            }
//
            //            if (!worldgenlevel.isEmptyBlock(blockpos$mutableblockpos1.setWithOffset(blockpos$mutableblockpos2, Direction.DOWN))) {
            //                worldgenlevel.setBlock(blockpos$mutableblockpos2, BeyondBlocks.PEARL.get().defaultBlockState(), 2);
            //            }
            //        }
            //    }
            //}

            SimplexNoise noise = new SimplexNoise(randomsource);
            if (worldgenlevel.getBlockState(blockpos$mutableblockpos).isSolid())
                placeRing(worldgenlevel, randomsource, blockpos$mutableblockpos.above());
            PearlPoolFeature.createFloor(worldgenlevel,noise ,4 , blockpos$mutableblockpos);

            return true;
        } else {
            return false;
        }
    }

    private void placeBaseHangOff(LevelAccessor level, RandomSource random, BlockPos pos) {
        if (random.nextBoolean()) {
            level.setBlock(pos, BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        }

    }

    private boolean placeHangOff(LevelAccessor level, RandomSource random, BlockPos pos) {
        if (random.nextFloat() < 0.5) {
            level.setBlock(pos, BeyondBlocks.PEARL.get().defaultBlockState(), 2);
            return true;
        } else {
            return false;
        }
    }

    private void placeRing(LevelAccessor level, RandomSource random, BlockPos pos) {
        Direction axis = random.nextBoolean() ? Direction.NORTH : Direction.EAST;

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);

        if (random.nextBoolean())level.setBlock(pos.offset(axis.getStepX(), 1, axis.getStepZ()),   BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        if (random.nextBoolean())level.setBlock(pos.offset(axis.getStepX(), -1, axis.getStepZ()),  BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        if (random.nextBoolean())level.setBlock(pos.offset(-axis.getStepX(), 1, -axis.getStepZ()), BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        if (random.nextBoolean()) level.setBlock(pos.offset(-axis.getStepX(), -1,-axis.getStepZ()), BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        level.setBlock(pos.offset(0, 1, 0),   BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        level.setBlock(pos.offset(-axis.getStepX(), 0, -axis.getStepZ()), BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        level.setBlock(pos.offset(axis.getStepX(), 0, axis.getStepZ()),   BeyondBlocks.PEARL.get().defaultBlockState(), 2);
        level.setBlock(pos.offset(0, -1, 0),   BeyondBlocks.PEARL.get().defaultBlockState(), 2);

        //level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
    }
}
