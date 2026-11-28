package com.thebeyond.common.worldgen;

import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

class SubSitesTest {

    private static final long SEED = -109715808474535652L;
    private static final Path OUT = Path.of("build", "test-run", "subsites.txt");
    private static final List<String> LOG = new ArrayList<>();

    private static void log(String line) {
        System.out.println(line);
        LOG.add(line);
        try {
            Files.createDirectories(OUT.getParent());
            Files.write(OUT, LOG);
        } catch (IOException ignored) {
        }
    }

    private static SubSites sites(double w, double h) {
        return new SubSites(new VoronoiNoise(SEED, (short) 0), w, h);
    }

    private static SubSites.Settings settings(BiomeEdgeQuery.Shape shape, SubSites.Count count, double amount,
                                              SubSites.Inner inner, SubSites.Outer outer, Predicate<BlockPos> keep,
                                              Predicate<SubSites.Cell> member, ToDoubleFunction<SubSites.Cell> score) {
        return new SubSites.Settings(shape, count, amount, inner, outer, 7L, keep, member, score);
    }

    private static boolean half(SubSites.Cell c) {
        return Math.floorMod(c.id() * 0x9E3779B97F4A7C15L >>> 33, 10) < 6;
    }

    @Test
    void cellsMatchTheGame() {
        double w = 200, h = 150;
        SubSites s = sites(w, h);
        VoronoiNoise v = new VoronoiNoise(SEED, (short) 0);
        Random r = new Random(1);
        int bad = 0;
        for (int i = 0; i < 20000; i++) {
            double x = (r.nextDouble() - 0.5) * 2e6, y = -64 + r.nextDouble() * 384, z = (r.nextDouble() - 0.5) * 2e6;
            SubSites.Cell c = s.cellAt(x, y, z);
            VoronoiNoise.CellResult3D g = v.getCell(x, y * (w / h), z, 1.0 / w);
            if (c.id() != g.cellId() || Math.abs(c.x() - g.cellX()) > 1e-6 || Math.abs(c.y() - g.cellY() / (w / h)) > 1e-6
                    || Math.abs(c.z() - g.cellZ()) > 1e-6) bad++;
        }
        log("cells: 20000 points, " + bad + " differ from VoronoiNoise.getCell");
        Assertions.assertEquals(0, bad);
    }

    @Test
    void geometryMatchesBlocks() {
        double w = 16, h = 12;
        SubSites s = sites(w, h);
        Random r = new Random(2);
        long blocks = 0, wrong = 0, missing = 0, unseen = 0, faces = 0, polyBug = 0, trueMissing = 0, throughThird = 0, ties = 0;
        double[] err = new double[60];
        for (int t = 0; t < err.length; t++) {
            int lx = r.nextInt(4000) - 2000, ly = r.nextInt(40) - 20, lz = r.nextInt(4000) - 2000;
            SubSites.Cell c = s.cell(lx, ly, lz);
            SubSites.Geometry g = s.geometry(c);
            Set<Long> ids = new HashSet<>();
            for (SubSites.Cell n : g.neighbors) ids.add(n.id());
            Set<Long> touched = new HashSet<>();
            int x0 = (int) Math.floor(c.x() - 3 * w), x1 = (int) Math.ceil(c.x() + 3 * w);
            int y0 = (int) Math.floor(c.y() - 3 * h), y1 = (int) Math.ceil(c.y() + 3 * h);
            int z0 = (int) Math.floor(c.z() - 3 * w), z1 = (int) Math.ceil(c.z() + 3 * w);
            long count = 0;
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean game = s.gameId(x, y, z) == c.id();
                        if (game != g.contains(x, y, z)) {
                            wrong++;
                            if ((trueNearest(s, x / w, y / h, z / w) == c.id()) != g.contains(x, y, z)) {
                                if (tieMargin(s, x / w, y / h, z / w) < 1e-6) {
                                    ties++;
                                } else {
                                    polyBug++;
                                    log("  polytope differs at " + x + " " + y + " " + z + " cell " + c + " contains "
                                            + g.contains(x, y, z) + " true nearest " + trueNearest(s, x / w, y / h, z / w)
                                            + " margin " + tieMargin(s, x / w, y / h, z / w) + " window " + windowOf(s, c, g));
                                }
                            }
                        }
                        if (!game) continue;
                        count++;
                        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                        for (int[] o : d) {
                            long id = s.gameId(x + o[0], y + o[1], z + o[2]);
                            if (id != c.id() && !ids.contains(id)) {
                                long here = trueNearest(s, x / w, y / h, z / w);
                                long there = trueNearest(s, (x + o[0]) / w, (y + o[1]) / h, (z + o[2]) / w);
                                if (here == c.id() && there == id) {
                                    boolean third = false;
                                    for (int q = 1; q < 64 && !third; q++) {
                                        double f = q / 64.0;
                                        long mid = trueNearest(s, (x + f * o[0]) / w, (y + f * o[1]) / h, (z + f * o[2]) / w);
                                        third = mid != c.id() && mid != id;
                                    }
                                    if (third) throughThird++;
                                    else trueMissing++;
                                }
                            }
                            if (id != c.id()) touched.add(id);
                        }
                    }
                }
            }
            for (long id : touched) if (!ids.contains(id)) missing++;
            for (long id : ids) if (!touched.contains(id)) unseen++;
            faces += ids.size();
            blocks += count;
            err[t] = Math.abs(count - g.volume) / g.volume;
        }
        Arrays.sort(err);
        log("geometry: 60 cells, " + blocks + " blocks, " + wrong + " blocks where polytope and game disagree, "
                + missing + " block-touching neighbors missing, " + unseen + " of " + faces
                + " polytope neighbors with no touching block, volume error p50 " + err[30] + " max " + err[59]
                + "; against the true nearest site: " + polyBug + " disagreeing blocks (" + ties + " more on a tie), "
                + trueMissing + " missing neighbor touches (" + throughThird + " more cross a third cell between the blocks)");
        Assertions.assertEquals(0, polyBug);
        Assertions.assertEquals(0, trueMissing);
    }

    private static String windowOf(SubSites s, SubSites.Cell c, SubSites.Geometry g) {
        int far = 0;
        for (SubSites.Cell n : g.neighbors) {
            far = Math.max(far, Math.max(Math.abs(n.lx() - c.lx()), Math.max(Math.abs(n.ly() - c.ly()),
                    Math.abs(n.lz() - c.lz()))));
        }
        return "neighbors " + g.neighbors.size() + " farthest lattice step " + far + " radius " + g.radius;
    }

    /** Gap between the nearest and second nearest site distances, in lattice units. */
    private static double tieMargin(SubSites s, double sx, double sy, double sz) {
        int fx = (int) Math.floor(sx), fy = (int) Math.floor(sy), fz = (int) Math.floor(sz);
        double a = Double.MAX_VALUE, b = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
            SubSites.Cell c = s.cell(fx + dx, fy + dy, fz + dz);
            double d = Math.sqrt(dist2(new double[]{c.x() / s.width(), c.y() / s.height(), c.z() / s.width()},
                    new double[]{sx, sy, sz}));
            if (d < a) {
                b = a;
                a = d;
            } else if (d < b) {
                b = d;
            }
        }
        return b - a;
    }

    /** Nearest site over a 9x9x9 lattice window, wider than the game's 5x5x5. */
    private static long trueNearest(SubSites s, double sx, double sy, double sz) {
        int fx = (int) Math.floor(sx), fy = (int) Math.floor(sy), fz = (int) Math.floor(sz);
        double best = Double.MAX_VALUE;
        long id = 0;
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
            SubSites.Cell c = s.cell(fx + dx, fy + dy, fz + dz);
            double d = dist2(new double[]{c.x() / s.width(), c.y() / s.height(), c.z() / s.width()}, new double[]{sx, sy, sz});
            if (d < best) {
                best = d;
                id = c.id();
            }
        }
        return id;
    }

    @Test
    void layoutsFollowTheirSettings() {
        double w = 64, h = 48;
        Random r = new Random(3);
        int checked = 0, outside = 0, filtered = 0, short4 = 0, flatOff = 0, differ = 0;
        List<Double> ratio = new ArrayList<>();
        Predicate<BlockPos> low = p -> p.getY() < 0;
        for (int t = 0; t < 150; t++) {
            int lx = r.nextInt(2000) - 1000, ly = r.nextInt(8) - 4, lz = r.nextInt(2000) - 1000;
            SubSites a = sites(w, h), b = sites(w, h);
            SubSites.Cell c = a.cell(lx, ly, lz);
            for (BiomeEdgeQuery.Shape shape : BiomeEdgeQuery.Shape.values()) {
                SubSites.Settings fixed = settings(shape, SubSites.Count.FIXED, 4, SubSites.Inner.DELAUNAY,
                        SubSites.Outer.NONE, p -> true, x -> true, x -> 0);
                SubSites.Layout la = a.network(fixed).layout(c), lb = b.network(fixed).layout(b.cell(lx, ly, lz));
                if (!la.equals(lb)) differ++;
                if (la.subs().size() != 4) short4++;
                for (SubSites.Site site : la.subs()) {
                    checked++;
                    if (a.gameId(site.x(), site.y(), site.z()) != c.id()) outside++;
                    if (shape == BiomeEdgeQuery.Shape.HORIZONTAL && site.y() != c.y()) flatOff++;
                }
                SubSites.Settings dense = settings(shape, SubSites.Count.PER_VOLUME,
                        shape == BiomeEdgeQuery.Shape.HORIZONTAL ? 200 : 8000, SubSites.Inner.NONE, SubSites.Outer.NONE,
                        p -> true, x -> true, x -> 0);
                SubSites.Network n = a.network(dense);
                SubSites.Geometry g = n.geometry(c);
                double room = shape == BiomeEdgeQuery.Shape.HORIZONTAL ? g.sliceArea(c.y()) : g.volume;
                if (room / dense.amount() >= 4) ratio.add(n.layout(c).subs().size() / (room / dense.amount()));
                SubSites.Settings under = settings(shape, SubSites.Count.FIXED, 6, SubSites.Inner.HUB, SubSites.Outer.NONE,
                        low, x -> true, x -> 0);
                for (SubSites.Site site : a.network(under).layout(c).subs()) {
                    if (!low.test(BlockPos.containing(site.x(), site.y(), site.z()))) filtered++;
                }
            }
        }
        ratio.sort(null);
        log("layout: " + checked + " sub-sites, " + outside + " outside their game cell, " + filtered
                + " failing the filter, " + flatOff + " horizontal off the core plane, " + short4 + " fixed layouts short of 4, "
                + differ + " differ between two instances; per-volume count / expected p10 "
                + ratio.get(ratio.size() / 10) + " p50 " + ratio.get(ratio.size() / 2) + " p90 "
                        + ratio.get(ratio.size() * 9 / 10));
        Assertions.assertEquals(0, outside);
        Assertions.assertEquals(0, filtered);
        Assertions.assertEquals(0, flatOff);
        Assertions.assertEquals(0, differ);
    }

    @Test
    void delaunayMatchesEmptySpheres() {
        Random r = new Random(4);
        int sets = 0, wrong = 0, edges = 0;
        for (int t = 0; t < 400; t++) {
            boolean flat = t % 2 == 1;
            int n = 4 + r.nextInt(6);
            double[][] p = new double[n][];
            for (int i = 0; i < n; i++) p[i] = new double[]{r.nextDouble() * 100, flat ? 5 : r.nextDouble() * 100,
                    r.nextDouble() * 100};
            Set<Long> got = new HashSet<>();
            for (int[] e : SubSites.delaunay(p)) got.add((long) e[0] * 64 + e[1]);
            Set<Long> want = flat ? brute2(p) : brute3(p);
            sets++;
            edges += want.size();
            if (!got.equals(want)) {
                wrong++;
                Set<Long> extra = new HashSet<>(got), miss = new HashSet<>(want);
                extra.removeAll(want);
                miss.removeAll(got);
                double margin = 0;
                for (long e : miss) margin = Math.max(margin, flat ? margin2(p, (int) (e / 64), (int) (e % 64))
                        : margin3(p, (int) (e / 64), (int) (e % 64)));
                log("  delaunay differs: flat " + flat + " n " + n + " extra " + extra + " missing " + miss
                        + ", best emptiness margin of a missing edge " + margin);
            }
        }
        log("delaunay: " + sets + " point sets (half coplanar), " + edges + " edges, " + wrong
                + " sets differ from empty spheres");
        Assertions.assertEquals(0, wrong);
        int big = 0, bigWrong = 0, bruteSets = 0, bruteWrong = 0;
        long fast = 0, slow = 0;
        for (int t = 0; t < 60; t++) {
            boolean flat = t % 2 == 1;
            int n = 20 + r.nextInt(101);
            double[][] p = new double[n][];
            for (int i = 0; i < n; i++) p[i] = new double[]{r.nextDouble() * 100, flat ? 5 : r.nextDouble() * 100,
                    r.nextDouble() * 100};
            long t0 = System.nanoTime();
            Set<Long> got = new HashSet<>();
            for (int[] e : SubSites.delaunay(p)) got.add((long) e[0] * 256 + e[1]);
            long t1 = System.nanoTime();
            Set<Long> old = new HashSet<>();
            for (int[] e : delaunayByIndexOrder(p)) old.add((long) e[0] * 256 + e[1]);
            long t2 = System.nanoTime();
            fast += t1 - t0;
            slow += t2 - t1;
            big++;
            if (!got.equals(old)) {
                bigWrong++;
                Set<Long> extra = new HashSet<>(got), miss = new HashSet<>(old);
                extra.removeAll(old);
                miss.removeAll(got);
                log("  delaunay differs from the index-order clipping: flat " + flat + " n " + n + " extra " + extra
                        + " missing " + miss);
            }
            if (n <= 30) {
                bruteSets++;
                Set<Long> want = new HashSet<>();
                for (long e : flat ? brute2(p) : brute3(p)) want.add(e / 64 * 256 + e % 64);
                if (!got.equals(want)) bruteWrong++;
            }
        }
        log("delaunay, 60 sets of 20 to 120 points (half coplanar): " + bigWrong + " differ from clipping in index order, "
                + bruteWrong + " of " + bruteSets + " sets up to 30 points differ from empty spheres | time " + fast / 1000000
                + " ms against " + slow / 1000000 + " ms");
        Assertions.assertEquals(0, bigWrong);
        Assertions.assertEquals(0, bruteWrong);
    }

    /** The first Delaunay: each bisector square clipped by every other site in index order. */
    private static List<int[]> delaunayByIndexOrder(double[][] input) {
        double extent = 1;
        for (double[] a : input) {
            for (double[] b : input) extent = Math.max(extent, Math.sqrt(dist2(a, b)));
        }
        double big = extent * 1e6, eps = 1e-9;
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < input.length; i++) {
            double[][] p = new double[input.length][];
            for (int k = 0; k < p.length; k++) {
                p[k] = new double[]{input[k][0] - input[i][0], input[k][1] - input[i][1], input[k][2] - input[i][2]};
            }
            for (int j = i + 1; j < p.length; j++) {
                double[] n = p[j];
                double[] m = {p[j][0] / 2, p[j][1] / 2, p[j][2] / 2};
                double[] u = Math.abs(n[0]) < 0.9 * Math.sqrt(dist2(n, new double[3])) ? cross(n, new double[]{1, 0, 0})
                        : cross(n, new double[]{0, 1, 0});
                double lu = Math.sqrt(dist2(u, new double[3]));
                double[] v = cross(n, u);
                double lv = Math.sqrt(dist2(v, new double[3]));
                List<double[]> f = new ArrayList<>();
                for (int k = 0; k < 4; k++) {
                    double su = (k == 0 || k == 3 ? -big : big) / lu, sv = (k < 2 ? -big : big) / lv;
                    f.add(new double[]{m[0] + su * u[0] + sv * v[0], m[1] + su * u[1] + sv * v[1], m[2] + su * u[2] + sv * v[2]});
                }
                for (int k = 0; k < p.length && f.size() >= 3; k++) {
                    if (k == i || k == j) continue;
                    double d = dist2(p[k], new double[3]) / 2, e = eps * Math.max(1, d);
                    List<double[]> out = new ArrayList<>();
                    for (int a = 0; a < f.size(); a++) {
                        double[] x = f.get(a), y = f.get((a + 1) % f.size());
                        double sx = p[k][0] * x[0] + p[k][1] * x[1] + p[k][2] * x[2] - d;
                        double sy = p[k][0] * y[0] + p[k][1] * y[1] + p[k][2] * y[2] - d;
                        if (sx <= e) out.add(x);
                        if ((sx < -e && sy > e) || (sx > e && sy < -e)) {
                            double s = sx / (sx - sy);
                            out.add(new double[]{x[0] + s * (y[0] - x[0]), x[1] + s * (y[1] - x[1]), x[2] + s * (y[2] - x[2])});
                        }
                    }
                    f = out;
                }
                if (f.size() >= 3) {
                    double nx = 0, ny = 0, nz = 0;
                    for (int a = 0; a < f.size(); a++) {
                        double[] x = f.get(a), y = f.get((a + 1) % f.size());
                        nx += (x[1] - y[1]) * (x[2] + y[2]);
                        ny += (x[2] - y[2]) * (x[0] + y[0]);
                        nz += (x[0] - y[0]) * (x[1] + y[1]);
                    }
                    if (Math.sqrt(nx * nx + ny * ny + nz * nz) / 2 > eps * extent * extent) edges.add(new int[]{i, j});
                }
            }
        }
        return edges;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    @Test
    void windowShortcutMatchesTheGame() {
        Random r = new Random(10);
        int points = 0, taken = 0, falseTaken = 0;
        for (double[] size : new double[][]{{200, 200}, {48, 40}, {400, 100}}) {
            SubSites s = sites(size[0], size[1]);
            for (int t = 0; t < 4000; t++) {
                double x = (r.nextDouble() * 2 - 1) * 1e5, y = r.nextDouble() * 384 - 64, z = (r.nextDouble() * 2 - 1) * 1e5;
                SubSites.Cell game = s.cellAt(x, y, z);
                points++;
                int fx = (int) Math.floor(x / size[0]), fy = (int) Math.floor(y / size[1]), fz = (int) Math.floor(z / size[0]);
                for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                    SubSites.Cell c = s.cell(fx + dx, fy + dy, fz + dz);
                    if (s.geometry(c).depth(x, y, z) > -1e-6 || !s.inWindow(c, x, y, z)) continue;
                    if (c.id() == game.id()) {
                        taken++;
                    } else {
                        falseTaken++;
                    }
                }
            }
        }
        log("window shortcut: " + points + " points, " + taken + " answered without the game's search, " + falseTaken
                + " would take a cell the game does not pick");
        Assertions.assertEquals(0, falseTaken);
    }

    @Test
    void regionMatchesNearest() {
        double w = 48, h = 40;
        SubSites s = sites(w, h);
        Random r = new Random(9);
        int regions = 0, points = 0, outside = 0, siteWrong = 0, linkWrong = 0, cellWrong = 0, near = 0;
        for (SubSites.Outer outer : new SubSites.Outer[]{SubSites.Outer.CLOSEST, SubSites.Outer.TREE}) {
            for (SubSites.Count count : SubSites.Count.values()) {
                SubSites.Network net = s.network(settings(BiomeEdgeQuery.Shape.SPHERE, count,
                        count == SubSites.Count.FIXED ? 6 : 4000, SubSites.Inner.DELAUNAY, outer, p -> true,
                        SubSitesTest::half, SubSites.Cell::y));
                for (int t = 0; t < 40; t++) {
                    double cx = r.nextDouble() * 5 * w, cy = r.nextDouble() * 5 * h, cz = r.nextDouble() * 5 * w;
                    double spread = 2 + r.nextDouble() * 30, siteReach = t % 2 == 0 ? 400 : 6 + r.nextDouble() * 10;
                    double linkReach = t % 3 == 0 ? 400 : 1 + r.nextDouble() * 4;
                    SubSites.Network.Region region = net.region(cx, cy, cz, spread, siteReach, linkReach);
                    regions++;
                    for (int q = 0; q < 50; q++) {
                        double rad = spread * (q < 45 ? Math.cbrt(r.nextDouble()) : 1 + r.nextDouble());
                        double a = r.nextDouble() * 2 * Math.PI, b = Math.acos(2 * r.nextDouble() - 1);
                        double x = cx + rad * Math.sin(b) * Math.cos(a), y = cy + rad * Math.cos(b);
                        double z = cz + rad * Math.sin(b) * Math.sin(a);
                        SubSites.Hit got = region.at(x, y, z);
                        SubSites.Hit bySite = net.at(x, y, z, siteReach), byLink = net.at(x, y, z, linkReach);
                        points++;
                        if (rad > spread) outside++;
                        if (got.linkDistance() <= linkReach) near++;
                        if (!same(got.siteDistance(), bySite.siteDistance())) siteWrong++;
                        if (!same(got.linkDistance(), byLink.linkDistance())) linkWrong++;
                        if (!got.cell().equals(bySite.cell())) cellWrong++;
                    }
                }
            }
        }
        log("region: " + regions + " regions, " + points + " points (" + outside + " outside the spread, " + near
                + " near a link), " + siteWrong + " site, " + linkWrong + " link and " + cellWrong
                + " cell answers differ from at()");
        Assertions.assertEquals(0, siteWrong);
        Assertions.assertEquals(0, linkWrong);
        Assertions.assertEquals(0, cellWrong);
    }

    private static boolean same(double a, double b) {
        return a == b || Math.abs(a - b) <= 1e-9;
    }

    private static Set<Long> brute3(double[][] p) {
        Set<Long> out = new HashSet<>();
        int n = p.length;
        for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) for (int c = b + 1; c < n; c++) for (int d = c
                + 1; d < n; d++) {
            double[] o = sphere(p[a], p[b], p[c], p[d]);
            if (o == null) continue;
            boolean empty = true;
            for (int k = 0; k < n && empty; k++) {
                if (k == a || k == b || k == c || k == d) continue;
                if (dist2(p[k], o) < o[3] * (1 - 1e-9)) empty = false;
            }
            if (!empty) continue;
            int[] q = {a, b, c, d};
            for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) out.add((long) q[i] * 64 + q[j]);
        }
        return out;
    }

    /** Largest relative gap between a sphere through edge (i, j) and two more points and the nearest other point (0 on a tie). */
    private static double margin3(double[][] p, int i, int j) {
        double best = -Double.MAX_VALUE;
        for (int c = 0; c < p.length; c++) for (int d = c + 1; d < p.length; d++) {
            if (c == i || c == j || d == i || d == j) continue;
            double[] o = sphere(p[i], p[j], p[c], p[d]);
            if (o == null) continue;
            double m = Double.MAX_VALUE;
            for (int k = 0; k < p.length; k++) if (k != i && k != j && k != c && k != d) m = Math.min(m, dist2(p[k],
                    o) / o[3] - 1);
            best = Math.max(best, m);
        }
        return best;
    }

    private static double margin2(double[][] p, int i, int j) {
        double best = -Double.MAX_VALUE;
        for (int c = 0; c < p.length; c++) {
            if (c == i || c == j) continue;
            double ax = p[i][0], az = p[i][2], bx = p[j][0] - ax, bz = p[j][2] - az, cx = p[c][0] - ax, cz = p[c][2] - az;
            double d = 2 * (bx * cz - bz * cx);
            if (Math.abs(d) < 1e-12) continue;
            double ux = (cz * (bx * bx + bz * bz) - bz * (cx * cx + cz * cz)) / d;
            double uz = (bx * (cx * cx + cz * cz) - cx * (bx * bx + bz * bz)) / d;
            double r2 = ux * ux + uz * uz, m = Double.MAX_VALUE;
            for (int k = 0; k < p.length; k++) {
                if (k == i || k == j || k == c) continue;
                double ex = p[k][0] - ax - ux, ez = p[k][2] - az - uz;
                m = Math.min(m, (ex * ex + ez * ez) / r2 - 1);
            }
            best = Math.max(best, m);
        }
        return best;
    }

    private static Set<Long> brute2(double[][] p) {
        Set<Long> out = new HashSet<>();
        int n = p.length;
        for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) for (int c = b + 1; c < n; c++) {
            double ax = p[a][0], az = p[a][2], bx = p[b][0] - ax, bz = p[b][2] - az, cx = p[c][0] - ax, cz = p[c][2] - az;
            double d = 2 * (bx * cz - bz * cx);
            if (Math.abs(d) < 1e-12) continue;
            double ux = (cz * (bx * bx + bz * bz) - bz * (cx * cx + cz * cz)) / d;
            double uz = (bx * (cx * cx + cz * cz) - cx * (bx * bx + bz * bz)) / d;
            double r2 = ux * ux + uz * uz;
            boolean empty = true;
            for (int k = 0; k < n && empty; k++) {
                if (k == a || k == b || k == c) continue;
                double ex = p[k][0] - ax - ux, ez = p[k][2] - az - uz;
                if (ex * ex + ez * ez < r2 * (1 - 1e-9)) empty = false;
            }
            if (!empty) continue;
            out.add((long) a * 64 + b);
            out.add((long) a * 64 + c);
            out.add((long) b * 64 + c);
        }
        if (n == 2) out.add(1L);
        return out;
    }

    private static double[] sphere(double[] a, double[] b, double[] c, double[] d) {
        double[][] m = new double[3][4];
        double[][] q = {b, c, d};
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) m[i][k] = 2 * (q[i][k] - a[k]);
            m[i][3] = dist2(q[i], new double[]{0, 0, 0, 0}) - dist2(a, new double[]{0, 0, 0, 0});
        }
        for (int col = 0; col < 3; col++) {
            int piv = col;
            for (int i = col + 1; i < 3; i++) if (Math.abs(m[i][col]) > Math.abs(m[piv][col])) piv = i;
            if (Math.abs(m[piv][col]) < 1e-9) return null;
            double[] tmp = m[col];
            m[col] = m[piv];
            m[piv] = tmp;
            for (int i = 0; i < 3; i++) {
                if (i == col) continue;
                double f = m[i][col] / m[col][col];
                for (int k = col; k < 4; k++) m[i][k] -= f * m[col][k];
            }
        }
        double[] o = {m[0][3] / m[0][0], m[1][3] / m[1][1], m[2][3] / m[2][2], 0};
        o[3] = dist2(a, o);
        return o;
    }

    private static double dist2(double[] a, double[] b) {
        return (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]);
    }

    @Test
    void treeIsMinimal() {
        Random r = new Random(5);
        int wrong = 0;
        for (int t = 0; t < 300; t++) {
            int n = 2 + r.nextInt(12);
            double[][] p = new double[n][];
            for (int i = 0; i < n; i++) p[i] = new double[]{r.nextDouble() * 50, r.nextDouble() * 50, r.nextDouble() * 50};
            List<int[]> e = SubSites.tree(p);
            double got = 0;
            int[] root = new int[n];
            for (int i = 0; i < n; i++) root[i] = i;
            for (int[] k : e) {
                got += Math.sqrt(dist2(p[k[0]], p[k[1]]));
                union(root, k[0], k[1]);
            }
            Set<Integer> parts = new HashSet<>();
            for (int i = 0; i < n; i++) parts.add(find(root, i));
            List<int[]> all = new ArrayList<>();
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) all.add(new int[]{i, j});
            all.sort((x, y) -> Double.compare(dist2(p[x[0]], p[x[1]]), dist2(p[y[0]], p[y[1]])));
            int[] kr = new int[n];
            for (int i = 0; i < n; i++) kr[i] = i;
            double want = 0;
            for (int[] k : all) {
                if (find(kr, k[0]) != find(kr, k[1])) {
                    union(kr, k[0], k[1]);
                    want += Math.sqrt(dist2(p[k[0]], p[k[1]]));
                }
            }
            if (e.size() != n - 1 || parts.size() != 1 || Math.abs(got - want) > 1e-9) wrong++;
        }
        log("tree: 300 point sets, " + wrong + " not a minimum spanning tree");
        Assertions.assertEquals(0, wrong);
    }

    private static int find(int[] r, int i) {
        while (r[i] != i) i = r[i] = r[r[i]];
        return i;
    }

    private static void union(int[] r, int a, int b) {
        r[find(r, a)] = find(r, b);
    }

    @Test
    void outerLinksAgreeAndClimbToCores() {
        double w = 64, h = 48;
        SubSites s = sites(w, h);
        Random r = new Random(6);
        double tx = 3000, ty = 80, tz = -2000;
        SubSites.Cell target = s.cellAt(tx, ty, tz);
        ToDoubleFunction<SubSites.Cell> height = SubSites.Cell::y;
        ToDoubleFunction<SubSites.Cell> toward = c -> -Math.sqrt(dist2(new double[]{c.x(), c.y(), c.z()}, new double[]{tx, ty,
                tz}));
        String[] names = {"height, 60% members", "toward a point, all members", "toward a point, 60% members"};
        Object[][] cases = {{height, (Predicate<SubSites.Cell>) SubSitesTest::half},
                {toward, (Predicate<SubSites.Cell>) c -> true}, {toward, (Predicate<SubSites.Cell>) SubSitesTest::half}};
        for (int k = 0; k < cases.length; k++) {
            @SuppressWarnings("unchecked") ToDoubleFunction<SubSites.Cell> score = (ToDoubleFunction<SubSites.Cell>) cases[k][0];
            @SuppressWarnings("unchecked") Predicate<SubSites.Cell> member = (Predicate<SubSites.Cell>) cases[k][1];
            SubSites.Network net = s.network(settings(BiomeEdgeQuery.Shape.SPHERE, SubSites.Count.FIXED, 3,
                    SubSites.Inner.TREE, SubSites.Outer.TREE, p -> true, member, score));
            int starts = 0, loops = 0, atTarget = 0, asym = 0, cores = 0;
            int[] steps = new int[200];
            for (int t = 0; t < steps.length; t++) {
                SubSites.Cell c = s.cell(target.lx() + r.nextInt(17) - 8, target.ly() + r.nextInt(7) - 3, target.lz()
                        + r.nextInt(17) - 8);
                if (!net.member(c)) continue;
                starts++;
                Set<SubSites.Cell> seen = new LinkedHashSet<>();
                SubSites.Cell at = c;
                while (at != null && seen.add(at)) {
                    for (SubSites.Link l : net.outer(at)) {
                        SubSites.Cell other = l.a().cell().equals(at) ? l.b().cell() : l.a().cell();
                        if (!net.outer(other).contains(l)) asym++;
                    }
                    SubSites.Cell up = net.parent(at);
                    if (up != null && !net.geometry(at).neighbors.contains(up)) loops++;
                    if (up == null) cores++;
                    at = up;
                }
                if (at != null) loops++;
                SubSites.Cell last = (SubSites.Cell) seen.toArray()[seen.size() - 1];
                if (last.equals(target)) atTarget++;
                steps[t] = seen.size();
            }
            Arrays.sort(steps);
            log("outer tree, " + names[k] + ": " + starts + " member starts, " + loops + " loops or non-neighbor parents, "
                    + asym + " one-sided links, " + atTarget + " reach the target cell, longest path " + steps[steps.length - 1]);
            Assertions.assertEquals(0, loops);
            Assertions.assertEquals(0, asym);
            if (k == 1) Assertions.assertEquals(starts, atTarget);
        }
    }

    @Test
    void nearestMatchesBruteForce() {
        double w = 48, h = 40;
        SubSites s = sites(w, h);
        Random r = new Random(7);
        for (SubSites.Outer outer : new SubSites.Outer[]{SubSites.Outer.CLOSEST, SubSites.Outer.TREE}) {
            SubSites.Network net = s.network(settings(BiomeEdgeQuery.Shape.SPHERE, SubSites.Count.FIXED, 4,
                    SubSites.Inner.DELAUNAY, outer, p -> true, SubSitesTest::half, SubSites.Cell::y));
            int siteWrong = 0, linkWrong = 0, points = 0, found = 0;
            double reach = w;
            for (int t = 0; t < 400; t++) {
                double x = r.nextDouble() * 5 * w, y = r.nextDouble() * 5 * h, z = r.nextDouble() * 5 * w;
                SubSites.Hit hit = net.at(x, y, z, reach);
                int fx = (int) Math.floor(x / w), fy = (int) Math.floor(y / h), fz = (int) Math.floor(z / w);
                double siteD = Double.POSITIVE_INFINITY, linkD = Double.POSITIVE_INFINITY;
                for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
                    SubSites.Cell c = s.cell(fx + dx, fy + dy, fz + dz);
                    SubSites.Layout l = net.layout(c);
                    if (l == null) continue;
                    for (SubSites.Site site : SubSites.sites(l)) {
                        siteD = Math.min(siteD, Math.sqrt(dist2(new double[]{site.x(), site.y(), site.z()}, new double[]{x, y,
                                z})));
                    }
                    for (SubSites.Link k : l.links()) linkD = Math.min(linkD, Math.sqrt(SubSites.distance2(k, x, y, z)));
                    for (SubSites.Link k : net.outer(c)) linkD = Math.min(linkD, Math.sqrt(SubSites.distance2(k, x, y, z)));
                }
                if (siteD > reach) siteD = Double.POSITIVE_INFINITY;
                if (linkD > reach) linkD = Double.POSITIVE_INFINITY;
                points++;
                if (hit.linkDistance() < reach) found++;
                if (siteD != hit.siteDistance() && Math.abs(siteD - hit.siteDistance()) > 1e-9) siteWrong++;
                if (linkD != hit.linkDistance() && Math.abs(linkD - hit.linkDistance()) > 1e-9) linkWrong++;
            }
            log("nearest within " + reach + " blocks, outer " + outer + ": " + points + " points (" + found + " near a link), "
                    + siteWrong + " site and " + linkWrong + " link distances differ from the 9x9x9 brute force");
            Assertions.assertEquals(0, siteWrong);
            Assertions.assertEquals(0, linkWrong);
        }
    }

    @Test
    void cost() {
        SubSites s = sites(200, 200);
        Random r = new Random(8);
        int n = 300;
        long[] geo = new long[n], lay = new long[n], at = new long[n], warm = new long[n];
        for (int t = 0; t < n; t++) {
            double x = r.nextDouble() * 1e6 - 5e5, y = r.nextDouble() * 384 - 64, z = r.nextDouble() * 1e6 - 5e5;
            SubSites.Network net = s.network(settings(BiomeEdgeQuery.Shape.SPHERE, SubSites.Count.FIXED, 4,
                    SubSites.Inner.DELAUNAY, SubSites.Outer.TREE, p -> true, SubSitesTest::half, SubSites.Cell::y));
            long t0 = System.nanoTime();
            SubSites.Cell c = s.cellAt(x, y, z);
            net.geometry(c);
            long t1 = System.nanoTime();
            net.layout(c);
            long t2 = System.nanoTime();
            net.at(x, y, z, 16);
            long t3 = System.nanoTime();
            net.at(x + 1, y, z, 16);
            long t4 = System.nanoTime();
            geo[t] = t1 - t0;
            lay[t] = t2 - t1;
            at[t] = t3 - t2;
            warm[t] = t4 - t3;
        }
        log("cost (us, p50/p90/max, cells 200 blocks, 4 sub-sites, Delaunay, tree, reach 16): geometry " + pct(geo)
                + ", layout " + pct(lay) + ", first query of a network " + pct(at) + ", next query " + pct(warm));
        long[] walk = new long[1024];
        for (int t = 0; t < walk.length; t++) {
            SubSites.Network net = s.network(settings(BiomeEdgeQuery.Shape.SPHERE, SubSites.Count.FIXED, 4,
                    SubSites.Inner.DELAUNAY, SubSites.Outer.TREE, p -> true, SubSitesTest::half, SubSites.Cell::y));
            long t0 = System.nanoTime();
            net.at(900000 + (t % 32) * 16 + 8, 60, 700000 + (t / 32) * 16 + 8, 16);
            walk[t] = System.nanoTime() - t0;
        }
        log("cost walking 32x32 chunks, a new network per chunk, shared geometry cache (us, p50/p90/max): " + pct(walk));
    }

    private static String pct(long[] v) {
        long[] c = v.clone();
        Arrays.sort(c);
        return c[c.length / 2] / 1000 + "/" + c[c.length * 9 / 10] / 1000 + "/" + c[c.length - 1] / 1000;
    }
}
