package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.util.HashSimplexNoise;
import com.thebeyond.util.WorldSeedHolder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.thebeyond.mixin.SinglePoolElementAccessor;
import com.thebeyond.mixin.StructureTemplateAccessor;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import java.text.DecimalFormat;
import java.util.*;
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
    public static volatile HashSimplexNoise simplexNoise;
    public static volatile HashSimplexNoise biomeSimplexNoise;
    public static volatile HashSimplexNoise warpZSimplexNoise;
    public static volatile PerlinSimplexNoise globalHOffsetNoise;
    public static volatile PerlinSimplexNoise globalVOffsetNoise;
    public static volatile PerlinSimplexNoise globalCOffsetNoise;
    private double islandRadius = 75.0;
    private double buffer = 700.0;
    public static long seed = 1;

    static final double DEFAULT_WORLD_HEIGHT = 192;

    private static final int BASELINE_DIM_RANGE = 256;

    public static double getWorldHeight() {
        int dimMinY = BeyondTerrainState.getDimMinY();
        int dimMaxY = BeyondTerrainState.getDimMaxY();
        int dimRange = dimMaxY - dimMinY;
        if (dimRange <= BASELINE_DIM_RANGE) return DEFAULT_WORLD_HEIGHT;
        return DEFAULT_WORLD_HEIGHT * ((double) dimRange / BASELINE_DIM_RANGE);
    }

    private static final int NUM_OCTAVES = 4;
    private static final double LACUNARITY = 2.0;
    private static final double PERSISTENCE = 0.5;
    private static final int TERRAIN_Y_OFFSET = 32;
    /** MEASURED ceiling on |normalizedNoise| (peak ≈0.979 over 50M+ samples), not analytic — never lower past the measured peak. */
    static final double MAX_NOISE = 1.20;

    private static final double[] OCTAVE_WRAP_FACTORS = {1.00, 0.91, 0.83, 0.77};

    static int pingPongWrap(int input, int min, int max) {
        int range = max - min;
        int wrap = range * 2;

        int x = (input - min) % wrap;
        if (x < 0) x += wrap;

        if (x > range) {
            x = wrap - x;
        }

        return x + min;
    }

    public static volatile BeyondTerrainParams activeTerrainParams = BeyondTerrainParams.DEFAULTS;

    static final int WRAP_RANGE = BeyondTerrainParams.DEFAULTS.wrapRange();

    /** Single source of truth for the wrap+warp transform — all density/biome/heightmap queries MUST route through this. */
    public static long computeWrappedCoords(int globalX, int globalZ) {
        return computeWrappedCoords(globalX, globalZ, activeTerrainParams);
    }

    public static long computeWrappedCoords(int globalX, int globalZ, BeyondTerrainParams params) {
        int wrapRange = params.wrapRange();

        HashSimplexNoise snoise = simplexNoise;
        HashSimplexNoise zsnoise = warpZSimplexNoise;
        if (snoise == null || zsnoise == null) {
            int rawX = pingPongWrap(globalX, -wrapRange, wrapRange);
            int rawZ = pingPongWrap(globalZ, -wrapRange, wrapRange);
            return ((long) rawX << 32) | ((long) rawZ & 0xFFFFFFFFL);
        }
        int warpedInputX;
        int warpedInputZ;
        if (warpDisabled) {
            warpedInputX = globalX;
            warpedInputZ = globalZ;
        } else {
            double warpScale = params.warpScale();
            double warpAmplitude = params.warpAmplitude();
            double warpInX = globalX * warpScale;
            double warpInZ = globalZ * warpScale;
            double warpX = snoise.getValue(warpInX, warpInZ) * warpAmplitude;
            double warpZ = zsnoise.getValue(warpInX, warpInZ) * warpAmplitude;
            warpedInputX = (int) (globalX + warpX);
            warpedInputZ = (int) (globalZ + warpZ);
        }
        int wrappedX;
        int wrappedZ;
        if (wrapDisabled) {
            wrappedX = warpedInputX;
            wrappedZ = warpedInputZ;
        } else {
            wrappedX = pingPongWrap(warpedInputX, -wrapRange, wrapRange);
            wrappedZ = pingPongWrap(warpedInputZ, -wrapRange, wrapRange);
        }
        return ((long) wrappedX << 32) | ((long) wrappedZ & 0xFFFFFFFFL);
    }

    public static int unpackWrappedX(long packed) { return (int) (packed >> 32); }
    public static int unpackWrappedZ(long packed) { return (int) packed; }

    /** Coords and scales MUST be paired index-wise or the octave anti-cancellation invariant breaks. */
    private static void computeOctaveFields(
            int globalX, int globalZ,
            BeyondTerrainParams params,
            double[] hScales, double[] vScales,
            int[] wrappedXs, int[] wrappedZs,
            PerlinSimplexNoise hNoise, PerlinSimplexNoise vNoise) {
        int baseWrapRange = params.wrapRange();
        double warpScale = params.warpScale();
        double warpAmplitude = params.warpAmplitude();

        HashSimplexNoise snoise = simplexNoise;
        HashSimplexNoise zsnoise = warpZSimplexNoise;
        int warpedInputX;
        int warpedInputZ;
        if (snoise == null || zsnoise == null || warpDisabled) {
            warpedInputX = globalX;
            warpedInputZ = globalZ;
        } else {
            double warpInX = globalX * warpScale;
            double warpInZ = globalZ * warpScale;
            double warpX = snoise.getValue(warpInX, warpInZ) * warpAmplitude;
            double warpZ = zsnoise.getValue(warpInX, warpInZ) * warpAmplitude;
            warpedInputX = (int) (globalX + warpX);
            warpedInputZ = (int) (globalZ + warpZ);
        }

        double frequency = 1.0;
        for (int k = 0; k < NUM_OCTAVES; k++) {
            // Floor at MIN_WRAP_RANGE: guards a datapack's minimal wrap_range × 0.77 falling below the validation floor.
            int wrapRange_k = Math.max(
                    BeyondTerrainParams.MIN_WRAP_RANGE,
                    (int) (baseWrapRange * OCTAVE_WRAP_FACTORS[k]));

            int wrappedX;
            int wrappedZ;
            if (wrapDisabled) {
                wrappedX = warpedInputX;
                wrappedZ = warpedInputZ;
            } else {
                wrappedX = pingPongWrap(warpedInputX, -wrapRange_k, wrapRange_k);
                wrappedZ = pingPongWrap(warpedInputZ, -wrapRange_k, wrapRange_k);
            }
            wrappedXs[k] = wrappedX;
            wrappedZs[k] = wrappedZ;

            Double hOverride = hScaleOverride;
            Double vOverride = vScaleOverride;
            double hBase = (hOverride != null) ? hOverride : getHorizontalBaseScale(wrappedX, wrappedZ, hNoise);
            double vBase = (vOverride != null) ? vOverride : getVerticalBaseScale(wrappedX, wrappedZ, vNoise);
            hScales[k] = hBase * frequency;
            vScales[k] = vBase * frequency;

            frequency *= LACUNARITY;
        }
    }

    public BeyondEndChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
    }

    public void computeNoisesIfNotPresent(RandomState randomState) {
        if (simplexNoise == null || biomeSimplexNoise == null || warpZSimplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {
            long worldSeed = ((WorldSeedHolder) (Object) randomState).the_Beyond$getWorldSeed();
            computeNoisesIfNotPresent(worldSeed);
        }
    }

    public void computeNoisesIfNotPresent(long worldSeed) {
        if (simplexNoise == null || biomeSimplexNoise == null || warpZSimplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {
            synchronized (BeyondEndChunkGenerator.class) {
                if (simplexNoise == null || biomeSimplexNoise == null || warpZSimplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {

                    seed = worldSeed;
                    // Sequential worldSeed offsets, next free = +6 — never reuse a prior offset.
                    RandomSource random1 = RandomSource.create(worldSeed);
                    RandomSource random2 = RandomSource.create(worldSeed + 1);
                    RandomSource random3 = RandomSource.create(worldSeed + 2);
                    RandomSource random4 = RandomSource.create(worldSeed + 3);
                    RandomSource random5 = RandomSource.create(worldSeed + 4);
                    RandomSource random6 = RandomSource.create(worldSeed + 5);

                    simplexNoise = new HashSimplexNoise(random1);
                    globalHOffsetNoise = new PerlinSimplexNoise(random2, Collections.singletonList(1));
                    globalVOffsetNoise = new PerlinSimplexNoise(random3, Collections.singletonList(1));
                    globalCOffsetNoise = new PerlinSimplexNoise(random4, Collections.singletonList(1));
                    biomeSimplexNoise = new HashSimplexNoise(random5);
                    warpZSimplexNoise = new HashSimplexNoise(random6);
                }
            }
        }
    }

    public static void resetNoises() {
        synchronized (BeyondEndChunkGenerator.class) {
            simplexNoise = null;
            biomeSimplexNoise = null;
            warpZSimplexNoise = null;
            globalHOffsetNoise = null;
            globalVOffsetNoise = null;
            globalCOffsetNoise = null;
            activeTerrainParams = BeyondTerrainParams.DEFAULTS;
            seed = 1;
        }
    }

    /** Falls back to piece bboxes (fail-soft) if absent in a rare disk-loaded window. */
    static volatile StructureTemplateManager the_beyond$templateManager;

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
        computeNoisesIfNotPresent(structureState.getLevelSeed());
        BeyondTerrainStateInternal.setDimBounds(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        the_beyond$templateManager = structureTemplateManager;
        super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);

        // Vanilla only references starts within ±8 chunks; seed the mask here so a wide carve structure's far arm doesn't cut straight.
        if (BeyondTerrainState.isActive()) {
            try {
                var structReg = registryAccess.registryOrThrow(Registries.STRUCTURE);
                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (start == null || !start.isValid()) continue;
                    if (the_beyond$carver.the_beyond$maskCache.containsKey(start)) continue;   // already seeded
                    ResourceLocation key = structReg.getKey(start.getStructure());
                    StructureIntegrationProfile profile = BeyondForeignStructureProfiles.resolve(start.getStructure(), key);
                    if (profile == null || !profile.carve()) continue;      // only carve structures need far reach
                    boolean layerDistributed = BeyondForeignStructureProfiles.isLayerDistributed(key, start.getChunkPos().toLong());
                    boolean distributed = layerDistributed
                            || BeyondForeignStructureProfiles.isAutoSeatedProjected(key);
                    boolean seated = distributed || profile.anchor() == StructureIntegrationProfile.Anchor.SEATED;
                    CarveMask m = the_beyond$carver.the_beyond$buildMask(start, seated, distributed, layerDistributed, profile.flushTolerance());
                    m.carveOnly = BeyondForeignStructureProfiles.isEmbedded(key);
                    the_beyond$carver.the_beyond$maskCache.put(start, m);
                    if (distributed
                            && BeyondGenDiagnostics.loggedFootingSuppressed.add(System.identityHashCode(start))
                            && BeyondGenDiagnostics.loggedFootingSuppressed.size() <= 40) {
                        com.thebeyond.TheBeyond.LOGGER.info(
                                "[Beyond] {} @chunk [{},{}] DISTRIBUTED ({}) -> footing-ONLY seat CONFINED to start_platform base"
                                + " footprint {} (organic lip OFF): minimal base seat, NO terraced welds, NO bridges under"
                                + " cantilever houses / walkways / ship. F3 a former weld cell -> now AIR (was END_STONE, T < Threshold).",
                                key, start.getChunkPos().x, start.getChunkPos().z,
                                layerDistributed ? "layer re-anchored" : "topmost-projected",
                                m.baseBox == null ? "<none>" : ("[" + m.baseBox.minX() + "," + m.baseBox.minZ() + ".." + m.baseBox.maxX() + "," + m.baseBox.maxZ() + "]"));
                    }
                }
            } catch (Throwable ignored) {
                // fail-soft: a seed miss only reverts to the reference-based (near-chunk) discovery
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        computeNoisesIfNotPresent(randomState);
        BeyondTerrainStateInternal.setDimBounds(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        return super.createBiomes(randomState, blender, structureManager, chunk);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        computeNoisesIfNotPresent(randomState);
        BeyondTerrainStateInternal.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        float distanceFromOrigin = (float) Math.sqrt((double) x * x + (double) z * z);

        ColumnScratch scratch = SCRATCH.get();
        initColumnScratch(x, z, distanceFromOrigin, scratch);

        // scanTop must track generateEndTerrain's fill range (dimMaxY-33): a pancake can sit anywhere below it.
        int scanTop = level.getMaxBuildHeight() - 33;
        for (int y = scanTop; y >= level.getMinBuildHeight(); y--) {
            if (isSolidTerrainScratch(y, scratch)) {
                return y;
            }
        }

        return level.getMinBuildHeight();
    }
    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        computeNoisesIfNotPresent(random);
        return Math.max(this.getBaseHeight(x, z, type, level, random), level.getMinBuildHeight());
    }

    @Override
    public int getMinY() {
        return 10;
    }

    // Density-path callers MUST pass WRAPPED coords (mismatch causes streaks at |X| >= ~500k); BiomeSource
    // intentionally passes grid coords — the biome field doesn't need wrapping.
    public static double getHorizontalBaseScale(int x, int z) {
        return getHorizontalBaseScale(x, z, globalHOffsetNoise);
    }
    public static double getHorizontalBaseScale(int x, int z, PerlinSimplexNoise noise) {
        double nx = x * 0.000001;
        double nz = z * 0.000001;
        if (hScaleLocalWrap) {
            return globalNoiseOffsetLocalWrap(0.005, 0.015, HSCALE_LOCAL_WRAP_RANGE, x, z, noise);
        }
        if (hScaleDistanceAdaptive) {
            return globalNoiseOffsetDistanceAdaptive(0.005, 0.015, HSCALE_REF_RADIUS, x, z, noise);
        }
        if (hScaleUseHashNoise) {
            HashSimplexNoise hashNoise = biomeSimplexNoise;
            if (hashNoise != null) {
                double v = hashNoise.getValue(nx, nz);
                return 0.005 + (0.015 - 0.005) * ((v + 1.0) * 0.5);
            }
        }
        if (hScaleBlur3x3) {
            return globalNoiseOffsetBlur3x3(0.005, 0.015, nx, nz, noise);
        }
        if (hScaleMultirotation) {
            return globalNoiseOffsetMultirotation(0.005, 0.015, nx, nz, noise);
        }
        return globalNoiseOffset(0.005, 0.015, nx, nz, noise);
    }

    public static double getVerticalBaseScale(int x, int z) {
        return getVerticalBaseScale(x, z, globalVOffsetNoise);
    }
    public static double getVerticalBaseScale(int x, int z, PerlinSimplexNoise noise) {
        return globalNoiseOffset(0.005, 0.015, x * 0.00001, z * 0.00001, noise);
    }

    @VisibleForTesting
    static volatile Double cycleHeightOverride = null;

    @VisibleForTesting
    static volatile double cycleHeightFrequencyMultiplier = 1.0;

    @VisibleForTesting
    static volatile boolean wrapDisabled = false;

    @VisibleForTesting
    static volatile boolean warpDisabled = false;

    @VisibleForTesting
    static volatile Double hScaleOverride = null;

    @VisibleForTesting
    static volatile Double vScaleOverride = null;

    @VisibleForTesting
    static volatile boolean hScaleMultirotation = false;

    @VisibleForTesting
    static volatile boolean hScaleUseHashNoise = false;

    @VisibleForTesting
    static volatile boolean hScaleBlur3x3 = false;

    @VisibleForTesting
    static final double HSCALE_REF_RADIUS = 100_000.0;

    @VisibleForTesting
    static volatile boolean hScaleDistanceAdaptive = false;

    @VisibleForTesting
    static final int HSCALE_LOCAL_WRAP_RANGE = 50_000;

    @VisibleForTesting
    static volatile boolean hScaleLocalWrap = false;

    @VisibleForTesting
    static volatile boolean useIslandEnvelope = false;

    @VisibleForTesting
    static volatile double islandEnvelopeHScale = 0.010;

    @VisibleForTesting
    static volatile double envelopeLow  = 0.5;
    @VisibleForTesting
    static volatile double envelopeHigh = 1.5;

    @VisibleForTesting
    static volatile double envelopeScale = 5e-7;

    public static double getIslandEnvelope(int x, int z) {
        PerlinSimplexNoise noise = globalHOffsetNoise;
        if (noise == null) return 1.0;
        double nx = x * envelopeScale;
        double nz = z * envelopeScale;
        double v = noise.getValue(nx, nz, false);
        return envelopeLow + (envelopeHigh - envelopeLow) * ((v + 1.0) * 0.5);
    }

    // Band-blend: 2-sample lerp over fixed-frequency bands instead of X*hScale(X,Z) — constant h_i removes the
    // X*dh/dX streak term, smoothstep blend avoids band seams.
    private static final int BB_BAND_COUNT = 17;
    private static final double[] BB_BAND_FREQUENCIES = new double[BB_BAND_COUNT];
    private static final double BB_LOG_H_MIN;
    private static final double BB_INV_LOG_STEP;

    static {
        double logMin = Math.log(0.005);
        double logMax = Math.log(0.015);
        BB_LOG_H_MIN    = logMin;
        BB_INV_LOG_STEP = (BB_BAND_COUNT - 1) / (logMax - logMin);
        double step = (logMax - logMin) / (BB_BAND_COUNT - 1);
        for (int i = 0; i < BB_BAND_COUNT; i++) {
            BB_BAND_FREQUENCIES[i] = Math.exp(logMin + i * step);
        }
    }

    @VisibleForTesting
    static volatile boolean useBandBlend = true;

    static void computeBBState(double[] hScales, int[] bbLoIdx, double[] bbT) {
        double frequencyMult = 1.0;
        for (int k = 0; k < NUM_OCTAVES; k++) {
            double hBaseTarget = hScales[k] / frequencyMult;

            double fIdx = (Math.log(hBaseTarget) - BB_LOG_H_MIN) * BB_INV_LOG_STEP;
            int lo;
            double t;
            if (fIdx <= 0.0) {
                lo = 0;
                t = 0.0;
            } else if (fIdx >= BB_BAND_COUNT - 1) {
                lo = BB_BAND_COUNT - 2;
                t = 1.0;
            } else {
                lo = (int) fIdx;
                t = fIdx - lo;
            }

            bbLoIdx[k] = lo;
            bbT[k] = t * t * (3.0 - 2.0 * t);   // smoothstep

            frequencyMult *= LACUNARITY;
        }
    }

    public static double getCycleHeight(int x, int z) {
        Double override = cycleHeightOverride;
        if (override != null) return override;
        return getCycleHeight(x, z, globalCOffsetNoise);
    }
    public static double getCycleHeight(int x, int z, PerlinSimplexNoise noise) {
        Double override = cycleHeightOverride;
        if (override != null) return override;
        // Period 1M blocks keeps cyclicDensity's divisor discontinuities from piling up in one scene.
        double freq = 0.00005 * cycleHeightFrequencyMultiplier;
        return globalNoiseOffset(5, 100, x * freq, z * freq, noise);
    }


    public static double getThreshold(int x, int z, float distanceFromOrigin) {
        double baseThreshold = globalNoiseOffset(0.01, 0.6, x * 0.0002, z * 0.0002, globalCOffsetNoise);

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
        return getTerrainDensity(globalX, globalY, globalZ, activeTerrainParams);
    }

    public static double getTerrainDensity(int globalX, int globalY, int globalZ, BeyondTerrainParams paramsOverride) {
        PerlinSimplexNoise hNoise = globalHOffsetNoise;
        PerlinSimplexNoise vNoise = globalVOffsetNoise;
        PerlinSimplexNoise cNoise = globalCOffsetNoise;

        double[] hScales = new double[NUM_OCTAVES];
        double[] vScales = new double[NUM_OCTAVES];
        int[] wrappedXs = new int[NUM_OCTAVES];
        int[] wrappedZs = new int[NUM_OCTAVES];
        computeOctaveFields(globalX, globalZ, paramsOverride,
                hScales, vScales, wrappedXs, wrappedZs, hNoise, vNoise);

        if (useIslandEnvelope) {
            double h = islandEnvelopeHScale;
            double freq = 1.0;
            for (int k = 0; k < NUM_OCTAVES; k++) {
                hScales[k] = h * freq;
                freq *= LACUNARITY;
            }
        }

        double cycleHeight = getCycleHeight(wrappedXs[0], wrappedZs[0], cNoise);

        double density = getTerrainDensity(globalY, hScales, vScales, cycleHeight, wrappedXs, wrappedZs);

        if (useIslandEnvelope) {
            density *= getIslandEnvelope(globalX, globalZ);
        }

        return density;
    }

    public static double getTerrainDensity(
            int globalY,
            double[] hScales,
            double[] vScales,
            double cycleHeight,
            int[] wrappedXs,
            int[] wrappedZs) {
        final int dimMinY = BeyondTerrainState.getDimMinY();
        final int dimMaxY = BeyondTerrainState.getDimMaxY();
        if (useBandBlend) {
            int[] bbLoIdx = new int[NUM_OCTAVES];
            double[] bbT = new double[NUM_OCTAVES];
            computeBBState(hScales, bbLoIdx, bbT);
            return getTerrainDensity(globalY, hScales, vScales, cycleHeight,
                    wrappedXs, wrappedZs, bbLoIdx, bbT, dimMinY, dimMaxY);
        }
        return getTerrainDensity(globalY, hScales, vScales, cycleHeight,
                wrappedXs, wrappedZs, null, null, dimMinY, dimMaxY);
    }

    public static double getTerrainDensity(
            int globalY,
            double[] hScales,
            double[] vScales,
            double cycleHeight,
            int[] wrappedXs,
            int[] wrappedZs,
            int[] bbLoIdx,
            double[] bbT,
            int dimMinY,
            int dimMaxY) {
        int shiftedY = globalY + TERRAIN_Y_OFFSET;
        double normalizedNoise = computeNormalizedNoise(shiftedY, hScales, vScales, wrappedXs, wrappedZs, bbLoIdx, bbT);
        double densityModifier = cyclicDensity(shiftedY, cycleHeight);
        return edgeGradient(shiftedY, normalizedNoise * densityModifier, dimMinY, dimMaxY);
    }

    /** Factored out so the hot-loop air-gap skip can avoid the 8 (band-blend) / 4 simplex evals entirely. */
    static double computeNormalizedNoise(int shiftedY, double[] hScales, double[] vScales,
            int[] wrappedXs, int[] wrappedZs, int[] bbLoIdx, double[] bbT) {
        HashSimplexNoise noise = simplexNoise;

        double noiseValue = 0.0;
        double amplitude = 1.0;
        double maxAmplitude = 0.0;

        if (useBandBlend) {
            double frequencyMult = 1.0;

            for (int octave = 0; octave < NUM_OCTAVES; octave++) {
                double vScale = vScales[octave];
                double sampleY = shiftedY * vScale;

                int lo = bbLoIdx[octave];
                double tSmooth = bbT[octave];

                double hLo = BB_BAND_FREQUENCIES[lo]     * frequencyMult;
                double hHi = BB_BAND_FREQUENCIES[lo + 1] * frequencyMult;
                double wx = wrappedXs[octave];
                double wz = wrappedZs[octave];
                double sLo = noise.getValue(wx * hLo, sampleY, wz * hLo);
                double sHi = noise.getValue(wx * hHi, sampleY, wz * hHi);
                double octaveNoise = sLo + tSmooth * (sHi - sLo);

                noiseValue += octaveNoise * amplitude;
                maxAmplitude += amplitude;
                amplitude *= PERSISTENCE;
                frequencyMult *= LACUNARITY;
            }
        } else {
            for (int octave = 0; octave < NUM_OCTAVES; octave++) {
                double hScale = hScales[octave];
                double vScale = vScales[octave];

                double sampleX = wrappedXs[octave] * hScale;
                double sampleY = shiftedY * vScale;
                double sampleZ = wrappedZs[octave] * hScale;

                double octaveNoise = noise.getValue(sampleX, sampleY, sampleZ);
                noiseValue += octaveNoise * amplitude;
                maxAmplitude += amplitude;

                amplitude *= PERSISTENCE;
            }
        }

        return noiseValue / maxAmplitude;
    }

    static final double AIR_SENTINEL = Double.NEGATIVE_INFINITY;

    /** Skips the noise evals only when max possible density (ef*cyc*MAX_NOISE) plus {@code beardCap} still can't
     *  reach {@code threshold} — beardCap must be +INF wherever a beard/carve touches the chunk, or this isn't bit-exact. */
    static double terrainDensityOrSkip(int globalY, ColumnScratch s, double cycleHeight, double threshold, double beardCap) {
        int shiftedY = globalY + TERRAIN_Y_OFFSET;
        double ef = edgeGradientFactor(shiftedY, s.dimMinY, s.dimMaxY);
        if (ef <= 0.0) return AIR_SENTINEL;
        double cyc = cyclicDensity(shiftedY, cycleHeight);
        if (ef * cyc * MAX_NOISE + beardCap <= threshold) return AIR_SENTINEL;
        double nv = computeNormalizedNoise(shiftedY, s.hScales, s.vScales, s.wrappedXs, s.wrappedZs,
                useBandBlend ? s.bbLoIdx : null, useBandBlend ? s.bbT : null);
        return ef * (nv * cyc);
    }

    public static final class ColumnScratch {
        public final double[] hScales = new double[NUM_OCTAVES];
        public final double[] vScales = new double[NUM_OCTAVES];
        public final int[] wrappedXs = new int[NUM_OCTAVES];
        public final int[] wrappedZs = new int[NUM_OCTAVES];
        public final int[] bbLoIdx = new int[NUM_OCTAVES];
        public final double[] bbT = new double[NUM_OCTAVES];
        public double cycleHeight;
        public double threshold;
        public double islandEnvelope;
        public int dimMinY;
        public int dimMaxY;
    }

    static final ThreadLocal<ColumnScratch> SCRATCH =
            ThreadLocal.withInitial(ColumnScratch::new);
    /** Kept distinct from {@link #SCRATCH} so a roof probe never clobbers the hot loop's own column state. */
    static final ThreadLocal<ColumnScratch> PROBE_SCRATCH =
            ThreadLocal.withInitial(ColumnScratch::new);
    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    public static ColumnScratch getColumnScratch() {
        return SCRATCH.get();
    }

    public static void initColumnScratch(int globalX, int globalZ, float distanceFromOrigin, ColumnScratch s) {
        PerlinSimplexNoise hNoise = globalHOffsetNoise;
        PerlinSimplexNoise vNoise = globalVOffsetNoise;
        PerlinSimplexNoise cNoise = globalCOffsetNoise;

        computeOctaveFields(globalX, globalZ, activeTerrainParams,
                s.hScales, s.vScales, s.wrappedXs, s.wrappedZs, hNoise, vNoise);

        if (useIslandEnvelope) {
            double h = islandEnvelopeHScale;
            double freq = 1.0;
            for (int k = 0; k < NUM_OCTAVES; k++) {
                s.hScales[k] = h * freq;
                freq *= LACUNARITY;
            }
        }

        if (useBandBlend) {
            computeBBState(s.hScales, s.bbLoIdx, s.bbT);
        }

        s.cycleHeight = getCycleHeight(s.wrappedXs[0], s.wrappedZs[0], cNoise);
        s.threshold = getThreshold(s.wrappedXs[0], s.wrappedZs[0], distanceFromOrigin);
        s.islandEnvelope = useIslandEnvelope ? getIslandEnvelope(globalX, globalZ) : 1.0;
        s.dimMinY = BeyondTerrainState.getDimMinY();
        s.dimMaxY = BeyondTerrainState.getDimMaxY();
    }

    public static double getTerrainDensityScratch(int globalY, ColumnScratch s) {
        double density = getTerrainDensity(globalY, s.hScales, s.vScales, s.cycleHeight,
                s.wrappedXs, s.wrappedZs, useBandBlend ? s.bbLoIdx : null,
                useBandBlend ? s.bbT : null, s.dimMinY, s.dimMaxY);
        return density * s.islandEnvelope;
    }

    public static boolean isSolidTerrainScratch(int globalY, ColumnScratch s) {
        return getTerrainDensityScratch(globalY, s) > s.threshold;
    }

    public static boolean isSolidTerrain(int globalX, int globalY, int globalZ, float distanceFromOrigin) {
        long packed = computeWrappedCoords(globalX, globalZ);
        int wrappedX = unpackWrappedX(packed);
        int wrappedZ = unpackWrappedZ(packed);
        double threshold = getThreshold(wrappedX, wrappedZ, distanceFromOrigin);
        double density = getTerrainDensity(globalX, globalY, globalZ);

        return density > threshold;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        computeNoisesIfNotPresent(randomState);
        BeyondTerrainStateInternal.setDimBounds(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();

            List<StructureStart> validStarts = null;
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (start.isValid()) {
                    if (validStarts == null) validStarts = new ArrayList<>(4);
                    validStarts.add(start);
                }
            }
            if (validStarts == null) validStarts = Collections.emptyList();

            Beardifier beard = Beardifier.forStructuresInChunk(structureManager, chunkPos);
            List<CarveMask> carveMasks = the_beyond$collectCarveMasks(structureManager, chunkPos);

            // Air-gap skip is only safe where nothing can lift a gap cell to solid (Beardifier lift or a carve
            // mask); either present here -> never skip (beardCap=+INF), so density stays bit-exact.
            double chunkBeardCap = Double.POSITIVE_INFINITY;
            if (carveMasks.isEmpty()
                    && !structureManager.startsForStructure(chunkPos,
                            p -> p.terrainAdaptation() != TerrainAdjustment.NONE).iterator().hasNext()) {
                chunkBeardCap = 0.0;
            }

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int globalX = startX + x;
                    int globalZ = startZ + z;

                    float distanceFromOrigin = (float) Math.sqrt((double) globalX * globalX + (double) globalZ * globalZ);

                    if (distanceFromOrigin <= islandRadius + 50) {
                        generateMainIsland(chunk, globalX, globalZ, distanceFromOrigin, islandRadius);
                    }

                    else if (distanceFromOrigin >= 650) {
                      generateEndTerrain(chunk, globalX, globalZ, distanceFromOrigin, validStarts, beard, carveMasks, chunkBeardCap);
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
        BlockPos.MutableBlockPos mutable = MUTABLE.get();

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

            if ((double) globalX * globalX + (double) y * y + (double) globalZ * globalZ <= islandRadius * islandRadius * (0.5 + 0.5 * finalNoise) * threshold) {
                chunk.setBlockState(mutable.set(globalX, y + 20, globalZ), Blocks.END_STONE.defaultBlockState(), false);
            }
        }
    }

    private void generateFarlands(ChunkAccess chunk, int globalX, int globalZ) {

    }

    private void generateEndTerrain(ChunkAccess chunk, int globalX, int globalZ, float distanceFromOrigin, List<StructureStart> validStarts, Beardifier beard, List<CarveMask> carveMasks, double beardCap) {
        ColumnScratch s = SCRATCH.get();
        initColumnScratch(globalX, globalZ, distanceFromOrigin, s);
        double cycleHeight = s.cycleHeight;
        double baseThreshold = s.threshold;

        BlockPos.MutableBlockPos mutable = MUTABLE.get();

        final int dimMinY = BeyondTerrainState.getDimMinY();
        final int dimMaxY = BeyondTerrainState.getDimMaxY();
        final boolean bb = useBandBlend;
        final int[] bbLo = bb ? s.bbLoIdx : null;
        final double[] bbT = bb ? s.bbT : null;
        final BeardCtx bc = new BeardCtx();
        bc.x = globalX;
        bc.z = globalZ;
        final BeyondStructureCarver.ColumnCarveState carve =
                BeyondStructureCarver.beginColumn(carveMasks, globalX, globalZ, s);
        // Embedded (crashed ships): suppress the vanilla beard_thin lift over the ship bbox (+margin) so the island
        // buries the sunk hull instead of a platform lifting it.
        boolean suppressBeard = false;
        for (int mi = 0; mi < carveMasks.size(); mi++) {
            CarveMask m = carveMasks.get(mi);
            if (m.carveOnly && globalX >= m.oX - 16 && globalX < m.oX + m.w + 16
                    && globalZ >= m.oZ - 16 && globalZ < m.oZ + m.d + 16) { suppressBeard = true; break; }
        }
        // loopStart/loopEnd skip the 32-block edgeGradient zero bands at top/bottom, where density is forced 0.
        final int loopStart = dimMinY + 33;
        final int loopEnd = dimMaxY - 32;
        for (int y = loopStart; y < loopEnd; y++) {
            if (carve != null && carve.foundationOrLipFillAt(y)) {
                chunk.setBlockState(mutable.set(globalX, y, globalZ), Blocks.END_STONE.defaultBlockState(), false);
                continue;
            }
            double density = terrainDensityOrSkip(y, s, cycleHeight, baseThreshold, beardCap);
            if (density == AIR_SENTINEL) continue;

            // Beard lift clamped non-negative: its gouging half is dropped, since the carve below handles clearing.
            bc.y = y;
            double beardDelta = suppressBeard ? 0.0 : Math.max(0.0, beard.compute(bc));
            double baseBeardDelta = carve != null ? carve.baseBeardDeltaAt(y) : 0.0;
            double finalDensity = density + beardDelta + baseBeardDelta;

            if (finalDensity > baseThreshold) {
                if (carve != null && carve.inCarveBand(y)) {
                    double penalty = carve.carvePenaltyAt(y, s);
                    if (penalty == Double.POSITIVE_INFINITY) continue;
                    // Skirt never cuts a cell any beard fills, or it would carve holes into a beard-lifted ground connection.
                    if (penalty > 0.0 && baseBeardDelta <= 0.0 && beardDelta <= 0.0) {
                        finalDensity -= penalty;
                        if (finalDensity <= baseThreshold) continue;
                    }
                }
                chunk.setBlockState(mutable.set(globalX, y, globalZ), Blocks.END_STONE.defaultBlockState(), false);
            }
        }
    }

    // Re-exported from BeyondStructureCarver: aliases keep BeyondEndChunkGenerator.<CONST> resolving for the headless gates and FeatureGuardMixin.
    public static final int CARVE_ERODE_REACH = BeyondStructureCarver.CARVE_ERODE_REACH;
    public static final int CARVE_CEIL_REACH = BeyondStructureCarver.CARVE_CEIL_REACH;
    static final int FLOAT_VMARGIN = BeyondStructureCarver.FLOAT_VMARGIN;
    static final int FLOAT_HMARGIN = BeyondStructureCarver.FLOAT_HMARGIN;
    static final int WARP_MAX = BeyondStructureCarver.WARP_MAX;
    static final int GUARD_STUB_DEPTH = BeyondStructureCarver.GUARD_STUB_DEPTH;
    static final int GUARD_GROWTH_UP = BeyondStructureCarver.GUARD_GROWTH_UP;
    static final int CARVE_SCAN_REACH = BeyondStructureCarver.CARVE_SCAN_REACH;
    static final int CLOSE_RADIUS = BeyondStructureCarver.CLOSE_RADIUS;
    static final int FOUNDATION_DEPTH = BeyondStructureCarver.FOUNDATION_DEPTH;
    static final int FOUNDATION_MAX_LIP = BeyondStructureCarver.FOUNDATION_MAX_LIP;
    static final int FOUNDATION_MIN_RUN = BeyondStructureCarver.FOUNDATION_MIN_RUN;
    static final double CARVE_AMP = BeyondStructureCarver.CARVE_AMP;
    static final int CARVE_LAT_REACH = BeyondStructureCarver.CARVE_LAT_REACH;
    static final int CARVE_VERT_REACH = BeyondStructureCarver.CARVE_VERT_REACH;
    static final int CARVE_DIST_LAT_REACH = BeyondStructureCarver.CARVE_DIST_LAT_REACH;
    static final int CARVE_DIST_VERT_REACH = BeyondStructureCarver.CARVE_DIST_VERT_REACH;
    static final int DIST_SUPPORT_BAND = BeyondStructureCarver.DIST_SUPPORT_BAND;
    static final int FOOTING_TOL = BeyondStructureCarver.FOOTING_TOL;
    static final double CARVE_DIST_AMP = BeyondStructureCarver.CARVE_DIST_AMP;
    static final double CARVE_DIST_WARP_GAIN = BeyondStructureCarver.CARVE_DIST_WARP_GAIN;
    static final double CARVE_WARP_AMT = BeyondStructureCarver.CARVE_WARP_AMT;
    static final int DT3_REACH = BeyondStructureCarver.DT3_REACH;
    static final double DT3_WARP_AMP = BeyondStructureCarver.DT3_WARP_AMP;
    static final int DT3_CAP = BeyondStructureCarver.DT3_CAP;
    private static final double FACE_ROUGH_FREQ = BeyondStructureCarver.FACE_ROUGH_FREQ;
    static final int FACE_ROUGH = BeyondStructureCarver.FACE_ROUGH;

    /** Flattened per-column union-over-columns scratch; ThreadLocal + grown on demand so the hot loop allocates nothing after warm-up. */
    static final class CarveBits {
        int[] dxz = new int[512], lo = new int[512], hi = new int[512], gLo = new int[512];
        boolean[] seat = new boolean[512], filled = new boolean[512], dist = new boolean[512], base = new boolean[512];
        // mask = per-bit source (TRUE-3D lookup); d3 = precomputed warped distance. mask[k]==null ⇒ no 3D field.
        CarveMask[] mask = new CarveMask[512];
        int[] d3 = new int[512];
        // baseNat = per-bit connected-island top below colLo (base beard's no-float anchor), MIN if none.
        // baseFloor = the bit's start-piece floor (baseBox.minY()), the ceiling the cover is capped to.
        int[] baseNat = new int[512], baseFloor = new int[512];
        // colX/colZ = bit's own world column; buriedAbove = natural island solid sits directly on its roof
        // (hi[k]+1) — drives the FACE A island-cap KEEP. Distributed bits only.
        short[] colX = new short[512], colZ = new short[512];
        boolean[] buriedAbove = new boolean[512];
        void ensure(int cap) {
            if (cap <= dxz.length) return;
            int c = Math.max(cap, dxz.length * 2);
            dxz = java.util.Arrays.copyOf(dxz, c); lo = java.util.Arrays.copyOf(lo, c); hi = java.util.Arrays.copyOf(hi, c);
            gLo = java.util.Arrays.copyOf(gLo, c); seat = java.util.Arrays.copyOf(seat, c); filled = java.util.Arrays.copyOf(filled, c);
            dist = java.util.Arrays.copyOf(dist, c); base = java.util.Arrays.copyOf(base, c);
            mask = java.util.Arrays.copyOf(mask, c); d3 = java.util.Arrays.copyOf(d3, c);
            baseNat = java.util.Arrays.copyOf(baseNat, c); baseFloor = java.util.Arrays.copyOf(baseFloor, c);
            colX = java.util.Arrays.copyOf(colX, c); colZ = java.util.Arrays.copyOf(colZ, c);
            buriedAbove = java.util.Arrays.copyOf(buriedAbove, c);
        }
    }
    static final ThreadLocal<CarveBits> CARVE_BITS = ThreadLocal.withInitial(CarveBits::new);

    /** Per-column occupancy mask of the real lobed footprint (not the bbox), so clearing doesn't carve a rectangular cube around a sparse structure. */
    public static final class CarveMask {
        final int oX, oZ, w, d;   // footprint origin + size, in columns
        final long[] occ;          // w*d bits; set if the column lies in some piece's footprint
        final int gLo, gHi;        // overall solid Y-range across the structure's pieces (gLo = the byte Y-origin)
        // Per-column solid Y-span as offsets from gLo, not the global range — avoids a pillar down to gLo
        // for a column whose blocks only span a high lobe. 16-bit offset covers the full build height.
        final short[] colLoOff, colHiOff;
        // Columns added by enclosed-hole fill — SEATED foundation skips these (not a leg needing support). Null if none filled.
        final long[] filledOcc;
        final boolean seated;
        // distributed: re-anchored onto a lower pancake, carved like FLOATING but support kept (skirt clipped at/below base).
        // layerDistributed: re-anchored (vs surface-projected/topmost) — gates footing + organic lip.
        final boolean distributed;
        final boolean layerDistributed;
        final int foundationDepth; // SEATED: support reach below gLo (= profile.flushTolerance)
        final int filledHoles;     // diagnostic only
        // True-3D Chebyshev distance field (DISTRIBUTED only), saturating byte (0..DT3_CAP); lets the carve
        // follow the structure's real 3D shape instead of a per-column ellipse.
        final byte[] dt3;
        final int dt3OX, dt3OZ, dt3W, dt3D, dt3YLo, dt3Layers;   // padded grid origin/size (all 0 when dt3 == null)
        // Start piece's bbox: DISTRIBUTED footing/lip fire only within it, so cantilevered parts don't weld to islands they pass over.
        final BoundingBox baseBox;
        // Embedded (crashed ships): carve-only — interior cleared, no foundation/base-beard platform.
        boolean carveOnly;
        CarveMask(int oX, int oZ, int w, int d, long[] occ, short[] colLoOff, short[] colHiOff,
                  long[] filledOcc, int gLo, int gHi, boolean seated, boolean distributed, boolean layerDistributed, int foundationDepth, int filledHoles,
                  byte[] dt3, int dt3OX, int dt3OZ, int dt3W, int dt3D, int dt3YLo, int dt3Layers, BoundingBox baseBox) {
            this.oX = oX; this.oZ = oZ; this.w = w; this.d = d; this.occ = occ;
            this.colLoOff = colLoOff; this.colHiOff = colHiOff; this.filledOcc = filledOcc;
            this.gLo = gLo; this.gHi = gHi; this.seated = seated; this.distributed = distributed; this.layerDistributed = layerDistributed; this.foundationDepth = foundationDepth;
            this.filledHoles = filledHoles;
            this.dt3 = dt3; this.dt3OX = dt3OX; this.dt3OZ = dt3OZ; this.dt3W = dt3W; this.dt3D = dt3D; this.dt3YLo = dt3YLo; this.dt3Layers = dt3Layers;
            this.baseBox = baseBox;
        }
        // False when baseBox is null.
        boolean inBaseFootprint(int x, int z) {
            if (baseBox == null) return false;
            int m = BEARD_LAT_REACH;
            return x >= baseBox.minX() - m && x <= baseBox.maxX() + m
                && z >= baseBox.minZ() - m && z <= baseBox.maxZ() + m;
        }
        // Returns DT3_CAP+1 outside the padded grid or when no field exists — carve falls back to the 2.5D ellipse.
        int dt3At(int x, int y, int z) {
            if (dt3 == null) return DT3_CAP + 1;
            int i = x - dt3OX, j = z - dt3OZ;
            if (i < 0 || i >= dt3W || j < 0 || j >= dt3D) return DT3_CAP + 1;
            int L = y - dt3YLo;
            if (L < 0 || L >= dt3Layers) return DT3_CAP + 1;
            return dt3[(L * dt3D + j) * dt3W + i] & 0xFF;
        }
        boolean isFilled(int bit) { return filledOcc != null && (filledOcc[bit >> 6] & (1L << (bit & 63))) != 0; }
        int colLo(int bit) { return gLo + (colLoOff[bit] & 0xFFFF); }
        int colHi(int bit) { return gLo + (colHiOff[bit] & 0xFFFF); }
        // 16-bit offset covers the full build height: a tower stacked >255 above gLo won't clamp and go un-carved.
        static void encodeColumnSpans(long[] occ, int[] cLo, int[] cHi, int gLo, short[] colLoOff, short[] colHiOff, int n) {
            for (int bit = 0; bit < n; bit++) {
                if ((occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                int lo = cLo[bit] - gLo, hi = cHi[bit] - gLo;
                if (lo < 0) lo = 0;
                if (lo > 0xFFFF) lo = 0xFFFF;
                if (hi > 0xFFFF) hi = 0xFFFF;
                if (hi < lo) hi = lo;
                colLoOff[bit] = (short) lo;
                colHiOff[bit] = (short) hi;
            }
        }
        static byte[] buildDt3(int w, int d, int gLo, int gHi, int dt3W, int dt3D, int dt3YLo, int dt3Layers,
                               IntArrayList blockI, IntArrayList blockJ, IntArrayList blockY,
                               long[] occ, int[] cLo, int[] cHi, boolean[] hasBlock) {
            if (dt3W <= 0 || dt3D <= 0 || dt3Layers <= 0 || gHi < gLo) return null;
            int[] occ3 = new int[dt3W * dt3D * dt3Layers];
            java.util.Arrays.fill(occ3, DT3_CAP);
            boolean seeded = false;
            if (blockI != null) {
                for (int k = 0, m = blockI.size(); k < m; k++) {
                    int i = blockI.getInt(k) + DT3_CAP, j = blockJ.getInt(k) + DT3_CAP, L = blockY.getInt(k) - dt3YLo;
                    if (i < 0 || i >= dt3W || j < 0 || j >= dt3D || L < 0 || L >= dt3Layers) continue;
                    occ3[(L * dt3D + j) * dt3W + i] = 0;
                    seeded = true;
                }
            }
            // Occupied columns with no captured block: seed the whole slab to match the 2.5D model.
            int n = w * d;
            for (int bit = 0; bit < n; bit++) {
                if ((occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                if (hasBlock != null && hasBlock[bit]) continue;   // already seeded by its real blocks
                int i = (bit % w) + DT3_CAP, j = (bit / w) + DT3_CAP;
                int yLo = cLo[bit], yHi = cHi[bit];
                if (yHi < yLo) continue;
                for (int y = yLo; y <= yHi; y++) {
                    int L = y - dt3YLo;
                    if (L < 0 || L >= dt3Layers) continue;
                    occ3[(L * dt3D + j) * dt3W + i] = 0;
                    seeded = true;
                }
            }
            if (!seeded) return null;
            chebyshevDT3(occ3, dt3W, dt3D, dt3Layers, DT3_CAP);
            byte[] dt3 = new byte[occ3.length];
            for (int k = 0; k < occ3.length; k++) dt3[k] = (byte) occ3[k];
            return dt3;
        }
        // An occupied column whose solid span doesn't reach y is transparent at y (flood passes through).
        private static boolean wallAtY(long[] occ, int[] cLo, int[] cHi, int bit, int y) {
            if ((occ[bit >> 6] & (1L << (bit & 63))) == 0) return false;
            return cLo[bit] <= y && y <= cHi[bit];
        }
        // borderSource=true treats out-of-grid as a distance-0 source, so the erosion step never seals the exterior.
        private static void chebyshevDT(int[] dist, int w, int d, int cap, boolean borderSource) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    int c = x + z * w, best = dist[c];
                    if (best == 0) continue;
                    if (x > 0)              { int v = dist[c - 1] + 1;     if (v < best) best = v; }
                    if (z > 0)              { int v = dist[c - w] + 1;     if (v < best) best = v; }
                    if (z > 0 && x > 0)     { int v = dist[c - w - 1] + 1; if (v < best) best = v; }
                    if (z > 0 && x < w - 1) { int v = dist[c - w + 1] + 1; if (v < best) best = v; }
                    if (borderSource && (x == 0 || z == 0) && best > 1) best = 1;
                    if (best > cap) best = cap;
                    dist[c] = best;
                }
            }
            for (int z = d - 1; z >= 0; z--) {
                for (int x = w - 1; x >= 0; x--) {
                    int c = x + z * w, best = dist[c];
                    if (best == 0) continue;
                    if (x < w - 1)              { int v = dist[c + 1] + 1;     if (v < best) best = v; }
                    if (z < d - 1)              { int v = dist[c + w] + 1;     if (v < best) best = v; }
                    if (z < d - 1 && x < w - 1) { int v = dist[c + w + 1] + 1; if (v < best) best = v; }
                    if (z < d - 1 && x > 0)     { int v = dist[c + w - 1] + 1; if (v < best) best = v; }
                    if (borderSource && (x == w - 1 || z == d - 1) && best > 1) best = 1;
                    dist[c] = best;
                }
            }
        }
        // 2-pass 3x3x3-stencil DT. No border source — the field must read DT3_CAP outside the seeded blocks, not 0.
        static void chebyshevDT3(int[] dist, int w, int d, int layers, int cap) {
            // forward: L,z,x ascending — the 13 neighbors with (dL<0) || (dL==0 && dz<0) || (dL==0 && dz==0 && dx<0).
            for (int L = 0; L < layers; L++) {
                for (int z = 0; z < d; z++) {
                    for (int x = 0; x < w; x++) {
                        int c = (L * d + z) * w + x, best = dist[c];
                        if (best == 0) continue;
                        for (int dL = -1; dL <= 0; dL++) {
                            int lz = L + dL; if (lz < 0) continue;
                            int dzMax = (dL == 0) ? 0 : 1;
                            for (int dz = -1; dz <= dzMax; dz++) {
                                int zz = z + dz; if (zz < 0 || zz >= d) continue;
                                int dxMax = (dL == 0 && dz == 0) ? -1 : 1;   // dL==0,dz==0 → only dx=-1 (the in-row predecessor)
                                for (int dx = -1; dx <= dxMax; dx++) {
                                    int xx = x + dx; if (xx < 0 || xx >= w) continue;
                                    int v = dist[(lz * d + zz) * w + xx] + 1;
                                    if (v < best) best = v;
                                }
                            }
                        }
                        if (best > cap) best = cap;
                        dist[c] = best;
                    }
                }
            }
            // backward: L,z,x descending — the mirror 13 neighbors with (dL>0) || (dL==0 && dz>0) || (dL==0 && dz==0 && dx>0).
            for (int L = layers - 1; L >= 0; L--) {
                for (int z = d - 1; z >= 0; z--) {
                    for (int x = w - 1; x >= 0; x--) {
                        int c = (L * d + z) * w + x, best = dist[c];
                        if (best == 0) continue;
                        for (int dL = 0; dL <= 1; dL++) {
                            int lz = L + dL; if (lz >= layers) continue;
                            int dzMin = (dL == 0) ? 0 : -1;
                            for (int dz = dzMin; dz <= 1; dz++) {
                                int zz = z + dz; if (zz < 0 || zz >= d) continue;
                                int dxMin = (dL == 0 && dz == 0) ? 1 : -1;   // dL==0,dz==0 → only dx=+1 (the in-row successor)
                                for (int dx = dxMin; dx <= 1; dx++) {
                                    int xx = x + dx; if (xx < 0 || xx >= w) continue;
                                    int v = dist[(lz * d + zz) * w + xx] + 1;
                                    if (v < best) best = v;
                                }
                            }
                        }
                        if (best > cap) best = cap;
                        dist[c] = best;
                    }
                }
            }
        }
        /** Clears a hosted structure's interior void so islands can't form inside it; open bays wider than closeR
         *  stay terrain. Per-Y: morphologically closes the walls, floods from the border, fills what's unreached. */
        static int fillEnclosed(long[] occ, long[] filledOcc, int w, int d, int[] cLo, int[] cHi, int gLo, int gHi, int closeR) {
            if (w <= 2 || d <= 2 || gHi < gLo) return 0;
            int n = w * d;
            // Keeps only the longest CONTIGUOUS trapped run per column: a column trapped between two vertically-
            // disjoint enclosures keeps just its longest run, so the open gap between them is never shafted.
            int[] runLo = new int[n], runHi = new int[n], bestLo = new int[n], bestHi = new int[n];
            java.util.Arrays.fill(runLo, Integer.MAX_VALUE); java.util.Arrays.fill(runHi, Integer.MIN_VALUE);
            java.util.Arrays.fill(bestLo, Integer.MAX_VALUE); java.util.Arrays.fill(bestHi, Integer.MIN_VALUE);
            int cap = closeR + 1;
            int[] dist = new int[n];
            boolean[] wall = new boolean[n];
            boolean[] inC = new boolean[n];   // morphological closing of the wall set at this y
            boolean[] reached = new boolean[n];
            int[] stack = new int[n];
            for (int y = gLo; y <= gHi; y++) {
                for (int c = 0; c < n; c++) { wall[c] = wallAtY(occ, cLo, cHi, c, y); dist[c] = wall[c] ? 0 : cap; }
                chebyshevDT(dist, w, d, cap, false);
                // Erode to the closing, then OR the raw walls back in: without it, erosion's open-border source
                // would also erode real walls near the bbox edge and leak the flood into a sealed interior.
                for (int c = 0; c < n; c++) dist[c] = (dist[c] > closeR) ? 0 : cap;
                chebyshevDT(dist, w, d, cap, true);
                for (int c = 0; c < n; c++) inC[c] = dist[c] > closeR || wall[c];
                // Flood the open space (complement of the closed set) from the envelope border, 4-conn.
                java.util.Arrays.fill(reached, false);
                int sp = 0;
                for (int x = 0; x < w; x++) {
                    int top = x, bot = x + (d - 1) * w;
                    if (!inC[top] && !reached[top]) { reached[top] = true; stack[sp++] = top; }
                    if (!inC[bot] && !reached[bot]) { reached[bot] = true; stack[sp++] = bot; }
                }
                for (int z = 0; z < d; z++) {
                    int left = z * w, right = (w - 1) + z * w;
                    if (!inC[left] && !reached[left]) { reached[left] = true; stack[sp++] = left; }
                    if (!inC[right] && !reached[right]) { reached[right] = true; stack[sp++] = right; }
                }
                while (sp > 0) {
                    int bit = stack[--sp];
                    int x = bit % w, z = bit / w;
                    if (x > 0)     { int nb = bit - 1; if (!reached[nb] && !inC[nb]) { reached[nb] = true; stack[sp++] = nb; } }
                    if (x < w - 1) { int nb = bit + 1; if (!reached[nb] && !inC[nb]) { reached[nb] = true; stack[sp++] = nb; } }
                    if (z > 0)     { int nb = bit - w; if (!reached[nb] && !inC[nb]) { reached[nb] = true; stack[sp++] = nb; } }
                    if (z < d - 1) { int nb = bit + w; if (!reached[nb] && !inC[nb]) { reached[nb] = true; stack[sp++] = nb; } }
                }
                // A truly-unoccupied column is cleared at this height only if the flood can't reach it (trapped
                // inside the closed silhouette); a flood-reached column drains to the exterior and stays terrain.
                for (int bit = 0; bit < n; bit++) {
                    if ((occ[bit >> 6] & (1L << (bit & 63))) != 0) continue;
                    if (reached[bit]) continue;
                    if (runHi[bit] == y - 1) { runHi[bit] = y; continue; }
                    if (runHi[bit] >= runLo[bit] && (bestHi[bit] < bestLo[bit]
                            || runHi[bit] - runLo[bit] > bestHi[bit] - bestLo[bit])) {
                        bestLo[bit] = runLo[bit]; bestHi[bit] = runHi[bit];
                    }
                    runLo[bit] = y; runHi[bit] = y;
                }
            }
            int filled = 0;
            for (int bit = 0; bit < n; bit++) {
                if (runHi[bit] >= runLo[bit] && (bestHi[bit] < bestLo[bit]
                        || runHi[bit] - runLo[bit] > bestHi[bit] - bestLo[bit])) {
                    bestLo[bit] = runLo[bit]; bestHi[bit] = runHi[bit];
                }
                if (bestHi[bit] < bestLo[bit]) continue;
                occ[bit >> 6] |= (1L << (bit & 63));
                filledOcc[bit >> 6] |= (1L << (bit & 63));
                cLo[bit] = bestLo[bit]; cHi[bit] = bestHi[bit];
                filled++;
            }
            return filled;
        }
        boolean occupied(int x, int z) {
            int i = x - oX, j = z - oZ;
            if (i < 0 || i >= w || j < 0 || j >= d) return false;
            int bit = i + j * w;
            return (occ[bit >> 6] & (1L << (bit & 63))) != 0;
        }
        int nearestOccupiedDist(int x, int z, int reach) {
            if (occupied(x, z)) return 0;
            int best = reach + 1;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int c = Math.max(Math.abs(dx), Math.abs(dz));
                    if (c >= best) continue;
                    if (occupied(x + dx, z + dz)) best = c;
                }
            }
            return best;
        }
        /** -1 if none — the column whose [colLo,colHi] a fringe cell erodes toward, so the cut face follows the LOCAL lobe height. */
        int nearestOccupiedBit(int x, int z, int reach) {
            if (x < oX - reach || x > oX + w - 1 + reach || z < oZ - reach || z > oZ + d - 1 + reach) return -1;
            int i = x - oX, j = z - oZ;
            if (i >= 0 && i < w && j >= 0 && j < d) {
                int bit = i + j * w;
                if ((occ[bit >> 6] & (1L << (bit & 63))) != 0) return bit;   // occupied at (x,z)
            }
            int best = reach + 1, bestBit = -1;
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    int c = Math.max(Math.abs(dx), Math.abs(dz));
                    if (c >= best) continue;
                    if (occupied(x + dx, z + dz)) { best = c; bestBit = (x + dx - oX) + (z + dz - oZ) * w; }
                }
            }
            return bestBit;
        }
        // RING order (increasing Chebyshev r) so the MIN meets the nearest covering box first, letting the d==0
        // break and lateral prune fire early. dXZ = r exactly; result is order-independent (min).
        int collectOccupiedBits(int x, int z, int reach,
                                int[] oDxz, int[] oLo, int[] oHi, int[] oGLo, boolean[] oSeat, boolean[] oFilled, boolean[] oDist,
                                boolean[] oBase, CarveMask[] oMask, short[] oColX, short[] oColZ, int n) {
            if (x < oX - reach || x > oX + w - 1 + reach || z < oZ - reach || z > oZ + d - 1 + reach) return n;
            for (int r = 0; r <= reach; r++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean fullRow = (dz == -r || dz == r);     // top/bottom rows: every dx; middle rows: only dx=±r
                    int step = fullRow ? 1 : Math.max(1, 2 * r);
                    for (int dx = -r; dx <= r; dx += step) {
                        int xx = x + dx - oX, zz = z + dz - oZ;
                        if (xx < 0 || xx >= w || zz < 0 || zz >= d) continue;
                        int bit = xx + zz * w;
                        if ((occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                        oDxz[n] = r;
                        oLo[n] = colLo(bit); oHi[n] = colHi(bit); oGLo[n] = gLo; oSeat[n] = seated; oFilled[n] = isFilled(bit); oDist[n] = distributed;
                        // distributed-gated so FLOATING/pure-SEATED stay base=false.
                        oBase[n] = distributed && inBaseFootprint(x + dx, z + dz);
                        oMask[n] = this;   // null dt3 ⇒ dt3At returns DT3_CAP+1
                        oColX[n] = (short) (x + dx); oColZ[n] = (short) (z + dz);
                        n++;
                    }
                }
            }
            return n;
        }
        public static CarveMask fromBoxes(List<BoundingBox> boxes, boolean seated) {
            return fromBoxes(boxes, seated, 0);
        }
        public static CarveMask fromBoxes(List<BoundingBox> boxes, boolean seated, int closeR) {
            return fromBoxes(boxes, seated, closeR, false);
        }
        public static CarveMask fromBoxes(List<BoundingBox> boxes, boolean seated, int closeR, boolean distributed) {
            return fromBoxes(boxes, seated, closeR, distributed, distributed);
        }
        public static CarveMask fromBoxes(List<BoundingBox> boxes, boolean seated, int closeR, boolean distributed, boolean layerDistributed) {
            int oX = Integer.MAX_VALUE, oZ = Integer.MAX_VALUE, mX = Integer.MIN_VALUE, mZ = Integer.MIN_VALUE;
            int gLo = Integer.MAX_VALUE, gHi = Integer.MIN_VALUE;
            for (BoundingBox b : boxes) {
                oX = Math.min(oX, b.minX()); oZ = Math.min(oZ, b.minZ());
                mX = Math.max(mX, b.maxX()); mZ = Math.max(mZ, b.maxZ());
                gLo = Math.min(gLo, b.minY()); gHi = Math.max(gHi, b.maxY());
            }
            int w = mX - oX + 1, d = mZ - oZ + 1;
            long[] occ = new long[((w * d) + 63) >> 6];
            int[] cLo = new int[w * d], cHi = new int[w * d];
            java.util.Arrays.fill(cLo, Integer.MAX_VALUE);
            java.util.Arrays.fill(cHi, Integer.MIN_VALUE);
            for (BoundingBox b : boxes) {
                for (int wz = b.minZ(); wz <= b.maxZ(); wz++) {
                    for (int wx = b.minX(); wx <= b.maxX(); wx++) {
                        int bit = (wx - oX) + (wz - oZ) * w;
                        occ[bit >> 6] |= (1L << (bit & 63));
                        if (b.minY() < cLo[bit]) cLo[bit] = b.minY();
                        if (b.maxY() > cHi[bit]) cHi[bit] = b.maxY();
                    }
                }
            }
            // Rasterize as real-block seeds so the gate exercises the REAL 3D distance path (a lateral inter-box
            // air gap stays far in 3D, not bridged by a column envelope).
            IntArrayList bI = null, bJ = null, bY = null; boolean[] hasBlock = null;
            if (distributed) {
                bI = new IntArrayList(); bJ = new IntArrayList(); bY = new IntArrayList();
                hasBlock = new boolean[w * d];
                for (BoundingBox b : boxes) {
                    for (int wz = b.minZ(); wz <= b.maxZ(); wz++) {
                        for (int wx = b.minX(); wx <= b.maxX(); wx++) {
                            int i = wx - oX, j = wz - oZ;
                            hasBlock[i + j * w] = true;
                            for (int wy = b.minY(); wy <= b.maxY(); wy++) { bI.add(i); bJ.add(j); bY.add(wy); }
                        }
                    }
                }
            }
            long[] filledOcc = new long[((w * d) + 63) >> 6];
            int filled = fillEnclosed(occ, filledOcc, w, d, cLo, cHi, gLo, gHi, closeR);
            short[] colLoOff = new short[w * d], colHiOff = new short[w * d];
            encodeColumnSpans(occ, cLo, cHi, gLo, colLoOff, colHiOff, w * d);
            byte[] dt3 = null; int dt3OX = 0, dt3OZ = 0, dt3W = 0, dt3D = 0, dt3YLo = 0, dt3Layers = 0;
            if (distributed) {
                dt3OX = oX - DT3_CAP; dt3OZ = oZ - DT3_CAP; dt3W = w + 2 * DT3_CAP; dt3D = d + 2 * DT3_CAP;
                dt3YLo = gLo - DT3_CAP; dt3Layers = (gHi - gLo) + 1 + 2 * DT3_CAP;
                dt3 = buildDt3(w, d, gLo, gHi, dt3W, dt3D, dt3YLo, dt3Layers, bI, bJ, bY, occ, cLo, cHi, hasBlock);
                if (dt3 == null) { dt3OX = 0; dt3OZ = 0; dt3W = 0; dt3D = 0; dt3YLo = 0; dt3Layers = 0; }
            }
            return new CarveMask(oX, oZ, w, d, occ, colLoOff, colHiOff, filledOcc, gLo, gHi, seated, distributed, layerDistributed, FOUNDATION_DEPTH, filled,
                    dt3, dt3OX, dt3OZ, dt3W, dt3D, dt3YLo, dt3Layers, boxes.isEmpty() ? null : boxes.get(0));
        }
    }

    /** Constants + kernel live statically on BeyondStructureCarver; the generator delegates via thin forwarders so callers stay unchanged. */
    private final BeyondStructureCarver the_beyond$carver = new BeyondStructureCarver(this);

    public List<CarveMask> the_beyond$collectCarveMasks(StructureManager sm, ChunkPos cp) {
        return the_beyond$carver.the_beyond$collectCarveMasks(sm, cp);
    }
    public void the_beyond$placeUnreferencedCarveStructures(net.minecraft.world.level.WorldGenLevel level,
            StructureManager sm, ChunkAccess chunk) {
        the_beyond$carver.the_beyond$placeUnreferencedCarveStructures(level, sm, chunk);
    }
    public boolean the_beyond$landmarkInForeignCavity(StructureManager sm, StructureStart self, ChunkPos decorChunk) {
        return the_beyond$carver.the_beyond$landmarkInForeignCavity(sm, self, decorChunk);
    }
    public static int the_beyond$carveOutsideDist(List<CarveMask> masks, int x, int y, int z) {
        return BeyondStructureCarver.the_beyond$carveOutsideDist(masks, x, y, z);
    }
    public static int the_beyond$guardOutsideDist(List<CarveMask> masks, int x, int y, int z) {
        return BeyondStructureCarver.the_beyond$guardOutsideDist(masks, x, y, z);
    }
    public static boolean the_beyond$carveRemovedAirAt(List<CarveMask> masks, int x, int y, int z) {
        return BeyondStructureCarver.the_beyond$carveRemovedAirAt(masks, x, y, z);
    }

    public static final int STRUCT_FEATURE_MARGIN_DOWN = 6, STRUCT_FEATURE_MARGIN_UP = 2, STRUCT_FEATURE_MARGIN_LAT = 4;
    /** True if (x,y,z) is inside ANY carve structure's bbox (+margin) — bars the gellid-void pool. Uses the bbox, not
     *  occupancy: the pool sits in the open interior, which an occupancy test would miss. */
    public static boolean the_beyond$insideAnyStructureFootprint(List<CarveMask> masks, int x, int y, int z) {
        for (int i = 0, n = masks.size(); i < n; i++) {
            CarveMask m = masks.get(i);
            if (x < m.oX - STRUCT_FEATURE_MARGIN_LAT || x >= m.oX + m.w + STRUCT_FEATURE_MARGIN_LAT
                    || z < m.oZ - STRUCT_FEATURE_MARGIN_LAT || z >= m.oZ + m.d + STRUCT_FEATURE_MARGIN_LAT) continue;
            if (y >= m.gLo - STRUCT_FEATURE_MARGIN_DOWN && y <= m.gHi + STRUCT_FEATURE_MARGIN_UP) return true;
        }
        return false;
    }

    // Thin forwarders to BeyondStructureCarver; access modifiers mirror the originals so overload resolution is identical.
    private static int the_beyond$bandWarp(int x, int y, int z) {
        return BeyondStructureCarver.the_beyond$bandWarp(x, y, z);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3) {
        return BeyondStructureCarver.the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow) {
        return BeyondStructureCarver.the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough) {
        return BeyondStructureCarver.the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base) {
        return BeyondStructureCarver.the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove) {
        return BeyondStructureCarver.the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor) {
        return BeyondStructureCarver.the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, baseFloor);
    }
    private static double the_beyond$carveWarp(int x, int y, int z) {
        return BeyondStructureCarver.the_beyond$carveWarp(x, y, z);
    }
    private static double the_beyond$faceRough(int x, int y, int z) {
        return BeyondStructureCarver.the_beyond$faceRough(x, y, z);
    }
    private static int the_beyond$carveDistAt(int dXZ, int colLo, int colHi, boolean seated, boolean filled, boolean distributed, int gLo, int hWarp, boolean guard, int y) {
        return BeyondStructureCarver.the_beyond$carveDistAt(dXZ, colLo, colHi, seated, filled, distributed, gLo, hWarp, guard, y);
    }
    public static int the_beyond$foundationNatTop(int gLo, int foundationDepth, java.util.function.IntPredicate naturalSolid) {
        return BeyondStructureCarver.the_beyond$foundationNatTop(gLo, foundationDepth, naturalSolid);
    }
    public static int the_beyond$contiguousRockDown(int probeTop, int need, java.util.function.IntPredicate naturalSolid) {
        return BeyondStructureCarver.the_beyond$contiguousRockDown(probeTop, need, naturalSolid);
    }
    public static boolean the_beyond$isFoundationFill(int y, int gLo, int natTop) {
        return BeyondStructureCarver.the_beyond$isFoundationFill(y, gLo, natTop);
    }
    public static boolean the_beyond$footingActiveAt(boolean seated, boolean distributed, boolean inBaseFootprint) {
        return BeyondStructureCarver.the_beyond$footingActiveAt(seated, distributed, inBaseFootprint);
    }
    public static int the_beyond$lipTop(int natTop, int anchorColLo, int anchorColHi, int dxz) {
        return BeyondStructureCarver.the_beyond$lipTop(natTop, anchorColLo, anchorColHi, dxz);
    }
    public static boolean the_beyond$isLipFill(int y, int natTop, int lipTop) {
        return BeyondStructureCarver.the_beyond$isLipFill(y, natTop, lipTop);
    }
    public static double the_beyond$baseBeardDelta(int natTop, int anchorColLo, int anchorColHi, int floorY, int dxz, int y) {
        return BeyondStructureCarver.the_beyond$baseBeardDelta(natTop, anchorColLo, anchorColHi, floorY, dxz, y);
    }
    static double the_beyond$baseBeardDeltaAt(int[] dxz, int[] lo, int[] hi, boolean[] base, int[] baseNat, int[] baseFloor, int nBits, int y) {
        return BeyondStructureCarver.the_beyond$baseBeardDeltaAt(dxz, lo, hi, base, baseNat, baseFloor, nBits, y);
    }
    static int the_beyond$groundRestNatTop(int lo, int natTop) {
        return BeyondStructureCarver.the_beyond$groundRestNatTop(lo, natTop);
    }
    static int the_beyond$seatConfinedNatTop(int natTop, int seatFloor) {
        return BeyondStructureCarver.the_beyond$seatConfinedNatTop(natTop, seatFloor);
    }

    static final boolean DISTRIBUTED_LIP = BeyondStructureCarver.DISTRIBUTED_LIP;
    static final int BEARD_LAT_REACH = BeyondStructureCarver.BEARD_LAT_REACH;
    static final int PLATFORM_COVER = BeyondStructureCarver.PLATFORM_COVER;
    static final boolean DISTRIBUTED_BASE_BEARD = BeyondStructureCarver.DISTRIBUTED_BASE_BEARD;
    static final double BASE_BEARD_AMP = BeyondStructureCarver.BASE_BEARD_AMP;
    static final int BASE_BEARD_VFADE = BeyondStructureCarver.BASE_BEARD_VFADE;
    static final boolean DISTRIBUTED_SUBSURFACE_BURY = BeyondStructureCarver.DISTRIBUTED_SUBSURFACE_BURY;
    static final int SUBSURF_KEEP_REACH = BeyondStructureCarver.SUBSURF_KEEP_REACH;
    static final boolean DISTRIBUTED_GROW_OVER_VOID = BeyondStructureCarver.DISTRIBUTED_GROW_OVER_VOID;
    static final int BEARD_GROUND_BAND = BeyondStructureCarver.BEARD_GROUND_BAND;
    static final int BEARD_SEAT_DROP = BeyondStructureCarver.BEARD_SEAT_DROP;

    private static final class BeardCtx implements DensityFunction.FunctionContext {
        int x, y, z;
        @Override public int blockX() { return x; }
        @Override public int blockY() { return y; }
        @Override public int blockZ() { return z; }
    }

    private static double cyclicDensity(int y, double cycleHeight) {
        // floorMod-style: Java's % returns the dividend's sign, which would invert the modifier below y=0.
        double mod = ((y % cycleHeight) + cycleHeight) % cycleHeight;
        double normalizedY = mod / cycleHeight;
        if (normalizedY < 0.8) {
            return Math.sin((normalizedY / 0.8) * (Math.PI / 2));
        } else {
            return 1 - (normalizedY - 0.8) / 0.2;
        }
    }

    /** Factoring this out of {@link #edgeGradient} changes no rounding (Java folds left-to-right) — used by the air-gap skip to bound max density. */
    static double edgeGradientFactor(double y, int dimMinY, int dimMaxY) {
        final double bottomZeroY = dimMinY + 64;
        final double bottomFadeEnd = bottomZeroY + 64;
        final double topZeroY = dimMaxY;
        final double topFadeStart = topZeroY - 64;
        double gradientBottom = 1.0;
        double gradientTop = 1.0;

        if (y <= bottomFadeEnd) {
            gradientBottom = (y - bottomZeroY) / 64.0;
            if (y < bottomZeroY) {
                gradientBottom = 0.0;
            }
        }

        if (y >= topFadeStart) {
            // Clamp: y above topZeroY would otherwise flip sign and leak phantom solid biomes at high y.
            gradientTop = Math.max(0.0, (topZeroY - y) / 64.0);
        }

        return gradientBottom * gradientTop;
    }

    private static double edgeGradient(double y, double noiseValue, int dimMinY, int dimMaxY) {
        return edgeGradientFactor(y, dimMinY, dimMaxY) * noiseValue;
    }

    private static double globalNoiseOffset(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double noiseValue = noise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }

    private static double globalNoiseOffsetMultirotation(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double r0 = noise.getValue( x,  z, false);
        double r1 = noise.getValue(-z,  x, false);
        double r2 = noise.getValue(-x, -z, false);
        double r3 = noise.getValue( z, -x, false);
        double avg = (r0 + r1 + r2 + r3) * 0.25;
        return min + (max - min) * ((avg + 1) * 0.5);
    }

    private static double globalNoiseOffsetBlur3x3(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        final double delta = 1.0; // one simplex cell in noise-space
        double sum = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sum += noise.getValue(x + dx * delta, z + dz * delta, false);
            }
        }
        double avg = sum / 9.0;
        return min + (max - min) * ((avg + 1) * 0.5);
    }

    private static double globalNoiseOffsetDistanceAdaptive(
            double min, double max, double refRadius,
            int worldX, int worldZ, PerlinSimplexNoise noise) {
        double r = Math.sqrt((double) worldX * worldX + (double) worldZ * worldZ);
        double atten = (r <= refRadius) ? 1.0 : (refRadius / r);
        double mid = (min + max) * 0.5;
        double halfRange = (max - min) * 0.5;
        double nx = worldX * 0.000001;
        double nz = worldZ * 0.000001;
        double noiseValue = noise.getValue(nx, nz, false); // in [-1, 1]
        return mid + halfRange * atten * noiseValue;
    }

    private static double globalNoiseOffsetLocalWrap(
            double min, double max, int wrapRange,
            int worldX, int worldZ, PerlinSimplexNoise noise) {
        int hx = pingPongWrap(worldX, 0, wrapRange);
        int hz = pingPongWrap(worldZ, 0, wrapRange);
        double nx = hx * 0.000001;
        double nz = hz * 0.000001;
        double noiseValue = noise.getValue(nx, nz, false);
        return min + (max - min) * ((noiseValue + 1) * 0.5);
    }

    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        int globalX = pos.getX();
        int globalZ = pos.getZ();

        computeNoisesIfNotPresent(random);

        float distanceFromOrigin = (float) Math.sqrt((double) globalX * globalX + (double) globalZ * globalZ);
        long packed = computeWrappedCoords(globalX, globalZ);
        int wrappedX = unpackWrappedX(packed);
        int wrappedZ = unpackWrappedZ(packed);
        double horizontalBaseScale = getHorizontalBaseScale(wrappedX, wrappedZ);
        double verticalBaseScale = getVerticalBaseScale(wrappedX, wrappedZ);
        double threshold = getThreshold(wrappedX, wrappedZ, distanceFromOrigin);
        double cycleHeight = getCycleHeight(wrappedX, wrappedZ);
        double terrainNoise = getTerrainDensity(pos.getX(), pos.getY(), pos.getZ());
        double simplex = simplexNoise.getValue(pos.getX(), pos.getY(), pos.getZ());


        info.add("TerrainNoise T: " + decimalformat.format(terrainNoise) +
                " HS: " + decimalformat.format(horizontalBaseScale) +
                " VS: " + decimalformat.format(verticalBaseScale) +
                " Threshold: " + decimalformat.format(threshold) +
                " CH: " + decimalformat.format(cycleHeight));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        computeNoisesIfNotPresent(randomState);
        return super.createState(structureSetLookup, randomState, seed);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        BeyondTerrainStateInternal.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        computeNoisesIfNotPresent(random);
        BeyondTerrainStateInternal.setDimBounds(height.getMinBuildHeight(), height.getMaxBuildHeight());
        // Far-field terrain is Beyond's pancake fill, not the vanilla noise router — reuse the same density
        // predicate here, or column-reading callers see phantom vanilla terrain.
        double dist = Math.sqrt((double) x * x + (double) z * z);
        if (dist >= 650.0 && BeyondTerrainState.isActive()) {
            int minY = height.getMinBuildHeight();
            int columnHeight = height.getHeight();
            net.minecraft.world.level.block.state.BlockState[] states =
                    new net.minecraft.world.level.block.state.BlockState[columnHeight];
            net.minecraft.world.level.block.state.BlockState endStone = Blocks.END_STONE.defaultBlockState();
            net.minecraft.world.level.block.state.BlockState air = Blocks.AIR.defaultBlockState();
            ColumnScratch s = SCRATCH.get();
            initColumnScratch(x, z, (float) dist, s);
            for (int i = 0; i < columnHeight; i++) {
                states[i] = isSolidTerrainScratch(minY + i, s) ? endStone : air;
            }
            return new NoiseColumn(minY, states);
        }
        return super.getBaseColumn(x, z, height, random);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        computeNoisesIfNotPresent(random);
        BeyondTerrainStateInternal.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        super.buildSurface(level, structureManager, random, chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        BeyondTerrainStateInternal.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        super.applyBiomeDecoration(level, chunk, structureManager);

        // Clear column (0,0) above the central dome so its heightmap re-primes onto Beyond's END_STONE, or the
        // exit portal spawns at the foreign-pushed column top.
        if (BeyondTerrainState.isActive()) {
            ChunkPos cp = chunk.getPos();
            if (cp.x == 0 && cp.z == 0) {
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                int top = chunk.getMaxBuildHeight() - 1;
                int floor = 60; // first y above the central island dome top at (0,0)
                net.minecraft.world.level.block.state.BlockState air = Blocks.AIR.defaultBlockState();
                for (int y = top; y >= floor; y--) {
                    mpos.set(0, y, 0);
                    if (!chunk.getBlockState(mpos).isAir()) {
                        chunk.setBlockState(mpos, air, false);
                    }
                }
            }
        }
    }

    private double calculateStructureAdaptation(List<StructureStart> validStarts, int x, int y, int z) {
        if (validStarts.isEmpty()) return 0.0;

        double adaptation = 0.0;
        for (int i = 0, n = validStarts.size(); i < n; i++) {
            adaptation += calculateStructureInfluence(validStarts.get(i), x, y, z);
        }
        return adaptation;
    }

    private double calculateStructureInfluence(StructureStart start, int x, int y, int z) {
        BoundingBox bounds = start.getBoundingBox();

        int dx = Math.max(Math.max(bounds.minX() - x, x - bounds.maxX()), 0);
        int dy = Math.max(Math.max(bounds.minY() - y, y - bounds.maxY()), 0);
        int dz = Math.max(Math.max(bounds.minZ() - z, z - bounds.maxZ()), 0);

        int distance = dx + dy + dz;

        int influenceRadius = Math.max(bounds.getXSpan(), bounds.getZSpan()) * 2;

        if (distance < influenceRadius) {
            double normalized = 1.0 - ((double)distance / influenceRadius);
            return normalized * normalized * 0.3;
        }

        return 0.0;
    }
}
