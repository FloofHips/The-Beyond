package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shortest paths over the graph, the search every trunk and link grows along. */
abstract class NetworkPaths extends NetworkLigaments {
    NetworkPaths(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    /** Incremental Dijkstra over the allowed cells, climbing dearer than descending with gravitropism, more so in the air. */
    final class Search {
        final boolean[] allowed;
        final double limit;
        final double[] dist = new double[n];
        final int[] pred = new int[n], src = new int[n];
        final OrganicHeap heap = new OrganicHeap();
        boolean grav = st.bio().grav(), geo;
        double linkCap = Double.POSITIVE_INFINITY;

        Search undirected() {
            grav = false;
            return this;
        }

        Search geometric() {
            grav = false;
            geo = true;
            return this;
        }

        Search linkCap(double cap) {
            linkCap = cap;
            return this;
        }

        double up(double sin, boolean air) {
            if (!grav) return 1;
            double gu = air ? st.bio().gUpLink() : st.bio().gUp();
            return 1 + gu * Math.max(sin, 0) - st.bio().gDn() * Math.max(-sin, 0);
        }

        Search(boolean[] allowed, double limit) {
            this.allowed = allowed;
            this.limit = limit;
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            Arrays.fill(pred, -1);
            Arrays.fill(src, -1);
        }

        void source(int u) {
            dist[u] = 0;
            pred[u] = -1;
            src[u] = u;
            heap.push(0, u);
        }

        void run() {
            while (heap.size > 0) {
                double d = heap.topKey();
                int u = heap.pop();
                if (d > dist[u]) continue;
                int x = nX[u], y = nY[u], z = nZ[u];
                for (int k = 0; k < 26; k++) {
                    int qx = x + OFF26[k][0], qy = y + OFF26[k][1], qz = z + OFF26[k][2];
                    if (!g.inside(qx, qy, qz)) continue;
                    int v = nodeAt(qx, qy, qz);
                    if (v < 0 || !allowed[nLab[v]]) continue;
                    double w = geo ? LEN26[k] : LEN26[k] * 0.5 * (nMult[u] + nMult[v]);
                    relax(u, v, d + w * up(OFF26[k][1] / LEN26[k], false));
                }
                List<double[]> extra = hasLink[u] ? linkAdj.get(u) : null;
                if (extra != null) {
                    for (double[] e : extra) {
                        int v = (int) e[0];
                        if (!allowed[nLab[v]] || e[2] > linkCap) continue;
                        relax(u, v, d + (geo ? e[2] : e[1]) * up((nY[v] - nY[u]) / e[2], true));
                    }
                }
            }
        }

        private void relax(int u, int v, double nd) {
            if (nd < dist[v] && nd <= limit) {
                dist[v] = nd;
                pred[v] = u;
                src[v] = src[u];
                heap.push(nd, v);
            }
        }

        /** Graph nodes from just after the source to u. */
        List<Integer> path(int u) {
            List<Integer> p = new ArrayList<>();
            while (pred[u] >= 0) {
                p.add(u);
                u = pred[u];
            }
            java.util.Collections.reverse(p);
            return p;
        }
    }
}
