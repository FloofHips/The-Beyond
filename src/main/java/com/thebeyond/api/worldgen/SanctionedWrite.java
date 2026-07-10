package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/**
 * Thread-local flag that lets the current thread's writes past the island carve-protection veto
 * in {@code WorldGenRegion.setBlock}; unsanctioned (foreign) writes are vetoed by default.
 */
@ApiStatus.Experimental
public final class SanctionedWrite {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private SanctionedWrite() {}

    /** Begin a sanctioned-write scope. Always pair with {@link #exit()} in a finally block. */
    public static void enter() { DEPTH.get()[0]++; }

    /** End a sanctioned-write scope opened by {@link #enter()}. */
    public static void exit() { int[] d = DEPTH.get(); if (d[0] > 0) d[0]--; }

    /** {@code true} while inside a sanctioned-write scope on this thread. */
    public static boolean isSanctioned() { return DEPTH.get()[0] > 0; }
}
