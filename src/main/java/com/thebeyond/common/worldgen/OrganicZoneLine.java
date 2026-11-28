package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.OrganicGrowth.Grown;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;


/** The dark zone line a fungus draws on the rock along the border with organisms it is not joined to. */
final class OrganicZoneLine {
    // setups whose mycelium marks the border with every organism it is not joined to by a dark zone line on the rock
    static boolean zoneLine(String setup) {
        return OrganicPresets.flag(setup, "zoneLine", ZONE_LINE);
    }

    static final Set<String> ZONE_LINE = Set.copyOf(Arrays.stream(System.getProperty("beyond.sub.org.zoneLine", "fungus")
            .split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList());
    static final Line NO_LINE = new Line(new int[0], new int[1], new int[0], new SubSites.Cell[0]);
    private static final int[][] SIX = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private OrganicZoneLine() {}

    /** Whether line voxel s borders an organism this cell is not joined to, decided once per organism (it needs its network). */
    static boolean drawn(Grown gr, SubSites.Cell c, int s, OrganicGrowth.Where w) throws Exception {
        int j = s - gr.nNet();
        for (int t = gr.lineStart()[j]; t < gr.lineStart()[j + 1]; t++) {
            SubSites.Cell m = gr.lineCells()[gr.lineOf()[t]];
            long key = SubSites.Network.key(m);
            Boolean f = gr.drawn().get(key);
            if (f == null) {
                SubSites.Cell a = OrganicGrowth.root(w, c);
                SubSites.Cell b = OrganicGrowth.root(w, m);
                f = !a.equals(b);
                if (gr.drawn().putIfAbsent(key, f) == null) {
                    TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG line %d %d %d | organism %d %d %d | roots %d %d %d"
                                    + " and %d %d %d | drawn %b", c.lx(), c.ly(), c.lz(), m.lx(), m.ly(), m.lz(), a.lx(), a.ly(),
                            a.lz(), b.lx(), b.ly(), b.lz(), f));
                }
            }
            if (f) return true;
        }
        return false;
    }

    private static boolean substrate(OrganicNetwork.Grid g, OrganicNetwork.Settings st, int x, int y, int z) {
        if (!g.inside(x, y, z)) return false;
        int i = g.index(x, y, z);
        if (g.solid(i) || !g.bog(i)) return false;
        boolean side = g.solidAt(x + 1, y, z) || g.solidAt(x - 1, y, z) || g.solidAt(x, y, z + 1) || g.solidAt(x, y, z - 1);
        return st.ceiling() && g.solidAt(x, y + 1, z) || st.wall() && side || st.floor() && g.solidAt(x, y - 1, z);
    }

    record Line(int[] vox, int[] start, int[] of, SubSites.Cell[] cells) {}

    /** Rock faces where this cell's substrate comes within 1.5 blocks of another organism's, with the organisms it borders. */
    static Line zoneLine(OrganicNetwork.Grid g, OrganicNetwork.Settings st, SubSites.Network net, SubSites.Cell own,
                                 int x0, int minY, int z0, int[] network) {
        double ox = own.x() - x0, oy = own.y() - minY, oz = own.z() - z0, dMax = 0;
        for (int cx = 0; cx <= g.nx; cx += g.nx) {
            for (int cy = 0; cy <= g.ny; cy += g.ny) {
                for (int cz = 0; cz <= g.nz; cz += g.nz) dMax = Math.max(dMax, Math.sqrt(sq(cx - ox, cy - oy, cz - oz)));
            }
        }
        // a block at distance d from this core can only be nearer a core within 2d of this one
        double reach = 2 * dMax + 3;
        int kx = (int) Math.ceil(reach / BiomeCellScale.cellWidth()) + 2;
        int ky = (int) Math.ceil(reach / BiomeCellScale.cellHeight()) + 2;
        List<SubSites.Cell> near = new ArrayList<>();
        for (int a = -kx; a <= kx; a++) {
            for (int b = -ky; b <= ky; b++) {
                for (int c = -kx; c <= kx; c++) {
                    if (a == 0 && b == 0 && c == 0) continue;
                    SubSites.Cell m = net.cell(own.lx() + a, own.ly() + b, own.lz() + c);
                    if (sq(m.x() - own.x(), m.y() - own.y(), m.z() - own.z()) <= reach * reach && net.member(m)) near.add(m);
                }
            }
        }
        near.sort(Comparator.comparingDouble(m -> sq(m.x() - own.x(), m.y() - own.y(), m.z() - own.z())));
        int n = near.size();
        double[] mx = new double[n], my = new double[n], mz = new double[n], md = new double[n];
        for (int i = 0; i < n; i++) {
            mx[i] = near.get(i).x() - x0;
            my[i] = near.get(i).y() - minY;
            mz[i] = near.get(i).z() - z0;
            md[i] = Math.sqrt(sq(mx[i] - ox, my[i] - oy, mz[i] - oz));
        }
        BitSet mine = new BitSet();
        for (int i : network) mine.set(i);
        TreeSet<Long> pairs = new TreeSet<>();
        double margin = 2 * Math.sqrt(2);
        for (int x = 0; x < g.nx; x++) {
            for (int y = 0; y < g.ny; y++) {
                for (int z = 0; z < g.nz; z++) {
                    if (!substrate(g, st, x, y, z)) continue;
                    double d = Math.sqrt(sq(x + 0.5 - ox, y + 0.5 - oy, z + 0.5 - oz));
                    // with no other core within the margin, every block beside this one is this cell's too
                    int k = nearest(x + 0.5, y + 0.5, z + 0.5, d, d + margin, mx, my, mz, md);
                    if (k < 0 || Math.sqrt(sq(x + 0.5 - mx[k], y + 0.5 - my[k], z + 0.5 - mz[k])) < d) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                int e = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                                if (e == 0 || e == 3 || !substrate(g, st, x + dx, y + dy, z + dz)) continue;
                                double qx = x + dx + 0.5, qy = y + dy + 0.5, qz = z + dz + 0.5;
                                double dq = Math.sqrt(sq(qx - ox, qy - oy, qz - oz));
                                int other = nearest(qx, qy, qz, dq, dq, mx, my, mz, md);
                                if (other < 0) continue;
                                for (int[] f : SIX) {
                                    int u = x + f[0], v = y + f[1], w = z + f[2];
                                    if (!g.solidAt(u, v, w) || mine.get(g.index(u, v, w))) continue;
                                    pairs.add((long) g.index(u, v, w) << 20 | other);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pairs.isEmpty()) return NO_LINE;
        int[] slot = new int[n];
        Arrays.fill(slot, -1);
        List<SubSites.Cell> cells = new ArrayList<>();
        int[] vox = new int[pairs.size()], start = new int[pairs.size() + 1], of = new int[pairs.size()];
        int nv = 0, t = 0;
        long last = -1;
        for (long p : pairs) {
            int v = (int) (p >>> 20), m = (int) (p & 0xFFFFF);
            if (v != last) {
                start[nv] = t;
                vox[nv++] = v;
                last = v;
            }
            if (slot[m] < 0) {
                slot[m] = cells.size();
                cells.add(near.get(m));
            }
            of[t++] = slot[m];
        }
        start[nv] = t;
        return new Line(Arrays.copyOf(vox, nv), Arrays.copyOf(start, nv + 1), of, cells.toArray(new SubSites.Cell[0]));
    }

    /** Core nearest the point among those closer than limit, or -1, the cores sorted by distance md so the search stops early. */
    private static int nearest(double px, double py, double pz, double d, double limit, double[] mx, double[] my, double[] mz,
                               double[] md) {
        int best = -1;
        double bd = limit;
        for (int i = 0; i < md.length && md[i] - d < bd; i++) {
            double e = Math.sqrt(sq(px - mx[i], py - my[i], pz - mz[i]));
            if (e < bd) {
                bd = e;
                best = i;
            }
        }
        return best;
    }

    private static double sq(double x, double y, double z) {
        return x * x + y * y + z * z;
    }
}
