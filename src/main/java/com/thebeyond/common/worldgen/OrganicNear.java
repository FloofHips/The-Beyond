package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Squared distance from each bog block to the nearest block whose rock differs, read in rings of x planes. */
final class OrganicNear {
    private static final byte NONE = Byte.MAX_VALUE;
    private final OrganicNetwork.Grid g;
    private final boolean rockToo;
    private final int R, lv, segs, slots;
    // a ring of x planes: per row and 64-block segment, each kind's masks within k along z and squared distance to the other
    private final long[] mask;
    private final byte[] d2;
    private final boolean[] masked, measured;
    private final int[] slotX;
    // the (dx, dy) rows nearby, and the steps (row, k) by dx^2 + dy^2 + k^2 up to R^2
    private final int[] pdx, pdy, base, sp, sk, ss;

    OrganicNear(OrganicNetwork.Grid g, double reach, boolean rockToo) {
        this.g = g;
        this.rockToo = rockToo;
        R = (int) Math.ceil(reach);
        lv = R + 1;
        segs = (g.nz + 63) >> 6;
        int ring = 1;
        // a slab of 4 planes reads R planes on either side
        while (ring < 2 * R + 4) ring <<= 1;
        slots = ring;
        mask = new long[slots * g.ny * segs * 2 * lv];
        d2 = new byte[slots * g.ny * segs * 64];
        masked = new boolean[slots * g.ny * segs];
        measured = new boolean[masked.length];
        slotX = new int[slots];
        Arrays.fill(slotX, -1);
        List<int[]> rows = new ArrayList<>(), steps = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                int q = dx * dx + dy * dy;
                if (q > R * R) continue;
                for (int k = 0; q + k * k <= R * R; k++) if (q + k * k > 0) steps.add(new int[]{rows.size(), k, q + k * k});
                rows.add(new int[]{dx, dy});
            }
        }
        steps.sort(Comparator.comparingInt(a -> a[2]));
        pdx = new int[rows.size()];
        pdy = new int[rows.size()];
        base = new int[rows.size()];
        for (int i = 0; i < pdx.length; i++) {
            pdx[i] = rows.get(i)[0];
            pdy[i] = rows.get(i)[1];
        }
        sp = new int[steps.size()];
        sk = new int[steps.size()];
        ss = new int[steps.size()];
        for (int i = 0; i < ss.length; i++) {
            sp[i] = steps.get(i)[0];
            sk[i] = steps.get(i)[1];
            ss[i] = steps.get(i)[2];
        }
    }

    /** Squared distance from a bog block to the nearest block whose rock differs from its own, Integer.MAX_VALUE beyond R. */
    int sq(int x, int y, int z) {
        int r = (slot(x) * g.ny + y) * segs + (z >> 6);
        if (!measured[r]) {
            measure(x, y, z >> 6, r << 6);
            measured[r] = true;
        }
        int v = d2[r << 6 | z & 63];
        return v == NONE ? Integer.MAX_VALUE : v;
    }

    private int slot(int x) {
        int s = x & (slots - 1);
        if (slotX[s] != x) {
            slotX[s] = x;
            Arrays.fill(masked, s * g.ny * segs, (s + 1) * g.ny * segs, false);
            Arrays.fill(measured, s * g.ny * segs, (s + 1) * g.ny * segs, false);
        }
        return s;
    }

    private void measure(int x, int y, int seg, int at) {
        Arrays.fill(d2, at, at + 64, NONE);
        for (int i = 0; i < pdx.length; i++) {
            int qx = x + pdx[i], qy = y + pdy[i];
            base[i] = qx < 0 || qy < 0 || qx >= g.nx || qy >= g.ny ? -1 : levels(qx, qy, seg);
        }
        int row = (x * g.ny + y) * g.nz, z0 = seg << 6, n = Math.min(64, g.nz - z0);
        long bog = g.bog64(row + z0) & (n < 64 ? (1L << n) - 1 : -1L);
        rings(at, bog & window(row, z0, false), 0);
        if (rockToo) rings(at, bog & window(row, z0, true), lv);
    }

    // Open blocks read the rock masks, rock blocks the open ones, ring by ring until every wanted block found one.
    private void rings(int at, long want, int kind) {
        long seen = 0;
        for (int i = 0; i < ss.length && seen != want; ) {
            int s = ss[i];
            long hit = 0;
            for (; i < ss.length && ss[i] == s; i++) {
                int m = base[sp[i]];
                if (m >= 0) hit |= mask[m + kind + sk[i]];
            }
            long found = hit & want & ~seen;
            seen |= found;
            for (; found != 0; found &= found - 1) d2[at + Long.numberOfTrailingZeros(found)] = (byte) s;
        }
    }

    private int levels(int x, int y, int seg) {
        int r = (slot(x) * g.ny + y) * segs + seg, m = r * 2 * lv;
        if (!masked[r]) {
            int row = (x * g.ny + y) * g.nz, z0 = seg << 6;
            mask[m] = window(row, z0, true);
            mask[m + lv] = window(row, z0, false);
            for (int k = 1; k <= R; k++) {
                mask[m + k] = mask[m + k - 1] | window(row, z0 + k, true) | window(row, z0 - k, true);
                mask[m + lv + k] = mask[m + lv + k - 1] | window(row, z0 + k, false) | window(row, z0 - k, false);
            }
            masked[r] = true;
        }
        return m;
    }

    // The 64 blocks of the row from z on that are rock (or open): blocks past either end of the row are neither.
    private long window(int row, int z, boolean rock) {
        int lo = Math.max(z, 0), hi = Math.min(z + 64, g.nz), n = hi - lo;
        if (n <= 0) return 0;
        long bits = g.solid64(row + lo);
        if (!rock) bits = ~bits;
        if (n < 64) bits &= (1L << n) - 1;
        return bits << (lo - z);
    }
}
