package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** Stops biome features from overwriting a foreign structure placed just before them in the same chunk
 *  step. Protected volume is the generator's own carve-clear predicate, so it never disagrees with the carve. */
@ApiStatus.Internal
public final class FeatureGuard {

    /** The region a feature write must avoid — supplied by the generator so it equals the carve's clear. */
    @FunctionalInterface
    public interface ProtectedVolume {
        boolean contains(int x, int y, int z);
    }

    private static final ThreadLocal<ProtectedVolume> VOLUME = new ThreadLocal<>();
    /** A carve structure's solid footprint — bars the gellid-void pool from intruding into it. */
    private static final ThreadLocal<ProtectedVolume> STRUCTURE_VOLUME = new ThreadLocal<>();
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private FeatureGuard() {}

    /** Arm the guard with the protected volume to enforce for the current chunk decoration. */
    public static void setVolume(ProtectedVolume volume) { VOLUME.set(volume); }

    public static void setStructureVolume(ProtectedVolume volume) { STRUCTURE_VOLUME.set(volume); }

    /** Disarm both volumes (call in a finally after the chunk decoration). */
    public static void clearVolume() { VOLUME.remove(); STRUCTURE_VOLUME.remove(); }

    /** Begin a structure's own placement scope (its writes are allowed inside the protected volume). */
    public static void enterStructure() { DEPTH.get()[0]++; }

    public static void exitStructure() { int[] d = DEPTH.get(); if (d[0] > 0) d[0]--; }

    /** {@code true} if a FEATURE write at (x,y,z) should be vetoed — not inside a structure's own
     *  placement and the position lies in the protected volume. */
    public static boolean blocksFeatureAt(int x, int y, int z) {
        if (DEPTH.get()[0] > 0) return false;          // a structure is building itself → allow
        ProtectedVolume v = VOLUME.get();
        return v != null && v.contains(x, y, z);
    }

    /** {@code true} if (x,y,z) is inside a carve structure's footprint (gellid-void barred here); a structure's own
     *  placement (DEPTH&gt;0) bypasses. */
    public static boolean insideStructureVolume(int x, int y, int z) {
        if (DEPTH.get()[0] > 0) return false;
        ProtectedVolume v = STRUCTURE_VOLUME.get();
        return v != null && v.contains(x, y, z);
    }

    /** True while the guard is armed for the current chunk decoration (a carve structure is near). */
    public static boolean isArmed() { return VOLUME.get() != null; }

    public static boolean inStructure() { return DEPTH.get()[0] > 0; }
}
