package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** Keeps biome features off a foreign structure placed just before them, using the carve's own clear test. */
@ApiStatus.Internal
public final class FeatureGuard {

    /** The region a feature write must avoid, supplied by the generator so it equals the carve's clear. */
    @FunctionalInterface
    public interface ProtectedVolume {
        boolean contains(int x, int y, int z);
    }

    private static final ThreadLocal<ProtectedVolume> VOLUME = new ThreadLocal<>();
    /** A carve structure's solid footprint, which bars the gellid-void pool. */
    private static final ThreadLocal<ProtectedVolume> STRUCTURE_VOLUME = new ThreadLocal<>();
    private static final ThreadLocal<ProtectedVolume> PIECE_VOLUME = new ThreadLocal<>();
    /** Piece boxes, the ground under them and a shore around them, kept free of gellid lakes. */
    private static final ThreadLocal<ProtectedVolume> PIECE_CLEARANCE = new ThreadLocal<>();
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    /** depth, decision (0 undecided, 1 allowed, 2 blocked). */
    private static final ThreadLocal<int[]> FEATURE = ThreadLocal.withInitial(() -> new int[2]);

    private FeatureGuard() {}

    /** Arm the guard with the protected volume to enforce for the current chunk decoration. */
    public static void setVolume(ProtectedVolume volume) { VOLUME.set(volume); }

    public static void setStructureVolume(ProtectedVolume volume) { STRUCTURE_VOLUME.set(volume); }

    public static void setPieceVolume(ProtectedVolume volume) { PIECE_VOLUME.set(volume); }

    public static void setPieceClearance(ProtectedVolume volume) { PIECE_CLEARANCE.set(volume); }

    public static void clearVolume() {
        VOLUME.remove(); STRUCTURE_VOLUME.remove(); PIECE_VOLUME.remove(); PIECE_CLEARANCE.remove();
    }

    public static boolean insidePieceClearance(int x, int y, int z) {
        ProtectedVolume v = PIECE_CLEARANCE.get();
        return v != null && v.contains(x, y, z);
    }

    public static boolean insidePieceVolume(int x, int y, int z) {
        if (DEPTH.get()[0] > 0) return false;
        ProtectedVolume v = PIECE_VOLUME.get();
        return v != null && v.contains(x, y, z);
    }

    /** Begin a structure's own placement scope (its writes are allowed inside the protected volume). */
    public static void enterStructure() { DEPTH.get()[0]++; }

    public static void exitStructure() { int[] d = DEPTH.get(); if (d[0] > 0) d[0]--; }

    public static void enterFeature() {
        int[] f = FEATURE.get();
        if (f[0]++ == 0) f[1] = 0;
    }

    public static void exitFeature() {
        int[] f = FEATURE.get();
        if (f[0] > 0 && --f[0] == 0) f[1] = 0;
    }

    /** Decided once per feature on its first block, so no feature is placed by halves. */
    public static boolean blocksFeatureAt(int x, int y, int z) {
        if (DEPTH.get()[0] > 0) return false;          // a structure is building itself → allow
        ProtectedVolume v = VOLUME.get();
        int[] f = FEATURE.get();
        if (f[0] == 0) return v != null && v.contains(x, y, z);
        if (f[1] == 0) f[1] = (v != null && v.contains(x, y, z)) ? 2 : 1;
        return f[1] == 2;
    }

    /** True inside a carve structure's footprint, except during the structure's own placement. */
    public static boolean insideStructureVolume(int x, int y, int z) {
        if (DEPTH.get()[0] > 0) return false;
        ProtectedVolume v = STRUCTURE_VOLUME.get();
        return v != null && v.contains(x, y, z);
    }

    /** True while the guard is armed for the current chunk decoration (a carve structure is near). */
    public static boolean isArmed() { return VOLUME.get() != null; }

    public static boolean inStructure() { return DEPTH.get()[0] > 0; }
}
