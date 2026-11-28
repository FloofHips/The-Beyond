package com.thebeyond.common.worldgen;

import java.util.Arrays;

/** Incomplete Cholesky factor preconditioning the slime mold's conductance solve. */
final class OrganicIchol {
    final int nc;
    final int[] node, start, col, edge;
    final double[] lv, ld, y;
    private final int[] loc;

    // Lower rows in node order, one entry per edge at (larger, smaller) end, sorted by column.
    OrganicIchol(int mm, int[] a, int[] b, int k) {
        loc = new int[mm];
        Arrays.fill(loc, -1);
        boolean[] has = new boolean[mm];
        for (int i = 0; i < k; i++) {
            has[a[i]] = true;
            has[b[i]] = true;
        }
        int n = 0;
        for (int v = 0; v < mm; v++) if (has[v]) loc[v] = n++;
        nc = n;
        node = new int[n];
        for (int v = 0; v < mm; v++) if (has[v]) node[loc[v]] = v;
        start = new int[n + 1];
        for (int i = 0; i < k; i++) start[Math.max(loc[a[i]], loc[b[i]]) + 1]++;
        for (int i = 0; i < n; i++) start[i + 1] += start[i];
        col = new int[k];
        edge = new int[k];
        int[] fill = Arrays.copyOf(start, n);
        for (int i = 0; i < k; i++) {
            int row = Math.max(loc[a[i]], loc[b[i]]);
            col[fill[row]] = Math.min(loc[a[i]], loc[b[i]]);
            edge[fill[row]++] = i;
        }
        for (int r = 0; r < n; r++) {
            for (int x = start[r] + 1; x < start[r + 1]; x++) {
                int c = col[x], e = edge[x], w = x - 1;
                for (; w >= start[r] && col[w] > c; w--) {
                    col[w + 1] = col[w];
                    edge[w + 1] = edge[w];
                }
                col[w + 1] = c;
                edge[w + 1] = e;
            }
        }
        lv = new double[k];
        ld = new double[n];
        y = new double[n];
    }

    void factor(double[] gc, double[] diag, int snk) {
        int s = loc[snk];
        for (int i = 0; i < nc; i++) {
            if (i == s) {
                for (int e = start[i]; e < start[i + 1]; e++) lv[e] = 0;
                ld[i] = 1;
                continue;
            }
            double di = diag[node[i]];
            for (int e = start[i]; e < start[i + 1]; e++) {
                int j = col[e];
                if (j == s) {
                    lv[e] = 0;
                    continue;
                }
                double v = -gc[edge[e]];
                for (int u = start[i], w = start[j]; u < e && w < start[j + 1]; ) {
                    if (col[u] == col[w]) v -= lv[u++] * lv[w++];
                    else if (col[u] < col[w]) u++;
                    else w++;
                }
                lv[e] = v / ld[j];
                di -= lv[e] * lv[e];
            }
            ld[i] = Math.sqrt(di);
        }
    }

    // z = (L L^T)^-1 r on the component, zero at the sink and outside it.
    void apply(double[] r, double[] z, int snk) {
        for (int i = 0; i < nc; i++) {
            double v = r[node[i]];
            for (int e = start[i]; e < start[i + 1]; e++) v -= lv[e] * y[col[e]];
            y[i] = v / ld[i];
        }
        for (int i = nc - 1; i >= 0; i--) {
            double v = y[i] / ld[i];
            z[node[i]] = v;
            for (int e = start[i]; e < start[i + 1]; e++) y[col[e]] -= lv[e] * v;
        }
        z[snk] = 0;
    }

    /** The same stopping rule as the diagonal one: every residual under 1e-9 of I0. Returns the iterations. */
    int solve(double[] gc, double[] diag, int[] a, int[] b, int k, int mm, int src, int snk, double i0, double[] p,
              double[] r, double[] z, double[] q, double[] ap) {
        factor(gc, diag, snk);
        Arrays.fill(p, 0);
        Arrays.fill(r, 0);
        r[src] = i0;
        apply(r, z, snk);
        double rz = 0;
        for (int i = 0; i < mm; i++) {
            q[i] = z[i];
            rz += r[i] * z[i];
        }
        double r0 = Math.abs(i0);
        int used = 0;
        for (int it = 0; it < 2000 && rz > 0; it++) {
            used++;
            Arrays.fill(ap, 0);
            for (int i = 0; i < k; i++) {
                double f = gc[i] * (q[a[i]] - q[b[i]]);
                ap[a[i]] += f;
                ap[b[i]] -= f;
            }
            ap[snk] = 0;
            double qap = 0;
            for (int i = 0; i < mm; i++) qap += q[i] * ap[i];
            if (qap <= 0) break;
            double alpha = rz / qap, rn = 0;
            for (int i = 0; i < mm; i++) {
                if (i == snk) continue;
                p[i] += alpha * q[i];
                r[i] -= alpha * ap[i];
                rn = Math.max(rn, Math.abs(r[i]));
            }
            if (rn < 1e-9 * r0) break;
            apply(r, z, snk);
            double rz2 = 0;
            for (int i = 0; i < mm; i++) rz2 += r[i] * z[i];
            double beta = rz2 / rz;
            rz = rz2;
            for (int i = 0; i < mm; i++) q[i] = z[i] + beta * q[i];
        }
        return used;
    }
}
