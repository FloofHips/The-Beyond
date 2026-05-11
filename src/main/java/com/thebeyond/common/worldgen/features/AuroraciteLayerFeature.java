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

/** 2-layer auroracite floor at {@code level.getMinBuildHeight()}, gated by
 *  {@code SimplexNoise(x*0.1, z*0.1) > 0} for organic patches (~50% coverage). Y tracks
 *  the active dim_type's min_y so the layer follows combo-mode floors. */
public class AuroraciteLayerFeature extends Feature<NoneFeatureConfiguration> {

    private static volatile SimplexNoise noise;

    // Diagnostic: logs the first minY seen per world load to record which dim_type won.
    // Integer.MIN_VALUE is a safe sentinel (-128 < minY < 320 by worldgen convention).
    private static volatile int loggedMinY = Integer.MIN_VALUE;

    public AuroraciteLayerFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static SimplexNoise getNoise(RandomSource random) {
        if (noise == null) {
            synchronized (AuroraciteLayerFeature.class) {
                if (noise == null) {
                    noise = new SimplexNoise(random);
                }
            }
        }
        return noise;
    }

    /** Returns the noise instance, or {@code null} if not yet initialized. */
    public static SimplexNoise getNoiseInstance() {
        return noise;
    }

    public static void resetNoise() {
        noise = null;
        loggedMinY = Integer.MIN_VALUE;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        SimplexNoise simplex = getNoise(random);

        int minY = level.getMinBuildHeight();
        if (loggedMinY != minY) {
            loggedMinY = minY;
            TheBeyond.LOGGER.info("[AuroraciteLayerFeature] placing at minY={} (level.getMinBuildHeight())", minY);
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
