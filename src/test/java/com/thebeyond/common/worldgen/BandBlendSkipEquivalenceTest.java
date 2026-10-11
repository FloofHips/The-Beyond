package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Proves {@link BeyondEndChunkGenerator#terrainDensityOrSkip} matches the eager
 * {@link BeyondEndChunkGenerator#getTerrainDensity} bit-exactly except where it skips genuine air.
 */
class BandBlendSkipEquivalenceTest {

    private static final long SEED = 42L;
    private static final int DIM_MIN_Y = -64, DIM_MAX_Y = 320;

    private HashSimplexNoise sS, sBS;
    private PerlinSimplexNoise sH, sV, sC;
    private BeyondTerrainParams sP;
    private int sMin, sMax;

    @BeforeEach
    void setUp() {
        sS = BeyondEndChunkGenerator.simplexNoise; sBS = BeyondEndChunkGenerator.biomeSimplexNoise;
        sH = BeyondEndChunkGenerator.globalHOffsetNoise; sV = BeyondEndChunkGenerator.globalVOffsetNoise;
        sC = BeyondEndChunkGenerator.globalCOffsetNoise; sP = BeyondEndChunkGenerator.activeTerrainParams;
        sMin = BeyondTerrainState.getDimMinY(); sMax = BeyondTerrainState.getDimMaxY();
        BeyondEndChunkGenerator.simplexNoise = new HashSimplexNoise(RandomSource.create(SEED));
        BeyondEndChunkGenerator.globalHOffsetNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 1), List.of(1));
        BeyondEndChunkGenerator.globalVOffsetNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 2), List.of(1));
        BeyondEndChunkGenerator.globalCOffsetNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 3), List.of(1));
        BeyondEndChunkGenerator.biomeSimplexNoise = new HashSimplexNoise(RandomSource.create(SEED + 4));
        BeyondEndChunkGenerator.activeTerrainParams = BeyondTerrainParams.DEFAULTS;
        BeyondTerrainStateInternal.setDimBounds(DIM_MIN_Y, DIM_MAX_Y);
    }

    @AfterEach
    void tearDown() {
        BeyondEndChunkGenerator.simplexNoise = sS; BeyondEndChunkGenerator.biomeSimplexNoise = sBS;
        BeyondEndChunkGenerator.globalHOffsetNoise = sH; BeyondEndChunkGenerator.globalVOffsetNoise = sV;
        BeyondEndChunkGenerator.globalCOffsetNoise = sC; BeyondEndChunkGenerator.activeTerrainParams = sP;
        BeyondTerrainStateInternal.setDimBounds(sMin, sMax);
    }

    @Test
    void skipIsBitExactAndNeverDropsSolid() {
        BeyondEndChunkGenerator.ColumnScratch s = BeyondEndChunkGenerator.getColumnScratch();
        int loopStart = DIM_MIN_Y + 33, loopEnd = DIM_MAX_Y - 32;
        long checked = 0, skipped = 0, nonSkipped = 0, solidNonSkipped = 0;
        double minThr = Double.MAX_VALUE, maxThr = -Double.MAX_VALUE;
        // skip ratio scales with the local threshold, so sample widely across thresholds.
        for (int cx = -12; cx <= 12; cx++) {
            for (int cz = -12; cz <= 12; cz++) {
                int x = cx * 1700 + 811;
                int z = cz * 1900 - 617;
                float dist = (float) Math.sqrt((double) x * x + (double) z * z);
                if (dist < 650) continue;
                BeyondEndChunkGenerator.initColumnScratch(x, z, dist, s);
                double thr = s.threshold;
                if (thr < minThr) minThr = thr;
                if (thr > maxThr) maxThr = thr;
                for (int y = loopStart; y < loopEnd; y++) {
                    double eager = BeyondEndChunkGenerator.getTerrainDensity(y, s.hScales, s.vScales, s.cycleHeight,
                            s.wrappedXs, s.wrappedZs, s.bbLoIdx, s.bbT, s.dimMinY, s.dimMaxY);
                    double opt = BeyondEndChunkGenerator.terrainDensityOrSkip(y, s, s.cycleHeight, thr, 0.0);
                    checked++;
                    if (opt == BeyondEndChunkGenerator.AIR_SENTINEL) {
                        skipped++;
                        Assertions.assertTrue(eager <= thr,
                                "SKIP dropped a SOLID cell at (" + x + "," + y + "," + z + "): eager=" + eager + " thr=" + thr);
                    } else {
                        nonSkipped++;
                        if (eager > thr) solidNonSkipped++;
                        Assertions.assertEquals(0, Double.compare(eager, opt),
                                "non-skipped density NOT bit-exact at (" + x + "," + y + "," + z + "): eager=" + eager + " opt=" + opt);
                        Assertions.assertEquals(eager > thr, opt > thr,
                                "solidity mismatch at (" + x + "," + y + "," + z + ")");
                    }
                }
            }
        }
        double skipPct = 100.0 * skipped / Math.max(1, checked);
        System.out.printf("[BandBlendSkipEquivalence] checked=%d  skipped=%d (%.2f%%)  nonSkipped=%d  solidNonSkipped=%d  thr=[%.3f..%.3f]%n",
                checked, skipped, skipPct, nonSkipped, solidNonSkipped, minThr, maxThr);
        // the gate is correctness (asserted per-cell above); the ratio here is reported, not asserted.
        Assertions.assertTrue(checked > 100_000, "too few cells sampled: " + checked);
        Assertions.assertTrue(solidNonSkipped > 0, "no solid cells sampled — test is vacuous");
    }
}
