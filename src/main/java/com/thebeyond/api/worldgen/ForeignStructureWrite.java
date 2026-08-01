package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ForeignStructureWrite {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private ForeignStructureWrite() {}

    public static void enter() { DEPTH.get()[0]++; }

    public static void exit() { int[] d = DEPTH.get(); if (d[0] > 0) d[0]--; }

    public static boolean isActive() { return DEPTH.get()[0] > 0; }
}
