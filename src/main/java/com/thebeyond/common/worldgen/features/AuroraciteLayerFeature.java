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

/**
 * Generates the auroracite floor layer at the bottom of the End dimension. Places 2 layers
 * of auroracite where {@code SimplexNoise(x*0.1, z*0.1) > 0}, matching the behavior of
 * {@code BeyondEndChunkGenerator.generateAuroracite()} and covering roughly half the area
 * in organic patches.
 *
 * <p>Placement Y is {@code level.getMinBuildHeight()}, so the layer tracks whichever
 * dim_type the active pack set declares (Beyond's {@code min_y=0}, Enderscape's
 * {@code min_y=-64}, etc.). The fountain structure is re-anchored via
 * {@code JigsawStructureMixin} so it still lands on the floor regardless.
 */
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
