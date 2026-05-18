package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * EMPIRICAL measurement (not reasoning): counts pancake tiers E per column using the real
 * production density ({@link BeyondEndChunkGenerator#isSolidTerrainScratch}) with the exact
 * air→solid transition semantics of {@code CountOnEveryLayerPlacementMixin.scanColumn}, so we
 * can decide (a) whether MAX_LAYERS=32 is really exceeded and (b) the correct per-tier count.
 */
class PancakeTierCountMeasurementTest {

    private static final long NOISE_SEED = 42L;
    private static final int DIM_MIN_Y = -64;   // Enderscape pack bounds (range 384)
    private static final int DIM_MAX_Y = 320;

    private HashSimplexNoise sS, sBS;
    private PerlinSimplexNoise sH, sV, sC;
    private BeyondTerrainParams sP;
    private int sMin, sMax;

    @BeforeEach
    void setUp() {
        sS = BeyondEndChunkGenerator.simplexNoise;
        sBS = BeyondEndChunkGenerator.biomeSimplexNoise;
        sH = BeyondEndChunkGenerator.globalHOffsetNoise;
        sV = BeyondEndChunkGenerator.globalVOffsetNoise;
        sC = BeyondEndChunkGenerator.globalCOffsetNoise;
        sP = BeyondEndChunkGenerator.activeTerrainParams;
        sMin = BeyondTerrainState.getDimMinY();
        sMax = BeyondTerrainState.getDimMaxY();

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
        BeyondTerrainStateInternal.setDimBounds(DIM_MIN_Y, DIM_MAX_Y);
    }

    @AfterEach
    void tearDown() {
        BeyondEndChunkGenerator.simplexNoise = sS;
        BeyondEndChunkGenerator.biomeSimplexNoise = sBS;
        BeyondEndChunkGenerator.globalHOffsetNoise = sH;
        BeyondEndChunkGenerator.globalVOffsetNoise = sV;
        BeyondEndChunkGenerator.globalCOffsetNoise = sC;
        BeyondEndChunkGenerator.activeTerrainParams = sP;
        BeyondTerrainStateInternal.setDimBounds(sMin, sMax);
        BeyondEndChunkGenerator.cycleHeightOverride = null;
    }

    /** Pancake tiers in column (x,z): air→solid transitions, top-down — exactly scanColumn. */
    private int[] tierStats(int x, int z, int minY, int maxY) {
        float dist = (float) Math.sqrt((double) x * x + (double) z * z);
        BeyondEndChunkGenerator.ColumnScratch s = new BeyondEndChunkGenerator.ColumnScratch();
        BeyondEndChunkGenerator.initColumnScratch(x, z, dist, s);
        int total = 0, negative = 0;
        boolean prevSolid = false;
        for (int y = maxY - 1; y >= minY; y--) {
            boolean solid = BeyondEndChunkGenerator.isSolidTerrainScratch(y, s);
            if (!prevSolid && solid) {
                total++;
                if (y + 1 < 0) negative++;
            }
            prevSolid = solid;
        }
        return new int[] { total, negative };
    }

    private void report(String label, List<int[]> rows) {
        int[] totals = rows.stream().mapToInt(r -> r[0]).sorted().toArray();
        int over32 = (int) rows.stream().filter(r -> r[0] > 32).count();
        int withNeg = (int) rows.stream().filter(r -> r[1] > 0).count();
        int sum = 0; for (int[] r : rows) sum += r[0];
        int min = totals.length == 0 ? 0 : totals[0];
        int max = totals.length == 0 ? 0 : totals[totals.length - 1];
        int med = totals.length == 0 ? 0 : totals[totals.length / 2];
        System.out.printf(
            "[PancakeE] %-22s columns=%d  E(min/med/max)=%d/%d/%d  mean=%.1f  E>32=%d (%.0f%%)  haveNegTier=%d (%.0f%%)%n",
            label, rows.size(), min, med, max,
            rows.isEmpty() ? 0.0 : (double) sum / rows.size(),
            over32, 100.0 * over32 / Math.max(1, rows.size()),
            withNeg, 100.0 * withNeg / Math.max(1, rows.size()));
    }

    @Test
    void measureE() {
        // Both real bound sets: Enderscape (-64..320, range 384) and Astrological
        // (-256..384, range 640 — the widest a loaded pack uses).
        int[][] bounds = { { -64, 320 }, { -256, 384 } };
        for (int[] b : bounds) {
            BeyondTerrainStateInternal.setDimBounds(b[0], b[1]);
            String tag = "bounds[" + b[0] + "," + b[1] + "]";

            List<int[]> noiseDriven = new ArrayList<>();
            for (int x = 15000; x <= 15600; x += 40)
                for (int z = 14400; z <= 15000; z += 40)
                    noiseDriven.add(tierStats(x, z, b[0], b[1]));
            report(tag + " cycleHeight=noise", noiseDriven);

            for (double ch : new double[] { 10.0, 15.0, 20.0, 30.0, 50.0 }) {
                BeyondEndChunkGenerator.cycleHeightOverride = ch;
                List<int[]> rows = new ArrayList<>();
                for (int x = 15000; x <= 15600; x += 40)
                    for (int z = 14400; z <= 15000; z += 40)
                        rows.add(tierStats(x, z, b[0], b[1]));
                report(tag + " cycleHeight=" + (int) ch, rows);
            }
            BeyondEndChunkGenerator.cycleHeightOverride = null;
        }
    }
}
