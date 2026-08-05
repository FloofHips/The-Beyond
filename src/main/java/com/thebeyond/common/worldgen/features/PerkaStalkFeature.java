package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.block.PerkaStalkMouthBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.jetbrains.annotations.NotNull;

public class PerkaStalkFeature extends Feature<NoneFeatureConfiguration> {
    public PerkaStalkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        BlockPos blockpos = context.origin();
        WorldGenLevel worldgenlevel = context.level();
        RandomSource randomsource = context.random();
        if (worldgenlevel.isEmptyBlock(blockpos) && worldgenlevel.isEmptyBlock(blockpos.above()) && !worldgenlevel.isEmptyBlock(blockpos.below())) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = blockpos.mutable();

            int counter = 0;
            int maxHeight = randomsource.nextInt(4,10);

            createViletFloor(worldgenlevel, randomsource, blockpos$mutableblockpos.below());

            while(counter < maxHeight && (worldgenlevel.isEmptyBlock(blockpos$mutableblockpos) || worldgenlevel.getBlockState(blockpos$mutableblockpos).is(BeyondBlocks.PEARL.get())) && counter < 40) {
                if (worldgenlevel.isOutsideBuildHeight(blockpos$mutableblockpos)) {
                    return true;
                }

                placeBlock(worldgenlevel, randomsource, blockpos$mutableblockpos);

                counter++;
                blockpos$mutableblockpos.move(Direction.UP);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void placeBlock(WorldGenLevel worldgenlevel, RandomSource randomsource, BlockPos.MutableBlockPos pos) {
        if (randomsource.nextInt(3) == 0) {
            worldgenlevel.setBlock(pos, BeyondBlocks.PERKA_STALK_MOUTH.get().defaultBlockState().setValue(PerkaStalkMouthBlock.FACING, GetDirection(randomsource)), 2);
            return;
        }        worldgenlevel.setBlock(pos, BeyondBlocks.PERKA_STALK.get().defaultBlockState(), 2);
    }

    private static @NotNull Direction GetDirection(RandomSource randomsource) {
        Direction dir = Direction.getRandom(randomsource);
        if (dir.getAxis().isVertical()) dir = randomsource.nextBoolean() ? Direction.SOUTH : Direction.NORTH;
        return dir;
    }

    private void createViletFloor(WorldGenLevel worldgenlevel, RandomSource randomsource, BlockPos pos) {
        int radius = randomsource.nextInt(2, 5);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {

                    BlockPos blockPos = pos.offset(x, y, z);
                    double distedSqr = blockPos.distSqr(pos);

                    if (distedSqr <= radius * radius) {
                        if (worldgenlevel.getBlockState(blockPos.above()).isAir() && worldgenlevel.getBlockState(blockPos).is(BeyondTags.END_FLOOR_BLOCKS)) {
                            worldgenlevel.setBlock(blockPos, BeyondBlocks.VILET.get().defaultBlockState(), 3);
                            if (randomsource.nextBoolean())
                                worldgenlevel.setBlock(blockPos.above(), BeyondBlocks.VILET_GROWTH.get().defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }
}
