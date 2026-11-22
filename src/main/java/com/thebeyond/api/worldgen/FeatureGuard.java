package com.thebeyond.api.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** Keeps biome features off a foreign structure placed just before them, using the carve's own clear test. */
@ApiStatus.Internal
public final class FeatureGuard {
    /** Logs from the guard, its lake fill and its floating sweep, off since they are frequent and slow a dev console. */
    public static boolean debugLogs = false;

    /** The region a feature write must avoid, supplied by the generator so it equals the carve's clear. */
    @FunctionalInterface
    public interface ProtectedVolume {
        boolean contains(int x, int y, int z);
    }

    /** One per thread, so a setBlock reads every field with a single ThreadLocal lookup. */
    private static final class State {
        ProtectedVolume volume;
        ProtectedVolume structureVolume;
        ProtectedVolume pieceVolume;
        ProtectedVolume pieceClearance;
        int depth;
        int featureDepth;
        /** 0 undecided, 1 allowed, 2 blocked. */
        int decision;
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private FeatureGuard() {}

    /** Arm the guard with the protected volume to enforce for the current chunk decoration. */
    public static void setVolume(ProtectedVolume volume) { STATE.get().volume = volume; }

    public static void setStructureVolume(ProtectedVolume volume) { STATE.get().structureVolume = volume; }

    public static void setPieceVolume(ProtectedVolume volume) { STATE.get().pieceVolume = volume; }

    public static void setPieceClearance(ProtectedVolume volume) { STATE.get().pieceClearance = volume; }

    public static void clearVolume() {
        State s = STATE.get();
        s.volume = null; s.structureVolume = null; s.pieceVolume = null; s.pieceClearance = null;
    }

    public static boolean insidePieceClearance(int x, int y, int z) {
        ProtectedVolume v = STATE.get().pieceClearance;
        return v != null && v.contains(x, y, z);
    }

    public static boolean insidePieceVolume(int x, int y, int z) {
        State s = STATE.get();
        if (s.depth > 0) return false;
        ProtectedVolume v = s.pieceVolume;
        return v != null && v.contains(x, y, z);
    }

    /** Begin a structure's own placement scope (its writes are allowed inside the protected volume). */
    public static void enterStructure() { STATE.get().depth++; }

    public static void exitStructure() { State s = STATE.get(); if (s.depth > 0) s.depth--; }

    public static void enterFeature() {
        State s = STATE.get();
        if (s.featureDepth++ == 0) s.decision = 0;
    }

    public static void exitFeature() {
        State s = STATE.get();
        if (s.featureDepth > 0 && --s.featureDepth == 0) s.decision = 0;
    }

    /** Decided once per feature on its first block, so no feature is placed by halves. */
    public static boolean blocksFeatureAt(int x, int y, int z) {
        State s = STATE.get();
        if (s.depth > 0) return false;
        ProtectedVolume v = s.volume;
        if (s.featureDepth == 0) return v != null && v.contains(x, y, z);
        if (s.decision == 0) s.decision = (v != null && v.contains(x, y, z)) ? 2 : 1;
        return s.decision == 2;
    }

    /** True inside a carve structure's footprint, except during the structure's own placement. */
    public static boolean insideStructureVolume(int x, int y, int z) {
        State s = STATE.get();
        if (s.depth > 0) return false;
        ProtectedVolume v = s.structureVolume;
        return v != null && v.contains(x, y, z);
    }

    /** True while the guard is armed for the current chunk decoration (a carve structure is near). */
    public static boolean isArmed() { return STATE.get().volume != null; }

    public static boolean inStructure() { return STATE.get().depth > 0; }

    /** isArmed() and not inStructure() in one lookup, for the per-write checks. */
    public static boolean armedOutsideStructure() {
        State s = STATE.get();
        return s.volume != null && s.depth <= 0;
    }
}
