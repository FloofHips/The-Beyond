package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondTerrain;
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
 * Gate for {@link com.thebeyond.mixin.ForeignStructureRejectionMixin}: mirrors its tower and
 * station rejection predicates over the far-field, re-checked at full sampling density.
 */
class ForeignStructureRejectionTest {

    private static final long NOISE_SEED = 42L;
    private static final int DIM_MIN_Y = -64, DIM_MAX_Y = 320;
    private static final int MIN_Y = DIM_MIN_Y, MAX_Y = DIM_MAX_Y - 1;

    // --- mirror ForeignStructureRejectionMixin ---
    private static final int TOWER_RADIUS = 19, TOWER_STEP = 1, FLUSH_TOL = 8;
    private static final double PAD_REJECT = 0.50;
    private static final int FLOAT_ENVELOPE = 126;
    private static final int STATION_START_ABS = 80;
    private static final double FAR_FIELD = 650.0;

    // --- mirror JigsawPlacementMixin max-anchor floor (the towers' stub Y) ---
    private static final int FX_RADIUS = 19, FX_STEP = 1, FX_BAND = 64;
    // getBaseHeight scans from dimMaxY-33 (top of the fill range) so upper pancakes on tall dims aren't missed.
    private static final int SCAN_TOP = DIM_MAX_Y - 33;

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
        RandomSource r1 = RandomSource.create(NOISE_SEED), r2 = RandomSource.create(NOISE_SEED + 1),
                r3 = RandomSource.create(NOISE_SEED + 2), r4 = RandomSource.create(NOISE_SEED + 3),
                r5 = RandomSource.create(NOISE_SEED + 4);
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
        BeyondEndChunkGenerator.simplexNoise = sS; BeyondEndChunkGenerator.biomeSimplexNoise = sBS;
        BeyondEndChunkGenerator.globalHOffsetNoise = sH; BeyondEndChunkGenerator.globalVOffsetNoise = sV;
        BeyondEndChunkGenerator.globalCOffsetNoise = sC; BeyondEndChunkGenerator.activeTerrainParams = sP;
        BeyondTerrainStateInternal.setDimBounds(sMin, sMax);
    }

    private static int[] tops(int x, int z) {
        return BeyondTerrain.streamPancakeTops(x, z, MIN_Y, MAX_Y).toArray();
    }

    /** Max-anchor floor = bit-for-bit mirror of JigsawPlacementMixin: per column the NEAREST top to
     *  targetTop within band (not the highest), then the max of those over the footprint, +1. */
    private static int maxAnchorFloor(int x, int z) {
        int[] c = tops(x, z);
        if (c.length == 0) return Integer.MIN_VALUE;
        int targetTop = c[0], maxTop = targetTop;
        for (int ox = -FX_RADIUS; ox <= FX_RADIUS; ox += FX_STEP)
            for (int oz = -FX_RADIUS; oz <= FX_RADIUS; oz += FX_STEP) {
                int best = Integer.MIN_VALUE, bestDist = Integer.MAX_VALUE;
                for (int t : tops(x + ox, z + oz)) {
                    int d = Math.abs(t - targetTop);
                    if (d <= FX_BAND && d < bestDist) { bestDist = d; best = t; }
                }
                if (best != Integer.MIN_VALUE && best > maxTop) maxTop = best;
            }
        return maxTop + 1;   // stub Y
    }

    private static boolean solidAt(int wx, int wz, int yLo, int yHi, BeyondEndChunkGenerator.ColumnScratch scr) {
        float d = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
        BeyondEndChunkGenerator.initColumnScratch(wx, wz, d, scr);
        for (int y = yHi; y >= yLo; y--)
            if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) return true;
        return false;
    }

    /** Pad-fraction of a tower footprint at the given sampling step (mirror of towerUnfit). */
    private static double padFraction(int x, int z, int floor, int step, BeyondEndChunkGenerator.ColumnScratch scr) {
        int pad = 0, total = 0, lo = Math.max(MIN_Y, floor - FLUSH_TOL);
        for (int ox = -TOWER_RADIUS; ox <= TOWER_RADIUS; ox += step)
            for (int oz = -TOWER_RADIUS; oz <= TOWER_RADIUS; oz += step) {
                total++;
                if (!solidAt(x + ox, z + oz, lo, floor - 1, scr)) pad++;
            }
        return total == 0 ? 0 : (double) pad / total;
    }

    /** getBaseHeight mirror: highest solid scanning from SCAN_TOP down; minY if void. */
    private static int baseHeight(int x, int z, BeyondEndChunkGenerator.ColumnScratch scr) {
        float d = (float) Math.sqrt((double) x * x + (double) z * z);
        BeyondEndChunkGenerator.initColumnScratch(x, z, d, scr);
        for (int y = SCAN_TOP; y >= MIN_Y; y--)
            if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) return y;
        return MIN_Y;
    }

    @Test
    void towerRejectionDensityAndFitness() {
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        double[] cuts = {0.15, 0.20, 0.25, 0.30, 0.40, 0.50};
        int[] kept = new int[cuts.length];
        int candidates = 0, acceptedButSparse = 0;   // sparse = accepted at PAD_REJECT(step2) but pad>cut at step1
        for (int cx = -200; cx <= 200; cx += 11) {
            for (int cz = -200; cz <= 200; cz += 11) {
                int x = (cx << 4) + 8, z = (cz << 4) + 8;
                if (Math.sqrt((double) x * x + (double) z * z) <= FAR_FIELD) continue;
                int floor = maxAnchorFloor(x, z);
                if (floor == Integer.MIN_VALUE) continue;   // no pancake at centre → mixin leaves vanilla
                candidates++;
                double pf2 = padFraction(x, z, floor, TOWER_STEP, scr);   // production sampling
                for (int i = 0; i < cuts.length; i++) if (pf2 <= cuts[i]) kept[i]++;
                if (pf2 <= PAD_REJECT && padFraction(x, z, floor, 1, scr) > PAD_REJECT) acceptedButSparse++;
            }
        }
        StringBuilder sb = new StringBuilder("[towerRejection] candidates=" + candidates + "  kept by pad-cut: ");
        for (int i = 0; i < cuts.length; i++)
            sb.append(String.format("%.2f→%.1f%%  ", cuts[i], 100.0 * kept[i] / Math.max(1, candidates)));
        sb.append("acceptedButSparse@").append(PAD_REJECT).append("=").append(acceptedButSparse);
        System.out.println(sb);

        int keptAtProd = 0;
        for (int i = 0; i < cuts.length; i++) if (cuts[i] == PAD_REJECT) keptAtProd = kept[i];
        double keptPct = 100.0 * keptAtProd / Math.max(1, candidates);

        Assertions.assertTrue(candidates >= 100, "too few far-field candidates: " + candidates);
        Assertions.assertEquals(0, acceptedButSparse,
                acceptedButSparse + " accepted towers exceed pad-fraction at full density (step-2 blind spot)");
        Assertions.assertTrue(keptPct >= 8.0 && keptPct <= 40.0,
                "kept fraction at PAD_REJECT=" + PAD_REJECT + " is " + String.format("%.1f", keptPct)
                        + "% — outside the intended ~20% band; retune PAD_REJECT from the sweep above");
    }

    @Test
    void stationRejectionAndFitness() {
        // The station is rejected only on ceiling clip; islands crossing it get organically cleared by
        // carveOutsideDist instead (geometry gated by BodyBurialScanTest.silhouetteClearGate).
        final int ERODE = BeyondEndChunkGenerator.CARVE_ERODE_REACH;   // carveOutsideDist <= ERODE ⇒ carved
        final int SCAN  = BeyondEndChunkGenerator.CARVE_SCAN_REACH;    // lateral clear extent for a floating piece
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int candidates = 0, clipRejected = 0, acceptedButClipped = 0;
        for (int cx = -200; cx <= 200; cx += 11) {
            for (int cz = -200; cz <= 200; cz += 11) {
                int x = (cx << 4) + 8, z = (cz << 4) + 8;
                if (Math.sqrt((double) x * x + (double) z * z) <= FAR_FIELD) continue;
                candidates++;
                int floor = STATION_START_ABS + Math.max(baseHeight(x, z, scr), MIN_Y);   // refY = 80 + getFirstFreeHeight
                if (floor + FLOAT_ENVELOPE > MAX_Y) { clipRejected++; continue; }
                if (floor + FLOAT_ENVELOPE > MAX_Y) acceptedButClipped++;   // 0 by construction
            }
        }

        // carveOutsideDist at the station's start-piece scale (73x86x89): body all distance 0, bounded.
        // One structure → one occupancy mask (here a single fully-occupied box).
        final int sHalfX = 36, sHalfZ = 44, sH = 86, f = 100;
        java.util.List<BeyondEndChunkGenerator.CarveMask> sBox =
                java.util.List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(
                        java.util.List.of(new net.minecraft.world.level.levelgen.structure.BoundingBox(
                                -sHalfX, f, -sHalfZ, sHalfX, f + sH - 1, sHalfZ)), false));   // station = floating
        long uncovered = 0, beyondRing = 0;
        for (int ox = -(sHalfX + SCAN + 2); ox <= sHalfX + SCAN + 2; ox++) {
            for (int oz = -(sHalfZ + SCAN + 2); oz <= sHalfZ + SCAN + 2; oz++) {
                boolean inFoot = Math.abs(ox) <= sHalfX && Math.abs(oz) <= sHalfZ;
                int dToBox = Math.max(0, Math.max(Math.abs(ox) - sHalfX, Math.abs(oz) - sHalfZ));
                if (inFoot) {   // whole body must be hard-cleared (distance 0)
                    for (int y = f; y <= f + sH - 1; y++) {
                        if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(sBox, ox, y, oz) != 0) uncovered++;
                    }
                }
                if (dToBox > SCAN) {   // beyond the FLOAT_HMARGIN band + erosion fringe: no body cell may be in range
                    for (int y = f; y <= f + sH - 1; y++) {
                        if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(sBox, ox, y, oz) <= ERODE) { beyondRing++; break; }
                    }
                }
            }
        }

        double clipPct = 100.0 * clipRejected / Math.max(1, candidates);
        System.out.printf("[stationRejection] candidates=%d clipRejected=%d (%.1f%%) acceptedButClipped=%d  stationBox uncovered=%d beyondRing=%d%n",
                candidates, clipRejected, clipPct, acceptedButClipped, uncovered, beyondRing);

        Assertions.assertTrue(candidates >= 100, "too few far-field candidates: " + candidates);
        Assertions.assertEquals(0, acceptedButClipped, "accepted stations clip the ceiling");
        Assertions.assertEquals(0, uncovered, uncovered + " station-footprint cells not hard-cleared (body would embed)");
        Assertions.assertEquals(0, beyondRing, beyondRing + " station clear reaches beyond footprint+CARVE_SCAN_REACH");
    }
}
