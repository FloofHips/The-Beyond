package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/** Fitness tests shared by the rejection gate and the ground seat, since the gate never sees the height the seat picks. */
public final class ForeignFit {
    private ForeignFit() {}

    static final int MIN_RADIUS = 3;
    static final int MAX_RADIUS = 32;

    public static boolean seatedUnfit(int x, int z, int floor, @org.jetbrains.annotations.Nullable StructurePiecesBuilder pieces,
            int minY, StructureIntegrationProfile profile) {
        if (!profile.towerRadiusPinned() && pieces != null && !pieces.isEmpty()) {
            BoundingBox bb = pieces.getBoundingBox();
            x = (bb.minX() + bb.maxX()) / 2;
            z = (bb.minZ() + bb.maxZ()) / 2;
        }
        return footprintUnfit(x, z, floor, minY, profile, radiusFor(profile, pieces));
    }

    static int radiusFor(StructureIntegrationProfile profile, StructurePiecesBuilder pieces) {
        if (profile.towerRadiusPinned() || pieces == null || pieces.isEmpty()) return profile.towerRadius();
        BoundingBox bb = pieces.getBoundingBox();
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, Math.max(bb.getXSpan(), bb.getZSpan()) / 2));
    }

    /** True when every piece is a feature element, whose one-block box says nothing of what the feature builds. */
    public static boolean featureOnly(StructurePiecesBuilder pieces) {
        if (pieces == null || pieces.isEmpty()) return false;
        for (StructurePiece p : pieces.build().pieces()) {
            if (!(p instanceof PoolElementStructurePiece pe) || !(pe.getElement() instanceof FeaturePoolElement)) return false;
        }
        return true;
    }

    static final int FOUNDATION_REACH = 8;

    /** Share of the start's columns held from {@code depth} under the foundation through its first {@code grip} courses. */
    public static double baseHeldShare(java.util.List<StructurePiece> pieces, int depth, int grip) {
        if (pieces.isEmpty()) return 1.0;
        BoundingBox base = pieces.get(0).getBoundingBox();
        java.util.List<BoundingBox> boxes = new java.util.ArrayList<>(pieces.size());
        for (StructurePiece p : pieces) boxes.add(p.getBoundingBox());
        int floor = foundationFloor(boxes);
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int held = 0, total = 0;
        for (int x = base.minX(); x <= base.maxX(); x++) {
            for (int z = base.minZ(); z <= base.maxZ(); z++) {
                total++;
                BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scr);
                for (int y = floor + grip - 1; y >= floor - depth; y--) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) { held++; break; }
                }
            }
        }
        return total == 0 ? 1.0 : (double) held / total;
    }

    public static int footprintSurface(BoundingBox b) {
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        java.util.List<Integer> tops = new java.util.ArrayList<>();
        int total = 0;
        for (int x = b.minX(); x <= b.maxX(); x += 2) {
            for (int z = b.minZ(); z <= b.maxZ(); z += 2) {
                total++;
                BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scr);
                boolean below = BeyondEndChunkGenerator.isSolidTerrainScratch(b.minY() - 8, scr);
                for (int y = b.minY() - 7; y <= b.minY() + 12; y++) {
                    boolean solid = BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr);
                    if (below && !solid) { tops.add(y - 1); break; }
                    below = solid;
                }
            }
        }
        if (2 * tops.size() < total) return Integer.MIN_VALUE;
        tops.sort(null);
        return tops.get(tops.size() / 2);
    }

    public static double startBuriedShare(BoundingBox b) {
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int buried = 0, total = 0;
        for (int x = b.minX(); x <= b.maxX(); x += 2) {
            for (int z = b.minZ(); z <= b.maxZ(); z += 2) {
                total++;
                BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scr);
                if (BeyondEndChunkGenerator.isSolidTerrainScratch(b.maxY(), scr)) buried++;
            }
        }
        return total == 0 ? 0.0 : (double) buried / total;
    }

    public static final int GROUND_REACH = 8;

    /** A piece's ground floor: its lowest course with blocks and the columns starting there or one above, x in the high word. */
    public record Course(int y, it.unimi.dsi.fastutil.longs.LongOpenHashSet cols,
            it.unimi.dsi.fastutil.longs.LongOpenHashSet raised) {}

    /** Share of the ground floor's soles the seat keeps at or over their island top, the carve opening the bumps above. */
    static final double SEAT_SHARE = 0.75;

    public static int groundSeat(Course floor, int boxFloor, int sampledTop) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet foot = floor.cols();
        if (foot.isEmpty()) return Integer.MIN_VALUE;
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        it.unimi.dsi.fastutil.ints.IntArrayList flush = new it.unimi.dsi.fastutil.ints.IntArrayList();
        for (long k : foot) {
            int x = (int) (k >> 32), z = (int) k;
            int top = topNear(x, z, sampledTop, GROUND_REACH, scr);
            if (top == Integer.MIN_VALUE) continue;
            flush.add(top - (floor.y() + (floor.raised().contains(k) ? 1 : 0) - boxFloor));
        }
        return 4 * flush.size() < foot.size() ? Integer.MIN_VALUE : seatQuantile(flush.toIntArray());
    }

    static int topNear(int x, int z, int ref, int reach, BeyondEndChunkGenerator.ColumnScratch scr) {
        BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scr);
        int top = Integer.MIN_VALUE;
        boolean above = BeyondEndChunkGenerator.isSolidTerrainScratch(ref + reach + 1, scr);
        for (int y = ref + reach; y >= ref - reach; y--) {
            boolean s = BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr);
            if (s && above && top == Integer.MIN_VALUE) top = ref + reach;
            if (s && !above && (top == Integer.MIN_VALUE || Math.abs(y - ref) < Math.abs(top - ref))) top = y;
            above = s;
        }
        return top;
    }

    public static final int FIT_REACH = 16;
    public static final double FIT_NEED = 0.75;

    /** A seat: the sideways move, the box floor there, and the shares of ground floor held and of apron with island near. */
    public record Fit(int dx, int dz, int seat, double held, double apron) {}

    public static Fit groundFit(Course floor, int boxFloor, int sampledTop) {
        int n = floor.cols().size();
        if (n == 0) return new Fit(0, 0, Integer.MIN_VALUE, 0.0, 0.0);
        int[] xs = new int[n], zs = new int[n], rel = new int[n];
        int i = 0, x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long k : floor.cols()) {
            xs[i] = (int) (k >> 32); zs[i] = (int) k;
            rel[i] = floor.y() + (floor.raised().contains(k) ? 1 : 0) - boxFloor;
            x0 = Math.min(x0, xs[i]); x1 = Math.max(x1, xs[i]);
            z0 = Math.min(z0, zs[i]); z1 = Math.max(z1, zs[i]);
            i++;
        }
        int[][] apron = apronCells(xs, zs);
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        Fit here = fitAt(xs, zs, rel, apron[0], apron[1], 0, 0, (x, z) -> topNear(x, z, sampledTop, GROUND_REACH, scr));
        if (here.held() >= 1.0 && here.apron() >= 1.0) return here;
        // Every column a move can reach is read once: moves share most of their columns.
        int m = FIT_REACH + CityGroundPlan.APRON + 1;
        int gx = x0 - m, gz = z0 - m, w = x1 - x0 + 1 + 2 * m, d = z1 - z0 + 1 + 2 * m;
        int[] tops = new int[w * d];
        for (int j = 0; j < d; j++) {
            for (int k = 0; k < w; k++) tops[k + j * w] = topNear(gx + k, gz + j, sampledTop, GROUND_REACH, scr);
        }
        return bestFit(xs, zs, rel, apron[0], apron[1], (x, z) -> tops[(x - gx) + (z - gz) * w], FIT_REACH);
    }

    static int[][] apronCells(int[] xs, int[] zs) {
        int pad = CityGroundPlan.CLOSE_R + CityGroundPlan.APRON + 2;
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++) {
            x0 = Math.min(x0, xs[i]); x1 = Math.max(x1, xs[i]);
            z0 = Math.min(z0, zs[i]); z1 = Math.max(z1, zs[i]);
        }
        int gx = x0 - pad, gz = z0 - pad, nx = x1 - x0 + 1 + 2 * pad, nz = z1 - z0 + 1 + 2 * pad;
        boolean[] g = new boolean[nx * nz];
        for (int i = 0; i < xs.length; i++) g[(xs[i] - gx) + (zs[i] - gz) * nx] = true;
        boolean[] a = CityGroundPlan.apron(g, nx, nz);
        int count = 0;
        for (boolean b : a) if (b) count++;
        int[] ax = new int[count], az = new int[count];
        count = 0;
        for (int c = 0; c < a.length; c++) {
            if (!a[c]) continue;
            ax[count] = gx + c % nx; az[count++] = gz + c / nx;
        }
        return new int[][]{ax, az};
    }

    static Fit bestFit(int[] xs, int[] zs, int[] rel, int[] ax, int[] az, java.util.function.IntBinaryOperator top, int reach) {
        Fit best = fitAt(xs, zs, rel, ax, az, 0, 0, top);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) best = better(fitAt(xs, zs, rel, ax, az, dx, dz, top), best);
        }
        return best;
    }

    private static Fit better(Fit a, Fit b) {
        if (a.held() > b.held() + 1e-9) return a;
        if (a.held() < b.held() - 1e-9) return b;
        if (a.apron() > b.apron() + 1e-9) return a;
        if (a.apron() < b.apron() - 1e-9) return b;
        return Math.abs(a.dx()) + Math.abs(a.dz()) < Math.abs(b.dx()) + Math.abs(b.dz()) ? a : b;
    }

    static Fit fitAt(int[] xs, int[] zs, int[] rel, int[] ax, int[] az, int dx, int dz, java.util.function.IntBinaryOperator top) {
        int n = xs.length, m = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        int[] flush = new int[n];
        for (int i = 0; i < n; i++) {
            int t = top.applyAsInt(xs[i] + dx, zs[i] + dz);
            if (t == Integer.MIN_VALUE) continue;
            int f = t - rel[i];
            flush[m++] = f;
            lo = Math.min(lo, f); hi = Math.max(hi, f);
        }
        int around = 0;
        for (int i = 0; i < ax.length; i++) if (top.applyAsInt(ax[i] + dx, az[i] + dz) != Integer.MIN_VALUE) around++;
        double apron = ax.length == 0 ? 1.0 : (double) around / ax.length;
        if (4 * m < n) return new Fit(dx, dz, Integer.MIN_VALUE, 0.0, apron);
        int seat = quantileOf(flush, m, lo, hi);
        int held = 0;
        for (int i = 0; i < m; i++) if (flush[i] >= seat - 1 - CityGroundPlan.STEP) held++;
        return new Fit(dx, dz, seat, (double) held / n, apron);
    }

    static int quantileOf(int[] v, int m, int lo, int hi) {
        if (hi - lo > 64) return seatQuantile(java.util.Arrays.copyOf(v, m));
        int[] count = new int[hi - lo + 1];
        for (int i = 0; i < m; i++) count[v[i] - lo]++;
        int rank = Math.min(m - 1, (int) Math.floor(SEAT_SHARE * (m - 1) + 0.5));
        for (int k = 0, seen = 0; k < count.length; k++) {
            seen += count[k];
            if (seen > rank) return lo + k;
        }
        return hi;
    }

    /** Share of a placed ground floor the island holds, as the ground plan reads it. */
    public static double floorHeld(Course floor) {
        if (floor.cols().isEmpty()) return 1.0;
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int held = 0;
        for (long k : floor.cols()) {
            int x = (int) (k >> 32), z = (int) k;
            int sole = floor.y() + (floor.raised().contains(k) ? 1 : 0);
            int t = topNear(x, z, sole - 1, CityGroundPlan.SEAT_REACH, scr);
            if (t != Integer.MIN_VALUE && (sole - 1) - t <= CityGroundPlan.STEP) held++;
        }
        return (double) held / floor.cols().size();
    }

    /** Pieces reaching over CityGroundPlan.STEP under the start's floor sink by design: a pedestal would leave them hanging. */
    public static boolean sinks(List<StructurePiece> pieces, @org.jetbrains.annotations.Nullable StructureTemplateManager tm) {
        return sinkDepth(pieces, tm) > CityGroundPlan.STEP;
    }

    public static int sinkDepth(List<StructurePiece> pieces, @org.jetbrains.annotations.Nullable StructureTemplateManager tm) {
        if (pieces.isEmpty()) return 0;
        BoundingBox start = pieces.get(0).getBoundingBox();
        StructureTemplateManager t = tm != null ? tm : BeyondEndChunkGenerator.the_beyond$templateManager;
        return sinkDepth(pieces.stream().map(StructurePiece::getBoundingBox).toList(),
                i -> isFoundationRock(pieces.get(i), start, t));
    }

    static int sinkDepth(List<BoundingBox> boxes, java.util.function.IntPredicate rock) {
        if (boxes.isEmpty()) return 0;
        int floor = boxes.get(0).minY(), low = floor;
        for (int i = 1; i < boxes.size(); i++) if (!rock.test(i)) low = Math.min(low, boxes.get(i).minY());
        return floor - low;
    }

    public static void movePieces(StructurePiecesBuilder pieces, int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return;
        for (StructurePiece p : pieces.build().pieces()) p.move(dx, dy, dz);
    }

    static int seatQuantile(int[] heights) {
        int[] h = heights.clone();
        java.util.Arrays.sort(h);
        return h[Math.min(h.length - 1, (int) Math.floor(SEAT_SHARE * (h.length - 1) + 0.5))];
    }

    @org.jetbrains.annotations.Nullable
    public static Course lowestCourse(StructurePiece piece, @org.jetbrains.annotations.Nullable StructureTemplateManager tm) {
        StructureTemplate t = templateOf(piece, tm);
        if (t == null) return null;
        StructurePlaceSettings settings;
        net.minecraft.core.BlockPos origin;
        if (piece instanceof PoolElementStructurePiece pe) {
            settings = new StructurePlaceSettings().setRotation(pe.getRotation());
            origin = pe.getPosition();
        } else if (piece instanceof net.minecraft.world.level.levelgen.structure.TemplateStructurePiece tsp) {
            settings = tsp.placeSettings();
            origin = tsp.templatePosition();
        } else {
            return null;
        }
        List<StructureTemplate.Palette> pals = ((com.thebeyond.mixin.StructureTemplateAccessor) (Object) t).the_beyond$palettes();
        if (pals.isEmpty()) return null;
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap sole = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        int floor = Integer.MAX_VALUE;
        for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
            if (!placesRock(info.state())) continue;
            net.minecraft.core.BlockPos wp = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
            long k = ((long) wp.getX() << 32) | (wp.getZ() & 0xFFFFFFFFL);
            if (!sole.containsKey(k) || wp.getY() < sole.get(k)) sole.put(k, wp.getY());
            floor = Math.min(floor, wp.getY());
        }
        if (sole.isEmpty()) return null;
        it.unimi.dsi.fastutil.longs.LongOpenHashSet cols = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet raised = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (var e : sole.long2IntEntrySet()) {
            if (e.getIntValue() > floor + 1) continue;
            cols.add(e.getLongKey());
            if (e.getIntValue() > floor) raised.add(e.getLongKey());
        }
        return new Course(floor, cols, raised);
    }

    public static boolean isFoundationRock(StructurePiece piece, BoundingBox start, @org.jetbrains.annotations.Nullable StructureTemplateManager tm) {
        BoundingBox b = piece.getBoundingBox();
        if (b.maxY() >= start.minY() || b.minX() < start.minX() || b.maxX() > start.maxX()
                || b.minZ() < start.minZ() || b.maxZ() > start.maxZ()) return false;
        StructureTemplate t = templateOf(piece, tm);
        if (t == null) return false;
        List<StructureTemplate.Palette> pals = ((com.thebeyond.mixin.StructureTemplateAccessor) (Object) t).the_beyond$palettes();
        if (pals.isEmpty()) return false;
        int rock = 0;
        for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
            if (!placesRock(info.state())) continue;
            if (!info.state().is(com.thebeyond.common.registry.BeyondTags.FOUNDATION_ROCK)) return false;
            rock++;
        }
        return rock > 0;
    }

    private static boolean placesRock(net.minecraft.world.level.block.state.BlockState s) {
        return !s.isAir() && !s.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID)
                && !s.is(net.minecraft.world.level.block.Blocks.JIGSAW);
    }

    @org.jetbrains.annotations.Nullable
    private static StructureTemplate templateOf(StructurePiece piece, @org.jetbrains.annotations.Nullable StructureTemplateManager tm) {
        try {
            if (piece instanceof net.minecraft.world.level.levelgen.structure.TemplateStructurePiece tsp) return tsp.template();
            if (tm == null || !(piece instanceof PoolElementStructurePiece pe)) return null;
            if (!(BeyondStructureCarver.the_beyond$unwrapPoolElement(pe.getElement())
                    instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement spe)) return null;
            return ((com.thebeyond.mixin.SinglePoolElementAccessor) (Object) spe).the_beyond$template().map(tm::getOrCreate, st -> st);
        } catch (Throwable t) {
            return null;
        }
    }

    static int foundationFloor(java.util.List<BoundingBox> boxes) {
        BoundingBox base = boxes.get(0);
        int floor = base.minY();
        for (BoundingBox b : boxes) {
            if (b.maxX() >= base.minX() && b.minX() <= base.maxX() && b.maxZ() >= base.minZ() && b.minZ() <= base.maxZ()
                    && b.minY() < floor && b.minY() >= base.minY() - FOUNDATION_REACH) floor = b.minY();
        }
        return floor;
    }

    static boolean footprintUnfit(int cx, int cz, int floor, int minY, StructureIntegrationProfile profile, int radius) {
        // A wide assembly is sampled coarser, so the test never probes more columns than a 39x39 square.
        int step = Math.max(Math.max(1, profile.towerStep()), (2 * radius + 1 + 38) / 39);
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int pad = 0, total = 0;
        int lo = Math.max(minY, floor - profile.flushTolerance());
        for (int ox = -radius; ox <= radius; ox += step) {
            for (int oz = -radius; oz <= radius; oz += step) {
                total++;
                int wx = cx + ox, wz = cz + oz;
                BeyondEndChunkGenerator.initColumnScratch(wx, wz, (float) Math.sqrt((double) wx * wx + (double) wz * wz), scr);
                boolean flush = false;
                for (int y = floor - 1; y >= lo; y--) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) { flush = true; break; }
                }
                if (!flush) pad++;
            }
        }
        return total > 0 && (double) pad / total > profile.padRejectFraction();
    }
}
