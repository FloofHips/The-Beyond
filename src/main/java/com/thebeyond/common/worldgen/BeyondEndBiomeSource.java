package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondBiomeRegistration;
import com.thebeyond.api.worldgen.BeyondEndBiomeSourceApi;
import com.thebeyond.api.worldgen.BeyondMacroRegions;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
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

/** End biome source with a ResourceLocation codec, homogeneousList throws on save, unknown biomes drop at decode. */
public class BeyondEndBiomeSource extends BiomeSource implements BeyondEndBiomeSourceApi {
    private Supplier<VoronoiNoise> voronoiNoise;
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

    public static final MapCodec<BeyondEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LENIENT_BIOME_LIST.fieldOf("tainted_end_biomes").forGetter(source -> source.taintedEndBiomeList),
                    LENIENT_BIOME_LIST.optionalFieldOf("farlands_biomes", List.of()).forGetter(source -> source.farlandsBiomeList),
                    Codec.INT.optionalFieldOf("farlands_z_boundary").forGetter(source -> source.farlandsZBoundary),
                    LENIENT_BIOME_LIST.fieldOf("outer_void_biomes").forGetter(source -> source.outerVoidBiomeList),
                    LENIENT_BIOME_LIST.fieldOf("inner_void_biomes").forGetter(source -> source.innerVoidBiomeList),
                    LENIENT_BIOME_LIST.optionalFieldOf("land_rate_void_biomes", List.of())
                            .forGetter(source -> source.landRateVoidBiomeList),
                    LENIENT_BIOME.fieldOf("center_biome").forGetter(source -> source.centerBiome),
                    LENIENT_BIOME.fieldOf("bottom_biome").forGetter(source -> source.bottomBiome),
                    BeyondTerrainParams.FULL_CODEC.optionalFieldOf("terrain_params", BeyondTerrainParams.DEFAULTS)
                            .forGetter(source -> source.terrainParams)
            ).apply(instance, BeyondEndBiomeSource::new)
    );

    public static final int DEFAULT_FARLANDS_Z_BOUNDARY = -10000;

    // Mutable: End-biome auto-discovery may inject additional biomes at server start.
    private final List<Holder<Biome>> taintedEndBiomeList;
    private final List<Holder<Biome>> farlandsBiomeList;
    private final Optional<Integer> farlandsZBoundary;
    private final List<Holder<Biome>> outerVoidBiomeList;
    private final List<Holder<Biome>> outerVoidBiomeListNoTheVoid;
    private final List<Holder<Biome>> innerVoidBiomeList;
    private final List<Holder<Biome>> landRateVoidBiomeList;
    private final Holder<Biome> centerBiome;
    private final Holder<Biome> bottomBiome;
    /** Effective values live in {@link BeyondEndChunkGenerator#activeTerrainParams}, written by this constructor. */
    private final BeyondTerrainParams terrainParams;
    private Set<Holder<Biome>> allBiomes;
    private final boolean farlandsGateActive;

    private final Set<ResourceKey<Biome>> disabledBiomes = new HashSet<>();
    private volatile Map<List<Holder<Biome>>, PoolMask> poolMasks;

    private record PoolMask(boolean[] off, int[] enabled) {}

    private volatile LandRateDraw landRateDraw;

    private record LandRateDraw(List<Holder<Biome>> slots, int cells) {}

    public BeyondEndBiomeSource(List<Holder<Biome>> taintedEndBiomes,
                                List<Holder<Biome>> farlandsBiomes,
                                Optional<Integer> farlandsZBoundary,
                                List<Holder<Biome>> outerVoidBiomes,
                                List<Holder<Biome>> innerVoidBiomes,
                                List<Holder<Biome>> landRateVoidBiomes,
                                Holder<Biome> centerBiome, Holder<Biome> bottomBiome,
                                BeyondTerrainParams terrainParams) {
        super();
        this.taintedEndBiomeList = new ArrayList<>(taintedEndBiomes.stream()
                .filter(h -> h.unwrapKey().map(k -> !BeyondBiomeRegistration.isPoolExcluded(k.location())).orElse(true))
                .toList());
        this.farlandsBiomeList = farlandsBiomes;
        this.farlandsZBoundary = farlandsZBoundary;
        this.outerVoidBiomeList = outerVoidBiomes;
        this.outerVoidBiomeListNoTheVoid = outerVoidBiomes.stream()
                .filter(h -> !h.is(net.minecraft.world.level.biome.Biomes.THE_VOID))
                .toList();
        this.innerVoidBiomeList = innerVoidBiomes;
        this.landRateVoidBiomeList = landRateVoidBiomes;
        this.centerBiome = centerBiome;
        this.bottomBiome = bottomBiome;
        this.terrainParams = terrainParams;

        this.farlandsGateActive = farlandsZBoundary.isPresent() && !farlandsBiomeList.isEmpty();

        this.allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(farlandsBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .addAll(landRateVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        // Published here (not lazily) so misconfigured terrain_params fail at world-load, not mid-generation.
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
        rebuildPoolMasks();
        // terrain state is not set here, CreateWorldScreen builds this even without the pack, BeyondCoreLifecycle sets it
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return allBiomes.stream();
    }

    @Override
    public int injectBiomesIntoTaintedPool(Collection<Holder<Biome>> biomes) {
        Set<ResourceKey<Biome>> existingKeys = allBiomes.stream()
                .flatMap(h -> h.unwrapKey().stream())
                .collect(Collectors.toSet());

        List<Holder<Biome>> toAdd = biomes.stream()
                .filter(h -> h.unwrapKey().map(k -> !existingKeys.contains(k)).orElse(false))
                .toList();

        if (toAdd.isEmpty()) return 0;

        // Overlay-only biomes bypass the Voronoi pool but still reach allBiomes for /locate.
        List<Holder<Biome>> overlayOnly = toAdd.stream()
                .filter(h -> h.unwrapKey().map(k -> BeyondBiomeRegistration.isPoolExcluded(k.location())).orElse(false))
                .toList();
        List<Holder<Biome>> poolEligible = toAdd.stream()
                .filter(h -> h.unwrapKey().map(k -> !BeyondBiomeRegistration.isPoolExcluded(k.location())).orElse(true))
                .toList();
        taintedEndBiomeList.addAll(poolEligible);
        rebuildPoolMasks();

        allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(overlayOnly)
                .addAll(farlandsBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .addAll(landRateVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        Set<Holder<Biome>> finalBiomes = allBiomes;
        this.possibleBiomes = Suppliers.memoize(() -> finalBiomes);

        return toAdd.size();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return getVoronoiNoiseBiome(x, y, z, sampler);
    }

    public Holder<Biome> getVoronoiNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        float distanceFromO = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);

        // centerBiome first: BiomeFilter runs at y = min_y, and bottomBiome there would reject every ring spike
        if (distanceFromO <= 116)
            return centerBiome;

        int dimMinY = BeyondTerrainState.getDimMinY();
        if (blockY < dimMinY + 10)
            return bottomBiome;

        if (BeyondEndChunkGenerator.biomeSimplexNoise == null) return centerBiome;

        // Inside 690 radius, skip getTerrainDensity (the costliest call here) entirely.
        if (distanceFromO <= 690) {
            if (innerVoidBiomeList.isEmpty()) return centerBiome;
            int inner_void_index = selectBiome(blockX, blockY, blockZ, innerVoidBiomeList);
            return innerVoidBiomeList.get(inner_void_index);
        }

        boolean isVoid = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        if (isVoid) {
            // the_void stays in a band above the_paths so deep dimensions are not mostly void
            List<Holder<Biome>> voidPool = blockY < dimMinY + 32
                    ? outerVoidBiomeList
                    : (outerVoidBiomeListNoTheVoid.isEmpty() ? outerVoidBiomeList : outerVoidBiomeListNoTheVoid);
            if (voidPool.isEmpty()) return centerBiome;
            long cellId = cellId(blockX, blockY, blockZ);
            Holder<Biome> landRate = landRateVoidBiome(cellId);
            return landRate != null ? landRate : voidPool.get(selectBiome(cellId, voidPool));
        }

        // solid columns only, so a macro region never smears onto void columns near the_paths floor
        Holder<Biome> reefMacro = BeyondMacroRegions.queryAt(blockX, blockZ);
        if (reefMacro != null) return reefMacro;

        List<Holder<Biome>> solidPool = (farlandsGateActive && blockZ < farlandsZBoundary.get())
                ? farlandsBiomeList
                : taintedEndBiomeList;

        if (solidPool.isEmpty()) return centerBiome;
        int solid_index = selectBiome(blockX, blockY, blockZ, solidPool);
        return solidPool.get(solid_index);
    }

    private int selectBiome(int x, int y, int z, List<Holder<Biome>> biomePool) {
        return selectBiome(cellId(x, y, z), biomePool);
    }

    /** Scaling y by width over height makes the cells that tall, and equal sizes give back the plain cubic cell. */
    private long cellId(int x, int y, int z) {
        double width = BiomeCellScale.cellWidth();
        return voronoi().cellId3D(x, y * (width / BiomeCellScale.cellHeight()), z, 1.0 / width);
    }

    private VoronoiNoise voronoi() {
        Supplier<VoronoiNoise> noise = voronoiNoise;
        if (noise == null) {
            noise = Suppliers.memoize(() -> new VoronoiNoise(BeyondEndChunkGenerator.seed, (short) 0));
            voronoiNoise = noise;
        }
        return noise.get();
    }

    private int selectBiome(long cellId, List<Holder<Biome>> biomePool) {
        int index = (int)(Math.abs(cellId) % biomePool.size());
        Map<List<Holder<Biome>>, PoolMask> masks = poolMasks;
        if (masks == null) return index;
        PoolMask mask = masks.get(biomePool);
        if (mask == null || !mask.off()[index]) return index;
        long h = cellId * 0x9E3779B97F4A7C15L;
        return mask.enabled()[(int) Math.floorMod(h ^ (h >>> 32), (long) mask.enabled().length)];
    }

    /** The land-rate void biome of this cell, or null: each takes one void cell in (enabled land biomes + their count). */
    private Holder<Biome> landRateVoidBiome(long cellId) {
        LandRateDraw draw = landRateDraw;
        if (draw == null) return null;
        long h = (cellId ^ 0x5DEECE66DL) * 0x9E3779B97F4A7C15L;
        int slot = (int) Math.floorMod(h ^ (h >>> 32), (long) draw.cells());
        return slot < draw.slots().size() ? draw.slots().get(slot) : null;
    }

    /** Disabled biomes keep their pool slot, so only their own cells re-roll, and a pool left with none keeps them all. */
    @Override
    public int disableBiomes(Collection<ResourceKey<Biome>> keys) {
        Set<ResourceKey<Biome>> pooled = Stream.of(taintedEndBiomeList, farlandsBiomeList, outerVoidBiomeList, innerVoidBiomeList)
                .flatMap(List::stream).flatMap(h -> h.unwrapKey().stream()).collect(Collectors.toSet());
        int added = 0;
        for (ResourceKey<Biome> key : keys) {
            if (pooled.contains(key)) {
                if (disabledBiomes.add(key)) added++;
            } else if (key.equals(centerBiome.unwrapKey().orElse(null)) || key.equals(bottomBiome.unwrapKey().orElse(null))) {
                TheBeyond.LOGGER.warn("[TheBeyond] {} is the End's center or bottom biome and cannot be disabled", key.location());
            }
        }
        rebuildPoolMasks();
        return added;
    }

    private void rebuildPoolMasks() {
        if (disabledBiomes.isEmpty() && landRateVoidBiomeList.isEmpty()) return;
        Map<List<Holder<Biome>>, PoolMask> masks = new IdentityHashMap<>();
        for (List<Holder<Biome>> pool : List.of(taintedEndBiomeList, farlandsBiomeList, outerVoidBiomeList,
                outerVoidBiomeListNoTheVoid, innerVoidBiomeList)) {
            // a land-rate biome also listed in the outer void pool gives its slots back, its share comes from its own draw
            boolean outerVoid = pool == outerVoidBiomeList || pool == outerVoidBiomeListNoTheVoid;
            boolean[] off = new boolean[pool.size()];
            int[] enabled = new int[pool.size()];
            int count = 0;
            boolean anyDisabled = false;
            for (int i = 0; i < pool.size(); i++) {
                boolean disabled = isDisabled(pool.get(i));
                anyDisabled |= disabled;
                off[i] = disabled || outerVoid && landRateVoidBiomeList.contains(pool.get(i));
                if (!off[i]) enabled[count++] = i;
            }
            if (count == 0 && !pool.isEmpty()) {
                if (anyDisabled) {
                    TheBeyond.LOGGER.warn("[TheBeyond] Every biome of an End pool is disabled, keeping that pool as it is: {}",
                            pool.stream().flatMap(h -> h.unwrapKey().stream()).map(k -> k.location().toString()).toList());
                }
            } else if (count < pool.size()) {
                masks.put(pool, new PoolMask(off, Arrays.copyOf(enabled, count)));
            }
        }
        poolMasks = masks.isEmpty() ? null : masks;
        rebuildLandRateDraw();
    }

    private void rebuildLandRateDraw() {
        if (landRateVoidBiomeList.isEmpty()) return;
        int land = (int) taintedEndBiomeList.stream().filter(h -> !isDisabled(h)).count();
        if (land == 0) land = taintedEndBiomeList.size();
        List<Holder<Biome>> slots = new ArrayList<>(landRateVoidBiomeList.size());
        for (Holder<Biome> biome : landRateVoidBiomeList) slots.add(isDisabled(biome) ? null : biome);
        LandRateDraw draw = new LandRateDraw(Collections.unmodifiableList(slots), land + slots.size());
        if (!draw.equals(landRateDraw)) {
            TheBeyond.LOGGER.info("[TheBeyond] Land-rate void biomes {} take one void cell in {} each",
                    landRateVoidBiomeList.stream().flatMap(h -> h.unwrapKey().stream()).map(k -> k.location().toString()).toList(),
                    draw.cells());
        }
        landRateDraw = draw;
    }

    private boolean isDisabled(Holder<Biome> biome) {
        return biome.unwrapKey().map(disabledBiomes::contains).orElse(false);
    }

    /** Voronoi-cell biome of the tainted pool ignoring air and solid, for Isleweaver's 3D-cell feature filter. */
    @Override
    public Holder<Biome> voronoiCellBiomeIgnoringDensity(int blockX, int blockY, int blockZ) {
        Holder<Biome> reefMacro = BeyondMacroRegions.queryAt(blockX, blockZ);
        if (reefMacro != null) return reefMacro;
        if (taintedEndBiomeList.isEmpty()) return centerBiome;
        int idx = selectBiome(blockX, blockY, blockZ, taintedEndBiomeList);
        return taintedEndBiomeList.get(idx);
    }

    /** Looks both ways so /locate biome finds Voronoi-cell biomes at air positions getNoiseBiome calls void. */
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

        // cube shells outward, stopping once the next shell cannot beat bestDistSq, so locate skips most of the cube
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
                            float dist = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);
                            if (dist > 690.0f) {
                                Holder<Biome> voronoi = this.voronoiCellBiomeIgnoringDensity(blockX, blockY, blockZ);
                                if (filter.test(voronoi)) matched = voronoi;
                            }
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
