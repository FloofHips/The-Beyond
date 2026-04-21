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

/**
 * Post-decoration safety net that guarantees a full auroracite floor in every End chunk,
 * independent of whether {@link AuroraciteLayerFeature} / {@link AuroraciteLayerDTFeature}
 * have been wired up for the current biome via
 * {@code #the_beyond:has_auroracite_layer} biome_modifier.
 *
 * <h2>Why this mixin exists</h2>
 * The data-driven path (biome_modifier + feature) covers most End biomes but has known
 * blind spots:
 * <ul>
 *   <li><b>Orphan biomes:</b> e.g. {@code unusualend:gloopstone_midlands} is injected via
 *       Unusual End's custom "slice" mechanism and never appears in {@code #minecraft:is_end}
 *       nor any other tag referenced by {@code has_auroracite_layer}.</li>
 *   <li><b>Fabric-via-Sinytra biomes:</b> e.g. {@code phantasm:dreaming_den} and
 *       {@code phantasm:acidburnt_abysses} are listed in {@code #minecraft:is_end} via
 *       Phantasm's datapack, but NeoForge's {@code add_features} biome_modifier does not
 *       propagate to biomes registered through the Sinytra Connector, so the floor
 *       feature does not run for these biomes.</li>
 * </ul>
 * Both cases produce the same symptom — biome-boundary-aligned void holes at
 * {@code minY}. Manually expanding the tag cannot protect against future modded biomes.
 * This mixin closes both gaps by running AFTER all feature decoration in every End chunk
 * and filling any remaining floor gaps idempotently.
 *
 * <h2>Safety guards (layered)</h2>
 * <ol>
 *   <li>Fast exit for non-End chunks (reads only {@code level.getLevel().dimension()}).</li>
 *   <li>Noise is <b>shared</b> with the feature via {@link AuroraciteLayerFeature#getNoiseInstance()}
 *       and {@link AuroraciteLayerDTFeature#getNoiseInstance()} — if either feature has
 *       already initialized a noise field for this world session, we reuse it so the
 *       pattern is visually continuous across biome boundaries where one side gets floor
 *       via the feature and the other via this mixin.</li>
 *   <li>If neither feature has initialized (worst case: biome_modifier didn't reach ANY
 *       biome), we seed from the world seed for deterministic patterns per save.</li>
 *   <li><b>Never overwrite non-air blocks.</b> We only write at positions where the
 *       current block state is air — legitimate terrain, ice, structure blocks, etc.
 *       are preserved. This makes the pass strictly additive.</li>
 *   <li>Targets abstract {@link ChunkGenerator#applyBiomeDecoration} at {@code TAIL}, so
 *       it runs for vanilla, {@code NoiseBasedChunkGenerator}, {@code WoverChunkGenerator},
 *       and {@code BeyondEndChunkGenerator} (the last calls {@code super} first, so we
 *       fire before its exit-portal cleanup which operates at high-y and can't conflict
 *       with the floor layer).</li>
 *   <li>DT detection uses {@link ModList}; if loaded, fills {@code noise <= 0} cells with
 *       Dimensional Tears' source fluid at {@code minY} (mirrors
 *       {@link AuroraciteLayerDTFeature}). If not loaded, leaves those cells as air
 *       (mirrors {@link AuroraciteLayerFeature}).</li>
 * </ol>
 *
 * <h2>Interaction with existing systems</h2>
 * <ul>
 *   <li>{@link AuroraciteLayerProtectionMixin} vetoes ice/snow overwrites at minY via
 *       {@code WorldGenRegion.setBlock}. This mixin writes via
 *       {@link ChunkAccess#setBlockState(BlockPos, BlockState, boolean)}, which does not
 *       go through {@code WorldGenRegion.setBlock}, so there's no interaction. Our own
 *       writes are auroracite/DT-fluid, which are not ice, so even if the paths were
 *       merged there'd be no veto.</li>
 *   <li>{@link BeyondEndChunkGenerator#applyBiomeDecoration} calls {@code super} first
 *       (our inject fires), then does a portal-column cleanup at high-y — no conflict.</li>
 * </ul>
 */
@Mixin(ChunkGenerator.class)
public abstract class AuroraciteLayerFillMixin {

    private static final ResourceLocation DT_FLUID_ID = ResourceLocation.parse("dimensional_tears:dimensional_tears");

    // Cached via double-checked locking; populated lazily the first time the mixin fires.
    private static volatile Boolean dtLoaded;
    private static volatile BlockState cachedDTFluid;
    private static volatile SimplexNoise fallbackNoise;

    // One-shot diagnostic logging the mixin's first fire and which noise source it uses
    // (feature-shared vs fallback). Logs once per JVM session.
    private static final AtomicBoolean LOGGED_FIRST_FIRE = new AtomicBoolean(false);

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("TAIL")
    )
    private void the_beyond$fillAuroraciteFloor(
            WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager,
            CallbackInfo ci) {

        // Gate 1: End only. Early exit for all other dimensions.
        if (level.getLevel().dimension() != Level.END) return;

        // Gate 2: resolve the SimplexNoise. Prefer the feature's instance for visual
        // continuity with biomes where the feature path DID run.
        SimplexNoise noise = resolveNoise(level);
        if (noise == null) return; // should never happen; defensive

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
                    // Two-layer auroracite column at floor.
                    mutable.set(globalX, minY, globalZ);
                    if (chunk.getBlockState(mutable).isAir()) {
                        chunk.setBlockState(mutable, auroracite, false);
                    }
                    mutable.set(globalX, minY + 1, globalZ);
                    if (chunk.getBlockState(mutable).isAir()) {
                        chunk.setBlockState(mutable, auroracite, false);
                    }
                } else if (dtUsable) {
                    // Single-layer DT fluid in the gaps (DT variant only).
                    mutable.set(globalX, minY, globalZ);
                    if (chunk.getBlockState(mutable).isAir()) {
                        chunk.setBlockState(mutable, dtFluid, false);
                    }
                }
                // else: no DT and noise <= 0 -> leave air (matches AuroraciteLayerFeature semantics).
            }
        }
    }

    /**
     * Returns the SimplexNoise to use for this chunk. Preference order:
     * <ol>
     *   <li>{@link AuroraciteLayerDTFeature#getNoiseInstance()} if non-null (DT variant
     *       already initialized by a feature call in some biome this session).</li>
     *   <li>{@link AuroraciteLayerFeature#getNoiseInstance()} if non-null (regular variant
     *       already initialized).</li>
     *   <li>A fallback noise seeded from the world seed, cached per JVM. This path only
     *       activates when NO biome in the current world session has triggered the feature
     *       yet — i.e. biome_modifier reached zero biomes.</li>
     * </ol>
     * Using the feature's noise when possible ensures pattern continuity at biome
     * boundaries: if biome A uses the feature and biome B uses this mixin, both sample
     * the same SimplexNoise so the auroracite landmasses flow seamlessly across the
     * boundary.
     */
    private static SimplexNoise resolveNoise(WorldGenLevel level) {
        SimplexNoise n = AuroraciteLayerDTFeature.getNoiseInstance();
        if (n != null) return n;
        n = AuroraciteLayerFeature.getNoiseInstance();
        if (n != null) return n;

        // Fallback: seed from world seed. Cached per JVM; not reset between worlds in the
        // same session, which is acceptable because the fallback is an emergency fill and
        // continuity across worlds isn't visible.
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
     * Lazily resolves Dimensional Tears' source fluid block state. Mirrors
     * {@link AuroraciteLayerDTFeature#getDTFluidState()}: sets {@code is_ocean=true} on
     * the blockstate if DT still exposes that property (skipRendering perf win for
     * stacked fluid cells). Falls back silently to air if DT is missing or the property
     * was renamed.
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
