package com.thebeyond.mixin;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.worldgen.features.AuroraciteLayerDTFeature;
import com.thebeyond.common.worldgen.features.AuroraciteLayerFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/** Safety-net auroracite floor for End chunks that bypass Beyond's chunkgen.
 *  Tail-injected so it fires under any generator. */
@Mixin(ChunkGenerator.class)
public abstract class AuroraciteLayerFillMixin {

    private static final ResourceLocation DT_FLUID_ID = ResourceLocation.parse("dimensional_tears:dimensional_tears");

    // Lazily populated on first fire.
    private static volatile Boolean dtLoaded;
    private static volatile BlockState cachedDTFluid;
    private static volatile SimplexNoise fallbackNoise;

    /** Logs the first fire per JVM with the chosen noise source. */
    private static final AtomicBoolean LOGGED_FIRST_FIRE = new AtomicBoolean(false);

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("TAIL")
    )
    private void the_beyond$fillAuroraciteFloor(
            WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
            CallbackInfo ci) {

        // End only.
        if (level.getLevel().dimension() != Level.END) return;

        SimplexNoise noise = resolveNoise(level);
        if (noise == null) return; // defensive; resolveNoise always returns non-null

        final int minY = level.getMinBuildHeight();
        final int chunkX = chunk.getPos().getMinBlockX();
        final int chunkZ = chunk.getPos().getMinBlockZ();

        final BlockState auroracite = BeyondBlocks.AURORACITE.get().defaultBlockState();
        final boolean hasDT = isDTLoaded();
        final BlockState dtFluid = hasDT ? getDTFluidState() : Blocks.AIR.defaultBlockState();
        final boolean dtUsable = hasDT && !dtFluid.isAir();

        if (LOGGED_FIRST_FIRE.compareAndSet(false, true)) {
            TheBeyond.LOGGER.info(
                    "[AuroraciteLayerFillMixin] first fire: minY={}, dtLoaded={}, dtFluidAir={}, noiseSource={}",
                    minY, hasDT, dtFluid.isAir(),
                    (AuroraciteLayerDTFeature.getNoiseInstance() != null ? "DT-feature"
                            : AuroraciteLayerFeature.getNoiseInstance() != null ? "regular-feature"
                            : "fallback"));
        }

        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                final int globalX = chunkX + x;
                final int globalZ = chunkZ + z;
                final double n = noise.getValue(globalX * 0.1, globalZ * 0.1);

                if (n > 0.0) {
                    // Two-layer auroracite column at the floor; overwrites foreign blocks
                    // (auroracite is the End floor's ground truth). Idempotent: skip if already set.
                    mutable.set(globalX, minY, globalZ);
                    if (!chunk.getBlockState(mutable).is(BeyondBlocks.AURORACITE.get())) {
                        chunk.setBlockState(mutable, auroracite, false);
                    }
                    mutable.set(globalX, minY + 1, globalZ);
                    if (!chunk.getBlockState(mutable).is(BeyondBlocks.AURORACITE.get())) {
                        chunk.setBlockState(mutable, auroracite, false);
                    }
                    // Clear foreign vegetation that landed on the column via MOTION_BLOCKING
                    // heightmap features; surface plants belong on islands, not the void floor.
                    // Air check avoids erasing legitimate minY+2 placements.
                    mutable.set(globalX, minY + 2, globalZ);
                    BlockState aboveFloor = chunk.getBlockState(mutable);
                    if (!aboveFloor.isAir()) {
                        chunk.setBlockState(mutable, Blocks.AIR.defaultBlockState(), false);
                    }
                } else if (dtUsable) {
                    // DT fluid fills the gaps; overwrite foreign blocks, skip if already
                    // auroracite or the same DT fluid.
                    mutable.set(globalX, minY, globalZ);
                    BlockState existing = chunk.getBlockState(mutable);
                    if (!existing.is(BeyondBlocks.AURORACITE.get()) && existing.getBlock() != dtFluid.getBlock()) {
                        chunk.setBlockState(mutable, dtFluid, false);
                    }
                }
                // No DT and noise <= 0: leave the existing block.
            }
        }
    }

    /**
     * Prefers either feature's live noise for cross-biome continuity; falls back to a
     * JVM-cached, world-seeded noise when neither feature has run yet.
     */
    private static SimplexNoise resolveNoise(WorldGenLevel level) {
        SimplexNoise n = AuroraciteLayerDTFeature.getNoiseInstance();
        if (n != null) return n;
        n = AuroraciteLayerFeature.getNoiseInstance();
        if (n != null) return n;

        SimplexNoise cached = fallbackNoise;
        if (cached != null) return cached;
        synchronized (AuroraciteLayerFillMixin.class) {
            if (fallbackNoise == null) {
                long seed = level.getLevel().getSeed();
                fallbackNoise = new SimplexNoise(RandomSource.create(seed));
            }
            return fallbackNoise;
        }
    }

    private static boolean isDTLoaded() {
        Boolean cached = dtLoaded;
        if (cached != null) return cached;
        synchronized (AuroraciteLayerFillMixin.class) {
            if (dtLoaded == null) {
                ModList list = ModList.get();
                dtLoaded = list != null && list.isLoaded("dimensional_tears");
            }
            return dtLoaded;
        }
    }

    /**
     * Returns DT's source fluid with {@code is_ocean=true} when that property exists
     * (enables DT's skipRendering optimisation). Air if DT is absent or the property moved.
     */
    private static BlockState getDTFluidState() {
        BlockState cached = cachedDTFluid;
        if (cached != null) return cached;
        synchronized (AuroraciteLayerFillMixin.class) {
            if (cachedDTFluid == null) {
                Block block = BuiltInRegistries.BLOCK.get(DT_FLUID_ID);
                if (block == null || block == Blocks.AIR) {
                    cachedDTFluid = Blocks.AIR.defaultBlockState();
                } else {
                    BlockState state = block.defaultBlockState();
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
}
