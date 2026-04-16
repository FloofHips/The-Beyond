package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Generates the auroracite layer at the bottom of the End dimension.
 * Replicates the exact behavior of BeyondEndChunkGenerator.generateAuroracite():
 * - Uses SimplexNoise at scale 0.1 (same as the chunk generator)
 * - Places 2 layers of auroracite (bottom + 1 above) where noise > 0
 * - Covers ~50% of the area in organic patches
 *
 * <p>Placement Y is always {@code level.getMinBuildHeight()}, i.e. whatever the active
 * dimension_type declares:
 * <ul>
 *   <li>Beyond-só: Beyond's subpack declares {@code min_y=0}, so auroracite at Y=0.</li>
 *   <li>Enderscape-só: Enderscape declares {@code min_y=-64}, so auroracite at Y=-64
 *       (the original behavior the user confirmed is ideal for Enderscape's terrain).</li>
 *   <li>Combo with Enderscape: Enderscape's {@code min_y=-64} typically wins the load
 *       order even when Beyond's chunk gen runs, so auroracite falls to Y=-64. The
 *       fountain structure is re-anchored to {@code min_y+2} via
 *       {@code JigsawStructureMixin} so it still sits on the auroracite regardless.</li>
 * </ul>
 */
public class AuroraciteLayerFeature extends Feature<NoneFeatureConfiguration> {

    private static volatile SimplexNoise noise;

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

    /**
     * Returns the noise instance used for auroracite placement, or {@code null} if not yet
     * initialized. Used by {@code BeyondEndChunkGenerator} for post-decoration restoration.
     */
    public static SimplexNoise getNoiseInstance() {
        return noise;
    }

    public static void resetNoise() {
        noise = null;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        SimplexNoise simplex = getNoise(random);

        int minY = level.getMinBuildHeight();
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
