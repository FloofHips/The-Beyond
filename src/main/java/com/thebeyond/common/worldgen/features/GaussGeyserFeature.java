package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

public class GaussGeyserFeature extends Feature<NoneFeatureConfiguration> {
    public GaussGeyserFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource source = context.random();
        SimplexNoise noise = new SimplexNoise(source);

        int size = getSize(level, source, origin);
        if (!canPlace(size)) return false;

        int radius = Math.max(2, size + source.nextInt(1, 2));
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos blockPos = origin.offset(x, y, z);
                    if (blockPos.distSqr(origin) <= radius * radius) {
                        if (level.getBlockState(blockPos).isSolid() && noise.getValue(x*0.1f, y, z*0.1f) < 0f) {
                            level.setBlock(blockPos, BeyondBlocks.PORTELAIN.get().defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        if (size==0) build(level, source, size, origin);
        else generateSection(level, origin, size, size-2, size+source.nextInt(5, 10),true, source);



        return true;
    }

    public void build(WorldGenLevel level, RandomSource random, int size, BlockPos pos) {
        int height = size + random.nextInt(5, 10);
        for (int y = 0; y <= height; y++) {

            BlockState state = Blocks.END_STONE.defaultBlockState();
            if (y >= (height/3)*2 || y == height/2 || y == height/3)
                state = BeyondBlocks.PORTELAIN.get().defaultBlockState();

            level.setBlock(pos.offset(0, y, 0), state, 2);
        }
        setVent(level, pos.offset(0, height, 0));
    }

    private void generateSection(WorldGenLevel level, BlockPos basePos, int baseDiam, int topDiam, int height, boolean upward, RandomSource random) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int xBias = random.nextBoolean() ? 1 : -1;
        int zBias = random.nextBoolean() ? 1 : -1;

        for (int y = 0; y < height; y++) {
            float progress = ((float)y / height)*1.5F;
            int currentDiam = (int) Mth.lerp(progress, baseDiam, topDiam);
            int radius = currentDiam / 2;

            int yPos = upward ? basePos.getY() + y : basePos.getY() - y;

            BlockState state = Blocks.END_STONE.defaultBlockState();
            if (y >= (height/3)*2 || y == height/2 || y == height/3)
                state = BeyondBlocks.PORTELAIN.get().defaultBlockState();

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {

                    if (radius > 1 && Mth.abs(z) == Mth.abs(x) && Mth.abs(x) == radius) continue;

                    mutablePos.set(basePos.getX() + x, yPos, basePos.getZ() + z);
                    level.setBlock(mutablePos, state, 2);
                    if (radius==0) {
                        level.setBlock(mutablePos.offset(0,0, zBias), state, 2);
                        level.setBlock(mutablePos.offset(xBias,0,0), state, 2);
                        level.setBlock(mutablePos.offset(xBias,0, zBias), state, 2);
                    }
                }
            }
        }

        setVent(level, basePos.offset(0, height-1, 0));
    }

    public void setVent(WorldGenLevel level, BlockPos pos) {
        level.setBlock(pos, BeyondBlocks.GUSTER.get().defaultBlockState(), 2);
        for (Direction d : Direction.values()) {
            if (d.getAxis().isVertical()) continue;
            if (level.getBlockState(pos.offset(d.getStepX(), -1, d.getStepZ())).isAir()) return;
        }
        level.setBlock(pos.offset(0,-1,0), BeyondBlocks.GELLID_VOID.get().defaultBlockState(), 2);
    }

    public int getSize(WorldGenLevel level, RandomSource random, BlockPos pos) {
        int randomSize = random.nextInt(1, 6);
        int maxHeight = 0;
        int requiredBaseHeight = 1;

        for (int i = -randomSize; i <= randomSize; i++) {
            for (int j = -randomSize; j <= randomSize; j++) {
                //if (level.getBlockState(pos.offset(i, -1, j)).isAir()) {
                //    return -1;
                //}
                if (!level.getBlockState(pos.offset(i, 0, j)).isAir()) {
                    return -1;
                }
            }
        }

        for (int i = 1; i <= randomSize + 8; i++) {
            if (level.getBlockState(pos.offset(0, i, 0)).isAir()) {
                maxHeight++;
            } else {
                break;
            }
        }

        if (maxHeight < requiredBaseHeight) {
            return -1;
        }

        return Math.min(random.nextInt(0, maxHeight + 1), random.nextInt(0, maxHeight + 1));
    }

    public boolean canPlace(int size) {
        return size != -1;
    }
}
