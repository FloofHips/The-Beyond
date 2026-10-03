package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Two-layer auroracite floor at the dimension's min_y, in noise patches covering about half. */
public class AuroraciteLayerFeature extends Feature<NoneFeatureConfiguration> {

    private record SeededNoise(long seed, SimplexNoise noise) {}

    private static volatile SeededNoise floorNoise;

    // logs the first minY of each world load, to show which dimension type won
    private static volatile int loggedMinY = Integer.MIN_VALUE;

    public AuroraciteLayerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** One floor pattern for both features and the fill, from the world seed alone so chunk order never changes it. */
    public static SimplexNoise noiseFor(long seed) {
        SeededNoise n = floorNoise;
        if (n != null && n.seed() == seed) return n.noise();
        synchronized (AuroraciteLayerFeature.class) {
            n = floorNoise;
            if (n == null || n.seed() != seed) {
                n = new SeededNoise(seed, new SimplexNoise(RandomSource.create(seed)));
                floorNoise = n;
                TheBeyond.LOGGER.debug("[AuroraciteLayerFeature] floor noise built from world seed {}", seed);
            }
            return n.noise();
        }
    }

    public static void resetNoise() {
        floorNoise = null;
        loggedMinY = Integer.MIN_VALUE;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        SimplexNoise simplex = noiseFor(level.getSeed());

        int minY = level.getMinBuildHeight();
        if (loggedMinY != minY) {
            loggedMinY = minY;
            TheBeyond.LOGGER.debug("[AuroraciteLayerFeature] placing at minY={}", minY);
        }
        int chunkX = origin.getX() & ~15; // align to chunk
        int chunkZ = origin.getZ() & ~15;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX + x;
                int globalZ = chunkZ + z;

                double auroraNoise = simplex.getValue(globalX * 0.1, globalZ * 0.1);
                if (auroraNoise > 0.0) {
                    level.setBlock(mutable.set(globalX, minY, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), 2);
                    level.setBlock(mutable.set(globalX, minY + 1, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), 2);
                    placed = true;
                }
            }
        }

        return placed;
    }
}
