package com.thebeyond.common.worldgen;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.compat.WarpedReefMacroRegion;
import com.thebeyond.util.HashSimplexNoise;
import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import com.google.common.base.Suppliers;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Custom End biome source. The dimension JSON lists biomes from all supported End mods;
 *  unknown entries are silently dropped at decode (the codec is ResourceLocation-based
 *  instead of {@code RegistryCodecs.homogeneousList} to avoid ClassCastException on save). */
public class BeyondEndBiomeSource extends BiomeSource {
    private Supplier<VoronoiNoise> voronoiNoise;
    /** Decode: resolve ResourceLocation strings against the biome registry, drop missing.
     *  Encode: extract ResourceLocation strings from bound holders. */
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

    /** Single-biome codec for center/bottom; falls back to {@code minecraft:the_end} on miss. */
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

    /** Pools and a few singletons: tainted/farlands/outer/inner/center/bottom plus optional
     *  {@code terrain_params}. Farlands duo-region active only when both pool and z-boundary set. */
    public static final MapCodec<BeyondEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LENIENT_BIOME_LIST.fieldOf("tainted_end_biomes").forGetter(source -> source.taintedEndBiomeList),
                    LENIENT_BIOME_LIST.optionalFieldOf("farlands_biomes", List.of()).forGetter(source -> source.farlandsBiomeList),
                    Codec.INT.optionalFieldOf("farlands_z_boundary").forGetter(source -> source.farlandsZBoundary),
                    LENIENT_BIOME_LIST.fieldOf("outer_void_biomes").forGetter(source -> source.outerVoidBiomeList),
                    LENIENT_BIOME_LIST.fieldOf("inner_void_biomes").forGetter(source -> source.innerVoidBiomeList),
                    LENIENT_BIOME.fieldOf("center_biome").forGetter(source -> source.centerBiome),
                    LENIENT_BIOME.fieldOf("bottom_biome").forGetter(source -> source.bottomBiome),
                    BeyondTerrainParams.FULL_CODEC.optionalFieldOf("terrain_params", BeyondTerrainParams.DEFAULTS)
                            .forGetter(source -> source.terrainParams)
            ).apply(instance, BeyondEndBiomeSource::new)
    );

    /** Reference value only; datapacks must set {@code farlands_z_boundary} explicitly. */
    public static final int DEFAULT_FARLANDS_Z_BOUNDARY = -10000;

    /** Biomes that have a dedicated overlay system (e.g. macro region) and must be kept out of
     *  the random Voronoi tainted pool — they reach {@link #allBiomes} via a separate path so
     *  {@code /locate} still works, but {@link #getNoiseBiome} never random-rolls them. */
    private static final Set<ResourceLocation> POOL_EXCLUDED_BIOMES = Set.of(
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_reef")
    );

    // Mutable: auto-discovery (EndBiomeDiscovery) may inject additional biomes at server start.
    private final List<Holder<Biome>> taintedEndBiomeList;
    private final List<Holder<Biome>> farlandsBiomeList;
    private final Optional<Integer> farlandsZBoundary;
    private final List<Holder<Biome>> outerVoidBiomeList;
    /** {@link #outerVoidBiomeList} minus {@code minecraft:the_void}. Used above the floor band so the void biome stays visually adjacent to the bottom auroracite layer. */
    private final List<Holder<Biome>> outerVoidBiomeListNoTheVoid;
    private final List<Holder<Biome>> innerVoidBiomeList;
    private final Holder<Biome> centerBiome;
    private final Holder<Biome> bottomBiome;
    /** Retained for codec round-trip. Effective values live in
     *  {@link BeyondEndChunkGenerator#activeTerrainParams}, written by this constructor. */
    private final BeyondTerrainParams terrainParams;
    private Set<Holder<Biome>> allBiomes;
    private final boolean farlandsGateActive;

    /** Per-thread cache for the y-invariant biome computation across the ~80 y-samples
     *  per (quartX, quartZ). ThreadLocal because chunk-gen is multi-threaded. */
    private static final class ColumnCache {
        /** Sentinel value meaning "no column cached yet". */
        int blockX = Integer.MIN_VALUE;
        int blockZ = Integer.MIN_VALUE;
        /** {@code absSeed} — incorporates biomeNoise × threshold × 1M plus biome-grid hash. */
        long absSeed;
    }

    private final ThreadLocal<ColumnCache> columnCacheTL = ThreadLocal.withInitial(ColumnCache::new);

    /** Baseline pool size the default {@code 0.02} frequency was calibrated for; bigger pools
     *  lose biome coherence, so {@link #getBiomeNoiseScale()} widens the simplex patches. */
    private static final int BASELINE_POOL_SIZE = 12;

    /** Cached biome-noise frequency; invalidated to {@code -1} by {@link #injectBiomesIntoTaintedPool}.
     *  Volatile because injects happen on server-start while reads happen on chunk-gen workers. */
    private volatile double cachedBiomeNoiseScale = -1.0;

    /** {@code sqrt(BASELINE / poolSize)} scaling (patches are 2D), clamped to [0.35, 1.0] × 0.02. */
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
        this.taintedEndBiomeList = new ArrayList<>(taintedEndBiomes.stream()
                .filter(h -> h.unwrapKey().map(k -> !POOL_EXCLUDED_BIOMES.contains(k.location())).orElse(true))
                .toList());
        this.farlandsBiomeList = farlandsBiomes;
        this.farlandsZBoundary = farlandsZBoundary;
        this.outerVoidBiomeList = outerVoidBiomes;
        this.outerVoidBiomeListNoTheVoid = outerVoidBiomes.stream()
                .filter(h -> !h.is(net.minecraft.world.level.biome.Biomes.THE_VOID))
                .toList();
        this.innerVoidBiomeList = innerVoidBiomes;
        this.centerBiome = centerBiome;
        this.bottomBiome = bottomBiome;
        this.terrainParams = terrainParams;

        // Gate active only when both a boundary and a non-empty Farlands pool are provided;
        // omitting either falls back to a single-pool (Tainted End only) layout.
        this.farlandsGateActive = farlandsZBoundary.isPresent() && !farlandsBiomeList.isEmpty();

        this.allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(farlandsBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        // Publish datapack-configured terrain params to the chunk generator eagerly
        // so misconfigured values fail at world-load rather than mid-generation.
        BeyondEndChunkGenerator.activeTerrainParams = terrainParams;

        if (farlandsGateActive) {
            TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} tainted, {} farlands (gate at z<{}), {} innerVoid, {} outerVoid biomes",
                    taintedEndBiomeList.size(), farlandsBiomeList.size(), farlandsZBoundary.get(),
                    innerVoidBiomeList.size(), outerVoidBiomeList.size());
        } else {
            TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} tainted (Farlands gate inactive), {} innerVoid, {} outerVoid biomes",
                    taintedEndBiomeList.size(), innerVoidBiomeList.size(), outerVoidBiomeList.size());
        }
        if (!terrainParams.equals(BeyondTerrainParams.DEFAULTS)) {
            TheBeyond.LOGGER.info("[TheBeyond] Custom terrain_params: wrap_range={} warp_amplitude={} warp_scale={}",
                    terrainParams.wrapRange(), terrainParams.warpAmplitude(), terrainParams.warpScale());
        }
        TheBeyond.LOGGER.info("[TheBeyond] Biome noise scale (patch frequency): {} (pool-size-adjusted from baseline 0.02; tainted pool size {})",
                getBiomeNoiseScale(), taintedEndBiomeList.size());

        // BeyondTerrainState.active is NOT set here: CreateWorldScreen.openFresh decodes
        // WorldDataConfiguration.DEFAULT and runs this constructor regardless of whether
        // the pack is actually selected. The flag is set authoritatively in
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

    /** Injects discovered biomes into the tainted pool at server start; skips duplicates.
     *  @return the number of biomes actually injected */
    public int injectBiomesIntoTaintedPool(Collection<Holder<Biome>> biomes) {
        Set<ResourceKey<Biome>> existingKeys = allBiomes.stream()
                .flatMap(h -> h.unwrapKey().stream())
                .collect(Collectors.toSet());

        List<Holder<Biome>> toAdd = biomes.stream()
                .filter(h -> h.unwrapKey().map(k -> !existingKeys.contains(k)).orElse(false))
                .toList();

        if (toAdd.isEmpty()) return 0;

        // Overlay-only biomes (e.g. warped_reef macro region) bypass the random Voronoi pool
        // but still reach allBiomes below so /locate and structure placement see them.
        List<Holder<Biome>> overlayOnly = toAdd.stream()
                .filter(h -> h.unwrapKey().map(k -> POOL_EXCLUDED_BIOMES.contains(k.location())).orElse(false))
                .toList();
        List<Holder<Biome>> poolEligible = toAdd.stream()
                .filter(h -> h.unwrapKey().map(k -> !POOL_EXCLUDED_BIOMES.contains(k.location())).orElse(true))
                .toList();
        taintedEndBiomeList.addAll(poolEligible);

        // Pool size changed — force biome-noise frequency recompute on next call.
        cachedBiomeNoiseScale = -1.0;

        // Rebuild combined set so /locate and structure placement see the new biomes.
        allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(overlayOnly)
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
        if (true) return getVoronoiNoiseBiome(x, y, z, sampler);
        else return getSimplexNoiseBiome(x, y, z, sampler);
    }

    public Holder<Biome> getSimplexNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        // distanceFromO is cheap (one sqrt). Compute always; don't cache.
        float distanceFromO = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);

        // centerBiome FIRST — applyBiomeDecoration runs BiomeFilter at sectionPos.origin()
        // (y=min_y). END_SPIKE is registered only in minecraft:the_end; if bottomBiome wins
        // here, the filter rejects every spike in the central island's ring.
        if (distanceFromO <= 116)
            return centerBiome;

        // bottom_biome band: auroracite row + small buffer. dimMinY follows the dim floor.
        int dimMinY = BeyondTerrainState.getDimMinY();
        if (blockY < dimMinY + 4)
            return bottomBiome;

        // Column cache: absSeed is y-invariant (two Perlin + one Simplex lookup, all in xz).
        ColumnCache cache = columnCacheTL.get();
        final long absSeed;
        if (cache.blockX == blockX && cache.blockZ == blockZ) {
            absSeed = cache.absSeed;
        } else {
            int biomeX = blockX / 64;
            int biomeZ = blockZ / 64;
            // Load-bearing unit mismatch: blockX (blocks) × biomeZ (blocks/64). The threshold
            // curve below is calibrated against this asymmetric magnitude — do NOT normalize
            // to blockZ without recalibrating, or biome selection shifts at center/edges.
            float distanceFromOrigin = (float) Math.sqrt((double) blockX * blockX + (double) biomeZ * biomeZ);

            // Intentionally NOT wrapped: these feed the absSeed biome-hash, not a density
            // sampler. Wrapping here would inject 2*wrapRange periodic repetition in biomes.
            double horizontalScale = BeyondEndChunkGenerator.getHorizontalBaseScale(biomeX, biomeZ);
            double threshold = BeyondEndChunkGenerator.getThreshold(biomeX, biomeZ, distanceFromOrigin);

            // Dedicated biome simplex field — keeps terrain retuning from reshuffling biomes.
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

            cache.blockX = blockX;
            cache.blockZ = blockZ;
            cache.absSeed = absSeed;
        }

        // Inner ring: pick directly from innerVoidBiomes without a terrain-density sample.
        // getTerrainDensity is the hottest call here (4-octave 3D simplex + 3 Perlin lookups).
        if (distanceFromO <= 690) {
            if (innerVoidBiomeList.isEmpty()) return centerBiome;
            int inner_void_index = (int) (absSeed % innerVoidBiomeList.size());
            return innerVoidBiomeList.get(inner_void_index);
        }

        // Outer region: terrain density (y-dependent, can't be column-cached) decides
        // whether this point is void or solid.
        boolean isVoid = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        if (isVoid) {
            if (outerVoidBiomeList.isEmpty()) return centerBiome;
            int outer_void_index = (int) (absSeed % outerVoidBiomeList.size());
            return outerVoidBiomeList.get(outer_void_index);
        }

        // Solid-land pool: Farlands when gate is active and north of boundary, else Tainted End.
        List<Holder<Biome>> solidPool = (farlandsGateActive && blockZ < farlandsZBoundary.get())
                ? farlandsBiomeList
                : taintedEndBiomeList;

        if (solidPool.isEmpty()) return centerBiome;
        int solid_index = (int) (absSeed % solidPool.size());
        return solidPool.get(solid_index);
    }

    public Holder<Biome> getVoronoiNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
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

            if (voronoiNoise == null) voronoiNoise = Suppliers.memoize(() -> new VoronoiNoise(BeyondEndChunkGenerator.seed, (short) 0));

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
            int inner_void_index = selectBiome(blockX, blockY, blockZ, innerVoidBiomeList);
            return innerVoidBiomeList.get(inner_void_index);
        }

        // Outer region: terrain density decides whether this point lands in a void biome
        // (empty air column) or in a solid end biome. This call DOES depend on y so it
        // cannot be hoisted into the column cache.
        boolean isVoid = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        if (isVoid) {
            // Confine minecraft:the_void to a band right above the_paths (auroracite floor).
            // Above the band, drop the_void from the pool so dim ranges that extend deep
            // negative don't smear pure-void biome across the bulk of the dim.
            List<Holder<Biome>> voidPool = blockY < dimMinY + 32
                    ? outerVoidBiomeList
                    : (outerVoidBiomeListNoTheVoid.isEmpty() ? outerVoidBiomeList : outerVoidBiomeListNoTheVoid);
            if (voidPool.isEmpty()) return centerBiome;
            int outer_void_index = selectBiome(blockX, blockY, blockZ, voidPool);
            return voidPool.get(outer_void_index);
        }

        // Macro overlay only on solid columns — keeps warped_reef from smearing onto void
        // columns near the_paths floor where the only "terrain" is the bottom_biome band.
        Holder<Biome> reefMacro = WarpedReefMacroRegion.biomeAt(blockX, blockZ);
        if (reefMacro != null) return reefMacro;

        // Pick the solid-land pool: Farlands when north of the boundary AND the gate is active,
        // Tainted End everywhere else. Gate stays inactive (and Farlands pool is empty) until the
        // dimension JSON populates `farlands_biomes` and `farlands_z_boundary`.
        List<Holder<Biome>> solidPool = (farlandsGateActive && blockZ < farlandsZBoundary.get())
                ? farlandsBiomeList
                : taintedEndBiomeList;

        if (solidPool.isEmpty()) return centerBiome;
        int solid_index = selectBiome(blockX, blockY, blockZ, solidPool);
        return solidPool.get(solid_index);
    }

    private int selectBiome(int x, int y, int z, List<Holder<Biome>> biomePool) {
        double cellSize = 200.0;
        VoronoiNoise.CellResult3D cell = voronoiNoise.get().getCell(
                x,
                y,
                z,
                1.0 / cellSize
        );

        return (int)(Math.abs(cell.cellId()) % biomePool.size());
    }

    /** Voronoi-cell biome from the tainted pool, ignoring the air/solid split — used by
     *  {@code BiomeFilterMixin} for features that need 3D-cell semantics. */
    public Holder<Biome> voronoiCellBiomeIgnoringDensity(int blockX, int blockY, int blockZ) {
        Holder<Biome> reefMacro = WarpedReefMacroRegion.biomeAt(blockX, blockZ);
        if (reefMacro != null) return reefMacro;
        if (taintedEndBiomeList.isEmpty()) return centerBiome;
        int idx = selectBiome(blockX, blockY, blockZ, taintedEndBiomeList);
        return taintedEndBiomeList.get(idx);
    }

    /** Dual-lookup so {@code /locate biome} sees Voronoi-cell biomes at air positions
     *  that regular {@link #getNoiseBiome} would resolve to void. */
    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
            BlockPos origin, int radius, int horizontalStep, int verticalStep,
            Predicate<Holder<Biome>> filter, Climate.Sampler sampler, LevelReader level) {
        int qx = QuartPos.fromBlock(origin.getX());
        int qy = QuartPos.fromBlock(origin.getY());
        int qz = QuartPos.fromBlock(origin.getZ());
        int qRadius = radius >> 2;
        int qHorizStep = Math.max(1, horizontalStep >> 2);
        int qVertStep = Math.max(1, verticalStep >> 2);
        int blockMinStep = Math.min(qHorizStep, qVertStep) * 4;
        int maxShell = Math.max(
                (qRadius + qHorizStep - 1) / qHorizStep,
                (qRadius + qVertStep - 1) / qVertStep);

        Pair<BlockPos, Holder<Biome>> best = null;
        long bestDistSq = Long.MAX_VALUE;

        // Chebyshev-shell expansion: iterate concentric cube shells outward, exit when the
        // next shell's minimum distance to origin can no longer beat {@code bestDistSq}.
        // Cuts {@code /locate biome}'s worst-case from cubic O(qRadius³) to typically O(few shells).
        shellLoop:
        for (int shell = 0; shell <= maxShell; shell++) {
            int xRange = Math.min(qRadius, shell * qHorizStep);
            int yRange = Math.min(qRadius, shell * qVertStep);
            int prevXRange = shell == 0 ? -1 : Math.min(qRadius, (shell - 1) * qHorizStep);
            int prevYRange = shell == 0 ? -1 : Math.min(qRadius, (shell - 1) * qVertStep);

            for (int yi = -yRange; yi <= yRange; yi += qVertStep) {
                int absYi = Math.abs(yi);
                int targetQy = qy + yi;
                int blockY = QuartPos.toBlock(targetQy);
                if (blockY < level.getMinBuildHeight() || blockY >= level.getMaxBuildHeight()) continue;
                for (int xi = -xRange; xi <= xRange; xi += qHorizStep) {
                    int absXi = Math.abs(xi);
                    for (int zi = -xRange; zi <= xRange; zi += qHorizStep) {
                        int absZi = Math.abs(zi);
                        // Skip points already visited by a smaller shell.
                        if (shell > 0
                                && absXi <= prevXRange
                                && absYi <= prevYRange
                                && absZi <= prevXRange) continue;

                        int targetQx = qx + xi;
                        int targetQz = qz + zi;
                        int blockX = QuartPos.toBlock(targetQx);
                        int blockZ = QuartPos.toBlock(targetQz);

                        Holder<Biome> matched = null;
                        Holder<Biome> regular = this.getNoiseBiome(targetQx, targetQy, targetQz, sampler);
                        if (filter.test(regular)) {
                            matched = regular;
                        } else {
                            Holder<Biome> voronoi = this.voronoiCellBiomeIgnoringDensity(blockX, blockY, blockZ);
                            if (filter.test(voronoi)) matched = voronoi;
                        }
                        if (matched == null) continue;

                        long dx = blockX - origin.getX();
                        long dy = blockY - origin.getY();
                        long dz = blockZ - origin.getZ();
                        long distSq = dx * dx + dy * dy + dz * dz;
                        if (distSq < bestDistSq) {
                            best = Pair.of(new BlockPos(blockX, blockY, blockZ), matched);
                            bestDistSq = distSq;
                        }
                    }
                }
            }

            if (best != null) {
                long nextMin = (long) (shell + 1) * blockMinStep;
                if (bestDistSq <= nextMin * nextMin) break shellLoop;
            }
        }
        return best;
    }
}
