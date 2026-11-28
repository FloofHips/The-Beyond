package com.thebeyond.common.worldgen;

import java.util.Arrays;

/** A binary min-heap of int items keyed by double, for the network's shortest paths. */
final class OrganicHeap {
    double[] key = new double[1024];
    int[] val = new int[1024];
    int size;

    void push(double k, int v) {
        if (size == key.length) {
            key = Arrays.copyOf(key, size * 2);
            val = Arrays.copyOf(val, size * 2);
        }
        int i = size++;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (key[p] <= k) break;
            key[i] = key[p];
            val[i] = val[p];
            i = p;
        }
        key[i] = k;
        val[i] = v;
    }

    double topKey() {
        return key[0];
    }

    int pop() {
        int top = val[0];
        double k = key[--size];
        int v = val[size];
        int i = 0;
        while (true) {
            int c = 2 * i + 1;
            if (c >= size) break;
            if (c + 1 < size && key[c + 1] < key[c]) c++;
            if (key[c] >= k) break;
            key[i] = key[c];
            val[i] = val[c];
            i = c;
        }
        key[i] = k;
        val[i] = v;
        return top;
    }
}
