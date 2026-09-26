package com.thebeyond.api.worldgen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ForeignStructureWrite {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    private ForeignStructureWrite() {}

    /** One foreign structure's placement in one chunk, tracking its own writes so its later air can reopen them. */
    public static final class Scope {
        public final String structureId;
        private final LongOpenHashSet written = new LongOpenHashSet();
        public int selfOverwrite;
        public int terrainVeto;
        public int featureVeto;

        Scope(String structureId) { this.structureId = structureId; }

        public void recordWrite(long packedPos) { written.add(packedPos); }

        public boolean wroteHere(long packedPos) { return written.contains(packedPos); }
    }

    public static void enter() { DEPTH.get()[0]++; }

    public static void exit() { int[] d = DEPTH.get(); if (d[0] > 0) d[0]--; }

    public static boolean isActive() { return DEPTH.get()[0] > 0; }

    public static Scope openScope(String structureId) {
        Scope prev = SCOPE.get();
        SCOPE.set(new Scope(structureId));
        return prev;
    }

    public static void closeScope(@Nullable Scope restore) {
        if (restore == null) SCOPE.remove(); else SCOPE.set(restore);
    }

    @Nullable
    public static Scope currentScope() { return SCOPE.get(); }
}
