package com.thebeyond.common.worldgen;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import com.google.common.base.Suppliers;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom End biome source for The Beyond.
 *
 * The dimension JSON lists biomes from ALL supported End mods. At load time,
 * biomes from mods that aren't installed are silently filtered out during
 * codec decode. This avoids ClassCastException during world save.
 *
 * Uses ResourceLocation-based codec instead of RegistryCodecs.homogeneousList()
 * to gracefully handle missing biome entries.
 */
public class BeyondEndBiomeSource extends BiomeSource {

    /**
     * A lenient biome list codec that:
     * - Decode: reads ResourceLocation strings, resolves each against the biome registry,
     *   silently drops biomes that don't exist (from mods not installed)
     * - Encode: extracts ResourceLocation strings from bound holders
     */
    private static final Codec<List<Holder<Biome>>> LENIENT_BIOME_LIST = new Codec<>() {
        @Override
        public <T> DataResult<Pair<List<Holder<Biome>>, T>> decode(DynamicOps<T> ops, T input) {
            return ResourceLocation.CODEC.listOf().decode(ops, input).map(pair -> {
                List<ResourceLocation> ids = pair.getFirst();
                List<Holder<Biome>> resolved = new ArrayList<>();

                if (ops instanceof RegistryOps<T> registryOps) {
                    Optional<HolderGetter<Biome>> getter = registryOps.getter(Registries.BIOME);
                    if (getter.isPresent()) {
                        HolderGetter<Biome> biomeGetter = getter.get();
                        for (ResourceLocation id : ids) {
                            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                            Optional<Holder.Reference<Biome>> holder = biomeGetter.get(key);
                            if (holder.isPresent()) {
                                resolved.add(holder.get());
                            } else {
                                TheBeyond.LOGGER.debug("[TheBeyond] Biome not found, skipping: {}", id);
                            }
                        }
                    }
                }

                if (resolved.isEmpty()) {
                    TheBeyond.LOGGER.warn("[TheBeyond] No biomes resolved from list of {} entries", ids.size());
                }

                return Pair.of(List.copyOf(resolved), pair.getSecond());
            });
        }

        @Override
        public <T> DataResult<T> encode(List<Holder<Biome>> input, DynamicOps<T> ops, T prefix) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (Holder<Biome> holder : input) {
                holder.unwrapKey().ifPresent(key -> ids.add(key.location()));
            }
            return ResourceLocation.CODEC.listOf().encode(ids, ops, prefix);
        }
    };

    /**
     * A lenient single biome codec (for center_biome and bottom_biome).
     * Falls back to minecraft:the_end if the specified biome doesn't exist.
     */
    private static final Codec<Holder<Biome>> LENIENT_BIOME = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Holder<Biome>, T>> decode(DynamicOps<T> ops, T input) {
            return ResourceLocation.CODEC.decode(ops, input).flatMap(pair -> {
                ResourceLocation id = pair.getFirst();
                if (ops instanceof RegistryOps<T> registryOps) {
                    Optional<HolderGetter<Biome>> getter = registryOps.getter(Registries.BIOME);
                    if (getter.isPresent()) {
                        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
                        Optional<Holder.Reference<Biome>> holder = getter.get().get(key);
                        if (holder.isPresent()) {
                            return DataResult.success(Pair.of(holder.get(), pair.getSecond()));
                        }
                        // Fallback to the_end
                        TheBeyond.LOGGER.warn("[TheBeyond] Biome {} not found, falling back to minecraft:the_end", id);
                        Optional<Holder.Reference<Biome>> fallback = getter.get().get(
                                ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("the_end")));
                        if (fallback.isPresent()) {
                            return DataResult.success(Pair.of(fallback.get(), pair.getSecond()));
                        }
                    }
                }
                return DataResult.error(() -> "Could not resolve biome: " + id);
            });
        }

        @Override
        public <T> DataResult<T> encode(Holder<Biome> input, DynamicOps<T> ops, T prefix) {
            Optional<ResourceKey<Biome>> key = input.unwrapKey();
            if (key.isPresent()) {
                return ResourceLocation.CODEC.encode(key.get().location(), ops, prefix);
            }
            return DataResult.error(() -> "Unbound biome holder");
        }
    };

    /**
     * Codec schema:
     *   tainted_end_biomes  (required) — main biome pool for the corrupted End region. Foreign mod
     *                                    biomes (UnusualEnd, Phantasm, Enderscape, etc.) live here.
     *   farlands_biomes     (optional) — biome pool for the Farlands region (north of farlands_z_boundary).
     *                                    Defaults to empty. Foreign mod biomes should NOT go here.
     *   farlands_z_boundary (optional) — z block coordinate at which the Farlands begin (negative = north).
     *                                    No default. The duo-region gate is active only when both this
     *                                    and a non-empty farlands_biomes list are present.
     *   outer_void_biomes   (required) — void biomes used in the outer void ring
     *   inner_void_biomes   (required) — void biomes used in the inner void ring
     *   center_biome        (required) — single biome for the central island
     *   bottom_biome        (required) — single biome below y=20
     */
    public static final MapCodec<BeyondEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LENIENT_BIOME_LIST.fieldOf("tainted_end_biomes").forGetter(source -> source.taintedEndBiomeList),
                    LENIENT_BIOME_LIST.optionalFieldOf("farlands_biomes", List.of()).forGetter(source -> source.farlandsBiomeList),
                    Codec.INT.optionalFieldOf("farlands_z_boundary").forGetter(source -> source.farlandsZBoundary),
                    LENIENT_BIOME_LIST.fieldOf("outer_void_biomes").forGetter(source -> source.outerVoidBiomeList),
                    LENIENT_BIOME_LIST.fieldOf("inner_void_biomes").forGetter(source -> source.innerVoidBiomeList),
                    LENIENT_BIOME.fieldOf("center_biome").forGetter(source -> source.centerBiome),
                    LENIENT_BIOME.fieldOf("bottom_biome").forGetter(source -> source.bottomBiome),
                    // Optional — datapacks that don't specify this get the calibrated
                    // DEFAULTS. Conceptually the field lives on the biome source for
                    // the same reason farlands_z_boundary does: both are generation
                    // knobs the biome source already owns and passes to the chunk
                    // generator via static state.
                    BeyondTerrainParams.FULL_CODEC.optionalFieldOf("terrain_params", BeyondTerrainParams.DEFAULTS)
                            .forGetter(source -> source.terrainParams)
            ).apply(instance, BeyondEndBiomeSource::new)
    );

    /**
     * Reference value for the Farlands z boundary as described in the design doc ("thousands of blocks
     * north of 0,0,0"). NOT used by generation logic — datapacks must opt in by setting
     * `farlands_z_boundary` explicitly. Exposed only as documentation for whoever writes the dimension JSON.
     */
    public static final int DEFAULT_FARLANDS_Z_BOUNDARY = -10000;

    // Mutable: auto-discovery (EndBiomeDiscovery) may inject additional biomes at server start.
    private final List<Holder<Biome>> taintedEndBiomeList;
    private final List<Holder<Biome>> farlandsBiomeList;
    private final Optional<Integer> farlandsZBoundary;
    private final List<Holder<Biome>> outerVoidBiomeList;
    private final List<Holder<Biome>> innerVoidBiomeList;
    private final Holder<Biome> centerBiome;
    private final Holder<Biome> bottomBiome;
    /** Retained for round-trip codec encoding. The chunk generator reads the effective
     *  values from {@link BeyondEndChunkGenerator#activeTerrainParams}, which this
     *  constructor writes into. Keeping the field here means a save→reload round-trip
     *  serializes back the same JSON the author wrote. */
    private final BeyondTerrainParams terrainParams;
    private Set<Holder<Biome>> allBiomes;
    private final boolean farlandsGateActive;

    /**
     * Per-thread column cache: memoizes the expensive y-invariant biome
     * computation (two Perlin lookups + one Simplex lookup) across the ~80
     * y-samples Mojang issues per (quartX, quartZ). ThreadLocal because chunk
     * generation is multi-threaded.
     */
    private static final class ColumnCache {
        /** Sentinel value meaning "no column cached yet". */
        int blockX = Integer.MIN_VALUE;
        int blockZ = Integer.MIN_VALUE;
        /** {@code absSeed} — incorporates biomeNoise × threshold × 1M plus biome-grid hash. */
        long absSeed;
    }

    private final ThreadLocal<ColumnCache> columnCacheTL = ThreadLocal.withInitial(ColumnCache::new);

    /**
     * Baseline pool size the default {@code 0.02} frequency is calibrated for
     * (~Beyond native + vanilla End). Larger pools (mod combos push tainted past
     * 50) lose biome coherence because {@code absSeed % poolSize} jumps too far
     * per step — {@link #getBiomeNoiseScale()} widens the simplex patches to
     * compensate.
     */
    private static final int BASELINE_POOL_SIZE = 12;

    /**
     * Cached per-instance biome noise frequency. Invalidated to {@code -1} on
     * {@link #injectBiomesIntoTaintedPool} since pool growth changes the computed value.
     * Volatile because inject happens on the server-start thread and reads happen on
     * chunk-gen worker threads.
     */
    private volatile double cachedBiomeNoiseScale = -1.0;

    /**
     * Simplex frequency for biome selection, scaled by {@code sqrt(BASELINE / poolSize)}
     * so patch area grows with pool size (biome patches are 2D — linear scaling would
     * over-correct). Clamped to {@code [0.35 * 0.02, 1.0 * 0.02]}.
     */
    private double getBiomeNoiseScale() {
        double cached = cachedBiomeNoiseScale;
        if (cached > 0) return cached;
        // Recompute. Only the tainted pool is scaled against — inner/outer void pools are
        // typically small and hand-curated in the dimension JSON, they don't dynamically grow.
        // If the tainted pool shrank below baseline (custom datapacks), keep the default 0.02.
        int poolSize = Math.max(taintedEndBiomeList.size(), 1);
        double factor;
        if (poolSize <= BASELINE_POOL_SIZE) {
            factor = 1.0;
        } else {
            factor = Math.sqrt((double) BASELINE_POOL_SIZE / poolSize);
            // Clamp the lower bound so continents don't get absurd at pool sizes >100.
            if (factor < 0.35) factor = 0.35;
        }
        double scale = 0.02 * factor;
        cachedBiomeNoiseScale = scale;
        return scale;
    }

    public BeyondEndBiomeSource(List<Holder<Biome>> taintedEndBiomes,
                                List<Holder<Biome>> farlandsBiomes,
                                Optional<Integer> farlandsZBoundary,
                                List<Holder<Biome>> outerVoidBiomes,
                                List<Holder<Biome>> innerVoidBiomes,
                                Holder<Biome> centerBiome, Holder<Biome> bottomBiome,
                                BeyondTerrainParams terrainParams) {
        super();
        this.taintedEndBiomeList = new ArrayList<>(taintedEndBiomes);
        this.farlandsBiomeList = farlandsBiomes;
        this.farlandsZBoundary = farlandsZBoundary;
        this.outerVoidBiomeList = outerVoidBiomes;
        this.innerVoidBiomeList = innerVoidBiomes;
        this.centerBiome = centerBiome;
        this.bottomBiome = bottomBiome;
        this.terrainParams = terrainParams;

        // Gate is active only when BOTH a boundary and a non-empty Farlands pool are provided.
        // This makes the schema scaffolding-friendly: omit either field and the duo-region is silently
        // disabled, falling back to a single-pool (Tainted End only) biome layout.
        this.farlandsGateActive = farlandsZBoundary.isPresent() && !farlandsBiomeList.isEmpty();

        this.allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(farlandsBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        // Publish the datapack-configured terrain params to the chunk generator.
        // Static mutable state is the established communication channel between
        // these two objects (see the volatile noise fields). Done here rather than
        // lazily so a misconfigured value trips the IllegalArgumentException at
        // world-load time, not hundreds of chunks in.
        BeyondEndChunkGenerator.activeTerrainParams = terrainParams;

        if (farlandsGateActive) {
            TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} tainted, {} farlands (gate at z<{}), {} innerVoid, {} outerVoid biomes",
                    taintedEndBiomeList.size(), farlandsBiomeList.size(), farlandsZBoundary.get(),
                    innerVoidBiomeList.size(), outerVoidBiomeList.size());
        } else {
            TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} tainted (Farlands gate inactive), {} innerVoid, {} outerVoid biomes",
                    taintedEndBiomeList.size(), innerVoidBiomeList.size(), outerVoidBiomeList.size());
        }
        // Log the terrain params at startup so debugging an unexpected generator
        // behavior (mirrored world, extra-wavy features) has a breadcrumb.
        if (!terrainParams.equals(BeyondTerrainParams.DEFAULTS)) {
            TheBeyond.LOGGER.info("[TheBeyond] Custom terrain_params: wrap_range={} warp_amplitude={} warp_scale={}",
                    terrainParams.wrapRange(), terrainParams.warpAmplitude(), terrainParams.warpScale());
        }
        // Biome-noise frequency breadcrumb — helps correlate "striped mosaic" complaints
        // with runtime pool size. Scale == 0.02 in the default 12-biome calibration.
        TheBeyond.LOGGER.info("[TheBeyond] Biome noise scale (patch frequency): {} (pool-size-adjusted from baseline 0.02; tainted pool size {})",
                getBiomeNoiseScale(), taintedEndBiomeList.size());

        // NOTE: BeyondTerrainState.active is NOT set here. Constructor side-effects leak:
        // CreateWorldScreen.openFresh decodes WorldDataConfiguration.DEFAULT which auto-adds
        // beyond_terrain and runs this constructor — if the pack is then deselected in
        // the UI before clicking Create, the flag would remain true even though the real
        // server uses vanilla TheEndBiomeSource. The flag is derived authoritatively in
        // ServerWorldEvents.onServerAboutToStart from the server's LEVEL_STEM registry.
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return allBiomes.stream();
    }

    /**
     * Injects externally-discovered biomes into the tainted End pool at runtime.
     * Called by {@link com.thebeyond.common.worldgen.compat.EndBiomeDiscovery} during
     * {@code ServerAboutToStartEvent}, before any chunk generation occurs.
     *
     * <p>Biomes already present in any pool (tainted, farlands, void, center, bottom)
     * are silently skipped — no duplicates are created.</p>
     *
     * @param biomes the biomes to inject; only those not already present are added
     * @return the number of biomes actually injected
     */
    public int injectBiomesIntoTaintedPool(Collection<Holder<Biome>> biomes) {
        Set<ResourceKey<Biome>> existingKeys = allBiomes.stream()
                .flatMap(h -> h.unwrapKey().stream())
                .collect(Collectors.toSet());

        List<Holder<Biome>> toAdd = biomes.stream()
                .filter(h -> h.unwrapKey().map(k -> !existingKeys.contains(k)).orElse(false))
                .toList();

        if (toAdd.isEmpty()) return 0;

        taintedEndBiomeList.addAll(toAdd);

        // Invalidate the biome-noise frequency cache: pool size just changed, so
        // getBiomeNoiseScale() must recompute. Next getNoiseBiome call reseeds the cache.
        cachedBiomeNoiseScale = -1.0;

        // Rebuild the combined set and update the memoized possibleBiomes supplier
        // so /locate and structure placement see the new biomes.
        allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(farlandsBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        Set<Holder<Biome>> finalBiomes = allBiomes;
        this.possibleBiomes = Suppliers.memoize(() -> finalBiomes);

        return toAdd.size();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        // distanceFromO is cheap (one sqrt). Compute always; don't cache.
        float distanceFromO = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);

        // centerBiome FIRST — applyBiomeDecoration runs BiomeFilter at sectionPos.origin()
        // (y=min_y). END_SPIKE is registered only in minecraft:the_end; if bottomBiome wins
        // here, the filter rejects every spike in the ring (radius ~42, all inside this disk)
        // and the central island generates without pillars.
        if (distanceFromO <= 116)
            return centerBiome;

        // bottom_biome (the_paths): restrict to the auroracite row + a small buffer.
        // dimMinY comes from BeyondTerrainState so the band follows the dim floor —
        // [0,3] in Beyond-só, [-64,-61] with beyond_enderscape_bounds.
        int dimMinY = BeyondTerrainState.getDimMinY();
        if (blockY < dimMinY + 4)
            return bottomBiome;

        // ----- Column cache for EXPENSIVE invariants -----
        // absSeed requires two Perlin lookups + one Simplex lookup, all of which depend on
        // (blockX, blockZ) only — not y. Cache it per column.
        ColumnCache cache = columnCacheTL.get();
        final long absSeed;
        if (cache.blockX == blockX && cache.blockZ == blockZ) {
            absSeed = cache.absSeed;
        } else {
            int biomeX = blockX / 64;
            int biomeZ = blockZ / 64;
            // NOTE: this distance mixes units — blockX (block-space) with biomeZ (biome-space,
            // = blockZ/64). This looks like a bug but is load-bearing: the threshold curve
            // downstream is tuned against this exact asymmetric magnitude. Do NOT "fix" to
            // blockZ without recalibrating the threshold function, or biome selection at the
            // center/edges will shift.
            float distanceFromOrigin = (float) Math.sqrt((double) blockX * blockX + (double) biomeZ * biomeZ);

            // Intentionally NOT wrapped: these feed the absSeed biome-hash, not a
            // density sampler, so the chunk-gen wrap isn't needed — and wrapping
            // here would inject a 2*wrapRange periodic repetition into biome layout.
            // See BeyondEndChunkGenerator's helper contract block for derivation.
            double horizontalScale = BeyondEndChunkGenerator.getHorizontalBaseScale(biomeX, biomeZ);
            double threshold = BeyondEndChunkGenerator.getThreshold(biomeX, biomeZ, distanceFromOrigin);

            // Dedicated biome simplex field (separate from terrain simplex) so
            // terrain retuning can't silently reshuffle biome placement.
            HashSimplexNoise biomeNoiseField = BeyondEndChunkGenerator.biomeSimplexNoise;
            if (biomeNoiseField == null) {
                return centerBiome;
            }
            double biomeFreq = getBiomeNoiseScale();
            double biomeNoise = biomeNoiseField.getValue(
                    biomeX * horizontalScale * biomeFreq,
                    biomeZ * horizontalScale * biomeFreq
            );

            long seed = (long) (biomeNoise * threshold * 1000000) + biomeX * 31L + biomeZ * 961L;
            absSeed = Math.abs(seed);

            // Store for future y-samples of the same column.
            cache.blockX = blockX;
            cache.blockZ = blockZ;
            cache.absSeed = absSeed;
        }
        // ----- End column cache -----

        // Pick directly from innerVoidBiomes without sampling
        // terrain density. getTerrainDensity() is the most expensive call in this method
        // (4-octave 3D simplex + 3 Perlin scale/cycle lookups), so skipping it for every
        // point inside the 690-block radius halves per-call cost across a huge portion of
        // each End chunk column.
        if (distanceFromO <= 690) {
            if (innerVoidBiomeList.isEmpty()) return centerBiome;
            int inner_void_index = (int) (absSeed % innerVoidBiomeList.size());
            return innerVoidBiomeList.get(inner_void_index);
        }

        // Outer region: terrain density decides whether this point lands in a void biome
        // (empty air column) or in a solid end biome. This call DOES depend on y so it
        // cannot be hoisted into the column cache.
        boolean isVoid = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        if (isVoid) {
            if (outerVoidBiomeList.isEmpty()) return centerBiome;
            int outer_void_index = (int) (absSeed % outerVoidBiomeList.size());
            return outerVoidBiomeList.get(outer_void_index);
        }

        // Pick the solid-land pool: Farlands when north of the boundary AND the gate is active,
        // Tainted End everywhere else. Gate stays inactive (and Farlands pool is empty) until the
        // dimension JSON populates `farlands_biomes` and `farlands_z_boundary`.
        List<Holder<Biome>> solidPool = (farlandsGateActive && blockZ < farlandsZBoundary.get())
                ? farlandsBiomeList
                : taintedEndBiomeList;

        if (solidPool.isEmpty()) return centerBiome;
        int solid_index = (int) (absSeed % solidPool.size());
        return solidPool.get(solid_index);
    }
}
