package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** Thread-local pass past the island carve veto in WorldGenRegion.setBlock, unsanctioned writes are vetoed. */
@ApiStatus.Experimental
public final class SanctionedWrite {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private SanctionedWrite() {}

    /** Always pair with {@link #exit()} in a finally block. */
    public static void enter() { DEPTH.get()[0]++; }

    public static void exit() { int[] d = DEPTH.get(); if (d[0] > 0) d[0]--; }

    public static boolean isSanctioned() { return DEPTH.get()[0] > 0; }
}
