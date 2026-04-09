package com.thebeyond.common.worldgen;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.TheBeyond;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class BeyondEndBiomeSource extends BiomeSource {
    public static final MapCodec<BeyondEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("end_biomes").forGetter(source -> source.endBiomes),
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("outer_void_biomes").forGetter(source -> source.outerVoidBiomes),
                    RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("inner_void_biomes").forGetter(source -> source.innerVoidBiomes),
                    Biome.CODEC.fieldOf("center_biome").forGetter(source -> source.centerBiome),
                    Biome.CODEC.fieldOf("bottom_biome").forGetter(source -> source.bottomBiome)
            ).apply(instance, BeyondEndBiomeSource::new)
    );

    private final HolderSet<Biome> endBiomes;
    private final HolderSet<Biome> outerVoidBiomes;
    private final HolderSet<Biome> innerVoidBiomes;
    private final Holder<Biome> centerBiome;
    private final Holder<Biome> bottomBiome;
    private final Set<Holder<Biome>> allBiomes;

    public BeyondEndBiomeSource(HolderSet<Biome> endBiomes, HolderSet<Biome> outerVoidBiomes, HolderSet<Biome> innerVoidBiomes,
                                Holder<Biome> centerBiome, Holder<Biome> bottomBiome) {
        super();
        this.endBiomes = endBiomes;
        this.outerVoidBiomes = outerVoidBiomes;
        this.centerBiome = centerBiome;
        this.bottomBiome = bottomBiome;
        this.innerVoidBiomes = innerVoidBiomes;

        this.allBiomes = ImmutableSet.<Holder<Biome>>builder()
                .addAll(endBiomes.stream().toList())
                .addAll(innerVoidBiomes.stream().toList())
                .addAll(outerVoidBiomes.stream().toList())
                .add(centerBiome)
                .add(bottomBiome)
                .build();
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
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        if (allBiomes.isEmpty()) {
            //throw new IllegalStateException("No biomes configured in BeyondEndBiomeSource");
        }

        int blockX = QuartPos.toBlock(x);
        int blockY = QuartPos.toBlock(y);
        int blockZ = QuartPos.toBlock(z);

        float distanceFromO = (float) Math.sqrt((double) blockX * blockX + (double) blockZ * blockZ);

        if (distanceFromO <= 116)
            return centerBiome;

        if (blockY < 20)
            return bottomBiome;

        int biomeX = blockX / 64;
        int biomeZ = blockZ / 64;

        float distanceFromOrigin = (float) Math.sqrt((double) blockX * blockX + (double) biomeZ * biomeZ);

        double horizontalScale = BeyondEndChunkGenerator.getHorizontalBaseScale(biomeX, biomeZ);
        double threshold = BeyondEndChunkGenerator.getThreshold(biomeX, biomeZ, distanceFromOrigin);

        double biomeNoise = BeyondEndChunkGenerator.simplexNoise.getValue(
                biomeX * horizontalScale * 0.1,
                biomeZ * horizontalScale * 0.1
        );

        long seed = (long) (biomeNoise * threshold * 1000000) + biomeX * 31L + biomeZ * 961L;
        long absSeed = Math.abs(seed);

        // Inner void ring: pick directly from innerVoidBiomes without sampling terrain density.
        // getTerrainDensity() is the most expensive call in this method (4-octave 3D simplex +
        // 3 Perlin scale/cycle lookups), so skipping it here halves the per-call cost for every
        // point inside the 690-block radius - a huge portion of a typical End chunk column.
        if (distanceFromO <= 690) {
            int inner_void_index = (int) (absSeed % innerVoidBiomes.size());
            return innerVoidBiomes.get(inner_void_index);
        }

        // Outer region: terrain density decides whether this point lands in a void biome
        // (empty air column) or in a solid end biome.
        boolean voidPoint = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        // HolderSet.get(int) is O(1) - the previous implementation allocated three new
        // ArrayLists per call via stream().toList() before indexing into one of them, which
        // was millions of throwaway allocations per chunk batch and dominated GC pressure.
        if (voidPoint) {
            int outer_void_index = (int) (absSeed % outerVoidBiomes.size());
            return outerVoidBiomes.get(outer_void_index);
        } else {
            int solid_index = (int) (absSeed % endBiomes.size());
            return endBiomes.get(solid_index);
        }
    }
}