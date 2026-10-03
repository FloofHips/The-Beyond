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
    static final int EDGE_REACH = 3, EDGE_RUN = 2;
    static final int CLOSE_R = 6, APRON = 3;
    /** Range of the lift's fade length, picked per column by lobe noise. */
    static final double LIFT_R_LO = 2.0, LIFT_R_HI = 18.0;
    /** Amplitude of the wave bending the lift's steps off the walls, and how near the feet it may only shorten the distance. */
    static final double LIFT_WAVE_AMP = 3.0, LIFT_WAVE_NEAR = 4.0;
    static final int LIFT_PAD = (int) Math.ceil(LIFT_R_HI + LIFT_WAVE_AMP) + 1;
    /** Lobed margin beside a raised foot where the carve keeps the island up to the foot's rock. */
    static final double KEEP_LO = 2.0, KEEP_HI = 5.0;
    /** Feet closing a gap up to four columns wide deck the pocket between them. */
    static final int POCKET_R = 2;

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

    interface Pads {
        /** A floor foot with no full block on it, read as paving. */
        boolean pad(int x, int z);
    }

    final int x0, z0, nx, nz, floor;
    final int[] topLo, topHi, botLo, botHi;
    /** Ground around the floor, laid a course at a time only on a course already placed. */
    final int[] rampLo, rampHi;
    int held, step, missing, sunk, apron, volume, columns, edgeVolume, edgeColumns;
    double reach;
    boolean orphan;
    boolean[] inApron;
    /** Blocks the island's surface rises around the floor, and liftCap where it stops, on a grid LIFT_PAD wider. */
    float[] lift;
    int[] liftCap;
    float liftMax;
    int liftColumns;
    /** Course up to which the carve keeps the island, on the lift's grid. */
    int[] keep;
    /** Sole of the nearest foot, the higher on a tie: off the floor the plan's own ground stays under it. */
    int[] nearSole;
    /** Pockets closed in by feet, the nearest raised or paving, decked to the floor like a courtyard. */
    boolean[] deck;
    /** Chunk-aligned area whose chunks reference the start: the only columns that ever read this plan. */
    int reachX0, reachZ0, reachX1, reachZ1;

    private CityGroundPlan(int x0, int z0, int nx, int nz, int floor) {
        this.x0 = x0; this.z0 = z0; this.nx = nx; this.nz = nz; this.floor = floor;
        int n = nx * nz;
        topLo = new int[n]; topHi = new int[n]; botLo = new int[n]; botHi = new int[n];
        rampLo = new int[n]; rampHi = new int[n];
        java.util.Arrays.fill(topLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(topHi, Integer.MIN_VALUE);
        java.util.Arrays.fill(botLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(botHi, Integer.MIN_VALUE);
        java.util.Arrays.fill(rampLo, Integer.MAX_VALUE);
        java.util.Arrays.fill(rampHi, Integer.MIN_VALUE);
    }

    int index(int x, int z) {
        int i = x - x0, j = z - z0;
        return i < 0 || i >= nx || j < 0 || j >= nz ? -1 : i + j * nx;
    }

    boolean hasTop(int c) { return topLo[c] <= topHi[c]; }
    boolean hasBottom(int c) { return botLo[c] <= botHi[c]; }
    boolean hasRamp(int c) { return rampLo[c] <= rampHi[c]; }

    private int liftIndex(int x, int z) {
        int w = nx + 2 * LIFT_PAD, i = x - (x0 - LIFT_PAD), j = z - (z0 - LIFT_PAD);
        return lift == null || i < 0 || i >= w || j < 0 || j >= nz + 2 * LIFT_PAD ? -1 : i + j * w;
    }

    float liftAt(int x, int z) {
        int e = liftIndex(x, z);
        return e < 0 ? 0f : lift[e];
    }

    int liftCapAt(int x, int z) {
        int e = liftIndex(x, z);
        return e < 0 ? Integer.MIN_VALUE : liftCap[e];
    }

    int nearSoleAt(int x, int z) {
        int c = index(x, z);
        return c < 0 || nearSole == null ? Integer.MIN_VALUE : nearSole[c];
    }

    int keepAt(int x, int z) {
        int w = nx + 2 * LIFT_PAD, i = x - (x0 - LIFT_PAD), j = z - (z0 - LIFT_PAD);
        return keep == null || i < 0 || i >= w || j < 0 || j >= nz + 2 * LIFT_PAD ? Integer.MIN_VALUE : keep[i + j * w];
    }

    boolean deckAt(int x, int z) {
        int c = index(x, z);
        return c >= 0 && deck != null && deck[c];
    }

    static CityGroundPlan compute(BoundingBox start, int floor, Foot foot, Terrain terrain, Field lobe) {
        return compute(start, start.inflatedBy(12), floor, foot, (x, z) -> false, terrain, lobe, (x, z) -> 0.0);
    }

    static CityGroundPlan compute(BoundingBox start, int floor, Foot foot, Pads pads, Terrain terrain, Field lobe) {
        return compute(start, start.inflatedBy(12), floor, foot, pads, terrain, lobe, (x, z) -> 0.0);
    }

    static CityGroundPlan compute(BoundingBox start, BoundingBox reach, int floor, Foot foot, Terrain terrain, Field lobe) {
        return compute(start, reach, floor, foot, (x, z) -> false, terrain, lobe, (x, z) -> 0.0);
    }

    static CityGroundPlan compute(BoundingBox start, BoundingBox reach, int floor, Foot foot, Terrain terrain, Field lobe, Field wave) {
        return compute(start, reach, floor, foot, (x, z) -> false, terrain, lobe, wave);
    }

    static CityGroundPlan compute(BoundingBox start, BoundingBox reach, int floor, Foot foot, Pads pads, Terrain terrain, Field lobe,
            Field wave) {
        int F = floor;
        int x0 = start.minX() - PAD, z0 = start.minZ() - PAD;
        int nx = start.getXSpan() + 2 * PAD, nz = start.getZSpan() + 2 * PAD;
        CityGroundPlan p = new CityGroundPlan(x0, z0, nx, nz, F);
        p.reachX0 = reach.minX() & ~15; p.reachZ0 = reach.minZ() & ~15;
        p.reachX1 = (reach.maxX() & ~15) + 15; p.reachZ1 = (reach.maxZ() & ~15) + 15;
        int n = nx * nz;
        int[] T = new int[n], B = new int[n], L = new int[n], S = new int[n], E = new int[n];
        boolean[] A = new boolean[n], G = new boolean[n], miss = new boolean[n];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int c = i + j * nx;
                S[c] = foot.sole(x0 + i, z0 + j);
                G[c] = S[c] != Integer.MIN_VALUE;
                // Paving reads as raised only to close a pocket, elsewhere the ground meets every floor foot under it.
                E[c] = G[c] && S[c] == F && pads.pad(x0 + i, z0 + j) ? F + 1 : S[c];
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
        p.lift(terrain, G, S, lobe, wave);
        p.keep(G, S, lobe);
        p.nearSole = new int[n];
        for (int c = 0; c < n; c++) p.nearSole[c] = G[c] ? S[c] : p.nearestRock(G, S, c, CLOSE_R + APRON + 1) + 1;
        p.deck = p.pockets(G, E);
        if (!p.orphan) p.edge(terrain, W, lobe);
        for (int c = 0; c < n; c++) {
            int x = x0 + c % nx, z = z0 + c / nx, v = 0, e = 0;
            if (p.hasTop(c)) for (int y = p.topLo[c]; y <= p.topHi[c]; y++) if (!terrain.solid(x, y, z)) v++;
            if (p.hasBottom(c)) for (int y = p.botLo[c]; y <= p.botHi[c]; y++) if (!terrain.solid(x, y, z)) v++;
            if (p.hasRamp(c)) for (int y = p.rampLo[c]; y <= p.rampHi[c]; y++) if (!terrain.solid(x, y, z)) e++;
            if (v > 0) { p.volume += v; p.columns++; }
            if (e > 0) { p.edgeVolume += e; p.edgeColumns++; }
        }
        return p;
    }

    /** Top of the island run nearest ref within SEAT_REACH, a run solid past the window's top reading as reaching it. */
    static int topNear(Terrain t, int x, int z, int ref) {
        int best = Integer.MIN_VALUE;
        int hi = ref + SEAT_REACH, lo = ref - SEAT_REACH;
        boolean above = t.solid(x, hi + 1, z);
        for (int y = hi; y >= lo; y--) {
            boolean s = t.solid(x, y, z);
            if (s && !above && (best == Integer.MIN_VALUE || Math.abs(y - ref) < Math.abs(best - ref))) best = y;
            if (s && above && best == Integer.MIN_VALUE) best = hi;
            above = s;
        }
        return best;
    }

    /** Top and base of the island run nearest the floor, and the highest top of any island further down. */
    private static void seatIsland(Terrain t, int x, int z, int F, int[] T, int[] B, int[] L, int c) {
        int best = topNear(t, x, z, F - 1);
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
                    int hi = G[c] ? S[c] - 1 : W[c] ? nearestRock(G, S, c, CLOSE_R + APRON + 1) : (int) Math.round(wtop[c]);
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

    /** Columns a square of POCKET_R closes in between feet, where the nearest foot is raised or paving. */
    private boolean[] pockets(boolean[] G, int[] E) {
        boolean[] closed = square(square(G, POCKET_R, true), POCKET_R, false);
        boolean[] out = null;
        for (int c = 0; c < nx * nz; c++) {
            if (!closed[c] || G[c] || nearestRock(G, E, c, POCKET_R + 1) < floor) continue;
            if (out == null) out = new boolean[nx * nz];
            out[c] = true;
        }
        return out;
    }

    /** Square dilation or erosion of radius r, the grid's outside reading as unset. */
    private boolean[] square(boolean[] a, int r, boolean dilate) {
        boolean[] rows = new boolean[nx * nz], out = new boolean[nx * nz];
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                boolean v = !dilate;
                for (int t = -r; t <= r && v != dilate; t++) {
                    int q = i + t;
                    if ((q >= 0 && q < nx && a[q + j * nx]) == dilate) v = dilate;
                }
                rows[i + j * nx] = v;
            }
        }
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                boolean v = !dilate;
                for (int t = -r; t <= r && v != dilate; t++) {
                    int q = j + t;
                    if ((q >= 0 && q < nz && rows[i + q * nx]) == dilate) v = dilate;
                }
                out[i + j * nx] = v;
            }
        }
        return out;
    }

    /** Rock under the nearest foot, the higher on a tie, so ground beside a raised foot meets it. */
    private int nearestRock(boolean[] G, int[] S, int c, int r) {
        int i = c % nx, j = c / nx, best = Integer.MAX_VALUE, rock = floor - 1;
        for (int b = Math.max(0, j - r); b <= Math.min(nz - 1, j + r); b++) {
            for (int a = Math.max(0, i - r); a <= Math.min(nx - 1, i + r); a++) {
                int q = a + b * nx;
                if (!G[q]) continue;
                int dd = (a - i) * (a - i) + (b - j) * (b - j);
                if (dd < best || (dd == best && S[q] - 1 > rock)) { best = dd; rock = S[q] - 1; }
            }
        }
        return rock;
    }

    /** Raises the island's surface to the rock under nearby soles, fading over a lobed reach so no ring forms. */
    private void lift(Terrain t, boolean[] G, int[] S, Field lobe, Field wave) {
        int n = nx * nz, F = floor;
        int[] off = { 1, -1, nx, -nx };
        int[] ring = new int[n];
        float[] need = new float[n];
        int rn = 0;
        for (int j = 1; j < nz - 1; j++) {
            for (int i = 1; i < nx - 1; i++) {
                int c = i + j * nx;
                if (G[c]) continue;
                // Up to the highest neighbouring foot's base, so beside a raised foot the ground meets its rock.
                int cap = Integer.MIN_VALUE;
                for (int k = 0; k < 4; k++) if (G[c + off[k]]) cap = Math.max(cap, S[c + off[k]] - 1);
                if (cap == Integer.MIN_VALUE) continue;
                int top = topNear(t, x0 + i, z0 + j, F - 1);
                // A drop past the seat band is the island's own edge, and stays one.
                if (top == Integer.MIN_VALUE || top < F - 1 - SEAT_BAND || top >= cap) continue;
                ring[rn] = c;
                need[rn++] = cap - top;
            }
        }
        if (rn == 0) return;
        int w = nx + 2 * LIFT_PAD, h = nz + 2 * LIFT_PAD, lx0 = x0 - LIFT_PAD, lz0 = z0 - LIFT_PAD;
        double[] r = new double[w * h], wv = new double[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                r[i + j * w] = LIFT_R_LO + (LIFT_R_HI - LIFT_R_LO) * (0.5 + 0.5 * lobe.at(lx0 + i, lz0 + j));
                wv[i + j * w] = wave.at(lx0 + i, lz0 + j);
            }
        }
        float[] up = new float[w * h];
        int reachCells = (int) Math.ceil(LIFT_R_HI + LIFT_WAVE_AMP);
        for (int q = 0; q < rn; q++) {
            int ci = ring[q] % nx + LIFT_PAD, cj = ring[q] / nx + LIFT_PAD;
            int rx = x0 + ring[q] % nx, rz = z0 + ring[q] / nx;
            // No chunk past the reach reads this plan, so the fade ends inside it or a chunk edge would cut it straight.
            double safe = Math.min(Math.min(rx - reachX0, reachX1 - rx), Math.min(rz - reachZ0, reachZ1 - rz)) + 0.5;
            if (safe <= 0.0) continue;
            for (int j = Math.max(0, cj - reachCells); j <= Math.min(h - 1, cj + reachCells); j++) {
                for (int i = Math.max(0, ci - reachCells); i <= Math.min(w - 1, ci + reachCells); i++) {
                    int e = i + j * w;
                    double d = Math.hypot(i - ci, j - cj), near = Math.min(1.0, d / LIFT_WAVE_NEAR);
                    // Near the feet the wave may only shorten the distance, so the lift never sinks there into a ring.
                    double push = LIFT_WAVE_AMP * wv[e] * (wv[e] > 0.0 ? near * near * (3.0 - 2.0 * near) : Math.min(1.0, d));
                    // The raw distance still ends the fade at the reach, where a shortened one would run past it.
                    double f = Math.max(Math.max(0.0, d + push) / Math.min(r[e], safe), d / safe);
                    if (f >= 1.0) continue;
                    float v = (float) (need[q] * (1.0 - f * f * f * (f * (f * 6.0 - 15.0) + 10.0)));
                    if (v > up[e]) up[e] = v;
                }
            }
        }
        int gn = 0;
        int[] gi = new int[n], gj = new int[n], gs = new int[n];
        for (int c = 0; c < n; c++) {
            if (!G[c]) continue;
            gi[gn] = c % nx + LIFT_PAD; gj[gn] = c / nx + LIFT_PAD; gs[gn++] = S[c];
        }
        int[] cap = new int[w * h];
        java.util.Arrays.fill(cap, Integer.MIN_VALUE);
        for (int e = 0; e < w * h; e++) {
            if (up[e] <= 0f) continue;
            int i = e % w, j = e / w, pi = i - LIFT_PAD, pj = j - LIFT_PAD;
            // The floor's own columns get the step under them, not a lift.
            if (pi >= 0 && pi < nx && pj >= 0 && pj < nz && G[pi + pj * nx]) { up[e] = 0f; continue; }
            int best = Integer.MAX_VALUE;
            for (int g = 0; g < gn; g++) {
                int di = gi[g] - i, dj = gj[g] - j, dd = di * di + dj * dj;
                if (dd < best || (dd == best && gs[g] - 1 > cap[e])) { best = dd; cap[e] = gs[g] - 1; }
            }
            if (up[e] >= 0.5f) liftColumns++;
            liftMax = Math.max(liftMax, up[e]);
        }
        lift = up;
        liftCap = cap;
    }

    /** The carve would cut the lift and the island beside a raised foot, both under the soles, so the plan keeps them. */
    private void keep(boolean[] G, int[] S, Field lobe) {
        int F = floor, w = nx + 2 * LIFT_PAD, h = nz + 2 * LIFT_PAD;
        boolean[] gp = new boolean[w * h];
        int gn = 0;
        for (int c = 0; c < nx * nz; c++) {
            if (!G[c]) continue;
            gp[(c % nx + LIFT_PAD) + (c / nx + LIFT_PAD) * w] = true;
            gn++;
        }
        int[] k = new int[w * h];
        java.util.Arrays.fill(k, Integer.MIN_VALUE);
        double[] dG = edt(gp, w, h);
        int r = (int) Math.ceil(KEEP_HI);
        for (int e = 0; e < w * h; e++) {
            if (gp[e]) continue;
            if (lift != null && lift[e] > 0f) k[e] = liftCap[e];
            if (gn == 0 || dG[e] > KEEP_HI) continue;
            int i = e % w, j = e / w, best = Integer.MAX_VALUE, cap = Integer.MIN_VALUE;
            for (int b = Math.max(0, j - r); b <= Math.min(h - 1, j + r); b++) {
                for (int a = Math.max(0, i - r); a <= Math.min(w - 1, i + r); a++) {
                    int q = a + b * w;
                    if (!gp[q]) continue;
                    int dd = (a - i) * (a - i) + (b - j) * (b - j);
                    int s = S[(a - LIFT_PAD) + (b - LIFT_PAD) * nx] - 1;
                    if (dd < best || (dd == best && s > cap)) { best = dd; cap = s; }
                }
            }
            if (cap <= F - 1) continue;
            double m = KEEP_LO + (KEEP_HI - KEEP_LO) * (0.5 + 0.5 * lobe.at(x0 - LIFT_PAD + i, z0 - LIFT_PAD + j));
            if (Math.sqrt(best) <= m) k[e] = Math.max(k[e], cap);
        }
        keep = k;
    }

    /** Grown ground's edge: level with it out to a lobe margin, then a course lower every EDGE_RUN blocks, no box outline. */
    private void edge(Terrain t, boolean[] W, Field lobe) {
        int n = nx * nz, F = floor;
        int[] off = { 1, -1, nx, -nx };
        boolean[] has = new boolean[n], src = new boolean[n];
        for (int c = 0; c < n; c++) {
            has[c] = hasTop(c);
            // The lift meets the floor itself, so only the grown apron lays an edge, from its own top.
            src[c] = W[c] && has[c];
        }
        double[] d = edt(src, nx, nz);
        int[] lvl = new int[n];
        double lvlReach = MARGIN_HI + EDGE_REACH + 1;
        int lr = (int) Math.ceil(lvlReach);
        for (int c = 0; c < n; c++) {
            lvl[c] = F - 1;
            if (src[c]) { lvl[c] = Math.min(F, topHi[c]); continue; }
            if (d[c] > lvlReach) continue;
            int i = c % nx, j = c / nx, best = Integer.MAX_VALUE;
            for (int b = Math.max(0, j - lr); b <= Math.min(nz - 1, j + lr); b++) {
                for (int a = Math.max(0, i - lr); a <= Math.min(nx - 1, i + lr); a++) {
                    int q = a + b * nx;
                    if (!src[q]) continue;
                    int dd = (a - i) * (a - i) + (b - j) * (b - j), v = Math.min(F, topHi[q]);
                    if (dd < best || (dd == best && v > lvl[c])) { best = dd; lvl[c] = v; }
                }
            }
        }
        int[] g = new int[n], h = new int[n];
        java.util.Arrays.fill(g, Integer.MIN_VALUE);
        java.util.Arrays.fill(h, Integer.MIN_VALUE);
        boolean[] consider = new boolean[n];
        for (int j = 1; j < nz - 1; j++) {
            for (int i = 1; i < nx - 1; i++) {
                int c = i + j * nx;
                if (src[c]) continue;
                int x = x0 + i, z = z0 + j;
                double m = MARGIN_LO + (MARGIN_HI - MARGIN_LO) * (0.5 + 0.5 * lobe.at(x, z));
                if (d[c] > m + EDGE_REACH) continue;
                int top = has[c] ? topHi[c] : topNear(t, x, z, F - 1);
                if (top == Integer.MIN_VALUE || top > F + SEAT_OVER) continue;
                g[c] = top;
                consider[c] = true;
                if (top >= F - 1 - SEAT_BAND) h[c] = lvl[c] - Math.max(0, (int) Math.ceil((d[c] - m) / EDGE_RUN));
            }
        }
        // Only columns joined to the floor by ground get any, so nothing jumps a gap onto a loose islet.
        boolean[] joined = new boolean[n];
        int[] stack = new int[n];
        int sp = 0;
        for (int c = 0; c < n; c++) {
            if (!src[c]) continue;
            int i = c % nx, j = c / nx;
            if (i == 0 || j == 0 || i == nx - 1 || j == nz - 1) continue;
            for (int k = 0; k < 4; k++) {
                int e = c + off[k];
                if (consider[e] && !joined[e]) { joined[e] = true; stack[sp++] = e; }
            }
        }
        while (sp > 0) {
            int c = stack[--sp];
            for (int k = 0; k < 4; k++) {
                int e = c + off[k];
                if (consider[e] && !joined[e] && Math.abs(g[e] - g[c]) <= STEP) { joined[e] = true; stack[sp++] = e; }
            }
        }
        int[] top = new int[n];
        for (int c = 0; c < n; c++) {
            if (!joined[c]) { consider[c] = false; h[c] = Integer.MIN_VALUE; }
            top[c] = src[c] ? lvl[c] : consider[c] ? g[c] : Integer.MIN_VALUE;
            if (consider[c] && h[c] != Integer.MIN_VALUE && h[c] > g[c] && h[c] - g[c] <= STEP) top[c] = h[c];
        }
        // Never more than a course between neighbours: the low side is lifted, by at most STEP over its own ground.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int j = 1; j < nz - 1; j++) {
                for (int i = 1; i < nx - 1; i++) {
                    int c = i + j * nx, t0 = top[c];
                    if (t0 == Integer.MIN_VALUE || !(src[c] || (consider[c] && t0 > g[c]))) continue;
                    for (int k = 0; k < 4; k++) {
                        int e = c + off[k];
                        if (!consider[e] || top[e] >= t0 - 1 || t0 - 1 - g[e] > STEP || t0 - 1 > lvl[e]) continue;
                        top[e] = t0 - 1;
                        changed = true;
                    }
                }
            }
        }
        boolean[] fill = new boolean[n];
        for (int c = 0; c < n; c++) fill[c] = consider[c] && top[c] > g[c];
        // A cap: no more than a course over grown ground that stays, nor over natural ground beyond the drop it already had.
        int[] cap = new int[n], nat = new int[n];
        java.util.Arrays.fill(cap, Integer.MAX_VALUE);
        java.util.Arrays.fill(nat, UNSEEN);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int j = 1; j < nz - 1; j++) {
            for (int i = 1; i < nx - 1; i++) {
                int c = i + j * nx;
                if (!fill[c]) continue;
                for (int k = 0; k < 4; k++) {
                    int e = c + off[k];
                    if (src[e] || fill[e]) continue;
                    int lim;
                    if (has[e]) {
                        lim = topHi[e] + 1;
                    } else {
                        int tn = consider[e] ? g[e] : naturalTop(t, nat, e);
                        if (tn == Integer.MIN_VALUE) continue;
                        lim = tn + Math.max(1, g[c] - tn);
                    }
                    cap[c] = Math.min(cap[c], lim);
                }
                if (cap[c] != Integer.MAX_VALUE) queue.add(c);
            }
        }
        while (!queue.isEmpty()) {
            int c = queue.poll(), u = cap[c] + 1;
            for (int k = 0; k < 4; k++) {
                int e = c + off[k];
                if (fill[e] && cap[e] > u) { cap[e] = u; queue.add(e); }
            }
        }
        for (int c = 0; c < n; c++) {
            if (!fill[c]) continue;
            int i = c % nx, j = c / nx;
            // The first ring keeps a walkable step off the floor, or the cap would open a face under the wall.
            if (d[c] <= 1.5) cap[c] = Math.max(cap[c], lvl[c] - (int) Math.ceil(d[c]));
            int rim = Math.min(Math.min(i, j), Math.min(nx - 1 - i, nz - 1 - j));
            top[c] = Math.min(top[c], Math.min(cap[c], g[c] + rim - 1));
            fill[c] = top[c] > g[c];
        }
        // past the first ring a partial fill at a drop's foot sinks, and a sunk column still caps its neighbours
        changed = true;
        while (changed) {
            changed = false;
            for (int j = 1; j < nz - 1; j++) {
                for (int i = 1; i < nx - 1; i++) {
                    int c = i + j * nx;
                    if (!fill[c] || d[c] <= 1.5) continue;
                    int lim = top[c];
                    for (int k = 0; k < 4; k++) {
                        int e = c + off[k];
                        if (fill[e] || src[e]) {
                            lim = Math.min(lim, top[e] + 1);
                        } else if (has[e]) {
                            lim = Math.min(lim, topHi[e] + 1);
                        } else {
                            int tn = consider[e] ? g[e] : naturalTop(t, nat, e);
                            if (tn != Integer.MIN_VALUE) lim = Math.min(lim, tn + Math.max(1, g[c] - tn));
                        }
                    }
                    if (lim < top[c]) {
                        top[c] = Math.max(lim, g[c]);
                        changed = true;
                        if (top[c] <= g[c]) fill[c] = false;
                    }
                }
            }
        }
        for (int c = 0; c < n; c++) {
            if (consider[c] && top[c] > g[c]) { rampLo[c] = g[c] + 1; rampHi[c] = top[c]; }
        }
    }

    private static final int UNSEEN = Integer.MIN_VALUE + 1;

    private int naturalTop(Terrain t, int[] cache, int c) {
        if (cache[c] == UNSEEN) cache[c] = topNear(t, x0 + c % nx, z0 + c / nx, floor - 1);
        return cache[c];
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
