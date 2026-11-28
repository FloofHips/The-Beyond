package com.thebeyond.common.worldgen;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Sub-sites in the biome source's Voronoi cells and their links, built from each cell and its neighbors alone. */
public final class SubSites {

    public enum Count { FIXED, PER_VOLUME }

    public enum Inner { NONE, HUB, TREE, DELAUNAY }

    /** TREE links each member cell to its best-scored member neighbor, so the links climb the score to a core cell. */
    public enum Outer { NONE, CORES, CLOSEST, TREE }

    public record Cell(int lx, int ly, int lz, long id, double x, double y, double z) {}

    public record Site(Cell cell, int index, double x, double y, double z) {}

    public record Link(Site a, Site b, Vec3 door) {}

    /** amount is the site count (FIXED) or blocks per site (PER_VOLUME, area per site when HORIZONTAL). */
    public record Settings(BiomeEdgeQuery.Shape shape, Count count, double amount, Inner inner, Outer outer, long salt,
                           Predicate<BlockPos> keep, Predicate<Cell> member, ToDoubleFunction<Cell> score) {}

    public record Layout(Site core, List<Site> subs, List<Link> links) {}

    public record Hit(Cell cell, Site site, double siteDistance, Link link, double linkDistance) {}

    private record GeoKey(long seed, double width, double height, int lx, int ly, int lz) {}

    private static final int POOL = 8, MAX_SITES = 256, CACHE = 2048;
    private static final double EPS = 1e-9, INSIDE = 1e-6;
    // geometry depends only on the seed, the cell size and the lattice corner, so a cached one is never stale
    private static final Cache<GeoKey, Geometry> GEOMETRY = CacheBuilder.newBuilder().maximumSize(CACHE).build();

    private final VoronoiNoise voronoi;
    private final double width, height;

    public SubSites(VoronoiNoise voronoi, double width, double height) {
        this.voronoi = voronoi;
        this.width = width;
        this.height = height;
    }

    public static SubSites of() {
        return new SubSites(new VoronoiNoise(BeyondEndChunkGenerator.seed, (short) 0), BiomeCellScale.cellWidth(),
                BiomeCellScale.cellHeight());
    }

    double width() {
        return width;
    }

    double height() {
        return height;
    }

    public Network network(Settings settings) {
        return new Network(settings);
    }

    public Cell cell(int lx, int ly, int lz) {
        double[] p = new double[3];
        long id = voronoi.site3D(lx, ly, lz, p);
        return new Cell(lx, ly, lz, id, p[0] * width, p[1] * height, p[2] * width);
    }

    /** Whether the game's 125 candidates for this point (2 lattice steps around its corner) include the cell's site. */
    boolean inWindow(Cell c, double x, double y, double z) {
        double f = 1.0 / width;
        return Math.abs(corner(x * f) - c.lx()) <= 2 && Math.abs(corner(y * (width / height) * f) - c.ly()) <= 2
                && Math.abs(corner(z * f) - c.lz()) <= 2;
    }

    private static int corner(double v) {
        return (int) (v > 0 ? v : v - 1);
    }

    long gameId(double x, double y, double z) {
        return voronoi.cellId3D(x, y * (width / height), z, 1.0 / width);
    }

    /** The cell BeyondEndBiomeSource picks for this point. */
    public Cell cellAt(double x, double y, double z) {
        long id = gameId(x, y, z);
        int fx = Mth.floor(x / width), fy = Mth.floor(y / height), fz = Mth.floor(z / width);
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                        Cell c = cell(fx + dx, fy + dy, fz + dz);
                        if (c.id() == id) return c;
                    }
                }
            }
        }
        throw new IllegalStateException("no lattice corner near " + x + " " + y + " " + z + " has cell id " + id);
    }

    /** The cell's exact Voronoi polytope, kept in units where the lattice is 1 apart. */
    public final class Geometry {
        public final Cell cell;
        public final List<Cell> neighbors;
        public final double volume;
        /** Farthest corner from the site, in blocks. */
        public final double radius;
        private final double[][] planes;
        private final double[] blockNorm;
        private final List<double[][]> faces;
        private final Map<Long, double[][]> faceOf;
        private final double[] box;

        private Geometry(Cell cell, List<Cell> neighbors, double[][] planes, List<double[][]> faces,
                         Map<Long, double[][]> faceOf) {
            this.cell = cell;
            this.neighbors = neighbors;
            this.planes = planes;
            this.faces = faces;
            this.faceOf = faceOf;
            blockNorm = new double[planes.length];
            for (int i = 0; i < planes.length; i++) {
                blockNorm[i] = Math.sqrt(sq(planes[i][0] / width, planes[i][1] / height, planes[i][2] / width));
            }
            double[] a = {0, 0, 0};
            double v = 0, r = 0;
            box = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                    -Double.MAX_VALUE};
            for (double[][] f : faces) {
                double[] n = newell(f);
                v += Math.abs((f[0][0] - a[0]) * n[0] + (f[0][1] - a[1]) * n[1] + (f[0][2] - a[2]) * n[2]) / 6;
                for (double[] p : f) {
                    r = Math.max(r, sq((p[0] - a[0]) * width, (p[1] - a[1]) * height, (p[2] - a[2]) * width));
                    for (int k = 0; k < 3; k++) {
                        box[k] = Math.min(box[k], p[k]);
                        box[k + 3] = Math.max(box[k + 3], p[k]);
                    }
                }
            }
            volume = v * width * width * height;
            radius = Math.sqrt(r);
        }

        /** The largest face plane excess in lattice units: at most EPS for a point of the cell, negative inside. */
        double depth(double x, double y, double z) {
            double px = (x - cell.x()) / width, py = (y - cell.y()) / height, pz = (z - cell.z()) / width;
            double m = -Double.MAX_VALUE;
            for (double[] pl : planes) m = Math.max(m, pl[0] * px + pl[1] * py + pl[2] * pz - pl[3]);
            return m;
        }

        public boolean contains(double x, double y, double z) {
            double[] p = local(cell, x, y, z);
            for (double[] pl : planes) {
                if (pl[0] * p[0] + pl[1] * p[1] + pl[2] * p[2] > pl[3] + EPS) return false;
            }
            return true;
        }

        /** A lower bound in blocks on the distance from a point to this cell (0 inside it): its farthest face plane. */
        public double lowerBound(double x, double y, double z) {
            double px = (x - cell.x()) / width, py = (y - cell.y()) / height, pz = (z - cell.z()) / width;
            double best = 0;
            for (int i = 0; i < planes.length; i++) {
                double[] pl = planes[i];
                best = Math.max(best, (pl[0] * px + pl[1] * py + pl[2] * pz - pl[3]) / blockNorm[i]);
            }
            return best;
        }

        /** Area in blocks of the cell's cut by the horizontal plane at block height y. */
        public double sliceArea(double y) {
            double sy = (y - cell.y()) / height;
            List<double[]> pts = new ArrayList<>();
            for (double[][] f : faces) {
                for (int i = 0; i < f.length; i++) {
                    double[] p = f[i], q = f[(i + 1) % f.length];
                    if ((p[1] - sy) * (q[1] - sy) > 0 || p[1] == q[1]) continue;
                    double t = (sy - p[1]) / (q[1] - p[1]);
                    pts.add(new double[]{p[0] + t * (q[0] - p[0]), p[2] + t * (q[2] - p[2])});
                }
            }
            if (pts.size() < 3) return 0;
            double mx = 0, mz = 0;
            for (double[] p : pts) {
                mx += p[0] / pts.size();
                mz += p[1] / pts.size();
            }
            final double cx = mx, cz = mz;
            pts.sort(Comparator.comparingDouble(p -> Math.atan2(p[1] - cz, p[0] - cx)));
            double area = 0;
            for (int i = 0; i < pts.size(); i++) {
                double[] p = pts.get(i), q = pts.get((i + 1) % pts.size());
                area += p[0] * q[1] - q[0] * p[1];
            }
            return Math.abs(area) / 2 * width * width;
        }

        /** Where the straight line from a (in this cell) to b (in neighbor n) should cross their shared face, in blocks. */
        Vec3 door(Cell n, Site a, Site b) {
            double[][] f = faceOf.get(Network.key(n));
            double[] pa = local(cell, a.x(), a.y(), a.z()), pb = local(cell, b.x(), b.y(), b.z());
            double[] o = local(cell, n.x(), n.y(), n.z()), c = {0, 0, 0};
            double[] nrm = {o[0] - c[0], o[1] - c[1], o[2] - c[2]};
            double d = (sq(o[0], o[1], o[2]) - sq(c[0], c[1], c[2])) / 2;
            double den = nrm[0] * (pb[0] - pa[0]) + nrm[1] * (pb[1] - pa[1]) + nrm[2] * (pb[2] - pa[2]);
            double t = den == 0 ? 0.5 : Mth.clamp((d - nrm[0] * pa[0] - nrm[1] * pa[1] - nrm[2] * pa[2]) / den, 0, 1);
            double[] x = {pa[0] + t * (pb[0] - pa[0]), pa[1] + t * (pb[1] - pa[1]), pa[2] + t * (pb[2] - pa[2])};
            // project onto the plane, then onto the face when the crossing falls outside it
            double s = (nrm[0] * x[0] + nrm[1] * x[1] + nrm[2] * x[2] - d) / sq(nrm[0], nrm[1], nrm[2]);
            for (int k = 0; k < 3; k++) x[k] -= s * nrm[k];
            if (!inside(f, x, nrm)) {
                double best = Double.MAX_VALUE;
                double[] near = x;
                for (int i = 0; i < f.length; i++) {
                    double[] p = f[i], q = f[(i + 1) % f.length];
                    double[] e = {q[0] - p[0], q[1] - p[1], q[2] - p[2]};
                    double len = sq(e[0], e[1], e[2]);
                    double u = len == 0 ? 0 : Mth.clamp(((x[0] - p[0]) * e[0] + (x[1] - p[1]) * e[1]
                            + (x[2] - p[2]) * e[2]) / len, 0, 1);
                    double[] y = {p[0] + u * e[0], p[1] + u * e[1], p[2] + u * e[2]};
                    double dd = sq(y[0] - x[0], y[1] - x[1], y[2] - x[2]);
                    if (dd < best) {
                        best = dd;
                        near = y;
                    }
                }
                x = near;
            }
            return new Vec3(cell.x() + x[0] * width, cell.y() + x[1] * height, cell.z() + x[2] * width);
        }
    }

    private static boolean inside(double[][] f, double[] x, double[] n) {
        for (int i = 0; i < f.length; i++) {
            double[] p = f[i], q = f[(i + 1) % f.length];
            double[] c = cross(new double[]{q[0] - p[0], q[1] - p[1], q[2] - p[2]}, new double[]{x[0] - p[0], x[1] - p[1],
                    x[2] - p[2]});
            double side = c[0] * n[0] + c[1] * n[1] + c[2] * n[2];
            double[] c0 = cross(new double[]{q[0] - p[0], q[1] - p[1], q[2] - p[2]},
                    new double[]{f[(i + 2) % f.length][0] - p[0], f[(i + 2) % f.length][1] - p[1], f[(i
                            + 2) % f.length][2] - p[2]});
            if (side * (c0[0] * n[0] + c0[1] * n[1] + c0[2] * n[2]) < 0) return false;
        }
        return true;
    }

    /** Lattice units around the cell's site, which keeps far-out cells as precise as those near the origin. */
    private double[] local(Cell c, double x, double y, double z) {
        return new double[]{(x - c.x()) / width, (y - c.y()) / height, (z - c.z()) / width};
    }

    public Geometry geometry(Cell c) {
        GeoKey key = new GeoKey(voronoi.getSeed(), width, height, c.lx(), c.ly(), c.lz());
        Geometry g = GEOMETRY.getIfPresent(key);
        if (g == null) {
            g = build(c);
            GEOMETRY.put(key, g);
        }
        return g;
    }

    static void clearGeometry() {
        GEOMETRY.invalidateAll();
    }

    private Geometry build(Cell c) {
        double[] a = {0, 0, 0};
        double[] at = {c.x() / width, c.y() / height, c.z() / width};
        for (int w = 2; ; w++) {
            List<Cell> cand = new ArrayList<>();
            for (int dx = -w; dx <= w; dx++) {
                for (int dy = -w; dy <= w; dy++) {
                    for (int dz = -w; dz <= w; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) cand.add(cell(c.lx() + dx, c.ly() + dy, c.lz() + dz));
                    }
                }
            }
            int n = cand.size();
            double[][] s = new double[n][];
            double[] d2 = new double[n];
            for (int i = 0; i < n; i++) {
                Cell o = cand.get(i);
                s[i] = local(c, o.x(), o.y(), o.z());
                d2[i] = sq(s[i][0] - a[0], s[i][1] - a[1], s[i][2] - a[2]);
            }
            int[] order = byDistance(d2);
            List<int[]> sources = new ArrayList<>();
            List<double[][]> faces = cube(a, w + 1, sources);
            double rho = radius(faces, a);
            for (int i : order) {
                if (Math.sqrt(d2[i]) > 2 * rho + EPS) break;
                double[] o = s[i];
                double[] pl = {o[0] - a[0], o[1] - a[1], o[2] - a[2], (sq(o[0], o[1], o[2]) - sq(a[0], a[1], a[2])) / 2};
                if (clip(faces, sources, pl, i)) rho = radius(faces, a);
            }
            // a site outside the window sits farther than this from ours on some axis
            int[] l = {c.lx(), c.ly(), c.lz()};
            double gap = Double.MAX_VALUE;
            for (int k = 0; k < 3; k++) gap = Math.min(gap, Math.min(l[k] + w - at[k], at[k] - (l[k] - w)));
            boolean boxed = false;
            Set<Integer> used = new LinkedHashSet<>();
            List<double[][]> kept = new ArrayList<>();
            Map<Long, double[][]> faceOf = new HashMap<>();
            for (int f = 0; f < faces.size(); f++) {
                double[] nw = newell(faces.get(f));
                if (Math.sqrt(sq(nw[0], nw[1], nw[2])) / 2 <= 1e-12) continue;
                kept.add(faces.get(f));
                int src = sources.get(f)[0];
                if (src < 0) {
                    boxed = true;
                } else {
                    used.add(src);
                    faceOf.put(Network.key(cand.get(src)), faces.get(f));
                }
            }
            if (boxed || 2 * rho > gap) continue;
            List<Cell> neighbors = new ArrayList<>();
            double[][] planes = new double[used.size()][];
            int k = 0;
            for (int i : used) {
                neighbors.add(cand.get(i));
                double[] o = s[i];
                planes[k++] = new double[]{o[0] - a[0], o[1] - a[1], o[2] - a[2], (sq(o[0], o[1], o[2]) - sq(a[0], a[1],
                        a[2])) / 2};
            }
            return new Geometry(c, List.copyOf(neighbors), planes, kept, faceOf);
        }
    }

    private static double sq(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    private static List<double[][]> cube(double[] a, double h, List<int[]> sources) {
        double[][] v = new double[8][];
        for (int i = 0; i < 8; i++) {
            v[i] = new double[]{a[0] + ((i & 1) == 0 ? -h : h), a[1] + ((i & 2) == 0 ? -h : h), a[2] + ((i & 4) == 0 ? -h : h)};
        }
        int[][] quads = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        List<double[][]> faces = new ArrayList<>();
        for (int[] q : quads) {
            faces.add(new double[][]{v[q[0]], v[q[1]], v[q[2]], v[q[3]]});
            sources.add(new int[]{-1});
        }
        return faces;
    }

    private static double radius(List<double[][]> faces, double[] a) {
        double r = 0;
        for (double[][] f : faces) {
            for (double[] p : f) r = Math.max(r, sq(p[0] - a[0], p[1] - a[1], p[2] - a[2]));
        }
        return Math.sqrt(r);
    }

    private static double[] newell(double[][] f) {
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < f.length; i++) {
            double[] p = f[i], q = f[(i + 1) % f.length];
            nx += (p[1] - q[1]) * (p[2] + q[2]);
            ny += (p[2] - q[2]) * (p[0] + q[0]);
            nz += (p[0] - q[0]) * (p[1] + q[1]);
        }
        return new double[]{nx, ny, nz};
    }

    /** Cuts the convex polytope to n.p <= d and closes the cut with a face from source. False when nothing is cut. */
    private static boolean clip(List<double[][]> faces, List<int[]> sources, double[] pl, int source) {
        double eps = EPS * Math.max(1, Math.abs(pl[3]));
        boolean cuts = false;
        for (double[][] f : faces) {
            for (double[] p : f) {
                if (pl[0] * p[0] + pl[1] * p[1] + pl[2] * p[2] - pl[3] > eps) {
                    cuts = true;
                    break;
                }
            }
            if (cuts) break;
        }
        if (!cuts) return false;
        List<double[][]> out = new ArrayList<>();
        List<int[]> outSources = new ArrayList<>();
        List<double[]> cap = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            double[][] f = clipPolygon(faces.get(i), pl, eps, cap);
            if (f.length >= 3) {
                out.add(f);
                outSources.add(sources.get(i));
            }
        }
        double[][] closed = order(cap, pl);
        if (closed.length >= 3) {
            out.add(closed);
            outSources.add(new int[]{source});
        }
        faces.clear();
        faces.addAll(out);
        sources.clear();
        sources.addAll(outSources);
        return true;
    }

    private static double[][] clipPolygon(double[][] f, double[] pl, double eps, List<double[]> cut) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < f.length; i++) {
            double[] p = f[i], q = f[(i + 1) % f.length];
            double sp = pl[0] * p[0] + pl[1] * p[1] + pl[2] * p[2] - pl[3];
            double sq = pl[0] * q[0] + pl[1] * q[1] + pl[2] * q[2] - pl[3];
            if (sp <= eps) out.add(p);
            if (Math.abs(sp) <= eps && cut != null) cut.add(p);
            if ((sp < -eps && sq > eps) || (sp > eps && sq < -eps)) {
                double t = sp / (sp - sq);
                double[] x = {p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1]), p[2] + t * (q[2] - p[2])};
                out.add(x);
                if (cut != null) cut.add(x);
            }
        }
        return out.toArray(new double[0][]);
    }

    /** Puts points of one plane in angular order around their center, dropping repeats. */
    private static double[][] order(List<double[]> pts, double[] n) {
        if (pts.size() < 3) return new double[0][];
        double[] c = new double[3];
        for (double[] p : pts) {
            for (int k = 0; k < 3; k++) c[k] += p[k] / pts.size();
        }
        double[] u = Math.abs(n[0]) < 0.9 * Math.sqrt(sq(n[0], n[1], n[2])) ? cross(n, new double[]{1, 0, 0})
                : cross(n, new double[]{0, 1, 0});
        double[] v = cross(n, u);
        List<double[]> sorted = new ArrayList<>(pts);
        sorted.sort(Comparator.comparingDouble(p -> Math.atan2(dot(p, c, v), dot(p, c, u))));
        List<double[]> out = new ArrayList<>();
        for (double[] p : sorted) {
            if (out.isEmpty() || far(p, out.get(out.size() - 1))) out.add(p);
        }
        while (out.size() > 1 && !far(out.get(0), out.get(out.size() - 1))) out.remove(out.size() - 1);
        return out.toArray(new double[0][]);
    }

    private static boolean far(double[] p, double[] q) {
        return sq(p[0] - q[0], p[1] - q[1], p[2] - q[2]) > 1e-20;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] p, double[] c, double[] u) {
        return (p[0] - c[0]) * u[0] + (p[1] - c[1]) * u[1] + (p[2] - c[2]) * u[2];
    }

    /** Pairs of sites whose Voronoi regions share a face (the Delaunay edges), from clipping each bisector by the rest. */
    static List<int[]> delaunay(double[][] input) {
        int n = input.length;
        double extent = 1;
        for (double[] a : input) {
            for (double[] b : input) extent = Math.max(extent, Math.sqrt(sq(a[0] - b[0], a[1] - b[1], a[2] - b[2])));
        }
        // nearly flat triples put a shared face far out, so the bisector square must reach far past the points
        double big = extent * 1e6;
        List<int[]> edges = new ArrayList<>();
        double[][] p = new double[n][3];
        double[] d2 = new double[n];
        double[] f = new double[3 * (n + 8)], g = new double[3 * (n + 8)];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                p[k][0] = input[k][0] - input[i][0];
                p[k][1] = input[k][1] - input[i][1];
                p[k][2] = input[k][2] - input[i][2];
                d2[k] = sq(p[k][0], p[k][1], p[k][2]);
            }
            int[] order = byDistance(d2);
            for (int j = i + 1; j < n; j++) {
                double[] nj = p[j];
                double[] u = Math.abs(nj[0]) < 0.9 * Math.sqrt(d2[j]) ? cross(nj, new double[]{1, 0, 0})
                        : cross(nj, new double[]{0, 1, 0});
                double lu = Math.sqrt(sq(u[0], u[1], u[2]));
                double[] v = cross(nj, u);
                double lv = Math.sqrt(sq(v[0], v[1], v[2]));
                int count = 4;
                for (int k = 0; k < 4; k++) {
                    double su = (k == 0 || k == 3 ? -big : big) / lu, sv = (k < 2 ? -big : big) / lv;
                    for (int a = 0; a < 3; a++) f[3 * k + a] = nj[a] / 2 + su * u[a] + sv * v[a];
                }
                double reach2 = farthest2(f, count);
                for (int idx = 0; idx < n && count >= 3; idx++) {
                    int k = order[idx];
                    if (k == i || k == j) continue;
                    // no vertex is farther than sqrt(reach2), so a site twice that far can no longer cut the face
                    if (d2[k] >= 4 * reach2 * (1 + 1e-9)) break;
                    if (g.length < 6 * count) g = new double[6 * count];
                    int cut = clipInto(f, count, p[k], d2[k] / 2, EPS * Math.max(1, d2[k] / 2), g);
                    double[] t = f;
                    f = g;
                    g = t;
                    count = cut;
                    reach2 = farthest2(f, count);
                }
                if (count >= 3 && area(f, count) > EPS * extent * extent) edges.add(new int[]{i, j});
            }
        }
        return edges;
    }

    /** Indices sorted by a non-negative key, through one primitive sort of the key bits with the index in the low bits. */
    private static int[] byDistance(double[] d2) {
        int n = d2.length, bits = 32 - Integer.numberOfLeadingZeros(Math.max(1, n));
        long low = (1L << bits) - 1;
        long[] packed = new long[n];
        for (int k = 0; k < n; k++) packed[k] = (Double.doubleToRawLongBits(d2[k]) & ~low) | k;
        Arrays.sort(packed);
        int[] order = new int[n];
        for (int k = 0; k < n; k++) order[k] = (int) (packed[k] & low);
        return order;
    }

    private static double farthest2(double[] f, int count) {
        double r = 0;
        for (int k = 0; k < count; k++) r = Math.max(r, sq(f[3 * k], f[3 * k + 1], f[3 * k + 2]));
        return r;
    }

    private static double area(double[] f, int count) {
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < count; i++) {
            int a = 3 * i, b = 3 * ((i + 1) % count);
            nx += (f[a + 1] - f[b + 1]) * (f[a + 2] + f[b + 2]);
            ny += (f[a + 2] - f[b + 2]) * (f[a] + f[b]);
            nz += (f[a] - f[b]) * (f[a + 1] + f[b + 1]);
        }
        return Math.sqrt(sq(nx, ny, nz)) / 2;
    }

    /** clipPolygon on a flat array of count vertices, to n.p <= d, written into out. Returns the vertices kept. */
    private static int clipInto(double[] f, int count, double[] n, double d, double eps, double[] out) {
        int kept = 0;
        for (int i = 0; i < count; i++) {
            int a = 3 * i, b = 3 * ((i + 1) % count);
            double sp = n[0] * f[a] + n[1] * f[a + 1] + n[2] * f[a + 2] - d;
            double sq = n[0] * f[b] + n[1] * f[b + 1] + n[2] * f[b + 2] - d;
            if (sp <= eps) {
                out[3 * kept] = f[a];
                out[3 * kept + 1] = f[a + 1];
                out[3 * kept + 2] = f[a + 2];
                kept++;
            }
            if ((sp < -eps && sq > eps) || (sp > eps && sq < -eps)) {
                double t = sp / (sp - sq);
                for (int k = 0; k < 3; k++) out[3 * kept + k] = f[a + k] + t * (f[b + k] - f[a + k]);
                kept++;
            }
        }
        return kept;
    }

    /** Minimum spanning tree from site 0 (Prim). */
    static List<int[]> tree(double[][] p) {
        int n = p.length;
        List<int[]> edges = new ArrayList<>();
        if (n < 2) return edges;
        boolean[] in = new boolean[n];
        double[] best = new double[n];
        int[] from = new int[n];
        Arrays.fill(best, Double.MAX_VALUE);
        best[0] = 0;
        for (int step = 0; step < n; step++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!in[i] && (u < 0 || best[i] < best[u])) u = i;
            }
            in[u] = true;
            if (step > 0) edges.add(new int[]{from[u], u});
            for (int i = 0; i < n; i++) {
                double d = sq(p[i][0] - p[u][0], p[i][1] - p[u][1], p[i][2] - p[u][2]);
                if (!in[i] && d < best[i]) {
                    best[i] = d;
                    from[i] = u;
                }
            }
        }
        return edges;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** One network's view of the cells, built lazily and kept for the caller's lifetime. Threads may share it. */
    public final class Network {
        public final Settings settings;
        private final Map<Long, Layout> layouts = new ConcurrentHashMap<>();
        private final Map<Long, Boolean> members = new ConcurrentHashMap<>();
        private final Map<Long, List<Cell>> neighborLists = new ConcurrentHashMap<>();
        // a cell with no parent maps to itself
        private final Map<Long, Cell> parents = new ConcurrentHashMap<>();
        private final Map<Long, List<Link>> outers = new ConcurrentHashMap<>();

        private Network(Settings settings) {
            this.settings = settings;
        }

        static long key(Cell c) {
            return ((long) (c.lx() & 0x1FFFFF) << 42) | ((long) (c.ly() & 0x1FFFFF) << 21) | (c.lz() & 0x1FFFFF);
        }

        public Geometry geometry(Cell c) {
            return SubSites.this.geometry(c);
        }

        public Cell cell(int lx, int ly, int lz) {
            return SubSites.this.cell(lx, ly, lz);
        }

        public boolean member(Cell c) {
            Boolean m = members.get(key(c));
            if (m == null) {
                m = settings.member().test(c);
                members.put(key(c), m);
            }
            return m;
        }

        /** Neighbors that list this cell back, so both sides of a link agree that it exists. */
        public List<Cell> neighbors(Cell c) {
            List<Cell> out = neighborLists.get(key(c));
            if (out == null) {
                List<Cell> both = new ArrayList<>();
                for (Cell n : geometry(c).neighbors) {
                    if (geometry(n).faceOf.containsKey(key(c))) both.add(n);
                }
                out = List.copyOf(both);
                neighborLists.put(key(c), out);
            }
            return out;
        }

        /** Null when the cell is not in the network. */
        public Layout layout(Cell c) {
            return member(c) ? probe(c) : null;
        }

        /** The layout a cell has as a member, built for any cell so a membership rule can count its sites. */
        public Layout probe(Cell c) {
            Layout l = layouts.get(key(c));
            if (l == null) {
                l = build(c);
                Layout raced = layouts.putIfAbsent(key(c), l);
                if (raced != null) l = raced;
            }
            return l;
        }

        private Layout build(Cell c) {
            Geometry g = geometry(c);
            Settings s = settings;
            boolean flat = s.shape() == BiomeEdgeQuery.Shape.HORIZONTAL;
            Site core = new Site(c, -1, c.x(), c.y(), c.z());
            double room = flat ? g.sliceArea(c.y()) : g.volume;
            int slots = (int) Math.min(MAX_SITES, Math.round(s.count() == Count.FIXED ? s.amount() : room / s.amount()));
            long state = mix(mix(mix(mix(voronoi.getSeed() ^ s.salt()) + c.lx()) + c.ly()) + c.lz());
            double[] lo = {c.x() + g.box[0] * width, c.y() + g.box[1] * height, c.z() + g.box[2] * width};
            double[] hi = {c.x() + g.box[3] * width, c.y() + g.box[4] * height, c.z() + g.box[5] * width};
            List<double[]> kept = new ArrayList<>();
            int drawn = 0, want = slots * POOL;
            for (int t = 0; t < want * 8 && drawn < want; t++) {
                double[] q = new double[3];
                for (int k = 0; k < 3; k++) {
                    state += 0x9E3779B97F4A7C15L;
                    q[k] = lo[k] + (hi[k] - lo[k]) * ((mix(state) >>> 11) * 0x1.0p-53);
                }
                if (flat) q[1] = c.y();
                double depth = g.depth(q[0], q[1], q[2]);
                if (depth > EPS) continue;
                // deep inside, our site is the true nearest, and the game finds it whenever its corner is in the window
                if ((depth > -INSIDE || !inWindow(c, q[0], q[1], q[2])) && gameId(q[0], q[1], q[2]) != c.id()) continue;
                drawn++;
                if (s.keep().test(BlockPos.containing(q[0], q[1], q[2]))) kept.add(q);
            }
            int target = s.count() == Count.FIXED ? Math.min(slots, kept.size())
                    : (int) Math.round(slots * (double) kept.size() / Math.max(1, drawn));
            List<Site> subs = new ArrayList<>();
            double[] near = new double[kept.size()];
            for (int i = 0; i < near.length; i++) {
                double[] q = kept.get(i);
                near[i] = sq(q[0] - c.x(), q[1] - c.y(), q[2] - c.z());
            }
            for (int n = 0; n < target; n++) {
                int pick = -1;
                for (int i = 0; i < near.length; i++) {
                    if (near[i] > 0 && (pick < 0 || near[i] > near[pick])) pick = i;
                }
                if (pick < 0) break;
                double[] q = kept.get(pick);
                subs.add(new Site(c, n, q[0], q[1], q[2]));
                for (int i = 0; i < near.length; i++) {
                    double[] o = kept.get(i);
                    near[i] = Math.min(near[i], sq(o[0] - q[0], o[1] - q[1], o[2] - q[2]));
                }
            }
            List<Site> all = new ArrayList<>();
            all.add(core);
            all.addAll(subs);
            double[][] p = points(all);
            List<int[]> edges = switch (s.inner()) {
                case NONE -> List.of();
                case HUB -> {
                    List<int[]> hub = new ArrayList<>();
                    for (int i = 1; i < p.length; i++) hub.add(new int[]{0, i});
                    yield hub;
                }
                case TREE -> tree(p);
                case DELAUNAY -> delaunay(p);
            };
            List<Link> links = new ArrayList<>();
            for (int[] e : edges) links.add(new Link(all.get(e[0]), all.get(e[1]), null));
            return new Layout(core, List.copyOf(subs), List.copyOf(links));
        }

        private boolean above(Cell a, Cell b) {
            double sa = settings.score().applyAsDouble(a), sb = settings.score().applyAsDouble(b);
            return sa > sb || (sa == sb && key(a) > key(b));
        }

        /** The member neighbor this cell's TREE link climbs to, null for a core (no member neighbor scores higher). */
        public Cell parent(Cell c) {
            Cell best = parents.get(key(c));
            if (best == null) {
                best = c;
                if (member(c)) {
                    for (Cell b : neighbors(c)) {
                        if (member(b) && above(b, best)) best = b;
                    }
                }
                parents.put(key(c), best);
            }
            return best.equals(c) ? null : best;
        }

        /** Links from this cell to its member neighbors, equal whichever side asks. */
        public List<Link> outer(Cell c) {
            List<Link> out = outers.get(key(c));
            if (out == null) {
                out = List.copyOf(links(c));
                outers.put(key(c), out);
            }
            return out;
        }

        private List<Link> links(Cell c) {
            List<Link> out = new ArrayList<>();
            if (settings.outer() == Outer.NONE || !member(c)) return out;
            Cell up = settings.outer() == Outer.TREE ? parent(c) : null;
            for (Cell b : neighbors(c)) {
                if (!member(b)) continue;
                if (settings.outer() == Outer.TREE && !b.equals(up) && !c.equals(parent(b))) continue;
                boolean first = key(c) < key(b);
                Cell lo = first ? c : b, hi = first ? b : c;
                Layout la = layout(lo), lb = layout(hi);
                Site[] pair = settings.outer() == Outer.CORES ? new Site[]{la.core(), lb.core()} : closest(la, lb);
                out.add(new Link(pair[0], pair[1], geometry(lo).door(hi, pair[0], pair[1])));
            }
            return out;
        }

        private Site[] closest(Layout a, Layout b) {
            Site[] best = null;
            double d = Double.MAX_VALUE;
            for (Site p : sites(a)) {
                for (Site q : sites(b)) {
                    double e = sq(p.x() - q.x(), p.y() - q.y(), p.z() - q.z());
                    if (e < d) {
                        d = e;
                        best = new Site[]{p, q};
                    }
                }
            }
            return best;
        }

        /** The exact nearest site and link within reach blocks of a point, searching cells from near to far. */
        public Hit at(double x, double y, double z, double reach) {
            Cell home = cellAt(x, y, z);
            PriorityQueue<Object[]> queue = new PriorityQueue<>(Comparator.comparingDouble(e -> (double) e[0]));
            Set<Long> seen = new HashSet<>();
            queue.add(new Object[]{0.0, home});
            seen.add(key(home));
            Site site = null;
            Link link = null;
            double siteD = Double.MAX_VALUE, linkD = Double.MAX_VALUE;
            while (!queue.isEmpty()) {
                Object[] e = queue.poll();
                double bound = (double) e[0];
                // every site, and every link half, lies inside one cell, and no unvisited cell is nearer than this bound
                if (bound > Math.min(reach, Math.max(siteD, linkD))) break;
                Cell c = (Cell) e[1];
                Layout l = layout(c);
                if (l != null) {
                    for (Site s : sites(l)) {
                        double d = Math.sqrt(sq(s.x() - x, s.y() - y, s.z() - z));
                        if (d < siteD) {
                            siteD = d;
                            site = s;
                        }
                    }
                    List<Link> links = new ArrayList<>(l.links());
                    links.addAll(outer(c));
                    for (Link k : links) {
                        double d = Math.sqrt(distance2(k, x, y, z));
                        if (d < linkD) {
                            linkD = d;
                            link = k;
                        }
                    }
                }
                for (Cell n : geometry(c).neighbors) {
                    if (seen.add(key(n))) queue.add(new Object[]{geometry(n).lowerBound(x, y, z), n});
                }
            }
            boolean hasSite = siteD <= reach, hasLink = linkD <= reach;
            return new Hit(home, hasSite ? site : null, hasSite ? siteD : Double.POSITIVE_INFINITY,
                    hasLink ? link : null, hasLink ? linkD : Double.POSITIVE_INFINITY);
        }

        /** Every site and link that can be nearest within spread of (x, y, z), kept in trees for fast exact queries there. */
        public Region region(double x, double y, double z, double spread, double siteReach, double linkReach) {
            Hit h = at(x, y, z, Math.max(siteReach, linkReach));
            double siteD = h.siteDistance() <= siteReach ? h.siteDistance() : Double.POSITIVE_INFINITY;
            double linkD = h.linkDistance() <= linkReach ? h.linkDistance() : Double.POSITIVE_INFINITY;
            // the nearest of a point within spread is no farther from it than the center's nearest plus spread
            double rs = Math.min(siteD + 2 * spread, siteReach + spread), rl = Math.min(linkD + 2 * spread, linkReach + spread);
            double range = Math.max(rs, rl);
            List<Site> near = new ArrayList<>();
            Set<Link> close = new LinkedHashSet<>();
            Set<Long> seen = new HashSet<>();
            ArrayDeque<Cell> todo = new ArrayDeque<>();
            todo.add(h.cell());
            seen.add(key(h.cell()));
            List<Cell> walked = new ArrayList<>();
            while (!todo.isEmpty()) {
                Cell c = todo.poll();
                walked.add(c);
                Layout l = layout(c);
                if (l != null) {
                    for (Site s : sites(l)) {
                        if (sq(s.x() - x, s.y() - y, s.z() - z) <= rs * rs) near.add(s);
                    }
                    for (Link k : l.links()) {
                        if (distance2(k, x, y, z) <= rl * rl) close.add(k);
                    }
                    for (Link k : outer(c)) {
                        if (distance2(k, x, y, z) <= rl * rl) close.add(k);
                    }
                }
                for (Cell n : geometry(c).neighbors) {
                    if (seen.add(key(n)) && geometry(n).lowerBound(x, y, z) <= range) todo.add(n);
                }
            }
            return new Region(x, y, z, spread, siteReach, linkReach, near, List.copyOf(close), List.copyOf(walked));
        }

        /** Exact nearest site and link for points within spread blocks of one center (others fall back to at()). */
        public final class Region {
            private final double cx, cy, cz, spread, siteReach, linkReach;
            private final Index sites, links;
            public final List<Cell> walked;
            public final List<Site> near;
            public final List<Link> close;

            private Region(double x, double y, double z, double spread, double siteReach, double linkReach, List<Site> near,
                           List<Link> close, List<Cell> walked) {
                this.cx = x;
                this.cy = y;
                this.cz = z;
                this.spread = spread;
                this.siteReach = siteReach;
                this.linkReach = linkReach;
                this.walked = walked;
                this.near = List.copyOf(near);
                this.close = close;
                List<double[]> ps = new ArrayList<>();
                for (Site s : near) ps.add(new double[]{s.x(), s.y(), s.z(), s.x(), s.y(), s.z()});
                sites = new Index(ps, new ArrayList<>(near));
                List<double[]> ls = new ArrayList<>();
                List<Object> owners = new ArrayList<>();
                for (Link k : close) {
                    Vec3 d = k.door();
                    if (d == null) {
                        ls.add(new double[]{k.a().x(), k.a().y(), k.a().z(), k.b().x(), k.b().y(), k.b().z()});
                        owners.add(k);
                    } else {
                        ls.add(new double[]{k.a().x(), k.a().y(), k.a().z(), d.x, d.y, d.z});
                        ls.add(new double[]{d.x, d.y, d.z, k.b().x(), k.b().y(), k.b().z()});
                        owners.add(k);
                        owners.add(k);
                    }
                }
                links = new Index(ls, owners);
            }

            public int siteCount() {
                return sites.size();
            }

            public int linkCount() {
                return links.size();
            }

            public Hit at(double x, double y, double z) {
                if (sq(x - cx, y - cy, z - cz) > spread * spread) {
                    Hit s = Network.this.at(x, y, z, siteReach);
                    Hit l = linkReach == siteReach ? s : Network.this.at(x, y, z, linkReach);
                    return new Hit(s.cell(), s.site(), s.siteDistance(), l.link(), l.linkDistance());
                }
                double[] d = new double[1];
                int si = sites.nearest(x, y, z, siteReach * siteReach * (1 + 1e-9), d);
                double siteD = si < 0 ? Double.POSITIVE_INFINITY : Math.sqrt(d[0]);
                int li = links.nearest(x, y, z, linkReach * linkReach * (1 + 1e-9), d);
                double linkD = li < 0 ? Double.POSITIVE_INFINITY : Math.sqrt(d[0]);
                boolean hasSite = siteD <= siteReach, hasLink = linkD <= linkReach;
                return new Hit(cellAt(x, y, z), hasSite ? (Site) sites.item(si) : null,
                        hasSite ? siteD : Double.POSITIVE_INFINITY, hasLink ? (Link) links.item(li) : null,
                        hasLink ? linkD : Double.POSITIVE_INFINITY);
            }
        }
    }

    /** Bounding box tree over segments (a site is a segment of length 0) for exact nearest queries. */
    private static final class Index {
        private static final int LEAF = 4;
        private final double[] seg;
        private final Object[] items;
        private final int[] perm;
        private final double[] box;
        private final int[] left, right, first, count;
        private int nodes;

        Index(List<double[]> segments, List<Object> owners) {
            int n = segments.size();
            seg = new double[6 * n];
            for (int i = 0; i < n; i++) System.arraycopy(segments.get(i), 0, seg, 6 * i, 6);
            items = owners.toArray();
            perm = new int[n];
            for (int i = 0; i < n; i++) perm[i] = i;
            int cap = Math.max(1, 2 * n);
            box = new double[6 * cap];
            left = new int[cap];
            right = new int[cap];
            first = new int[cap];
            count = new int[cap];
            if (n > 0) build(0, n);
        }

        int size() {
            return perm.length;
        }

        Object item(int i) {
            return items[i];
        }

        private double key(int item, int axis) {
            return seg[6 * item + axis] + seg[6 * item + 3 + axis];
        }

        private int build(int from, int to) {
            int node = nodes++, o = 6 * node;
            double[] cmin = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] cmax = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (int a = 0; a < 3; a++) {
                box[o + a] = Double.MAX_VALUE;
                box[o + 3 + a] = -Double.MAX_VALUE;
            }
            for (int i = from; i < to; i++) {
                int s = 6 * perm[i];
                for (int a = 0; a < 3; a++) {
                    double lo = Math.min(seg[s + a], seg[s + 3 + a]), hi = Math.max(seg[s + a], seg[s + 3 + a]);
                    box[o + a] = Math.min(box[o + a], lo);
                    box[o + 3 + a] = Math.max(box[o + 3 + a], hi);
                    cmin[a] = Math.min(cmin[a], lo + hi);
                    cmax[a] = Math.max(cmax[a], lo + hi);
                }
            }
            if (to - from <= LEAF) {
                first[node] = from;
                count[node] = to - from;
                return node;
            }
            int axis = 0;
            for (int a = 1; a < 3; a++) {
                if (cmax[a] - cmin[a] > cmax[axis] - cmin[axis]) axis = a;
            }
            int mid = (from + to) >>> 1;
            select(from, to - 1, mid, axis);
            left[node] = build(from, mid);
            right[node] = build(mid, to);
            return node;
        }

        /** Puts the item with the k-th smallest key along axis at k, smaller ones before it and larger ones after. */
        private void select(int lo, int hi, int k, int axis) {
            while (lo < hi) {
                double pivot = key(perm[(lo + hi) >>> 1], axis);
                int i = lo, j = hi;
                while (i <= j) {
                    while (key(perm[i], axis) < pivot) i++;
                    while (key(perm[j], axis) > pivot) j--;
                    if (i <= j) {
                        int t = perm[i];
                        perm[i++] = perm[j];
                        perm[j--] = t;
                    }
                }
                if (k <= j) {
                    hi = j;
                } else if (k >= i) {
                    lo = i;
                } else {
                    return;
                }
            }
        }

        private double boxDistance2(int node, double x, double y, double z) {
            int o = 6 * node;
            double dx = Math.max(0, Math.max(box[o] - x, x - box[o + 3]));
            double dy = Math.max(0, Math.max(box[o + 1] - y, y - box[o + 4]));
            double dz = Math.max(0, Math.max(box[o + 2] - z, z - box[o + 5]));
            return dx * dx + dy * dy + dz * dz;
        }

        /** The nearest item no farther than sqrt(limit2), or -1. Writes its squared distance to out[0]. */
        int nearest(double x, double y, double z, double limit2, double[] out) {
            if (nodes == 0) return -1;
            double best = limit2;
            int found = -1;
            int[] stack = new int[64];
            int sp = 0;
            stack[sp++] = 0;
            while (sp > 0) {
                int node = stack[--sp];
                if (boxDistance2(node, x, y, z) > best) continue;
                if (count[node] > 0) {
                    for (int i = first[node], end = i + count[node]; i < end; i++) {
                        int s = 6 * perm[i];
                        double d = segment(seg[s], seg[s + 1], seg[s + 2], seg[s + 3], seg[s + 4], seg[s + 5], x, y, z);
                        if (d < best || (found < 0 && d <= best)) {
                            best = d;
                            found = perm[i];
                        }
                    }
                } else if (boxDistance2(left[node], x, y, z) <= boxDistance2(right[node], x, y, z)) {
                    stack[sp++] = right[node];
                    stack[sp++] = left[node];
                } else {
                    stack[sp++] = left[node];
                    stack[sp++] = right[node];
                }
            }
            out[0] = best;
            return found;
        }
    }

    static List<Site> sites(Layout l) {
        List<Site> all = new ArrayList<>();
        all.add(l.core());
        all.addAll(l.subs());
        return all;
    }

    private static double[][] points(List<Site> sites) {
        double[][] p = new double[sites.size()][];
        for (int i = 0; i < p.length; i++) p[i] = new double[]{sites.get(i).x(), sites.get(i).y(), sites.get(i).z()};
        return p;
    }

    /** Squared distance from a point to a link (both halves when it bends at a door). */
    static double distance2(Link k, double x, double y, double z) {
        if (k.door() == null) return segment(k.a().x(), k.a().y(), k.a().z(), k.b().x(), k.b().y(), k.b().z(), x, y, z);
        Vec3 d = k.door();
        return Math.min(segment(k.a().x(), k.a().y(), k.a().z(), d.x, d.y, d.z, x, y, z),
                segment(d.x, d.y, d.z, k.b().x(), k.b().y(), k.b().z(), x, y, z));
    }

    private static double segment(double ax, double ay, double az, double bx, double by, double bz, double x, double y,
            double z) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = sq(dx, dy, dz);
        double t = len == 0 ? 0 : Mth.clamp(((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / len, 0, 1);
        return sq(ax + t * dx - x, ay + t * dy - y, az + t * dz - z);
    }
}
