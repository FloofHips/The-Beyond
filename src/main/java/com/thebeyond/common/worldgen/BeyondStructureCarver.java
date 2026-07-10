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
    static final int CARVE_VERT_REACH = FLOAT_VMARGIN + CARVE_ERODE_REACH;   // 17
    static final int CARVE_DIST_LAT_REACH = 6;
    static final int CARVE_DIST_VERT_REACH = 8;
    static final int DIST_SUPPORT_BAND = 6;
    static final int FOOTING_TOL = 4;
    static final double CARVE_DIST_AMP = 0.25;
    static final double CARVE_DIST_WARP_GAIN = 2.0;
    static final double CARVE_WARP_AMT = 0.3;
    private static final double CARVE_WARP_FREQ = 0.08, CARVE_WARP_FREQ_HI = 0.18;

    /** HARD INVARIANT: {@code DT3_REACH + DT3_WARP_AMP (=8) <= FLOAT_HMARGIN (=8)}, so the warped clear stays ⊆ guard. */
    static final int DT3_REACH = 6;
    static final double DT3_WARP_AMP = 2.0;
    static final int DT3_CAP = DT3_REACH + (int) Math.ceil(DT3_WARP_AMP) + 1;   // = 9

    /** INVARIANT: {@code 0 <= FACE_ROUGH < DT3_REACH}. */
    static final double FACE_ROUGH_FREQ = 0.045;
    static final int FACE_ROUGH = 3;

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
        double penalty = 0.0;
        if (buriedAbove != null && rockBelow) {
            boolean occupied = false, keepNear = false;
            for (int k = 0; k < nBits; k++) {
                int vo = y < lo[k] ? lo[k] - y : (y > hi[k] ? y - hi[k] : 0);
                if (dxz[k] == 0 && vo == 0) { occupied = true; break; }
                if (buriedAbove[k] && (base == null || !base[k]) && (baseFloor == null || hi[k] <= baseFloor[k])) {
                    boolean cap   = (dxz[k] == 0 && y > hi[k] && (y - hi[k]) <= CARVE_CEIL_REACH);
                    boolean shell = (dxz[k] <= SUBSURF_KEEP_REACH && y <= hi[k]);
                    if (cap || shell) keepNear = true;
                }
            }
            if (!occupied && keepNear) return 0.0;
        }
        for (int k = 0; k < nBits; k++) {
            int vertOut = y < lo[k] ? lo[k] - y : (y > hi[k] ? y - hi[k] : 0);
            if (dxz[k] == 0 && vertOut == 0) return Double.POSITIVE_INFINITY;
            if (dxz[k] == 0 && seat[k] && !filled[k] && y > hi[k]) {
                int rc = (int) Math.round(FACE_ROUGH * 0.5 * (1.0 + rough));
                rc = rc < 0 ? 0 : (rc > FACE_ROUGH ? FACE_ROUGH : rc);
                int ceilEff = dist[k] ? (CARVE_CEIL_REACH - rc) : CARVE_CEIL_REACH;
                if ((y - hi[k]) <= ceilEff) return Double.POSITIVE_INFINITY;
            }
            if (filled[k]) continue;
            if (seat[k] && !dist[k]) continue;
            if (seat[k] && dist[k] && dxz[k] == 0 && y <= lo[k]) continue;
            double p;
            if (dist[k]) {
                int dd = d3[k];
                if (base != null && base[k] && dxz[k] <= BEARD_LAT_REACH && y < lo[k] && rockBelow) continue;
                if (dd == DT3_CAP + 1) {
                    if (BeyondGenDiagnostics.loggedMaskKeys.add("dt3-fallback"))
                        com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] DT3 absent -> 2.5D fallback");
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
                    int r = (int) Math.round(FACE_ROUGH * 0.5 * (1.0 + rough));
                    r = r < 0 ? 0 : (r > FACE_ROUGH ? FACE_ROUGH : r);
                    int reachEff = DT3_REACH - r;
                    if (dd > reachEff) continue;
                    p = CARVE_DIST_AMP * (1.0 - the_beyond$smootherstep((double) dd / reachEff));
                }
                if (dxz[k] > 0 && rockBelow) {
                    int aboveBase = y - gLo[k];
                    if (aboveBase <= 0) p = 0.0;
                    else if (aboveBase < DIST_SUPPORT_BAND) p *= the_beyond$smootherstep((double) aboveBase / DIST_SUPPORT_BAND);
                }
            } else {
                double nl = dxz[k] / (double) CARVE_LAT_REACH;
                double nv = vertOut / (double) CARVE_VERT_REACH;
                double nd2 = (nl * nl + nv * nv) - warp;
                if (nd2 >= 1.0) continue;
                if (nd2 < 0.0) nd2 = 0.0;
                p = CARVE_AMP * (1.0 - the_beyond$smootherstep(nd2));
            }
            if (p > penalty) penalty = p;
        }
        return penalty;
    }

    static double the_beyond$carveWarp(int x, int y, int z) {
        if (BeyondEndChunkGenerator.simplexNoise == null) return 0.0;
        return 0.6 * BeyondEndChunkGenerator.simplexNoise.getValue(x * CARVE_WARP_FREQ + 1024.0, y * CARVE_WARP_FREQ - 2048.0, z * CARVE_WARP_FREQ + 512.0)
             + 0.4 * BeyondEndChunkGenerator.simplexNoise.getValue(x * CARVE_WARP_FREQ_HI - 4096.0, y * CARVE_WARP_FREQ_HI + 1536.0, z * CARVE_WARP_FREQ_HI - 768.0);
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
        if (seated && y <= gLo) return CARVE_ERODE_REACH + 1;
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
    static final int BEARD_LAT_REACH = 5;
    static final int PLATFORM_COVER = 5;
    static final boolean DISTRIBUTED_BASE_BEARD = true;
    static final double BASE_BEARD_AMP = 0.5;
    static final int BASE_BEARD_VFADE = 2;
    static final boolean DISTRIBUTED_SUBSURFACE_BURY = true;
    static final int SUBSURF_KEEP_REACH = 6;
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

    final java.util.Map<StructureStart, CarveMask> the_beyond$maskCache =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private final java.util.Map<StructureStart, Boolean> the_beyond$cavityReject =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public List<CarveMask> the_beyond$collectCarveMasks(StructureManager sm, ChunkPos cp) {
        if (!BeyondTerrainState.isActive()) return Collections.emptyList();
        final RegistryAccess ra = sm.registryAccess();
        List<CarveMask> masks = null;
        var structReg = ra.registryOrThrow(Registries.STRUCTURE);
        for (StructureStart start : sm.startsForStructure(cp, s -> true)) {
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
            boolean layerDistributed = BeyondForeignStructureProfiles.isLayerDistributed(key, start.getChunkPos().toLong());
            boolean distributed = layerDistributed
                    || BeyondForeignStructureProfiles.isAutoSeatedProjected(key);
            boolean seated = distributed || profile.anchor() == StructureIntegrationProfile.Anchor.SEATED;
            CarveMask mask = the_beyond$maskCache.get(start);
            if (mask == null) {
                mask = the_beyond$buildMask(start, seated, distributed, layerDistributed, profile.flushTolerance());
                the_beyond$maskCache.put(start, mask);
            }
            mask.carveOnly = BeyondForeignStructureProfiles.isEmbedded(key);
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
                String baseBeardKind = mask.carveOnly ? "OFF(interior-only: no foundation/beard)"
                        : (DISTRIBUTED_BASE_BEARD && distributed && mask.baseBox != null)
                        ? "ON(amp=" + BASE_BEARD_AMP + ",vfade=" + BASE_BEARD_VFADE + ",cover=" + PLATFORM_COVER
                          + ",floor=" + mask.baseBox.minY() + ",ceil=" + (mask.baseBox.minY() - 1) + ")" : "OFF";
                com.thebeyond.TheBeyond.LOGGER.debug(
                        "[Beyond] mask {}: cells={} (filled={} closeR={}) bbox={}x{}={} ({}%) y=[{}..{}] span={} seated={} "
                        + "carve={} skirt(lat={},vert={},amp={}) faceRough={} baseAnchorPreserve={} baseBeard={} clearedEnvelope~{}x{} [amp tunes cut depth: lower=hug closer, higher=wider gap]",
                        id, cells, mask.filledHoles, CLOSE_RADIUS, mask.w, mask.d, area,
                        area > 0 ? (cells * 100 / area) : 0, mask.gLo, mask.gHi, span, seated,
                        carveKind, latR, vertR, ampR, faceRoughKind, baseAnchorKind, baseBeardKind, mask.w + 2 * latR, mask.d + 2 * latR);
                if (span > 255) com.thebeyond.TheBeyond.LOGGER.debug(
                        "[Beyond] mask {} span={} > 255 — a tall jigsaw tower; the 16-bit column encoding now carves it (was clamped)", id, span);
            }
        }
        synchronized (the_beyond$maskCache) {
            for (var e : the_beyond$maskCache.entrySet()) {
                StructureStart start = e.getKey();
                CarveMask mask = e.getValue();
                if (start == null || mask == null || !start.isValid()) continue;
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
                boolean referenced = false;
                for (StructureStart s : sm.startsForStructure(sp, start.getStructure())) {
                    if (s == start) { referenced = true; break; }
                }
                if (referenced) continue;
                if (far == null) far = new java.util.ArrayList<>(1);
                far.add(start);
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
        BoundingBox env = start.getBoundingBox();
        BoundingBox baseBox = start.getPieces().isEmpty() ? null : start.getPieces().get(0).getBoundingBox();
        int oX = env.minX(), oZ = env.minZ(), w = env.getXSpan(), d = env.getZSpan();
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
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pe)) {
                the_beyond$rasterizeBox(piece.getBoundingBox(), oX, oZ, w, d, occ, gy, cLo, cHi);
                continue;
            }
            StructurePoolElement el = the_beyond$unwrapPoolElement(pe.getElement());
            if (el.getProjection() != StructureTemplatePool.Projection.RIGID) {
                // TERRAIN_MATCHING re-projects per column at place time, so the assembly bbox can sit on a different
                // pancake than the placed deck; must snap per-column to topmost-natural or it mask-carves a phantom slab.
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
            boolean perBlock = false;
            int hits = 0, clipped = 0;
            if (t != null) {
                try {
                    List<StructureTemplate.Palette> pals =
                            ((StructureTemplateAccessor) (Object) t).the_beyond$palettes();
                    if (!pals.isEmpty()) {
                        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pe.getRotation());
                        BlockPos origin = pe.getPosition();
                        for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
                            if (info.state().isAir()) continue;
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
                        perBlock = true;
                    }
                } catch (Throwable ignored) { perBlock = false; }
            }
            if (!perBlock) the_beyond$rasterizeBox(piece.getBoundingBox(), oX, oZ, w, d, occ, gy, cLo, cHi);
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
                        "[Beyond] piece {} rot={} pos={} bbox=[{},{},{}..{},{},{}] mode={} hits={} clipped={} startFloor={}{}",
                        pieceLoc, pe.getRotation(), pe.getPosition(),
                        pb.minX(), pb.minY(), pb.minZ(), pb.maxX(), pb.maxY(), pb.maxZ(),
                        perBlock ? "perBlock" : "failsoft-bbox", hits, clipped,
                        baseBox != null ? baseBox.minY() : Integer.MIN_VALUE, depth);
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
        return new CarveMask(oX, oZ, w, d, occ, colLoOff, colHiOff, filledOcc, gLo, gHi, seated, distributed, layerDistributed, foundationDepth, filled,
                dt3, dt3OX, dt3OZ, dt3W, dt3D, dt3YLo, dt3Layers, baseBox);
    }

    private static final java.util.concurrent.ConcurrentMap<Class<?>, java.util.Optional<java.lang.reflect.Method>>
            the_beyond$delegateMethods = new java.util.concurrent.ConcurrentHashMap<>();

    /** guard bounds the loop so a cyclic delegate() chain can't hang generation. */
    private static StructurePoolElement the_beyond$unwrapPoolElement(StructurePoolElement el) {
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

    public static boolean the_beyond$carveRemovedAirAt(List<CarveMask> masks, int x, int y, int z) {
        BeyondEndChunkGenerator.CarveBits cb = BeyondEndChunkGenerator.CARVE_BITS.get();
        int nBits = 0;
        for (int mi = 0, n = masks.size(); mi < n; mi++) {
            CarveMask mask = masks.get(mi);
            if (x < mask.oX - CARVE_SCAN_REACH || x > mask.oX + mask.w - 1 + CARVE_SCAN_REACH
             || z < mask.oZ - CARVE_SCAN_REACH || z > mask.oZ + mask.d - 1 + CARVE_SCAN_REACH) continue;
            cb.ensure(nBits + (2 * CARVE_SCAN_REACH + 1) * (2 * CARVE_SCAN_REACH + 1));
            nBits = mask.collectOccupiedBits(x, z, CARVE_SCAN_REACH, cb.dxz, cb.lo, cb.hi, cb.gLo, cb.seat, cb.filled, cb.dist, cb.base, cb.mask, cb.colX, cb.colZ, nBits);
        }
        if (nBits == 0) return false;
        double warpRaw = the_beyond$carveWarp(x, y, z);
        double warp = CARVE_WARP_AMT * warpRaw;
        double roughNoise = the_beyond$faceRough(x, y, z);
        int wx = (int) Math.round(x + DT3_WARP_AMP * warpRaw);
        int wy = (int) Math.round(y + DT3_WARP_AMP * the_beyond$carveWarp(x + 131, y + 57, z + 911));
        int wz = (int) Math.round(z + DT3_WARP_AMP * the_beyond$carveWarp(x + 877, y + 401, z + 283));
        for (int k = 0; k < nBits; k++)
            cb.d3[k] = (cb.dist[k] && cb.mask[k] != null) ? cb.mask[k].dt3At(wx, wy, wz) : DT3_CAP + 1;
        double p = the_beyond$carvePenalty(cb.dxz, cb.lo, cb.hi, cb.gLo, cb.seat, cb.filled, cb.dist, nBits, y, warp, cb.d3, true, roughNoise, cb.base);
        if (p == Double.POSITIVE_INFINITY) return true;
        if (p <= 0.0) return false;
        BeyondEndChunkGenerator.ColumnScratch s = BeyondEndChunkGenerator.SCRATCH.get();
        float dist = (float) Math.sqrt((double) x * x + (double) z * z);
        BeyondEndChunkGenerator.initColumnScratch(x, z, dist, s);
        double density = BeyondEndChunkGenerator.getTerrainDensityScratch(y, s);
        return (density - p) <= s.threshold;
    }

    static final class ColumnCarveState {
        final int globalX, globalZ;
        final BeyondEndChunkGenerator.CarveBits bits;
        final int nBits, nFnd, nLip;
        final int[] aFndGLo, aFndNatTop, aLipNatTop, aLipTop;
        final int beardYLo, beardYHi;
        final boolean anyDist;
        final int carveYLo, carveYHi;

        private ColumnCarveState(int globalX, int globalZ, BeyondEndChunkGenerator.CarveBits bits,
                int nBits, int nFnd, int nLip, int[] aFndGLo, int[] aFndNatTop, int[] aLipNatTop, int[] aLipTop,
                int beardYLo, int beardYHi, boolean anyDist, int carveYLo, int carveYHi) {
            this.globalX = globalX; this.globalZ = globalZ; this.bits = bits;
            this.nBits = nBits; this.nFnd = nFnd; this.nLip = nLip;
            this.aFndGLo = aFndGLo; this.aFndNatTop = aFndNatTop; this.aLipNatTop = aLipNatTop; this.aLipTop = aLipTop;
            this.beardYLo = beardYLo; this.beardYHi = beardYHi; this.anyDist = anyDist;
            this.carveYLo = carveYLo; this.carveYHi = carveYHi;
        }

        boolean foundationOrLipFillAt(int y) {
            for (int i = 0; i < nFnd; i++) {
                if (the_beyond$isFoundationFill(y, aFndGLo[i], aFndNatTop[i])) return true;
            }
            for (int i = 0; i < nLip; i++) {
                if (the_beyond$isLipFill(y, aLipNatTop[i], aLipTop[i])) return true;
            }
            return false;
        }

        double baseBeardDeltaAt(int y) {
            return (DISTRIBUTED_BASE_BEARD && y >= beardYLo && y <= beardYHi)
                    ? the_beyond$baseBeardDeltaAt(bits.dxz, bits.lo, bits.hi, bits.base, bits.baseNat, bits.baseFloor, nBits, y) : 0.0;
        }

        boolean inCarveBand(int y) {
            return y >= carveYLo && y <= carveYHi;
        }

        /** roughNoise/warp must be the byte-identical sample carveRemovedAirAt takes, or the carve and the guard's removed-air test drift. */
        double carvePenaltyAt(int y, BeyondEndChunkGenerator.ColumnScratch s) {
            double warpRaw = the_beyond$carveWarp(globalX, y, globalZ);
            double warp = CARVE_WARP_AMT * warpRaw;
            double roughNoise = anyDist ? the_beyond$faceRough(globalX, y, globalZ) : 0.0;
            if (anyDist) {
                int wx = (int) Math.round(globalX + DT3_WARP_AMP * warpRaw);
                int wy = (int) Math.round(y + DT3_WARP_AMP * the_beyond$carveWarp(globalX + 131, y + 57, globalZ + 911));
                int wz = (int) Math.round(globalZ + DT3_WARP_AMP * the_beyond$carveWarp(globalX + 877, y + 401, globalZ + 283));
                for (int k = 0; k < nBits; k++)
                    bits.d3[k] = (bits.dist[k] && bits.mask[k] != null) ? bits.mask[k].dt3At(wx, wy, wz) : DT3_CAP + 1;
            }
            boolean rockBelow;
            { int run = 0; for (int yy = y - 1; run < FOUNDATION_MIN_RUN; yy--) { if (!BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s)) break; run++; } rockBelow = run >= FOUNDATION_MIN_RUN; }
            return the_beyond$carvePenalty(bits.dxz, bits.lo, bits.hi, bits.gLo, bits.seat, bits.filled, bits.dist, nBits, y, warp, bits.d3, rockBelow, roughNoise, bits.base, bits.buriedAbove, bits.baseFloor);
        }
    }

    static ColumnCarveState beginColumn(List<CarveMask> carveMasks, int globalX, int globalZ,
            BeyondEndChunkGenerator.ColumnScratch s) {
        final int maskN = carveMasks.size();
        BeyondEndChunkGenerator.CarveBits cb = null;
        int bitN = 0, fndN = 0, lipN = 0;
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
                bitN = mask.collectOccupiedBits(globalX, globalZ, CARVE_SCAN_REACH, cb.dxz, cb.lo, cb.hi, cb.gLo, cb.seat, cb.filled, cb.dist, cb.base, cb.mask, cb.colX, cb.colZ, bitN);
                if (bitN == before) continue;
                if (mask.gLo < cyLo) cyLo = mask.gLo;
                if (mask.gHi > cyHi) cyHi = mask.gHi;
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
                }
            }
        }
        final boolean hasCarve = bitN > 0;
        if (!hasCarve) return null;
        final int nBits = bitN, nFnd = fndN, nLip = lipN;
        final BeyondEndChunkGenerator.CarveBits bits = cb;
        int bbYLo = Integer.MAX_VALUE, bbYHi = Integer.MIN_VALUE;
        if (DISTRIBUTED_BASE_BEARD && hasCarve) {
            final java.util.function.IntPredicate solidProbe = yy -> BeyondEndChunkGenerator.isSolidTerrainScratch(yy, s);
            final BeyondEndChunkGenerator.ColumnScratch probe = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
            for (int k = 0; k < nBits; k++) {
                if (bits.dist[k]) {
                    int cx = bits.colX[k], cz = bits.colZ[k];
                    float pd = (float) Math.sqrt((double) cx * cx + (double) cz * cz);
                    BeyondEndChunkGenerator.initColumnScratch(cx, cz, pd, probe);
                    bits.buriedAbove[k] = BeyondEndChunkGenerator.isSolidTerrainScratch(bits.hi[k] + 1, probe);
                } else {
                    bits.buriedAbove[k] = false;
                }
                boolean hasBox = bits.mask[k] != null && bits.mask[k].baseBox != null;
                int seatFloor = hasBox ? bits.mask[k].baseBox.minY() : Integer.MIN_VALUE;
                int nat = (hasBox && bits.dist[k] && !bits.mask[k].carveOnly) ? the_beyond$groundRestNatTop(bits.lo[k],
                        the_beyond$foundationNatTop(bits.lo[k], bits.mask[k].foundationDepth, solidProbe)) : Integer.MIN_VALUE;
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
                bits.baseFloor[k] = seatFloor;
                if (nat == Integer.MIN_VALUE) continue;
                int t = bits.lo[k] + (PLATFORM_COVER - 1);
                if (t > bits.hi[k]) t = bits.hi[k];
                if (bits.base[k] && t > bits.baseFloor[k] - 1) t = bits.baseFloor[k] - 1;
                if (nat + 1 < bbYLo) bbYLo = nat + 1;
                if (t > bbYHi) bbYHi = t;
            }
        }
        final int beardYLo = bbYLo, beardYHi = bbYHi;
        boolean anyDistTmp = false;
        for (int k = 0; k < nBits; k++) if (bits.dist[k]) { anyDistTmp = true; break; }
        final boolean anyDist = anyDistTmp;
        final int carveYLo = hasCarve ? cyLo - FLOAT_VMARGIN - CARVE_ERODE_REACH : 0;
        final int carveYHi = hasCarve ? cyHi + FLOAT_VMARGIN + CARVE_ERODE_REACH : 0;
        if (DISTRIBUTED_SUBSURFACE_BURY && hasCarve && anyDist) {
            final BeyondEndChunkGenerator.ColumnScratch probe = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
            for (int k = 0; k < nBits; k++) {
                if (!bits.buriedAbove[k] || bits.base[k]) continue;
                int cx = bits.colX[k], cz = bits.colZ[k];
                float pd = (float) Math.sqrt((double) cx * cx + (double) cz * cz);
                BeyondEndChunkGenerator.initColumnScratch(cx, cz, pd, probe);
                int run = 0; for (int yy = bits.hi[k]; run < FOUNDATION_MIN_RUN; yy--) { if (!BeyondEndChunkGenerator.isSolidTerrainScratch(yy, probe)) break; run++; }
                if (run < FOUNDATION_MIN_RUN) continue;
                if (BeyondGenDiagnostics.loggedMaskKeys.add("buried@" + (globalX >> 4) + "," + (globalZ >> 4))) {
                    com.thebeyond.TheBeyond.LOGGER.debug(
                            "[Beyond] island-cap KEEP chunk=[{},{}] col=({},{}) roof=hi{}+1 buriedAbove=true rockBelow=true -> island buries room",
                            globalX >> 4, globalZ >> 4, cx, cz, bits.hi[k]);
                    break;
                }
            }
        }
        return new ColumnCarveState(globalX, globalZ, bits, nBits, nFnd, nLip, aFndGLo, aFndNatTop, aLipNatTop, aLipTop, beardYLo, beardYHi, anyDist, carveYLo, carveYHi);
    }
}
