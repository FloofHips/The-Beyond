package com.thebeyond.common.worldgen;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.util.HashSimplexNoise;
import com.thebeyond.util.WorldSeedHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
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
    /** Terrain density field. Sampled by {@link #getTerrainDensity} octaves. */
    public static volatile HashSimplexNoise simplexNoise;
    /** Biome-selection noise; decoupled from {@link #simplexNoise} so density tuning doesn't reshuffle biomes. */
    public static volatile HashSimplexNoise biomeSimplexNoise;
    /** Z-channel warp noise; independently seeded so warpX and warpZ don't collapse on the x=z diagonal. */
    public static volatile HashSimplexNoise warpZSimplexNoise;
    public static volatile PerlinSimplexNoise globalHOffsetNoise;
    public static volatile PerlinSimplexNoise globalVOffsetNoise;
    public static volatile PerlinSimplexNoise globalCOffsetNoise;
    private double islandRadius = 75.0;
    private double buffer = 700.0;

    /** Baseline worldHeight (dim range 256). Always read via {@link #getWorldHeight()} to scale with enlarged dims. */
    static final double DEFAULT_WORLD_HEIGHT = 192;

    private static final int BASELINE_DIM_RANGE = 256;

    /** Effective worldHeight for the active dim; scales with dim range beyond 256. */
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

    /**
     * Per-octave wrap-range factors for mirror-seam decorrelation. Octave 0 stays at
     * 1.00 (F3/diagnostics use it); octaves 1-3 step down so pivot planes separate.
     * Length MUST equal {@link #NUM_OCTAVES}; factors MUST be ≤ 1.00.
     */
    private static final double[] OCTAVE_WRAP_FACTORS = {1.00, 0.91, 0.83, 0.77};

    /** Triangle-wave wrap between min/max. External callers go through {@link #computeWrappedCoords}. */
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

    /** Runtime terrain-transform params from dim JSON; falls back to {@link BeyondTerrainParams#DEFAULTS}. */
    public static volatile BeyondTerrainParams activeTerrainParams = BeyondTerrainParams.DEFAULTS;

    /** Default wrap range — test reference only; production uses {@link #activeTerrainParams}. */
    static final int WRAP_RANGE = BeyondTerrainParams.DEFAULTS.wrapRange();

    /**
     * Single source of truth for the wrap+warp transform. Packs wrappedX (high 32b)
     * + wrappedZ (low 32b). All density samplers, biome source, and heightmap queries
     * MUST route through this — inlining it elsewhere causes structures to float.
     */
    public static long computeWrappedCoords(int globalX, int globalZ) {
        return computeWrappedCoords(globalX, globalZ, activeTerrainParams);
    }

    /** Parameterized overload for diagnostic tools that need a non-live params snapshot. */
    public static long computeWrappedCoords(int globalX, int globalZ, BeyondTerrainParams params) {
        int wrapRange = params.wrapRange();

        HashSimplexNoise snoise = simplexNoise;
        HashSimplexNoise zsnoise = warpZSimplexNoise;
        if (snoise == null || zsnoise == null) {
            // Noise not yet initialized (can happen on cold queries before the
            // first computeNoisesIfNotPresent). Fall through to a raw wrap so
            // callers still get deterministic integers instead of NPE.
            int rawX = pingPongWrap(globalX, -wrapRange, wrapRange);
            int rawZ = pingPongWrap(globalZ, -wrapRange, wrapRange);
            return ((long) rawX << 32) | ((long) rawZ & 0xFFFFFFFFL);
        }
        int warpedInputX;
        int warpedInputZ;
        if (warpDisabled) {
            // Test-only: skip warp entirely.
            warpedInputX = globalX;
            warpedInputZ = globalZ;
        } else {
            double warpScale = params.warpScale();
            double warpAmplitude = params.warpAmplitude();
            double warpInX = globalX * warpScale;
            double warpInZ = globalZ * warpScale;
            // warpX/warpZ use independent noises so the channels don't collapse on x=z.
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

    /**
     * Fills per-octave wrap+scale arrays (length {@link #NUM_OCTAVES}). {@code hScales[k]}
     * is sampled at {@code (wrappedXs[k], wrappedZs[k])} — mismatched coords break the
     * per-octave anti-cancellation invariant. Frequency is baked into {@code hScales}/{@code vScales}.
     */
    private static void computeOctaveFields(
            int globalX, int globalZ,
            BeyondTerrainParams params,
            double[] hScales, double[] vScales,
            int[] wrappedXs, int[] wrappedZs,
            PerlinSimplexNoise hNoise, PerlinSimplexNoise vNoise) {
        int baseWrapRange = params.wrapRange();
        double warpScale = params.warpScale();
        double warpAmplitude = params.warpAmplitude();

        // Warp is (globalX, globalZ)-only — compute once, reuse across octaves.
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
            // Floor at MIN_WRAP_RANGE: defends against datapacks pushing wrap_range
            // to its minimum where factor 0.77 would fall below the validation floor.
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
            WorldSeedHolder holder = (WorldSeedHolder) (Object) randomState;
            long worldSeed = holder.the_Beyond$getWorldSeed();
            computeNoisesIfNotPresent(worldSeed);
        }
    }

    public void computeNoisesIfNotPresent(long worldSeed) {
        if (simplexNoise == null || biomeSimplexNoise == null || warpZSimplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {
            synchronized (BeyondEndChunkGenerator.class) {
                if (simplexNoise == null || biomeSimplexNoise == null || warpZSimplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {
                    // Seed chain: sequential offsets from worldSeed (next free = +6).
                    // Never reuse a prior offset — separating concerns was intentional.
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

    /** Resets static noises + params. Call on server stop to prevent cross-world leakage. */
    public static void resetNoises() {
        synchronized (BeyondEndChunkGenerator.class) {
            simplexNoise = null;
            biomeSimplexNoise = null;
            warpZSimplexNoise = null;
            globalHOffsetNoise = null;
            globalVOffsetNoise = null;
            globalCOffsetNoise = null;
            activeTerrainParams = BeyondTerrainParams.DEFAULTS;
        }
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
        computeNoisesIfNotPresent(structureState.getLevelSeed());
        BeyondTerrainState.setDimBounds(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        computeNoisesIfNotPresent(randomState);
        // Publish dim bounds before super triggers biome sampling (BeyondEndBiomeSource consumes
        // them to anchor the bottom_biome band and scale worldHeight).
        BeyondTerrainState.setDimBounds(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        return super.createBiomes(randomState, blender, structureManager, chunk);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        computeNoisesIfNotPresent(randomState);
        BeyondTerrainState.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        float distanceFromOrigin = (float) Math.sqrt((double) x * x + (double) z * z);

        // Scan from just below the dynamic worldHeight (where gradientTop starts ramping down
        // to 0) down to the dim floor. Beyond-só: worldHeight=192, scan starts at 132 as
        // before. Combo (bounds pack): worldHeight=288, scan starts at 228 so Beyond-gen
        // islands that now live up to y~256 are findable by the heightmap query.
        int scanTop = (int) (getWorldHeight() - 60);
        for (int y = scanTop; y >= level.getMinBuildHeight(); y--) {
            if (isSolidTerrain(x, y, z, distanceFromOrigin)) {
                return y;
            }
        }

        return 0;
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

    // Column-invariant scale helpers. Density-path callers MUST pass WRAPPED coords
    // (mismatch with sampler coords causes streaks at |X| ≥ ~500k). BiomeSource
    // intentionally passes grid coords — biome field doesn't need wrapping.

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
            // Pre-init: fall through to PerlinSimplex.
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

    /** Test-only: when non-null, {@link #getCycleHeight} returns this constant. */
    @VisibleForTesting
    static volatile Double cycleHeightOverride = null;

    /** Test-only cycleHeight frequency multiplier (1.0 = production 1 M-block period). */
    @VisibleForTesting
    static volatile double cycleHeightFrequencyMultiplier = 1.0;

    /** Test-only: when {@code true}, skips {@link #pingPongWrap} so raw globals feed the sampler. */
    @VisibleForTesting
    static volatile boolean wrapDisabled = false;

    /** Test-only: when {@code true}, skips the domain-warp noise; raw (x,z) feeds the wrap. */
    @VisibleForTesting
    static volatile boolean warpDisabled = false;

    /** Test-only: when non-null, forces {@code hScales[k]} to this constant (frequency still baked in). */
    @VisibleForTesting
    static volatile Double hScaleOverride = null;

    /** Test-only: same mechanism as {@link #hScaleOverride} for {@code vScales[k]}. */
    @VisibleForTesting
    static volatile Double vScaleOverride = null;

    /** Prototype: averages hScale at 4 90° rotations to cancel lattice directional bias. */
    @VisibleForTesting
    static volatile boolean hScaleMultirotation = false;

    /** Prototype: hScale via HashSimplexNoise instead of PerlinSimplexNoise. */
    @VisibleForTesting
    static volatile boolean hScaleUseHashNoise = false;

    /** Prototype: 3x3 blur of hScale noise to smear cell-local gradient bias. */
    @VisibleForTesting
    static volatile boolean hScaleBlur3x3 = false;

    /** Reference radius for distance-adaptive hScale (full amplitude inside, 1/r outside). */
    @VisibleForTesting
    static final double HSCALE_REF_RADIUS = 100_000.0;

    /** Prototype: 1/r amplitude attenuation of hScale beyond {@link #HSCALE_REF_RADIUS}. */
    @VisibleForTesting
    static volatile boolean hScaleDistanceAdaptive = false;

    /** Local-wrap range for hScale (ping-pong every 100 k blocks). */
    @VisibleForTesting
    static final int HSCALE_LOCAL_WRAP_RANGE = 50_000;

    /**
     * Prototype knob: routes {@link #getHorizontalBaseScale} through
     * {@link #globalNoiseOffsetLocalWrap} (ping-pong wrap range
     * {@link #HSCALE_LOCAL_WRAP_RANGE}). Bounds the noise input so streak-width stays
     * small at any distance; amplitude stays full (no 1/r decay like distance-adaptive).
     */
    @VisibleForTesting
    static volatile boolean hScaleLocalWrap = false;

    // Island-envelope sampler: freezes hScale to a global constant (kills the
    // X*∂h/∂X streak term) and restores regional island-size variance as an
    // amplitude envelope at ~2 M-block wavelength. Superseded by the band-blend
    // sampler (preserves wavelength variation, not just amplitude). Kept flag-
    // gated for comparison dumps.

    /** Forces constant hScale + amplitude envelope. Mutually exclusive with {@link #useBandBlend}. */
    @VisibleForTesting
    static volatile boolean useIslandEnvelope = false;

    /** Constant hScale when {@link #useIslandEnvelope} is active (midpoint of [0.005, 0.015]). */
    @VisibleForTesting
    static volatile double islandEnvelopeHScale = 0.010;

    /** Envelope output range; symmetric around 1.0 keeps average density unchanged. */
    @VisibleForTesting
    static volatile double envelopeLow  = 0.5;
    @VisibleForTesting
    static volatile double envelopeHigh = 1.5;

    /** Envelope spatial scale; 5e-7 ≈ 2 M-block wavelength (constant per plot). */
    @VisibleForTesting
    static volatile double envelopeScale = 5e-7;

    /** Envelope sampled at WORLD (x,z); returns scalar in [envelopeLow, envelopeHigh]. */
    public static double getIslandEnvelope(int x, int z) {
        PerlinSimplexNoise noise = globalHOffsetNoise;
        if (noise == null) return 1.0;
        double nx = x * envelopeScale;
        double nz = z * envelopeScale;
        double v = noise.getValue(nx, nz, false);
        return envelopeLow + (envelopeHigh - envelopeLow) * ((v + 1.0) * 0.5);
    }

    // Band-blend sampler: replaces the streak-producing `sampleX = X*hScale(X,Z)`
    // with a 2-sample lerp over adjacent fixed frequencies h_lo, h_hi picked
    // from BB_BAND_FREQUENCIES by an O(1) log-space lookup on hBase. h_i*2^k is
    // a global constant, so the X*∂h/∂X Jacobian term vanishes; C¹ smoothstep
    // blend avoids band seams. K=17 log-spaced bands give <5% wavelength error
    // vs continuous hBase. Column-invariant state (bbLoIdx, bbT) is computed
    // once per column; y-loop does 2 simplex samples + a lerp per octave (~2x baseline).

    /** K=17 log-spaced band frequencies spanning [0.005, 0.015]. O(1) indexed by {@link #computeBBState}. */
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

    /** Band-blend terrain sampler. Mutually exclusive with {@link #useIslandEnvelope}. */
    @VisibleForTesting
    static volatile boolean useBandBlend = false;

    /**
     * Column-invariant BB state. Fills {@code bbLoIdx[k]} (low-band index) and
     * {@code bbT[k]} (pre-smoothstepped blend weight) from {@code hScales[k]}.
     * Call ONCE per column; y-loop then just samples 2 simplexes + lerps.
     */
    static void computeBBState(double[] hScales, int[] bbLoIdx, double[] bbT) {
        double frequencyMult = 1.0;
        for (int k = 0; k < NUM_OCTAVES; k++) {
            // Recover octave k's own hBase — hScales[k] = hBase_k × LACUNARITY^k.
            double hBaseTarget = hScales[k] / frequencyMult;

            // Closed-form log-space index. Clamped to [0, BB_BAND_COUNT − 1] so
            // the subsequent 2-sample blend always picks a valid pair (lo, lo+1).
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
            // Smoothstep: C¹ partition-of-unity weight. Bakes the polynomial
            // once per column so the y-loop is just a lerp.
            bbT[k] = t * t * (3.0 - 2.0 * t);

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
        // Period 1 M blocks: keeps in-view cycleHeight variation under one divisor
        // step so cyclicDensity's divisor discontinuities don't pile up in a scene.
        // (Semantic fix tracked as Level 2 in BB_RESEARCH_NOTES §4.7.)
        double freq = 0.000001 * cycleHeightFrequencyMultiplier;
        return globalNoiseOffset(10, 100, x * freq, z * freq, noise);
    }

    /**
     * Base threshold for density vs void. Pass WRAPPED (x,z) to align with the
     * density sampler; {@code distanceFromOrigin} stays global (island taper).
     */
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

    /** Cold-path entry (heightmap / biome source / F3 debug). Allocates; hot path uses the array overload below. */
    public static double getTerrainDensity(int globalX, int globalY, int globalZ) {
        return getTerrainDensity(globalX, globalY, globalZ, activeTerrainParams);
    }

    /** Cold-path entry with caller-supplied params (diagnostic tools). */
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

        // Island-envelope: overwrite hScales with constant (frequency-ramped per octave).
        if (useIslandEnvelope) {
            double h = islandEnvelopeHScale;
            double freq = 1.0;
            for (int k = 0; k < NUM_OCTAVES; k++) {
                hScales[k] = h * freq;
                freq *= LACUNARITY;
            }
        }

        // cycleHeight sampled at octave-0's wrap — matches F3 debug readout and getThreshold.
        double cycleHeight = getCycleHeight(wrappedXs[0], wrappedZs[0], cNoise);

        double density = getTerrainDensity(globalY, hScales, vScales, cycleHeight, wrappedXs, wrappedZs);

        // Island-envelope: multiply by slowly-varying envelope sampled at WORLD coords.
        if (useIslandEnvelope) {
            density *= getIslandEnvelope(globalX, globalZ);
        }

        return density;
    }

    /**
     * Hot-path density sampler. Caller MUST hoist the column-invariant arrays out of
     * the y-loop. {@code hScales[k]}/{@code vScales[k]} have frequency baked in.
     */
    public static double getTerrainDensity(
            int globalY,
            double[] hScales,
            double[] vScales,
            double cycleHeight,
            int[] wrappedXs,
            int[] wrappedZs) {
        // Back-compat wrapper. Hot callers use the 8-arg form with column-hoisted bb state.
        if (useBandBlend) {
            int[] bbLoIdx = new int[NUM_OCTAVES];
            double[] bbT = new double[NUM_OCTAVES];
            computeBBState(hScales, bbLoIdx, bbT);
            return getTerrainDensity(globalY, hScales, vScales, cycleHeight,
                    wrappedXs, wrappedZs, bbLoIdx, bbT);
        }
        return getTerrainDensity(globalY, hScales, vScales, cycleHeight,
                wrappedXs, wrappedZs, null, null);
    }

    /**
     * Hot-path sampler with column-hoisted BB state. Pass {@code bbLoIdx}/{@code bbT}
     * populated by {@link #computeBBState} when {@link #useBandBlend} is active, or
     * {@code null} for both otherwise.
     */
    public static double getTerrainDensity(
            int globalY,
            double[] hScales,
            double[] vScales,
            double cycleHeight,
            int[] wrappedXs,
            int[] wrappedZs,
            int[] bbLoIdx,
            double[] bbT) {
        int shiftedY = globalY + TERRAIN_Y_OFFSET;
        HashSimplexNoise noise = simplexNoise;

        double noiseValue = 0.0;
        double amplitude = 1.0;
        double maxAmplitude = 0.0;

        if (useBandBlend) {
            // Band-blend: 2-sample lerp across adjacent fixed-frequency bands.
            // h_i * frequencyMult is a constant → no X·∂h/∂X streak term.
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

        noiseValue /= maxAmplitude;
        double densityModifier = cyclicDensity(shiftedY, cycleHeight);
        noiseValue *= densityModifier;

        return edgeGradient(shiftedY, getWorldHeight(), noiseValue);
    }

    public static boolean isSolidTerrain(int globalX, int globalY, int globalZ, float distanceFromOrigin) {
        // Wrap before threshold to match generateEndTerrain's phase alignment.
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
        BeyondTerrainState.setDimBounds(chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();

            // Snapshot valid structure starts once per chunk (skips ~40k redundant
            // Map.values() iterations and isValid() checks from the y-loop below).
            List<StructureStart> validStarts = null;
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (start.isValid()) {
                    if (validStarts == null) validStarts = new ArrayList<>(4);
                    validStarts.add(start);
                }
            }
            if (validStarts == null) validStarts = Collections.emptyList();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int globalX = startX + x;
                    int globalZ = startZ + z;

                    // Auroracite floor: placed by AuroraciteLayerFeature, not here.
                    float distanceFromOrigin = (float) Math.sqrt((double) globalX * globalX + (double) globalZ * globalZ);

                    if (distanceFromOrigin <= islandRadius + 50) {
                        generateMainIsland(chunk, globalX, globalZ, distanceFromOrigin, islandRadius);
                    }

                    else if (distanceFromOrigin >= 650) {
                      generateEndTerrain(chunk, globalX, globalZ, distanceFromOrigin, validStarts);
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
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

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

    private void generateEndTerrain(ChunkAccess chunk, int globalX, int globalZ, float distanceFromOrigin, List<StructureStart> validStarts) {
        // Column-invariant values resolved once per (globalX, globalZ); y-loop reuses them.
        PerlinSimplexNoise hNoise = globalHOffsetNoise;
        PerlinSimplexNoise vNoise = globalVOffsetNoise;
        PerlinSimplexNoise cNoise = globalCOffsetNoise;

        // Snapshot terrain params once per column (datapack reload safety).
        BeyondTerrainParams params = activeTerrainParams;

        double[] hScales = new double[NUM_OCTAVES];
        double[] vScales = new double[NUM_OCTAVES];
        int[] wrappedXs = new int[NUM_OCTAVES];
        int[] wrappedZs = new int[NUM_OCTAVES];
        computeOctaveFields(globalX, globalZ, params,
                hScales, vScales, wrappedXs, wrappedZs, hNoise, vNoise);

        // cycleHeight + baseThreshold use octave-0's wrap (matches F3 debug + WrappedCoordsContractTest).
        double cycleHeight = getCycleHeight(wrappedXs[0], wrappedZs[0], cNoise);
        double baseThreshold = getThreshold(wrappedXs[0], wrappedZs[0], distanceFromOrigin);

        // BB-3a: column-hoisted band state keeps log/divide/smoothstep out of the y-loop.
        int[] bbLoIdx = null;
        double[] bbT = null;
        if (useBandBlend) {
            bbLoIdx = new int[NUM_OCTAVES];
            bbT = new double[NUM_OCTAVES];
            computeBBState(hScales, bbLoIdx, bbT);
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // Loop cap at (worldHeight - 32): edgeGradient zeroes beyond.
        final int loopEnd = (int) (getWorldHeight() - 32);
        for (int y = 1; y < loopEnd; y++) {
            double structureAdaptation = calculateStructureAdaptation(validStarts, globalX, y, globalZ);

            double density = getTerrainDensity(y, hScales, vScales, cycleHeight, wrappedXs, wrappedZs, bbLoIdx, bbT);

            // Beardification: pull threshold down + density up near structure bounds so
            // terrain extends to support them (prevents floating structures).
            double adaptedThreshold = baseThreshold - structureAdaptation * 0.15;
            double adaptedDensity = density + structureAdaptation * 0.1;

            if (adaptedDensity > adaptedThreshold) {
                chunk.setBlockState(mutable.set(globalX, y, globalZ), Blocks.END_STONE.defaultBlockState(), false);
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
            // Clamp to [0, 1]: y > worldHeight would otherwise flip the sign and leak
            // phantom solid biomes at high y in BeyondEndBiomeSource queries.
            gradientTop = Math.max(0.0, (worldHeight - y) / 64.0);
        }

        return gradientBottom * gradientTop * noiseValue;
    }

    private static double globalNoiseOffset(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double noiseValue = noise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }

    /** Isotropic variant: averages noise at four 90° rotations to cancel lattice gradient bias. */
    private static double globalNoiseOffsetMultirotation(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double r0 = noise.getValue( x,  z, false);
        double r1 = noise.getValue(-z,  x, false);
        double r2 = noise.getValue(-x, -z, false);
        double r3 = noise.getValue( z, -x, false);
        double avg = (r0 + r1 + r2 + r3) * 0.25;
        return min + (max - min) * ((avg + 1) * 0.5);
    }

    /** 3x3 spatial-blur variant: mixes decorrelated adjacent simplex cells. */
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

    /** Distance-adaptive variant: amplitude decays 1/r beyond {@code refRadius} to bound ∂h/∂x·r. */
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

    /** Local-wrap variant: ping-pong-wraps the input to {@code [0, wrapRange]} before sampling. */
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
        // Wrap first so F3 matches what chunk gen actually sees at the same coords.
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
        BeyondTerrainState.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        computeNoisesIfNotPresent(random);
        BeyondTerrainState.setDimBounds(height.getMinBuildHeight(), height.getMaxBuildHeight());
        return super.getBaseColumn(x, z, height, random);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        computeNoisesIfNotPresent(random);
        BeyondTerrainState.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        super.buildSurface(level, structureManager, random, chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        BeyondTerrainState.setDimBounds(level.getMinBuildHeight(), level.getMaxBuildHeight());
        super.applyBiomeDecoration(level, chunk, structureManager);
        // Auroracite floor: owned by AuroraciteLayerFeature + AuroraciteLayerProtectionMixin.

        // Exit-portal column sweep: EndDragonFight.spawnExitPortal reads the
        // MOTION_BLOCKING_NO_LEAVES heightmap at (0,0) to place the obsidian podium.
        // In enlarged-dim combos (e.g. Enderscape), foreign decorators push that
        // column's heightmap up to build limit and the portal spawns at y≈319.
        // Fix: on chunk (0,0), clear column (0,0) from y=60 (just above Beyond's
        // central island dome) up to dim top so the heightmap re-primes onto
        // Beyond's END_STONE at y≈59. Scoped to one column only (platforms survive)
        // and gated on isActive() (soup mode leaves foreign owners alone).
        if (BeyondTerrainState.isActive()) {
            ChunkPos cp = chunk.getPos();
            if (cp.x == 0 && cp.z == 0) {
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                int top = chunk.getMaxBuildHeight() - 1;
                int floor = 60; // first y above Beyond's central island dome top at (0,0)
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

    /** Sums beardification from every valid structure start overlapping {@code (x, y, z)}. */
    private double calculateStructureAdaptation(List<StructureStart> validStarts, int x, int y, int z) {
        if (validStarts.isEmpty()) return 0.0;

        double adaptation = 0.0;
        // Indexed loop avoids Iterator allocation.
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
