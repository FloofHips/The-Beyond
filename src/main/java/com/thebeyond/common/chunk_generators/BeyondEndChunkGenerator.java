package com.thebeyond.common.chunk_generators;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.Collections;
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
    private final SimplexNoise simplexNoise;
    private final PerlinSimplexNoise globalHOffsetNoise;
    private final PerlinSimplexNoise globalVOffsetNoise;
    private final PerlinSimplexNoise globalCOffsetNoise;
    private final double worldHeight = 256;

    public BeyondEndChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
        RandomSource random1 = RandomSource.create(545424);
        RandomSource random2 = RandomSource.create(254525);
        RandomSource random3 = RandomSource.create(542244);
        RandomSource random4 = RandomSource.create(254572);
        this.simplexNoise = new SimplexNoise(random1);
        this.globalHOffsetNoise = new PerlinSimplexNoise(random2, Collections.singletonList(1));
        this.globalVOffsetNoise = new PerlinSimplexNoise(random3, Collections.singletonList(1));
        this.globalCOffsetNoise = new PerlinSimplexNoise(random4, Collections.singletonList(1));
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();
            int sizeX = 16;
            int sizeY = 256;
            int sizeZ = 16;

            // Octave stuff, don't touch this. PLEASE!
            int numOctaves = 4;
            double lacunarity = 2.0;
            double persistence = 0.5;

            // Dynamic stuff
            double horizontalBaseScale;
            double verticalBaseScale;
            double threshold;
            double cycleHeight;

            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int y = 0; y < sizeY; y++) {
                        int globalX = startX + x;
                        int globalZ = startZ + z;

                        //min = 0.005, max = 0.015
                        horizontalBaseScale = globalHorizontalScaleOffset(0.005, 0.015,globalX * 0.00001,globalZ * 0.00001);
                        verticalBaseScale = globalVerticalScaleOffset(0.005, 0.015,globalX * 0.00001,globalZ * 0.00001);
                        cycleHeight = globalCycleOffset(10, 100, globalX * 0.0001,globalZ * 0.0001);
                        threshold = globalThresholdOffset(0.01, 0.6, globalX * 0.0002,globalZ * 0.0002);

                        double noiseValue = 0.0;
                        double amplitude = 1.0;
                        double frequency = 1.0;
                        double maxAmplitude = 0.0;

                        // Create basic noise stuff
                        for (int octave = 0; octave < numOctaves; octave++) {
                            double hScale = horizontalBaseScale * frequency;
                            double vScale = verticalBaseScale * frequency;
                            double octaveNoise = simplexNoise.getValue(globalX * hScale, y * vScale, globalZ * hScale);

                            noiseValue += octaveNoise * amplitude;
                            maxAmplitude += amplitude;

                            amplitude *= persistence;
                            frequency *= lacunarity;
                        }

                        // Normalize
                        noiseValue /= maxAmplitude;

                        // Apply sine wave gaps
                        double densityModifier = cyclicDensity(y, cycleHeight);
                        noiseValue *= densityModifier;

                        BlockPos blockPos = new BlockPos(globalX, y, globalZ);
                        double finalValue = edgeGradient(blockPos.getY(), worldHeight, noiseValue);
                        if (finalValue > threshold) {
                            chunk.setBlockState(blockPos, Blocks.END_STONE.defaultBlockState(), false);
//                            if (horizontalBaseScale <= 0.005) {
//                                chunk.setBlockState(blockPos, Blocks.BLACK_STAINED_GLASS.defaultBlockState(), false);
//                            } else if (horizontalBaseScale > 0.005 && horizontalBaseScale <= 0.0075) {
//                                chunk.setBlockState(blockPos, Blocks.GRAY_STAINED_GLASS.defaultBlockState(), false);
//                            } else if (horizontalBaseScale > 0.0075 && horizontalBaseScale <= 0.01) {
//                                chunk.setBlockState(blockPos, Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState(), false);
//                            } else if (horizontalBaseScale > 0.01 && horizontalBaseScale <= 0.0125) {
//                                chunk.setBlockState(blockPos, Blocks.WHITE_STAINED_GLASS.defaultBlockState(), false);
//                            } else if (horizontalBaseScale > 0.0125 && horizontalBaseScale <= 0.015) {
//                                chunk.setBlockState(blockPos, Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), false);
//                            }
                        }

                    }
//                    int yLevelNoise1 = 0;
//                    int yLevelNoise2 = 10;
//                    int yLevelNoise3 = 20;
//                    double scale = 0.001;
//                    double thresholdx = 0.2;
//
//                    // Calculate noise for the first layer
//                    double noiseValue1 = globalHOffsetNoise.getValue((startX + x) * scale, (startZ + z) * scale, false);
//                    BlockPos blockPos1 = new BlockPos(startX + x, yLevelNoise1, startZ + z);
//                    if (noiseValue1 > thresholdx) {
//                        chunk.setBlockState(blockPos1, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
//                    }
//
//                    // Calculate noise for the second layer
//                    double noiseValue2 = globalVOffsetNoise.getValue((startX + x) * scale, (startZ + z) * scale, false);
//                    BlockPos blockPos2 = new BlockPos(startX + x, yLevelNoise2, startZ + z);
//                    if (noiseValue2 > thresholdx) {
//                        chunk.setBlockState(blockPos2, Blocks.GREEN_STAINED_GLASS.defaultBlockState(), false);
//                    }
//
//                    // Calculate noise for the third layer
//                    double noiseValue3 = globalCOffsetNoise.getValue((startX + x) * scale, (startZ + z) * scale, false);
//                    BlockPos blockPos3 = new BlockPos(startX + x, yLevelNoise3, startZ + z);
//                    if (noiseValue3 > thresholdx) {
//                        chunk.setBlockState(blockPos3, Blocks.RED_STAINED_GLASS.defaultBlockState(), false);
//                    }
                }
            }

            return chunk;
        });
    }
    private double cyclicDensity(int y, double cycleHeight) {
        double normalizedY = (y % cycleHeight) / cycleHeight;
        if (normalizedY < 0.8) {
            return Math.sin((normalizedY / 0.8) * (Math.PI / 2)); // Rising part
        } else {
            return 1 - (normalizedY - 0.8) / 0.2; // Falling part
        }
    }

    private double edgeGradient(double y, double worldHeight, double noiseValue) {
        double gradientBottom = 1.0;
        double gradientTop = 1.0;

        // Bottom up gradient density.. deleter thing... from 0 to 32
        if (y <= 32) {
            gradientBottom = y / 32.0;
        }

        // Same thing from the top
        if (y >= (worldHeight - 64)) {
            gradientTop = (worldHeight - y) / 64;
        }

        return gradientBottom * gradientTop * noiseValue;
    }

    private double globalHorizontalScaleOffset(double min, double max, double x, double z) {
        double noiseValue = globalHOffsetNoise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }

    private double globalVerticalScaleOffset(double min, double max, double x, double z) {
        double noiseValue = globalVOffsetNoise.getValue(x, z, true);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }

    private double globalCycleOffset(double min, double max, double x, double z) {
        double noiseValue = globalCOffsetNoise.getValue(x, z, false);
        return (min + (max - min) * ((noiseValue + 1) / 2));
    }

    private double globalThresholdOffset(double min, double max, double x, double z) {
        double noiseValue = globalCOffsetNoise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }
}
