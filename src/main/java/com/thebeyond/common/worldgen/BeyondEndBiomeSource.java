package com.thebeyond.common.worldgen;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
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
     *
     * Field rename note: pre-2026-04-10 the main pool was called `end_biomes`. It is now
     * `tainted_end_biomes` to make the duo-region intent obvious. Existing dimension JSONs must be
     * updated when upgrading.
     */
    public static final MapCodec<BeyondEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    LENIENT_BIOME_LIST.fieldOf("tainted_end_biomes").forGetter(source -> source.taintedEndBiomeList),
                    LENIENT_BIOME_LIST.optionalFieldOf("farlands_biomes", List.of()).forGetter(source -> source.farlandsBiomeList),
                    Codec.INT.optionalFieldOf("farlands_z_boundary").forGetter(source -> source.farlandsZBoundary),
                    LENIENT_BIOME_LIST.fieldOf("outer_void_biomes").forGetter(source -> source.outerVoidBiomeList),
                    LENIENT_BIOME_LIST.fieldOf("inner_void_biomes").forGetter(source -> source.innerVoidBiomeList),
                    LENIENT_BIOME.fieldOf("center_biome").forGetter(source -> source.centerBiome),
                    LENIENT_BIOME.fieldOf("bottom_biome").forGetter(source -> source.bottomBiome)
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
    private Set<Holder<Biome>> allBiomes;
    private final boolean farlandsGateActive;

    public BeyondEndBiomeSource(List<Holder<Biome>> taintedEndBiomes,
                                List<Holder<Biome>> farlandsBiomes,
                                Optional<Integer> farlandsZBoundary,
                                List<Holder<Biome>> outerVoidBiomes,
                                List<Holder<Biome>> innerVoidBiomes,
                                Holder<Biome> centerBiome, Holder<Biome> bottomBiome) {
        super();
        this.taintedEndBiomeList = new ArrayList<>(taintedEndBiomes);
        this.farlandsBiomeList = farlandsBiomes;
        this.farlandsZBoundary = farlandsZBoundary;
        this.outerVoidBiomeList = outerVoidBiomes;
        this.innerVoidBiomeList = innerVoidBiomes;
        this.centerBiome = centerBiome;
        this.bottomBiome = bottomBiome;

        // Gate is active only when BOTH a boundary and a non-empty Farlands pool are provided.
        // This makes the schema scaffolding-friendly: omit either field and the duo-region is silently
        // disabled, falling back to the pre-refactor single-pool behavior.
        this.farlandsGateActive = farlandsZBoundary.isPresent() && !farlandsBiomeList.isEmpty();

        this.allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(taintedEndBiomeList)
                .addAll(farlandsBiomeList)
                .addAll(innerVoidBiomeList)
                .addAll(outerVoidBiomeList)
                .add(centerBiome)
                .add(bottomBiome)
                .build();

        if (farlandsGateActive) {
            TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} tainted, {} farlands (gate at z<{}), {} innerVoid, {} outerVoid biomes",
                    taintedEndBiomeList.size(), farlandsBiomeList.size(), farlandsZBoundary.get(),
                    innerVoidBiomeList.size(), outerVoidBiomeList.size());
        } else {
            TheBeyond.LOGGER.info("[TheBeyond] BiomeSource initialized: {} tainted (Farlands gate inactive), {} innerVoid, {} outerVoid biomes",
                    taintedEndBiomeList.size(), innerVoidBiomeList.size(), outerVoidBiomeList.size());
        }

        // Mark Beyond's terrain as the active End provider for this server session.
        // ServerWorldEvents reads this to skip fallback biome injection paths.
        BeyondTerrainState.markActive();
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

        float distanceFromO = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);

        if (distanceFromO <= 116)
            return centerBiome;

        // bottom_biome (the_paths) is the auroracite floor at y=0,1 — restrict it to the very
        // bottom of the dimension. The previous y<20 check assigned the_paths to any low-y
        // sample, including raised end_stone terrain in the y=1-20 band, which let creatures
        // from the_paths spawners (Abyssal Nomads) appear on solid terrain far above the
        // actual paths layer. Quart pos 0 is blockY 0-3, which covers the auroracite row plus
        // a small buffer.
        if (blockY < 4)
            return bottomBiome;

        int biomeX = blockX / 64;
        int biomeZ = blockZ / 64;

        float distanceFromOrigin = (float) Math.sqrt((double) blockX * blockX + (double) biomeZ * biomeZ);

        double horizontalScale = BeyondEndChunkGenerator.getHorizontalBaseScale(biomeX, biomeZ);
        double threshold = BeyondEndChunkGenerator.getThreshold(biomeX, biomeZ, distanceFromOrigin);

        // f4ba752 "Tweaks and Tweaks": halved horizontal noise frequency (0.2 -> 0.1) so
        // biome patches are ~2x larger and don't look shredded at default render distance.
        if (BeyondEndChunkGenerator.simplexNoise == null) {
            return centerBiome;
        }
        double biomeNoise = BeyondEndChunkGenerator.simplexNoise.getValue(
                biomeX * horizontalScale * 0.1,
                biomeZ * horizontalScale * 0.1
        );

        long seed = (long) (biomeNoise * threshold * 1000000) + biomeX * 31L + biomeZ * 961L;
        long absSeed = Math.abs(seed);

        // d985d68 "Even more bug fixes!": pick directly from innerVoidBiomes without sampling
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
        // (empty air column) or in a solid end biome.
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
