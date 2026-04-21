package com.thebeyond.common.worldgen.features;

import com.mojang.serialization.Codec;
import com.thebeyond.TheBeyond;
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
 * Dimensional-Tears-aware variant of the auroracite layer. Places 2-layer auroracite where
 * {@code SimplexNoise(x*0.1, z*0.1) > 0} and fills the remaining cells at {@code minY} with
 * Dimensional Tears' source fluid, producing auroracite islands that protrude one block above
 * the fluid surface. Mutual exclusion with {@link AuroraciteLayerFeature} is enforced via
 * {@code mod_loaded}/{@code not(mod_loaded)} conditions on the biome_modifier.
 *
 * <p>The DT fluid block is looked up lazily and cached. If DT is absent, falls back to air
 * (no fluid in the gaps) so manual wiring without the condition guard degrades gracefully.
 */
public class AuroraciteLayerDTFeature extends Feature<NoneFeatureConfiguration> {

    private static final ResourceLocation DT_FLUID_ID = ResourceLocation.parse("dimensional_tears:dimensional_tears");

    private static volatile SimplexNoise noise;
    private static volatile BlockState cachedDTFluid;

    // Diagnostic: logs the first minY seen per world load to record which dim_type won.
    private static volatile int loggedMinY = Integer.MIN_VALUE;

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

    /** Returns the noise instance, or {@code null} if not yet initialized. */
    public static SimplexNoise getNoiseInstance() {
        return noise;
    }

    public static void resetNoise() {
        noise = null;
        cachedDTFluid = null;
        loggedMinY = Integer.MIN_VALUE;
    }

    private static BlockState getDTFluidState() {
        BlockState cached = cachedDTFluid;
        if (cached != null) {
            return cached;
        }
        synchronized (AuroraciteLayerDTFeature.class) {
            if (cachedDTFluid == null) {
                Block block = BuiltInRegistries.BLOCK.get(DT_FLUID_ID);
                // Missing DT -> registry returns air; fall back to the air state.
                if (block == null || block == Blocks.AIR) {
                    cachedDTFluid = Blocks.AIR.defaultBlockState();
                } else {
                    BlockState state = block.defaultBlockState();
                    // is_ocean=true enables DT's skipRendering optimization for stacked fluid
                    // cells. Looked up by name so a DT rename falls back to the default state.
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
        if (loggedMinY != minY) {
            loggedMinY = minY;
            TheBeyond.LOGGER.info("[AuroraciteLayerDTFeature] placing at minY={} (level.getMinBuildHeight())", minY);
        }
        int chunkX = origin.getX() & ~15; // align to chunk
        int chunkZ = origin.getZ() & ~15;
        BlockState auroracite = BeyondBlocks.AURORACITE.get().defaultBlockState();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int globalX = chunkX + x;
                int globalZ = chunkZ + z;

                double auroraNoise = simplex.getValue(globalX * 0.1, globalZ * 0.1);

                if (auroraNoise > 0.0) {
                    level.setBlock(mutable.set(globalX, minY, globalZ), auroracite, 2);
                    level.setBlock(mutable.set(globalX, minY + 1, globalZ), auroracite, 2);
                    placed = true;
                } else if (hasDTFluid) {
                    level.setBlock(mutable.set(globalX, minY, globalZ), dtFluid, 2);
                    placed = true;
                }
            }
        }

        return placed;
    }
}
