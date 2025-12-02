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

import java.util.List;
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
        int sectionX = SectionPos.blockToSectionCoord(blockX);
        int sectionZ = SectionPos.blockToSectionCoord(blockZ);

        float distanceFromO = (float) Math.sqrt(blockX * blockX + blockZ * blockZ);

        if (distanceFromO <= 116)
            return centerBiome;

        if(blockY < 20)
            return bottomBiome;

        int biomeX = blockX / 64;
        int biomeZ = blockZ / 64;

        float distanceFromOrigin = (float) Math.sqrt(blockX * blockX + biomeZ * biomeZ);

        double horizontalScale = BeyondEndChunkGenerator.getHorizontalBaseScale(biomeX, biomeZ);
        double threshold = BeyondEndChunkGenerator.getThreshold(biomeX, biomeZ, distanceFromOrigin);

        double biomeNoise = BeyondEndChunkGenerator.simplexNoise.getValue(
                biomeX * horizontalScale * 0.2,
                biomeZ * horizontalScale * 0.2
        );

        long seed = (long) (biomeNoise * threshold * 1000000) + biomeX * 31L + biomeZ * 961L;
        int solid_index = (int) (Math.abs(seed) % endBiomes.size());
        int inner_void_index = (int) (Math.abs(seed) % innerVoidBiomes.size());
        int outer_void_index = (int) (Math.abs(seed) % outerVoidBiomes.size());


        boolean f = BeyondEndChunkGenerator.getTerrainDensity(blockX, blockY, blockZ) < 0.01f;

        List<Holder<Biome>> endBiomeList = endBiomes.stream().toList();
        List<Holder<Biome>> innerVoidBiomeList = innerVoidBiomes.stream().toList();
        List<Holder<Biome>> outerVoidBiomeList = outerVoidBiomes.stream().toList();

        if (distanceFromO <= 690)
            return innerVoidBiomes.get(inner_void_index);

        return f ? outerVoidBiomeList.get(outer_void_index) : endBiomeList.get(solid_index);
    }
}