package com.thebeyond.common.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BeyondEndChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<BeyondEndChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((p_255585_) -> {
        return p_255585_.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter((p_255584_) -> {
            return p_255584_.biomeSource;
        }), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter((p_224278_) -> {
            return p_224278_.settings;
        })).apply(p_255585_, p_255585_.stable(BeyondEndChunkGenerator::new));
    });

    private final Holder<NoiseGeneratorSettings> settings;
    public static SimplexNoise simplexNoise;
    public static PerlinSimplexNoise globalHOffsetNoise;
    public static PerlinSimplexNoise globalVOffsetNoise;
    public static PerlinSimplexNoise globalCOffsetNoise;
    private double islandRadius = 75.0;
    private double buffer = 700.0;
    private static final double worldHeight = 192;

    private static final int NUM_OCTAVES = 4;
    private static final double LACUNARITY = 2.0;
    private static final double PERSISTENCE = 0.5;
    private static final int TERRAIN_Y_OFFSET = 32;

    public BeyondEndChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);

        this.settings = settings;
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
        long seed = structureState.getLevelSeed();

        RandomSource random1 = RandomSource.create(seed);
        RandomSource random2 = RandomSource.create(seed);
        RandomSource random3 = RandomSource.create(seed);
        RandomSource random4 = RandomSource.create(seed);
        simplexNoise = new SimplexNoise(random1);
        globalHOffsetNoise = new PerlinSimplexNoise(random2, Collections.singletonList(1));
        globalVOffsetNoise = new PerlinSimplexNoise(random3, Collections.singletonList(1));
        globalCOffsetNoise = new PerlinSimplexNoise(random4, Collections.singletonList(1));

        super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return super.createBiomes(randomState, blender, structureManager, chunk);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        float distanceFromOrigin = (float) Math.sqrt(x * x + z * z);

        for (int y = 132; y >= level.getMinBuildHeight() - 10; y--) {
            if (isSolidTerrain(x, y, z, distanceFromOrigin)) {
                return y;
            }
        }

        return 10;
    }
    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.max(this.getBaseHeight(x, z, type, level, random), level.getMinBuildHeight() - 10);
    }

    @Override
    public int getMinY() {
        return 10;
    }

    public static double getHorizontalBaseScale(int globalX, int globalZ) {
        return globalNoiseOffset(0.005, 0.015, globalX * 0.000001, globalZ * 0.000001, globalHOffsetNoise);
    }

    public static double getVerticalBaseScale(int globalX, int globalZ) {
        return globalNoiseOffset(0.005, 0.015, globalX * 0.00001, globalZ * 0.00001, globalVOffsetNoise);
    }

    public static double getCycleHeight(int globalX, int globalZ) {
        return globalNoiseOffset(10, 100, globalX * 0.0001, globalZ * 0.0001, globalCOffsetNoise);
    }

    public static double getThreshold(int globalX, int globalZ, float distanceFromOrigin) {
        double baseThreshold = globalNoiseOffset(0.01, 0.6, globalX * 0.0002, globalZ * 0.0002, globalCOffsetNoise);

        float innerTaperEnd = 100f;
        float outerTaperStart = 700f;
        float outerTaperEnd = 750f;

        if (distanceFromOrigin > innerTaperEnd && distanceFromOrigin < outerTaperStart) {
            return 1.0;
        }
        else if (distanceFromOrigin >= outerTaperStart && distanceFromOrigin <= outerTaperEnd) {
            float progress = (distanceFromOrigin - outerTaperStart) / (outerTaperEnd - outerTaperStart);
            double taperValue = 0.59 * (1.0 - progress);

            return baseThreshold + taperValue;
        }
        else {
            return baseThreshold;
        }
    }

    public static double getTerrainDensity(int globalX, int globalY, int globalZ) {
        double horizontalBaseScale = getHorizontalBaseScale(globalX, globalZ);
        double verticalBaseScale = getVerticalBaseScale(globalX, globalZ);
        double cycleHeight = getCycleHeight(globalX, globalZ);

        int shiftedY = globalY + TERRAIN_Y_OFFSET;

        double noiseValue = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxAmplitude = 0.0;

        for (int octave = 0; octave < NUM_OCTAVES; octave++) {
            double hScale = horizontalBaseScale * frequency;
            double vScale = verticalBaseScale * frequency;

            double sampleX = globalX * hScale;
            double sampleY = shiftedY * vScale;
            double sampleZ = globalZ * hScale;

            double octaveNoise = simplexNoise.getValue(sampleX, sampleY, sampleZ);
            noiseValue += octaveNoise * amplitude;
            maxAmplitude += amplitude;

            amplitude *= PERSISTENCE;
            frequency *= LACUNARITY;
        }

        noiseValue /= maxAmplitude;
        double densityModifier = cyclicDensity(shiftedY, cycleHeight);
        noiseValue *= densityModifier;

        return edgeGradient(shiftedY, worldHeight, noiseValue);
    }

    public static boolean isSolidTerrain(int globalX, int globalY, int globalZ, float distanceFromOrigin) {
        double threshold = getThreshold(globalX, globalZ, distanceFromOrigin);
        double density = getTerrainDensity(globalX, globalY, globalZ);

        return density > threshold;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int globalX = startX + x;
                    int globalZ = startZ + z;

                    generateAuroracite(chunk, globalX, globalZ);

                    float distanceFromOrigin = (float) Math.sqrt(globalX * globalX + globalZ * globalZ);

                    if (distanceFromOrigin <= islandRadius + 50) {
                        generateMainIsland(chunk, globalX, globalZ, distanceFromOrigin, islandRadius);
                    }

                    else if (distanceFromOrigin >= 650) {
                      generateEndTerrain(chunk, globalX, globalZ, distanceFromOrigin);
                    }
                }
            }

            return chunk;
        });
    }

    private void generateMainIsland(ChunkAccess chunk, int globalX, int globalZ, double distance, double islandRadius) {
        islandRadius += 50;
        int height = 40;
        float threshold = 1;

        for (int y = 0; y <= 40; y++) {
            double noiseValue = simplexNoise.getValue(globalX * 0.03, y * 0.1, globalZ * 0.03);
            double noiseOctave = simplexNoise.getValue(globalX * 0.1, y * 0.1, globalZ * 0.1);

            double finalNoise = noiseValue + noiseOctave * 0.2f;

            if (y > 37) {
                threshold = 1 - ((y - 37) / 3f);
            }
            else {
                threshold = y / 35f;
            }

            if (globalX * globalX + y * y + globalZ * globalZ <= islandRadius * islandRadius * (0.5 + 0.5 * finalNoise) * threshold) {
                chunk.setBlockState(new BlockPos(globalX, y + 20 , globalZ), Blocks.END_STONE.defaultBlockState(), false);
            }
        }
    }

    private void generateFarlands(ChunkAccess chunk, int globalX, int globalZ) {

    }

    private void generateAuroracite(ChunkAccess chunk, int globalX, int globalZ) {
        double auroraNoise = simplexNoise.getValue(globalX * 0.1, globalZ * 0.1);
        if (auroraNoise > 0.0) {
            chunk.setBlockState(new BlockPos(globalX, 0, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), false);
            chunk.setBlockState(new BlockPos(globalX, 1, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), false);
        }
    }

    private void generateEndTerrain(ChunkAccess chunk, int globalX, int globalZ, float distanceFromOrigin) {
        for (int y = 1; y < 160; y++) {
            if (isSolidTerrain(globalX, y, globalZ, distanceFromOrigin)) {
                chunk.setBlockState(new BlockPos(globalX, y, globalZ), Blocks.END_STONE.defaultBlockState(), false);
            }
        }
    }

    private static double cyclicDensity(int y, double cycleHeight) {
        double normalizedY = (y % cycleHeight) / cycleHeight;
        if (normalizedY < 0.8) {
            return Math.sin((normalizedY / 0.8) * (Math.PI / 2));
        } else {
            return 1 - (normalizedY - 0.8) / 0.2;
        }
    }

    private static double edgeGradient(double y, double worldHeight, double noiseValue) {
        double gradientBottom = 1.0;
        double gradientTop = 1.0;

        if (y <= 64) {
            gradientBottom = (y - 32) / 32.0;
            if (y < 32) {
                gradientBottom = 0.0;
            }
        }

        if (y >= (worldHeight - 64)) {
            gradientTop = (worldHeight - y) / 64.0;
        }

        return gradientBottom * gradientTop * noiseValue;
    }

    private static double globalNoiseOffset(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double noiseValue = noise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }

    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        int globalX = pos.getX();
        int globalZ = pos.getZ();

        float distanceFromOrigin = (float) Math.sqrt(globalX * globalX + globalZ * globalZ);
        double horizontalBaseScale = getHorizontalBaseScale(pos.getX(), pos.getZ());
        double verticalBaseScale = getVerticalBaseScale(pos.getX(), pos.getZ());
        double threshold = getThreshold(pos.getX(), pos.getZ(), distanceFromOrigin);
        double cycleHeight = getCycleHeight(pos.getX(), pos.getZ());
        double terrainNoise = getTerrainDensity(pos.getX(), pos.getY(), pos.getZ());
        double simplex = simplexNoise.getValue(pos.getX(), pos.getY(), pos.getZ());

        //info.add("TerrainNoise T: " + decimalformat.format(terrainNoise) +
        //        " HS: " + decimalformat.format(horizontalBaseScale) +
        //        " VS: " + decimalformat.format(verticalBaseScale) +
        //        " Threshold: " + decimalformat.format(threshold) +
        //        " CH: " + decimalformat.format(cycleHeight));

        info.add("Simplex: " + decimalformat.format(simplex));
    }
}