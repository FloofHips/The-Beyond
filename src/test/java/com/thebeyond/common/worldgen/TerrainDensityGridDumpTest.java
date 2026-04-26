package com.thebeyond.common.worldgen;

import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic PNG dumps of {@link BeyondEndChunkGenerator#getTerrainDensity(int, int, int)}
 * over a 2D slice at fixed {@code y}, for visual inspection of directional
 * anisotropy ("stretching") in the density field.
 *
 * <p>NOT a contract test: assertions only guard against NaN/Infinity. The
 * value is in the PNG artifacts under {@code build/terrain-grid-dumps/}.
 * Kept as {@code @Test} so it runs under {@code ./gradlew test} without
 * extra wiring.
 *
 * <p>Centres cover interior references ({@code clean_*}), the wrap pivot
 * ({@code x_500k}), mirrored counterparts ({@code x_750k}), and doubly-
 * reflected coordinates ({@code x_1M}). Variants per centre: full density,
 * {@code cyclicDensity} modulator in isolation, fixed {@code cycleHeight}
 * (removes spatial gradient), and 10× wider period.
 *
 * <p>PNG pixels: grayscale = density auto-contrasted per image; red tint =
 * density exceeds threshold (END_STONE placement). Grid defaults to
 * {@code GRID_SIZE}² samples at {@code GRID_STRIDE} blocks/pixel (2 048-block
 * window at 2 blocks/pixel). Bit-exact reproducible via {@link #NOISE_SEED}.
 */
class TerrainDensityGridDumpTest {

    /** Samples per axis; 1024 at stride 2 = 2 048-block window. */
    private static final int GRID_SIZE = 1024;

    /** Blocks per sample. */
    private static final int GRID_STRIDE = 2;

    /** Sample altitude. Sits below the edgeGradient top cutoff when the
     *  extended-bounds (Enderscape pack: [-64, 320)) is in effect. Under the
     *  default Beyond-only bounds y=234 clamps to 0 everywhere and produces
     *  blank PNGs — see {@link BeyondEndChunkGenerator#edgeGradient}. */
    private static final int SAMPLE_Y = 234;

    /** Dim min Y — matches {@code beyond_enderscape_bounds} sidecar pack. */
    private static final int SAMPLE_DIM_MIN_Y = -64;

    /** Dim max Y — see {@link #SAMPLE_DIM_MIN_Y}. */
    private static final int SAMPLE_DIM_MAX_Y = 320;

    /** Pinned seed for bit-exact reproducible PNGs. */
    private static final long NOISE_SEED = 42L;

    /** Output directory (ignored/auto-cleaned under build/). */
    private static final File OUTPUT_DIR = new File("build/terrain-grid-dumps");

    // ---------- state capture ----------

    private HashSimplexNoise savedSimplex;
    private HashSimplexNoise savedBiomeSimplex;
    private PerlinSimplexNoise savedH;
    private PerlinSimplexNoise savedV;
    private PerlinSimplexNoise savedC;
    private BeyondTerrainParams savedParams;
    private int savedMinY;
    private int savedMaxY;

    @BeforeEach
    void setUp() {
        savedSimplex = BeyondEndChunkGenerator.simplexNoise;
        savedBiomeSimplex = BeyondEndChunkGenerator.biomeSimplexNoise;
        savedH = BeyondEndChunkGenerator.globalHOffsetNoise;
        savedV = BeyondEndChunkGenerator.globalVOffsetNoise;
        savedC = BeyondEndChunkGenerator.globalCOffsetNoise;
        savedParams = BeyondEndChunkGenerator.activeTerrainParams;
        savedMinY = BeyondTerrainState.getDimMinY();
        savedMaxY = BeyondTerrainState.getDimMaxY();

        // Mirror the production noise chain (5 sequential seeds, SplitMix64
        // hash decorrelates them). See BeyondEndChunkGenerator.computeNoisesIfNotPresent.
        RandomSource r1 = RandomSource.create(NOISE_SEED);
        RandomSource r2 = RandomSource.create(NOISE_SEED + 1);
        RandomSource r3 = RandomSource.create(NOISE_SEED + 2);
        RandomSource r4 = RandomSource.create(NOISE_SEED + 3);
        RandomSource r5 = RandomSource.create(NOISE_SEED + 4);

        BeyondEndChunkGenerator.simplexNoise = new HashSimplexNoise(r1);
        BeyondEndChunkGenerator.globalHOffsetNoise = new PerlinSimplexNoise(r2, List.of(1));
        BeyondEndChunkGenerator.globalVOffsetNoise = new PerlinSimplexNoise(r3, List.of(1));
        BeyondEndChunkGenerator.globalCOffsetNoise = new PerlinSimplexNoise(r4, List.of(1));
        BeyondEndChunkGenerator.biomeSimplexNoise = new HashSimplexNoise(r5);

        // Force DEFAULTS so the snapshot is the reference transform,
        // regardless of state leaked from prior tests.
        BeyondEndChunkGenerator.activeTerrainParams = BeyondTerrainParams.DEFAULTS;

        // Extended dim bounds (Enderscape pack) — needed so SAMPLE_Y sits
        // inside the edgeGradient taper zone; otherwise density clamps to 0.
        BeyondTerrainState.setDimBounds(SAMPLE_DIM_MIN_Y, SAMPLE_DIM_MAX_Y);

        //noinspection ResultOfMethodCallIgnored
        OUTPUT_DIR.mkdirs();
    }

    @AfterEach
    void tearDown() {
        BeyondEndChunkGenerator.simplexNoise = savedSimplex;
        BeyondEndChunkGenerator.biomeSimplexNoise = savedBiomeSimplex;
        BeyondEndChunkGenerator.globalHOffsetNoise = savedH;
        BeyondEndChunkGenerator.globalVOffsetNoise = savedV;
        BeyondEndChunkGenerator.globalCOffsetNoise = savedC;
        BeyondEndChunkGenerator.activeTerrainParams = savedParams;
        BeyondTerrainState.setDimBounds(savedMinY, savedMaxY);
    }

    // ---------- the three scenarios ----------

    @Test
    void dumpX250k() throws IOException {
        dumpGrid(250154, -45, "x_250k");
    }

    @Test
    void dumpClean50k() throws IOException {
        dumpGrid(50000, 0, "clean_50k");
    }

    @Test
    void dumpClean150k() throws IOException {
        dumpGrid(150000, 0, "clean_150k");
    }

    // ---------- extended coverage: 500 k / 750 k / 1 M ----------

    @Test
    void dumpX500k() throws IOException {
        // Exactly on the default wrapRange pivot plane.
        dumpGrid(500000, 0, "x_500k");
    }

    @Test
    void dumpX750k() throws IOException {
        // Past the pivot; wraps to the mirrored counterpart of x_250k.
        dumpGrid(750000, 0, "x_750k");
    }

    @Test
    void dumpX1M() throws IOException {
        // Two pivots out; wraps back to the origin.
        dumpGrid(1000000, 0, "x_1M");
    }

    // ---------- isolation dump: cyclicDensity field only ----------

    /**
     * Renders {@code cyclicDensity(SAMPLE_Y, cycleHeight(x, z))} alone (no
     * simplex, no edgeGradient, no threshold overlay). Isolates whether
     * directional banding in the matching {@code density_*} PNGs originates
     * from the {@code y % cycleHeight} modulation at a fixed {@code y} slice.
     */
    @Test
    void dumpCyclicFieldClean50k() throws IOException {
        dumpCyclicGrid(50000, 0, "cyclic_clean_50k");
    }

    @Test
    void dumpCyclicFieldClean150k() throws IOException {
        dumpCyclicGrid(150000, 0, "cyclic_clean_150k");
    }

    @Test
    void dumpCyclicFieldX250k() throws IOException {
        dumpCyclicGrid(250154, -45, "cyclic_x_250k");
    }

    @Test
    void dumpCyclicFieldX500k() throws IOException {
        dumpCyclicGrid(500000, 0, "cyclic_x_500k");
    }

    @Test
    void dumpCyclicFieldX750k() throws IOException {
        dumpCyclicGrid(750000, 0, "cyclic_x_750k");
    }

    @Test
    void dumpCyclicFieldX1M() throws IOException {
        dumpCyclicGrid(1000000, 0, "cyclic_x_1M");
    }

    // ---------- Fixed global cycleHeight validation ----------

    /**
     * Dumps density with {@link BeyondEndChunkGenerator#cycleHeightOverride}
     * pinned to {@value #FIXED_CYCLE_HEIGHT_OVERRIDE}. Flattens
     * {@code cycleHeight} so any remaining directional banding must come from
     * the simplex blob field itself.
     */
    private static final double FIXED_CYCLE_HEIGHT_OVERRIDE = 55.0;

    @Test
    void dumpDensityFixedCycleClean50k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightOverride = FIXED_CYCLE_HEIGHT_OVERRIDE;
        try {
            dumpGrid(50000, 0, "fixedcycle_clean_50k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    @Test
    void dumpDensityFixedCycleClean150k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightOverride = FIXED_CYCLE_HEIGHT_OVERRIDE;
        try {
            dumpGrid(150000, 0, "fixedcycle_clean_150k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    @Test
    void dumpDensityFixedCycleX250k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightOverride = FIXED_CYCLE_HEIGHT_OVERRIDE;
        try {
            dumpGrid(250154, -45, "fixedcycle_x_250k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    @Test
    void dumpDensityFixedCycleX500k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightOverride = FIXED_CYCLE_HEIGHT_OVERRIDE;
        try {
            dumpGrid(500000, 0, "fixedcycle_x_500k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    @Test
    void dumpDensityFixedCycleX750k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightOverride = FIXED_CYCLE_HEIGHT_OVERRIDE;
        try {
            dumpGrid(750000, 0, "fixedcycle_x_750k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    @Test
    void dumpDensityFixedCycleX1M() throws IOException {
        BeyondEndChunkGenerator.cycleHeightOverride = FIXED_CYCLE_HEIGHT_OVERRIDE;
        try {
            dumpGrid(1000000, 0, "fixedcycle_x_1M");
        } finally {
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    // ---------- sanity check: 10× wider cycleHeight period ----------

    /**
     * Widens {@code cycleHeight}'s spatial period to 10× the production default
     * via {@link BeyondEndChunkGenerator#cycleHeightFrequencyMultiplier} =
     * {@value #PERIOD_1M_FREQUENCY_MULTIPLIER}. Sanity check that a flatter
     * {@code cycleHeight} gradient does not reintroduce banding.
     */
    private static final double PERIOD_1M_FREQUENCY_MULTIPLIER = 0.1;

    @Test
    void dumpPeriod1MClean50k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = PERIOD_1M_FREQUENCY_MULTIPLIER;
        try {
            dumpGrid(50000, 0, "period1m_clean_50k");
            dumpCyclicGrid(50000, 0, "period1m_cyclic_clean_50k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = 1.0;
        }
    }

    @Test
    void dumpPeriod1MClean150k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = PERIOD_1M_FREQUENCY_MULTIPLIER;
        try {
            dumpGrid(150000, 0, "period1m_clean_150k");
            dumpCyclicGrid(150000, 0, "period1m_cyclic_clean_150k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = 1.0;
        }
    }

    @Test
    void dumpPeriod1MX250k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = PERIOD_1M_FREQUENCY_MULTIPLIER;
        try {
            dumpGrid(250154, -45, "period1m_x_250k");
            dumpCyclicGrid(250154, -45, "period1m_cyclic_x_250k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = 1.0;
        }
    }

    @Test
    void dumpPeriod1MX500k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = PERIOD_1M_FREQUENCY_MULTIPLIER;
        try {
            dumpGrid(500000, 0, "period1m_x_500k");
            dumpCyclicGrid(500000, 0, "period1m_cyclic_x_500k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = 1.0;
        }
    }

    @Test
    void dumpPeriod1MX750k() throws IOException {
        BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = PERIOD_1M_FREQUENCY_MULTIPLIER;
        try {
            dumpGrid(750000, 0, "period1m_x_750k");
            dumpCyclicGrid(750000, 0, "period1m_cyclic_x_750k");
        } finally {
            BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = 1.0;
        }
    }

    @Test
    void dumpPeriod1MX1M() throws IOException {
        BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = PERIOD_1M_FREQUENCY_MULTIPLIER;
        try {
            dumpGrid(1000000, 0, "period1m_x_1M");
            dumpCyclicGrid(1000000, 0, "period1m_cyclic_x_1M");
        } finally {
            BeyondEndChunkGenerator.cycleHeightFrequencyMultiplier = 1.0;
        }
    }

    // ---------- wrap-disabled validation sweep ----------
    // Bypasses ping-pong reflection via {@link BeyondEndChunkGenerator#wrapDisabled}.
    // 6 distances × 3 axial directions matrix — probes whether streaks/chevron
    // artifacts vanish once the reflective wrap is removed. Read-out per PNG:
    //   - clean field → hScale protection sufficient, no wrap needed
    //   - diagonal streaks on x=z → warp correlation (wrap-independent)
    //   - axis-aligned streaks → simplex lattice bias unmasked

    private void dumpNoWrap(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
        }
    }

    // X-only axis (globalZ = 0)

    @Test void dumpNoWrapX100k()  throws IOException { dumpNoWrap(100_000,   0, "nowrap_x_100k"); }
    @Test void dumpNoWrapX250k()  throws IOException { dumpNoWrap(250_000,   0, "nowrap_x_250k"); }
    @Test void dumpNoWrapX500k()  throws IOException { dumpNoWrap(500_000,   0, "nowrap_x_500k"); }
    @Test void dumpNoWrapX750k()  throws IOException { dumpNoWrap(750_000,   0, "nowrap_x_750k"); }
    @Test void dumpNoWrapX1M()    throws IOException { dumpNoWrap(1_000_000, 0, "nowrap_x_1M"); }
    @Test void dumpNoWrapX2M()    throws IOException { dumpNoWrap(2_000_000, 0, "nowrap_x_2M"); }

    // Z-only axis (globalX = 0)

    @Test void dumpNoWrapZ100k()  throws IOException { dumpNoWrap(0, 100_000,   "nowrap_z_100k"); }
    @Test void dumpNoWrapZ250k()  throws IOException { dumpNoWrap(0, 250_000,   "nowrap_z_250k"); }
    @Test void dumpNoWrapZ500k()  throws IOException { dumpNoWrap(0, 500_000,   "nowrap_z_500k"); }
    @Test void dumpNoWrapZ750k()  throws IOException { dumpNoWrap(0, 750_000,   "nowrap_z_750k"); }
    @Test void dumpNoWrapZ1M()    throws IOException { dumpNoWrap(0, 1_000_000, "nowrap_z_1M"); }
    @Test void dumpNoWrapZ2M()    throws IOException { dumpNoWrap(0, 2_000_000, "nowrap_z_2M"); }

    // Diagonal (globalX = globalZ)

    @Test void dumpNoWrapDiag100k() throws IOException { dumpNoWrap(100_000,   100_000,   "nowrap_diag_100k"); }
    @Test void dumpNoWrapDiag250k() throws IOException { dumpNoWrap(250_000,   250_000,   "nowrap_diag_250k"); }
    @Test void dumpNoWrapDiag500k() throws IOException { dumpNoWrap(500_000,   500_000,   "nowrap_diag_500k"); }
    @Test void dumpNoWrapDiag750k() throws IOException { dumpNoWrap(750_000,   750_000,   "nowrap_diag_750k"); }
    @Test void dumpNoWrapDiag1M()   throws IOException { dumpNoWrap(1_000_000, 1_000_000, "nowrap_diag_1M"); }
    @Test void dumpNoWrapDiag2M()   throws IOException { dumpNoWrap(2_000_000, 2_000_000, "nowrap_diag_2M"); }

    // ---------- Diagnostic isolation tests ----------
    // Each helper pins one component of the density pipeline (or disables it)
    // so its contribution to streaks is observable in isolation. Components:
    //   warp        — snoise/zsnoise per-column offset to globalX/globalZ
    //   hScale      — PerlinSimplexNoise (table-based) horizontal scale
    //   vScale      — PerlinSimplexNoise vertical scale
    //   cycleHeight — PerlinSimplexNoise cyclic density modulator
    //   base        — HashSimplexNoise 3D sample with all above baked in

    /** Disables the domain warp, keeps wrap disabled. Isolates warp contribution. */
    private void dumpNoWarpOnly(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.warpDisabled = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.warpDisabled = false;
        }
    }

    /** Pins hScale to a constant (mean of its sampled range), keeps wrap disabled. */
    private void dumpFixedHScale(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.hScaleOverride = 0.010; // mean of [0.005, 0.015]
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.hScaleOverride = null;
        }
    }

    /** Pins vScale to a constant, keeps wrap disabled. */
    private void dumpFixedVScale(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.vScaleOverride = 0.010;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.vScaleOverride = null;
        }
    }

    /** Pins cycleHeight to a constant, keeps wrap disabled. */
    private void dumpFixedCycleHeight(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.cycleHeightOverride = 50.0;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    /** All overrides active: wrap off, warp off, hScale/vScale/cycleHeight pinned.
     *  Isolates base HashSimplexNoise 3D sampling — any remaining streaks are
     *  from the simplex lattice itself. */
    private void dumpAllFixed(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.warpDisabled = true;
        BeyondEndChunkGenerator.hScaleOverride = 0.010;
        BeyondEndChunkGenerator.vScaleOverride = 0.010;
        BeyondEndChunkGenerator.cycleHeightOverride = 50.0;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.warpDisabled = false;
            BeyondEndChunkGenerator.hScaleOverride = null;
            BeyondEndChunkGenerator.vScaleOverride = null;
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }

    // diag_250k component isolations

    @Test void dumpDiag250k_noWarp()        throws IOException { dumpNoWarpOnly(250_000, 250_000, "diag_250k_noWarp"); }
    @Test void dumpDiag250k_fixedHScale()   throws IOException { dumpFixedHScale(250_000, 250_000, "diag_250k_fixedHScale"); }
    @Test void dumpDiag250k_fixedVScale()   throws IOException { dumpFixedVScale(250_000, 250_000, "diag_250k_fixedVScale"); }
    @Test void dumpDiag250k_fixedCycleH()   throws IOException { dumpFixedCycleHeight(250_000, 250_000, "diag_250k_fixedCycleH"); }
    @Test void dumpDiag250k_allFixed()      throws IOException { dumpAllFixed(250_000, 250_000, "diag_250k_allFixed"); }

    // diag_500k component isolations

    @Test void dumpDiag500k_noWarp()        throws IOException { dumpNoWarpOnly(500_000, 500_000, "diag_500k_noWarp"); }
    @Test void dumpDiag500k_fixedHScale()   throws IOException { dumpFixedHScale(500_000, 500_000, "diag_500k_fixedHScale"); }
    @Test void dumpDiag500k_fixedVScale()   throws IOException { dumpFixedVScale(500_000, 500_000, "diag_500k_fixedVScale"); }
    @Test void dumpDiag500k_fixedCycleH()   throws IOException { dumpFixedCycleHeight(500_000, 500_000, "diag_500k_fixedCycleH"); }
    @Test void dumpDiag500k_allFixed()      throws IOException { dumpAllFixed(500_000, 500_000, "diag_500k_allFixed"); }

    // Extended to 1M/2M to check hScale-alone vs all-pinned at extreme distances.
    @Test void dumpDiag1M_fixedHScale()     throws IOException { dumpFixedHScale(1_000_000, 1_000_000, "diag_1M_fixedHScale"); }
    @Test void dumpDiag1M_allFixed()        throws IOException { dumpAllFixed(1_000_000, 1_000_000, "diag_1M_allFixed"); }
    @Test void dumpDiag2M_fixedHScale()     throws IOException { dumpFixedHScale(2_000_000, 2_000_000, "diag_2M_fixedHScale"); }
    @Test void dumpDiag2M_allFixed()        throws IOException { dumpAllFixed(2_000_000, 2_000_000, "diag_2M_allFixed"); }

    // ---------- hScale multi-rotation averaging ----------
    // Averages hScale over four 90° input rotations so directional lattice
    // bias cancels while each point still receives a distinct scalar.

    private void dumpMultirotation(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.hScaleMultirotation = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.hScaleMultirotation = false;
        }
    }

    @Test void dumpDiag250k_multiRot() throws IOException { dumpMultirotation(250_000,   250_000,   "diag_250k_multiRot"); }
    @Test void dumpDiag500k_multiRot() throws IOException { dumpMultirotation(500_000,   500_000,   "diag_500k_multiRot"); }
    @Test void dumpDiag1M_multiRot()   throws IOException { dumpMultirotation(1_000_000, 1_000_000, "diag_1M_multiRot"); }
    @Test void dumpDiag2M_multiRot()   throws IOException { dumpMultirotation(2_000_000, 2_000_000, "diag_2M_multiRot"); }

    // ---------- hScale via HashSimplexNoise (hash-based permutation) ----------
    // Swaps table-based PerlinSimplexNoise for HashSimplexNoise (same lattice,
    // hash permutation) to separate table-driven bias from lattice-driven bias.

    private void dumpHashNoise(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.hScaleUseHashNoise = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.hScaleUseHashNoise = false;
        }
    }

    @Test void dumpDiag250k_hashNoise() throws IOException { dumpHashNoise(250_000,   250_000,   "diag_250k_hashNoise"); }
    @Test void dumpDiag500k_hashNoise() throws IOException { dumpHashNoise(500_000,   500_000,   "diag_500k_hashNoise"); }
    @Test void dumpDiag1M_hashNoise()   throws IOException { dumpHashNoise(1_000_000, 1_000_000, "diag_1M_hashNoise"); }
    @Test void dumpDiag2M_hashNoise()   throws IOException { dumpHashNoise(2_000_000, 2_000_000, "diag_2M_hashNoise"); }

    // ---------- hScale via 3×3 spatial blur ----------
    // Smooths hScale over a 3×3 neighbourhood of simplex cells so cell-local
    // directional bias averages out across decorrelated gradients.

    private void dumpBlur3x3(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.hScaleBlur3x3 = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.hScaleBlur3x3 = false;
        }
    }

    @Test void dumpDiag250k_blur3x3() throws IOException { dumpBlur3x3(250_000,   250_000,   "diag_250k_blur3x3"); }
    @Test void dumpDiag500k_blur3x3() throws IOException { dumpBlur3x3(500_000,   500_000,   "diag_500k_blur3x3"); }
    @Test void dumpDiag1M_blur3x3()   throws IOException { dumpBlur3x3(1_000_000, 1_000_000, "diag_1M_blur3x3"); }
    @Test void dumpDiag2M_blur3x3()   throws IOException { dumpBlur3x3(2_000_000, 2_000_000, "diag_2M_blur3x3"); }

    // ---------- Distance-adaptive hScale amplitude ----------
    // Attenuates hScale amplitude as 1/r beyond a reference radius (~100k) to
    // keep the (∂h/∂x)·r streak-width product bounded at any distance while
    // preserving near-origin regional variety.

    private void dumpDistanceAdaptive(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.hScaleDistanceAdaptive = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.hScaleDistanceAdaptive = false;
        }
    }

    @Test void dumpDiag250k_distAdapt() throws IOException { dumpDistanceAdaptive(250_000,   250_000,   "diag_250k_distAdapt"); }
    @Test void dumpDiag500k_distAdapt() throws IOException { dumpDistanceAdaptive(500_000,   500_000,   "diag_500k_distAdapt"); }
    @Test void dumpDiag1M_distAdapt()   throws IOException { dumpDistanceAdaptive(1_000_000, 1_000_000, "diag_1M_distAdapt"); }
    @Test void dumpDiag2M_distAdapt()   throws IOException { dumpDistanceAdaptive(2_000_000, 2_000_000, "diag_2M_distAdapt"); }

    // ---------- hScale local ping-pong wrap ----------
    // Bounds hScale noise INPUT via dedicated ping-pong wrap (range 50k, period
    // 100k). Full amplitude everywhere while capping streak-width, since the
    // wrapped input is bounded regardless of raw worldX/worldZ.

    private void dumpLocalWrap(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.hScaleLocalWrap = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.hScaleLocalWrap = false;
        }
    }

    @Test void dumpDiag250k_localWrap() throws IOException { dumpLocalWrap(250_000,   250_000,   "diag_250k_localWrap"); }
    @Test void dumpDiag500k_localWrap() throws IOException { dumpLocalWrap(500_000,   500_000,   "diag_500k_localWrap"); }
    @Test void dumpDiag1M_localWrap()   throws IOException { dumpLocalWrap(1_000_000, 1_000_000, "diag_1M_localWrap"); }
    @Test void dumpDiag2M_localWrap()   throws IOException { dumpLocalWrap(2_000_000, 2_000_000, "diag_2M_localWrap"); }

    // ---------- 3×3 blur with pingPongWrap ENABLED (production mode) ----------
    // hScaleBlur3x3 at six diagonal centres that exercise pingPongWrap
    // stationarity pairs (range 500_000):
    //   (0,0) ↔ (1M,1M) ↔ (2M,2M)   all → wrappedX=0
    //   (250k,250k) ↔ (750k,750k)   both → wrappedX=250k
    //   (500k,500k)                  exactly on the pivot
    // Pass criteria: (1) no streaks/anisotropy, (2) pair members visually
    // identical (pixel-exact for the 0/1M/2M group; mirrored for 250k/750k),
    // (3) logged density ranges match within each pair.

    private void dumpBlur3x3Production(int centerX, int centerZ, String label) throws IOException {
        // wrapDisabled intentionally left false — this is production mode.
        BeyondEndChunkGenerator.hScaleBlur3x3 = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.hScaleBlur3x3 = false;
        }
    }

    @Test void dumpProdBlur3x3_diag0()    throws IOException { dumpBlur3x3Production(0,         0,         "prodBlur3x3_diag_0"); }
    @Test void dumpProdBlur3x3_diag250k() throws IOException { dumpBlur3x3Production(250_000,   250_000,   "prodBlur3x3_diag_250k"); }
    @Test void dumpProdBlur3x3_diag500k() throws IOException { dumpBlur3x3Production(500_000,   500_000,   "prodBlur3x3_diag_500k"); }
    @Test void dumpProdBlur3x3_diag750k() throws IOException { dumpBlur3x3Production(750_000,   750_000,   "prodBlur3x3_diag_750k"); }
    @Test void dumpProdBlur3x3_diag1M()   throws IOException { dumpBlur3x3Production(1_000_000, 1_000_000, "prodBlur3x3_diag_1M"); }
    @Test void dumpProdBlur3x3_diag2M()   throws IOException { dumpBlur3x3Production(2_000_000, 2_000_000, "prodBlur3x3_diag_2M"); }

    // ---------- Baseline dumps: production mode, no hScale knob ----------
    // Reference for the prodBlur3x3_* matrix — same six centres, defaults.
    // Artifacts shared between sets are pre-existing; artifacts unique to
    // prodBlur3x3 are introduced by the 3×3 blur.

    @Test void dumpProdBaseline_diag0()    throws IOException { dumpGrid(0,         0,         "prodBase_diag_0"); }
    @Test void dumpProdBaseline_diag250k() throws IOException { dumpGrid(250_000,   250_000,   "prodBase_diag_250k"); }
    @Test void dumpProdBaseline_diag500k() throws IOException { dumpGrid(500_000,   500_000,   "prodBase_diag_500k"); }
    @Test void dumpProdBaseline_diag750k() throws IOException { dumpGrid(750_000,   750_000,   "prodBase_diag_750k"); }
    @Test void dumpProdBaseline_diag1M()   throws IOException { dumpGrid(1_000_000, 1_000_000, "prodBase_diag_1M"); }
    @Test void dumpProdBaseline_diag2M()   throws IOException { dumpGrid(2_000_000, 2_000_000, "prodBase_diag_2M"); }

    // ---------- Island-envelope sampler validation ----------
    // Forces hScale constant so the X·∂hScale/∂X streak term vanishes at all
    // distances; regional island-size variance moves into a slowly-varying
    // X-independent amplitude envelope. Runs with wrapDisabled=true to prove
    // streak-freeness is structural, not wrap-masked.
    // Coverage: diag_0 reference; 250k/500k/750k (baseline streaked); 1M/2M
    // (baseline clean); 5M/10M (scale-freedom stress).
    // Pass criteria: (1) zero streaks at every centre including 5M/10M,
    // (2) visible regional variation in solid-cell density (envelope working),
    // (3) density range stays near [-0.3, 0.3] at 10M (no precision loss).

    private void dumpIslandEnvelope(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.useIslandEnvelope = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.useIslandEnvelope = false;
        }
    }

    @Test void dumpEnvelope_diag0()    throws IOException { dumpIslandEnvelope(0,          0,          "envelope_diag_0"); }
    @Test void dumpEnvelope_diag250k() throws IOException { dumpIslandEnvelope(250_000,    250_000,    "envelope_diag_250k"); }
    @Test void dumpEnvelope_diag500k() throws IOException { dumpIslandEnvelope(500_000,    500_000,    "envelope_diag_500k"); }
    @Test void dumpEnvelope_diag750k() throws IOException { dumpIslandEnvelope(750_000,    750_000,    "envelope_diag_750k"); }
    @Test void dumpEnvelope_diag1M()   throws IOException { dumpIslandEnvelope(1_000_000,  1_000_000,  "envelope_diag_1M"); }
    @Test void dumpEnvelope_diag2M()   throws IOException { dumpIslandEnvelope(2_000_000,  2_000_000,  "envelope_diag_2M"); }
    @Test void dumpEnvelope_diag5M()   throws IOException { dumpIslandEnvelope(5_000_000,  5_000_000,  "envelope_diag_5M"); }
    @Test void dumpEnvelope_diag10M()  throws IOException { dumpIslandEnvelope(10_000_000, 10_000_000, "envelope_diag_10M"); }

    // Axis-only at a challenging distance — isotropy check. Streaks on X-only
    // xor Z-only indicate a lurking asymmetry in the sample pipeline.

    @Test void dumpEnvelope_xOnly1M()  throws IOException { dumpIslandEnvelope(1_000_000,  0,          "envelope_xOnly_1M"); }
    @Test void dumpEnvelope_zOnly1M()  throws IOException { dumpIslandEnvelope(0,          1_000_000,  "envelope_zOnly_1M"); }

    // ---------- Band-blend sampler validation ----------
    // Band-blend hot-path (2-sample lerp over adjacent fixed-frequency bands)
    // with wrapDisabled=true — confirms streak-freeness is structural. Same
    // matrix as island-envelope plus axis-only variants at 1M.
    // Pass criteria: (a) zero streaks at every centre (5M/10M included),
    // (b) regional wavelength variance visible across centres (different
    // island sizes, not just amplitudes), (c) (0,0) output matches stock
    // pipeline's local character (blend at h* ≈ hBase is ~identical to
    // simplex(X·h*, Z·h*) for hBase near a band centre).

    private void dumpBandBlend(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        BeyondEndChunkGenerator.useBandBlend = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
            BeyondEndChunkGenerator.useBandBlend = false;
        }
    }

    @Test void dumpBlend_diag0()    throws IOException { dumpBandBlend(0,          0,          "blend_diag_0"); }
    @Test void dumpBlend_diag250k() throws IOException { dumpBandBlend(250_000,    250_000,    "blend_diag_250k"); }
    @Test void dumpBlend_diag500k() throws IOException { dumpBandBlend(500_000,    500_000,    "blend_diag_500k"); }
    @Test void dumpBlend_diag750k() throws IOException { dumpBandBlend(750_000,    750_000,    "blend_diag_750k"); }
    @Test void dumpBlend_diag1M()   throws IOException { dumpBandBlend(1_000_000,  1_000_000,  "blend_diag_1M"); }
    @Test void dumpBlend_diag2M()   throws IOException { dumpBandBlend(2_000_000,  2_000_000,  "blend_diag_2M"); }
    @Test void dumpBlend_diag5M()   throws IOException { dumpBandBlend(5_000_000,  5_000_000,  "blend_diag_5M"); }
    @Test void dumpBlend_diag10M()  throws IOException { dumpBandBlend(10_000_000, 10_000_000, "blend_diag_10M"); }

    // Axis-only variants — isotropy check.
    @Test void dumpBlend_xOnly1M()  throws IOException { dumpBandBlend(1_000_000,  0,          "blend_xOnly_1M"); }
    @Test void dumpBlend_zOnly1M()  throws IOException { dumpBandBlend(0,          1_000_000,  "blend_zOnly_1M"); }

    // ---------- implementation ----------

    /**
     * Samples a {@code GRID_SIZE × GRID_STRIDE}-block grid centered on
     * {@code (centerX, centerZ)} at {@link #SAMPLE_Y} and writes a PNG to
     * {@link #OUTPUT_DIR}.
     *
     * <p>Density normalization is per-image (auto-contrast) so subtle
     * directional patterns are visible regardless of absolute magnitude; raw
     * min/max are logged to stdout. Solid pixels (density &gt; threshold) are
     * red-tinted so the terrain shape overlays the density field.
     */
    private void dumpGrid(int centerX, int centerZ, String label) throws IOException {
        BufferedImage image = new BufferedImage(GRID_SIZE, GRID_SIZE, BufferedImage.TYPE_INT_RGB);

        double[] densities = new double[GRID_SIZE * GRID_SIZE];
        double[] thresholds = new double[GRID_SIZE * GRID_SIZE];
        double minDensity = Double.POSITIVE_INFINITY;
        double maxDensity = Double.NEGATIVE_INFINITY;
        int solidCount = 0;

        final int half = GRID_SIZE / 2;
        for (int py = 0; py < GRID_SIZE; py++) {
            int globalZ = centerZ + (py - half) * GRID_STRIDE;
            for (int px = 0; px < GRID_SIZE; px++) {
                int globalX = centerX + (px - half) * GRID_STRIDE;

                double density = BeyondEndChunkGenerator.getTerrainDensity(globalX, SAMPLE_Y, globalZ);
                float distanceFromOrigin = (float) Math.sqrt(
                        (double) globalX * globalX + (double) globalZ * globalZ);
                // Threshold must be sampled in wrapped coord space to match
                // getTerrainDensity (which wraps internally); otherwise the red
                // tint disagrees with actual END_STONE placement.
                long packed = BeyondEndChunkGenerator.computeWrappedCoords(globalX, globalZ);
                int wrappedX = BeyondEndChunkGenerator.unpackWrappedX(packed);
                int wrappedZ = BeyondEndChunkGenerator.unpackWrappedZ(packed);
                double threshold = BeyondEndChunkGenerator.getThreshold(wrappedX, wrappedZ, distanceFromOrigin);

                int idx = py * GRID_SIZE + px;
                densities[idx] = density;
                thresholds[idx] = threshold;

                assertFalse(Double.isNaN(density), () -> "NaN density at " + globalX + "," + globalZ);
                assertFalse(Double.isInfinite(density), () -> "Infinite density at " + globalX + "," + globalZ);

                if (density < minDensity) minDensity = density;
                if (density > maxDensity) maxDensity = density;
                if (density > threshold) solidCount++;
            }
        }

        double span = Math.max(maxDensity - minDensity, 1e-9);
        for (int py = 0; py < GRID_SIZE; py++) {
            for (int px = 0; px < GRID_SIZE; px++) {
                int idx = py * GRID_SIZE + px;
                double d = densities[idx];
                double t = thresholds[idx];

                int gray = (int) (255.0 * (d - minDensity) / span);
                if (gray < 0) gray = 0;
                if (gray > 255) gray = 255;

                int rgb;
                if (d > t) {
                    // Red-tinted solid — terrain shape overlays density.
                    int r = Math.min(255, gray + 80);
                    int g = gray / 2;
                    int b = gray / 4;
                    rgb = (r << 16) | (g << 8) | b;
                } else {
                    rgb = (gray << 16) | (gray << 8) | gray;
                }

                // Flip Y so +Z is up (standard cartographic convention).
                image.setRGB(px, GRID_SIZE - 1 - py, rgb);
            }
        }

        String fileName = String.format("density_%s_center_%d_%d_y%d_stride%d.png",
                label, centerX, centerZ, SAMPLE_Y, GRID_STRIDE);
        File outFile = new File(OUTPUT_DIR, fileName);
        ImageIO.write(image, "png", outFile);

        int totalCells = GRID_SIZE * GRID_SIZE;
        System.out.printf(
                "[TerrainDensityGridDump] %s -> %s%n" +
                "  center=(%d, %d, y=%d)  span=%d blocks  stride=%d blocks/px%n" +
                "  density range=[%.4f, %.4f]  solid=%d/%d (%.1f%%)%n",
                label, outFile.getAbsolutePath(),
                centerX, centerZ, SAMPLE_Y, GRID_SIZE * GRID_STRIDE, GRID_STRIDE,
                minDensity, maxDensity, solidCount, totalCells,
                100.0 * solidCount / totalCells);
    }

    /**
     * Samples {@code cyclicDensity(SAMPLE_Y, cycleHeight(wrappedX, wrappedZ))}
     * over the same grid as {@link #dumpGrid} and writes a grayscale PNG.
     * Mirrors the chunk generator's wrapping ({@code cycleHeight} is sampled
     * at octave-0 wrapped coords in production) so output is directly
     * comparable to the density image at the same centre.
     */
    private void dumpCyclicGrid(int centerX, int centerZ, String label) throws IOException {
        BufferedImage image = new BufferedImage(GRID_SIZE, GRID_SIZE, BufferedImage.TYPE_INT_RGB);

        double[] values = new double[GRID_SIZE * GRID_SIZE];
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        double minCycleHeight = Double.POSITIVE_INFINITY;
        double maxCycleHeight = Double.NEGATIVE_INFINITY;

        final int half = GRID_SIZE / 2;
        for (int py = 0; py < GRID_SIZE; py++) {
            int globalZ = centerZ + (py - half) * GRID_STRIDE;
            for (int px = 0; px < GRID_SIZE; px++) {
                int globalX = centerX + (px - half) * GRID_STRIDE;

                long packed = BeyondEndChunkGenerator.computeWrappedCoords(globalX, globalZ);
                int wrappedX = BeyondEndChunkGenerator.unpackWrappedX(packed);
                int wrappedZ = BeyondEndChunkGenerator.unpackWrappedZ(packed);

                double cycleHeight = BeyondEndChunkGenerator.getCycleHeight(wrappedX, wrappedZ);
                double cyclic = replicateCyclicDensity(SAMPLE_Y, cycleHeight);

                int idx = py * GRID_SIZE + px;
                values[idx] = cyclic;
                if (cyclic < minValue) minValue = cyclic;
                if (cyclic > maxValue) maxValue = cyclic;
                if (cycleHeight < minCycleHeight) minCycleHeight = cycleHeight;
                if (cycleHeight > maxCycleHeight) maxCycleHeight = cycleHeight;
            }
        }

        double span = Math.max(maxValue - minValue, 1e-9);
        for (int py = 0; py < GRID_SIZE; py++) {
            for (int px = 0; px < GRID_SIZE; px++) {
                int idx = py * GRID_SIZE + px;
                double v = values[idx];
                int gray = (int) (255.0 * (v - minValue) / span);
                if (gray < 0) gray = 0;
                if (gray > 255) gray = 255;
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(px, GRID_SIZE - 1 - py, rgb);
            }
        }

        String fileName = String.format("%s_center_%d_%d_y%d_stride%d.png",
                label, centerX, centerZ, SAMPLE_Y, GRID_STRIDE);
        File outFile = new File(OUTPUT_DIR, fileName);
        ImageIO.write(image, "png", outFile);

        System.out.printf(
                "[TerrainDensityGridDump] %s -> %s%n" +
                "  center=(%d, %d, y=%d)  span=%d blocks  stride=%d blocks/px%n" +
                "  cyclicDensity range=[%.4f, %.4f]  cycleHeight range=[%.2f, %.2f]%n",
                label, outFile.getAbsolutePath(),
                centerX, centerZ, SAMPLE_Y, GRID_SIZE * GRID_STRIDE, GRID_STRIDE,
                minValue, maxValue, minCycleHeight, maxCycleHeight);
    }

    /**
     * Bit-exact replica of the private
     * {@code BeyondEndChunkGenerator.cyclicDensity(int, double)}. Allows
     * isolation dumps to sample the field without exposing a package-private
     * helper. Must be kept in sync with the production formula.
     */
    private static double replicateCyclicDensity(int y, double cycleHeight) {
        double normalizedY = (y % cycleHeight) / cycleHeight;
        if (normalizedY < 0.8) {
            return Math.sin((normalizedY / 0.8) * (Math.PI / 2));
        } else {
            return 1 - (normalizedY - 0.8) / 0.2;
        }
    }
}
