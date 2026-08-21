package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PearlPoolFeature extends Feature<NoneFeatureConfiguration> {
    public PearlPoolFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    Map<BlockPos, Integer> pearlPos = new HashMap<>();
    BlockPos holePos;

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos originPos = context.origin();

        RandomSource source = context.random();
        SimplexNoise noise = new SimplexNoise(source);

        int amount = source.nextInt(1,7);
        boolean attempted = false;
        boolean tried;
        boolean floorOnly = source.nextBoolean();
        for (int i = 0; i < amount; i++) {
            tried = placePool(level, BlockPos.randomInCube(source,1, originPos, 7).iterator().next(), source, noise, floorOnly, originPos);
            if (tried) attempted = tried;
        }

        return attempted;
    }

    private boolean placePool(WorldGenLevel level, BlockPos origin, RandomSource source, SimplexNoise noise, boolean floorOnly, BlockPos base) {

        BlockPos start = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, origin).below();
        if (start.getY() < level.getMinBuildHeight() + 3) return false;

        int radius = source.nextInt(1,4)*2;
        if (radius==2) {
            radius = source.nextBoolean() ? 1 : 2;
        }
        if (radius==4) {
            radius = source.nextBoolean() ? 3 : 5;
        }

        int test = 0;

        createFloor(level, noise, radius, start, base);
        if (!floorOnly) createPool(level, source, radius, test, start);

        return true;
    }

    private boolean createPool(WorldGenLevel level, RandomSource source, int radius, int test, BlockPos start) {
        if (radius == 1) {
            for (int x = -1; x <= 0; x++) {
                for (int z = -1; z <= 0; z++) {
                    test = PlaceColumn(level, source, start, x, z, test);
                }
            }
        } else {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x * x + z * z) <= radius) {
                        test = PlaceColumn(level, source, start, x, z, test);
                    }
                }
            }
        }

        holePos = start.offset((radius + 1) / 2, 0, 0);

        placePearlRim(level, source);
        pearlPos.clear();

        if (radius < 2) return true;

        for (int y = 3; y < 10; y++) {
            BlockPos offset = start.offset((radius + 1) / 2, y, 0);
            if (level.getBlockState(offset).is(BeyondBlocks.PEARL.get())) {
                this.setBlock(level, offset, BeyondBlocks.PEARL.get().defaultBlockState());
            } else {
                this.setBlock(level, offset.offset(0,-2,0), Blocks.AIR.defaultBlockState());
                break;
            }
        }
        return true;
    }

    public static void createFloor(WorldGenLevel level, SimplexNoise noise, int radius, BlockPos start, BlockPos origin) {
        int groundRadius = (int) (radius*1.5f);

        for (int x = -groundRadius; x <= groundRadius; x++) {
            for (int y = -groundRadius; y <= 2; y++) {
                for (int z = -groundRadius; z <= groundRadius; z++) {
                    BlockPos blockPos = start.offset(x, y, z);
                    if (!blockPos.closerToCenterThan(origin.getCenter(), 24)) continue;
                    double distedSqr = blockPos.distSqr(start);
                    double noiseValue = noise.getValue(x * 0.1f, y, z * 0.1f);
                    float noisyRadius = (float) (groundRadius-noiseValue*2);
                    if (distedSqr <= noisyRadius * noisyRadius) {
                        if (level.getBlockState(blockPos.above()).isAir() && level.getBlockState(blockPos).is(BeyondTags.END_DECORATOR_REPLACEABLE)) {
                            if (noiseValue < 0.1f)
                              level.setBlock(blockPos, BeyondBlocks.NACRE.get().defaultBlockState(), 3);
                            else
                                level.setBlock(blockPos, BeyondBlocks.PLATE_BLOCK.get().defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
    }

    private int PlaceColumn(WorldGenLevel level, RandomSource source, BlockPos start, int x, int z, int test) {
        this.setBlock(level, start.offset(x, 3, z), Blocks.AIR.defaultBlockState());
        this.setBlock(level, start.offset(x, 2, z), Blocks.AIR.defaultBlockState());
        this.setBlock(level, start.offset(x, 1, z), Blocks.AIR.defaultBlockState());
        this.setBlock(level, start.offset(x, 0, z), BeyondBlocks.GELLID_VOID.get().defaultBlockState());
        this.setBlock(level, start.offset(x, -1, z), BeyondBlocks.PEARL.get().defaultBlockState());
        this.setBlock(level, start.offset(x, -2, z), Blocks.END_STONE.defaultBlockState());
        this.setBlock(level, start.offset(x, -3, z), Blocks.END_STONE.defaultBlockState());
        for (Direction d : Direction.values()) {
            if (d.getAxis().isVertical()) continue;
            if (!level.getBlockState(start.offset(x, 0, z).relative(d)).is(BeyondBlocks.GELLID_VOID.get()) && !level.getBlockState(start.offset(x, -1, z).relative(d)).is(BeyondBlocks.GELLID_VOID.get())) {
                this.setBlock(level, start.offset(x, 0, z).relative(d), BeyondBlocks.PEARL.get().defaultBlockState());
                pearlPos.put(start.offset(x, 0, z).relative(d), source.nextFloat() > 0.1f ? test : ++test);
                this.setBlock(level, start.offset(x, -1, z).relative(d), Blocks.END_STONE.defaultBlockState());
            }
        }
        return test;
    }

    private void placePearlRim(WorldGenLevel level, RandomSource random) {
        Map<BlockPos, Integer> currentPearlPos = new HashMap<>(pearlPos);

        for (BlockPos pos : currentPearlPos.keySet()) {
            if (level.getBlockState(pos).is(BeyondBlocks.PEARL.get())) {
                int height = currentPearlPos.get(pos) - random.nextInt(0,1);
                for (int y = 0; y <= height; y++) {
                    this.setBlock(level, pos.offset(0, y, 0), BeyondBlocks.PEARL.get().defaultBlockState());
                }
            }
        }
    }
}
