package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Random;

public class ObirootFeature extends Feature<NoneFeatureConfiguration> {
    public ObirootFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource source = context.random();

        int f = canStart(level, source, origin);

        if(f == -1)
            return false;

        if(f==0)
            createSmallCore(level, origin);
        else
            createCore(level, origin, f + 1);

        return true;
    }



    public void createBranches(WorldGenLevel level, BlockPos pos, int size){

        for (Direction direction : Direction.values()) {

            if(direction == Direction.DOWN || direction == Direction.UP)
                continue;

            int r = level.getRandom().nextInt(10);
            int r2 = level.getRandom().nextInt(10);

            level.setBlock(pos.offset(direction.getStepX()*(size+2), 0, direction.getStepZ()*(size+2)), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);

            if (r > 5)
                level.setBlock(pos.offset(direction.getStepX()*(size+3), 0, direction.getStepZ()*(size+3)), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);

            level.setBlock(pos.offset(direction.getStepX()*(size+3), 1, direction.getStepZ()*(size+3)), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);
            level.setBlock(pos.offset(direction.getStepX()*(size+2), 2, direction.getStepZ()*(size+2)), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);

            if (r2 > 5)
                level.setBlock(pos.offset(direction.getStepX()*(size+3), 2, direction.getStepZ()*(size+3)), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);

        }
    }

    private void createSmallCore(WorldGenLevel level, BlockPos pos) {
        int offset = level.getRandom().nextInt(2);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = 0; k <= 1 + 1; k++) {
                    level.setBlock(pos.offset(i, k, j), BeyondBlocks.PEEPING_OBIROOT.get().defaultBlockState(), 2);
                }
            }
        }
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 4 + offset; k++) {
                    if(!(k == 4 + offset && i==0 && j==0))
                        level.setBlock(pos.offset(i, k, j), BeyondBlocks.PEEPING_OBIROOT.get().defaultBlockState(), 2);
                    if((k == 3 + offset && i==0 && j==0) && level.getRandom().nextInt(10) < 5)
                        level.setBlock(pos.offset(i, k, j), BeyondBlocks.GELLID_VOID.get().defaultBlockState(), 2);
                }
            }
        }

        createRoots(level, pos, 0);
        createBranches(level, pos.offset(0, 4 + offset, 0), 0);
    }

    public void createCore(WorldGenLevel level, BlockPos pos, int size){
        for (int i = -size; i <= size; i++) {
            for (int j = -size; j <= size; j++) {
                for (int k = 0; k <= size + 1; k++) {
                    if(!(((k == size) || k == size + 1) && Mth.abs(i) == size && Mth.abs(j) == size))
                        level.setBlock(pos.offset(i, k, j), BeyondBlocks.PEEPING_OBIROOT.get().defaultBlockState(), 2);
                }
            }
        }
        for (int i = -size+1; i <= size-1; i++) {
            for (int j = -size+1; j <= size-1; j++) {
                for (int k = -1; k <= 2 * size + 2; k++) {
                    if(!(k == 2*size+2 && i==0 && j==0))
                        level.setBlock(pos.offset(i, k, j), BeyondBlocks.PEEPING_OBIROOT.get().defaultBlockState(), 2);
                    if((k == 2*size+1 && i==0 && j==0) && level.getRandom().nextInt(10) < 5)
                        level.setBlock(pos.offset(i, k, j), BeyondBlocks.GELLID_VOID.get().defaultBlockState(), 2);
                }
            }
        }

        createRoots(level, pos, size-1);
        createBranches(level, pos.offset(0, 2 * size + 2, 0), size-2);
    }
    public void createRoots(WorldGenLevel level, BlockPos pos, int size){
        size += 2;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (Mth.abs(j)==Mth.abs(i))
                    continue;
                level.setBlock(pos.offset(i * size, 0, j * size), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);
                if (level.getBlockState(pos.offset(i * size, -1, j * size)).isAir())
                    level.setBlock(pos.offset(i * size, -1, j * size), BeyondBlocks.OBIROOT.get().defaultBlockState(), 2);
            }
        }
    }
    public int canStart(WorldGenLevel level, RandomSource random, BlockPos pos) {
        int randomSize = random.nextInt(1, 5);
        int availableHeight = 0;

        for (int i = 0; i <= randomSize + 4; i++) {
            if (level.getBlockState(pos.offset(0, i, 0)).isAir()) {
                availableHeight++;
            } else {
                break;
            }
        }

        for (int i = -randomSize; i <= randomSize; i++) {
            for (int j = -randomSize; j <= randomSize; j++) {
                if (level.getBlockState(pos.offset(i, -1, j)).isAir()) {
                    return -1;
                }
                if (!level.getBlockState(pos.offset(i, 0, j)).isAir()) {
                    return -1;
                }
            }
        }

        if(availableHeight>4)
            availableHeight = random.nextInt(availableHeight - 4);

        return availableHeight;
    }
}
