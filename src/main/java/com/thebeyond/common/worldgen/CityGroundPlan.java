package com.thebeyond.common.worldgen;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Ground under a pedestal city's first floor where the island misses it, grown out from the island's own edge. */
final class CityGroundPlan {
    /** Grid margin around the start box, room for the widest join the plan draws. */
    static final int PAD = 14;
    static final int SEAT_BAND = 4;
    static final int STEP = SEAT_BAND;
    static final int SEAT_OVER = 3;
    static final int SEAT_REACH = 8;
    static final int LOWER_SCAN = 64;
    static final int LOWER_GAP = 3;
    static final double MARGIN_LO = 1.0, MARGIN_HI = 4.0;
    static final double REACH_BASE = 6.0, REACH_PER_BLOCK = 1.6;
    /** Overhang past which the join stops widening: the widest join still ends inside the grid. */
    static final double REACH_CAP = 16.0;
    static final int EDGE_FADE = 3;
    static final double TIP_THIN = 0.5;
    static final double THICKEN_MIN = 0.75;
    static final int TALUS = 3, TALUS_DROP = STEP;
    /** Ground floor gaps this narrow close, and an apron of ground reaches APRON past it so a player can walk around. */
    static final int CLOSE_R = 6, APRON = 3;

    interface Terrain {
        boolean solid(int x, int y, int z);
    }

    interface Field {
        /** In [-1, 1]. */
        double at(int x, int z);
    }

    interface Foot {
        /** Height of the column's lowest block, MIN_VALUE off the ground floor. */
        int sole(int x, int z);
    }

    final int x0, z0, nx, nz, floor;
    final int[] topLo, topHi, botLo, botHi;
    int held, step, missing, sunk, apron, volume, columns;
    double reach;
    boolean orphan;
    boolean[] inApron;

    private CityGroundPlan(int x0, int z0, int nx, int nz, int floor) {
        this.x0 = x0; this.z0 = z0; this.nx = nx; this.nz = nz; this.floor = floor;
        int n = nx * nz;
        topLo = new int[n]; topHi = new int[n]; botLo = new int[n]; botHi = new int[n];
        java.util.Arrays.fill(topLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(topHi, Integer.MIN_VALUE);
        java.util.Arrays.fill(botLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(botHi, Integer.MIN_VALUE);
    }

    int index(int x, int z) {
        int i = x - x0, j = z - z0;
        return i < 0 || i >= nx || j < 0 || j >= nz ? -1 : i + j * nx;
    }

    boolean hasTop(int c) { return topLo[c] <= topHi[c]; }
    boolean hasBottom(int c) { return botLo[c] <= botHi[c]; }

    static CityGroundPlan compute(BoundingBox start, int floor, Foot foot, Terrain terrain, Field lobe) {
        int F = floor;
        int x0 = start.minX() - PAD, z0 = start.minZ() - PAD;
        int nx = start.getXSpan() + 2 * PAD, nz = start.getZSpan() + 2 * PAD;
        CityGroundPlan p = new CityGroundPlan(x0, z0, nx, nz, F);
        int n = nx * nz;
        int[] T = new int[n], B = new int[n], L = new int[n], S = new int[n];
        boolean[] A = new boolean[n], G = new boolean[n], miss = new boolean[n];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int c = i + j * nx;
                S[c] = foot.sole(x0 + i, z0 + j);
                G[c] = S[c] != Integer.MIN_VALUE;
                if (G[c]) seatIsland(terrain, x0 + i, z0 + j, S[c], T, B, L, c);
            }
        }
        for (int c = 0; c < n; c++) {
            if (!G[c]) continue;
            if (T[c] == Integer.MIN_VALUE || (S[c] - 1) - T[c] > STEP) { miss[c] = true; p.missing++; }
            else if (T[c] >= S[c] + 1) p.sunk++;
            else if (T[c] >= S[c] - 1) p.held++;
            else {
                p.step++;
                p.topLo[c] = T[c] + 1;
                p.topHi[c] = S[c] - 1;
            }
        }
        boolean[] W = apron(G, nx, nz);
        p.inApron = W;
        for (int c = 0; c < n; c++) {
            if (!W[c]) continue;
            seatIsland(terrain, x0 + c % nx, z0 + c / nx, F, T, B, L, c);
            // An island lower down is ground to walk on, and filling up to the floor over it would stand as a podium.
            if (T[c] == Integer.MIN_VALUE) { miss[c] = true; p.apron++; }
        }
        if (p.missing > 0 || p.apron > 0) {
            // Only ground with a gap to bridge needs the island around it.
            for (int j = 0; j < nz; j++) {
                for (int i = 0; i < nx; i++) {
                    int c = i + j * nx;
                    if (!G[c] && !W[c]) seatIsland(terrain, x0 + i, z0 + j, F, T, B, L, c);
                    A[c] = !miss[c] && T[c] != Integer.MIN_VALUE && T[c] >= F - 1 - SEAT_BAND && T[c] <= F + SEAT_OVER;
                }
            }
            p.grow(T, B, L, S, A, G, W, miss, lobe);
        }
        p.talus(terrain, G, W);
        for (int c = 0; c < n; c++) {
            int x = x0 + c % nx, z = z0 + c / nx, v = 0;
            if (p.hasTop(c)) for (int y = p.topLo[c]; y <= p.topHi[c]; y++) if (!terrain.solid(x, y, z)) v++;
            if (p.hasBottom(c)) for (int y = p.botLo[c]; y <= p.botHi[c]; y++) if (!terrain.solid(x, y, z)) v++;
            if (v > 0) { p.volume += v; p.columns++; }
        }
        return p;
    }

    /** Top and base of the island run nearest the floor, and the highest top of any island further down. */
    private static void seatIsland(Terrain t, int x, int z, int F, int[] T, int[] B, int[] L, int c) {
        int ref = F - 1, best = Integer.MIN_VALUE;
        int hi = ref + SEAT_REACH, lo = ref - SEAT_REACH;
        // A run already solid at the window's top reads as reaching it: the island rises over the floor there.
        boolean above = t.solid(x, hi + 1, z);
        for (int y = hi; y >= lo; y--) {
            boolean s = t.solid(x, y, z);
            if (s && !above && (best == Integer.MIN_VALUE || Math.abs(y - ref) < Math.abs(best - ref))) best = y;
            if (s && above && best == Integer.MIN_VALUE) best = hi;
            above = s;
        }
        T[c] = best;
        B[c] = Integer.MIN_VALUE;
        L[c] = Integer.MIN_VALUE;
        if (best != Integer.MIN_VALUE) {
            int y = best;
            while (y > best - LOWER_SCAN && t.solid(x, y - 1, z)) y--;
            B[c] = y;
        }
        int from = best != Integer.MIN_VALUE && best >= F - 1 - SEAT_BAND ? B[c] - 1 : F - 2 - SEAT_BAND;
        for (int y = from; y >= F - 1 - LOWER_SCAN; y--) {
            if (t.solid(x, y, z)) { L[c] = y; break; }
        }
        if (best != Integer.MIN_VALUE && best < F - 1 - SEAT_BAND && (L[c] == Integer.MIN_VALUE || best > L[c])) L[c] = best;
    }

    private void grow(int[] T, int[] B, int[] L, int[] S, boolean[] A, boolean[] G, boolean[] W, boolean[] miss, Field lobe) {
        int n = nx * nz, F = floor;
        boolean[] notA = new boolean[n];
        for (int c = 0; c < n; c++) notA[c] = !A[c];
        double[] din = edt(notA, nx, nz), dout = edt(A, nx, nz);
        double[] sd = new double[n];
        for (int c = 0; c < n; c++) sd[c] = A[c] ? din[c] : -dout[c];
        double p = 0;
        for (int c = 0; c < n; c++) if (miss[c] && dout[c] > p) p = dout[c];
        reach = p;
        double k = REACH_BASE + REACH_PER_BLOCK * Math.min(p, REACH_CAP);
        boolean[] notMiss = new boolean[n];
        for (int c = 0; c < n; c++) notMiss[c] = !miss[c];
        double[] mIn = edt(notMiss, nx, nz), mOut = edt(miss, nx, nz);
        boolean[] U = new boolean[n];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int c = i + j * nx;
                double margin = MARGIN_LO + (MARGIN_HI - MARGIN_LO) * (0.5 + 0.5 * lobe.at(x0 + i, z0 + j));
                double sdM = (miss[c] ? -mIn[c] : mOut[c]) - margin;
                U[c] = smin(-sd[c], sdM, k) <= 0.0 || miss[c];
            }
        }
        boolean[] notU = new boolean[n];
        for (int c = 0; c < n; c++) notU[c] = !U[c];
        double[] rU = edt(notU, nx, nz);
        double[] gs = blur(sd, nx, nz, 1.2);
        int[] aCells = new int[n];
        int na = 0;
        for (int c = 0; c < n; c++) if (A[c]) aCells[na++] = c;
        if (na == 0) { orphan = true; return; }
        double[] depth = new double[n];
        for (int c = 0; c < n; c++) depth[c] = A[c] ? (F - 1) - B[c] + 1 : 0.0;
        double[] want = new double[n], wtop = new double[n];
        boolean[] zone = new boolean[n];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int c = i + j * nx;
                want[c] = depth[c];
                wtop[c] = F - 1;
                if (!U[c]) continue;
                double shift = rU[c] - sd[c];
                if (shift <= 0.5) continue;
                double gx = grad(gs, i, j, true), gz = grad(gs, i, j, false);
                double len = Math.hypot(gx, gz);
                double ux = len > 1e-6 ? gx / len : 0.0, uz = len > 1e-6 ? gz / len : 0.0;
                double fi = i + ux * shift, fj = j + uz * shift;
                int qi = (int) Math.round(fi), qj = (int) Math.round(fj);
                int q = qi >= 0 && qi < nx && qj >= 0 && qj < nz ? qi + qj * nx : -1;
                if (q < 0 || !A[q]) {
                    double bestD = Double.MAX_VALUE;
                    for (int a = 0; a < na; a++) {
                        int ai = aCells[a] % nx, aj = aCells[a] / nx;
                        double dd = (ai - fi) * (ai - fi) + (aj - fj) * (aj - fj);
                        if (dd < bestD) { bestD = dd; q = aCells[a]; }
                    }
                }
                want[c] = Math.max(want[c], depth[q]);
                wtop[c] = Math.min(F - 1, T[q]);
                zone[c] = true;
            }
        }
        double[] zw = new double[n], zv = new double[n];
        for (int c = 0; c < n; c++) { zv[c] = zone[c] ? want[c] : 0.0; zw[c] = zone[c] ? 1.0 : 0.0; }
        double[] sm = blur(zv, nx, nz, 1.0), wt = blur(zw, nx, nz, 1.0);
        double[] av = new double[n], aw = new double[n];
        for (int c = 0; c < n; c++) { av[c] = A[c] ? depth[c] : 0.0; aw[c] = A[c] ? 1.0 : 0.0; }
        double[] dsm = blur(av, nx, nz, 1.5), dw = blur(aw, nx, nz, 1.5);
        double s1 = 0, s2 = 0;
        for (int a = 0; a < na; a++) {
            int c = aCells[a];
            double r = depth[c] - dsm[c] / Math.max(dw[c], 1e-6);
            s1 += r; s2 += r * r;
        }
        double mean = s1 / na, rough = Math.sqrt(Math.max(0.0, s2 / na - mean * mean));
        double deepest = 0;
        for (int a = 0; a < na; a++) deepest = Math.max(deepest, depth[aCells[a]]);
        double far = 0;
        for (int c = 0; c < n; c++) if (zone[c] && dout[c] > far) far = dout[c];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int c = i + j * nx;
                if (!zone[c]) continue;
                int x = x0 + i, z = z0 + j;
                int e = Math.min(Math.min(i, j), Math.min(nx - 1 - i, nz - 1 - j));
                if (e == 0) continue;
                double wantS = sm[c] / Math.max(wt[c], 1e-6) * Math.min(1.0, (double) e / EDGE_FADE);
                double tip = far > 0 ? 1.0 - TIP_THIN * Math.pow(Math.min(1.0, dout[c] / far), 1.5) : 1.0;
                double jitter = (jitter01(x, z) - 0.5) * 2.0 * rough;
                // Never deeper than the island's own deepest base, and the new ground is at least one course.
                int dNew = (int) Math.min(deepest, Math.max(A[c] ? 0 : 1, Math.round(wantS * tip + jitter)));
                int lo = F - dNew;
                if (A[c]) {
                    if (wantS - depth[c] < THICKEN_MIN) continue;
                    int hi = Math.min(B[c] - 1, F - 1);
                    // The air under the island's base stays open: a cave, or the gap to an island below.
                    if (L[c] != Integer.MIN_VALUE) lo = Math.max(lo, L[c] + LOWER_GAP);
                    if (hi >= lo) { botLo[c] = lo; botHi[c] = hi; }
                } else {
                    // Ground rising over the floor is a hill the structure stands into, with nothing to lay under it.
                    if (T[c] != Integer.MIN_VALUE && T[c] > F + SEAT_OVER) continue;
                    if (W[c] && !miss[c]) continue;
                    int hi = G[c] ? S[c] - 1 : W[c] ? F - 1 : (int) Math.round(wtop[c]);
                    // Under the floor a top in the seat window is the same island, elsewhere a deeper top may be another.
                    boolean own = G[c] && T[c] != Integer.MIN_VALUE;
                    if (own) lo = T[c] + 1;
                    else if (L[c] != Integer.MIN_VALUE) lo = Math.max(lo, L[c] + LOWER_GAP);
                    if (hi >= lo) {
                        topLo[c] = Math.min(topLo[c], lo);
                        topHi[c] = Math.max(topHi[c], hi);
                    }
                }
            }
        }
    }

    /** Steps the ground down outward, a course per block, from a floor or apron edge two or more courses over its neighbour. */
    private void talus(Terrain t, boolean[] G, boolean[] W) {
        int n = nx * nz;
        int[] top = new int[n], queued = new int[n];
        boolean[] stop = new boolean[n];
        int[] front = new int[n], next = new int[n];
        int nf = 0;
        for (int c = 0; c < n; c++) {
            top[c] = hasTop(c) ? topHi[c] : Integer.MIN_VALUE;
            // The apron's own ground stands at the floor, and the steps go down into the rest of it.
            stop[c] = G[c] || (W[c] && hasTop(c));
            if ((G[c] || W[c]) && hasTop(c)) front[nf++] = c;
        }
        for (int r = 0; r < TALUS && nf > 0; r++) {
            int nn = 0;
            for (int f = 0; f < nf; f++) {
                int c = front[f], i = c % nx, j = c / nx, t0 = top[c];
                for (int d = 0; d < 4; d++) {
                    int ni = i + (d == 0 ? 1 : d == 1 ? -1 : 0), nj = j + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (ni < 0 || ni >= nx || nj < 0 || nj >= nz) continue;
                    int e = ni + nj * nx;
                    if (stop[e]) continue;
                    int tn = top[e] != Integer.MIN_VALUE ? top[e] : groundBelow(t, x0 + ni, z0 + nj, t0);
                    if (tn == Integer.MIN_VALUE || t0 - tn < 2 || t0 - tn > TALUS_DROP) continue;
                    topLo[e] = Math.min(topLo[e], tn + 1);
                    topHi[e] = Math.max(topHi[e], t0 - 1);
                    top[e] = t0 - 1;
                    if (queued[e] != r + 1) { queued[e] = r + 1; next[nn++] = e; }
                }
            }
            int[] swap = front; front = next; next = swap;
            nf = nn;
        }
    }

    static boolean[] apron(boolean[] G, int nx, int nz) {
        int n = nx * nz;
        double[] dG = edt(G, nx, nz);
        boolean[] far = new boolean[n];
        for (int c = 0; c < n; c++) far[c] = dG[c] > CLOSE_R + 0.5;
        double[] dFar = edt(far, nx, nz);
        boolean[] closed = new boolean[n];
        for (int c = 0; c < n; c++) closed[c] = G[c] || dFar[c] > CLOSE_R + 0.5;
        double[] dC = edt(closed, nx, nz);
        boolean[] w = new boolean[n];
        for (int c = 0; c < n; c++) w[c] = !G[c] && dC[c] <= APRON + 0.5;
        return w;
    }

    private static int groundBelow(Terrain t, int x, int z, int y) {
        for (int yy = y - 1; yy >= y - TALUS_DROP; yy--) {
            if (t.solid(x, yy, z)) return yy;
        }
        return Integer.MIN_VALUE;
    }

    static double smin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    static double jitter01(int x, int z) {
        int h = (x * 73856093) ^ (z * 19349663);
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xffff) / 65535.0;
    }

    /** Euclidean distance from every cell to the nearest target cell (0 on targets), two-pass lower envelope. */
    static double[] edt(boolean[] target, int nx, int nz) {
        final double inf = 1e20;
        int n = nx * nz, m = Math.max(nx, nz);
        double[] g = new double[n], f = new double[m], dd = new double[m], zz = new double[m + 1];
        int[] v = new int[m];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) f[i] = target[i + j * nx] ? 0.0 : inf;
            dt1(f, nx, dd, v, zz);
            for (int i = 0; i < nx; i++) g[i + j * nx] = dd[i];
        }
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) f[j] = g[i + j * nx];
            dt1(f, nz, dd, v, zz);
            for (int j = 0; j < nz; j++) g[i + j * nx] = Math.sqrt(dd[j]);
        }
        return g;
    }

    private static void dt1(double[] f, int n, double[] d, int[] v, double[] z) {
        int k = 0;
        v[0] = 0; z[0] = Double.NEGATIVE_INFINITY; z[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            while (s <= z[k]) {
                k--;
                s = ((f[q] + (double) q * q) - (f[v[k]] + (double) v[k] * v[k])) / (2.0 * q - 2.0 * v[k]);
            }
            k++;
            v[k] = q; z[k] = s; z[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (z[k + 1] < q) k++;
            d[q] = (double) (q - v[k]) * (q - v[k]) + f[v[k]];
        }
    }

    /** Separable Gaussian, edges mirrored the way scipy's reflect mode mirrors them. */
    static double[] blur(double[] a, int nx, int nz, double sigma) {
        int r = (int) (4.0 * sigma + 0.5);
        double[] w = new double[2 * r + 1];
        double sum = 0;
        for (int t = -r; t <= r; t++) { w[t + r] = Math.exp(-(double) t * t / (2 * sigma * sigma)); sum += w[t + r]; }
        for (int t = 0; t < w.length; t++) w[t] /= sum;
        double[] tmp = new double[a.length], out = new double[a.length];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                double s = 0;
                for (int t = -r; t <= r; t++) s += w[t + r] * a[reflect(i + t, nx) + j * nx];
                tmp[i + j * nx] = s;
            }
        }
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                double s = 0;
                for (int t = -r; t <= r; t++) s += w[t + r] * tmp[i + reflect(j + t, nz) * nx];
                out[i + j * nx] = s;
            }
        }
        return out;
    }

    private static int reflect(int i, int n) {
        while (i < 0 || i >= n) i = i < 0 ? -i - 1 : 2 * n - i - 1;
        return i;
    }

    private double grad(double[] a, int i, int j, boolean alongX) {
        if (alongX) {
            if (nx < 2) return 0.0;
            if (i == 0) return a[1 + j * nx] - a[j * nx];
            if (i == nx - 1) return a[i + j * nx] - a[i - 1 + j * nx];
            return (a[i + 1 + j * nx] - a[i - 1 + j * nx]) * 0.5;
        }
        if (nz < 2) return 0.0;
        if (j == 0) return a[i + nx] - a[i];
        if (j == nz - 1) return a[i + j * nx] - a[i + (j - 1) * nx];
        return (a[i + (j + 1) * nx] - a[i + (j - 1) * nx]) * 0.5;
    }
}
