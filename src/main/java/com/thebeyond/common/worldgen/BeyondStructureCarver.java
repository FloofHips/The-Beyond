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
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator.CarveMask;

public final class BeyondStructureCarver {
    private final BeyondEndChunkGenerator gen;
    BeyondStructureCarver(BeyondEndChunkGenerator gen) { this.gen = gen; }

    public static final int CARVE_ERODE_REACH = 5;
    public static final int CARVE_CEIL_REACH = 8;
    static final int FLOAT_VMARGIN = 12;
    static final int FLOAT_HMARGIN = 8;
    static final int WARP_MAX = 3;
    private static final double WARP_FREQ = 0.09, WARP_FREQ_Y = 0.12, WARP_FREQ_HI = 0.22;
    static final int GUARD_STUB_DEPTH = 96;
    static final int GUARD_GROWTH_UP = 34;
    static final int CARVE_SCAN_REACH = FLOAT_HMARGIN + WARP_MAX + CARVE_ERODE_REACH;
    static final int CLOSE_RADIUS = 6;
    static final int FOUNDATION_DEPTH = 8;
    static final int FOUNDATION_MAX_LIP = 3;
    static final int FOUNDATION_MIN_RUN = 3;
    static final double CARVE_AMP = 3.0;
    static final int CARVE_LAT_REACH = 11;
    // Every finite body has a corner cell with at most 3 solid neighbours, so seeding there skips island interiors.
    static final int DEBRIS_MAX_CELLS = 12;
    static final int DEBRIS_SEED_DEGREE = 3;
    static final int COURTYARD_GUARD_DOWN = 4;
    static final int COURTYARD_GUARD_UP = 6;
    static final int GRAIN_WART_ROCK = 2;
    // 7 is unreachable on a 6-neighbourhood, so the pit arm is off (every fill rule tried planted cover at depth).
    static final int GRAIN_PIT_ROCK = 7;
    static final int GRAIN_BUILD_MARGIN = 12;
    // Two passes: filling the pit a removed wart leaves needs the state after the removal.
    static final int GRAIN_PASSES = 2;
    private static final int[][] DEBRIS_DIRS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final int CARVE_VERT_REACH = FLOAT_VMARGIN + CARVE_ERODE_REACH;   // 17
    static final int CARVE_DIST_LAT_REACH = 6;
    static final int CARVE_DIST_VERT_REACH = 8;
    /** Fades the skirt in above the mask floor. At 6 the cut starts flush and reads as a straight base. */
    static final int DIST_SUPPORT_BAND = 3;
    static final int FOOTING_TOL = 4;
    static final double CARVE_DIST_AMP = 0.25;
    static final double CARVE_DIST_WARP_GAIN = 2.0;
    static final double CARVE_WARP_AMT = 1.0;
    private static final double CARVE_WARP_FREQ = 0.08, CARVE_WARP_FREQ_HI = 0.18;
    // A floater's open cut turns the short octave into dither, so it warps long. Others keep the tight wave.
    private static final double CARVE_WARP_FREQ_WIDE = 0.045, CARVE_WARP_FREQ_HI_WIDE = 0.09;

    /** HARD INVARIANT: {@code DT3_REACH + DT3_WARP_AMP (=8) <= FLOAT_HMARGIN (=8)}, so the warped clear stays ⊆ guard. */
    static final int DT3_REACH = 6;
    static final double DT3_WARP_AMP = 2.0;
    static final int DT3_CAP = DT3_REACH + (int) Math.ceil(DT3_WARP_AMP) + 1;   // = 9

    /** INVARIANT: {@code 0 <= FACE_ROUGH < DT3_REACH}. */
    static final double FACE_ROUGH_FREQ = 0.045;
    static final int FACE_ROUGH = 3;

    /** Lateral taper of the ceiling clear, meeting the skirt on a slope. Past CARVE_ERODE_REACH it would leave the guard band. */
    static final int CEIL_LAT = 3;
    static final int CEIL_SLOPE = 2;

    static final java.util.concurrent.atomic.AtomicLongArray CEIL_APRON_HITS =
            new java.util.concurrent.atomic.AtomicLongArray(CEIL_LAT + 1);
    private static volatile boolean the_beyond$apronLogged = false;

    static void the_beyond$logCeilApron() {
        if (the_beyond$apronLogged || CEIL_APRON_HITS.get(0) < APRON_LOG_MIN) return;
        if (!BeyondGenDiagnostics.loggedMaskKeys.add("ceil-apron")) return;
        the_beyond$apronLogged = true;
        StringBuilder run = new StringBuilder();
        for (int t = 0; t <= CEIL_LAT; t++) run.append(t == 0 ? "" : ",").append(CEIL_APRON_HITS.get(t));
        com.thebeyond.TheBeyond.LOGGER.info(
                "[Beyond] ceil-apron lat={} slope={} reach={} run=[{}] (cumulative, all seated masks)",
                CEIL_LAT, CEIL_SLOPE, CARVE_CEIL_REACH, run);
    }

    private static final int APRON_LOG_MIN = 500;

    static void the_beyond$resetApronCounters() {
        the_beyond$apronLogged = false;
        for (int t = 0; t <= CEIL_LAT; t++) CEIL_APRON_HITS.set(t, 0L);
    }

    /** Span clear only within this dt3 distance of a block, so far-apart parts keep their gap, but a wide room keeps rock. */
    static final int SPAN_DIST_CAP = 8;

    static int the_beyond$bandWarp(int x, int y, int z) {
        if (BeyondEndChunkGenerator.simplexNoise == null) return 0;
        double n = 0.55 * BeyondEndChunkGenerator.simplexNoise.getValue(x * WARP_FREQ + 8192.0, y * WARP_FREQ_Y, z * WARP_FREQ - 8192.0)
                 + 0.45 * BeyondEndChunkGenerator.simplexNoise.getValue(x * WARP_FREQ_HI - 4096.0, y * WARP_FREQ_HI, z * WARP_FREQ_HI + 4096.0);
        int w = (int) Math.round(WARP_MAX * 0.5 * (1.0 + n));   // n∈[-1,1] (0.55+0.45 weights) → w∈[0,WARP_MAX]
        return w < 0 ? 0 : (w > WARP_MAX ? WARP_MAX : w);
    }

    private static double the_beyond$smootherstep(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, true);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, 0.0);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, null);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, null);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, null);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, baseFloor, null);
    }
    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, baseFloor, corr, SPAN_GROW_OFF);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, baseFloor, corr, grow, false);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow, boolean insideBox) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, baseFloor, corr, grow, insideBox, null);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow, boolean insideBox, boolean[] fly) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough, base, buriedAbove, baseFloor, corr, grow, insideBox, fly, null);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow, boolean insideBox, boolean[] fly, int[] d2) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough,
                base, buriedAbove, baseFloor, corr, grow, insideBox, fly, d2, null);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow, boolean insideBox, boolean[] fly, int[] d2, boolean[] hug) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough,
                base, buriedAbove, baseFloor, corr, grow, insideBox, fly, d2, hug, null);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow, boolean insideBox, boolean[] fly, int[] d2, boolean[] hug, int[] capTop) {
        return the_beyond$carvePenalty(dxz, lo, hi, gLo, seat, filled, dist, nBits, y, warp, d3, rockBelow, rough,
                base, buriedAbove, baseFloor, corr, grow, insideBox, fly, d2, hug, capTop, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    static double the_beyond$carvePenalty(int[] dxz, int[] lo, int[] hi, int[] gLo, boolean[] seat, boolean[] filled, boolean[] dist, int nBits, int y, double warp, int[] d3, boolean rockBelow, double rough, boolean[] base, boolean[] buriedAbove, int[] baseFloor, boolean[] corr, int grow, boolean insideBox, boolean[] fly, int[] d2, boolean[] hug, int[] capTop, int runBot, int runTop) {
        double penalty = 0.0;
        final double botRim = runBot == Integer.MIN_VALUE ? 0.0 : the_beyond$rimReach(y - runBot);
        final double topRim = runTop == Integer.MAX_VALUE ? 0.0 : the_beyond$rimReach(runTop - y);
        boolean rimHit = false;
        final int growLat = grow < 0 ? 0 : (grow > SPAN_GROW_MAX ? SPAN_GROW_MAX : grow);
        final int ceilLat = grow < 0 ? CEIL_LAT : (grow > CEIL_LAT ? CEIL_LAT : grow);
        int growNear = Integer.MAX_VALUE;

        if (buriedAbove != null && rockBelow) {
            boolean occupied = false, keepNear = false;
            for (int k = 0; k < nBits; k++) {
                int vo = y < lo[k] ? lo[k] - y : (y > hi[k] ? y - hi[k] : 0);
                if (dxz[k] == 0 && vo == 0) { occupied = true; break; }
                if (baseFloor == null || (baseFloor[k] != Integer.MIN_VALUE && hi[k] <= baseFloor[k])) {
                    // The cap is about a roof the island grew over, so it still asks for rock overhead.
                    boolean cap   = (buriedAbove[k] && (base == null || !base[k]) && dxz[k] <= CEIL_LAT && y > hi[k]
                            && (y - hi[k]) <= CARVE_CEIL_REACH - CEIL_SLOPE * dxz[k]);
                    // Not the flank: a bunker whose roof is the ground has nothing above, and asking left a skirt-wide gap.
                    boolean shell = (dxz[k] <= SUBSURF_KEEP_REACH && y <= hi[k]);
                    if (cap || shell) keepNear = true;
                }
            }
            if (!occupied && keepNear) {
                ISLAND_KEEP_HITS.incrementAndGet();
                return 0.0;
            }
        }
        // One true-3D field serves the whole mask, so any held flank keeps every skirt off the cell.
        boolean flankHeld = false;
        for (int k = 0; k < nBits && !flankHeld; k++) flankHeld = the_beyond$subFlank(k, dxz, lo, hi, dist, baseFloor, y, insideBox);
        for (int k = 0; k < nBits; k++) {
            int vertOut = y < lo[k] ? lo[k] - y : (y > hi[k] ? y - hi[k] : 0);
            boolean floorPlane = baseFloor != null && baseFloor[k] != Integer.MIN_VALUE && y == baseFloor[k];
            boolean subFlank = the_beyond$subFlank(k, dxz, lo, hi, dist, baseFloor, y, insideBox);
            // Fails open: no dt3 for this bit means today's unconditional span carve is kept.
            boolean flyBit = fly != null && fly[k];
            // The island is meant to close on this hull, so nothing outside its own volume is offered a clear.
            boolean hugBit = hug != null && hug[k];
            if (!flyBit && vertOut == 0 && (!dist[k] || d3[k] > DT3_CAP || d3[k] <= SPAN_DIST_CAP)) {
                if (dxz[k] == 0) return Double.POSITIVE_INFINITY;
                // The d3 test keeps separated tower and ship gaps apart, and the start's floor plane waits for its tiles.
                if (!hugBit && !floorPlane && !subFlank && dxz[k] < growNear) growNear = dxz[k];
                // Round the underside where a piece hangs below, the top only where it also rises out (no courtyard moat).
                if (!rimHit && dist[k] && !hugBit && !floorPlane && !subFlank && !flankHeld && d2 != null && lo[k] < runBot) {
                    double rim = hi[k] > runTop ? Math.max(botRim, topRim) : botRim;
                    if (rim > 0.0 && d2[k] <= (growLat + rim) * (growLat + rim)) rimHit = true;
                }
            }
            // A run column skips the ceiling apron (it would eat island above the gap) but keeps the dt3 skirt.
            boolean runBit = corr != null && corr[k];
            // A lower neighbour makes the base's floor plane read as its roof, so that plane is left to the ground.
            boolean baseDeck = (floorPlane && dxz[k] > 0) || subFlank;
            boolean capped = capTop != null && capTop[k] != Integer.MIN_VALUE && y <= capTop[k];
            if (!hugBit && !runBit && !baseDeck && !capped && (dxz[k] == 0 || dxz[k] <= ceilLat) && seat[k] && !filled[k] && y > hi[k]) {
                int rc = (int) Math.round(FACE_ROUGH * 0.5 * (1.0 + rough));
                rc = rc < 0 ? 0 : (rc > FACE_ROUGH ? FACE_ROUGH : rc);
                int ceilEff = dist[k] ? (CARVE_CEIL_REACH - rc) : CARVE_CEIL_REACH;
                // Grow off the own column, capped by the slope, so the rings are not a rectangle and the lid stays monotone.
                if (dxz[k] > 0) ceilEff += growLat > CEIL_SLOPE ? CEIL_SLOPE : growLat;
                if ((y - hi[k]) <= ceilEff - CEIL_SLOPE * dxz[k]) {
                    CEIL_APRON_HITS.incrementAndGet(dxz[k]);
                    return Double.POSITIVE_INFINITY;
                }
            }
            if (filled[k]) continue;
            if (hugBit) continue;
            if (seat[k] && !dist[k]) continue;
            if (seat[k] && dist[k] && dxz[k] == 0 && y <= lo[k]) continue;
            // Off the column the skirt spares the floor plane, or courtyards would end a course below their tiles.
            if (subFlank || flankHeld || (floorPlane && dxz[k] > 0)) continue;
            double p;
            if (dist[k]) {
                int dd = d3[k];
                if (base != null && base[k] && dxz[k] <= BEARD_LAT_REACH && y < lo[k] && rockBelow) continue;
                if (dd == DT3_CAP + 1) {
                    if (!BeyondGenDiagnostics.loggedDt3Fallback) {
                        BeyondGenDiagnostics.loggedDt3Fallback = true;
                        com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] DT3 absent -> 2.5D fallback");
                    }
                    double nl = dxz[k] / (double) CARVE_DIST_LAT_REACH;
                    double nv = vertOut / (double) CARVE_DIST_VERT_REACH;
                    double nd2 = (nl * nl + nv * nv) - warp * CARVE_DIST_WARP_GAIN;
                    if (nd2 >= 1.0) continue;
                    if (nd2 < 0.0) nd2 = 0.0;
                    p = CARVE_DIST_AMP * (1.0 - the_beyond$smootherstep(Math.sqrt(nd2)));
                } else {
                    if (dd == 0) {
                        if (y > lo[k]) return Double.POSITIVE_INFINITY;
                        else continue;
                    }
                    // The zero clear widens by the same round because it sets the wall where the warp passes the collar.
                    if (!rimHit && lo[k] < runBot && y > lo[k]
                            && dd <= Math.round(hi[k] > runTop ? Math.max(botRim, topRim) : botRim)) rimHit = true;
                    int r = (int) Math.round(FACE_ROUGH * 0.5 * (1.0 + rough));
                    r = r < 0 ? 0 : (r > FACE_ROUGH ? FACE_ROUGH : r);
                    int reachEff = DT3_REACH - r;
                    if (dd > reachEff) continue;
                    // Only DT3_REACH whole-block rungs, so the face dithers between two with this cell's warp.
                    double sub = 0.5 + 0.5 * warp;
                    sub = sub < 0.0 ? 0.0 : (sub > 1.0 ? 1.0 : sub);
                    p = CARVE_DIST_AMP * (1.0 - the_beyond$smootherstep((dd - 0.5 + sub) / reachEff));
                }
                if (dxz[k] > 0 && rockBelow) {
                    int aboveBase = y - gLo[k];
                    if (aboveBase <= 0) p = 0.0;
                    else if (aboveBase < DIST_SUPPORT_BAND) p *= the_beyond$smootherstep((double) aboveBase / DIST_SUPPORT_BAND);
                }
            } else {
                // An island whose run tops out under the hull is not in the floater's way, so the bubble leaves it whole.
                if (y < lo[k] && runTop != Integer.MAX_VALUE && runTop < lo[k]) continue;
                // True distance keeps this widest clear round like an island edge, Chebyshev would make it square.
                double lat = d2 != null ? Math.sqrt(d2[k]) : dxz[k];
                double nl = lat / (double) CARVE_LAT_REACH;
                double nv = vertOut / (double) CARVE_VERT_REACH;
                double nd2 = (nl * nl + nv * nv) - warp;
                if (nd2 >= 1.0) continue;
                if (nd2 < 0.0) nd2 = 0.0;
                p = CARVE_AMP * (1.0 - the_beyond$smootherstep(nd2));
            }
            if (p > penalty) penalty = p;
        }
        if (growNear <= growLat) {
            SPAN_GROW_HITS.incrementAndGet(growNear);
            return Double.POSITIVE_INFINITY;
        }
        if (rimHit) {
            RIM_HITS.incrementAndGet();
            return Math.max(penalty, RIM_PENALTY);
        }
        return penalty;
    }

    /** Flank of a piece under the base floor, which the island closes on, so lateral clears stand off it, over its roof too. */
    static boolean the_beyond$subFlank(int k, int[] dxz, int[] lo, int[] hi, boolean[] dist, int[] baseFloor, int y, boolean insideBox) {
        if (baseFloor == null || !dist[k] || dxz[k] > ENVELOPE_LAT
                || baseFloor[k] == Integer.MIN_VALUE || lo[k] >= baseFloor[k]) return false;
        int envV = y > hi[k] ? y - hi[k] : (y < lo[k] ? lo[k] - y : 0);
        return !(dxz[k] == 0 && envV == 0) && !insideBox
                && (y > hi[k] || y < baseFloor[k])
                && envV <= (y > hi[k] ? ENVELOPE_UP : ENVELOPE_DOWN);
    }

    /** Feeds the kernel the undilated span clear and the full-width apron: the shape every constant-fed gate pins. */
    static final int SPAN_GROW_OFF = -1;

    /** Per-column dilation of the span clear and apron, breaking the union of rectangles. CEIL_LAT keeps it in the guard band. */
    static final int SPAN_GROW_MAX = CEIL_LAT;
    /** A long octave near a piece's edge in size, and a short one that varies within one perimeter instead of moving it whole. */
    private static final double SPAN_GROW_FREQ = 0.09, SPAN_GROW_FREQ_HI = BeyondStructureCarver.PEDESTAL_RING_FREQ;
    /** Slower than the lateral octaves: the wall is meant to lean and swell over a dozen courses, not to serrate. */
    private static final double SPAN_GROW_VFREQ = 0.05, SPAN_GROW_VFREQ_HI = 0.15;
    /** Simplex rarely leaves its middle, so without this gain every side of a footprint comes out the same width. */
    private static final double SPAN_GROW_GAIN = 1.9;

    static final java.util.concurrent.atomic.AtomicLongArray SPAN_GROW_HITS =
            new java.util.concurrent.atomic.AtomicLongArray(SPAN_GROW_MAX + 1);
    static final java.util.concurrent.atomic.AtomicLong ISLAND_KEEP_HITS =
            new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean the_beyond$branchLogged = false;

    static void the_beyond$resetCarveCounters() {
        the_beyond$branchLogged = false;
        the_beyond$rimLogged = false;
        for (int t = 0; t <= SPAN_GROW_MAX; t++) SPAN_GROW_HITS.set(t, 0L);
        ISLAND_KEEP_HITS.set(0L);
        RIM_HITS.set(0L);
    }

    static final int ROUND_R = 5;
    /** How far from a cell the ends of its natural island are looked for. A thicker island keeps square edges. */
    static final int RIM_SCAN = 32;
    /** Finite, so a beard that lifts the cell still keeps it, as it does against the skirt. */
    static final double RIM_PENALTY = 1.0e6;
    static final java.util.concurrent.atomic.AtomicLong RIM_HITS = new java.util.concurrent.atomic.AtomicLong();
    private static volatile boolean the_beyond$rimLogged = false;

    static void the_beyond$logRim() {
        if (the_beyond$rimLogged || RIM_HITS.get() < APRON_LOG_MIN / 2) return;
        if (!BeyondGenDiagnostics.loggedMaskKeys.add("rim-round")) return;
        the_beyond$rimLogged = true;
        com.thebeyond.TheBeyond.LOGGER.info("[Beyond] rim-round R={} cells={} (cumulative: island edges rounded where a structure passes through)",
                ROUND_R, RIM_HITS.get());
    }

    static double the_beyond$rimReach(int depth) {
        double c = ROUND_R - depth;
        return c > 0.0 ? ROUND_R - Math.sqrt(ROUND_R * ROUND_R - c * c) : 0.0;
    }

    /** The natural island run holding the cell as {bottom, top}, each end MIN/MAX when farther than RIM_SCAN. */
    static int[] the_beyond$natRun(int y, BeyondEndChunkGenerator.ColumnScratch s) {
        int[] r = {Integer.MIN_VALUE, Integer.MAX_VALUE};
        if (!BeyondEndChunkGenerator.isSolidTerrainScratch(y, s)) return r;
        int t = y, b = y;
        while (t - y < RIM_SCAN && BeyondEndChunkGenerator.isSolidTerrainScratch(t + 1, s)) t++;
        while (y - b < RIM_SCAN && BeyondEndChunkGenerator.isSolidTerrainScratch(b - 1, s)) b--;
        if (t - y < RIM_SCAN) r[1] = t;
        if (y - b < RIM_SCAN) r[0] = b;
        return r;
    }


    static void the_beyond$logCarveBranches() {
        if (the_beyond$branchLogged) return;
        long total = 0;
        for (int t = 1; t <= SPAN_GROW_MAX; t++) total += SPAN_GROW_HITS.get(t);
        if (total < APRON_LOG_MIN || !BeyondGenDiagnostics.loggedMaskKeys.add("carve-branches")) return;
        the_beyond$branchLogged = true;
        StringBuilder ring = new StringBuilder();
        for (int t = 1; t <= SPAN_GROW_MAX; t++) ring.append(t == 1 ? "" : ",").append(SPAN_GROW_HITS.get(t));
        com.thebeyond.TheBeyond.LOGGER.info(
                "[Beyond] carve-branches: span-grow by ring=[{}] total={} island-cap-keep={} (cumulative, all"
                + " masks; a ring at zero means the dilation never reached it)", ring, total, ISLAND_KEEP_HITS.get());
    }

    /** Extra ring around the start's box so the span grow is not clipped, at the cost of hole closing sealing channel mouths. */
    static final int ENVELOPE_PAD = SPAN_GROW_MAX;

    public static final boolean TERRAIN_MATCHING_IS_RIGID = false;

    static int the_beyond$spanGrow(int x, int z) {
        return the_beyond$spanGrow(x, 0, z);
    }

    /** Varies with height too, or a cut through an island keeps the sheer wall the occupancy stamps into the rock. */
    static int the_beyond$spanGrow(int x, int y, int z) {
        return the_beyond$spanGrowAt(BeyondEndChunkGenerator.simplexNoise, x, y, z);
    }

    static int the_beyond$spanGrowAt(com.thebeyond.util.HashSimplexNoise n, int x, int z) {
        return the_beyond$spanGrowAt(n, x, 0, z);
    }

    static int the_beyond$spanGrowAt(com.thebeyond.util.HashSimplexNoise n, int x, int y, int z) {
        if (n == null) return SPAN_GROW_OFF;
        double lo = n.getValue(x * SPAN_GROW_FREQ + 2237.0, y * SPAN_GROW_VFREQ, z * SPAN_GROW_FREQ - 8461.0);
        double hi = n.getValue(x * SPAN_GROW_FREQ_HI - 5119.0, y * SPAN_GROW_VFREQ_HI, z * SPAN_GROW_FREQ_HI + 3733.0);
        double v = SPAN_GROW_GAIN * (0.55 * lo + 0.45 * hi);
        if (v < -1.0) v = -1.0; else if (v > 1.0) v = 1.0;
        // Must reach 0: with a floor of 1 every thin face takes the same collar and the outline is a bigger rectangle.
        int g = (int) Math.round(SPAN_GROW_MAX * 0.5 * (1.0 + v));
        return g < 0 ? 0 : (g > SPAN_GROW_MAX ? SPAN_GROW_MAX : g);
    }

    static void the_beyond$logSpanGrow(String id, int oX, int oZ, int w, int d) {
        if (BeyondEndChunkGenerator.simplexNoise == null) return;
        if (w < 2 || d < 2) return;
        int n = 0, min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, changes = 0, prev = -1;
        long sum = 0, sumSq = 0;
        for (int step = 0, steps = 2 * (w + d) - 4; step < steps; step++) {
            int i, j;
            if (step < w) { i = step; j = 0; }
            else if (step < w + d - 1) { i = w - 1; j = step - w + 1; }
            else if (step < 2 * w + d - 2) { i = 2 * w + d - 3 - step; j = d - 1; }
            else { i = 0; j = 2 * (w + d) - 4 - step; }
            int g = the_beyond$spanGrow(oX + i, oZ + j);
            if (prev >= 0 && g != prev) changes++;
            prev = g;
            n++; sum += g; sumSq += (long) g * g;
            if (g < min) min = g;
            if (g > max) max = g;
        }
        if (n == 0) return;
        double mean = (double) sum / n;
        double sd = Math.sqrt(Math.max(0.0, (double) sumSq / n - mean * mean));
        com.thebeyond.TheBeyond.LOGGER.info(
                "[Beyond] span-grow {}: perimeter n={} min={} max={} mean={} sd={} changes/100={} (max={}; the"
                + " gate scores the change count, and under 20 per 100 the field correlated across the footprint"
                + " and the cut still reads square)",
                id, n, min, max, String.format(java.util.Locale.ROOT, "%.2f", mean),
                String.format(java.util.Locale.ROOT, "%.2f", sd),
                String.format(java.util.Locale.ROOT, "%.1f", 100.0 * changes / n), SPAN_GROW_MAX);
    }

    static double the_beyond$carveWarp(int x, int y, int z) {
        return the_beyond$carveWarp(x, y, z, false);
    }

    static double the_beyond$carveWarp(int x, int y, int z, boolean wide) {
        if (BeyondEndChunkGenerator.simplexNoise == null) return 0.0;
        double f = wide ? CARVE_WARP_FREQ_WIDE : CARVE_WARP_FREQ;
        double fh = wide ? CARVE_WARP_FREQ_HI_WIDE : CARVE_WARP_FREQ_HI;
        return 0.6 * BeyondEndChunkGenerator.simplexNoise.getValue(x * f + 1024.0, y * f - 2048.0, z * f + 512.0)
             + 0.4 * BeyondEndChunkGenerator.simplexNoise.getValue(x * fh - 4096.0, y * fh + 1536.0, z * fh - 768.0);
    }

    static double the_beyond$faceRough(int x, int y, int z) {
        if (BeyondEndChunkGenerator.simplexNoise == null) return 0.0;
        return BeyondEndChunkGenerator.simplexNoise.getValue(x * FACE_ROUGH_FREQ + 5300.0,
                                     y * FACE_ROUGH_FREQ - 2700.0,
                                     z * FACE_ROUGH_FREQ + 9100.0);
    }

    static int the_beyond$carveDistAt(int dXZ, int colLo, int colHi, boolean seated, boolean filled, boolean distributed, int gLo, int hWarp, boolean guard, int y) {
        boolean noMargin = filled || (seated && !distributed);
        int hMargin = noMargin ? 0 : FLOAT_HMARGIN + hWarp;
        if (dXZ > hMargin + CARVE_ERODE_REACH) return CARVE_ERODE_REACH + 1;
        int loB = noMargin ? colLo : (guard ? Math.min(colLo - FLOAT_VMARGIN, gLo - GUARD_STUB_DEPTH) : colLo - FLOAT_VMARGIN);
        int ceilExt = (seated && !filled) ? CARVE_CEIL_REACH : 0;
        int hiB = noMargin ? colHi + ceilExt : (guard ? colHi + FLOAT_VMARGIN + GUARD_GROWTH_UP : colHi + FLOAT_VMARGIN);
        if (dXZ <= hMargin && y >= loB && y <= hiB) return 0;
        if (seated && y < gLo) return CARVE_ERODE_REACH + 1;
        int latDist = Math.max(0, dXZ - hMargin);
        int dY = Math.max(loB - y, y - hiB);
        return Math.max(latDist, Math.max(dY, 0));
    }

    public static int the_beyond$foundationNatTop(int gLo, int foundationDepth, java.util.function.IntPredicate naturalSolid) {
        int floor = gLo - foundationDepth;
        int airGap = 0;
        for (int yy = gLo - 1; yy > floor; yy--) {
            if (!naturalSolid.test(yy)) {
                if (++airGap > FOUNDATION_MAX_LIP) return Integer.MIN_VALUE;
                continue;
            }
            return the_beyond$contiguousRockDown(yy, FOUNDATION_MIN_RUN, naturalSolid) >= FOUNDATION_MIN_RUN
                    ? yy : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    public static int the_beyond$contiguousRockDown(int probeTop, int need, java.util.function.IntPredicate naturalSolid) {
        int run = 0;
        for (int yy = probeTop; run < need; yy--) {
            if (!naturalSolid.test(yy)) break;
            run++;
        }
        return run;
    }

    public static boolean the_beyond$isFoundationFill(int y, int gLo, int natTop) {
        return natTop != Integer.MIN_VALUE && y > natTop && y <= gLo;
    }

    public static boolean the_beyond$footingActiveAt(boolean seated, boolean distributed, boolean inBaseFootprint) {
        return seated && !distributed;
    }

    static final boolean DISTRIBUTED_LIP = false;
    /** How far under a base floor its courtyard may be completed: the measured deficit, shallow enough not to read as a slab. */
    static final int DECK_FILL_REACH = 2;
    static final int BEARD_LAT_REACH = 5;
    static final int PLATFORM_COVER = 5;
    static final boolean DISTRIBUTED_BASE_BEARD = true;
    static final double BASE_BEARD_AMP = 0.5;
    static final int BASE_BEARD_VFADE = 2;

    /** A piece under the base floor is an underground room, so the lift wraps its flanks and underside into the island. */
    static final double ENVELOPE_BEARD_AMP = 2.0;
    static final int ENVELOPE_LAT = 4;
    static final int ENVELOPE_UP = 3;
    static final int ENVELOPE_DOWN = 6;
    static final double ENVELOPE_ROUGH = 0.35;

    /** Ellipsoidal so the cover does not read as a box around a box. */
    public static double the_beyond$envelopeBeardDelta(int lo, int hi, double dist, int y, double rough) {
        // Lifted ground over a roof floats as a slab over an air slit, so the roof's own cover is a fill instead.
        if (y > hi) return 0.0;
        int dv = y < lo ? lo - y : 0;
        if (dist <= 0.0 && dv == 0) return 0.0;

        if (dist > ENVELOPE_LAT || dv > ENVELOPE_DOWN) return 0.0;
        double nl = dist / (ENVELOPE_LAT + 1);
        double nv = (double) dv / (ENVELOPE_DOWN + 1);
        double nd = Math.sqrt(nl * nl + nv * nv) - ENVELOPE_ROUGH * rough;
        if (nd >= 1.0) return 0.0;
        if (nd < 0.0) nd = 0.0;
        return ENVELOPE_BEARD_AMP * (1.0 - the_beyond$smootherstep(nd));
    }
    static final boolean DISTRIBUTED_SUBSURFACE_BURY = true;
    static final int SUBSURF_KEEP_REACH = 8;
    static final boolean DISTRIBUTED_GROW_OVER_VOID = false;
    public static int the_beyond$lipTop(int natTop, int anchorColLo, int anchorColHi, int dxz) {
        if (natTop == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        int target = anchorColLo + (PLATFORM_COVER - 1);
        if (target > anchorColHi) target = anchorColHi;
        if (target < natTop) target = natTop;
        double feather = 1.0 - the_beyond$smootherstep((double) dxz / BEARD_LAT_REACH);
        int top = natTop + (int) Math.round((double) (target - natTop) * feather);
        return top > target ? target : top;
    }
    public static boolean the_beyond$isLipFill(int y, int natTop, int lipTop) {
        return natTop != Integer.MIN_VALUE && lipTop != Integer.MIN_VALUE && y > natTop && y <= lipTop;
    }

    /** The {@code floorY} clamp is load-bearing: {@code colLo} tapers per-column, so an uncapped target buries the building. */
    public static double the_beyond$baseBeardDelta(int natTop, int anchorColLo, int anchorColHi, int floorY, int dxz, int y) {
        if (natTop == Integer.MIN_VALUE || dxz > BEARD_LAT_REACH) return 0.0;
        if (floorY != Integer.MIN_VALUE && y >= floorY) return 0.0;
        int target = anchorColLo + (PLATFORM_COVER - 1);
        if (target > anchorColHi) target = anchorColHi;
        if (floorY != Integer.MIN_VALUE && target > floorY - 1) target = floorY - 1;
        if (target < natTop) target = natTop;
        if (y <= natTop || y > target + BASE_BEARD_VFADE) return 0.0;
        double feather = 1.0 - the_beyond$smootherstep((double) dxz / BEARD_LAT_REACH);
        double vAmp = (y <= target) ? 1.0
                : 1.0 - the_beyond$smootherstep((double) (y - target) / BASE_BEARD_VFADE);
        return BASE_BEARD_AMP * feather * vAmp;
    }
    /** Outline noise of a pedestal city's planned ground, with lobes longer than a pad's 10-block edge. */
    static final double PEDESTAL_LOBE_FREQ = 0.075;
    static final double PEDESTAL_LOBE_FREQ_HI = 0.145;
    static final double PEDESTAL_RING_FREQ = 0.4;
    static final double PEDESTAL_LOBE_GAIN = 1.9;

    static double the_beyond$padLobe(int x, int z) {
        return the_beyond$padLobeAt(BeyondEndChunkGenerator.simplexNoise, x, z);
    }

    static double the_beyond$padLobeAt(com.thebeyond.util.HashSimplexNoise n, int x, int z) {
        if (n == null) return 0.0;
        double lo = n.getValue(x * PEDESTAL_LOBE_FREQ - 8123.0, 0.0, z * PEDESTAL_LOBE_FREQ + 5077.0);
        double hi = n.getValue(x * PEDESTAL_LOBE_FREQ_HI + 611.0, 0.0, z * PEDESTAL_LOBE_FREQ_HI - 2903.0);
        double v = PEDESTAL_LOBE_GAIN * (0.6 * lo + 0.4 * hi);
        return v < -1.0 ? -1.0 : (v > 1.0 ? 1.0 : v);
    }

    /** Courses of island kept under a pedestal city's start, or its carve sands away the thin rim its first floor stands on. */
    static final int GROUND_KEEP = 5;
    static final int BEARD_GROUND_BAND = 16;
    static final int BEARD_SEAT_DROP = 12;
    static int the_beyond$groundRestNatTop(int lo, int natTop) {
        return (natTop != Integer.MIN_VALUE && lo - natTop <= FOUNDATION_MAX_LIP) ? natTop : Integer.MIN_VALUE;
    }
    static int the_beyond$seatConfinedNatTop(int natTop, int seatFloor) {
        if (natTop == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (seatFloor == Integer.MIN_VALUE || seatFloor - natTop <= BEARD_SEAT_DROP) ? natTop : Integer.MIN_VALUE;
    }
    static double the_beyond$baseBeardDeltaAt(int[] dxz, int[] lo, int[] hi, boolean[] base, int[] baseNat, int[] baseFloor, int nBits, int y) {
        double best = 0.0;
        for (int k = 0; k < nBits; k++) {
            if (baseNat[k] == Integer.MIN_VALUE || dxz[k] > BEARD_LAT_REACH) continue;
            int floorY = base[k] ? baseFloor[k] : Integer.MIN_VALUE;
            double d = the_beyond$baseBeardDelta(baseNat[k], lo[k], hi[k], floorY, dxz[k], y);
            if (d > best) best = d;
        }
        return best;
    }

    static void the_beyond$logNoGroundFoot(CarveMask m) {
        if (BeyondGenDiagnostics.loggedCityGround.size() < 4000
                && BeyondGenDiagnostics.loggedCityGround.add("foot@" + m.baseBox.minX() + "," + m.baseBox.minZ())) {
            com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] ground under {} at [{},{},{}]: the start piece's ground floor is"
                    + " unknown, so nothing is laid under it", m.structureKey, m.baseBox.minX(), m.baseBox.minY(), m.baseBox.minZ());
        }
    }

    static void the_beyond$notePlanKeptOut(CarveMask owner, int x, int y, int z, String by) {
        if (owner.planKeptOutLogged) return;
        owner.planKeptOutLogged = true;
        com.thebeyond.TheBeyond.LOGGER.info("[Beyond] ground under {}: planned cell [{},{},{}] kept out by {}",
                owner.structureKey, x, y, z, by);
    }

    static boolean the_beyond$pedestal(StructureIntegrationProfile profile, @org.jetbrains.annotations.Nullable ResourceLocation key,
            @org.jetbrains.annotations.Nullable net.minecraft.core.Registry<Structure> reg, StructureStart start) {
        return (profile.basePedestal() || BeyondForeignStructureProfiles.isBasePedestal(key, reg, start.getStructure()))
                && !ForeignFit.sinks(start.getPieces(), null);
    }

    static void the_beyond$logGroundPlan(CarveMask m, @org.jetbrains.annotations.Nullable CityGroundPlan p) {
        if (p == null || BeyondGenDiagnostics.loggedCityGround.size() >= 4000
                || !BeyondGenDiagnostics.loggedCityGround.add("plan@" + m.baseBox.minX() + "," + m.baseBox.minZ())) return;
        if (p.orphan) {
            com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] ground under {} at [{},{},{}]: {} ground floor columns over the void"
                    + " and no island at the floor's level to grow from, left unsupported", m.structureKey, m.baseBox.minX(),
                    p.floor, m.baseBox.minZ(), p.missing);
            return;
        }
        com.thebeyond.TheBeyond.LOGGER.info("[Beyond] ground under {} at [{},{},{}]: ground floor held {}, step {}, missing {},"
                + " sunk {}, apron missing {}, laid {} blocks in {} columns, overhang {}", m.structureKey, m.baseBox.minX(),
                p.floor, m.baseBox.minZ(), p.held, p.step, p.missing, p.sunk, p.apron, p.volume, p.columns, Math.round(p.reach));
    }

    final java.util.Map<StructureStart, CarveMask> the_beyond$maskCache =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private final java.util.Map<StructureStart, Boolean> the_beyond$cavityReject =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** The starts the carve has already seen, which reaches further back than vanilla's 8-chunk references. */
    public java.util.Collection<StructureStart> the_beyond$knownStarts() {
        return the_beyond$maskCache.keySet();
    }

    public List<CarveMask> the_beyond$collectCarveMasks(StructureManager sm, ChunkPos cp) {
        if (!BeyondTerrainState.isActive()) return Collections.emptyList();
        final RegistryAccess ra = sm.registryAccess();
        List<CarveMask> masks = null;
        var structReg = ra.registryOrThrow(Registries.STRUCTURE);
        for (StructureStart start : sm.startsForStructure(cp, s -> true)) {
            if (BeyondStructureArbiter.isVetoed(start)) continue;
            ResourceLocation key = structReg.getKey(start.getStructure());
            StructureIntegrationProfile profile = BeyondForeignStructureProfiles.resolve(start.getStructure(), key);
            if (profile == null || !profile.carve()) {
                if (key != null && !"the_beyond".equals(key.getNamespace()) && !"minecraft".equals(key.getNamespace())
                        && BeyondGenDiagnostics.loggedLeaveAlone.add(key.toString())) {
                    com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] foreign-structure {} -> LEAVE_ALONE (vanilla pipeline, not carved)", key);
                }
                continue;
            }
            if (!the_beyond$bboxNearChunk(start.getBoundingBox(), cp)) continue;
            boolean layerDistributed = AutoHostWarmUp.layerDistributed(key, start, gen.getBiomeSource().possibleBiomes());
            // Floaters are distributed too, which buys the true-3D skirt instead of a sheer-walled column prism.
            boolean floater = profile.anchor() == StructureIntegrationProfile.Anchor.FLOATING
                    || BeyondForeignStructureProfiles.isAutoFloatCarved(key);
            boolean distributed = layerDistributed
                    || BeyondForeignStructureProfiles.isAutoSeatedProjected(key);
            boolean seated = !floater
                    && (distributed || profile.anchor() == StructureIntegrationProfile.Anchor.SEATED);
            CarveMask mask = the_beyond$maskCache.get(start);
            if (mask == null) {
                mask = the_beyond$buildMask(start, seated, distributed, layerDistributed, profile.flushTolerance(),
                        profile.connectDetached());
                // Before publishing: the far scan reads cached masks, and carveOnly false would add an END_STONE foundation.
                mask.carveOnly = BeyondForeignStructureProfiles.isEmbedded(key);
                mask.hugTerrain = BeyondForeignStructureProfiles.isHugTerrain(key);
                mask.basePedestal = the_beyond$pedestal(profile, key, structReg, start);
                mask.floating = floater;
                mask.structureKey = key;
                the_beyond$maskCache.put(start, mask);
            } else {
                mask.carveOnly = BeyondForeignStructureProfiles.isEmbedded(key);
                mask.hugTerrain = BeyondForeignStructureProfiles.isHugTerrain(key);
                mask.basePedestal = the_beyond$pedestal(profile, key, structReg, start);
                mask.floating = floater;
                mask.structureKey = key;
            }
            if (masks == null) masks = new ArrayList<>(2);
            masks.add(mask);
            String id = key == null ? "unknown" : key.toString();
            if (BeyondGenDiagnostics.loggedMaskKeys.add(id)) {
                int cells = the_beyond$maskCellCount(mask);
                int area = mask.w * mask.d;
                int span = mask.gHi - mask.gLo;
                int latR = distributed ? CARVE_DIST_LAT_REACH : (seated ? 0 : CARVE_LAT_REACH);
                int vertR = distributed ? CARVE_DIST_VERT_REACH : (seated ? 0 : CARVE_VERT_REACH);
                double ampR = distributed ? CARVE_DIST_AMP : (seated ? 0.0 : CARVE_AMP);
                String carveKind = distributed ? "DISTRIBUTED-organic" : (seated ? "SEATED(no-skirt)" : "FLOATING");
                String faceRoughKind = distributed
                        ? "ON(max=" + FACE_ROUGH + ",freq=" + FACE_ROUGH_FREQ + ")" : "OFF";
                String baseAnchorKind = (distributed && mask.baseBox != null)
                        ? "ON reach=" + BEARD_LAT_REACH + " baseBox=[" + mask.baseBox.minX() + "," + mask.baseBox.minZ()
                          + ".." + mask.baseBox.maxX() + "," + mask.baseBox.maxZ() + "]"
                        : "OFF";
                String baseBeardKind = mask.basePedestal
                        ? "GROUND-PLAN(its own ground where the island misses its ground floor)"
                        : mask.carveOnly ? "OFF(interior-only: no foundation/beard)"
                        : (DISTRIBUTED_BASE_BEARD && distributed && mask.baseBox != null)
                        ? "ON(amp=" + BASE_BEARD_AMP + ",vfade=" + BASE_BEARD_VFADE + ",cover=" + PLATFORM_COVER
                          + ",floor=" + mask.baseBox.minY() + ",ceil=" + (mask.baseBox.minY() - 1) + ")" : "OFF";
                com.thebeyond.TheBeyond.LOGGER.debug(
                        "[Beyond] mask {}: cells={} (filled={} closeR={}) bbox={}x{}={} ({}%) y=[{}..{}] span={} seated={} "
                        + "carve={} skirt(lat={},vert={},amp={}) faceRough={} baseAnchorPreserve={} baseBeard={} clearedEnvelope~{}x{} [amp tunes cut depth: lower=hug closer, higher=wider gap]",
                        id, cells, mask.filledHoles, CLOSE_RADIUS, mask.w, mask.d, area,
                        area > 0 ? (cells * 100 / area) : 0, mask.gLo, mask.gHi, span, seated,
                        carveKind, latR, vertR, ampR, faceRoughKind, baseAnchorKind, baseBeardKind, mask.w + 2 * latR, mask.d + 2 * latR);
                the_beyond$logSpanGrow(id, mask.oX, mask.oZ, mask.w, mask.d);
                if (span > 255) com.thebeyond.TheBeyond.LOGGER.debug(
                        "[Beyond] mask {} span={} > 255 — a tall jigsaw tower; the 16-bit column encoding now carves it (was clamped)", id, span);
            }
        }
        synchronized (the_beyond$maskCache) {
            for (var e : the_beyond$maskCache.entrySet()) {
                StructureStart start = e.getKey();
                CarveMask mask = e.getValue();
                if (start == null || mask == null || !start.isValid()) continue;
                if (BeyondStructureArbiter.isVetoed(start)) continue;
                if (!the_beyond$bboxNearChunk(start.getBoundingBox(), cp)) continue;
                if (masks != null && masks.contains(mask)) continue;
                if (masks == null) masks = new ArrayList<>(2);
                masks.add(mask);
                if (BeyondGenDiagnostics.loggedFarMaskKeys.add(System.identityHashCode(start))) {
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] far-chunk carve mask hit (no STRUCTURE_REFERENCES) for start bbox={} at chunk {}"
                            + " -> STRUCTURE_STARTS pre-seed supplied the mask", start.getBoundingBox(), cp);
                }
            }
        }
        if (masks != null && !BeyondGenDiagnostics.loggedSilhouetteClear) {
            BeyondGenDiagnostics.loggedSilhouetteClear = true;
            com.thebeyond.TheBeyond.LOGGER.debug(
                    "[Beyond] occupancy-mask clear active for {} structure(s) near chunk {}",
                    masks.size(), cp);
        }
        return masks == null ? Collections.emptyList() : masks;
    }

    /** The chunk's terrain in the carve band before decoration, so the debris sweep judges connectivity on island rock alone. */
    public static final class TerrainSnapshot {
        private final int yLo, yHi, oX, oZ;
        private final BlockState[] states;

        private TerrainSnapshot(int yLo, int yHi, int oX, int oZ) {
            this.yLo = yLo; this.yHi = yHi; this.oX = oX; this.oZ = oZ;
            this.states = new BlockState[256 * (yHi - yLo + 1)];
        }

        private int index(int x, int y, int z) {
            if (y < yLo || y > yHi) return -1;
            int lx = x - oX, lz = z - oZ;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return -1;
            return (lx + lz * 16) * (yHi - yLo + 1) + (y - yLo);
        }

        BlockState at(int x, int y, int z) {
            int i = index(x, y, z);
            return i < 0 ? null : states[i];
        }

        boolean covers(int y) {
            return y >= yLo && y <= yHi;
        }
    }

    public TerrainSnapshot the_beyond$snapshotCarveTerrain(ChunkAccess chunk, List<CarveMask> masks) {
        if (masks.isEmpty()) return null;
        int yLo = Integer.MAX_VALUE, yHi = Integer.MIN_VALUE;
        for (int i = 0; i < masks.size(); i++) {
            CarveMask m = masks.get(i);
            if (m.gLo - CARVE_VERT_REACH < yLo) yLo = m.gLo - CARVE_VERT_REACH;
            if (m.gHi + CARVE_VERT_REACH > yHi) yHi = m.gHi + CARVE_VERT_REACH;
        }
        yLo = Math.max(yLo, chunk.getMinBuildHeight());
        yHi = Math.min(yHi, chunk.getMaxBuildHeight() - 1);
        if (yLo > yHi) return null;
        ChunkPos cp = chunk.getPos();
        TerrainSnapshot snap = new TerrainSnapshot(yLo, yHi, cp.getMinBlockX(), cp.getMinBlockZ());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // Every column: a body hanging past the carve reach is still attached, and holes would read it as debris.
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                for (int y = yLo; y <= yHi; y++) {
                    BlockState st = chunk.getBlockState(p.set(x, y, z));
                    if (!st.isAir()) snap.states[snap.index(x, y, z)] = st;
                }
            }
        }
        return snap;
    }

    public int the_beyond$dropCarveDebris(WorldGenLevel level, ChunkAccess chunk, List<CarveMask> masks,
            TerrainSnapshot snap) {
        if (snap == null) return 0;
        if (masks.isEmpty()) return 0;
        final ChunkPos cp = chunk.getPos();
        final int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
        final int yLo = snap.yLo, yHi = snap.yHi;

        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet attached = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final it.unimi.dsi.fastutil.longs.LongArrayList stack = new it.unimi.dsi.fastutil.longs.LongArrayList();
        final it.unimi.dsi.fastutil.longs.LongArrayList body = new it.unimi.dsi.fastutil.longs.LongArrayList();
        final java.util.function.LongPredicate solid = c -> the_beyond$debrisSolid(level, cp, snap, probe,
                BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c));
        final BlockState air = Blocks.AIR.defaultBlockState();
        int dropped = 0;
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                if (!the_beyond$inCarveReach(masks, x, z)) continue;
                for (int y = yLo; y <= yHi; y++) {
                    long key = BlockPos.asLong(x, y, z);
                    if (seen.contains(key) || !the_beyond$debrisSolid(level, cp, snap, probe, x, y, z)) continue;
                    if (the_beyond$debrisDegree(level, cp, snap, probe, x, y, z) > DEBRIS_SEED_DEGREE) continue;
                    if (!the_beyond$floodDebris(key, solid, seen, attached, body, stack)) {
                        if (body.size() <= DEBRIS_MAX_CELLS) BeyondGenDiagnostics.debrisAttached.addAndGet(body.size());
                        continue;
                    }
                    for (int i = 0; i < body.size(); i++) {
                        long c = body.getLong(i);
                        int cx = BlockPos.getX(c), czz = BlockPos.getZ(c);
                        if (cx < bx || cx >= bx + 16 || czz < bz || czz >= bz + 16) continue;
                        if (the_beyond$courtyardFill(masks, cx, BlockPos.getY(c), czz)) continue;
                        chunk.setBlockState(probe.set(cx, BlockPos.getY(c), czz), air, false);
                        dropped++;
                    }
                }
            }
        }
        for (int pass = 0; pass < GRAIN_PASSES; pass++) {
            dropped += the_beyond$degrain(level, chunk, masks, snap, yLo, yHi);
        }
        return dropped;
    }

    /** Removes speckle on a carved face: a block held by two rock neighbours at most. Radius one spares the island's meanders. */
    private int the_beyond$degrain(WorldGenLevel level, ChunkAccess chunk, List<CarveMask> masks,
            TerrainSnapshot snap, int yLo, int yHi) {
        final ChunkPos cp = chunk.getPos();
        final int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final BlockState air = Blocks.AIR.defaultBlockState();
        it.unimi.dsi.fastutil.longs.LongArrayList warts = new it.unimi.dsi.fastutil.longs.LongArrayList();
        it.unimi.dsi.fastutil.longs.LongArrayList pits = new it.unimi.dsi.fastutil.longs.LongArrayList();
        java.util.List<BlockState> pitFill = new java.util.ArrayList<>();
        final int[] band = new int[2];
        final BeyondEndChunkGenerator.ColumnScratch scratch = BeyondEndChunkGenerator.getColumnScratch();
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                if (!the_beyond$inCarveReach(masks, x, z)) continue;
                the_beyond$buildBand(masks, x, z, band);
                boolean sampled = false;
                for (int y = yLo; y <= yHi; y++) {
                    final boolean nearBuild = y >= band[0] && y <= band[1];
                    boolean self = the_beyond$debrisSolid(level, cp, snap, p, x, y, z);
                    if (self && nearBuild) continue;   // beard, envelope and courtyard fill are deliberate
                    int rock = 0;
                    boolean readable = true, roofed = false, openAbove = false;
                    BlockState cover = null, bulk = null;
                    for (int[] d : DEBRIS_DIRS) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        if (ny < level.getMinBuildHeight() || ny >= level.getMaxBuildHeight()
                                || Math.abs((nx >> 4) - cp.x) > 1 || Math.abs((nz >> 4) - cp.z) > 1) { readable = false; break; }
                        boolean solid = the_beyond$debrisSolid(level, cp, snap, p, nx, ny, nz);
                        if (solid) {
                            rock++;
                            if (self) continue;
                            // Air above means biome cover, rock above means bulk, and the pit copies the one for its role.
                            boolean nOpen = ny + 1 < level.getMaxBuildHeight()
                                    && level.getBlockState(p.set(nx, ny + 1, nz)).isAir();
                            BlockState st = level.getBlockState(p.set(nx, ny, nz));
                            if (nOpen) { if (cover == null) cover = st; }
                            else if (bulk == null) bulk = st;
                        } else if (d[1] > 0) {
                            if (level.getBlockState(p.set(nx, ny, nz)).isAir()) openAbove = true;
                            else roofed = true;   // rock under a floor is bearing it, not grain
                        }
                    }
                    if (!readable) continue;
                    if (self) {
                        if (rock > GRAIN_WART_ROCK || roofed) continue;
                        // Neighbour features may be in the snapshot, so the generator's density tells rock from plant.
                        if (!sampled) {
                            BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scratch);
                            sampled = true;
                        }
                        if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scratch)) warts.add(BlockPos.asLong(x, y, z));
                        continue;
                    }
                    BlockState fill = openAbove ? (cover != null ? cover : bulk) : (bulk != null ? bulk : cover);
                    if (rock >= GRAIN_PIT_ROCK && fill != null
                            && level.getBlockState(p.set(x, y, z)).isAir()
                            && !the_beyond$courtyardFill(masks, x, y, z)) {
                        pits.add(BlockPos.asLong(x, y, z));
                        pitFill.add(fill);
                    }
                }
            }
        }
        BeyondGenDiagnostics.grainWarts.addAndGet(warts.size());
        BeyondGenDiagnostics.grainPits.addAndGet(pits.size());
        for (int i = 0; i < warts.size(); i++) {
            long c = warts.getLong(i);
            chunk.setBlockState(p.set(BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c)), air, false);
        }
        for (int i = 0; i < pits.size(); i++) {
            long c = pits.getLong(i);
            chunk.setBlockState(p.set(BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c)), pitFill.get(i), false);
        }
        return warts.size() + pits.size();
    }



    private static void the_beyond$buildBand(List<CarveMask> masks, int x, int z, int[] band) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < masks.size(); i++) {
            CarveMask m = masks.get(i);
            boolean near = false;
            for (int dx = -GRAIN_BUILD_MARGIN; dx <= GRAIN_BUILD_MARGIN && !near; dx++) {
                for (int dz = -GRAIN_BUILD_MARGIN; dz <= GRAIN_BUILD_MARGIN; dz++) {
                    if (m.occupied(x + dx, z + dz)) { near = true; break; }
                }
            }
            if (!near) continue;
            if (m.gLo - CARVE_VERT_REACH < lo) lo = m.gLo - CARVE_VERT_REACH;
            if (m.gHi + CARVE_VERT_REACH > hi) hi = m.gHi + CARVE_VERT_REACH;
        }
        band[0] = lo; band[1] = hi;
    }

    /** Floods the body holding seed, debris only when small and touching no attached body, even one whose flood was cut short. */
    static boolean the_beyond$floodDebris(long seed, java.util.function.LongPredicate solid,
            it.unimi.dsi.fastutil.longs.LongOpenHashSet seen, it.unimi.dsi.fastutil.longs.LongOpenHashSet attached,
            it.unimi.dsi.fastutil.longs.LongArrayList body, it.unimi.dsi.fastutil.longs.LongArrayList stack) {
        body.clear();
        stack.clear();
        stack.add(seed);
        seen.add(seed);
        boolean big = false, touches = false;
        while (!stack.isEmpty()) {
            long c = stack.removeLong(stack.size() - 1);
            body.add(c);
            if (body.size() > DEBRIS_MAX_CELLS) { big = true; break; }
            int cx = BlockPos.getX(c), cy = BlockPos.getY(c), cz = BlockPos.getZ(c);
            for (int[] d : DEBRIS_DIRS) {
                long nk = BlockPos.asLong(cx + d[0], cy + d[1], cz + d[2]);
                if (attached.contains(nk)) touches = true;
                if (seen.contains(nk) || !solid.test(nk)) continue;
                seen.add(nk);
                stack.add(nk);
            }
        }
        if (!big && !touches) return true;
        attached.addAll(body);
        attached.addAll(stack);
        return false;
    }

    /** A courtyard floor is deliberate fill, guarded only on the start's floor plane, so an interior far above is still swept. */
    private static boolean the_beyond$courtyardFill(List<CarveMask> masks, int x, int y, int z) {
        for (int i = 0; i < masks.size(); i++) {
            CarveMask m = masks.get(i);
            int floor = m.baseBox != null ? m.baseBox.minY() : m.gLo;
            if (y < floor - COURTYARD_GUARD_DOWN || y > floor + COURTYARD_GUARD_UP) continue;
            if (!m.occupied(x, z)) continue;
            if (m.isFilled((x - m.oX) + (z - m.oZ) * m.w)) return true;
        }
        return false;
    }

    private static boolean the_beyond$inCarveReach(List<CarveMask> masks, int x, int z) {
        for (int i = 0; i < masks.size(); i++) {
            CarveMask m = masks.get(i);
            if (x >= m.oX - CARVE_SCAN_REACH && x < m.oX + m.w + CARVE_SCAN_REACH
                    && z >= m.oZ - CARVE_SCAN_REACH && z < m.oZ + m.d + CARVE_SCAN_REACH) return true;
        }
        return false;
    }

    private static boolean the_beyond$debrisSolid(WorldGenLevel level, ChunkPos cp, TerrainSnapshot snap,
            BlockPos.MutableBlockPos p, int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        int dcx = (x >> 4) - cp.x, dcz = (z >> 4) - cp.z;
        if (Math.abs(dcx) > 1 || Math.abs(dcz) > 1) return true;
        if (dcx == 0 && dcz == 0) {
            if (!snap.covers(y)) return !level.getBlockState(p.set(x, y, z)).isAir();
            BlockState was = snap.at(x, y, z);
            return was != null && level.getBlockState(p.set(x, y, z)) == was;
        }
        return !level.getBlockState(p.set(x, y, z)).isAir();
    }

    private static int the_beyond$debrisDegree(WorldGenLevel level, ChunkPos cp, TerrainSnapshot snap,
            BlockPos.MutableBlockPos p, int x, int y, int z) {
        int n = 0;
        for (int[] d : DEBRIS_DIRS) {
            if (the_beyond$debrisSolid(level, cp, snap, p, x + d[0], y + d[1], z + d[2])) n++;
        }
        return n;
    }

    public void the_beyond$placeUnreferencedCarveStructures(net.minecraft.world.level.WorldGenLevel level,
            StructureManager sm, ChunkAccess chunk) {
        if (!BeyondTerrainState.isActive() || the_beyond$maskCache.isEmpty()) return;
        ChunkPos cp = chunk.getPos();
        net.minecraft.world.level.LevelHeightAccessor lha = chunk.getHeightAccessorForGeneration();
        BoundingBox area = new BoundingBox(cp.getMinBlockX(), lha.getMinBuildHeight() + 1, cp.getMinBlockZ(),
                cp.getMaxBlockX(), lha.getMaxBuildHeight() - 1, cp.getMaxBlockZ());
        net.minecraft.core.SectionPos sp = net.minecraft.core.SectionPos.of(cp, level.getMinSection());
        java.util.List<StructureStart> far = null;
        synchronized (the_beyond$maskCache) {
            for (StructureStart start : the_beyond$maskCache.keySet()) {
                if (start == null || !start.isValid()) continue;
                if (!start.getBoundingBox().intersects(area)) continue;
                // Keyed by structure and start chunk: a reloaded start is a new object and the stale one would place twice.
                boolean referenced = false;
                for (StructureStart s : sm.startsForStructure(sp, start.getStructure())) {
                    if (s.getChunkPos().equals(start.getChunkPos())) { referenced = true; break; }
                }
                if (referenced) continue;
                if (far == null) far = new java.util.ArrayList<>(1);
                boolean twin = false;
                for (StructureStart f : far) twin |= f.getStructure() == start.getStructure() && f.getChunkPos().equals(start.getChunkPos());
                if (!twin) far.add(start);
            }
        }
        if (far == null) return;
        for (StructureStart start : far) {
            try {
                net.minecraft.world.level.levelgen.WorldgenRandom rnd =
                        new net.minecraft.world.level.levelgen.WorldgenRandom(
                                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
                rnd.setLargeFeatureSeed(level.getSeed() ^ start.getChunkPos().toLong(), cp.x, cp.z);
                start.placeInChunk(level, sm, gen, rnd, area, cp);
                if (BeyondGenDiagnostics.loggedFarBuild.add(System.identityHashCode(start)))
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] far-chunk BUILD (no reference) start@{} placed at chunk {}", start.getChunkPos(), cp);
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean the_beyond$landmarkInForeignCavity(StructureManager sm, StructureStart self, ChunkPos decorChunk) {
        Boolean cached = the_beyond$cavityReject.get(self);
        if (cached != null) return cached;
        try {
            BoundingBox sb = self.getBoundingBox();
            int cx = (sb.minX() + sb.maxX()) / 2, cz = (sb.minZ() + sb.maxZ()) / 2;
            if ((double) cx * cx + (double) cz * cz < 650.0 * 650.0) return false;
            ChunkPos centerCp = new ChunkPos(cx >> 4, cz >> 4);
            if (Math.abs(centerCp.x - decorChunk.x) > 1 || Math.abs(centerCp.z - decorChunk.z) > 1) return false;
            List<CarveMask> masks = the_beyond$collectCarveMasks(sm, centerCp);
            if (masks.isEmpty()) { the_beyond$cavityReject.put(self, Boolean.FALSE); return false; }
            CarveMask own = the_beyond$buildMask(self, false, false, false, 0);
            int probed = 0, inHole = 0;
            for (int j = 0; j < own.d; j++) {
                for (int i = 0; i < own.w; i++) {
                    int bit = i + j * own.w;
                    if ((own.occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                    if (own.isFilled(bit)) continue;
                    probed++;
                    int wx = own.oX + i, wz = own.oZ + j;
                    for (int y = own.colLo(bit); y <= own.colHi(bit); y++) {
                        if (the_beyond$carveOutsideDist(masks, wx, y, wz) == 0) { inHole++; break; }
                    }
                }
            }
            boolean reject = probed > 0 && inHole * 2 >= probed;
            the_beyond$cavityReject.put(self, reject);
            if (reject) {
                ResourceLocation key = sm.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE).getKey(self.getStructure());
                String id = key == null ? "unknown" : key.toString();
                if (BeyondGenDiagnostics.loggedCavityReject.add(id)) {
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] landmark-cavity-reject {} @bbox-center({},{}) frac={}/{} in foreign carve cavity -> SKIP placeInChunk",
                            id, cx, cz, inHole, probed);
                }
            }
            return reject;
        } catch (Throwable ignored) {
            return false;
        }
    }

    CarveMask the_beyond$buildMask(StructureStart start, boolean seated, boolean distributed, boolean layerDistributed, int foundationDepth) {
        return the_beyond$buildMask(start, seated, distributed, layerDistributed, foundationDepth, false);
    }

    CarveMask the_beyond$buildMask(StructureStart start, boolean seated, boolean distributed, boolean layerDistributed, int foundationDepth, boolean connectDetached) {
        BoundingBox env = start.getBoundingBox();
        BoundingBox baseBox = start.getPieces().isEmpty() ? null : start.getPieces().get(0).getBoundingBox();
        int pedLo = Integer.MAX_VALUE;
        // A city's outer pieces sit on the start's box, so without this pad the visible faces get clipped.
        final int envPad = ENVELOPE_PAD;
        int oX = env.minX() - envPad, oZ = env.minZ() - envPad;
        int w = env.getXSpan() + 2 * envPad, d = env.getZSpan() + 2 * envPad;
        int[] boxTop = new int[w * d], boxBot = new int[w * d];
        java.util.Arrays.fill(boxTop, Integer.MIN_VALUE);
        java.util.Arrays.fill(boxBot, Integer.MAX_VALUE);
        long[] occ = new long[((w * d) + 63) >> 6];
        int[] gy = { Integer.MAX_VALUE, Integer.MIN_VALUE };
        int[] cLo = new int[w * d], cHi = new int[w * d];
        java.util.Arrays.fill(cLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(cHi, Integer.MIN_VALUE);
        IntArrayList dt3I = distributed ? new IntArrayList() : null;
        IntArrayList dt3J = distributed ? new IntArrayList() : null;
        IntArrayList dt3Y = distributed ? new IntArrayList() : null;
        boolean[] hasBlock = distributed ? new boolean[w * d] : null;
        final StructureTemplateManager tm = BeyondEndChunkGenerator.the_beyond$templateManager;
        int[] tmTopCache = null;
        int[] tmLoCol = null;
        List<int[]> farFloors = null;
        int[] liftTop = boxTop, liftBot = boxBot;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet buriedInterior = null;
        int[][] roomRoof = new int[1][];
        final boolean[] surfacePlaced = baseBox != null ? the_beyond$surfacePlaced(start.getPieces()) : null;
        List<BoundingBox> pieceBoxList = new ArrayList<>(start.getPieces().size());
        int pieceIdx = -1;
        for (StructurePiece piece : start.getPieces()) {
            pieceIdx++;
            pieceBoxList.add(piece.getBoundingBox());
            if (!(piece instanceof PoolElementStructurePiece pe)) {
                // Air included: a room's floor is often its column's only solid, so solids alone would bury the room.
                int tsHits = -1;
                if (piece instanceof TemplateStructurePiece tsp) {
                    tsHits = the_beyond$rasterizeTemplate(tsp.template(), tsp.placeSettings(), tsp.templatePosition(),
                            oX, oZ, w, d, occ, gy, cLo, cHi, distributed, dt3I, dt3J, dt3Y, hasBlock, null, true);
                    if (com.thebeyond.TheBeyond.LOGGER.isDebugEnabled()
                            && BeyondGenDiagnostics.loggedMaskPieces.add("TS@" + tsp.templatePosition())) {
                        BoundingBox tb = piece.getBoundingBox();
                        com.thebeyond.TheBeyond.LOGGER.debug(
                                "[Beyond] mask template piece {} pos={} rot={} mode={} hits={} bbox=[{},{},{}..{},{},{}]",
                                piece.getClass().getSimpleName(), tsp.templatePosition(), tsp.getRotation(),
                                tsHits >= 0 ? "perBlock" : "failsoft-bbox", Math.max(tsHits, 0),
                                tb.minX(), tb.minY(), tb.minZ(), tb.maxX(), tb.maxY(), tb.maxZ());
                    }
                }
                if (tsHits < 0) the_beyond$rasterizeBox(piece.getBoundingBox(), oX, oZ, w, d, occ, gy, cLo, cHi);
                continue;
            }
            StructurePoolElement el = the_beyond$unwrapPoolElement(pe.getElement());
            int islandTop = Integer.MIN_VALUE;
            boolean buriedThere = false;
            if (baseBox != null && surfacePlaced[pieceIdx]
                    && (TERRAIN_MATCHING_IS_RIGID || el.getProjection() == StructureTemplatePool.Projection.RIGID)) {
                BoundingBox sb0 = piece.getBoundingBox();
                islandTop = the_beyond$footprintNatTop(sb0);
                buriedThere = islandTop != Integer.MIN_VALUE && islandTop - sb0.minY() >= PIECE_SINK;
                if (buriedThere) {
                    if (farFloors == null) farFloors = new ArrayList<>();
                    int floor = Math.min(sb0.maxY() + 1, islandTop + 1 + PIECE_RISE);
                    farFloors.add(new int[]{sb0.minX(), sb0.minZ(), sb0.maxX(), sb0.maxZ(), sb0.minY(), sb0.maxY(), floor});
                    if (liftTop == boxTop) { liftTop = boxTop.clone(); liftBot = boxBot.clone(); }
                }
            }
            // Every piece before terrain_matching exits, or the beard covers a walkway deck's headroom.
            {
                BoundingBox tb2 = piece.getBoundingBox();
                int bx0 = Math.max(tb2.minX(), oX), bx1 = Math.min(tb2.maxX(), oX + w - 1);
                int bz0 = Math.max(tb2.minZ(), oZ), bz1 = Math.min(tb2.maxZ(), oZ + d - 1);
                for (int bz = bz0; bz <= bz1; bz++) {
                    for (int bx = bx0; bx <= bx1; bx++) {
                        int bi = (bx - oX) + (bz - oZ) * w;
                        if (tb2.maxY() > boxTop[bi]) boxTop[bi] = tb2.maxY();
                        if (tb2.minY() < boxBot[bi]) boxBot[bi] = tb2.minY();
                        // A buried piece's room is cleared by its own columns, so its box margin is left to the lift.
                        if (liftTop != boxTop && !buriedThere) {
                            if (tb2.maxY() > liftTop[bi]) liftTop[bi] = tb2.maxY();
                            if (tb2.minY() < liftBot[bi]) liftBot[bi] = tb2.minY();
                        }
                    }
                }
            }
            if (!TERRAIN_MATCHING_IS_RIGID && el.getProjection() != StructureTemplatePool.Projection.RIGID) {
                // TERRAIN_MATCHING re-projects per column when placed: snap to the topmost natural or it carves a phantom slab.
                StructureTemplate tt = null;
                String tmLoc = "?";
                if (tm != null && el instanceof SinglePoolElement spe) {
                    try {
                        var either = ((SinglePoolElementAccessor) (Object) spe).the_beyond$template();
                        tmLoc = either.map(java.util.Objects::toString, st -> "inline");
                        tt = either.map(tm::getOrCreate, st -> st);
                    } catch (Throwable ignored) { tt = null; }
                }
                boolean tmPerBlock = false;
                int tmHits = 0, tmSkipVoid = 0, tmTopMin = Integer.MAX_VALUE, tmTopMax = Integer.MIN_VALUE;
                if (tt != null) {
                    try {
                        List<StructureTemplate.Palette> tmPals =
                                ((StructureTemplateAccessor) (Object) tt).the_beyond$palettes();
                        if (!tmPals.isEmpty()) {
                            if (tmTopCache == null) {
                                tmTopCache = new int[w * d];
                                java.util.Arrays.fill(tmTopCache, Integer.MAX_VALUE);
                                tmLoCol = new int[w * d];
                                java.util.Arrays.fill(tmLoCol, Integer.MAX_VALUE);
                            }
                            StructurePlaceSettings tmSettings = new StructurePlaceSettings().setRotation(pe.getRotation());
                            BlockPos tmOrigin = pe.getPosition();
                            for (StructureTemplate.StructureBlockInfo info : tmPals.get(0).blocks()) {
                                if (info.state().isAir()) continue;
                                BlockPos wp = StructureTemplate.calculateRelativePosition(tmSettings, info.pos()).offset(tmOrigin);
                                int i = wp.getX() - oX, j = wp.getZ() - oZ;
                                if (i < 0 || i >= w || j < 0 || j >= d) continue;
                                int bit = i + j * w;
                                int topSolid = tmTopCache[bit];
                                if (topSolid == Integer.MAX_VALUE) {
                                    topSolid = the_beyond$topmostNatSolid(wp.getX(), wp.getZ());
                                    tmTopCache[bit] = topSolid;
                                }
                                if (topSolid == Integer.MIN_VALUE) { tmSkipVoid++; continue; }
                                int wy = topSolid + (wp.getY() - tmOrigin.getY());
                                occ[bit >> 6] |= (1L << (bit & 63));
                                tmHits++;
                                if (topSolid < tmTopMin) tmTopMin = topSolid;
                                if (topSolid > tmTopMax) tmTopMax = topSolid;
                                if (wy < gy[0]) gy[0] = wy;
                                if (wy > gy[1]) gy[1] = wy;
                                if (wy < cLo[bit]) cLo[bit] = wy;
                                if (wy > cHi[bit]) cHi[bit] = wy;
                                if (wy < tmLoCol[bit]) tmLoCol[bit] = wy;
                                if (distributed) { dt3I.add(i); dt3J.add(j); dt3Y.add(wy); hasBlock[bit] = true; }
                            }
                            tmPerBlock = true;
                        }
                    } catch (Throwable ignored) { tmPerBlock = false; }
                }
                if (!tmPerBlock) the_beyond$rasterizeBox(piece.getBoundingBox(), oX, oZ, w, d, occ, gy, cLo, cHi);
                if (BeyondGenDiagnostics.loggedMaskPieces.add("TM@" + pe.getPosition())) {
                    BoundingBox tb = piece.getBoundingBox();
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] mask terrain_matching piece {} pos={} mode={} hits={} skipVoid={} topSolid=[{}..{}] assemblyBbox=[{},{},{}..{},{},{}]",
                            tmLoc, pe.getPosition(), tmPerBlock ? "perBlockSnapped" : "failsoft-bbox", tmHits, tmSkipVoid,
                            tmTopMin == Integer.MAX_VALUE ? 0 : tmTopMin, tmTopMax == Integer.MIN_VALUE ? 0 : tmTopMax,
                            tb.minX(), tb.minY(), tb.minZ(), tb.maxX(), tb.maxY(), tb.maxZ());
                }
                continue;
            }
            StructureTemplate t = null;
            String pieceLoc = "?";
            if (tm != null && el instanceof SinglePoolElement spe) {
                try {
                    var either = ((SinglePoolElementAccessor) (Object) spe).the_beyond$template();
                    pieceLoc = either.map(java.util.Objects::toString, st -> "inline");
                    t = either.map(tm::getOrCreate, st -> st);
                } catch (Throwable ignored) { t = null; }
            }
            int[] clippedBox = new int[1];
            int hits = the_beyond$rasterizeTemplate(t,
                    new StructurePlaceSettings().setRotation(pe.getRotation()), pe.getPosition(),
                    oX, oZ, w, d, occ, gy, cLo, cHi, distributed, dt3I, dt3J, dt3Y, hasBlock, clippedBox, false);
            if (buriedThere && t != null) {
                if (buriedInterior == null) buriedInterior = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
                the_beyond$addPieceInterior(t, new StructurePlaceSettings().setRotation(pe.getRotation()), pe.getPosition(),
                        piece.getBoundingBox(), buriedInterior);
            }
            if (t != null && baseBox != null && el.getProjection() == StructureTemplatePool.Projection.RIGID
                    && piece.getBoundingBox().getYSpan() >= ROOM_MIN_HEIGHT) {
                BoundingBox rb = piece.getBoundingBox();
                boolean byStart = rb.maxX() >= baseBox.minX() - BEARD_LAT_REACH && rb.minX() <= baseBox.maxX() + BEARD_LAT_REACH
                        && rb.maxZ() >= baseBox.minZ() - BEARD_LAT_REACH && rb.minZ() <= baseBox.maxZ() + BEARD_LAT_REACH;
                boolean underStart = rb.maxY() <= baseBox.minY() && !byStart;
                if (buriedThere || underStart) {
                    int marked = the_beyond$markRoomRoof(t, new StructurePlaceSettings().setRotation(pe.getRotation()),
                            pe.getPosition(), rb, oX, oZ, w, d, roomRoof);
                    if (marked > 0 && BeyondGenDiagnostics.loggedMaskPieces.add("ROOF@" + pe.getPosition())) {
                        com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] room roof cover {} roofY={} columns={} ({})",
                                pieceLoc, rb.maxY(), marked, buriedThere ? "buried there" : "under the start floor");
                    }
                }
            }
            boolean perBlock = hits >= 0;
            int clipped = clippedBox[0];
            if (!perBlock) {
                hits = 0;
                the_beyond$rasterizeBox(piece.getBoundingBox(), oX, oZ, w, d, occ, gy, cLo, cHi);
            }
            if (baseBox != null) {
                BoundingBox sb = piece.getBoundingBox();
                if (sb.minY() < baseBox.minY()
                        && sb.maxX() >= baseBox.minX() && sb.minX() <= baseBox.maxX()
                        && sb.maxZ() >= baseBox.minZ() && sb.minZ() <= baseBox.maxZ()
                        && sb.minY() < pedLo) {
                    pedLo = sb.minY();
                }
            }
            if (BeyondGenDiagnostics.loggedMaskPieces.add(pieceLoc + "@" + pe.getPosition())) {
                BoundingBox pb = piece.getBoundingBox();
                String depth = "";
                if (baseBox != null && pb.minY() < baseBox.minY()) {
                    boolean inFoot = pb.maxX() >= baseBox.minX() - BEARD_LAT_REACH && pb.minX() <= baseBox.maxX() + BEARD_LAT_REACH
                            && pb.maxZ() >= baseBox.minZ() - BEARD_LAT_REACH && pb.minZ() <= baseBox.maxZ() + BEARD_LAT_REACH;
                    depth = inFoot ? " SUB-FLOOR(in base footprint; covered)"
                                   : " SUB-FLOOR-OFFSET(outside footprint; predicted EXPOSED by no-burial carve)";
                }
                com.thebeyond.TheBeyond.LOGGER.debug(
                        "[Beyond] piece {} rot={} pos={} bbox=[{},{},{}..{},{},{}] mode={} hits={} clipped={} startFloor={}{} islandTop={}",
                        pieceLoc, pe.getRotation(), pe.getPosition(),
                        pb.minX(), pb.minY(), pb.minZ(), pb.maxX(), pb.maxY(), pb.maxZ(),
                        perBlock ? "perBlock" : "failsoft-bbox", hits, clipped,
                        baseBox != null ? baseBox.minY() : Integer.MIN_VALUE, depth,
                        islandTop == Integer.MIN_VALUE ? "-" : islandTop + (islandTop - pb.minY() >= PIECE_SINK ? " (buried there)" : " (stands there)"));
            }
        }
        long[] corrOcc = null;
        if (connectDetached) {
            List<BoundingBox> pieceBoxes = new ArrayList<>(start.getPieces().size());
            for (StructurePiece piece : start.getPieces()) pieceBoxes.add(piece.getBoundingBox());
            corrOcc = new long[((w * d) + 63) >> 6];
            int added = the_beyond$connectDetached(pieceBoxes, occ, cLo, cHi, w, d, gy, oX, oZ, corrOcc);
            if (added > 0 && BeyondGenDiagnostics.loggedMaskKeys.add("corridor@" + oX + "," + oZ)) {
                com.thebeyond.TheBeyond.LOGGER.debug(
                        "[Beyond] detached-part corridor: {} columns added (half-width {}) mask origin=[{},{}] size={}x{}",
                        added, CORRIDOR_HALF_WIDTH, oX, oZ, w, d);
            }
        }
        int gLo = gy[0], gHi = gy[1];
        boolean any = false;
        for (long word : occ) if (word != 0) { any = true; break; }
        if (!any) {
            for (int i = 0, cells = w * d; i < cells; i++) occ[i >> 6] |= (1L << (i & 63));
            gLo = env.minY(); gHi = env.maxY();
            for (int i = 0, cells = w * d; i < cells; i++) { cLo[i] = gLo; cHi[i] = gHi; }
        }
        long[] filledOcc = new long[((w * d) + 63) >> 6];
        int filled = CarveMask.fillEnclosed(occ, filledOcc, w, d, cLo, cHi, gLo, gHi, CLOSE_RADIUS);
        short[] colLoOff = new short[w * d], colHiOff = new short[w * d];
        CarveMask.encodeColumnSpans(occ, cLo, cHi, gLo, colLoOff, colHiOff, w * d);
        byte[] dt3 = null; int dt3OX = 0, dt3OZ = 0, dt3W = 0, dt3D = 0, dt3YLo = 0, dt3Layers = 0;
        if (distributed) {
            dt3OX = oX - DT3_CAP; dt3OZ = oZ - DT3_CAP; dt3W = w + 2 * DT3_CAP; dt3D = d + 2 * DT3_CAP;
            dt3YLo = gLo - DT3_CAP; dt3Layers = (gHi - gLo) + 1 + 2 * DT3_CAP;
            dt3 = CarveMask.buildDt3(w, d, gLo, gHi, dt3W, dt3D, dt3YLo, dt3Layers, dt3I, dt3J, dt3Y, occ, cLo, cHi, hasBlock);
            if (dt3 == null) { dt3OX = 0; dt3OZ = 0; dt3W = 0; dt3D = 0; dt3YLo = 0; dt3Layers = 0; }
        }
        CarveMask built = new CarveMask(oX, oZ, w, d, occ, colLoOff, colHiOff, filledOcc, gLo, gHi, seated, distributed, layerDistributed, foundationDepth, filled,
                dt3, dt3OX, dt3OZ, dt3W, dt3D, dt3YLo, dt3Layers, baseBox);
        built.corridorOcc = corrOcc;
        built.pedLoY = pedLo;
        if (baseBox != null) {
            ForeignFit.Course course = ForeignFit.lowestCourse(start.getPieces().get(0), tm);
            if (course != null) {
                long[] gf = new long[((w * d) + 63) >> 6], gr = new long[gf.length];
                for (long k : course.cols()) {
                    int i = (int) (k >> 32) - oX, j = (int) k - oZ;
                    if (i < 0 || i >= w || j < 0 || j >= d) continue;
                    int bit = i + j * w;
                    gf[bit >> 6] |= 1L << (bit & 63);
                    if (course.raised().contains(k)) gr[bit >> 6] |= 1L << (bit & 63);
                }
                built.groundFloorY = course.y();
                built.groundRaised = course.raised().isEmpty() ? null : gr;
                built.groundFoot = gf;
            }
        }
        built.boxTop = boxTop;
        built.boxBot = boxBot;
        built.pieceBoxes = pieceBoxList.toArray(new BoundingBox[0]);
        if (liftTop != boxTop) { built.liftBoxTop = liftTop; built.liftBoxBot = liftBot; }
        built.buriedInterior = buriedInterior;
        built.roomRoof = roomRoof[0];
        built.floorOff = the_beyond$pieceFloors(farFloors, tmLoCol, occ, cLo, oX, oZ, w, d, gLo, baseBox);
        if (built.floorOff != null && com.thebeyond.TheBeyond.LOGGER.isDebugEnabled()
                && BeyondGenDiagnostics.loggedMaskKeys.add("floors@" + oX + "," + oZ)) {
            int moved = 0;
            for (short f : built.floorOff) if (f != 0) moved++;
            com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] piece floors origin=[{},{}] columns off the start floor={} buried-elsewhere pieces={}",
                    oX, oZ, moved, farFloors == null ? 0 : farFloors.size());
        }
        return built;
    }

    static final int PIECE_LAYER_BAND = 16;
    /** Depth below its island's surface at which a piece counts as buried there, more than a house on a slope sinks. */
    static final int PIECE_SINK = 3;
    /** How far above its own surface the island may rise to close on a buried piece's walls. */
    static final int PIECE_RISE = 3;
    /** Courses of ground laid over a buried room's roof. One is what the biome needs to paint it as ground. */
    static final int ROOF_COVER = 1;
    private static final int ROOF_MAX_THICK = 4;
    /** Shortest piece that can be a room: anything lower is a slab or a step, not a space to cover. */
    static final int ROOM_MIN_HEIGHT = 4;

    static int the_beyond$roofCapTop(CarveMask m, int x, int z, boolean base, int hi) {
        if (m == null || base || m.baseBox == null || m.floating || m.carveOnly || m.hugTerrain) return Integer.MIN_VALUE;
        int roof = m.roomRoofAt(x, z);
        if (roof == Integer.MIN_VALUE || roof != hi) return Integer.MIN_VALUE;
        int floor = m.floorFor(x, z, false);
        boolean under = m.hasOwnFloor(x, z) ? hi < floor : hi <= floor;
        return under ? hi + ROOF_COVER : Integer.MIN_VALUE;
    }

    private static int the_beyond$markRoomRoof(StructureTemplate t, StructurePlaceSettings st, BlockPos pos, BoundingBox bb,
            int oX, int oZ, int w, int d, int[][] roomRoof) {
        List<StructureTemplate.Palette> pals;
        try { pals = ((StructureTemplateAccessor) (Object) t).the_beyond$palettes(); } catch (Throwable e) { return 0; }
        if (pals.isEmpty()) return 0;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet inner = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        the_beyond$addPieceInterior(t, st, pos, bb, inner);
        if (inner.isEmpty()) return 0;
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap hollow = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        hollow.defaultReturnValue(Integer.MIN_VALUE);
        for (long c : inner) {
            long col = BlockPos.asLong(BlockPos.getX(c), 0, BlockPos.getZ(c));
            if (BlockPos.getY(c) > hollow.get(col)) hollow.put(col, BlockPos.getY(c));
        }
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap top = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        top.defaultReturnValue(Integer.MIN_VALUE);
        for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
            if (info.state().isAir() || info.state().is(Blocks.STRUCTURE_VOID)) continue;
            BlockPos wp = StructureTemplate.calculateRelativePosition(st, info.pos()).offset(pos);
            long c = BlockPos.asLong(wp.getX(), 0, wp.getZ());
            if (wp.getY() > top.get(c)) top.put(c, wp.getY());
        }
        int marked = 0;
        for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : top.long2IntEntrySet()) {
            int roofY = e.getIntValue();
            // Highest course or a lower one still closing a room, so stepped roofs are not left bare.
            int ceiling = hollow.get(e.getLongKey());
            boolean closes = ceiling != Integer.MIN_VALUE && roofY > ceiling && roofY - ceiling <= ROOF_MAX_THICK;
            if (roofY != bb.maxY() && !closes) continue;
            int x = BlockPos.getX(e.getLongKey()), z = BlockPos.getZ(e.getLongKey());
            int i = x - oX, j = z - oZ;
            if (i < 0 || i >= w || j < 0 || j >= d) continue;
            if (roomRoof[0] == null) { roomRoof[0] = new int[w * d]; java.util.Arrays.fill(roomRoof[0], Integer.MIN_VALUE); }
            // Stacked rooms share columns, and only the uppermost roof is the column's top that the cover sits on.
            if (roofY > roomRoof[0][i + j * w]) roomRoof[0][i + j * w] = roofY;
            marked++;
        }
        return marked;
    }

    private static short[] the_beyond$pieceFloors(List<int[]> farFloors, int[] tmLo, long[] occ, int[] cLo,
            int oX, int oZ, int w, int d, int gLo, BoundingBox baseBox) {
        if (baseBox == null || (farFloors == null && tmLo == null)) return null;
        short[] off = null;
        if (farFloors != null) {
            // Lowest piece last, so it owns the column whose bottom it supplies.
            farFloors.sort((a, b) -> Integer.compare(b[4], a[4]));
            for (int[] f : farFloors) {
                for (int z = Math.max(f[1], oZ); z <= Math.min(f[3], oZ + d - 1); z++) {
                    for (int x = Math.max(f[0], oX); x <= Math.min(f[2], oX + w - 1); x++) {
                        int bit = (x - oX) + (z - oZ) * w;
                        if ((occ[bit >> 6] & (1L << (bit & 63))) == 0 || cLo[bit] < f[4] || cLo[bit] > f[5]) continue;
                        if (off == null) off = new short[w * d];
                        off[bit] = (short) Math.max(1, Math.min(Short.MAX_VALUE, f[6] - gLo + 1));
                    }
                }
            }
        }
        if (tmLo != null) {
            // A terrain_matching piece at the bottom of its column stands on its own deck, not in a room under the start floor.
            for (int bit = 0, n = w * d; bit < n; bit++) {
                if (tmLo[bit] > cLo[bit] || cLo[bit] >= baseBox.minY()) continue;
                if ((occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                if (off == null) off = new short[w * d];
                off[bit] = (short) Math.max(1, Math.min(Short.MAX_VALUE, cLo[bit] - gLo + 1));
            }
        }
        return off;
    }

    private static boolean[] the_beyond$surfacePlaced(List<StructurePiece> pieces) {
        int n = pieces.size();
        boolean[] out = new boolean[n];
        for (int c = 1; c < n; c++) {
            if (!(pieces.get(c) instanceof PoolElementStructurePiece pe) || pe.getJunctions().isEmpty()) continue;
            JigsawJunction up = pe.getJunctions().get(0);
            if (up.getDestProjection() != StructureTemplatePool.Projection.RIGID
                    || the_beyond$unwrapPoolElement(pe.getElement()).getProjection() != StructureTemplatePool.Projection.RIGID) {
                out[c] = true;
                continue;
            }
            BoundingBox cb = pe.getBoundingBox();
            for (int p = c - 1; p >= 0; p--) {
                BoundingBox pb = pieces.get(p).getBoundingBox();
                if (up.getSourceX() >= pb.minX() && up.getSourceX() <= pb.maxX()
                        && up.getSourceZ() >= pb.minZ() && up.getSourceZ() <= pb.maxZ()
                        && pb.maxY() + 1 >= cb.minY() && pb.minY() - 1 <= cb.maxY()) {
                    out[c] = out[p];
                    break;
                }
            }
        }
        return out;
    }

    /** Cells a buried piece's walls enclose, jigsaws closing the doorways, so its shafts and rooms do not keep island rock. */
    private static void the_beyond$addPieceInterior(StructureTemplate t, StructurePlaceSettings st, BlockPos pos,
            BoundingBox bb, it.unimi.dsi.fastutil.longs.LongOpenHashSet out) {
        List<StructureTemplate.Palette> pals;
        try { pals = ((StructureTemplateAccessor) (Object) t).the_beyond$palettes(); } catch (Throwable e) { return; }
        if (pals.isEmpty()) return;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet walls = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
            if (info.state().isAir() || info.state().is(Blocks.STRUCTURE_VOID)) continue;
            BlockPos wp = StructureTemplate.calculateRelativePosition(st, info.pos()).offset(pos);
            walls.add(BlockPos.asLong(wp.getX(), wp.getY(), wp.getZ()));
        }
        int x0 = bb.minX() - 1, z0 = bb.minZ() - 1, wd = bb.getXSpan() + 2, dp = bb.getZSpan() + 2;
        boolean[] reached = new boolean[wd * dp];
        int[] stack = new int[wd * dp];
        for (int y = bb.minY(); y <= bb.maxY(); y++) {
            java.util.Arrays.fill(reached, false);
            int sp = 0;
            for (int i = 0; i < wd * dp; i++) {
                int ix = i % wd, iz = i / wd;
                if (ix == 0 || iz == 0 || ix == wd - 1 || iz == dp - 1) { reached[i] = true; stack[sp++] = i; }
            }
            while (sp > 0) {
                int c = stack[--sp], ix = c % wd, iz = c / wd;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = ix + (dir == 0 ? 1 : dir == 1 ? -1 : 0), nz = iz + (dir == 2 ? 1 : dir == 3 ? -1 : 0);
                    if (nx < 0 || nz < 0 || nx >= wd || nz >= dp) continue;
                    int n = nx + nz * wd;
                    if (reached[n] || walls.contains(BlockPos.asLong(x0 + nx, y, z0 + nz))) continue;
                    reached[n] = true;
                    stack[sp++] = n;
                }
            }
            for (int i = 0; i < wd * dp; i++) {
                if (reached[i]) continue;
                long key = BlockPos.asLong(x0 + i % wd, y, z0 + i / wd);
                if (!walls.contains(key)) out.add(key);
            }
        }
    }

    private static int the_beyond$footprintNatTop(BoundingBox b) {
        BeyondEndChunkGenerator.ColumnScratch probe = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
        int yHi = b.maxY() + PIECE_LAYER_BAND, yLo = b.minY() - PIECE_LAYER_BAND;
        IntArrayList tops = new IntArrayList();
        int samples = 0;
        for (int x = b.minX(); x <= b.maxX(); x += 2) {
            for (int z = b.minZ(); z <= b.maxZ(); z += 2) {
                samples++;
                BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), probe);
                int y = b.maxY();
                if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, probe)) {
                    while (y < yHi && BeyondEndChunkGenerator.isSolidTerrainScratch(y + 1, probe)) y++;
                    tops.add(y);
                } else {
                    while (y > yLo && !BeyondEndChunkGenerator.isSolidTerrainScratch(y, probe)) y--;
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, probe)) tops.add(y);
                }
            }
        }
        if (tops.size() * 2 <= samples) return Integer.MIN_VALUE;
        int[] sorted = tops.toIntArray();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static final java.util.concurrent.ConcurrentMap<Class<?>, java.util.Optional<java.lang.reflect.Method>>
            the_beyond$delegateMethods = new java.util.concurrent.ConcurrentHashMap<>();

    /** guard bounds the loop so a cyclic delegate() chain can't hang generation. */
    static StructurePoolElement the_beyond$unwrapPoolElement(StructurePoolElement el) {
        for (int guard = 0; guard < 8 && el != null; guard++) {
            java.lang.reflect.Method m = the_beyond$delegateMethods.computeIfAbsent(el.getClass(), c -> {
                for (String name : new String[]{"delegate", "getDelegate"}) {
                    try {
                        java.lang.reflect.Method cand = c.getMethod(name);
                        if (StructurePoolElement.class.isAssignableFrom(cand.getReturnType())) {
                            try { cand.setAccessible(true); } catch (Throwable ignored) {}
                            return java.util.Optional.of(cand);
                        }
                    } catch (NoSuchMethodException ignored) {}
                }
                return java.util.Optional.empty();
            }).orElse(null);
            if (m == null) break;
            try {
                Object inner = m.invoke(el);
                if (inner instanceof StructurePoolElement spe && spe != el) { el = spe; continue; }
            } catch (Throwable ignored) {}
            break;
        }
        return el;
    }

    private static int the_beyond$rasterizeTemplate(
            StructureTemplate t, StructurePlaceSettings settings, BlockPos origin,
            int oX, int oZ, int w, int d, long[] occ, int[] gy, int[] cLo, int[] cHi,
            boolean distributed, IntArrayList dt3I, IntArrayList dt3J, IntArrayList dt3Y, boolean[] hasBlock,
            int[] clippedOut, boolean includeAir) {
        if (t == null) return -1;
        try {
            List<StructureTemplate.Palette> pals =
                    ((StructureTemplateAccessor) (Object) t).the_beyond$palettes();
            if (pals.isEmpty()) return -1;
            int hits = 0, clipped = 0;
            for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
                if (!includeAir && info.state().isAir()) continue;
                BlockPos wp = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
                int i = wp.getX() - oX, j = wp.getZ() - oZ;
                if (i < 0 || i >= w || j < 0 || j >= d) { clipped++; continue; }
                int bit = i + j * w;
                occ[bit >> 6] |= (1L << (bit & 63));
                hits++;
                int wy = wp.getY();
                if (wy < gy[0]) gy[0] = wy;
                if (wy > gy[1]) gy[1] = wy;
                if (wy < cLo[bit]) cLo[bit] = wy;
                if (wy > cHi[bit]) cHi[bit] = wy;
                if (distributed) { dt3I.add(i); dt3J.add(j); dt3Y.add(wy); hasBlock[bit] = true; }
            }
            if (clippedOut != null) clippedOut[0] = clipped;
            return hits;
        } catch (Throwable t2) {
            if (!BeyondGenDiagnostics.loggedMaskTemplateError) {
                BeyondGenDiagnostics.loggedMaskTemplateError = true;
                com.thebeyond.TheBeyond.LOGGER.warn(
                        "[Beyond] template rasterisation failed, mask falls back to bounding boxes: {}", t2.toString());
            }
            return -1;
        }
    }

    static final int CORRIDOR_HALF_WIDTH = 2;
    /** EndCityPieces joins pieces within 11 blocks of their parent, but the ship at 61-70, so this gap is a real detachment. */
    static final int CORRIDOR_MIN_DETACH = 24;
    /** Bounds on the swept section, so a pathological piece box cannot open a hangar through the island. */
    static final int CORRIDOR_MAX_HALF_WIDTH = 8;
    static final double CORRIDOR_WANDER = 2.5;
    static final double CORRIDOR_BREATHE = 1.6;
    private static final double CORRIDOR_NOISE_FREQ = 0.21;

    static double the_beyond$corridorNoise(int x, int z) {
        return BeyondEndChunkGenerator.simplexNoise == null ? 0.0
                : BeyondEndChunkGenerator.simplexNoise.getValue(
                        x * CORRIDOR_NOISE_FREQ - 4409.0, 0.0, z * CORRIDOR_NOISE_FREQ + 1187.0);
    }
    static final int CORRIDOR_MAX_HEIGHT = 32;
    static final int CORRIDOR_ANCHOR_TRIES = 3;

    static volatile String CORRIDOR_LAST_SKIP;

    private static int the_beyond$clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    @VisibleForTesting
    static double the_beyond$boxGap(BoundingBox a, BoundingBox b) {
        long gx = Math.max(0, Math.max(a.minX() - b.maxX(), b.minX() - a.maxX()));
        long gy = Math.max(0, Math.max(a.minY() - b.maxY(), b.minY() - a.maxY()));
        long gz = Math.max(0, Math.max(a.minZ() - b.maxZ(), b.minZ() - a.maxZ()));
        return Math.sqrt((double) (gx * gx + gy * gy + gz * gz));
    }

    /** Index of the piece nearest {@code self} in 3-D, or -1 if alone. Plan view would pick a tower far under the ship. */
    @VisibleForTesting
    static int the_beyond$nearestPiece(List<BoundingBox> boxes, int self) {
        return the_beyond$nearestPiece(boxes, self, null);
    }

    @VisibleForTesting
    static int the_beyond$nearestPiece(List<BoundingBox> boxes, int self, boolean[] skip) {
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            if (i == self || (skip != null && skip[i])) continue;
            double g = the_beyond$boxGap(boxes.get(self), boxes.get(i));
            if (g < bestGap) { bestGap = g; best = i; }
        }
        return best;
    }

    private static boolean the_beyond$inPlan(BoundingBox b, int x, int z) {
        return x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ();
    }

    @VisibleForTesting
    static int the_beyond$connectDetached(List<BoundingBox> boxes, long[] occ, int[] cLo, int[] cHi,
            int w, int d, int[] gy, int oX, int oZ, long[] corr) {
        if (boxes == null || boxes.size() < 2) return 0;
        int added = 0, runs = 0;
        String reason = "no piece detached by >= " + CORRIDOR_MIN_DETACH + " blocks";
        boolean[] tried = new boolean[boxes.size()];
        String[] why = new String[1];
        for (int i = 0; i < boxes.size(); i++) {
            java.util.Arrays.fill(tried, false);
            for (int attempt = 0; attempt < CORRIDOR_ANCHOR_TRIES; attempt++) {
                int anchor = the_beyond$nearestPiece(boxes, i, tried);
                if (anchor < 0) break;
                tried[anchor] = true;
                double gap = the_beyond$boxGap(boxes.get(i), boxes.get(anchor));
                // The nearest piece decides: once in reach the piece is jointed and a corridor would trench it.
                if (gap < CORRIDOR_MIN_DETACH) break;
                why[0] = null;
                int c = the_beyond$carveRun(boxes.get(i), boxes.get(anchor), occ, cLo, cHi, w, d, gy, oX, oZ, i, gap, corr, why);
                if (why[0] == null) { added += c; runs++; break; }
                if (runs == 0) reason = why[0];
            }
        }
        CORRIDOR_LAST_SKIP = runs == 0 ? reason : null;
        if (runs == 0 && BeyondGenDiagnostics.loggedMaskKeys.add("corridor-none@" + oX + "," + oZ)) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] corridor NONE: {} pieces, {}, mask origin=[{},{}]",
                    boxes.size(), reason, oX, oZ);
        }
        return added;
    }

    /** One run, flat at the detached piece's floor, since ship and bridge_end sit at the same y off the last bridge. */
    private static int the_beyond$carveRun(BoundingBox det, BoundingBox anc, long[] occ, int[] cLo, int[] cHi,
            int w, int d, int[] gy, int oX, int oZ, int idx, double gap, long[] corr, String[] why) {
        int bx = the_beyond$clamp((det.minX() + det.maxX()) / 2, anc.minX(), anc.maxX());
        int bz = the_beyond$clamp((det.minZ() + det.maxZ()) / 2, anc.minZ(), anc.maxZ());
        int ax = the_beyond$clamp(bx, det.minX(), det.maxX());
        int az = the_beyond$clamp(bz, det.minZ(), det.maxZ());
        int steps = Math.max(Math.abs(ax - bx), Math.abs(az - bz));
        if (steps <= 1) {
            why[0] = "piece#" + idx + " zero-length run: the anchor is stacked on it, not beside it";
            return 0;
        }
        // The detached piece's own section, untapered: the gap reads as the volume the ship left behind.
        boolean alongZ = Math.abs(az - bz) >= Math.abs(ax - bx);
        int halfW = Math.max(CORRIDOR_HALF_WIDTH,
                ((alongZ ? det.getXSpan() : det.getZSpan()) - 1) / 2);
        if (halfW > CORRIDOR_MAX_HALF_WIDTH) halfW = CORRIDOR_MAX_HALF_WIDTH;
        int lo = det.minY();
        int hi = Math.min(det.maxY(), lo + CORRIDOR_MAX_HEIGHT - 1);
        int added = 0;
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            int px = (int) Math.round(ax + (bx - ax) * t), pz = (int) Math.round(az + (bz - az) * t);
            // Wander and breathing share the occupancy grow's field so both agree, clamped so the run never pinches shut.
            double wander = the_beyond$corridorNoise(px, pz);
            int off = (int) Math.round(CORRIDOR_WANDER * wander);
            if (alongZ) px += off; else pz += off;
            int stepHalf = halfW + (int) Math.round(CORRIDOR_BREATHE * the_beyond$corridorNoise(pz, px));
            if (stepHalf < CORRIDOR_HALF_WIDTH) stepHalf = CORRIDOR_HALF_WIDTH;
            if (stepHalf > CORRIDOR_MAX_HALF_WIDTH) stepHalf = CORRIDOR_MAX_HALF_WIDTH;
            for (int oz = -stepHalf; oz <= stepHalf; oz++) {
                for (int ox = -stepHalf; ox <= stepHalf; ox++) {
                    int qx = px + ox - oX, qz = pz + oz - oZ;
                    if (qx < 0 || qx >= w || qz < 0 || qz >= d) continue;
                    int bit = qx + qz * w;
                    // Only invented columns are corridor, so the tunnel mouth keeps the structure's own contour.
                    if ((occ[bit >> 6] & (1L << (bit & 63))) == 0) {
                        added++;
                        if (corr != null) corr[bit >> 6] |= (1L << (bit & 63));
                    }
                    occ[bit >> 6] |= (1L << (bit & 63));
                    if (lo < cLo[bit]) cLo[bit] = lo;
                    if (hi > cHi[bit]) cHi[bit] = hi;
                }
            }
        }
        if (lo < gy[0]) gy[0] = lo;
        if (hi > gy[1]) gy[1] = hi;
        if (BeyondGenDiagnostics.loggedMaskKeys.add("corridor-run@" + oX + "," + oZ + "#" + idx)) {
            int rock = Integer.MIN_VALUE;
            try {
                if (BeyondEndChunkGenerator.simplexNoise != null)
                    rock = the_beyond$topmostNatSolid((ax + bx) / 2, (az + bz) / 2);
            } catch (Throwable ignored) {}
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[Beyond] corridor run piece#{} [{},{}] y=[{}..{}] -> anchor [{},{}] y=[{}..{}] gap={} steps={} section={}x{} clear=[{}..{}] added={} midRockTop={}",
                    idx, ax, az, det.minY(), det.maxY(), bx, bz, anc.minY(), anc.maxY(),
                    (int) Math.round(gap), steps, 2 * halfW + 1, hi - lo + 1, lo, hi, added, rock);
        }
        return added;
    }

    private static void the_beyond$rasterizeBox(BoundingBox b, int oX, int oZ, int w, int d,
            long[] occ, int[] gy, int[] cLo, int[] cHi) {
        if (b.minY() < gy[0]) gy[0] = b.minY();
        if (b.maxY() > gy[1]) gy[1] = b.maxY();
        for (int wz = b.minZ(); wz <= b.maxZ(); wz++) {
            for (int wx = b.minX(); wx <= b.maxX(); wx++) {
                int i = wx - oX, j = wz - oZ;
                if (i < 0 || i >= w || j < 0 || j >= d) continue;
                int bit = i + j * w;
                occ[bit >> 6] |= (1L << (bit & 63));
                if (b.minY() < cLo[bit]) cLo[bit] = b.minY();
                if (b.maxY() > cHi[bit]) cHi[bit] = b.maxY();
            }
        }
    }

    private static int the_beyond$maskCellCount(CarveMask m) {
        int c = 0;
        for (long word : m.occ) c += Long.bitCount(word);
        return c;
    }

    private static int the_beyond$topmostNatSolid(int x, int z) {
        BeyondEndChunkGenerator.ColumnScratch probe = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
        float dist = (float) Math.sqrt((double) x * x + (double) z * z);
        BeyondEndChunkGenerator.initColumnScratch(x, z, dist, probe);
        for (int y = BeyondTerrainState.getDimMaxY() - 33; y > BeyondTerrainState.getDimMinY() + 32; y--) {
            if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, probe)) return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean the_beyond$bboxNearChunk(BoundingBox b, ChunkPos cp) {
        int r = CARVE_SCAN_REACH;
        return b.maxX() >= cp.getMinBlockX() - r && b.minX() <= cp.getMaxBlockX() + r
            && b.maxZ() >= cp.getMinBlockZ() - r && b.minZ() <= cp.getMaxBlockZ() + r;
    }

    /** GUARD band: carve removes a subset of it, so it stays a superset and nothing floats. */
    public static int the_beyond$carveOutsideDist(List<CarveMask> masks, int x, int y, int z) {
        return the_beyond$outsideDist(masks, x, y, z, false);
    }
    public static int the_beyond$guardOutsideDist(List<CarveMask> masks, int x, int y, int z) {
        return the_beyond$outsideDist(masks, x, y, z, true);
    }
    private static int the_beyond$outsideDist(List<CarveMask> masks, int x, int y, int z, boolean guard) {
        int best = CARVE_ERODE_REACH + 1;
        final int reach = CARVE_SCAN_REACH;
        final int hWarp = the_beyond$bandWarp(x, y, z);
        for (int m = 0, n = masks.size(); m < n; m++) {
            CarveMask mask = masks.get(m);
            if (x < mask.oX - reach || x > mask.oX + mask.w - 1 + reach
             || z < mask.oZ - reach || z > mask.oZ + mask.d - 1 + reach) continue;
            boolean seated = mask.seated; int gLo = mask.gLo;
            for (int dz = -reach; dz <= reach; dz++) {
                int zz = z + dz - mask.oZ;
                if (zz < 0 || zz >= mask.d) continue;
                int rowBase = zz * mask.w;
                for (int dx = -reach; dx <= reach; dx++) {
                    int xx = x + dx - mask.oX;
                    if (xx < 0 || xx >= mask.w) continue;
                    int bit = xx + rowBase;
                    if ((mask.occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                    int dXZ = Math.max(Math.abs(dx), Math.abs(dz));
                    if (dXZ - (FLOAT_HMARGIN + WARP_MAX) >= best) continue;
                    int dd = the_beyond$carveDistAt(dXZ, mask.colLo(bit), mask.colHi(bit), seated, mask.isFilled(bit), mask.distributed, gLo, hWarp, guard, y);
                    if (dd == 0) return 0;
                    if (dd < best) best = dd;
                }
            }
        }
        return best;
    }

    private static boolean the_beyond$inSubFloorEnvelope(List<CarveMask> masks,
            BeyondEndChunkGenerator.CarveBits cb, int nBits, int x, int y, int z) {
        for (int k = 0; k < nBits; k++) {
            if (!cb.dist[k] || cb.dxz[k] > ENVELOPE_LAT) continue;
            CarveMask mk = cb.mask[k];
            if (mk == null || mk.baseBox == null) continue;
            int floor = mk.floorFor(cb.colX[k], cb.colZ[k], cb.base[k]);
            if (cb.lo[k] >= floor) continue;
            int envV = y > cb.hi[k] ? y - cb.hi[k] : (y < cb.lo[k] ? cb.lo[k] - y : 0);
            if (cb.dxz[k] == 0 && envV == 0) continue;
            int top = cb.capTop[k] != Integer.MIN_VALUE ? cb.capTop[k] : Math.min(cb.hi[k], floor - 1);
            if (y > top || envV > ENVELOPE_DOWN) continue;
            boolean inBox = false;
            for (int mi = 0; mi < masks.size() && !inBox; mi++) inBox = masks.get(mi).insideLiftBoxAt(x, y, z);
            if (!inBox) return true;
        }
        return false;
    }

    static boolean the_beyond$anyFloating(BeyondEndChunkGenerator.CarveBits cb, int nBits) {
        for (int k = 0; k < nBits; k++) if (cb.mask[k] != null && cb.mask[k].floating) return true;
        return false;
    }

    public static boolean the_beyond$carveRemovedAirAt(List<CarveMask> masks, int x, int y, int z) {
        BeyondEndChunkGenerator.CarveBits cb = BeyondEndChunkGenerator.CARVE_BITS.get();
        int nBits = 0;
        for (int mi = 0, n = masks.size(); mi < n; mi++) {
            CarveMask mask = masks.get(mi);
            if (x < mask.oX - CARVE_SCAN_REACH || x > mask.oX + mask.w - 1 + CARVE_SCAN_REACH
             || z < mask.oZ - CARVE_SCAN_REACH || z > mask.oZ + mask.d - 1 + CARVE_SCAN_REACH) continue;
            cb.ensure(nBits + (2 * CARVE_SCAN_REACH + 1) * (2 * CARVE_SCAN_REACH + 1));
            nBits = mask.collectOccupiedBits(x, z, CARVE_SCAN_REACH, cb.dxz, cb.lo, cb.hi, cb.gLo, cb.seat, cb.filled, cb.dist, cb.base, cb.mask, cb.colX, cb.colZ, cb.d2, cb.corr, nBits);
        }
        if (nBits == 0) return false;
        for (int mi = 0, n = masks.size(); mi < n; mi++) if (masks.get(mi).inBuriedInterior(x, y, z)) return true;
        final boolean wide = the_beyond$anyFloating(cb, nBits);
        double warpRaw = the_beyond$carveWarp(x, y, z, wide);
        double warp = CARVE_WARP_AMT * warpRaw;
        double roughNoise = the_beyond$faceRough(x, y, z);
        int wx = (int) Math.round(x + DT3_WARP_AMP * warpRaw);
        int wy = (int) Math.round(y + DT3_WARP_AMP * the_beyond$carveWarp(x + 131, y + 57, z + 911, wide));
        int wz = (int) Math.round(z + DT3_WARP_AMP * the_beyond$carveWarp(x + 877, y + 401, z + 283, wide));
        for (int k = 0; k < nBits; k++)
            cb.d3[k] = (cb.dist[k] && cb.mask[k] != null) ? cb.mask[k].dt3At(wx, wy, wz) : DT3_CAP + 1;
        for (int k = 0; k < nBits; k++) {
            cb.capTop[k] = the_beyond$roofCapTop(cb.mask[k], cb.colX[k], cb.colZ[k], cb.base[k], cb.hi[k]);
            if (cb.dxz[k] == 0 && cb.capTop[k] != Integer.MIN_VALUE && y > cb.hi[k] && y <= cb.capTop[k]) return false;
        }
        // The envelope too, or the veto reads the cover as carved air and it keeps the default block.
        if (the_beyond$inSubFloorEnvelope(masks, cb, nBits, x, y, z)) return false;
        BeyondEndChunkGenerator.ColumnScratch s = BeyondEndChunkGenerator.SCRATCH.get();
        float dist = (float) Math.sqrt((double) x * x + (double) z * z);
        BeyondEndChunkGenerator.initColumnScratch(x, z, dist, s);
        // Per column like the live carve, since a sample over nothing must not count as held up by rock.
        boolean rockBelow;
        { int run = 0; for (int yy = y - 1; run < FOUNDATION_MIN_RUN; yy--) { if (!BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s)) break; run++; } rockBelow = run >= FOUNDATION_MIN_RUN; }
        int[] run = the_beyond$natRun(y, s);
        if (wide) run[0] = Integer.MIN_VALUE;
        double p = the_beyond$carvePenalty(cb.dxz, cb.lo, cb.hi, cb.gLo, cb.seat, cb.filled, cb.dist, nBits, y, warp, cb.d3, rockBelow, roughNoise, cb.base, null, null, cb.corr, the_beyond$spanGrow(x, y, z), false, null, cb.d2, null, cb.capTop, run[0], run[1]);
        if (p == Double.POSITIVE_INFINITY) return true;
        if (p <= 0.0) return false;
        double density = BeyondEndChunkGenerator.getTerrainDensityScratch(y, s);
        return (density - p) <= s.threshold;
    }

    static final class ColumnCarveState {
        final int globalX, globalZ;
        final BeyondEndChunkGenerator.CarveBits bits;
        final int nBits, nFnd, nLip;
        final int[] aFndGLo, aFndNatTop, aLipNatTop, aLipTop;
        final int beardYLo, beardYHi;
        final int spanGrow;   // the height-independent sample, kept for the logs
        final boolean anyDist;
        final boolean anyFloat;
        final int carveYLo, carveYHi;

        private ColumnCarveState(int globalX, int globalZ, BeyondEndChunkGenerator.CarveBits bits,
                int nBits, int nFnd, int nLip, int[] aFndGLo, int[] aFndNatTop, int[] aLipNatTop, int[] aLipTop,
                int beardYLo, int beardYHi, boolean anyDist, boolean anyFloat, int carveYLo, int carveYHi) {
            this.globalX = globalX; this.globalZ = globalZ; this.bits = bits;
            this.nBits = nBits; this.nFnd = nFnd; this.nLip = nLip;
            this.aFndGLo = aFndGLo; this.aFndNatTop = aFndNatTop; this.aLipNatTop = aLipNatTop; this.aLipTop = aLipTop;
            this.beardYLo = beardYLo; this.beardYHi = beardYHi; this.anyDist = anyDist; this.anyFloat = anyFloat;
            this.spanGrow = the_beyond$spanGrow(globalX, globalZ);
            this.carveYLo = carveYLo; this.carveYHi = carveYHi;
        }

        int deckY = Integer.MAX_VALUE;
        int pedLoY = Integer.MAX_VALUE, pedHiY = Integer.MIN_VALUE;

        boolean pedestalBandAt(int y) {
            return pedLoY != Integer.MAX_VALUE && y >= pedLoY && y <= pedHiY;
        }
        private int lastPlaced = Integer.MIN_VALUE;

        void notePlaced(int y) {
            if (y > lastPlaced) lastPlaced = y;
        }

        /** Anchored on the course actually placed below, so the deck is only ever finished off real ground. */
        boolean deckFillAt(int y) {
            return deckY != Integer.MAX_VALUE && y <= deckY && y > deckY - DECK_FILL_REACH
                    && lastPlaced == y - 1;
        }

        int[] planLo, planHi, planSole;
        CarveMask[] planOwner;
        int nPlan;

        /** Forced before the carve, the plan is kept out of other structures' pieces, and out of its own box from the sole up. */
        private boolean planLays(int i, int y) {
            if (y < planLo[i] || y > planHi[i]) return false;
            if (y >= planSole[i] && planOwner[i].insideBoxAt(globalX, y, globalZ)) {
                the_beyond$notePlanKeptOut(planOwner[i], globalX, y, globalZ, "its own pieces");
                return false;
            }
            if (flankBoxes != null) {
                for (int m = 0; m < flankBoxes.size(); m++) {
                    CarveMask other = flankBoxes.get(m);
                    if (other != planOwner[i] && other.insideBoxAt(globalX, y, globalZ)) {
                        the_beyond$notePlanKeptOut(planOwner[i], globalX, y, globalZ, "the pieces of " + other.structureKey);
                        return false;
                    }
                }
            }
            return true;
        }

        boolean foundationOrLipFillAt(int y) {
            for (int i = 0; i < nPlan; i++) {
                if (planLays(i, y)) return true;
            }
            for (int i = 0; i < nFnd; i++) {
                if (the_beyond$isFoundationFill(y, aFndGLo[i], aFndNatTop[i])) return true;
            }
            for (int i = 0; i < nLip; i++) {
                if (the_beyond$isLipFill(y, aLipNatTop[i], aLipTop[i])) return true;
            }
            for (int i = 0; i < nCap; i++) {
                if (y >= capLo[i] && y <= capHi[i]) return true;
            }
            return deckFillAt(y);
        }

        int[] capLo, capHi;
        int nCap;

        int[] envLo, envTop;
        double[] envDist;
        int envN;
        List<CarveMask> flankBoxes;

        boolean insideAnyBox(int y) {
            for (int m = 0; m < flankBoxes.size(); m++) {
                if (flankBoxes.get(m).insideLiftBoxAt(globalX, y, globalZ)) return true;
            }
            return false;
        }

        double baseBeardDeltaAt(int y) {
            double d = (DISTRIBUTED_BASE_BEARD && y >= beardYLo && y <= beardYHi)
                    ? the_beyond$baseBeardDeltaAt(bits.dxz, bits.lo, bits.hi, bits.base, bits.baseNat, bits.baseFloor, nBits, y) : 0.0;
            if (envN > 0 && !insideAnyBox(y)) {
                for (int e = 0; e < envN; e++) {
                    if (y > envTop[e] || y < envLo[e] - ENVELOPE_DOWN) continue;
                    double f = the_beyond$envelopeBeardDelta(envLo[e], envTop[e], envDist[e], y,
                            the_beyond$carveWarp(globalX, y, globalZ));
                    if (f > d) d = f;
                    break;
                }
            }
            return d;
        }

        boolean inCarveBand(int y) {
            return y >= carveYLo && y <= carveYHi;
        }

        /** Must read roughNoise, warp, spanGrow and the natural run as carveRemovedAirAt does, or carve and guard drift. */
        double carvePenaltyAt(int y, BeyondEndChunkGenerator.ColumnScratch s) {
            for (int m = 0; m < flankBoxes.size(); m++) {
                if (flankBoxes.get(m).inBuriedInterior(globalX, y, globalZ)) return Double.POSITIVE_INFINITY;
            }
            double warpRaw = the_beyond$carveWarp(globalX, y, globalZ, anyFloat);
            double warp = CARVE_WARP_AMT * warpRaw;
            double roughNoise = anyDist ? the_beyond$faceRough(globalX, y, globalZ) : 0.0;
            if (anyDist) {
                int wx = (int) Math.round(globalX + DT3_WARP_AMP * warpRaw);
                int wy = (int) Math.round(y + DT3_WARP_AMP * the_beyond$carveWarp(globalX + 131, y + 57, globalZ + 911, anyFloat));
                int wz = (int) Math.round(globalZ + DT3_WARP_AMP * the_beyond$carveWarp(globalX + 877, y + 401, globalZ + 283, anyFloat));
                for (int k = 0; k < nBits; k++)
                    bits.d3[k] = (bits.dist[k] && bits.mask[k] != null) ? bits.mask[k].dt3At(wx, wy, wz) : DT3_CAP + 1;
            }
            boolean rockBelow;
            { int run = 0; for (int yy = y - 1; run < FOUNDATION_MIN_RUN; yy--) { if (!BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s)) break; run++; } rockBelow = run >= FOUNDATION_MIN_RUN; }
            int rb = Integer.MIN_VALUE, rt = Integer.MAX_VALUE;
            // A floater column reads only the run's top: the rounds stay off, and the bubble spares an island under its hull.
            if ((anyDist || anyFloat) && natRunAt(y, s)) {
                if (!anyFloat && y - runBot < RIM_SCAN) rb = runBot;
                if (runTop - y < RIM_SCAN) rt = runTop;
            }
            return the_beyond$carvePenalty(bits.dxz, bits.lo, bits.hi, bits.gLo, bits.seat, bits.filled, bits.dist, nBits, y, warp, bits.d3, rockBelow, roughNoise, bits.base, bits.buriedAbove, bits.baseFloor, bits.corr, the_beyond$spanGrow(globalX, y, globalZ), insideAnyBox(y), bits.fly, bits.d2, bits.hug, bits.capTop, rb, rt);
        }

        private int runBot = Integer.MAX_VALUE, runTop = Integer.MIN_VALUE;

        private boolean natRunAt(int y, BeyondEndChunkGenerator.ColumnScratch s) {
            if (y >= runBot && y <= runTop) return true;
            if (!BeyondEndChunkGenerator.isSolidTerrainScratch(y, s)) return false;
            int t = y, b = y;
            while (t < carveYHi + RIM_SCAN && BeyondEndChunkGenerator.isSolidTerrainScratch(t + 1, s)) t++;
            while (b > carveYLo - RIM_SCAN && BeyondEndChunkGenerator.isSolidTerrainScratch(b - 1, s)) b--;
            runBot = b; runTop = t;
            return true;
        }
    }

    private static boolean the_beyond$deckGapColumn(CarveMask mask, int x, int z) {
        if (!mask.occupied(x, z)) return true;
        return mask.isFilled((x - mask.oX) + (z - mask.oZ) * mask.w);
    }

    static ColumnCarveState beginColumn(List<CarveMask> carveMasks, int globalX, int globalZ,
            BeyondEndChunkGenerator.ColumnScratch s) {
        final int maskN = carveMasks.size();
        BeyondEndChunkGenerator.CarveBits cb = null;
        int bitN = 0, fndN = 0, lipN = 0;
        int deckY = Integer.MAX_VALUE;
        int pedLoY = Integer.MAX_VALUE, pedHiY = Integer.MIN_VALUE;
        int cyLo = Integer.MAX_VALUE, cyHi = Integer.MIN_VALUE;
        int[] aFndGLo = null, aFndNatTop = null, aLipNatTop = null, aLipTop = null;
        if (maskN > 0) {
            cb = BeyondEndChunkGenerator.CARVE_BITS.get();
            aFndGLo = new int[maskN]; aFndNatTop = new int[maskN]; aLipNatTop = new int[maskN]; aLipTop = new int[maskN];
            final int window = (2 * CARVE_SCAN_REACH + 1) * (2 * CARVE_SCAN_REACH + 1);
            for (int m = 0; m < maskN; m++) {
                CarveMask mask = carveMasks.get(m);
                cb.ensure(bitN + window);
                int before = bitN;
                bitN = mask.collectOccupiedBits(globalX, globalZ, CARVE_SCAN_REACH, cb.dxz, cb.lo, cb.hi, cb.gLo, cb.seat, cb.filled, cb.dist, cb.base, cb.mask, cb.colX, cb.colZ, cb.d2, cb.corr, bitN);
                if (bitN == before) continue;
                if (mask.gLo < cyLo) cyLo = mask.gLo;
                if (mask.gHi > cyHi) cyHi = mask.gHi;
                if (mask.basePedestal && !mask.carveOnly && mask.baseBox != null
                        && globalX >= mask.baseBox.minX() && globalX <= mask.baseBox.maxX()
                        && globalZ >= mask.baseBox.minZ() && globalZ <= mask.baseBox.maxZ()) {
                    int keepLo = Math.min(mask.pedLoY, mask.groundFloor() - GROUND_KEEP);
                    if (keepLo < pedLoY) pedLoY = keepLo;
                    // Up to the column's own sole: one standing a course higher keeps the island's rock right under it.
                    int sole = mask.soleAt(globalX, globalZ);
                    int keepHi = (sole != Integer.MIN_VALUE ? sole : mask.groundFloor()) - 1;
                    if (keepHi > pedHiY) pedHiY = keepHi;
                }
                boolean footActive = !mask.carveOnly
                        && the_beyond$footingActiveAt(mask.seated, mask.distributed, mask.inBaseFootprint(globalX, globalZ));
                if (footActive && mask.occupied(globalX, globalZ)) {
                    int ob = (globalX - mask.oX) + (globalZ - mask.oZ) * mask.w;
                    if (!mask.isFilled(ob)) {
                        int base = mask.distributed ? mask.colLo(ob) : mask.gLo;
                        int depth = mask.foundationDepth;
                        int natTop = the_beyond$foundationNatTop(base, depth, yy -> BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s));
                        if (!mask.distributed || natTop != Integer.MIN_VALUE) {
                            aFndGLo[fndN] = base;
                            aFndNatTop[fndN] = natTop;
                            fndN++;
                        }
                    }
                } else if (DISTRIBUTED_LIP && footActive && mask.distributed && !mask.occupied(globalX, globalZ)) {
                    int lipDxz = Integer.MAX_VALUE, lipAnchorLo = Integer.MIN_VALUE, lipAnchorHi = Integer.MIN_VALUE;
                    for (int dz = -BEARD_LAT_REACH; dz <= BEARD_LAT_REACH; dz++) {
                        for (int dx = -BEARD_LAT_REACH; dx <= BEARD_LAT_REACH; dx++) {
                            int cheb = Math.max(Math.abs(dx), Math.abs(dz));
                            if (cheb == 0 || cheb >= lipDxz) continue;
                            int ox = globalX + dx, oz = globalZ + dz;
                            if (!mask.occupied(ox, oz)) continue;
                            int ob2 = (ox - mask.oX) + (oz - mask.oZ) * mask.w;
                            if (mask.isFilled(ob2)) continue;
                            lipDxz = cheb; lipAnchorLo = mask.colLo(ob2); lipAnchorHi = mask.colHi(ob2);
                        }
                    }
                    if (lipDxz <= BEARD_LAT_REACH && lipAnchorLo != Integer.MIN_VALUE) {
                        int lipNat = the_beyond$foundationNatTop(lipAnchorLo, mask.foundationDepth, yy -> BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s));
                        int lt = the_beyond$lipTop(lipNat, lipAnchorLo, lipAnchorHi, lipDxz);
                        if (lt > lipNat) {
                            aLipNatTop[lipN] = lipNat;
                            aLipTop[lipN] = lt;
                            lipN++;
                        }
                    }
                } else if (mask.baseBox != null && !mask.carveOnly
                        && globalX >= mask.baseBox.minX() && globalX <= mask.baseBox.maxX()
                        && globalZ >= mask.baseBox.minZ() && globalZ <= mask.baseBox.maxZ()
                        && the_beyond$deckGapColumn(mask, globalX, globalZ)) {
                    int deck = mask.baseBox.minY();
                    if (deck < deckY) deckY = deck;
                }
            }
        }
        for (int k = 0; k < bitN; k++) {
            cb.fly[k] = cb.mask[k] != null && cb.mask[k].floating;
            cb.hug[k] = cb.mask[k] != null && cb.mask[k].hugTerrain;
        }
        // Gathered before the no-carve exit: a planned column is laid even with no piece in reach.
        int nPlan = 0;
        int[] planLo = null, planHi = null, planSole = null;
        CarveMask[] planOwner = null;
        for (int m = 0; m < maskN; m++) {
            CarveMask mask = carveMasks.get(m);
            if (!mask.basePedestal) continue;
            CityGroundPlan plan = mask.groundPlan();
            int c = plan == null ? -1 : plan.index(globalX, globalZ);
            if (c < 0 || !(plan.hasTop(c) || plan.hasBottom(c))) continue;
            if (planLo == null) { planLo = new int[2]; planHi = new int[2]; planSole = new int[2]; planOwner = new CarveMask[2]; }
            if (nPlan + 2 > planLo.length) {
                planLo = java.util.Arrays.copyOf(planLo, nPlan + 2);
                planHi = java.util.Arrays.copyOf(planHi, nPlan + 2);
                planSole = java.util.Arrays.copyOf(planSole, nPlan + 2);
                planOwner = java.util.Arrays.copyOf(planOwner, nPlan + 2);
            }
            int sole = mask.soleAt(globalX, globalZ);
            // Off the ground floor its start has no block under that floor either, unless another of its pieces reaches under it.
            if (sole == Integer.MIN_VALUE && !mask.pieceUnderStart(globalX, globalZ)) sole = mask.groundFloor();
            if (plan.hasTop(c)) { planLo[nPlan] = plan.topLo[c]; planHi[nPlan] = plan.topHi[c]; planSole[nPlan] = sole; planOwner[nPlan++] = mask; }
            if (plan.hasBottom(c)) { planLo[nPlan] = plan.botLo[c]; planHi[nPlan] = plan.botHi[c]; planSole[nPlan] = sole; planOwner[nPlan++] = mask; }
        }
        final boolean hasCarve = bitN > 0;
        if (!hasCarve) {
            if (nPlan == 0) return null;
            ColumnCarveState only = new ColumnCarveState(globalX, globalZ, cb, 0, 0, 0, aFndGLo, aFndNatTop, aLipNatTop,
                    aLipTop, Integer.MAX_VALUE, Integer.MIN_VALUE, false, false, Integer.MAX_VALUE, Integer.MIN_VALUE);
            only.flankBoxes = carveMasks;
            only.planLo = planLo; only.planHi = planHi; only.planSole = planSole; only.planOwner = planOwner; only.nPlan = nPlan;
            return only;
        }
        final int nBits = bitN, nFnd = fndN, nLip = lipN;
        final BeyondEndChunkGenerator.CarveBits bits = cb;
        int bbYLo = Integer.MAX_VALUE, bbYHi = Integer.MIN_VALUE;
        if (DISTRIBUTED_BASE_BEARD && hasCarve) {
            final java.util.function.IntPredicate solidProbe = yy -> BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s);
            final BeyondEndChunkGenerator.ColumnScratch probe = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
            for (int k = 0; k < nBits; k++) {
                if (bits.dist[k]) {
                    bits.buriedAbove[k] = bits.mask[k].rockOverTop(bits.colX[k], bits.colZ[k], probe);
                } else {
                    bits.buriedAbove[k] = false;
                }
                boolean hasBox = bits.mask[k] != null && bits.mask[k].baseBox != null && !bits.mask[k].floating;
                int seatFloor = hasBox ? bits.mask[k].baseBox.minY() : Integer.MIN_VALUE;
                // A pedestal city's base is laid by its ground plan, so the weld stays off it.
                boolean weld = hasBox && bits.dist[k] && !bits.mask[k].carveOnly
                        && !(bits.base[k] && bits.mask[k].basePedestal);
                int rawNat = weld ? the_beyond$foundationNatTop(bits.lo[k], bits.mask[k].foundationDepth, solidProbe)
                        : Integer.MIN_VALUE;
                int nat = weld ? the_beyond$groundRestNatTop(bits.lo[k], rawNat) : Integer.MIN_VALUE;
                if (nat != Integer.MIN_VALUE && bits.lo[k] - bits.gLo[k] > BEARD_GROUND_BAND) nat = Integer.MIN_VALUE;
                int confined = the_beyond$seatConfinedNatTop(nat, seatFloor);
                if (confined == Integer.MIN_VALUE && nat != Integer.MIN_VALUE
                        && BeyondGenDiagnostics.loggedMaskKeys.add("bbconfine@" + (globalX >> 4) + "," + (globalZ >> 4))) {
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] base-beard SEAT-CONFINE chunk=[{},{}] col=({},{}) seatFloor={} natTop={} drop={} -> cross-pancake weld EXCLUDED (no wall-silhouette)",
                            globalX >> 4, globalZ >> 4, bits.colX[k], bits.colZ[k], seatFloor, nat, (seatFloor - nat));
                }
                nat = confined;
                bits.baseNat[k] = nat;
                bits.baseFloor[k] = hasBox ? bits.mask[k].floorFor(bits.colX[k], bits.colZ[k], bits.base[k]) : Integer.MIN_VALUE;
                bits.capTop[k] = hasBox ? the_beyond$roofCapTop(bits.mask[k], bits.colX[k], bits.colZ[k], bits.base[k], bits.hi[k]) : Integer.MIN_VALUE;
                if (nat == Integer.MIN_VALUE) continue;
                int t = bits.lo[k] + (PLATFORM_COVER - 1);
                if (t > bits.hi[k]) t = bits.hi[k];
                if (bits.base[k] && t > bits.baseFloor[k] - 1) t = bits.baseFloor[k] - 1;
                if (nat + 1 < bbYLo) bbYLo = nat + 1;
                if (t > bbYHi) bbYHi = t;
            }
        }
        final List<CarveMask> boxMasks = carveMasks;
        boolean subFloor = false;
        for (int k = 0; k < nBits && !subFloor; k++) {
            subFloor = bits.dist[k] && bits.dxz[k] <= ENVELOPE_LAT
                    && bits.baseFloor[k] != Integer.MIN_VALUE && bits.lo[k] < bits.baseFloor[k];
        }
        final int beardYLo = bbYLo, beardYHi = bbYHi;
        boolean anyDistTmp = false;
        for (int k = 0; k < nBits; k++) if (bits.dist[k]) { anyDistTmp = true; break; }
        final boolean anyDist = anyDistTmp;
        final boolean anyFloat = the_beyond$anyFloating(bits, nBits);
        final int carveYLo = hasCarve ? cyLo - FLOAT_VMARGIN - CARVE_ERODE_REACH : 0;
        final int carveYHi = hasCarve ? cyHi + FLOAT_VMARGIN + CARVE_ERODE_REACH : 0;
        // Only feeds a debug line, so it stops running once that line exists for this chunk.
        String buriedKey = DISTRIBUTED_SUBSURFACE_BURY && hasCarve && anyDist && com.thebeyond.TheBeyond.LOGGER.isDebugEnabled()
                ? "buried@" + (globalX >> 4) + "," + (globalZ >> 4) : null;
        if (buriedKey != null && !BeyondGenDiagnostics.loggedMaskKeys.contains(buriedKey)) {
            final BeyondEndChunkGenerator.ColumnScratch probe = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
            for (int k = 0; k < nBits; k++) {
                if (!bits.buriedAbove[k] || bits.base[k]) continue;
                int cx = bits.colX[k], cz = bits.colZ[k];
                float pd = (float) Math.sqrt((double) cx * cx + (double) cz * cz);
                BeyondEndChunkGenerator.initColumnScratch(cx, cz, pd, probe);
                int run = 0; for (int yy = bits.hi[k]; run < FOUNDATION_MIN_RUN; yy--) { if (!BeyondEndChunkGenerator.isSolidTerrainScratch(yy, probe)) break; run++; }
                if (run < FOUNDATION_MIN_RUN) continue;
                if (BeyondGenDiagnostics.loggedMaskKeys.add(buriedKey)) {
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] island-cap KEEP chunk=[{},{}] col=({},{}) roof=hi{}+1 buriedAbove=true rockBelow=true -> island buries room",
                            globalX >> 4, globalZ >> 4, cx, cz, bits.hi[k]);
                    break;
                }
            }
        }
        ColumnCarveState state = new ColumnCarveState(globalX, globalZ, bits, nBits, nFnd, nLip, aFndGLo, aFndNatTop, aLipNatTop, aLipTop, beardYLo, beardYHi, anyDist, anyFloat, carveYLo, carveYHi);
        state.deckY = deckY;
        for (int k = 0; k < nBits; k++) {
            if (bits.dxz[k] != 0 || bits.capTop[k] == Integer.MIN_VALUE) continue;
            if (state.capLo == null) { state.capLo = new int[nBits]; state.capHi = new int[nBits]; }
            state.capLo[state.nCap] = bits.hi[k] + 1;
            state.capHi[state.nCap++] = bits.capTop[k];
        }
        if (subFloor) {
            // Nearest sub-floor span holding a height lifts it, so stepped roofs keep their top and nothing rises over one.
            int n = 0;
            int[] eLo = new int[nBits], eTop = new int[nBits];
            double[] eDist = new double[nBits];
            for (int k = 0; k < nBits; k++) {
                if (!bits.dist[k] || bits.dxz[k] > ENVELOPE_LAT) continue;
                if (bits.baseFloor[k] == Integer.MIN_VALUE || bits.lo[k] >= bits.baseFloor[k]) continue;
                // A pedestal's own footing is left to the pedestal, wrapping it here hangs a wart under the island.
                if (bits.base[k] && bits.mask[k].basePedestal) continue;
                // Over a covered roof the flanks rise to the cover, so it does not stand a course proud of them.
                int top = bits.capTop[k] != Integer.MIN_VALUE ? bits.capTop[k] : Math.min(bits.hi[k], bits.baseFloor[k] - 1);
                if (top < bits.lo[k]) continue;
                double de = Math.sqrt(bits.d2[k]);
                int e = n++;
                while (e > 0 && eDist[e - 1] > de) { eLo[e] = eLo[e - 1]; eTop[e] = eTop[e - 1]; eDist[e] = eDist[e - 1]; e--; }
                eLo[e] = bits.lo[k]; eTop[e] = top; eDist[e] = de;
            }
            state.envLo = eLo; state.envTop = eTop; state.envDist = eDist; state.envN = n;
        }
        state.flankBoxes = boxMasks;
        state.pedLoY = pedLoY;
        state.pedHiY = pedHiY;
        state.planLo = planLo; state.planHi = planHi; state.planSole = planSole; state.planOwner = planOwner; state.nPlan = nPlan;
        return state;
    }
}
