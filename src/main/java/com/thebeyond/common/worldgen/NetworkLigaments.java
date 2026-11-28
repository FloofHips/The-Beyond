package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/** Ligaments, the pillars and cables that span the open space between rock faces. */
abstract class NetworkLigaments extends NetworkGraph {
    NetworkLigaments(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    /** Point at t (0 at the top): a pillar sways in two harmonics, a cable sags as a catenary, all of them with sagAll. */
    double[] linkPoint(double[] a, double[] b, double t) {
        double vx = b[0] - a[0], vy = b[1] - a[1], vz = b[2] - a[2];
        double h = Math.hypot(vx, vz), len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double[] q = {a[0] + t * vx, a[1] + t * vy, a[2] + t * vz};
        boolean pillar = steep(a, b, st.linkAng());
        if (pillar) {
            double sem = Math.sin(a[0] * 12.9898 + a[2] * 78.233 + b[0] * 37.719 + b[2] * 4.581) * 43758.5453;
            double phi = (sem - Math.floor(sem)) * 2 * Math.PI;
            double amp = Math.min(st.linkSway(), len / 10.0), s1 = amp * Math.sin(Math.PI * t);
            double s2 = 0.35 * amp * Math.sin(2 * Math.PI * t);
            q[0] += s1 * Math.cos(phi) - s2 * Math.sin(phi);
            q[2] += s1 * Math.sin(phi) + s2 * Math.cos(phi);
        }
        if (!pillar || st.bio().sagAll()) {
            double sag = st.catenary() ? Math.min(st.linkSag(), Math.max(st.sagMin(), st.linkSag() * h / st.linkMaxH())) : st.linkSag();
            q[1] -= sag * h * 4 * t * (1 - t);
        }
        return q;
    }

    /** Ligament radius along t (0 at the upper end), or with flux the factor applied to the radius of the flow it carries. */
    double profile(boolean pillar, double t) {
        if (st.bio().on()) return 1.0;
        if (st.flux()) {
            if (pillar) return st.pTop() + (st.pBot() - st.pTop()) * Math.pow(t, 0.7);
            return st.cMid() + (st.cEnd() - st.cMid()) * Math.pow(Math.abs(2 * t - 1), 2);
        }
        if (pillar) return st.linkR0() + (st.rMax() - st.linkR0()) * Math.pow(t, 0.7);
        return st.linkRm() + (st.linkRe() - st.linkRm()) * Math.pow(Math.abs(2 * t - 1), 2);
    }

    double linkR(double profile, double flowRadius) {
        return st.flux() ? Math.max(st.linkRMin(), flowRadius * profile) : profile;
    }

    /** Tube radius from the normalized Tero conductivity: with flux the same law as the tree's flow. */
    double meshRadius(double d) {
        d = Math.max(d, 1e-9);
        if (st.flux()) return st.rTip() + (st.rMax() - st.rTip()) * Math.pow(d, 1 / st.nExp());
        return Math.max(st.rTip(), st.rMax() * Math.pow(d, 0.25));
    }

    void ligaments() {
        List<Integer> cand = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            if ((nFlag[u] & F_ROCK) == 0 && ((nFlag[u] & F_HAB) != 0 || (nFlag[u] & F_FLOOR) != 0)) cand.add(u);
        }
        int[] anc = poisson(cand, st.linkAnc(), new SplittableRandom(st.seed() + 7), null);
        OrganicIntIntMap island = islands();
        int na = anc.length;
        int[] il = new int[na];
        for (int a = 0; a < na; a++) il[a] = islandOf(anc[a], island);
        List<double[]> ok = new ArrayList<>();
        double max2 = st.linkMax() * st.linkMax();
        for (int a = 0; a < na; a++) {
            for (int b = a + 1; b < na; b++) {
                if (il[a] == il[b] || il[a] <= 0 || il[b] <= 0) continue;
                int u = anc[a], v = anc[b];
                double dx = nX[u] - nX[v], dy = nY[u] - nY[v], dz = nZ[u] - nZ[v], d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > max2 || d2 <= st.lf() * st.lf()) continue;
                double[] pa = center(u), pb = center(v);
                int up = u, lo = v;
                if (swapEnds(pa, pb)) {
                    double[] t = pa;
                    pa = pb;
                    pb = t;
                    up = v;
                    lo = u;
                }
                boolean pillar = steep(pa, pb, st.linkAng());
                boolean floorUp = (nFlag[up] & F_FLOOR) != 0 && (nFlag[up] & F_HAB) == 0;
                boolean floorLo = (nFlag[lo] & F_FLOOR) != 0 && (nFlag[lo] & F_HAB) == 0;
                if (pillar ? !((nFlag[up] & F_CEIL) != 0 && (nFlag[lo] & F_FLOOR) != 0)
                        : (floorUp || floorLo || Math.hypot(pb[0] - pa[0], pb[2] - pa[2]) > st.linkMaxH())) continue;
                double len = Math.sqrt(d2);
                if (!clearCurve(pa, pb, len)) continue;
                ok.add(new double[]{len, up, lo, pillar ? 1 : 0});
            }
        }
        ok.sort((x, y) -> Double.compare(x[0], y[0]));
        Map<Integer, Integer> use = new HashMap<>();
        int pillars = 0, cables = 0;
        for (double[] e : ok) {
            int up = (int) e[1], lo = (int) e[2];
            if (use.getOrDefault(up, 0) >= st.linkK() || use.getOrDefault(lo, 0) >= st.linkK()) continue;
            use.merge(up, 1, Integer::sum);
            use.merge(lo, 1, Integer::sum);
            double w = e[0] * (e[3] > 0 ? st.linkCostV() : st.linkCost());
            linkAdj.computeIfAbsent(up, k -> new ArrayList<>()).add(new double[]{lo, w, e[0]});
            linkAdj.computeIfAbsent(lo, k -> new ArrayList<>()).add(new double[]{up, w, e[0]});
            hasLink[up] = true;
            hasLink[lo] = true;
            if (e[3] > 0) pillars++;
            else cables++;
        }
        report.append(String.format(Locale.ROOT, " | ligament anchors %d, clear %d, in graph %d pillars %d cables", na,
                ok.size(), pillars, cables));
    }

    boolean clearCurve(double[] a, double[] b, double len) {
        for (int s = 1; s < 100; s++) {
            double t = s / 100.0;
            if (t * len <= 1.5 || (1 - t) * len <= 1.5) continue;
            double[] q = linkPoint(a, b, t);
            int x = (int) Math.floor(q[0]), y = (int) Math.floor(q[1]), z = (int) Math.floor(q[2]);
            if (!g.inside(x, y, z)) return false;
            int i = g.index(x, y, z);
            if (g.solid(i) || (!st.linkOutside() && !g.bog(i))) return false;
        }
        return true;
    }

    /** Islands as 6-connected solid components, labelled from 1: only blocks touched by the flood get a label. */
    OrganicIntIntMap islands() {
        OrganicIntIntMap lab = new OrganicIntIntMap(1 << 16);
        int next = 0;
        int[] stack = new int[4096];
        for (int u = 0; u < n; u++) {
            if ((nFlag[u] & F_ROCK) != 0) continue;
            int x = nX[u], y = nY[u], z = nZ[u];
            for (int[] o : OFF6) {
                int qx = x + o[0], qy = y + o[1], qz = z + o[2];
                if (!g.solidAt(qx, qy, qz)) continue;
                int start = g.index(qx, qy, qz);
                if (lab.get(start) >= 0) continue;
                next++;
                int sp = 0;
                stack[sp++] = start;
                lab.put(start, next);
                while (sp > 0) {
                    int i = stack[--sp];
                    int ix = i / (g.ny * g.nz), iy = (i / g.nz) % g.ny, iz = i % g.nz;
                    for (int[] p : OFF6) {
                        int jx = ix + p[0], jy = iy + p[1], jz = iz + p[2];
                        if (!g.solidAt(jx, jy, jz)) continue;
                        int j = g.index(jx, jy, jz);
                        if (lab.get(j) >= 0) continue;
                        lab.put(j, next);
                        if (sp == stack.length) stack = Arrays.copyOf(stack, sp * 2);
                        stack[sp++] = j;
                    }
                }
            }
        }
        return lab;
    }

    int islandOf(int u, OrganicIntIntMap island) {
        int x = nX[u], y = nY[u], z = nZ[u];
        for (int[] o : OFF6) {
            int qx = x + o[0], qy = y + o[1], qz = z + o[2];
            if (g.solidAt(qx, qy, qz)) return island.get(g.index(qx, qy, qz));
        }
        return 0;
    }

    double[] center(int u) {
        return new double[]{nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5};
    }

    /** Greedy Poisson-disk thinning of graph nodes, in shuffled order or by ascending priority. */
    int[] poisson(List<Integer> pts, double r, SplittableRandom rnd, double[] prio) {
        return poisson(pts, null, r, rnd, prio);
    }

    /** rPer, aligned with pts, gives each point its own spacing (foraging). */
    int[] poisson(List<Integer> pts, double[] rPer, double r, SplittableRandom rnd, double[] prio) {
        Integer[] ord = new Integer[pts.size()];
        for (int i = 0; i < ord.length; i++) ord[i] = i;
        if (prio == null) {
            for (int i = ord.length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                Integer t = ord[i];
                ord[i] = ord[j];
                ord[j] = t;
            }
        } else {
            Arrays.sort(ord, (a, b) -> Double.compare(prio[a], prio[b]));
        }
        double gr = r;
        if (rPer != null) {
            gr = 0;
            for (double x : rPer) gr = Math.max(gr, x);
        }
        Map<Long, List<Integer>> cell = new HashMap<>();
        List<Integer> keep = new ArrayList<>();
        for (Integer i : ord) {
            int u = pts.get(i);
            double ri = rPer == null ? r : rPer[i], r2 = ri * ri;
            int kx = (int) Math.floor(nX[u] / gr), ky = (int) Math.floor(nY[u] / gr), kz = (int) Math.floor(nZ[u] / gr);
            boolean ok = true;
            for (int dx = -1; dx <= 1 && ok; dx++) {
                for (int dy = -1; dy <= 1 && ok; dy++) {
                    for (int dz = -1; dz <= 1 && ok; dz++) {
                        List<Integer> l = cell.get(key(kx + dx, ky + dy, kz + dz));
                        if (l == null) continue;
                        for (int v : l) {
                            double ex = nX[u] - nX[v], ey = nY[u] - nY[v], ez = nZ[u] - nZ[v];
                            if (ex * ex + ey * ey + ez * ez < r2) {
                                ok = false;
                                break;
                            }
                        }
                    }
                }
            }
            if (ok) {
                cell.computeIfAbsent(key(kx, ky, kz), k -> new ArrayList<>()).add(u);
                keep.add(u);
            }
        }
        return keep.stream().mapToInt(Integer::intValue).toArray();
    }
}
