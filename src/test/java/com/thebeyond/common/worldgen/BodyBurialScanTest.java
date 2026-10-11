package com.thebeyond.common.worldgen;

import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrain;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures BODY burial: with the tower anchored at
 * floor=maxTop, how much solid terrain (via {@link BeyondEndChunkGenerator#isSolidTerrainScratch}) sits above it.
 */
class BodyBurialScanTest {

    private static final long NOISE_SEED = 42L;

    private static final int DIM_MIN_Y = -64;
    private static final int DIM_MAX_Y = 320;
    private static final int MIN_Y = DIM_MIN_Y;
    private static final int MAX_Y = DIM_MAX_Y - 1;

    // bit-for-bit mirror of JigsawPlacementMixin's floor (FOOT_RADIUS/STEP/BAND).
    private static final int FX_RADIUS = 19, FX_STEP = 1, FX_BAND = 64;

    // classifies a buried solid block as same-tier vs a separate higher island.
    private static final int TIER_BAND = 64;

    // a body anchored on a high tier can poke through the dimension top; maxY is the highest placeable block.
    private static final int WORLD_CEIL = MAX_Y;

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

        // production noise chain: 5 sequential seeds, SplitMix64 hash decorrelates them.
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
    }

    private static int[] tops(int x, int z) {
        return BeyondTerrain.streamPancakeTops(x, z, MIN_Y, MAX_Y).toArray();
    }

    /** Pancake top nearest to chosenTop within +/-band at (wx,wz), or MIN_VALUE if void; mirrors the mixin's surfaceTopNear. */
    private static int surfaceTopNear(int wx, int wz, int chosenTop, int band) {
        int best = Integer.MIN_VALUE, bestDist = Integer.MAX_VALUE;
        for (int t : tops(wx, wz)) {
            int d = Math.abs(t - chosenTop);
            if (d <= band && d < bestDist) { bestDist = d; best = t; }
        }
        return best;
    }

    /** Bit-for-bit mirror of the corrected JigsawPlacementMixin's return value (maxTop+1); floor = this-1, MIN_VALUE if void. */
    private static int fixedSnap(int x, int z) {
        int[] c = tops(x, z);
        if (c.length == 0) return Integer.MIN_VALUE;
        int targetTop = c[0];   // far-field is all End biome, so top tier == element 0
        int maxTop = targetTop;
        for (int ox = -FX_RADIUS; ox <= FX_RADIUS; ox += FX_STEP) {
            for (int oz = -FX_RADIUS; oz <= FX_RADIUS; oz += FX_STEP) {
                int t = surfaceTopNear(x + ox, z + oz, targetTop, FX_BAND);
                if (t != Integer.MIN_VALUE && t > maxTop) maxTop = t;
            }
        }
        return maxTop + 1;
    }

    private static boolean solid(int x, int z, int y, BeyondEndChunkGenerator.ColumnScratch s) {
        return BeyondEndChunkGenerator.isSolidTerrainScratch(y, s);
    }

    private static double pct(int[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int i = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        if (i < 0) i = 0;
        if (i >= sorted.length) i = sorted.length - 1;
        return sorted[i];
    }

    private static String dist(String name, int[] vals) {
        if (vals.length == 0) return String.format("  %-30s n=0%n", name);
        int[] s = vals.clone();
        java.util.Arrays.sort(s);
        long sum = 0; for (int v : s) sum += v;
        return String.format(
            "  %-30s n=%d  min=%d  p25=%.0f  median=%.0f  mean=%.1f  p75=%.0f  p90=%.0f  max=%d%n",
            name, s.length, s[0], pct(s, 25), pct(s, 50), (double) sum / s.length,
            pct(s, 75), pct(s, 90), s[s.length - 1]);
    }

    private static final class Variant {
        final int H, half;
        final List<Integer> bodyBuryBlocks = new ArrayList<>();
        final List<Integer> bodyBuryCols = new ArrayList<>();
        final List<Integer> sameTierBlocks = new ArrayList<>();
        final List<Integer> sepIslandBlocks = new ArrayList<>();
        final List<Integer> highestBuryY = new ArrayList<>();    // penetration height (max Y of body solid, minus floor)
        int locsWithBodyBury = 0;
        int locsClippedByCeil = 0;
        Variant(int H, int half) { this.H = H; this.half = half; }
    }

    @Test
    void scanBodyBurial() throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("=== BodyBurialScan — LENS B HEADROOM (Enderscape bounds [-64,320), seed=42) ===\n");
        out.append("minY=").append(MIN_Y).append(" maxY=").append(MAX_Y)
           .append("  floor=JigsawPlacementMixin(maxTop) RADIUS=").append(FX_RADIUS)
           .append(" BAND=").append(FX_BAND).append("  tierBand=").append(TIER_BAND).append("\n");
        out.append("solid predicate = isSolidTerrainScratch (== generateEndTerrain END_STONE test)\n\n");

        // ancient_tower_remains H~=9 (39 wide -> half 19); shulker_tower H~=43 (33 wide -> half 16); cross both.
        Variant[] variants = {
            new Variant(9, 19),
            new Variant(9, 16),
            new Variant(43, 19),
            new Variant(43, 16),
        };

        int[] chunkCoords = { -190, -150, -110, -70, -45, 45, 70, 110, 150, 190 };
        int columnsScanned = 0, locationsWithPancake = 0;

        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();

        for (int cx : chunkCoords) {
            for (int cz : chunkCoords) {
                int x = (cx << 4) + 8;
                int z = (cz << 4) + 8;
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d <= 650) continue;
                columnsScanned++;

                int[] centreTops = tops(x, z);
                if (centreTops.length == 0) continue;
                locationsWithPancake++;
                int centreTop = centreTops[0];

                int snap = fixedSnap(x, z);
                if (snap == Integer.MIN_VALUE) continue;
                int floor = snap - 1;   // world-Y the structure base rests on

                for (Variant v : variants) {
                    int bodyTopY = floor + v.H;
                    boolean clipped = bodyTopY > WORLD_CEIL;
                    if (clipped) v.locsClippedByCeil++;
                    int scanTop = Math.min(bodyTopY, WORLD_CEIL);

                    int totalBody = 0, colsWithBody = 0, sameTier = 0, sepIsland = 0;
                    int highestPen = 0;
                    for (int ox = -v.half; ox <= v.half; ox++) {
                        for (int oz = -v.half; oz <= v.half; oz++) {
                            int wx = x + ox, wz = z + oz;
                            float dist = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                            BeyondEndChunkGenerator.initColumnScratch(wx, wz, dist, scr);
                            int colBody = 0;
                            for (int y = floor + 1; y <= scanTop; y++) {
                                if (!solid(wx, wz, y, scr)) continue;
                                colBody++;
                                totalBody++;
                                if (Math.abs(y - centreTop) <= TIER_BAND) sameTier++;
                                else sepIsland++;
                                int pen = y - floor;
                                if (pen > highestPen) highestPen = pen;
                            }
                            if (colBody > 0) colsWithBody++;
                        }
                    }
                    v.bodyBuryBlocks.add(totalBody);
                    v.bodyBuryCols.add(colsWithBody);
                    v.sameTierBlocks.add(sameTier);
                    v.sepIslandBlocks.add(sepIsland);
                    v.highestBuryY.add(highestPen);
                    if (totalBody > 0) v.locsWithBodyBury++;
                }
            }
        }

        out.append(String.format("columnsScanned(far-field) = %d%n", columnsScanned));
        out.append(String.format("locationsWithPancake      = %d%n%n", locationsWithPancake));

        int diagA = 0, diagB = 0, diagC = 0, diagCols = 0;
        for (int cx : chunkCoords) {
            for (int cz : chunkCoords) {
                int x = (cx << 4) + 8, z = (cz << 4) + 8;
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d <= 650) continue;
                int[] centreTops = tops(x, z);
                if (centreTops.length == 0) continue;
                int targetTop = centreTops[0];
                int snap = fixedSnap(x, z);
                if (snap == Integer.MIN_VALUE) continue;
                int floor = snap - 1;
                int scanTop = Math.min(floor + 9, WORLD_CEIL);
                for (int ox = -19; ox <= 19; ox++) {
                    for (int oz = -19; oz <= 19; oz++) {
                        int wx = x + ox, wz = z + oz;
                        float dist = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                        BeyondEndChunkGenerator.initColumnScratch(wx, wz, dist, scr);
                        int highestBodySolid = Integer.MIN_VALUE;
                        for (int y = floor + 1; y <= scanTop; y++) {
                            if (solid(wx, wz, y, scr)) highestBodySolid = y;
                        }
                        if (highestBodySolid == Integer.MIN_VALUE) continue;
                        diagCols++;
                        int usedTop = surfaceTopNear(wx, wz, targetTop, FX_BAND);
                        // max same-band top at this column — what the lift SHOULD have used if it took max, not nearest.
                        int maxBandTop = Integer.MIN_VALUE;
                        for (int t : tops(wx, wz)) {
                            if (Math.abs(t - targetTop) <= FX_BAND && t > maxBandTop) maxBandTop = t;
                        }
                        boolean buriedInBand = Math.abs((highestBodySolid + 1) - targetTop) <= FX_BAND;
                        if (buriedInBand && maxBandTop != Integer.MIN_VALUE && maxBandTop > usedTop
                                && maxBandTop - 1 >= floor + 1) {
                            diagA++;
                        } else if (!buriedInBand) {
                            diagB++;
                        } else {
                            diagC++;
                        }
                    }
                }
            }
        }
        out.append("--- ROOT-CAUSE of BODY burial (H=9/half=19 columns) ---\n");
        out.append(String.format("  body-burial columns analysed = %d%n", diagCols));
        out.append(String.format("  (A) NEAREST-NOT-MAX (floor lift took nearest top, a higher same-band top existed) = %d (%.1f%%)%n",
                diagA, 100.0 * diagA / Math.max(1, diagCols)));
        out.append(String.format("  (B) OUT-OF-BAND higher island (floor intentionally not lifted) = %d (%.1f%%)%n",
                diagB, 100.0 * diagB / Math.max(1, diagCols)));
        out.append(String.format("  (C) thick-pancake interior above its first-air top = %d (%.1f%%)%n%n",
                diagC, 100.0 * diagC / Math.max(1, diagCols)));

        int worstLocsWithBury = 0;
        int worstMaxBlocks = 0;
        for (Variant v : variants) {
            int n = v.bodyBuryBlocks.size();
            double pctBury = 100.0 * v.locsWithBodyBury / Math.max(1, n);
            out.append(String.format("===== H=%d  footprintHalf=%d  (footprint %dx%d cols) =====%n",
                    v.H, v.half, 2 * v.half + 1, 2 * v.half + 1));
            out.append(String.format("  locations with ANY body burial : %d/%d (%.1f%%)%n",
                    v.locsWithBodyBury, n, pctBury));
            out.append(String.format("  locations clipped by world ceil(%d): %d/%d (%.1f%%)%n",
                    WORLD_CEIL, v.locsClippedByCeil, n, 100.0 * v.locsClippedByCeil / Math.max(1, n)));
            out.append(dist("bodyBuryBlocks/loc", toArr(v.bodyBuryBlocks)));
            out.append(dist("bodyBuryColumns/loc", toArr(v.bodyBuryCols)));
            out.append(dist("highestPenetration(Y-floor)", toArr(v.highestBuryY)));
            out.append(dist("sameTierBumpBlocks/loc", toArr(v.sameTierBlocks)));
            out.append(dist("sepHigherIslandBlocks/loc", toArr(v.sepIslandBlocks)));
            long st = 0, si = 0;
            for (int b : v.sameTierBlocks) st += b;
            for (int b : v.sepIslandBlocks) si += b;
            long tot = st + si;
            out.append(String.format("  burial composition: sameTierBumps=%.1f%%  sepHigherIsland=%.1f%%  (totalBlocks=%d)%n%n",
                    100.0 * st / Math.max(1, tot), 100.0 * si / Math.max(1, tot), tot));
            if (v.locsWithBodyBury > worstLocsWithBury) worstLocsWithBury = v.locsWithBodyBury;
            int[] bb = toArr(v.bodyBuryBlocks);
            java.util.Arrays.sort(bb);
            if (bb.length > 0 && bb[bb.length - 1] > worstMaxBlocks) worstMaxBlocks = bb[bb.length - 1];
        }

        out.append("--- VERDICT ---\n");
        if (worstLocsWithBury == 0) {
            out.append("  BODY burial NOT observed: with floor=maxTop, nothing solid sits in [floor+1, floor+H]\n");
            out.append("  for any (H,half). The persisting in-game burial is NOT the body hitting an overhang.\n");
        } else {
            out.append(String.format("  BODY burial IS real: up to %d locations show solid inside the AABB above the floor;%n",
                    worstLocsWithBury));
            out.append(String.format("  worst single location buries %d body blocks. The floor clears the BASE but the%n",
                    worstMaxBlocks));
            out.append("  standing body still intersects overhanging pancake / a separate higher island above it.\n");
        }

        String report = out.toString();
        System.out.println(report);

        File dir = new File("build/footprint-sink-scan");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, "body_burial_scan.txt");
        try (PrintStream ps = new PrintStream(Files.newOutputStream(f.toPath()))) {
            ps.print(report);
        }
        System.out.println("[BodyBurialScan] wrote " + f.getAbsolutePath());

        // gates only on the measurement being trustworthy; the numbers themselves are the deliverable.
        int sampleN = variants[0].bodyBuryBlocks.size();
        Assertions.assertTrue(sampleN >= 30,
                "too few far-field island locations measured to trust the scan: " + sampleN);
    }

    private static int[] toArr(List<Integer> l) {
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    @Test
    void silhouetteClearGate() {
        final int REACH = 5;   // mirror BeyondEndChunkGenerator.CARVE_ERODE_REACH
        int[][] towers = { {9, 19}, {43, 16} };   // {H, footprintHalf}
        int[] chunkCoords = { -190, -150, -110, -70, -45, 45, 70, 110, 150, 190 };
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();

        long rawBuried = 0, bodyResidual = 0, clearedBeyondRing = 0;
        int locs = 0;
        final int PAD = REACH + 4;   // scan past the erosion fringe to prove boundedness
        for (int[] t : towers) {
            int H = t[0], half = t[1];
            for (int cx : chunkCoords) {
                for (int cz : chunkCoords) {
                    int x = (cx << 4) + 8, z = (cz << 4) + 8;
                    if (Math.sqrt((double) x * x + (double) z * z) <= 650) continue;
                    if (tops(x, z).length == 0) continue;
                    int snap = fixedSnap(x, z);
                    if (snap == Integer.MIN_VALUE) continue;
                    int floor = snap - 1;
                    BoundingBox box = new BoundingBox(x - half, floor, z - half, x + half, floor + H - 1, z + half);
                    List<BeyondEndChunkGenerator.CarveMask> boxes =
                            List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), true));
                    locs++;

                    for (int ox = -(half + PAD); ox <= half + PAD; ox++) {
                        for (int oz = -(half + PAD); oz <= half + PAD; oz++) {
                            int wx = x + ox, wz = z + oz;
                            float dist = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                            BeyondEndChunkGenerator.initColumnScratch(wx, wz, dist, scr);
                            boolean inFootprint = Math.abs(ox) <= half && Math.abs(oz) <= half;
                            int dxzToBox = Math.max(0, Math.max(Math.abs(ox) - half, Math.abs(oz) - half));

                            if (inFootprint) {
                                for (int y = floor; y <= floor + H - 1; y++) {
                                    if (!BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) continue;
                                    rawBuried++;
                                    if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(boxes, wx, y, wz) != 0) bodyResidual++;
                                }
                            }
                            if (dxzToBox > REACH) {
                                for (int y = floor; y <= floor + H - 1; y++) {
                                    if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(boxes, wx, y, wz) <= REACH) {
                                        clearedBeyondRing++; break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("[silhouetteClearGate] locs=%d rawBuried=%d bodyResidual=%d clearedBeyondRing=%d%n",
                locs, rawBuried, bodyResidual, clearedBeyondRing);

        Assertions.assertTrue(locs >= 30, "too few far-field locations: " + locs);
        Assertions.assertTrue(rawBuried > 0, "no body burial in sample — the fix would be untested");
        Assertions.assertEquals(0, bodyResidual,
                "silhouette clear leaves " + bodyResidual + " body cells not hard-cleared (burial risk)");
        Assertions.assertEquals(0, clearedBeyondRing,
                "erosion reaches " + clearedBeyondRing + " columns beyond footprint+CARVE_ERODE_REACH (neighbour risk)");
    }

    @Test
    void multiBoxGapGate() {
        final int REACH = 5;   // mirror BeyondEndChunkGenerator.CARVE_ERODE_REACH
        BoundingBox a = new BoundingBox(-10, 80, -10, 10, 100, 10);
        BoundingBox b = new BoundingBox(-10, 150, -10, 10, 170, 10);
        List<BeyondEndChunkGenerator.CarveMask> independent = List.of(   // two distinct structures
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(a), false),
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(b), false));
        List<BeyondEndChunkGenerator.CarveMask> oneStructure =          // one structure, two pieces
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(a, b), false));

        int colX = 0, colZ = 0;
        long indepMidInRange = 0, indepMidCells = 0, oneGapUncleared = 0, gapCells = 0;
        long bodyACleared = 0, bodyBCleared = 0;
        for (int y = 70; y <= 180; y++) {
            int dIndep = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(independent, colX, y, colZ);
            int dOne = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(oneStructure, colX, y, colZ);
            if (y >= 80 && y <= 100 && dIndep == 0) bodyACleared++;
            if (y >= 150 && y <= 170 && dIndep == 0) bodyBCleared++;
            if (y > 100 && y < 150) {
                gapCells++;
                if (dOne != 0) oneGapUncleared++;
                // gap MIDDLE = beyond the FLOATING margin + erosion reach of BOTH boxes (the margin extends the clear)
                int VM = BeyondEndChunkGenerator.FLOAT_VMARGIN;
                if (y - 100 - VM > REACH && 150 - VM - y > REACH) {
                    indepMidCells++;
                    if (dIndep <= REACH) indepMidInRange++;
                }
            }
        }

        System.out.printf("[multiBoxGapGate] oneGapUncleared=%d/%d indepMidInRange=%d/%d bodyACleared=%d bodyBCleared=%d%n",
                oneGapUncleared, gapCells, indepMidInRange, indepMidCells, bodyACleared, bodyBCleared);

        Assertions.assertEquals(0, oneGapUncleared,
                "within ONE structure the interior gap must be hard-cleared (island would embed): " + oneGapUncleared + " uncleared");
        Assertions.assertEquals(0, indepMidInRange,
                "cross-structure erosion reached " + indepMidInRange + " cells in the inter-tier gap middle (cubic-slab regression)");
        Assertions.assertTrue(indepMidCells > 0, "no gap-middle cells sampled — test is vacuous");
        Assertions.assertTrue(bodyACleared > 0 && bodyBCleared > 0, "both bodies must be hard-cleared (else test is vacuous)");
    }

    /** Seated vs floating: only seated protects the terrain below its base, and even then still erodes its sides. */
    @Test
    void foundationProtectedGate() {
        final int REACH = 5;   // mirror BeyondEndChunkGenerator.CARVE_ERODE_REACH
        BoundingBox box = new BoundingBox(-8, 100, -8, 8, 140, 8);
        List<BeyondEndChunkGenerator.CarveMask> seated =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), true));
        List<BeyondEndChunkGenerator.CarveMask> floating =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), false));

        long seatedFoundationInRange = 0, floatingBelowInRange = 0;
        for (int y = 100 - REACH; y < 100; y++) {
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(seated, 0, y, 0) <= REACH) seatedFoundationInRange++;
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(floating, 0, y, 0) <= REACH) floatingBelowInRange++;
        }
        // a seated structure still erodes its SIDES above the base (2 blocks outside the footprint, mid-height)
        int seatedSideDist = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(seated, 10, 120, 0);

        System.out.printf("[foundationProtectedGate] seatedFoundationInRange=%d floatingBelowInRange=%d seatedSideDist=%d%n",
                seatedFoundationInRange, floatingBelowInRange, seatedSideDist);

        Assertions.assertEquals(0, seatedFoundationInRange,
                "seated foundation eroded (" + seatedFoundationInRange + " cells) — legs would float");
        Assertions.assertTrue(floatingBelowInRange > 0, "floating structure must erode below its base (else seated guard is vacuous)");
        Assertions.assertTrue(seatedSideDist <= REACH, "seated structure should still erode its sides above the base");
    }

    @Test
    void occupancyMaskGate() {
        final int REACH = 5;   // mirror CARVE_ERODE_REACH
        BoundingBox lobeA = new BoundingBox(0, 64, 0, 20, 80, 20);
        BoundingBox lobeB = new BoundingBox(60, 64, 0, 80, 80, 20);
        List<BeyondEndChunkGenerator.CarveMask> masks =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(lobeA, lobeB), false));

        int dInA = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, 10, 72, 10);
        int dInB = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, 70, 72, 10);
        // the gap centre, beyond CARVE_SCAN_REACH from both lobes, must stay terrain (the FLOATING band+fringe
        // legitimately clears within CARVE_SCAN_REACH of each lobe).
        final int SCAN = BeyondEndChunkGenerator.CARVE_SCAN_REACH;
        long gapInRange = 0, gapCells = 0;
        for (int x = 20 + SCAN + 1; x <= 60 - SCAN - 1; x++) {
            gapCells++;
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, x, 72, 10) <= REACH) gapInRange++;
        }

        System.out.printf("[occupancyMaskGate] dInA=%d dInB=%d gapInRange=%d/%d%n", dInA, dInB, gapInRange, gapCells);

        Assertions.assertEquals(0, dInA, "lobe A column not cleared");
        Assertions.assertEquals(0, dInB, "lobe B column not cleared");
        Assertions.assertEquals(0, gapInRange,
                gapInRange + " concave-gap columns cleared — the bbox cube returned (occupancy mask not honoured)");
        Assertions.assertTrue(gapCells > 0, "no gap columns sampled — test is vacuous");
    }

    @Test
    void noShaftBelowColumnLowestGate() {
        BoundingBox low  = new BoundingBox(-10, 100, -10, 10, 104, 10);
        BoundingBox high = new BoundingBox( 40, 180, -10, 60, 200, 10);
        List<BeyondEndChunkGenerator.CarveMask> mask =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(low, high), false));   // ONE structure, FLOATING
        int x = 50, z = 0;   // inside the high lobe's column only (the low lobe is 30+ blocks away in X)
        int VM = BeyondEndChunkGenerator.FLOAT_VMARGIN;
        int clearedHigh = 0, marginCleared = 0, longShaft = 0;
        for (int y = 180; y <= 200; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(mask, x, y, z) == 0) clearedHigh++;
        for (int y = 180 - VM; y < 180; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(mask, x, y, z) == 0) marginCleared++;
        for (int y = 105; y < 180 - VM; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(mask, x, y, z) == 0) longShaft++;
        System.out.printf("[noShaftBelowColumnLowest] clearedHigh=%d marginCleared=%d/%d longShaft=%d%n",
                clearedHigh, marginCleared, VM, longShaft);
        Assertions.assertEquals(21, clearedHigh, "high lobe body must be fully cleared (no burial)");
        Assertions.assertEquals(VM, marginCleared, "FLOATING vertical clearance margin must be cleared below the lobe");
        Assertions.assertEquals(0, longShaft,
                "NO long shaft below the margin down to global gLo — the pillar (global-gLo clear would fail here)");
    }

    /** The foundation refill must key off the structure's global gLo, not a high wing's local colLo. */
    @Test
    void seatedMultiBaseNoPillarGate() {
        final int gLo = 100, highColLo = 180, natTop = 99;
        int correctFill = 0, badFill = 0;
        for (int y = 105; y <= 179; y++) {
            if (BeyondEndChunkGenerator.the_beyond$isFoundationFill(y, gLo, natTop)) correctFill++;
            if (BeyondEndChunkGenerator.the_beyond$isFoundationFill(y, highColLo, natTop)) badFill++;
        }
        System.out.printf("[seatedMultiBaseNoPillar] correctFill(gLo=100)=%d badFill(colLo=180)=%d%n", correctFill, badFill);
        Assertions.assertEquals(0, correctFill,
                "foundation keyed on GLOBAL gLo must NOT refill the run above the base (no pillar under a high wing)");
        Assertions.assertTrue(badFill > 0,
                "regression pin: keying the foundation off the high wing's colLo WOULD build a solid pillar");
    }

    /** Seated analogue of {@link #noShaftBelowColumnLowestGate} through the real CarveMask. */
    @Test
    void seatedMultiBaseCarveNoPillarGate() {
        final int REACH = 5;   // mirror BeyondEndChunkGenerator.CARVE_ERODE_REACH
        BoundingBox boxLow  = new BoundingBox(-10, 100, -10, 10, 104, 10);
        BoundingBox boxHigh = new BoundingBox( 40, 180, -10, 60, 200, 10);
        BeyondEndChunkGenerator.CarveMask mask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(boxLow, boxHigh), true);   // ONE SEATED structure
        List<BeyondEndChunkGenerator.CarveMask> masks = List.of(mask);

        int x = 50, z = 0;   // inside the HIGH piece's column only (the low piece is 30+ blocks away in X)
        int bit = mask.nearestOccupiedBit(x, z, REACH);
        Assertions.assertTrue(bit >= 0, "(50,0) must be an occupied column of the high piece");
        int gLo = mask.gLo, colLo = mask.colLo(bit);

        Assertions.assertEquals(100, gLo, "global gLo must be the structure's LOWEST base (the low piece)");
        Assertions.assertEquals(180, colLo, "this column's colLo must be the HIGH piece's base");

        int clearedHigh = 0;
        for (int y = 180; y <= 200; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, x, y, z) == 0) clearedHigh++;

        // a colLo-keyed guard would collapse erodedUnderside to 0 (protect the whole run instead).
        int protectedRun = 0, erodedUnderside = 0;
        for (int y = 105; y <= 179; y++) {
            int dd = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, x, y, z);
            if (dd > REACH) protectedRun++; else erodedUnderside++;
        }

        // same divergence as seatedMultiBaseNoPillarGate, exercised through the real mask.
        final int natTop = gLo - 1;
        int correctFill = 0, badFill = 0;
        for (int y = 105; y <= 179; y++) {
            if (BeyondEndChunkGenerator.the_beyond$isFoundationFill(y, gLo, natTop)) correctFill++;
            if (BeyondEndChunkGenerator.the_beyond$isFoundationFill(y, colLo, natTop)) badFill++;
        }

        System.out.printf("[seatedMultiBaseCarveNoPillar] gLo=%d colLo=%d clearedHigh=%d/21 protectedRun=%d erodedUnderside=%d correctFill=%d badFill=%d%n",
                gLo, colLo, clearedHigh, protectedRun, erodedUnderside, correctFill, badFill);

        Assertions.assertEquals(21, clearedHigh, "high lobe body must be fully cleared (no burial)");
        Assertions.assertTrue(erodedUnderside > 0,
                "high lobe underside must erode — the seated guard did NOT extend protection up to colLo (naive per-column-seated fix would protect the whole run)");
        Assertions.assertNotEquals(75, protectedRun,
                "the WHOLE run [105,179] is protected — the seated guard keyed off colLo not gLo (the pillar regression)");
        Assertions.assertEquals(0, correctFill,
                "foundation keyed on the mask's GLOBAL gLo must NOT refill the run under the high wing (no pillar)");
        Assertions.assertTrue(badFill > 0,
                "regression pin: keying the foundation off the high wing's colLo WOULD build a solid END_STONE pillar");
    }

    @Test
    void floatingVerticalMarginGate() {
        int VM = BeyondEndChunkGenerator.FLOAT_VMARGIN;
        BoundingBox box = new BoundingBox(-8, 100, -8, 8, 104, 8);
        List<BeyondEndChunkGenerator.CarveMask> floating =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), false));
        List<BeyondEndChunkGenerator.CarveMask> seated =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), true));
        int x = 0, z = 0;
        int floatAbove = 0, floatBelow = 0, seatAbove = 0;
        for (int y = 105; y < 105 + VM; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(floating, x, y, z) == 0) floatAbove++;
        for (int y = 100 - VM; y < 100; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(floating, x, y, z) == 0) floatBelow++;
        for (int y = 105; y < 105 + VM; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(seated, x, y, z) == 0) seatAbove++;
        System.out.printf("[floatingVerticalMargin] floatAbove=%d/%d floatBelow=%d/%d seatAbove=%d%n",
                floatAbove, VM, floatBelow, VM, seatAbove);
        Assertions.assertEquals(VM, floatAbove, "FLOATING must clear the vertical margin ABOVE the piece (bridge headroom)");
        Assertions.assertEquals(VM, floatBelow, "FLOATING must clear the vertical margin BELOW the piece (no island under bridges)");
        Assertions.assertTrue(seatAbove < VM, "SEATED gets no hard vertical margin (only erosion) — the seat path is unchanged");
    }

    @Test
    void holeFillEnclosedGate() {
        BoundingBox top    = new BoundingBox(0,  100, 0,  40, 120, 5);
        BoundingBox bottom = new BoundingBox(0,  100, 35, 40, 120, 40);
        BoundingBox left   = new BoundingBox(0,  100, 0,  5,  120, 40);
        BoundingBox right  = new BoundingBox(35, 100, 0,  40, 120, 40);
        BeyondEndChunkGenerator.CarveMask closed =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(top, bottom, left, right), false);
        List<BeyondEndChunkGenerator.CarveMask> closedMasks = List.of(closed);

        int cx = 20, cz = 20;   // ring centre — 15 blocks from any edge, only the fill can clear it
        int centreBody = 0;
        for (int y = 100; y <= 120; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(closedMasks, cx, y, cz) == 0) centreBody++;
        int VM = BeyondEndChunkGenerator.FLOAT_VMARGIN;
        int centreFarAbove = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(closedMasks, cx, 120 + VM + 30, cz);

        // same ring with the top edge removed: the centre now opens to the border.
        BeyondEndChunkGenerator.CarveMask open =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(bottom, left, right), false);
        List<BeyondEndChunkGenerator.CarveMask> openMasks = List.of(open);
        int openCentre = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(openMasks, cx, 110, cz);

        System.out.printf("[holeFillEnclosed] closedFilled=%d centreBody=%d/21 centreFarAbove=%d openFilled=%d openCentre=%d%n",
                closed.filledHoles, centreBody, centreFarAbove, open.filledHoles, openCentre);

        Assertions.assertTrue(closed.filledHoles > 0, "closed ring must fill its enclosed centre");
        Assertions.assertEquals(21, centreBody, "enclosed centre must be cleared across the full body height (islands can't embed)");
        Assertions.assertNotEquals(0, centreFarAbove, "the fill must not clear far above the structure (margin-bounded)");
        Assertions.assertEquals(0, open.filledHoles, "an OPEN bay (reachable from the border) must NOT be filled");
        Assertions.assertNotEquals(0, openCentre, "the open ring's centre must stay terrain (the interleaving)");
    }

    @Test
    void antiShaftFilledColumnGate() {
        int VM = BeyondEndChunkGenerator.FLOAT_VMARGIN;
        BoundingBox top    = new BoundingBox(0,  180, 0,  40, 200, 5);
        BoundingBox bottom = new BoundingBox(0,  180, 35, 40, 200, 40);
        BoundingBox left   = new BoundingBox(0,  180, 0,  5,  200, 40);
        BoundingBox right  = new BoundingBox(35, 180, 0,  40, 200, 40);
        BoundingBox low    = new BoundingBox(60, 100, 60, 70, 104, 70);   // low piece far away → global gLo=100
        BeyondEndChunkGenerator.CarveMask mask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(top, bottom, left, right, low), false);
        List<BeyondEndChunkGenerator.CarveMask> masks = List.of(mask);

        int cx = 20, cz = 20;
        int bit = mask.nearestOccupiedBit(cx, cz, 5);
        Assertions.assertTrue(bit >= 0 && mask.isFilled(bit), "ring centre must be a FILLED column");

        int bodyCleared = 0;
        for (int y = 180 - VM; y <= 200 + VM; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, cx, y, cz) == 0) bodyCleared++;
        int shaft = 0;
        for (int y = 105; y < 180 - VM; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, cx, y, cz) == 0) shaft++;

        System.out.printf("[antiShaftFilledColumn] gLo=%d colLo=%d colHi=%d bodyCleared=%d shaft=%d%n",
                mask.gLo, mask.colLo(bit), mask.colHi(bit), bodyCleared, shaft);

        Assertions.assertEquals(100, mask.gLo, "global gLo must be the low piece's base");
        Assertions.assertEquals(180, mask.colLo(bit), "filled centre span must start at the RING base, not the global gLo");
        Assertions.assertEquals(200, mask.colHi(bit), "filled centre span must end at the RING top");
        Assertions.assertEquals(21, bodyCleared, "the enclosed centre is cleared exactly across [180,200] (no vmargin on a filled column)");
        Assertions.assertEquals(0, shaft, "NO open-sky shaft below the ring down to the global gLo (the pillar)");
    }

    @Test
    void seatedEnclosedNoFoundationGate() {
        BoundingBox top    = new BoundingBox(0,  100, 0,  40, 120, 5);
        BoundingBox bottom = new BoundingBox(0,  100, 35, 40, 120, 40);
        BoundingBox left   = new BoundingBox(0,  100, 0,  5,  120, 40);
        BoundingBox right  = new BoundingBox(35, 100, 0,  40, 120, 40);
        BeyondEndChunkGenerator.CarveMask mask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(top, bottom, left, right), true);   // SEATED
        List<BeyondEndChunkGenerator.CarveMask> masks = List.of(mask);

        int cx = 20, cz = 20;
        int bit = mask.nearestOccupiedBit(cx, cz, 5);
        Assertions.assertTrue(bit >= 0 && mask.isFilled(bit),
                "seated courtyard centre must be a FILLED column (so the foundation path skips it)");
        int centreCleared = 0;
        for (int y = 100; y <= 120; y++)
            if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, cx, y, cz) == 0) centreCleared++;
        int belowBaseProtected = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, cx, 95, cz);

        System.out.printf("[seatedEnclosedNoFoundation] filled=%d centreCleared=%d belowBaseProtected=%d%n",
                mask.filledHoles, centreCleared, belowBaseProtected);

        Assertions.assertTrue(mask.filledHoles > 0, "the courtyard centre must be filled");
        Assertions.assertEquals(21, centreCleared, "the enclosed centre must be cleared across the walls' height");
        Assertions.assertTrue(belowBaseProtected > 5, "below the seated base must stay protected (seat guard)");
    }

    @Test
    void closingFillsNarrowSlotKeepsWideBay() {
        int R = BeyondEndChunkGenerator.CLOSE_RADIUS, SCAN = BeyondEndChunkGenerator.CARVE_SCAN_REACH;
        BoundingBox nA = new BoundingBox(0,  100, 0, 5,  120, 30);
        BoundingBox nB = new BoundingBox(16, 100, 0, 21, 120, 30);
        BeyondEndChunkGenerator.CarveMask narrow = BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(nA, nB), false, R);
        BoundingBox wA = new BoundingBox(0,  100, 0, 5,  120, 30);
        BoundingBox wC = new BoundingBox(50, 100, 0, 55, 120, 30);
        BeyondEndChunkGenerator.CarveMask wide = BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(wA, wC), false, R);

        int narrowMid  = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(narrow), 10, 110, 15);
        int narrowBelow= BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(narrow), 10, 80,  15);
        int wideMid    = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(wide),   27, 110, 15);

        System.out.printf("[closingSlot] R=%d SCAN=%d narrowMid=%d narrowBelow=%d wideMid=%d%n", R, SCAN, narrowMid, narrowBelow, wideMid);

        Assertions.assertEquals(0, narrowMid, "the FLOATING lateral band clears a narrow gap's middle (no horizontal hug)");
        Assertions.assertNotEquals(0, narrowBelow, "the CARVE must not shaft below the walls' band (no R10 pillar)");
        Assertions.assertTrue(wideMid > BeyondEndChunkGenerator.CARVE_ERODE_REACH, "a WIDE bay centre (≫ 2·CARVE_SCAN_REACH) must stay terrain (interleaving)");
    }

    @Test
    void closingSealsPerforatedDomeWindow() {
        int R = BeyondEndChunkGenerator.CLOSE_RADIUS;
        // ring at x[10,40] z[10,40] (margin 10 to the 0..50 envelope), body y[100,120], with a window in the top wall.
        BoundingBox left   = new BoundingBox(10, 100, 10, 12, 120, 40);
        BoundingBox right  = new BoundingBox(38, 100, 10, 40, 120, 40);
        BoundingBox bottom = new BoundingBox(10, 100, 38, 40, 120, 40);
        BoundingBox topL   = new BoundingBox(10, 100, 10, 22, 120, 12);   // window x[23,27] = 5 wide (<= 2R)
        BoundingBox topR   = new BoundingBox(28, 100, 10, 40, 120, 12);
        // anchors at the far corners pin the envelope to 0..50 so a real margin exists.
        BoundingBox anchor0 = new BoundingBox(0, 100, 0, 0, 120, 0);
        BoundingBox anchor1 = new BoundingBox(50, 100, 50, 50, 120, 50);
        List<BoundingBox> ringBoxes = List.of(left, right, bottom, topL, topR, anchor0, anchor1);
        BeyondEndChunkGenerator.CarveMask sealed = BeyondEndChunkGenerator.CarveMask.fromBoxes(ringBoxes, false, R);
        BeyondEndChunkGenerator.CarveMask leaky  = BeyondEndChunkGenerator.CarveMask.fromBoxes(ringBoxes, false, 0);

        int cx = 25, cz = 25;   // ring interior centre, far beyond R — only sealing+flood reaches it
        int sealedCentre = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(sealed), cx, 110, cz);
        int leakyCentre  = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(leaky),  cx, 110, cz);

        System.out.printf("[closingDome] R=%d sealedCentre=%d leakyCentre=%d sealedFilled=%d leakyFilled=%d%n",
                R, sealedCentre, leakyCentre, sealed.filledHoles, leaky.filledHoles);

        Assertions.assertEquals(0, sealedCentre, "closing must seal the window so the dome interior is cleared");
        Assertions.assertNotEquals(0, leakyCentre, "without closing the window leaks and the dome interior fills with terrain (the bug)");
    }

    @Test
    void closingFillsBorderFlushThinShell() {
        int R = BeyondEndChunkGenerator.CLOSE_RADIUS;
        BoundingBox top    = new BoundingBox(0,  100, 0,  19, 120, 0);    // 1-thick ring flush with x/z edges 0..19
        BoundingBox bottom = new BoundingBox(0,  100, 19, 19, 120, 19);
        BoundingBox left   = new BoundingBox(0,  100, 0,  0,  120, 19);
        BoundingBox right  = new BoundingBox(19, 100, 0,  19, 120, 19);
        List<BoundingBox> ring = List.of(top, bottom, left, right);
        BeyondEndChunkGenerator.CarveMask atR = BeyondEndChunkGenerator.CarveMask.fromBoxes(ring, false, R);
        BeyondEndChunkGenerator.CarveMask at0 = BeyondEndChunkGenerator.CarveMask.fromBoxes(ring, false, 0);
        int cx = 10, cz = 10;   // interior centre, 9 from any wall — only the sealed flood reaches it
        int centreR = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(atR), cx, 110, cz);
        int centre0 = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(at0), cx, 110, cz);
        System.out.printf("[closingExtensive] R=%d centreR=%d centre0=%d filledR=%d filled0=%d%n",
                R, centreR, centre0, atR.filledHoles, at0.filledHoles);
        Assertions.assertEquals(0, centre0, "enclosed-only (closeR=0) fills a sealed interior — the R8 floor");
        Assertions.assertEquals(0, centreR, "closing at CLOSE_RADIUS must NOT regress: a border-flush thin-shell sealed interior stays filled");
    }

    @Test
    void closingNoShaftNestedStackedEnclosures() {
        int R = BeyondEndChunkGenerator.CLOSE_RADIUS;
        java.util.List<BoundingBox> boxes = new java.util.ArrayList<>();
        // small low ring (nested, walls x{18..20,30..32}), y[100,130]
        boxes.add(new BoundingBox(18, 100, 18, 32, 130, 20));
        boxes.add(new BoundingBox(18, 100, 30, 32, 130, 32));
        boxes.add(new BoundingBox(18, 100, 18, 20, 130, 32));
        boxes.add(new BoundingBox(30, 100, 18, 32, 130, 32));
        // wide high ring (walls x{10..12,38..40}, disjoint columns from the low ring), y[180,200]
        boxes.add(new BoundingBox(10, 180, 10, 40, 200, 12));
        boxes.add(new BoundingBox(10, 180, 38, 40, 200, 40));
        boxes.add(new BoundingBox(10, 180, 10, 12, 200, 40));
        boxes.add(new BoundingBox(38, 180, 10, 40, 200, 40));
        boxes.add(new BoundingBox(0, 100, 0, 0, 120, 0));          // envelope-pin anchors (margin)
        boxes.add(new BoundingBox(50, 100, 50, 50, 120, 50));
        BeyondEndChunkGenerator.CarveMask mask = BeyondEndChunkGenerator.CarveMask.fromBoxes(boxes, false, R);
        List<BeyondEndChunkGenerator.CarveMask> masks = List.of(mask);
        int cx = 25, cz = 25;
        int cbit = mask.nearestOccupiedBit(cx, cz, R);
        int lowCleared = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, cx, 115, cz);
        int gapCleared = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, cx, 155, cz);
        System.out.printf("[nestedNoShaft] R=%d lowCleared=%d gapCleared=%d filled=%d centreBand=[%d,%d]%n",
                R, lowCleared, gapCleared, mask.filledHoles,
                cbit >= 0 ? mask.colLo(cbit) : -1, cbit >= 0 ? mask.colHi(cbit) : -1);
        Assertions.assertEquals(0, lowCleared, "the dominant (low) nested enclosure interior must be cleared");
        Assertions.assertNotEquals(0, gapCleared, "the OPEN gap between the nested enclosures must stay terrain (NO shaft)");
        Assertions.assertEquals(130, cbit >= 0 ? mask.colHi(cbit) : -1, "longest-run keeps only the dominant [100,130] run (no shaft to the high ring)");
    }

    @Test
    void filledColumnNoVerticalMargin() {
        int R = BeyondEndChunkGenerator.CLOSE_RADIUS;
        // Narrow slot → the middle (8,15) is a FILLED column with band [100,120]; the walls (x[0,5],x[12,17]) are REAL.
        BoundingBox a = new BoundingBox(0,  100, 0, 5,  120, 30);
        BoundingBox b = new BoundingBox(12, 100, 0, 17, 120, 30);
        BeyondEndChunkGenerator.CarveMask mask = BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(a, b), false, R);
        List<BeyondEndChunkGenerator.CarveMask> masks = List.of(mask);
        int fillBit = mask.nearestOccupiedBit(8, 15, R);
        Assertions.assertTrue(fillBit >= 0 && mask.isFilled(fillBit), "(8,15) must be a filled column");
        int filledMid = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, 8, 110, 15);  // inside the trapped band
        int headroom  = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, 8, 125, 15);  // 5 above band: real walls' headroom
        int farBelow  = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, 8, 80,  15);  // 20 below: carve keeps the box (no shaft)
        System.out.printf("[filledNoMargin] R=%d filledMid=%d headroom=%d farBelow=%d%n", R, filledMid, headroom, farBelow);
        Assertions.assertEquals(0, filledMid, "the filled column's trapped band is cleared");
        Assertions.assertEquals(0, headroom, "the adjacent REAL walls supply the vertical headroom around the filled column (union)");
        Assertions.assertNotEquals(0, farBelow, "no shaft: far below the trapping walls' margin stays terrain (CARVE keeps the box)");
    }

    @Test
    void openChannelNeverFilled() {
        // two parallel walls with a 5-wide gap x[2,6], open at both z-ends (z=0 and z=30): drains to the border.
        BoundingBox wA = new BoundingBox(0, 100, 0, 1, 120, 30);
        BoundingBox wB = new BoundingBox(7, 100, 0, 8, 120, 30);
        BeyondEndChunkGenerator.CarveMask channel =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(wA, wB), false, BeyondEndChunkGenerator.CLOSE_RADIUS);
        System.out.printf("[revertLattice] channelFilled=%d%n", channel.filledHoles);
        Assertions.assertEquals(0, channel.filledHoles,
                "an open channel that drains to the border is never filled (no phantom occupancy → no carve where no block exists)");
    }

    @Test
    void unionClearsViaTallNeighborNotNearestShort() {
        BoundingBox tall = new BoundingBox(10, 100, 0, 10, 140, 5);
        BoundingBox stub = new BoundingBox(11, 100, 0, 11, 102, 5);
        BeyondEndChunkGenerator.CarveMask m =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(tall, stub), false, 0);   // floating, no closing
        // (11,135) is XZ-nearest to the short stub, whose band would NOT clear y=135, but the tall column's
        // band [88,152] does — the union must return 0.
        int viaTall = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(m), 11, 135, 2);
        // control: a cell below both spans stays terrain (the carve keeps the bounded box, no shaft).
        int belowAll = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(List.of(m), 11, 80, 2);
        System.out.printf("[union] viaTall=%d belowAll=%d%n", viaTall, belowAll);
        Assertions.assertEquals(0, viaTall, "the union must let a TALL neighbor clear a high cell the nearest SHORT column would leave (no hug)");
        Assertions.assertNotEquals(0, belowAll, "the CARVE must not over-clear below the bands (the GUARD vetoes the stub, the carve leaves terrain)");
    }

    @Test
    void floatHMarginLateralBand() {
        int HM = BeyondEndChunkGenerator.FLOAT_HMARGIN, R = BeyondEndChunkGenerator.CARVE_ERODE_REACH;
        BoundingBox box = new BoundingBox(-8, 100, -8, 8, 104, 8);   // a piece; outer edge at x=8
        List<BeyondEndChunkGenerator.CarveMask> floating = List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), false));
        List<BeyondEndChunkGenerator.CarveMask> seated   = List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), true));
        int WM = BeyondEndChunkGenerator.WARP_MAX;
        int bandClear    = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(floating, 8 + HM, 102, 0);          // dXZ = HM (hard core) → 0
        int beyondBand   = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(floating, 8 + HM + WM + R + 1, 102, 0); // dXZ past the WARPED band + fringe (out)
        int seatedLateral= BeyondEndChunkGenerator.the_beyond$carveOutsideDist(seated,   8 + HM, 102, 0);          // SEATED: no band
        // just past the hard core, the cleared edge undulates per cell (organic, not a flat Chebyshev plane).
        int warpCleared = 0, warpTotal = 0;
        for (int zc = -8; zc <= 8; zc++)
            for (int yc = 100; yc <= 104; yc++) {
                warpTotal++;
                if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(floating, 8 + HM + 2, yc, zc) == 0) warpCleared++;
            }
        System.out.printf("[floatHMargin] HM=%d R=%d WM=%d bandClear=%d beyondBand=%d seatedLateral=%d warpCleared=%d/%d%n",
                HM, R, WM, bandClear, beyondBand, seatedLateral, warpCleared, warpTotal);
        Assertions.assertEquals(0, bandClear, "FLOATING must hard-clear the guaranteed lateral core (no horizontal hug)");
        Assertions.assertTrue(beyondBand > R, "beyond the WARPED band + erosion fringe must stay terrain (separate island untouched)");
        Assertions.assertTrue(seatedLateral > R, "SEATED gets NO lateral band — only the erosion reach (it rests on the island)");
        Assertions.assertTrue(warpCleared > 0 && warpCleared < warpTotal,
                "the OUTWARD warp makes the band edge UNDULATE (cleared past the hard core for some cells, terrain for others — not a flat plane)");
    }

    @Test
    void guardVetoesBelowBaseStubCarveDoesNot() {
        int HM = BeyondEndChunkGenerator.FLOAT_HMARGIN, SD = BeyondEndChunkGenerator.GUARD_STUB_DEPTH;
        int VM = BeyondEndChunkGenerator.FLOAT_VMARGIN, GU = BeyondEndChunkGenerator.GUARD_GROWTH_UP;
        BoundingBox box = new BoundingBox(-10, 200, -10, 10, 240, 10);   // FLOATING, gLo=200, colHi=240
        List<BeyondEndChunkGenerator.CarveMask> m = List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), false));
        int carveStub    = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(m, 5, 180, 0);             // 20 below base, in-band → CARVE leaves terrain
        int guardStub    = BeyondEndChunkGenerator.the_beyond$guardOutsideDist(m, 5, 180, 0);             // same cell → GUARD vetoes
        int guardTooDeep = BeyondEndChunkGenerator.the_beyond$guardOutsideDist(m, 5, 200 - SD - 7, 0);    // past GUARD_STUB_DEPTH → not vetoed
        int carveGrowUp  = BeyondEndChunkGenerator.the_beyond$carveOutsideDist(m, 5, 240 + VM + 10, 0);   // above colHi+VM → CARVE leaves air
        int guardGrowUp  = BeyondEndChunkGenerator.the_beyond$guardOutsideDist(m, 5, 240 + VM + 10, 0);   // same → GUARD vetoes the upward-growing column
        int guardTooHigh = BeyondEndChunkGenerator.the_beyond$guardOutsideDist(m, 5, 240 + VM + GU + 7, 0); // past GROWTH_UP → not vetoed
        int outBand      = 10 + HM + BeyondEndChunkGenerator.WARP_MAX + BeyondEndChunkGenerator.CARVE_ERODE_REACH + 1;
        int guardOutBand = BeyondEndChunkGenerator.the_beyond$guardOutsideDist(m, outBand, 180, 0);       // out-of-band → not vetoed
        System.out.printf("[guardStub] SD=%d GU=%d carveStub=%d guardStub=%d guardTooDeep=%d carveGrowUp=%d guardGrowUp=%d guardTooHigh=%d guardOutBand=%d%n",
                SD, GU, carveStub, guardStub, guardTooDeep, carveGrowUp, guardGrowUp, guardTooHigh, guardOutBand);
        Assertions.assertNotEquals(0, carveStub, "the CARVE leaves the below-base stub as terrain (no pillar / cross-structure bridge)");
        Assertions.assertEquals(0, guardStub, "the GUARD vetoes a feature on the below-base stub (no floating hut/antenna)");
        Assertions.assertNotEquals(0, guardTooDeep, "the guard stub veto is BOUNDED by GUARD_STUB_DEPTH (not an infinite shaft)");
        Assertions.assertNotEquals(0, carveGrowUp, "the CARVE does NOT clear above colHi+FLOAT_VMARGIN (only the guard ceiling extends)");
        Assertions.assertEquals(0, guardGrowUp, "the GUARD vetoes an upward-growing column above colHi+FLOAT_VMARGIN (no floating dralgae top)");
        Assertions.assertNotEquals(0, guardTooHigh, "the guard growth-up veto is BOUNDED by GUARD_GROWTH_UP");
        Assertions.assertNotEquals(0, guardOutBand, "out-of-band is never guarded (a separate island's features survive)");
    }

    @Test
    void landmarkCavityRejectGate() {
        BoundingBox station = new BoundingBox(-60, 100, -40, 60, 160, 40);
        List<BeyondEndChunkGenerator.CarveMask> masks =
                List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(station), false));   // FLOATING station
        final int HM = BeyondEndChunkGenerator.FLOAT_HMARGIN, REACH = BeyondEndChunkGenerator.CARVE_ERODE_REACH;

        // (A) landmark INSIDE the cavity (small footprint well within the station's hard-clear interior).
        BoundingBox inside = new BoundingBox(-6, 128, -6, 6, 140, 6);
        // (B) landmark OUTSIDE: shifted in +X past the station footprint + warped band (HM+WARP_MAX) + erosion fringe.
        int outMinX = 60 + HM + BeyondEndChunkGenerator.WARP_MAX + REACH + 4;
        BoundingBox outside = new BoundingBox(outMinX, 128, -6, outMinX + 12, 140, 6);

        double fracInside  = footprintHoleFraction(masks, inside);
        double fracOutside = footprintHoleFraction(masks, outside);
        System.out.printf("[landmarkCavityRejectGate] fracInside=%.2f fracOutside=%.2f (HM=%d REACH=%d)%n",
                fracInside, fracOutside, HM, REACH);

        Assertions.assertTrue(fracInside >= 0.5,
                "landmark centred in the station hard-clear must reject (frac>=0.5), got " + fracInside);
        Assertions.assertEquals(0.0, fracOutside, 1e-9,
                "landmark beyond the station band+fringe must NOT reject (frac==0), got " + fracOutside);
    }

    /** Fraction of a landmark's footprint columns with a cell at carveOutsideDist==0; mirrors the_beyond$landmarkInForeignCavity's reject metric. */
    private static double footprintHoleFraction(List<BeyondEndChunkGenerator.CarveMask> masks, BoundingBox lm) {
        BeyondEndChunkGenerator.CarveMask own =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(lm), false);
        int probed = 0, inHole = 0;
        for (int j = 0; j < own.d; j++) {
            for (int i = 0; i < own.w; i++) {
                int bit = i + j * own.w;
                if ((own.occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                if (own.isFilled(bit)) continue;
                int wx = own.oX + i, wz = own.oZ + j;
                probed++;
                for (int y = own.colLo(bit); y <= own.colHi(bit); y++) {
                    if (BeyondEndChunkGenerator.the_beyond$carveOutsideDist(masks, wx, y, wz) == 0) { inHole++; break; }
                }
            }
        }
        return probed == 0 ? 0.0 : (double) inHole / probed;
    }

    /** Pins the shipped {@code the_beyond$carvePenalty}: no burial, guard superset, elliptical falloff, 3D warp. */
    @Test
    void organicCarvePenaltyGate() {
        final int lo = 100, hi = 140;
        final int LAT = BeyondEndChunkGenerator.CARVE_LAT_REACH;
        final double WARP = BeyondEndChunkGenerator.CARVE_WARP_AMT;
        final int guardLat = BeyondEndChunkGenerator.FLOAT_HMARGIN + BeyondEndChunkGenerator.CARVE_ERODE_REACH;  // guard worst-case lateral reach (13)
        final int[] LO = {lo}, HI = {hi}, GLO = {lo}; final boolean[] NO = {false};   // FLOATING: gLo unused by the moat guard
        // FLOATING ignores d3 entirely (the distributed branch is the only d3 reader), so any out-of-range value works.
        final int[] D3F = {BeyondEndChunkGenerator.DT3_CAP + 1};

        Assertions.assertEquals(Double.POSITIVE_INFINITY,
                BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{0}, LO, HI, GLO, NO, NO, NO, 1,120, WARP, D3F),
                "occupied-span cell must signal unconditional clear (no burial), even with inward warp");
        Assertions.assertEquals(0.0,
                BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{guardLat}, LO, HI, GLO, NO, NO, NO, 1,120, WARP, D3F), 1e-9,
                "max-inward-warp must NOT clear at the guard's lateral edge (else a feature floats); guardLat=" + guardLat);
        double latP = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{8}, LO, HI, GLO, NO, NO, NO, 1,120, 0.0, D3F);
        double vertP = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{0}, LO, HI, GLO, NO, NO, NO, 1,hi + 8, 0.0, D3F);
        Assertions.assertTrue(vertP > latP,
                "elliptical: a vertical offset keeps more penalty than the same lateral offset (vertP=" + vertP + " latP=" + latP + ")");
        double pIn  = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{7}, LO, HI, GLO, NO, NO, NO, 1,120, +WARP, D3F);
        double pOut = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{7}, LO, HI, GLO, NO, NO, NO, 1,120, -WARP, D3F);
        Assertions.assertTrue(pIn > pOut + 0.1,
                "the 3D warp must move the cut at a fixed lateral column (vertical de-straightening); pIn=" + pIn + " pOut=" + pOut);

        // density-followed, unwarped
        final double D0 = 0.9, AMP = 0.6, T = 0.1;
        int minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
        StringBuilder profile = new StringBuilder();
        for (int y = lo + 2; y <= hi - 2; y += 3) {
            double density = D0 + AMP * Math.sin(y * 0.4);     // the island's natural vertical variation
            int boundary = LAT;
            for (int dxz = 1; dxz <= LAT; dxz++) {
                double pen = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{dxz}, LO, HI, GLO, NO, NO, NO, 1,y, 0.0, D3F);
                if (density - pen > T) { boundary = dxz; break; }   // first SOLID dxz outward = the cut face
            }
            minB = Math.min(minB, boundary); maxB = Math.max(maxB, boundary);
            profile.append(boundary).append(' ');
        }
        int range = maxB - minB;
        System.out.printf("[organicCarvePenaltyGate] cut-face dxz per y: %s| range=%d (flat machined plane=0)%n", profile, range);
        Assertions.assertTrue(range >= 2,
                "the cut face must FOLLOW the density (boundary varies >=2 blocks), not a flat machined plane; range=" + range);
    }

    @Test
    void guardSparesIntactTerrainVetoesRemovedAir() {
        BoundingBox box = new BoundingBox(-10, 200, -10, 10, 240, 10);   // FLOATING station, gLo=200, colHi=240
        var m = List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(box), false));
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(m, 0, 220, 0),
                "occupied span is carve-removed → feature in the cavity stays vetoed");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(m, 0, 200 - 30, 0),
                "intact terrain 30 BELOW the base is not carve-removed → island feature spared (the over-veto fix)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(m, 0, 240 + 30, 0),
                "intact terrain 30 ABOVE the top is not carve-removed → ceiling-island feature spared");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(m, 50, 220, 0),
                "far-lateral intact terrain is not carve-removed → spared");
    }

    /** Per-column Y-offsets used to clamp to a single byte (max 255); 16-bit offsets decode the true top instead. */
    @Test
    void tallTowerSpanNotClamped() {
        BoundingBox tall = new BoundingBox(-5, 100, -5, 5, 400, 5);   // FLOATING tower, span 300 > the old 255 byte clamp
        BeyondEndChunkGenerator.CarveMask mask = BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(tall), false);
        int bit = (0 - mask.oX) + (0 - mask.oZ) * mask.w;
        Assertions.assertEquals(100, mask.colLo(bit), "tower base decodes correctly");
        Assertions.assertEquals(400, mask.colHi(bit),
                "tower top must decode to 400, not the old byte clamp (gLo+255=355) that left it un-carved");
        var m = List.of(mask);
        Assertions.assertEquals(0, BeyondEndChunkGenerator.the_beyond$carveOutsideDist(m, 0, 400, 0),
                "the top of a >255-tall tower must be CARVED (16-bit span), not stranded above the old 255 clamp");
        Assertions.assertNotEquals(0, BeyondEndChunkGenerator.the_beyond$carveOutsideDist(
                        m, 0, 400 + BeyondEndChunkGenerator.FLOAT_VMARGIN + 10, 0),
                "well above the tower top stays un-carved (bounded — no phantom clear)");
    }

    /** A distributed structure's carve must peel only the thin skirt hugging its parts, not the wide FLOATING union. */
    @Test
    void distributedCarveSurgicalGate() {
        final int lo = 100, hi = 170;          // a tall distributed structure span (end_city ~70 tall)
        final int LAT  = BeyondEndChunkGenerator.CARVE_DIST_LAT_REACH;
        final int VERT = BeyondEndChunkGenerator.CARVE_DIST_VERT_REACH;
        final int BAND = BeyondEndChunkGenerator.DIST_SUPPORT_BAND;
        final double WARP = BeyondEndChunkGenerator.CARVE_WARP_AMT;
        final int[] LO = {lo}, HI = {hi}, GLO = {lo};   // GLO = the structure's global base (== lo for a single-piece span)
        final boolean[] YES = {true}, NO = {false};
        // The DISTRIBUTED branch keys off the precomputed TRUE-3D distance d3, not the per-column ellipse: NEAR
        // is a small value (inside the skirt), FAR is just past it (DT3_REACH+1); FLOATING ignores d3 (DT3_CAP+1).
        final int R3 = BeyondEndChunkGenerator.DT3_REACH;
        final int[] D3_NEAR = {2}, D3_FAR = {R3 + 1}, D3_F = {BeyondEndChunkGenerator.DT3_CAP + 1};

        Assertions.assertEquals(Double.POSITIVE_INFINITY,
                BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{0}, LO, HI, GLO, YES, NO, YES, 1, 130, 0.0, new int[]{0}),
                "distributed occupied-span cell must clear unconditionally (no burial)");

        int yUpper = lo + BAND + 4;            // safely above the support-contact band, inside the span
        double pNear = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{2}, LO, HI, GLO, YES, NO, YES, 1, yUpper, 0.0, D3_NEAR);
        Assertions.assertTrue(pNear > 0.0, "distributed carve must peel an island hugging the structure (penalty>0); got " + pNear);

        // not far, even at MAX inward warp — the warp is applied to the lookup INPUT at the call sites, not the
        // kernel, so a past-reach d3 stays 0.
        double pFar     = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{LAT + 3}, LO, HI, GLO, YES, NO, YES, 1, yUpper, 0.0, D3_FAR);
        double pFarWarp = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{LAT + 3}, LO, HI, GLO, YES, NO, YES, 1, yUpper, WARP, D3_FAR);
        Assertions.assertEquals(0.0, pFar, 1e-9, "distributed carve must NOT clear past DT3_REACH (no bubble); got " + pFar);
        Assertions.assertEquals(0.0, pFarWarp, 1e-9, "even at max inward warp the distributed carve stays bounded; got " + pFarWarp);

        double pBelow = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{2}, LO, HI, GLO, YES, NO, YES, 1, lo - 1, 0.0, D3_NEAR);
        Assertions.assertEquals(0.0, pBelow, 1e-9, "distributed carve must NOT clear below the base (support kept); got " + pBelow);

        double pAtBase = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{2}, LO, HI, GLO, YES, NO, YES, 1, lo, 0.0, D3_NEAR);
        double pRamp1  = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{2}, LO, HI, GLO, YES, NO, YES, 1, lo + 1, 0.0, D3_NEAR);
        double pRamp2  = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{2}, LO, HI, GLO, YES, NO, YES, 1, lo + 2, 0.0, D3_NEAR);
        Assertions.assertEquals(0.0, pAtBase, 1e-9, "no moat: the skirt is exactly 0 at the global base level; got " + pAtBase);
        Assertions.assertTrue(pRamp1 > 0.0 && pRamp2 > pRamp1,
                "support contact must RAMP up organically above the base (no flat shelf, no moat); pRamp1=" + pRamp1 + " pRamp2=" + pRamp2);

        int dxzMid = (LAT + BeyondEndChunkGenerator.CARVE_LAT_REACH) / 2;   // between the two reaches
        double pFloat = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{dxzMid}, LO, HI, GLO, NO,  NO, NO,  1, 130, 0.0, D3_F);    // FLOATING
        double pDist  = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{dxzMid}, LO, HI, GLO, YES, NO, YES, 1, 130, 0.0, D3_FAR);  // DISTRIBUTED
        Assertions.assertTrue(pFloat > 0.0, "FLOATING must clear at dxz=" + dxzMid + " (wide skirt); got " + pFloat);
        Assertions.assertEquals(0.0, pDist, 1e-9, "DISTRIBUTED must NOT clear at dxz=" + dxzMid + " (surgical); got " + pDist);

        double pCantilever = BeyondEndChunkGenerator.the_beyond$carvePenalty(
                new int[]{2}, new int[]{140}, new int[]{170}, new int[]{100}, YES, NO, YES, 1, 141, 0.0, D3_NEAR);
        Assertions.assertTrue(pCantilever > 0.0,
                "a cantilevered piece's underside must peel (moat guard keys off the GLOBAL base, not colLo); got " + pCantilever);

        // guard superset (parity): reaches pinned below the FLOAT_* band by construction.
        Assertions.assertTrue(LAT <= BeyondEndChunkGenerator.FLOAT_HMARGIN + BeyondEndChunkGenerator.CARVE_ERODE_REACH,
                "CARVE_DIST_LAT_REACH must stay within the guard's covered extent FLOAT_HMARGIN+CARVE_ERODE_REACH (carve ⊆ guard, no floating feature)");
        Assertions.assertTrue(VERT <= BeyondEndChunkGenerator.FLOAT_VMARGIN + BeyondEndChunkGenerator.CARVE_ERODE_REACH,
                "CARVE_DIST_VERT_REACH must stay within the guard's covered extent FLOAT_VMARGIN+CARVE_ERODE_REACH (carve ⊆ guard)");
        Assertions.assertTrue(
                LAT * Math.sqrt(1.0 + BeyondEndChunkGenerator.CARVE_DIST_WARP_GAIN * BeyondEndChunkGenerator.CARVE_WARP_AMT)
                        < BeyondEndChunkGenerator.FLOAT_HMARGIN,
                "the MAX warped distributed lateral clear must stay inside FLOAT_HMARGIN's hard guard band (warp parity, no feature floats)");
        BoundingBox dbox = new BoundingBox(-9, lo, -9, 9, hi, 9);
        BeyondEndChunkGenerator.CarveMask dmask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(dbox), true, BeyondEndChunkGenerator.CLOSE_RADIUS, true);
        Assertions.assertTrue(dmask.distributed, "fromBoxes(..., distributed=true) must build a DISTRIBUTED mask");
        var dmasks = List.of(dmask);
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(dmasks, 0, 130, 0),
                "distributed body cell must be carve-removed");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$guardOutsideDist(dmasks, 0, 130, 0)
                <= BeyondEndChunkGenerator.CARVE_ERODE_REACH, "guard must cover the carved body (parity)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(dmasks, 9 + LAT + 10, 130, 0),
                "far-lateral terrain past the skirt must NOT be carve-removed (neighbour island untouched)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(dmasks, 0, lo - 8, 0),
                "terrain below the base must NOT be carve-removed (support kept)");

        // de-saturated: bounded by CARVE_DIST_AMP, not the saturating FLOATING skirt amplitude
        // (which would flatten the cut into a cube).
        double pDistNear  = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{1}, LO, HI, GLO, YES, NO, YES, 1, yUpper, 0.0, new int[]{1});
        double pFloatNear = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{1}, LO, HI, GLO, NO,  NO, NO,  1, yUpper, 0.0, D3_F);
        Assertions.assertTrue(pDistNear <= BeyondEndChunkGenerator.CARVE_DIST_AMP + 1e-9,
                "distributed penalty must stay <= CARVE_DIST_AMP (bounded, strict subset of the guard band); got " + pDistNear);
        Assertions.assertTrue(pDistNear < 0.5 && pFloatNear > 1.0,
                "distributed must be DE-SATURATED (O(gap)) while FLOATING saturates (O(AMP)) — the de-square invariant; pDist=" + pDistNear + " pFloat=" + pFloatNear);

        System.out.printf("[distributedCarveSurgical] LAT=%d VERT=%d BAND=%d amp=%.2f pNear=%.3f pFar=%.3f pAtBase=%.3f pRamp1=%.3f pRamp2=%.3f pDistNear=%.3f pFloatNear=%.2f pCant=%.3f%n",
                LAT, VERT, BAND, BeyondEndChunkGenerator.CARVE_DIST_AMP, pNear, pFar, pAtBase, pRamp1, pRamp2, pDistNear, pFloatNear, pCantilever);
    }

    /** Tower↔ship topology (Y-disjoint, same XZ footprint): the old 2.5D model bridges the gap, true-3D does not. */
    @Test
    void distributedTrueModelGate() {
        final int R3 = BeyondEndChunkGenerator.DT3_REACH;
        Assertions.assertTrue(BeyondEndChunkGenerator.DT3_REACH + BeyondEndChunkGenerator.DT3_WARP_AMP
                        <= BeyondEndChunkGenerator.FLOAT_HMARGIN,
                "DT3_REACH + DT3_WARP_AMP must be <= FLOAT_HMARGIN (carve ⊆ guard band)");
        // two solid pieces, SAME XZ footprint, Y-disjoint with a gap [109,139] (31 > 2·DT3_REACH=12).
        BoundingBox low  = new BoundingBox(0, 100, 0, 8, 108, 8);
        BoundingBox high = new BoundingBox(0, 140, 0, 8, 148, 8);
        BeyondEndChunkGenerator.CarveMask mask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(low, high), true, BeyondEndChunkGenerator.CLOSE_RADIUS, true);
        Assertions.assertTrue(mask.distributed, "fromBoxes(..., distributed=true) must build a DISTRIBUTED mask");
        Assertions.assertNotNull(mask.dt3, "a distributed fromBoxes mask must build the TRUE-3D field");
        var masks = List.of(mask);

        final int gapY = 124;   // gap centre (≫ DT3_REACH from both pieces' Y)
        final int wallX = 9;    // one block off the +X wall (footprint x∈[0,8])

        // p<=0 short-circuit → terrain-independent.
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, wallX, gapY, 0),
                "the inter-piece gap must NOT be bridged (true-3D topology-following); the 2.5D convex union cleared this");
        // NON-VACUITY PIN: the OLD 2.5D per-column ellipse WOULD have cleared (dxz=1 to the nearest column, whose MERGED
        // span [100,148] makes vertOut=0 through the whole gap). Reproduced inline so the bridge is demonstrably real.
        double oldNl = 1.0 / BeyondEndChunkGenerator.CARVE_DIST_LAT_REACH;
        double oldBase2 = oldNl * oldNl;
        double oldEllipsePenalty = oldBase2 < 1.0
                ? BeyondEndChunkGenerator.CARVE_DIST_AMP * (1.0 - smoother(Math.sqrt(oldBase2))) : 0.0;
        Assertions.assertTrue(oldEllipsePenalty > 0.0,
                "non-vacuity: the OLD 2.5D ellipse WOULD bridge the gap (penalty>0), so (a) is a real de-bridge; got " + oldEllipsePenalty);
        // WHITE-BOX: the field itself discriminates — far in the gap, near at a wall (un-warped lookup).
        int ddGap  = mask.dt3At(wallX, gapY, 0);
        int ddWall = mask.dt3At(wallX, 104, 0);   // one block off the wall, AT the low piece's Y
        Assertions.assertTrue(ddGap > R3, "the 3D field must read PAST the skirt in the gap interior; ddGap=" + ddGap);
        Assertions.assertTrue(ddWall <= R3, "the 3D field must HUG the shell one block off a wall; ddWall=" + ddWall);

        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, 4, 104, 0),
                "an occupied body cell must be carve-removed (no burial)");

        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, 4, 100 - 8, 0),
                "terrain below the support base must NOT be carve-removed (support kept)");

        int removed = 0, parityViolations = 0;
        for (int x = -R3 - 4; x <= 8 + R3 + 4; x++) {
            for (int z = -R3 - 4; z <= 8 + R3 + 4; z++) {
                for (int y = 100 - R3 - 4; y <= 148 + R3 + 4; y++) {
                    if (!BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, x, y, z)) continue;
                    removed++;
                    if (BeyondEndChunkGenerator.the_beyond$guardOutsideDist(masks, x, y, z) > BeyondEndChunkGenerator.CARVE_ERODE_REACH)
                        parityViolations++;
                }
            }
        }
        System.out.printf("[distributedTrueModel] ddGap=%d ddWall=%d removed=%d parityViolations=%d oldEllipse=%.3f%n",
                ddGap, ddWall, removed, parityViolations, oldEllipsePenalty);
        Assertions.assertTrue(removed > 0, "sample must contain carve-removed cells (else parity is vacuous)");
        Assertions.assertEquals(0, parityViolations,
                parityViolations + " carve-removed cells fell OUTSIDE the guard band (carve ⊄ guard — a feature could float)");
    }

    @Test
    void baseAnchorPreserveGate() {
        Assertions.assertTrue(BeyondEndChunkGenerator.BEARD_LAT_REACH <= BeyondEndChunkGenerator.FLOAT_HMARGIN,
                "preserve lateral reach (BEARD_LAT_REACH) must stay inside the guard band (FLOAT_HMARGIN)");
        Assertions.assertTrue(BeyondEndChunkGenerator.DT3_REACH + BeyondEndChunkGenerator.DT3_WARP_AMP
                        <= BeyondEndChunkGenerator.FLOAT_HMARGIN,
                "carve ⊆ guard pin (unchanged): DT3_REACH + DT3_WARP_AMP <= FLOAT_HMARGIN");

        // test cell sits at y=lo-2, past the global support-contact ramp band, so only the base-anchor clip can
        // preserve it — isolates the clip from the ramp (else the ramp would mask the base-scope distinction).
        final int gLo = 100, lo = 120, hi = 170;
        final int[] HI = {hi}, GLO = {gLo}, LO = {lo}, DXZ2 = {2}, D3 = {2};   // fringe dxz=2, inside the skirt dd=2
        final boolean[] YES = {true}, NO = {false};
        final boolean[] BASE_T = {true}, BASE_F = {false};
        Assertions.assertTrue(lo - 2 - gLo > BeyondEndChunkGenerator.DIST_SUPPORT_BAND,
                "test cell must be past the support-contact ramp band so only the base-anchor clip can preserve it");

        double pIsland = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, YES, NO, YES, 1, lo - 2, 0.0, D3, true,  0.0, BASE_T);
        double pVoid   = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, YES, NO, YES, 1, lo - 2, 0.0, D3, false, 0.0, BASE_T);
        Assertions.assertEquals(0.0, pIsland, 1e-9, "base fringe below base WITH island below is PRESERVED (welds the start_platform)");
        Assertions.assertTrue(pVoid > 0.0, "the SAME cell over the VOID (rockBelow=false) is still CARVED (no §16 float)");

        double pCantilever = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, YES, NO, YES, 1, lo - 2, 0.0, D3, true, 0.0, BASE_F);
        Assertions.assertTrue(pCantilever > 0.0, "preserve is confined to the start footprint (does NOT weld cantilever underside island)");

        double pAbove = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, YES, NO, YES, 1, lo + 3, 0.0, D3, true, 0.0, BASE_T);
        Assertions.assertTrue(pAbove > 0.0, "preserve is y<lo only; above-base is still carved");

        // boundary y==lo: the clip uses '<' not '<=', so the base row is left to the existing occupied-span/dd==0 logic.
        double pAtBase = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, YES, NO, YES, 1, lo, 0.0, D3, true, 0.0, BASE_T);
        Assertions.assertTrue(pAtBase > 0.0, "y==lo is NOT clip-handled (the '<' boundary; the base row stays with the skirt/ramp)");

        double pBody = BeyondEndChunkGenerator.the_beyond$carvePenalty(new int[]{0}, LO, HI, GLO, YES, NO, YES, 1, 130, 0.0, new int[]{0}, true, 0.0, BASE_T);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, pBody, "no-burial: occupied-span cell clears unconditionally; ship/body untouched");

        // parity sweep on a mask WITH a start baseBox: the clip only shrinks the removed set, so this must stay 0.
        BoundingBox start = new BoundingBox(0, 108, 0, 8, 116, 8);   // boxes.get(0) ⇒ baseBox live ⇒ inBaseFootprint active
        var masks = List.of(BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(start), true, BeyondEndChunkGenerator.CLOSE_RADIUS, true));
        Assertions.assertNotNull(masks.get(0).baseBox, "the start mask must carry a baseBox so the preserve scope is live");
        int removed = 0, parityViolations = 0;
        final int R3 = BeyondEndChunkGenerator.DT3_REACH;
        for (int x = -R3 - 6; x <= 8 + R3 + 6; x++)
            for (int z = -R3 - 6; z <= 8 + R3 + 6; z++)
                for (int y = 108 - R3 - 6; y <= 116 + R3 + 6; y++) {
                    if (!BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, x, y, z)) continue;
                    removed++;
                    if (BeyondEndChunkGenerator.the_beyond$guardOutsideDist(masks, x, y, z) > BeyondEndChunkGenerator.CARVE_ERODE_REACH)
                        parityViolations++;
                }
        System.out.printf("[baseAnchorPreserve] pIsland=%f pVoid=%f pCantilever=%f pAbove=%f pAtBase=%f pBody=%f removed=%d parityViolations=%d%n",
                pIsland, pVoid, pCantilever, pAbove, pAtBase, pBody, removed, parityViolations);
        Assertions.assertTrue(removed > 0, "sweep must contain carve-removed cells (else parity is vacuous)");
        Assertions.assertEquals(0, parityViolations,
                parityViolations + " preserved/carved cell fell OUTSIDE the guard band (carve ⊄ guard — a feature could float)");
    }

    @Test
    void baseBeardCoverGate() {
        final double AMP = BeyondEndChunkGenerator.BASE_BEARD_AMP;
        final int VFADE = BeyondEndChunkGenerator.BASE_BEARD_VFADE;
        final int REACH = BeyondEndChunkGenerator.BEARD_LAT_REACH;
        final int COVER = BeyondEndChunkGenerator.PLATFORM_COVER;

        // AMP WINDOW: above the measured far-field gap p90 (reliable fill) but below the saturating p99 tail —
        // a higher amp re-flattens the cover into the rejected placed slab.
        Assertions.assertTrue(AMP >= 0.35 && AMP < 0.55, "BASE_BEARD_AMP must stay in [0.35,0.55) — rounds, not a flat slab");
        Assertions.assertTrue(REACH <= BeyondEndChunkGenerator.FLOAT_HMARGIN,
                "cover lateral reach (BEARD_LAT_REACH) must stay inside the guard band (FLOAT_HMARGIN) — no float");

        // tower floor=120, start_platform pedestal spans [115,119] (tapered, so colLo varies up to 119);
        // colHi=170 (tall tower); natTop = a connected island 6 below colLo.
        final int floorY = 120;
        final int colLo = floorY - COVER, colHi = 170, natTop = colLo - 6;
        final int target = Math.min(colLo + (COVER - 1), floorY - 1);        // = floorY-1 = 119 (platform top)

        // peak fill: dxz=0, below the platform top (feather=1, vAmp=1).
        Assertions.assertEquals(AMP, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, 0, colLo + 1), 1e-9,
                "directly under the foot, below the platform top, the cover adds full amp");
        Assertions.assertEquals(AMP, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, 0, target), 1e-9,
                "at the platform top the cover still adds full amp (y<=target)");

        for (int dxz = 0; dxz <= REACH; dxz++)
            for (int y = natTop - 4; y <= floorY + 4; y++)
                Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(Integer.MIN_VALUE, colLo, colHi, floorY, dxz, y), 1e-9,
                        "no connected island below ⇒ ZERO cover (cannot float)");

        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, 0, natTop), 1e-9, "no add at the island top");
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, 0, natTop - 1), 1e-9, "no add below the island");

        // the core invariant, swept over every taper column the platform can produce.
        for (int tcl = floorY - COVER; tcl <= floorY - 1; tcl++)
            for (int dxz = 0; dxz <= REACH; dxz++)
                for (int y = floorY; y <= floorY + COVER + VFADE; y++)
                    Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(tcl - 6, tcl, colHi, floorY, dxz, y), 1e-9,
                            "cover must NEVER fill at/above the building floor (taper colLo=" + tcl + ", y=" + y + ")");

        double prevV = AMP + 1;
        for (int y = target; y <= floorY; y++) {
            double d = BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, 0, y);
            Assertions.assertTrue(d <= prevV + 1e-9, "vertical profile must be non-increasing toward the floor ceiling");
            prevV = d;
        }
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, 0, floorY), 1e-9, "hard ceiling at the building floor");

        double prevL = AMP + 1;
        for (int dxz = 0; dxz <= REACH; dxz++) {
            double d = BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, dxz, colLo + 1);
            Assertions.assertTrue(d <= prevL + 1e-9, "lateral apron must be non-increasing outward (rounded face)");
            prevL = d;
        }
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, REACH, colLo + 1), 1e-9, "apron tapers to 0 at BEARD_LAT_REACH");
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, REACH + 1, colLo + 1), 1e-9, "no add beyond the reach");

        // anti-bury: colHi clamps the target so a short cantilever box is never over-covered.
        final int thinHi = colLo + 1;   // a 2-tall lobe (colHi < target)
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, thinHi, floorY, 0, target), 1e-9,
                "thin piece: the cover never exceeds the piece's own top (anti-bury), so no add at the full platform-top target");

        // bounded extent (carve ⊆ guard pin).
        for (int dxz = 0; dxz <= REACH + 3; dxz++)
            for (int y = natTop - 3; y <= colHi + 5; y++) {
                double d = BeyondEndChunkGenerator.the_beyond$baseBeardDelta(natTop, colLo, colHi, floorY, dxz, y);
                if (d <= 0.0) continue;
                Assertions.assertTrue(dxz < REACH && dxz <= BeyondEndChunkGenerator.FLOAT_HMARGIN,
                        "cover add only inside the float-safe lateral band");
                Assertions.assertTrue(y > natTop && y < floorY,
                        "cover add only in (natTop, floorY) — never below the island, never at/into the building");
            }

        // aggregator: the_beyond$baseBeardDeltaAt over per-bit arrays, gated on ground-contact (baseNat != MIN)
        // rather than the start footprint (base[k]).
        final int[] LO = {colLo}, HI = {colHi}, NAT = {natTop}, FLOOR = {floorY}, DXZ0 = {0};
        Assertions.assertEquals(AMP, BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(DXZ0, LO, HI, new boolean[]{true}, NAT, FLOOR, 1, colLo + 1), 1e-9,
                "an in-footprint base column with island below covers at full amp under the foot");
        Assertions.assertEquals(AMP, BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(DXZ0, LO, HI, new boolean[]{false}, NAT, FLOOR, 1, colLo + 1), 1e-9,
                "FULL-FOOTPRINT: a ground-contact column OUTSIDE the start footprint is NOW bearded too (gated on island-below, not base[k])");
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(DXZ0, LO, HI, new boolean[]{false}, new int[]{Integer.MIN_VALUE}, FLOOR, 1, colLo + 1), 1e-9,
                "a non-base column over VOID (baseNat=MIN, a true cantilever) adds nothing — no weld/bridge");
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(DXZ0, LO, HI, new boolean[]{true}, new int[]{Integer.MIN_VALUE}, FLOOR, 1, colLo + 1), 1e-9,
                "a base column with NO island below adds nothing (no float)");
        // GROUND-REST gate (anti-bridge): a column resting within FOUNDATION_MAX_LIP of its island keeps natTop; a piece
        // floating >MAX_LIP above an incidental island it merely skims is excluded (MIN), so no beard bridge to it.
        Assertions.assertEquals(natTop, BeyondEndChunkGenerator.the_beyond$groundRestNatTop(natTop + BeyondEndChunkGenerator.FOUNDATION_MAX_LIP, natTop),
                "ground-rest: a column within MAX_LIP of its island is ground-contact (bearded)");
        Assertions.assertEquals(Integer.MIN_VALUE, BeyondEndChunkGenerator.the_beyond$groundRestNatTop(natTop + BeyondEndChunkGenerator.FOUNDATION_MAX_LIP + 1, natTop),
                "ground-rest: a cantilever floating >MAX_LIP above an incidental island is NOT welded (anti-bridge)");
        // SEAT-PANCAKE CONFINEMENT (anti wall-silhouette): an island within BEARD_SEAT_DROP of the seat floor is
        // welded; deeper than that is a lower pancake and dropped, so the beard never walls an island it doesn't sit on.
        final int seatFloor = 100;
        Assertions.assertEquals(seatFloor - BeyondEndChunkGenerator.BEARD_SEAT_DROP,
                BeyondEndChunkGenerator.the_beyond$seatConfinedNatTop(seatFloor - BeyondEndChunkGenerator.BEARD_SEAT_DROP, seatFloor),
                "seat-confine: an island exactly BEARD_SEAT_DROP below the seat floor (bunker) stays welded");
        Assertions.assertEquals(Integer.MIN_VALUE,
                BeyondEndChunkGenerator.the_beyond$seatConfinedNatTop(seatFloor - BeyondEndChunkGenerator.BEARD_SEAT_DROP - 1, seatFloor),
                "seat-confine: an island >BEARD_SEAT_DROP below the seat floor (lower pancake) is dropped — no wall-silhouette");
        Assertions.assertEquals(seatFloor - 40,
                BeyondEndChunkGenerator.the_beyond$seatConfinedNatTop(seatFloor - 40, Integer.MIN_VALUE),
                "seat-confine: no baseBox (seatFloor=MIN) is a pass-through (legacy/unconfined)");
        Assertions.assertEquals(Integer.MIN_VALUE,
                BeyondEndChunkGenerator.the_beyond$seatConfinedNatTop(Integer.MIN_VALUE, seatFloor),
                "seat-confine: a MIN natTop (no island) stays MIN");
        double union = BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(new int[]{3, 0}, new int[]{colLo, colLo}, new int[]{colHi, colHi},
                new boolean[]{true, true}, new int[]{natTop, natTop}, new int[]{floorY, floorY}, 2, colLo + 1);
        Assertions.assertEquals(AMP, union, 1e-9, "union takes the max over base columns (the nearest dominates)");

        // taper over-fill: a shallow taper column (colLo=floorY-1) MAXed into the apron must not push the fill
        // above the floor — the cap tops it at floorY-1.
        final int[] TAPER_LO = {floorY - 1}, TAPER_HI = {colHi}, TAPER_NAT = {floorY - 1 - 6};
        Assertions.assertEquals(AMP, BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(DXZ0, TAPER_LO, TAPER_HI, new boolean[]{true}, TAPER_NAT, FLOOR, 1, floorY - 1), 1e-9,
                "taper column: cover still tops the platform at floorY-1");
        for (int y = floorY; y <= floorY + COVER; y++)
            Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$baseBeardDeltaAt(DXZ0, TAPER_LO, TAPER_HI, new boolean[]{true}, TAPER_NAT, FLOOR, 1, y), 1e-9,
                    "taper column MAX must NOT bury the building (y=" + y + " >= floor)");

        System.out.printf("[baseBeardCover] amp=%.3f vfade=%d reach=%d cover=%d floor=%d target(capped)=%d%n", AMP, VFADE, REACH, COVER, floorY, target);
    }

    /** The no-burial carve must KEEP the island body over a structure's authored sub-surface parts (mirestone bunker). */
    @Test
    void buriedAboveKeepGate() {
        final int gLo = 100, lo = 110, hi = 170;
        final int[] HI = {hi}, GLO = {gLo}, LO = {lo}, DXZ2 = {2}, DXZ0 = {0}, D3 = {2}, D30 = {0};
        final boolean[] SEAT = {true}, FILL_N = {false}, DIST = {true}, BASE_F = {false}, BASE_T = {true};
        final boolean[] BURIED_T = {true}, BURIED_F = {false};
        // A SHELL test cell: dxz=2 (offset), y in [lo,hi] (so y<=hi → SHELL true), past the support ramp.
        final int yShell = 130;
        Assertions.assertTrue(yShell - gLo >= BeyondEndChunkGenerator.DIST_SUPPORT_BAND,
                "shell test cell must be past the support-contact ramp so only the keep clause acts");

        double pKeep  = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_T);
        double pCarve = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_F);
        Assertions.assertEquals(0.0, pKeep, 1e-9, "buried-column shell WITH island beneath is KEPT (island buries the bunker)");
        Assertions.assertTrue(pCarve > 0.0, "non-vacuity: signal off (buriedAbove=false) WOULD peel the same shell cell");

        final int[] HI_LOW = {130};         // a low roof so a cap cell sits within reach
        final int yCap = 131;               // dxz=0, just above the roof
        double pCap    = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ0, LO, HI_LOW, GLO, SEAT, FILL_N, DIST, 1, yCap, 0.0, D3, true, 0.0, BASE_F, BURIED_T);
        double pCapOld = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ0, LO, HI_LOW, GLO, SEAT, FILL_N, DIST, 1, yCap, 0.0, D3, true, 0.0, BASE_F, BURIED_F);
        Assertions.assertEquals(0.0, pCap, 1e-9, "the island CAP directly above the buried roof is KEPT (buries it)");
        Assertions.assertEquals(Double.POSITIVE_INFINITY, pCapOld, "pre-fix: the A2 ceiling clear removed the cap (the rectangular pit over the bunker)");

        final int yAboveCap = hi + BeyondEndChunkGenerator.CARVE_CEIL_REACH + 4;
        double pAbove = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yAboveCap, 0.0, new int[]{1}, true, 0.0, BASE_F, BURIED_F);
        Assertions.assertTrue(pAbove > 0.0, "above the cap reach the skirt is unchanged (surface part exposed)");

        double pBaseT = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_T, BURIED_T);
        double pBaseF = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_T, BURIED_F);
        double p14    = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F);
        double p15null= BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, (boolean[]) null);
        Assertions.assertEquals(pBaseF, pBaseT, 1e-9, "in-footprint (base[k]=true) is byte-identical with/without buriedAbove (end_city untouched)");
        Assertions.assertTrue(pBaseT > 0.0, "in-footprint cell still carves (scoped out by !base[k])");
        Assertions.assertEquals(p15null, p14, 1e-9, "the 14-arg overload == 15-arg buriedAbove=null (existing callers/guard proxy byte-identical)");

        double pVoid = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, false, 0.0, BASE_F, BURIED_T);
        Assertions.assertTrue(pVoid > 0.0, "buried shell over VOID (rockBelow=false) is still carved (ship floats, no §16 bump)");

        // the pre-loop keep detects `occupied` and falls through regardless of buriedAbove.
        double pOcc = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ0, LO, HI, GLO, SEAT, FILL_N, DIST, 1, 130, 0.0, D30, true, 0.0, BASE_F, BURIED_T);
        Assertions.assertEquals(Double.POSITIVE_INFINITY, pOcc, "occupied span clears unconditionally (room stays hollow)");

        final int CR = BeyondEndChunkGenerator.CARVE_CEIL_REACH, FR = BeyondEndChunkGenerator.FACE_ROUGH;
        final int[] HI_SEAT = {150};
        int minCeil = Integer.MAX_VALUE, maxCeil = Integer.MIN_VALUE;
        for (int i = 0; i <= 20; i++) {
            double rough = -1.0 + 2.0 * i / 20.0;
            // Find the max cleared headroom (largest (y-hi) that returns +INF) for this rough.
            int ceil = 0;
            for (int off = 1; off <= CR + 2; off++) {
                double p = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ0, LO, HI_SEAT, GLO, SEAT, FILL_N, DIST, 1, 150 + off, 0.0, new int[]{DT3CAPSENTINEL()}, true, rough, BASE_F, BURIED_F);
                if (p == Double.POSITIVE_INFINITY) ceil = off; else break;
            }
            if (ceil < minCeil) minCeil = ceil;
            if (ceil > maxCeil) maxCeil = ceil;
            Assertions.assertTrue(ceil <= CR, "FACE B is INWARD-only: cleared headroom (" + ceil + ") must never exceed CARVE_CEIL_REACH=" + CR);
            Assertions.assertTrue(ceil >= CR - FR, "FACE B clamp: cleared headroom (" + ceil + ") must stay >= CARVE_CEIL_REACH-FACE_ROUGH=" + (CR - FR));
        }
        Assertions.assertTrue(maxCeil - minCeil >= 2, "FACE B wander: the cleared headroom must VARY by >=2 across rough (else the rough is inert); span=" + (maxCeil - minCeil));

        // must be seated (seat&&!dist has no skirt) so the +INF at hi+off comes only from the A2 ceiling.
        final boolean[] DIST_F = {false};
        for (int i = 0; i <= 10; i++) {
            double rough = -1.0 + 2.0 * i / 10.0;
            int ceil = 0;
            for (int off = 1; off <= CR + 2; off++) {
                double p = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ0, LO, HI_SEAT, GLO, SEAT, FILL_N, DIST_F, 1, 150 + off, 0.0, new int[]{DT3CAPSENTINEL()}, true, rough, BASE_F, BURIED_F);
                if (p == Double.POSITIVE_INFINITY) ceil = off; else break;
            }
            Assertions.assertEquals(CR, ceil, "FACE B is dist-gated: an all-floating (seated) column's A2 ceiling == CARVE_CEIL_REACH exactly (rough inert, station byte-identical); rough=" + rough);
        }

        double pOpen = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_F);
        Assertions.assertTrue(pOpen > 0.0, "open (buriedAbove=false) sunken floor is CARVED, never walled in");

        // (G10) the blocker gate (2-bit): bits {buried k0 at dxz=0, non-buried n at dxz=2}.
        final int[] DXZ_2BIT = {0, 2};      // k0 at the column, n offset by 2
        final int[] LO_2BIT  = {lo, lo};
        final int[] HI_2BIT  = {130, 170};  // k0 roof low (130) so a cap cell sits at 131; n is a tall exposed neighbour
        final int[] GLO_2BIT = {gLo, gLo};
        final boolean[] SEAT_2 = {true, true}, FILL_2 = {false, false}, DIST_2 = {true, true}, BASE_2 = {false, false};
        final boolean[] BURIED_2 = {true, false};   // k0 buried, n NOT buried (the exposed lateral neighbour)
        final int[] D3_2 = {2, 2};                  // both within DT3_REACH (n's skirt reaches the cap cell)
        double p2bit = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ_2BIT, LO_2BIT, HI_2BIT, GLO_2BIT, SEAT_2, FILL_2, DIST_2, 2, 131, 0.0, D3_2, true, 0.0, BASE_2, BURIED_2);
        Assertions.assertEquals(0.0, p2bit, 1e-9, "G10: the buried column's CAP stays KEPT even though a non-buried lateral neighbour's skirt would carve it (dominating pre-loop return-0; the per-bit-continue leak)");
        final boolean[] BURIED_2_OFF = {false, false};
        double p2bitOff = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ_2BIT, LO_2BIT, HI_2BIT, GLO_2BIT, SEAT_2, FILL_2, DIST_2, 2, 131, 0.0, D3_2, true, 0.0, BASE_2, BURIED_2_OFF);
        Assertions.assertTrue(p2bitOff == Double.POSITIVE_INFINITY || p2bitOff > 0.0,
                "G10 non-vacuity: with NO buried bit the same cell IS carved (so the keep is doing real work); got " + p2bitOff);

        // (G11) above-base ship cleared: same buried shell as G1, but the roof is at/above the surface seat.
        final int[] FLOOR_ABOVE = {hi - 40};   // surface seat well below the column roof → above-base
        double pShipAbove = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_T, FLOOR_ABOVE);
        Assertions.assertTrue(pShipAbove > 0.0, "G11: a buried shell whose roof is AT/ABOVE the surface seat (end_city ship in a stacked region) is CARVED, not kept");
        // (G12) sub-surface bunker still kept.
        final int[] FLOOR_SUB = {hi + 20};     // surface seat above the room roof → sub-surface
        double pBunkerKept = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_T, FLOOR_SUB);
        Assertions.assertEquals(0.0, pBunkerKept, 1e-9, "G12: a buried shell whose roof is BELOW the surface seat (mirestone bunker) is KEPT (still buried)");
        // (G13) 16-arg baseFloor=null must equal the 15-arg legacy keep.
        double p16null = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_T, (int[]) null);
        Assertions.assertEquals(pKeep, p16null, 1e-9, "G13: 16-arg baseFloor=null is byte-identical to the 15-arg legacy keep (no sub-surface gate)");

        // (G14) boundary — room ceiling at the seat: a strict `<` instead of `<=` would re-expose it.
        final int[] FLOOR_AT = {hi};
        double pCeilAtSeat = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_T, FLOOR_AT);
        Assertions.assertEquals(0.0, pCeilAtSeat, 1e-9, "G14: a buried room ceiling AT the surface seat (hi == baseFloor — the mirestone bunker) is KEPT (a strict < would re-expose it)");
        // (G15) boundary — roof 1 above the seat: must be CARVED, so the throat surfaces by design.
        final int[] FLOOR_BELOW1 = {hi - 1};
        double pAboveSeat1 = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ2, LO, HI, GLO, SEAT, FILL_N, DIST, 1, yShell, 0.0, D3, true, 0.0, BASE_F, BURIED_T, FLOOR_BELOW1);
        Assertions.assertTrue(pAboveSeat1 > 0.0, "G15: a buried roof 1 ABOVE the seat (entrance throat / surface part) is CARVED (only AT/below the seat is kept)");

        System.out.printf("[buriedAbove] keepReach=%d ceilReach=%d faceRough=%d pKeep=%.3f pCap=%.3f pCarve=%.3f ceil[min=%d max=%d] p2bit=%.3f pShipAbove=%.3f pBunkerKept=%.3f pCeilAtSeat=%.3f pAboveSeat1=%.3f%n",
                BeyondEndChunkGenerator.SUBSURF_KEEP_REACH, CR, FR, pKeep, pCap, pCarve, minCeil, maxCeil, p2bit, pShipAbove, pBunkerKept, pCeilAtSeat, pAboveSeat1);
    }

    /** A DT3 distance sentinel meaning "no field / out of range" (DT3_CAP+1) so the FACE B kernel cases isolate the A2
     *  ceiling path (the dd==DT3_CAP+1 branch falls to the 2.5D fallback, but for dxz==0 the A2 ceiling fires first). */
    private static int DT3CAPSENTINEL() {
        return BeyondEndChunkGenerator.DT3_CAP + 1;
    }

    @Test
    void faceRoughInwardOnlyAndWandersGate() {
        Assertions.assertTrue(BeyondEndChunkGenerator.FACE_ROUGH >= 0
                        && BeyondEndChunkGenerator.FACE_ROUGH < BeyondEndChunkGenerator.DT3_REACH,
                "FACE_ROUGH must be in [0, DT3_REACH) (reachEff stays positive and never exceeds DT3_REACH)");

        final int R3 = BeyondEndChunkGenerator.DT3_REACH;
        final int WARP = (int) Math.ceil(BeyondEndChunkGenerator.DT3_WARP_AMP);
        final int OUTER_BOUND = R3 + WARP;   // = FLOAT_HMARGIN; max true block-distance a removed cell may have

        // A wide, flat distributed slab (1-thick in Z so the +X/+Z faces are flat planes — the reported flat-cut case).
        BoundingBox slab = new BoundingBox(0, 100, 0, 40, 108, 0);
        BeyondEndChunkGenerator.CarveMask mask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(List.of(slab), true, BeyondEndChunkGenerator.CLOSE_RADIUS, true);
        Assertions.assertTrue(mask.distributed, "fromBoxes(..., distributed=true) must build a DISTRIBUTED mask");
        Assertions.assertNotNull(mask.dt3, "a distributed fromBoxes mask must build the TRUE-3D field");
        var masks = List.of(mask);

        int removed = 0, outwardViolations = 0;
        for (int x = -R3 - WARP - 3; x <= 40 + R3 + WARP + 3; x++) {
            for (int z = -R3 - WARP - 3; z <= R3 + WARP + 3; z++) {
                for (int y = 100 - R3 - WARP - 3; y <= 108 + R3 + WARP + 3; y++) {
                    if (!BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, x, y, z)) continue;
                    removed++;
                    int trueDist = mask.dt3At(x, y, z);   // exact un-warped Chebyshev distance to a real slab block
                    if (trueDist > OUTER_BOUND) outwardViolations++;
                }
            }
        }
        Assertions.assertTrue(removed > 0, "sample must contain carve-removed cells (else inward-only is vacuous)");
        Assertions.assertEquals(0, outwardViolations,
                outwardViolations + " carve-removed cells lie past DT3_REACH+DT3_WARP_AMP (the face noise pushed OUTWARD — a float risk)");

        // along the +X face.
        final int yMid = 104;
        int minOuter = Integer.MAX_VALUE, maxOuter = Integer.MIN_VALUE, faces = 0;
        for (int z = -6; z <= 6; z++) {
            int outer = Integer.MIN_VALUE;
            for (int x = 9; x <= 14; x++) {
                if (BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, x, yMid, z)) outer = x;
            }
            if (outer == Integer.MIN_VALUE) continue;
            faces++;
            if (outer < minOuter) minOuter = outer;
            if (outer > maxOuter) maxOuter = outer;
        }
        System.out.printf("[faceRough] removed=%d outwardViolations=%d faceCols=%d outer[min=%d max=%d span=%d]%n",
                removed, outwardViolations, faces, minOuter, maxOuter, faces > 0 ? (maxOuter - minOuter) : 0);
        Assertions.assertTrue(faces >= 2, "must find the +X face cut in at least 2 columns (else wander is vacuous)");
        Assertions.assertTrue(maxOuter - minOuter >= 2,
                "the outermost carve-removed dXZ must VARY by >=2 across z (a flat cut gives a constant outer dXZ — face noise inert); span="
                        + (maxOuter - minOuter));
    }

    /** Ken Perlin's smootherstep, mirrored for the test's inline OLD-ellipse reproduction (the kernel's is private). */
    private static double smoother(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** MEASUREMENT (not a gate; prints stats) — calibrates the organic distributed amplitude. */
    @Test
    void measureFarFieldDensityGap() {
        BeyondEndChunkGenerator.ColumnScratch s = BeyondEndChunkGenerator.getColumnScratch();
        int[] depths = {0, 1, 2, 3, 4, 5};
        java.util.List<java.util.List<Double>> gA = new ArrayList<>(); // 700>d>650 (threshold pinned 1.0)
        java.util.List<java.util.List<Double>> gB = new ArrayList<>(); // d>750 (threshold = baseThreshold)
        for (int i = 0; i < depths.length; i++) { gA.add(new ArrayList<>()); gB.add(new ArrayList<>()); }
        // coarse step — this is a one-time calibration, kept light so the heavy noise sweep doesn't stress the test JVM.
        for (int cx = -260; cx <= 260; cx += 16) {
            for (int cz = -260; cz <= 260; cz += 16) {
                int x = cx * 8, z = cz * 8;   // spread the samples out to ~2000 block radius
                double d = Math.sqrt((double) x * x + (double) z * z);
                boolean bandA = d > 650 && d < 700;
                boolean bandB = d > 800;
                if (!bandA && !bandB) continue;
                float dist = (float) d;
                BeyondEndChunkGenerator.initColumnScratch(x, z, dist, s);
                var bucket = bandA ? gA : gB;
                // find pancake tops (solid with air directly above) and sample g downward.
                boolean above = true;
                for (int y = MAX_Y - 33; y >= MIN_Y + 33; y--) {
                    boolean solid = BeyondEndChunkGenerator.isSolidTerrainScratch(y, s);
                    if (above && solid) {
                        for (int di = 0; di < depths.length; di++) {
                            int yy = y - depths[di];
                            if (yy < MIN_Y + 33) break;
                            double g = BeyondEndChunkGenerator.getTerrainDensityScratch(yy, s) - s.threshold;
                            bucket.get(di).add(g);
                        }
                    }
                    above = !solid;
                }
            }
        }
        System.out.println("=== Far-field density GAP g=(density-threshold) at depth below a pancake top ===");
        printGap("BAND A  650<d<700 (threshold=1.0)", gA, depths);
        printGap("BAND B  d>800 (threshold=baseThreshold)", gB, depths);
        Assertions.assertTrue(gB.get(0).size() > 100, "need enough Band-B surface samples: " + gB.get(0).size());
    }

    private static void printGap(String name, java.util.List<java.util.List<Double>> g, int[] depths) {
        System.out.println("-- " + name + " --");
        for (int di = 0; di < depths.length; di++) {
            java.util.List<Double> v = g.get(di);
            if (v.isEmpty()) { System.out.printf("  depth %d: n=0%n", depths[di]); continue; }
            double[] a = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            System.out.printf("  depth %d: n=%d  min=%.3f  p25=%.3f  p50=%.3f  p75=%.3f  p90=%.3f  p99=%.3f  max=%.3f%n",
                    depths[di], a.length, a[0], q(a, .25), q(a, .50), q(a, .75), q(a, .90), q(a, .99), a[a.length - 1]);
        }
    }

    private static double q(double[] sorted, double p) {
        int i = (int) Math.ceil(p * sorted.length) - 1;
        if (i < 0) i = 0; if (i >= sorted.length) i = sorted.length - 1;
        return sorted[i];
    }

    @Test
    void layerDistributedIsPerPlacementNotPerId() {
        ResourceLocation endCity = ResourceLocation.fromNamespaceAndPath("enderscape", "end_city");
        ResourceLocation other   = ResourceLocation.fromNamespaceAndPath("enderscape", "mirestone_ruins");
        long chunkA = ChunkPos.asLong(10, 20);
        long chunkB = ChunkPos.asLong(11, 20);
        BeyondForeignStructureProfiles.clearLayerDistributed();
        try {
            BeyondForeignStructureProfiles.markLayerDistributed(endCity, chunkA);
            Assertions.assertTrue(BeyondForeignStructureProfiles.isLayerDistributed(endCity, chunkA),
                    "the re-anchored instance (id, chunkA) must be distributed");
            Assertions.assertFalse(BeyondForeignStructureProfiles.isLayerDistributed(endCity, chunkB),
                    "a sibling of the same id in another chunk must NOT inherit distributed (per-placement, not per-id)");
            Assertions.assertFalse(BeyondForeignStructureProfiles.isLayerDistributed(other, chunkA),
                    "a different id in the same chunk must NOT be distributed");
            BeyondForeignStructureProfiles.clearLayerDistributed();
            Assertions.assertFalse(BeyondForeignStructureProfiles.isLayerDistributed(endCity, chunkA),
                    "clearLayerDistributed() (server stop) must drop the decision (no cross-world leak)");
        } finally {
            BeyondForeignStructureProfiles.clearLayerDistributed();
        }
    }

    @Test
    void distributedFootingNotGlobalGate() {
        int DEEP = BeyondEndChunkGenerator.FOUNDATION_DEPTH;   // the production footing reach (= mask.foundationDepth = 8)
        int footingColLo = 150;
        int seat = BeyondEndChunkGenerator.the_beyond$foundationNatTop(footingColLo, DEEP, yy -> yy <= footingColLo - 2);
        Assertions.assertEquals(footingColLo - 2, seat, "a footing column seats on the natural island within reach (1-block lip)");
        int cantColLo = 200;
        int noSeat = BeyondEndChunkGenerator.the_beyond$foundationNatTop(cantColLo, DEEP, yy -> yy <= 120);
        Assertions.assertEquals(Integer.MIN_VALUE, noSeat,
                "a cantilevered/hanging column with no ground within reach gets NO foundation (no flat slab under the whole footprint)");
        // contiguity: a void gap wider than FOUNDATION_MAX_LIP must NOT seat (would weld onto a disconnected sliver).
        int dipColLo = 150;
        Assertions.assertEquals(Integer.MIN_VALUE,
                BeyondEndChunkGenerator.the_beyond$foundationNatTop(dipColLo, DEEP, yy -> yy <= dipColLo - 6),
                "a 6-block VOID gap to a lower island must NOT glue a fill (round-7 no-float fix)");
        int lipNat = dipColLo - 3;
        Assertions.assertEquals(lipNat,
                BeyondEndChunkGenerator.the_beyond$foundationNatTop(dipColLo, DEEP, yy -> yy <= lipNat),
                "a ≤MAX_LIP(3) surface lip to the SAME island still seats (legitimate flank, no leap)");
        System.out.printf("[footing] DEEP=%d MAX_LIP=%d footingSeat=%d cantileverSeat=%d lipSeat=%d%n",
                DEEP, BeyondEndChunkGenerator.FOUNDATION_MAX_LIP, seat, noSeat, lipNat);
    }

    @Test
    void fringeBaseFloatCarvedSupportKeptGate() {
        final int gLo = 100, hi = 170;
        final int BAND = BeyondEndChunkGenerator.DIST_SUPPORT_BAND;
        final int loHigh = gLo + BAND + 6;          // a column whose OWN base is well above the global base
        final int[] HI = {hi}, GLO = {gLo};
        final boolean[] YES = {true}, NO = {false};
        final int[] D3_NEAR = {2};                  // warped 3D distance inside the skirt (<= DT3_REACH)
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$carvePenalty(
                new int[]{0}, new int[]{loHigh}, HI, GLO, YES, NO, YES, 1, loHigh - 1, 0.0, D3_NEAR), 1e-9,
                "TRUE support column (dXZ==0) below its base must be kept");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$carvePenalty(
                new int[]{2}, new int[]{loHigh}, HI, GLO, YES, NO, YES, 1, gLo + BAND + 2, 0.0, D3_NEAR) > 0.0,
                "isolated lateral fringe (dXZ>0) above the base band must be CARVED (the floating-block fix)");
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$carvePenalty(
                new int[]{2}, new int[]{gLo}, HI, GLO, YES, NO, YES, 1, gLo, 0.0, D3_NEAR), 1e-9,
                "genuine base contact (dXZ>0, y<=gLo) stays kept via the ramp (no moat)");
        Assertions.assertEquals(Double.POSITIVE_INFINITY, BeyondEndChunkGenerator.the_beyond$carvePenalty(
                new int[]{0}, new int[]{gLo}, HI, GLO, YES, NO, YES, 1, 130, 0.0, new int[]{0}),
                "no-burial: occupied-span cell still clears unconditionally");
    }

    @Test
    void rampRockBelowGate() {
        final int gLo = 100, lo = 100, hi = 170;
        final int[] DXZ = {2}, LO = {lo}, HI = {hi}, GLO = {gLo}, D3 = {2};   // fringe (dXZ=2) inside the skirt (dd=2)
        final boolean[] YES = {true}, NO = {false};
        final int yBand = gLo + 2;                                            // inside the support band [gLo, gLo+BAND)
        double pVoid = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ, LO, HI, GLO, YES, NO, YES, 1, yBand, 0.0, D3, false);
        double pRock = BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ, LO, HI, GLO, YES, NO, YES, 1, yBand, 0.0, D3, true);
        Assertions.assertTrue(pVoid > pRock,
                "fringe over the VOID (rockBelow=false) is carved MORE than over island (the floating-bump fix)");
        Assertions.assertTrue(pVoid > 0.0, "the void fringe in the band is actually carved (full skirt, no ramp)");
        // at aboveBase==0 (the base level itself).
        Assertions.assertEquals(0.0, BeyondEndChunkGenerator.the_beyond$carvePenalty(
                DXZ, LO, HI, GLO, YES, NO, YES, 1, gLo, 0.0, D3, true), 1e-9,
                "base-contact fringe WITH island below stays kept (ramp, no moat)");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$carvePenalty(
                DXZ, LO, HI, GLO, YES, NO, YES, 1, gLo, 0.0, D3, false) > 0.0,
                "base-level fringe over the VOID is carved (no floating bump at the global base = the ship level)");
        Assertions.assertEquals(
                BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ, LO, HI, GLO, YES, NO, YES, 1, yBand, 0.0, D3, true),
                BeyondEndChunkGenerator.the_beyond$carvePenalty(DXZ, LO, HI, GLO, YES, NO, YES, 1, yBand, 0.0, D3),
                1e-9, "11-arg overload = ramp-on (rockBelow=true), byte-identical for the existing gates");
    }

    @Test
    void autoSeatedProjectedGetsSkirtFlag() {
        ResourceLocation id = ResourceLocation.parse("test:room_skirt_probe");
        Assertions.assertFalse(BeyondForeignStructureProfiles.isAutoSeatedProjected(id),
                "an unmarked structure is not auto-projected (no skirt forced)");
        BeyondForeignStructureProfiles.markAutoSeatedProjected(id);
        Assertions.assertTrue(BeyondForeignStructureProfiles.isAutoSeatedProjected(id),
                "markAutoSeatedProjected → isAutoSeatedProjected → the carve builds it DISTRIBUTED (dt3 skirt clears its rooms)");
        Assertions.assertFalse(BeyondForeignStructureProfiles.isAutoSeatedProjected(null),
                "null id is never auto-projected");
    }

    /** The old ±2 cross accepted a tiny pancake, letting the base overhang into the void; must now be REJECTED. */
    @Test
    void footprintSupportGate() {
        final int R = PancakeScan.FOOTPRINT_SUPPORT_RADIUS, S = PancakeScan.FOOTPRINT_SUPPORT_STRIDE;
        final double F = PancakeScan.FOOTPRINT_SUPPORT_FRACTION;
        Assertions.assertFalse(
                PancakeScan.hasFootprintSupport(0, 0, 64, R, S, F, (x, y, z) -> x * x + z * z <= 4 * 4),
                "a tiny ~r4 pancake must NOT host the base (must fall back to topmost — no floating)");
        Assertions.assertTrue(
                PancakeScan.hasFootprintSupport(0, 0, 64, R, S, F, (x, y, z) -> x * x + z * z <= (2 * R) * (2 * R)),
                "a real island spanning the base must host the lower-pancake re-anchor");
        Assertions.assertFalse(
                PancakeScan.hasFootprintSupport(0, 0, 64, R, S, F, (x, y, z) -> false),
                "pure void must never host a re-anchor");
        // world-offset invariant: island at the base Y hosts; at another Y doesn't.
        Assertions.assertTrue(
                PancakeScan.hasFootprintSupport(12345, -67890, 71, R, S, F, (x, y, z) -> y == 71),
                "an island present at the candidate base Y hosts regardless of world offset");
        Assertions.assertFalse(
                PancakeScan.hasFootprintSupport(12345, -67890, 71, R, S, F, (x, y, z) -> y == 50),
                "island only at a DIFFERENT Y (the base level is void) must NOT host");
        System.out.println("[footprintSupport] R=" + R + " stride=" + S + " fraction=" + F + " — tiny→reject, island→host");
    }

    @Test
    void weightedLayerPickGate() {
        final int W = PancakeScan.LOWER_LAYER_WEIGHT;
        Assertions.assertTrue(W > 1, "de-weight requires LOWER_LAYER_WEIGHT > 1 (equal weights = old uniform pick)");
        for (long seed : new long[]{0, 1, 7, -3, Long.MAX_VALUE, Long.MIN_VALUE}) {
            Assertions.assertEquals(0, PancakeScan.pickWeightedIndex(new int[]{1}, 1, seed),
                    "a lone candidate must always win regardless of seed");
        }
        // top + one lower, W=3: total 4 → seed 0 → top; seeds 1..3 → lower.
        int[] topAndLower = {1, W};
        Assertions.assertEquals(0, PancakeScan.pickWeightedIndex(topAndLower, 2, 0), "seed 0 lands in the top's weight-1 slot");
        for (long seed = 1; seed <= W; seed++) {
            Assertions.assertEquals(1, PancakeScan.pickWeightedIndex(topAndLower, 2, seed),
                    "seeds 1.." + W + " land in the lower layer's slots (the flipped preference)");
        }
        Assertions.assertEquals(0, PancakeScan.pickWeightedIndex(topAndLower, 2, 1 + W), "wraps back to the top at total weight");
        Assertions.assertEquals(1, PancakeScan.pickWeightedIndex(topAndLower, 2, -1),
                "floorMod(-1, 4)=3 → lower layer (negative seeds stay in the weighted walk)");
        int[] uniform = {1, 1, 1};
        for (int k = 0; k < 6; k++) {
            Assertions.assertEquals(k % 3, PancakeScan.pickWeightedIndex(uniform, 3, k),
                    "equal weights must reproduce the plain floorMod pick");
        }
        // top + two lowers (one stacked column with 3 layers): lower layers carry 2W of 1+2W total.
        int[] threeLayers = {1, W, W};
        Assertions.assertEquals(0, PancakeScan.pickWeightedIndex(threeLayers, 3, 0), "top keeps exactly its weight-1 slot");
        Assertions.assertEquals(1, PancakeScan.pickWeightedIndex(threeLayers, 3, 1), "first lower layer starts at slot 1");
        Assertions.assertEquals(2, PancakeScan.pickWeightedIndex(threeLayers, 3, 1 + W), "second lower layer after the first's W slots");
        System.out.println("[weightedLayerPick] LOWER_LAYER_WEIGHT=" + W + " — lone top wins, stacked column flips " + W + ":1 to lower layers");
    }

}
