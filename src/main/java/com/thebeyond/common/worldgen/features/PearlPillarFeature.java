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

            SimplexNoise noise = new SimplexNoise(randomsource);
            if (worldgenlevel.getBlockState(blockpos$mutableblockpos).isSolid())
                placeRing(worldgenlevel, randomsource, blockpos$mutableblockpos.above());
            PearlPoolFeature.createFloor(worldgenlevel,noise ,4 , blockpos$mutableblockpos, blockpos$mutableblockpos);

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
    }
}
