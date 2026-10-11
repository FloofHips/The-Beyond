package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
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
 * Diagnostic PNG dumps of {@link BeyondEndChunkGenerator#getTerrainDensity(int, int, int)} for spotting
 * directional anisotropy. Not a contract test — real output is the PNGs under {@code build/terrain-grid-dumps/}.
 */
class TerrainDensityGridDumpTest {

    private static final int GRID_SIZE = 1024;

    private static final int GRID_STRIDE = 2;

    /** Needs extended dim bounds (Enderscape pack) to sit under the edgeGradient cutoff —
     *  under default Beyond-only bounds this clamps to 0 and dumps blank PNGs. */
    private static final int SAMPLE_Y = 234;

    private static final int SAMPLE_DIM_MIN_Y = -64;

    private static final int SAMPLE_DIM_MAX_Y = 320;

    private static final long NOISE_SEED = 42L;

    private static final File OUTPUT_DIR = new File("build/terrain-grid-dumps");

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

        BeyondEndChunkGenerator.activeTerrainParams = BeyondTerrainParams.DEFAULTS;

        BeyondTerrainStateInternal.setDimBounds(SAMPLE_DIM_MIN_Y, SAMPLE_DIM_MAX_Y);

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
        BeyondTerrainStateInternal.setDimBounds(savedMinY, savedMaxY);
    }

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

    @Test
    void dumpX500k() throws IOException {
        dumpGrid(500000, 0, "x_500k");
    }

    @Test
    void dumpX750k() throws IOException {
        dumpGrid(750000, 0, "x_750k");
    }

    @Test
    void dumpX1M() throws IOException {
        dumpGrid(1000000, 0, "x_1M");
    }

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

    private void dumpNoWrap(int centerX, int centerZ, String label) throws IOException {
        BeyondEndChunkGenerator.wrapDisabled = true;
        try {
            dumpGrid(centerX, centerZ, label);
        } finally {
            BeyondEndChunkGenerator.wrapDisabled = false;
        }
    }

    @Test void dumpNoWrapX100k()  throws IOException { dumpNoWrap(100_000,   0, "nowrap_x_100k"); }
    @Test void dumpNoWrapX250k()  throws IOException { dumpNoWrap(250_000,   0, "nowrap_x_250k"); }
    @Test void dumpNoWrapX500k()  throws IOException { dumpNoWrap(500_000,   0, "nowrap_x_500k"); }
    @Test void dumpNoWrapX750k()  throws IOException { dumpNoWrap(750_000,   0, "nowrap_x_750k"); }
    @Test void dumpNoWrapX1M()    throws IOException { dumpNoWrap(1_000_000, 0, "nowrap_x_1M"); }
    @Test void dumpNoWrapX2M()    throws IOException { dumpNoWrap(2_000_000, 0, "nowrap_x_2M"); }

    @Test void dumpNoWrapZ100k()  throws IOException { dumpNoWrap(0, 100_000,   "nowrap_z_100k"); }
    @Test void dumpNoWrapZ250k()  throws IOException { dumpNoWrap(0, 250_000,   "nowrap_z_250k"); }
    @Test void dumpNoWrapZ500k()  throws IOException { dumpNoWrap(0, 500_000,   "nowrap_z_500k"); }
    @Test void dumpNoWrapZ750k()  throws IOException { dumpNoWrap(0, 750_000,   "nowrap_z_750k"); }
    @Test void dumpNoWrapZ1M()    throws IOException { dumpNoWrap(0, 1_000_000, "nowrap_z_1M"); }
    @Test void dumpNoWrapZ2M()    throws IOException { dumpNoWrap(0, 2_000_000, "nowrap_z_2M"); }

    @Test void dumpNoWrapDiag100k() throws IOException { dumpNoWrap(100_000,   100_000,   "nowrap_diag_100k"); }
    @Test void dumpNoWrapDiag250k() throws IOException { dumpNoWrap(250_000,   250_000,   "nowrap_diag_250k"); }
    @Test void dumpNoWrapDiag500k() throws IOException { dumpNoWrap(500_000,   500_000,   "nowrap_diag_500k"); }
    @Test void dumpNoWrapDiag750k() throws IOException { dumpNoWrap(750_000,   750_000,   "nowrap_diag_750k"); }
    @Test void dumpNoWrapDiag1M()   throws IOException { dumpNoWrap(1_000_000, 1_000_000, "nowrap_diag_1M"); }
    @Test void dumpNoWrapDiag2M()   throws IOException { dumpNoWrap(2_000_000, 2_000_000, "nowrap_diag_2M"); }

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

    @Test void dumpDiag250k_noWarp()        throws IOException { dumpNoWarpOnly(250_000, 250_000, "diag_250k_noWarp"); }
    @Test void dumpDiag250k_fixedHScale()   throws IOException { dumpFixedHScale(250_000, 250_000, "diag_250k_fixedHScale"); }
    @Test void dumpDiag250k_fixedVScale()   throws IOException { dumpFixedVScale(250_000, 250_000, "diag_250k_fixedVScale"); }
    @Test void dumpDiag250k_fixedCycleH()   throws IOException { dumpFixedCycleHeight(250_000, 250_000, "diag_250k_fixedCycleH"); }
    @Test void dumpDiag250k_allFixed()      throws IOException { dumpAllFixed(250_000, 250_000, "diag_250k_allFixed"); }

    @Test void dumpDiag500k_noWarp()        throws IOException { dumpNoWarpOnly(500_000, 500_000, "diag_500k_noWarp"); }
    @Test void dumpDiag500k_fixedHScale()   throws IOException { dumpFixedHScale(500_000, 500_000, "diag_500k_fixedHScale"); }
    @Test void dumpDiag500k_fixedVScale()   throws IOException { dumpFixedVScale(500_000, 500_000, "diag_500k_fixedVScale"); }
    @Test void dumpDiag500k_fixedCycleH()   throws IOException { dumpFixedCycleHeight(500_000, 500_000, "diag_500k_fixedCycleH"); }
    @Test void dumpDiag500k_allFixed()      throws IOException { dumpAllFixed(500_000, 500_000, "diag_500k_allFixed"); }

    @Test void dumpDiag1M_fixedHScale()     throws IOException { dumpFixedHScale(1_000_000, 1_000_000, "diag_1M_fixedHScale"); }
    @Test void dumpDiag1M_allFixed()        throws IOException { dumpAllFixed(1_000_000, 1_000_000, "diag_1M_allFixed"); }
    @Test void dumpDiag2M_fixedHScale()     throws IOException { dumpFixedHScale(2_000_000, 2_000_000, "diag_2M_fixedHScale"); }
    @Test void dumpDiag2M_allFixed()        throws IOException { dumpAllFixed(2_000_000, 2_000_000, "diag_2M_allFixed"); }

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

    private void dumpBlur3x3Production(int centerX, int centerZ, String label) throws IOException {
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

    @Test void dumpProdBaseline_diag0()    throws IOException { dumpGrid(0,         0,         "prodBase_diag_0"); }
    @Test void dumpProdBaseline_diag250k() throws IOException { dumpGrid(250_000,   250_000,   "prodBase_diag_250k"); }
    @Test void dumpProdBaseline_diag500k() throws IOException { dumpGrid(500_000,   500_000,   "prodBase_diag_500k"); }
    @Test void dumpProdBaseline_diag750k() throws IOException { dumpGrid(750_000,   750_000,   "prodBase_diag_750k"); }
    @Test void dumpProdBaseline_diag1M()   throws IOException { dumpGrid(1_000_000, 1_000_000, "prodBase_diag_1M"); }
    @Test void dumpProdBaseline_diag2M()   throws IOException { dumpGrid(2_000_000, 2_000_000, "prodBase_diag_2M"); }

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

    @Test void dumpEnvelope_xOnly1M()  throws IOException { dumpIslandEnvelope(1_000_000,  0,          "envelope_xOnly_1M"); }
    @Test void dumpEnvelope_zOnly1M()  throws IOException { dumpIslandEnvelope(0,          1_000_000,  "envelope_zOnly_1M"); }

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

    @Test void dumpBlend_xOnly1M()  throws IOException { dumpBandBlend(1_000_000,  0,          "blend_xOnly_1M"); }
    @Test void dumpBlend_zOnly1M()  throws IOException { dumpBandBlend(0,          1_000_000,  "blend_zOnly_1M"); }

    @Test void dumpVerticalSlice_enderscape_diag250k() throws IOException {
        dumpVerticalSlice(250_000, 250_000, -64, 320, "vert_enderscape_diag_250k");
    }

    @Test void dumpVerticalSlice_enderscape_origin() throws IOException {
        dumpVerticalSlice(0, 0, -64, 320, "vert_enderscape_origin");
    }

    @Test void dumpVerticalSlice_astrological_diag250k() throws IOException {
        dumpVerticalSlice(250_000, 250_000, -256, 384, "vert_astrological_diag_250k");
    }

    @Test void dumpVerticalSlice_astrological_origin() throws IOException {
        dumpVerticalSlice(0, 0, -256, 384, "vert_astrological_origin");
    }

    @Test void dumpVerticalSlice_proposed_minus48to383_diag250k() throws IOException {
        dumpVerticalSlice(250_000, 250_000, -256, 384, "vert_target_minus48_383_diag_250k");
    }

    @Test void dumpVerticalSlice_beyondOnly_diag1M()  throws IOException { dumpVerticalSlice(1_000_000, 1_000_000, 0, 256, "vert_beyondOnly_diag_1M"); }
    @Test void dumpVerticalSlice_beyondOnly_diag2M()  throws IOException { dumpVerticalSlice(2_000_000, 2_000_000, 0, 256, "vert_beyondOnly_diag_2M"); }
    @Test void dumpVerticalSlice_beyondOnly_diag4M()  throws IOException { dumpVerticalSlice(4_000_000, 4_000_000, 0, 256, "vert_beyondOnly_diag_4M"); }
    @Test void dumpVerticalSlice_beyondOnly_diag5M()  throws IOException { dumpVerticalSlice(5_000_000, 5_000_000, 0, 256, "vert_beyondOnly_diag_5M"); }

    @Test void dumpVerticalSlice_enderscape_diag1M()  throws IOException { dumpVerticalSlice(1_000_000, 1_000_000, -64, 320, "vert_enderscape_diag_1M"); }
    @Test void dumpVerticalSlice_enderscape_diag2M()  throws IOException { dumpVerticalSlice(2_000_000, 2_000_000, -64, 320, "vert_enderscape_diag_2M"); }
    @Test void dumpVerticalSlice_enderscape_diag4M()  throws IOException { dumpVerticalSlice(4_000_000, 4_000_000, -64, 320, "vert_enderscape_diag_4M"); }
    @Test void dumpVerticalSlice_enderscape_diag5M()  throws IOException { dumpVerticalSlice(5_000_000, 5_000_000, -64, 320, "vert_enderscape_diag_5M"); }

    @Test void dumpVerticalSlice_astrological_diag1M()  throws IOException { dumpVerticalSlice(1_000_000, 1_000_000, -256, 384, "vert_astrological_diag_1M"); }
    @Test void dumpVerticalSlice_astrological_diag2M()  throws IOException { dumpVerticalSlice(2_000_000, 2_000_000, -256, 384, "vert_astrological_diag_2M"); }
    @Test void dumpVerticalSlice_astrological_diag4M()  throws IOException { dumpVerticalSlice(4_000_000, 4_000_000, -256, 384, "vert_astrological_diag_4M"); }
    @Test void dumpVerticalSlice_astrological_diag5M()  throws IOException { dumpVerticalSlice(5_000_000, 5_000_000, -256, 384, "vert_astrological_diag_5M"); }

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
                    int r = Math.min(255, gray + 80);
                    int g = gray / 2;
                    int b = gray / 4;
                    rgb = (r << 16) | (g << 8) | b;
                } else {
                    rgb = (gray << 16) | (gray << 8) | gray;
                }

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

    private void dumpVerticalSlice(int centerX, int centerZ, int dimMinY, int dimMaxY, String label)
            throws IOException {
        int savedMin = BeyondTerrainState.getDimMinY();
        int savedMax = BeyondTerrainState.getDimMaxY();
        BeyondTerrainStateInternal.setDimBounds(dimMinY, dimMaxY);
        try {
            int yRange = dimMaxY - dimMinY;
            int imageHeight = yRange;
            int imageWidth = GRID_SIZE;

            BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
            double[] densities = new double[imageWidth * imageHeight];
            double[] thresholds = new double[imageWidth * imageHeight];

            double minDensity = Double.POSITIVE_INFINITY;
            double maxDensity = Double.NEGATIVE_INFINITY;
            int solidCount = 0;
            int firstSolidY = Integer.MAX_VALUE;
            int lastSolidY = Integer.MIN_VALUE;

            final int half = imageWidth / 2;
            for (int yIdx = 0; yIdx < imageHeight; yIdx++) {
                int globalY = dimMinY + yIdx;
                for (int px = 0; px < imageWidth; px++) {
                    int globalX = centerX + (px - half) * GRID_STRIDE;

                    double density = BeyondEndChunkGenerator.getTerrainDensity(globalX, globalY, centerZ);
                    float distanceFromOrigin = (float) Math.sqrt(
                            (double) globalX * globalX + (double) centerZ * centerZ);
                    long packed = BeyondEndChunkGenerator.computeWrappedCoords(globalX, centerZ);
                    int wrappedX = BeyondEndChunkGenerator.unpackWrappedX(packed);
                    int wrappedZ = BeyondEndChunkGenerator.unpackWrappedZ(packed);
                    double threshold = BeyondEndChunkGenerator.getThreshold(wrappedX, wrappedZ, distanceFromOrigin);

                    int idx = yIdx * imageWidth + px;
                    densities[idx] = density;
                    thresholds[idx] = threshold;

                    assertFalse(Double.isNaN(density),
                            () -> "NaN density at " + globalX + "," + globalY + "," + centerZ);
                    assertFalse(Double.isInfinite(density),
                            () -> "Infinite density at " + globalX + "," + globalY + "," + centerZ);

                    if (density < minDensity) minDensity = density;
                    if (density > maxDensity) maxDensity = density;
                    if (density > threshold) {
                        solidCount++;
                        if (globalY < firstSolidY) firstSolidY = globalY;
                        if (globalY > lastSolidY) lastSolidY = globalY;
                    }
                }
            }

            double span = Math.max(maxDensity - minDensity, 1e-9);
            for (int yIdx = 0; yIdx < imageHeight; yIdx++) {
                for (int px = 0; px < imageWidth; px++) {
                    int idx = yIdx * imageWidth + px;
                    double d = densities[idx];
                    double t = thresholds[idx];
                    int gray = (int) (255.0 * (d - minDensity) / span);
                    if (gray < 0) gray = 0;
                    if (gray > 255) gray = 255;
                    int rgb;
                    if (d > t) {
                        int r = Math.min(255, gray + 80);
                        int g = gray / 2;
                        int b = gray / 4;
                        rgb = (r << 16) | (g << 8) | b;
                    } else {
                        rgb = (gray << 16) | (gray << 8) | gray;
                    }
                    image.setRGB(px, imageHeight - 1 - yIdx, rgb);
                }
            }

            String fileName = String.format("vert_%s_center_%d_%d_dimY%d_%d_stride%d.png",
                    label, centerX, centerZ, dimMinY, dimMaxY, GRID_STRIDE);
            File outFile = new File(OUTPUT_DIR, fileName);
            ImageIO.write(image, "png", outFile);

            int totalCells = imageWidth * imageHeight;
            System.out.printf(
                    "[VerticalSlice] %s -> %s%n" +
                            "  center=(%d, z=%d)  dimY=[%d, %d)  worldHeight=%.1f%n" +
                            "  density range=[%.4f, %.4f]  solid=%d/%d (%.2f%%)%n" +
                            "  solid Y range=[%d, %d]%n",
                    label, outFile.getAbsolutePath(),
                    centerX, centerZ, dimMinY, dimMaxY,
                    BeyondEndChunkGenerator.getWorldHeight(),
                    minDensity, maxDensity, solidCount, totalCells,
                    100.0 * solidCount / totalCells,
                    firstSolidY == Integer.MAX_VALUE ? 0 : firstSolidY,
                    lastSolidY == Integer.MIN_VALUE ? 0 : lastSolidY);
        } finally {
            BeyondTerrainStateInternal.setDimBounds(savedMin, savedMax);
        }
    }

    /** Bit-exact replica of the private {@code BeyondEndChunkGenerator.cyclicDensity(int, double)} — keep in sync. */
    private static double replicateCyclicDensity(int y, double cycleHeight) {
        double normalizedY = (y % cycleHeight) / cycleHeight;
        if (normalizedY < 0.8) {
            return Math.sin((normalizedY / 0.8) * (Math.PI / 2));
        } else {
            return 1 - (normalizedY - 0.8) / 0.2;
        }
    }
}
