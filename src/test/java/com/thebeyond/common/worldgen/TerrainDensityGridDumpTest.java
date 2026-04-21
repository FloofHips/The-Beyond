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
 * Diagnostic dump of {@link BeyondEndChunkGenerator#getTerrainDensity(int, int, int)}
 * over a 2D slice at fixed {@code y}, rendered as PNG for visual inspection of
 * directional anisotropy (stretching).
 *
 * <h2>Why this test exists</h2>
 * <p>Diagnoses directional anisotropy ("stretching") in the terrain density
 * field at arbitrary coordinates. The wrap+warp transform's documented
 * precision-safe zone is {@code ≤wrapRange} post-wrap (default 500 000) and
 * {@code <100 k} in warp sample space (the warp sample input
 * {@code globalX × warpScale} stays well below that). This test renders the
 * actual density function over a grid so any anisotropy is directly visible,
 * whether it originates at a geometric pivot or at an interior point.
 *
 * <p>Centres dumped:
 * <ul>
 *   <li><b>clean_50k</b>, <b>clean_150k</b> — interior reference regions
 *       well below any wrap pivot. Anisotropy here implicates a systemic
 *       cause (precision, modulator banding, cross-sample coupling)
 *       independent of wrap geometry.
 *   <li><b>x_250k</b> — {@code (250 154, -45)}. Deep interior at the
 *       default {@code wrapRange = 500 000}; all per-octave wraps are the
 *       identity here.
 *   <li><b>x_500k</b>, <b>x_750k</b>, <b>x_1M</b> — past 1, 1.5, and 2
 *       wrap cycles. 500 k sits exactly on the pivot plane; 750 k is the
 *       mirrored counterpart of 250 k; 1 M exercises double-reflected
 *       coordinates plus extreme per-octave spatial decorrelation (each
 *       octave samples an unrelated remote region).
 * </ul>
 *
 * <p>Variants per centre:
 * <ul>
 *   <li><b>density</b> — production {@code getTerrainDensity} with live
 *       {@code activeTerrainParams}.
 *   <li><b>cyclic</b> — isolation of the {@code cyclicDensity} modulator
 *       alone; reveals whether a centre's density anisotropy is driven by
 *       the spatial gradient of {@code cycleHeight}.
 *   <li><b>fixedcycle</b> — density with {@code cycleHeightOverride = 55}
 *       (a global constant). Flattens any anisotropy caused by
 *       {@code cycleHeight(x,z)} gradient.
 *   <li><b>period1m</b> — density with {@code cycleHeightFrequencyMultiplier = 0.1}
 *       (10× wider period than production). Preserves regional variety
 *       while eliminating in-view spatial gradient; acts as a sanity
 *       check that further widening the period produces no new artifact.
 * </ul>
 *
 * <h2>Output</h2>
 * PNGs land in {@code build/terrain-grid-dumps/}. Each pixel:
 * <ul>
 *   <li>Grayscale intensity = density normalized to [min, max] observed in
 *       that scenario (auto-contrast per image; absolute values are in the
 *       stdout log next to each dump).
 *   <li>Red tint = density exceeds threshold at that point, i.e. the chunk
 *       generator would place END_STONE here. Makes the terrain shape
 *       visible without needing to eyeball a grayscale threshold.
 * </ul>
 * Grid is {@link #GRID_SIZE}² samples spaced {@link #GRID_STRIDE} blocks
 * apart; total footprint per image is
 * {@code GRID_SIZE * GRID_STRIDE} blocks square. Current defaults give
 * 2 048×2 048 blocks at 2 blocks/pixel — wide enough to capture multiple
 * cycleHeight periods and reveal the OCTAVE_WRAP_FACTORS pivot pattern.
 *
 * <h2>Determinism</h2>
 * Uses {@link #NOISE_SEED} to init the same five-noise chain as
 * {@link BeyondEndChunkGenerator#computeNoisesIfNotPresent(long)}. The
 * PNGs are bit-exact reproducible across runs and machines.
 *
 * <h2>NOT a contract test</h2>
 * This test ALWAYS passes — its only failure mode is throwing on I/O. The
 * value is in the PNG artifacts, not in the assertion. Kept as {@code @Test}
 * rather than a {@code main()} so it runs under {@code ./gradlew test}
 * without extra wiring.
 */
class TerrainDensityGridDumpTest {

    /** Samples per axis. 1024 at stride 2 covers a 2 048-block window —
     *  wide enough that any anisotropy is hit in multiple periods. The
     *  resulting 4 MB PNG is acceptable for a diagnostic-only path. */
    private static final int GRID_SIZE = 1024;

    /** Blocks per sample. 2 keeps per-pixel fidelity while holding the
     *  window to a couple of cycleHeight periods. */
    private static final int GRID_STRIDE = 2;

    /** Sample altitude. 234 sits well below the edgeGradient top cutoff when
     *  the combo dim bounds are in effect (Enderscape bounds pack:
     *  [-64, 320) → worldHeight=288). With the default Beyond-só
     *  worldHeight=192, {@code y=234} would be above the cutoff and density
     *  would clamp to 0 everywhere, producing uniformly blank PNGs. See
     *  {@link BeyondEndChunkGenerator#edgeGradient}. */
    private static final int SAMPLE_Y = 234;

    /** Dim min Y. Matches the {@code beyond_enderscape_bounds} sidecar
     *  pack so the sampling reflects the extended-bounds environment in
     *  which terrain is actually generated when that pack is loaded. */
    private static final int SAMPLE_DIM_MIN_Y = -64;

    /** Dim max Y. See {@link #SAMPLE_DIM_MIN_Y}. */
    private static final int SAMPLE_DIM_MAX_Y = 320;

    /** Seed for noise init. Any stable value works; pinning it makes the
     *  PNGs bit-exact reproducible. */
    private static final long NOISE_SEED = 42L;

    /** Output directory. Under build/ so it's ignored and auto-cleaned. */
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

        // Mirror the production noise chain: 5 sequential seeds (the
        // SplitMix64 hash decorrelates them; no prime spacing needed).
        // See BeyondEndChunkGenerator.computeNoisesIfNotPresent.
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

        // Ensure terrain params at DEFAULTS so the snapshot is the reference
        // transform, regardless of state leaked from prior tests.
        BeyondEndChunkGenerator.activeTerrainParams = BeyondTerrainParams.DEFAULTS;

        // Combo dim bounds matching the Enderscape bounds sidecar pack.
        // getWorldHeight() uses these to scale the edgeGradient taper so
        // y=234 stays inside the generation zone; under the Beyond-only
        // default (0..256) edgeGradient would clamp density to 0 at that
        // altitude and produce uniformly blank PNGs.
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
    //
    // Each centre gets the full four-variant treatment (default, cyclic,
    // option2, period1m) so the table across centres is directly comparable.

    @Test
    void dumpX500k() throws IOException {
        // Exactly at the default wrapRange — the ping-pong pivot plane itself.
        dumpGrid(500000, 0, "x_500k");
    }

    @Test
    void dumpX750k() throws IOException {
        // Past the pivot; wraps back to |500 000 - 250 000| = 250 000, the
        // mirrored counterpart of x_250k.
        dumpGrid(750000, 0, "x_750k");
    }

    @Test
    void dumpX1M() throws IOException {
        // Two pivots out; wrap reflects back to the origin.
        dumpGrid(1000000, 0, "x_1M");
    }

    // ---------- isolation dump: cyclicDensity field only ----------

    /**
     * Renders {@code cyclicDensity(SAMPLE_Y, cycleHeight(x, z))} alone at each
     * of the three scenario centers — no simplex contribution, no
     * edgeGradient, no threshold overlay. Purpose: isolate whether the
     * directional banding visible in the corresponding {@code density_*}
     * PNGs originates from the {@code y % cycleHeight} modulation at a fixed
     * {@code y} slice.
     *
     * <p>If the isocontours in these images match the band direction seen in
     * the matching density image at the same center, the diagnosis is
     * confirmed and the fix target is {@code BeyondEndChunkGenerator.cyclicDensity}.
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
     * pinned to a single global constant ({@value #FIXED_CYCLE_HEIGHT_OVERRIDE}).
     * Makes {@code cycleHeight} independent of {@code (x, z)}, so at a fixed
     * {@code y} slice the modulator becomes a uniform scalar and any remaining
     * directional banding can only come from the simplex blob field itself.
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
     * Widens {@code cycleHeight}'s spatial period to 10× the production
     * default by setting {@link BeyondEndChunkGenerator#cycleHeightFrequencyMultiplier}
     * to {@value #PERIOD_1M_FREQUENCY_MULTIPLIER}. Sanity check that a
     * flatter spatial gradient of {@code cycleHeight} does not reintroduce
     * banding anywhere — i.e. the period scales monotonically with
     * anisotropy suppression.
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
    //
    // Runs the density dump with {@link BeyondEndChunkGenerator#wrapDisabled}
    // set so ping-pong reflection is bypassed. Coordinates cover a 6×3 matrix
    // (6 distances × 3 axial directions) to check whether visible stretching
    // and chevron artifacts vanish once the reflective wrap is removed. The
    // grid is {@value #GRID_SIZE}² at stride {@value #GRID_STRIDE}, so each
    // PNG shows a {@code GRID_SIZE * GRID_STRIDE} = 2 048-block square around
    // the named center.
    //
    // Expected read-out per PNG:
    //   - clean organic red-blotch field → hScale protection is sufficient,
    //     no wrap needed
    //   - residual diagonal streaks on the x=z diagonal → warp correlation
    //     contributes independently of wrap (next diagnostic target)
    //   - axis-aligned streaks on X-only or Z-only → simplex lattice bias
    //     is visible without the wrap masking it

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
    // Each method pins one component of the density pipeline to a constant
    // (or disables it) so its contribution to streaks is observable in
    // isolation. Pipeline components:
    //   warp        — snoise/zsnoise adds per-column offset to globalX/globalZ
    //   hScale      — PerlinSimplexNoise (table-based) varies horizontal scale
    //   vScale      — PerlinSimplexNoise varies vertical scale
    //   cycleHeight — PerlinSimplexNoise varies the cyclic density modulator
    //   base        — HashSimplexNoise 3D sample with all of the above baked in
    // Focused at diag_250k / diag_500k as representative streaky regions.

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

    /** All overrides active: wrap off, warp off, hScale fixed, vScale fixed, cycleHeight fixed.
     *  Isolates base HashSimplexNoise 3D sampling. If streaks STILL appear, they're from
     *  the simplex lattice itself. */
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

    // Isolation matrix extended to 1M and 2M to verify hScale alone versus
    // all-pinned behaviour at extreme distances.
    @Test void dumpDiag1M_fixedHScale()     throws IOException { dumpFixedHScale(1_000_000, 1_000_000, "diag_1M_fixedHScale"); }
    @Test void dumpDiag1M_allFixed()        throws IOException { dumpAllFixed(1_000_000, 1_000_000, "diag_1M_allFixed"); }
    @Test void dumpDiag2M_fixedHScale()     throws IOException { dumpFixedHScale(2_000_000, 2_000_000, "diag_2M_fixedHScale"); }
    @Test void dumpDiag2M_allFixed()        throws IOException { dumpAllFixed(2_000_000, 2_000_000, "diag_2M_allFixed"); }

    // ---------- hScale multi-rotation averaging ----------
    // Averages the hScale sample over four 90°-rotations of the input so the
    // directional lattice bias cancels while each point still receives a
    // distinct scalar. Exercised at diag_* distances to probe isotropy.

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
    // Swaps the table-based PerlinSimplexNoise for HashSimplexNoise (same
    // lattice, hash-based permutation) to separate table-driven bias from
    // lattice-driven bias.

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
    // Smooths the hScale sample over a 3×3 neighbourhood of simplex cells.
    // Adjacent cells carry decorrelated gradients, so cell-local directional
    // bias averages out.

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
    // Attenuates hScale amplitude as 1/r beyond a reference radius so the
    // (∂h/∂x)·r streak-width product stays bounded at any distance. Full
    // amplitude inside refRadius (~100 k) keeps near-origin regional variety.

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
    // Bounds the hScale noise INPUT via a dedicated ping-pong wrap
    // (range 50 k = period 100 k). Keeps amplitude full everywhere while
    // still capping streak-width, since the wrapped input is bounded
    // regardless of raw worldX/worldZ.

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
    // Runs hScaleBlur3x3 at six diagonal centres that exercise the
    // pingPongWrap stationarity pairs with range 500_000:
    //   (0,0)        ↔ (1M,1M)      ↔ (2M,2M)      all map to wrappedX=0
    //   (250k,250k)  ↔ (750k,750k)                 both map to wrappedX=250k
    //   (500k,500k)                               sits exactly on the pivot
    // Pass criteria:
    //   1) Zero visible streaks / anisotropy in any of the six PNGs.
    //   2) Pair members visually identical (pixel-exact for the 0/1M/2M
    //      group; mirrored for 250k/750k).
    //   3) Density value ranges printed in the log match within each pair.

    private void dumpBlur3x3Production(int centerX, int centerZ, String label) throws IOException {
        // NOTE: wrapDisabled intentionally left false — this is production mode.
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

    // ---------- Baseline dumps: production mode with no hScale knob ----------
    // Comparison reference for the prodBlur3x3_* matrix — same six diagonal centres,
    // all knobs at their defaults. Any artifact shared between the two sets
    // is pre-existing in the stock pipeline; anything unique to prodG is
    // introduced by the 3×3 blur.

    @Test void dumpProdBaseline_diag0()    throws IOException { dumpGrid(0,         0,         "prodBase_diag_0"); }
    @Test void dumpProdBaseline_diag250k() throws IOException { dumpGrid(250_000,   250_000,   "prodBase_diag_250k"); }
    @Test void dumpProdBaseline_diag500k() throws IOException { dumpGrid(500_000,   500_000,   "prodBase_diag_500k"); }
    @Test void dumpProdBaseline_diag750k() throws IOException { dumpGrid(750_000,   750_000,   "prodBase_diag_750k"); }
    @Test void dumpProdBaseline_diag1M()   throws IOException { dumpGrid(1_000_000, 1_000_000, "prodBase_diag_1M"); }
    @Test void dumpProdBaseline_diag2M()   throws IOException { dumpGrid(2_000_000, 2_000_000, "prodBase_diag_2M"); }

    // ---------- Island-envelope sampler validation ----------
    // Forces hScale constant so the X × ∂hScale/∂X streak term vanishes
    // identically at all distances; regional island-size variance is moved
    // into a slowly-varying amplitude envelope (X-independent factor). Runs
    // with wrapDisabled=true to confirm streak-freeness is structural and
    // not wrap-masked.
    // Coverage: diag_0 reference, 250k/500k/750k (baseline streaked),
    //           1M/2M (baseline clean), 5M/10M (scale-freedom stress).
    // Pass criteria:
    //   1) Zero streaks / directional bias at every centre including 5M/10M.
    //   2) Visible regional variation in solid-cell density across centres
    //      (envelope is doing its job — not a flat uniform amplitude).
    //   3) No catastrophic precision loss at 10M (density range stays
    //      near [-0.3, 0.3]).

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

    // Axis-only variants at a challenging distance (pure X streak test, pure Z
    // streak test). Proves isotropy claim: if streaks appear on X-only or Z-only
    // but not both, there's a lurking asymmetry in the sample pipeline.

    @Test void dumpEnvelope_xOnly1M()  throws IOException { dumpIslandEnvelope(1_000_000,  0,          "envelope_xOnly_1M"); }
    @Test void dumpEnvelope_zOnly1M()  throws IOException { dumpIslandEnvelope(0,          1_000_000,  "envelope_zOnly_1M"); }

    // ---------- Band-blend sampler validation ----------
    // Runs the band-blend hot-path (2-sample lerp over adjacent fixed-
    // frequency bands) with wrapDisabled=true to confirm streak-freeness is
    // structural. Same matrix as the island-envelope block plus axis-only
    // variants at 1M for directional-asymmetry checks.
    // Pass criteria:
    //   a) Zero streaks at every centre, at every distance (5M/10M included).
    //   b) Regional wavelength variance visible across centres — different
    //      island sizes, not just different amplitudes.
    //   c) (0,0) output matches the stock pipeline's local character (the
    //      blend at h* ≈ hBase is ~identical to simplex(X × h*, Z × h*) for
    //      hBase near one of the band centres).

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

    // Axis-only variants at a challenging distance — proves isotropy.
    @Test void dumpBlend_xOnly1M()  throws IOException { dumpBandBlend(1_000_000,  0,          "blend_xOnly_1M"); }
    @Test void dumpBlend_zOnly1M()  throws IOException { dumpBandBlend(0,          1_000_000,  "blend_zOnly_1M"); }

    // ---------- implementation ----------

    /**
     * Samples a {@code GRID_SIZE × GRID_STRIDE}-block grid centered on
     * {@code (centerX, centerZ)} at {@link #SAMPLE_Y}, renders density as
     * PNG, and writes the file to {@link #OUTPUT_DIR}.
     *
     * <p>Density normalization is PER-IMAGE (auto-contrast). This makes
     * subtle directional patterns visible regardless of absolute density
     * magnitude at that location, which differs between scenarios (the
     * threshold curve depends on distanceFromOrigin). The raw min/max are
     * printed to stdout so absolute values remain inspectable.
     *
     * <p>Solid pixels (density &gt; threshold) are red-tinted so the
     * terrain SHAPE is visible overlaid on the density field — if the
     * stretching is structural (solid cells form streaks) it shows up
     * directly as red bands, not just as a grayscale bias.
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
                // Sample threshold in wrapped coord space to match the chunk generator —
                // getTerrainDensity() wraps internally and getThreshold MUST be called on
                // the same (wrappedX, wrappedZ) pair or the "solid" red tint in the PNG
                // would disagree with where the generator actually places END_STONE.
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
                    // Red-tinted solid — terrain shape is the RED BAND pattern.
                    int r = Math.min(255, gray + 80);
                    int g = gray / 2;
                    int b = gray / 4;
                    rgb = (r << 16) | (g << 8) | b;
                } else {
                    rgb = (gray << 16) | (gray << 8) | gray;
                }

                // Flip Y so +Z is up in the image (standard cartographic
                // convention). The image's origin is top-left; our +Z points
                // into the map as the player sees it.
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
     * over the same grid shape as {@link #dumpGrid} and writes a grayscale
     * PNG. Mirrors the wrapping behaviour of the chunk generator
     * ({@code cycleHeight} is always sampled at the octave-0 wrapped coords
     * in the production path), so the output is directly comparable to the
     * density images at the same center.
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
     * {@code BeyondEndChunkGenerator.cyclicDensity(int, double)} used by the
     * terrain generator. Kept here so the isolation dumps can sample the
     * field without exposing a package-private helper in production code.
     * Any change to the production formula must be mirrored here or the
     * diagnostic loses meaning.
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
