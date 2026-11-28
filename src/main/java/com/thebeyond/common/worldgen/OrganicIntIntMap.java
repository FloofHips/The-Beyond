package com.thebeyond.common.worldgen;

import java.util.Arrays;

/** An open-addressing int to int map for the network's grid indices. */
final class OrganicIntIntMap {
    private int[] keys, vals;
    private int mask, size;

    OrganicIntIntMap(int cap) {
        int c = Integer.highestOneBit(Math.max(16, cap) - 1) << 1;
        keys = new int[c];
        vals = new int[c];
        Arrays.fill(keys, -1);
        mask = c - 1;
    }

    private static int hash(int k) {
        int h = k * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    int get(int k) {
        int h = hash(k) & mask;
        while (true) {
            int kk = keys[h];
            if (kk == k) return vals[h];
            if (kk == -1) return -1;
            h = (h + 1) & mask;
        }
    }

    void put(int k, int v) {
        if ((size + 1) * 2 > keys.length) grow();
        int h = hash(k) & mask;
        while (true) {
            int kk = keys[h];
            if (kk == k) {
                vals[h] = v;
                return;
            }
            if (kk == -1) {
                keys[h] = k;
                vals[h] = v;
                size++;
                return;
            }
            h = (h + 1) & mask;
        }
    }

    private void grow() {
        int[] ok = keys, ov = vals;
        keys = new int[ok.length * 2];
        vals = new int[ok.length * 2];
        Arrays.fill(keys, -1);
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) {
            if (ok[i] != -1) put(ok[i], ov[i]);
        }
    }
}
