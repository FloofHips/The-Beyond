package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

/** The fringe of fine roots grown by space colonization around the trunks. */
abstract class NetworkFringe extends NetworkCell {
    NetworkFringe(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    void fringe(int ci, int trunks) {
        List<Integer> mine = new ArrayList<>();
        for (int i = 0; i < trunks; i++) {
            if (cel[i] == ci) mine.add(i);
        }
        if (mine.isEmpty() || st.iters() <= 0) return;
        OrganicSpatialHash nodes = new OrganicSpatialHash(4);
        for (int i : mine) nodes.add(i, px[i], py[i], pz[i]);
        List<Integer> hug = new ArrayList<>();
        OrganicSpatialHash hugIx = new OrganicSpatialHash(2);
        for (int u = 0; u < n; u++) {
            if (nLab[u] == ci && (nFlag[u] & F_HUG) != 0) {
                hug.add(u);
                hugIx.add(u, nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5);
            }
        }
        List<Integer> cand = new ArrayList<>();
        for (int u : hug) {
            if ((nFlag[u] & F_HAB) != 0 && nodes.nearest(nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5, st.spread(), this::nodePos) >= 0) {
                cand.add(u);
            }
        }
        int[] att = poisson(cand, forageSpacing(cand, st.rAtt()), st.rAtt(), new SplittableRandom(st.seed() + 100 + ci), null);
        boolean[] alive = new boolean[att.length];
        Arrays.fill(alive, true);
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        for (int i : mine) taken.add(g.index((int) Math.floor(px[i]), (int) Math.floor(py[i]), (int) Math.floor(pz[i])));
        int grown = 0;
        for (int it = 0; it < st.iters(); it++) {
            Map<Integer, double[]> dirs = new TreeMap<>();
            for (int a = 0; a < att.length; a++) {
                if (!alive[a]) continue;
                double ax = nX[att[a]] + 0.5, ay = nY[att[a]] + 0.5, az = nZ[att[a]] + 0.5;
                int j = nodes.nearest(ax, ay, az, st.di(), this::nodePos);
                if (j < 0) continue;
                double vx = ax - px[j], vy = ay - py[j], vz = az - pz[j], l = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (l < 1e-6) continue;
                double[] acc = dirs.computeIfAbsent(j, k -> new double[3]);
                acc[0] += vx / l;
                acc[1] += vy / l;
                acc[2] += vz / l;
            }
            List<Integer> born = new ArrayList<>();
            for (Map.Entry<Integer, double[]> en : dirs.entrySet()) {
                int j = en.getKey();
                double[] v = en.getValue();
                double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                if (l < 1e-6) continue;
                double tx = px[j] + st.step() * v[0] / l, ty = py[j] + st.step() * v[1] / l, tz = pz[j] + st.step() * v[2] / l;
                int h = hugIx.nearest(tx, ty, tz, 1.2, this::graphPos);
                if (h < 0) continue;
                double qx = nX[h] + 0.5, qy = nY[h] + 0.5, qz = nZ[h] + 0.5;
                double L = Math.sqrt(sq(qx - px[j], qy - py[j], qz - pz[j]));
                if (L < 0.9 || taken.contains(nVox[h])) continue;
                if (g.solidAt((qx + px[j]) / 2, (qy + py[j]) / 2, (qz + pz[j]) / 2)) continue;
                taken.add(nVox[h]);
                born.add(add(qx, qy, qz, j, K_FRINGE, ci, dn[j] + L, 0, Double.NaN));
            }
            if (born.isEmpty()) break;
            for (int b : born) nodes.add(b, px[b], py[b], pz[b]);
            grown += born.size();
            for (int a = 0; a < att.length; a++) {
                if (!alive[a]) continue;
                double ax = nX[att[a]] + 0.5, ay = nY[att[a]] + 0.5, az = nZ[att[a]] + 0.5;
                for (int b : born) {
                    if (sq(ax - px[b], ay - py[b], az - pz[b]) <= st.dk() * st.dk()) {
                        alive[a] = false;
                        break;
                    }
                }
            }
        }
        report.append(String.format(Locale.ROOT, " | fringe cell %d: %d nodes from %d attractors", cores.get(ci).id(), grown,
                att.length));
    }

    double[] graphPos(int u) {
        return new double[]{nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5};
    }
}
