package com.thebeyond.common.chunk_generators;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
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
    private final SimplexNoise simplexNoise;
    private final PerlinSimplexNoise globalHOffsetNoise;
    private final PerlinSimplexNoise globalVOffsetNoise;
    private final PerlinSimplexNoise globalCOffsetNoise;
    private final double worldHeight = 192;

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
            int sizeY = 160;  // Original height before shifting
            int sizeZ = 16;
            int terrainYOffset = 32;  // Raise terrain by 32 blocks

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
                    int globalX = startX + x;
                    int globalZ = startZ + z;

                    // 2D noise for y=0 layer with noteblocks
                        double auroraNoise = simplexNoise.getValue(globalX * 0.1, globalZ * 0.1);  // Generate 2D noise

                        // Layer at y=0
                        if (auroraNoise > 0.0) {
                            chunk.setBlockState(new BlockPos(globalX, 20, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), false);
                            chunk.setBlockState(new BlockPos(globalX, 21, globalZ), BeyondBlocks.AURORACITE.get().defaultBlockState(), false);

                        }

                    // Continue generating terrain noise for other Y levels, starting from y=32
                    for (int y = 1; y < sizeY; y++) {  // Skip y=0 and y=1 since they're handled separately
                        int shiftedY = y + terrainYOffset;  // Shift terrain by 32 blocks upwards

                        //min = 0.005, max = 0.015
                        horizontalBaseScale = globalNoiseOffset(0.005, 0.015, globalX * 0.000001, globalZ * 0.000001, globalHOffsetNoise);
                        verticalBaseScale = globalNoiseOffset(0.005, 0.015, globalX * 0.00001, globalZ * 0.00001, globalVOffsetNoise);
                        cycleHeight = globalNoiseOffset(10, 100, globalX * 0.0001, globalZ * 0.0001, globalCOffsetNoise);
                        threshold = globalNoiseOffset(0.01, 0.6, globalX * 0.0002, globalZ * 0.0002, globalCOffsetNoise);

                        double noiseValue = 0.0;
                        double amplitude = 1.0;
                        double frequency = 1.0;
                        double maxAmplitude = 0.0;

                        // Create basic noise stuff
                        for (int octave = 0; octave < numOctaves; octave++) {
                            double hScale = horizontalBaseScale * frequency;
                            double vScale = verticalBaseScale * frequency;
                            double octaveNoise = simplexNoise.getValue(globalX * hScale, shiftedY * vScale, globalZ * hScale);

                            noiseValue += octaveNoise * amplitude;
                            maxAmplitude += amplitude;

                            amplitude *= persistence;
                            frequency *= lacunarity;
                        }

                        // Normalize
                        noiseValue /= maxAmplitude;

                        // Apply sine wave gaps
                        double densityModifier = cyclicDensity(shiftedY, cycleHeight);
                        noiseValue *= densityModifier;

                        BlockPos blockPos = new BlockPos(globalX, shiftedY, globalZ);
                        double finalValue = edgeGradient(blockPos.getY(), worldHeight, noiseValue);
                        if (finalValue > threshold) {
                            chunk.setBlockState(blockPos, Blocks.END_STONE.defaultBlockState(), false);
                        }
                    }
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

        // Adjust the bottom gradient to start at y=32 and go up to y=64
        if (y <= 64) {  // Adjust the gradient range to be between 32 and 64
            gradientBottom = (y - 32) / 32.0;  // Normalize the value to a range of 0 to 1 from y=32 to y=64
            if (y < 32) {
                gradientBottom = 0.0;  // Below y=32, the gradient should be 0
            }
        }

        // Same thing from the top
        if (y >= (worldHeight - 64)) {
            gradientTop = (worldHeight - y) / 64.0;
        }

        return gradientBottom * gradientTop * noiseValue;
    }

    private double globalNoiseOffset(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double noiseValue = noise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        //info.add("TerrainNoise T: " + decimalformat.format(simplexNoise.getValue(pos.getX(), pos.getY(), pos.getZ())) + " HS: " + decimalformat.format(horizontalBaseScale) + " VS: " + decimalformat.format(verticalBaseScale) + " Threshold: " + decimalformat.format(threshold) + " CH: " + decimalformat.format(cycleHeight));
    }
}
