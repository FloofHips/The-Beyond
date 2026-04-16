package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Dimensional-Tears-aware variant of the auroracite layer.
 *
 * <p>The default {@link AuroraciteLayerFeature} places auroracite in organic patches wherever
 * {@code SimplexNoise(x*0.1, z*0.1) > 0} — that's roughly 50% of the surface, leaving the
 * other half as void gaps. When the Dimensional Tears mod (modid {@code dimensional_tears})
 * is installed, this variant runs <em>instead of</em> the default feature (mutual exclusion
 * is enforced at the {@code neoforge:add_features} biome_modifier level via
 * {@code mod_loaded} / {@code not(mod_loaded)} conditions) and:
 *
 * <ul>
 *   <li>places auroracite in the {@code noise > 0} cells (same 2-layer column as the default),
 *       preserving the island silhouette;</li>
 *   <li>fills the {@code noise ≤ 0} gaps with Dimensional Tears' source fluid block
 *       ({@code dimensional_tears:dimensional_tears}) in a single layer at {@code minY} only,
 *       so the auroracite "islands" stick up 1 block above the fluid surface.</li>
 * </ul>
 *
 * <p>Net visual: auroracite forms organic landmasses that protrude 1 block above the DT fluid
 * channels between them — "islands and rivers" with visible shoreline relief.
 *
 * <h2>Why the block lookup is lazy and cached</h2>
 * The feature class is registered unconditionally (registering a Java feature never touches
 * DT classes) but the DT fluid block is only looked up the first time {@code place()} runs.
 * Since placement only happens when the biome_modifier's {@code mod_loaded} condition
 * matched at datapack load, DT is guaranteed to be installed by the time we hit that lookup.
 * The safety fallback (air state) only matters if someone wires this feature up manually
 * without the condition guard; in that edge case, we silently degrade to the default
 * auroracite behavior (no fluid in the gaps) instead of crashing.
 *
 * <h2>Noise sharing</h2>
 * This feature uses its own static {@link SimplexNoise} instance seeded from the first
 * {@link FeaturePlaceContext}'s random. The default feature has its own independent noise
 * field — only one of the two features ever runs per world (mutual exclusion), so there's
 * no need to share.
 */
public class AuroraciteLayerDTFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceLocation DT_FLUID_ID = ResourceLocation.parse("dimensional_tears:dimensional_tears");

    private static volatile SimplexNoise noise;
    private static volatile BlockState cachedDTFluid;

    public AuroraciteLayerDTFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static SimplexNoise getNoise(RandomSource random) {
        if (noise == null) {
            synchronized (AuroraciteLayerDTFeature.class) {
                if (noise == null) {
                    noise = new SimplexNoise(random);
                }
            }
        }
        return noise;
    }

    /**
     * Returns the noise instance used for auroracite/DT placement, or {@code null} if not yet
     * initialized. Used by {@code BeyondEndChunkGenerator} for post-decoration restoration.
     */
    public static SimplexNoise getNoiseInstance() {
        return noise;
    }

    public static void resetNoise() {
        noise = null;
        cachedDTFluid = null;
    }

    private static BlockState getDTFluidState() {
        BlockState cached = cachedDTFluid;
        if (cached != null) {
            return cached;
        }
        synchronized (AuroraciteLayerDTFeature.class) {
            if (cachedDTFluid == null) {
                Block block = BuiltInRegistries.BLOCK.get(DT_FLUID_ID);
                // If DT is missing (shouldn't happen when gated by the biome_modifier condition),
                // the registry returns minecraft:air — fall back to the air state so we silently
                // degrade to "no fluid in gaps" instead of placing garbage.
                if (block == null || block == Blocks.AIR) {
                    cachedDTFluid = Blocks.AIR.defaultBlockState();
                } else {
                    BlockState state = block.defaultBlockState();
                    // DT's ocean blockstate flag `is_ocean=true` is what DT's native ocean feature
                    // places for stacked fluid columns — skipRendering logic in DimensionalTearsBlock
                    // drops internal face rendering between stacked ocean cells (big perf win for
                    // contiguous fluid surfaces) and marks the block as non-bucketable.
                    // Look up the property dynamically by name to avoid any direct reference to
                    // DT classes — if DT ever removes or renames the property, we silently fall
                    // back to the default (non-ocean) state.
                    Property<?> isOceanProp = block.getStateDefinition().getProperty("is_ocean");
                    if (isOceanProp instanceof BooleanProperty boolProp) {
                        state = state.setValue(boolProp, Boolean.TRUE);
                    }
                    cachedDTFluid = state;
                }
            }
            return cachedDTFluid;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        SimplexNoise simplex = getNoise(random);
        BlockState dtFluid = getDTFluidState();
        boolean hasDTFluid = !dtFluid.isAir();

        int minY = level.getMinBuildHeight();
        int chunkX = origin.getX() & ~15; // align to chunk
        int chunkZ = origin.getZ() & ~15;
        BlockState auroracite = BeyondBlocks.AURORACITE.get().defaultBlockState();
        boolean placed = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX + x;
                int globalZ = chunkZ + z;

                double auroraNoise = simplex.getValue(globalX * 0.1, globalZ * 0.1);
                BlockPos pos0 = new BlockPos(globalX, minY, globalZ);
                BlockPos pos1 = new BlockPos(globalX, minY + 1, globalZ);

                if (auroraNoise > 0.0) {
                    level.setBlock(pos0, auroracite, 2);
                    level.setBlock(pos1, auroracite, 2);
                    placed = true;
                } else if (hasDTFluid) {
                    // Fill the void gaps with DT fluid ONE layer below the auroracite surface.
                    // Auroracite occupies minY and minY+1; DT fluid only occupies minY, so the
                    // auroracite "islands" stick up 1 block above the fluid — per Reda's suggestion.
                    level.setBlock(pos0, dtFluid, 2);
                    placed = true;
                }
            }
        }

        return placed;
    }
}
