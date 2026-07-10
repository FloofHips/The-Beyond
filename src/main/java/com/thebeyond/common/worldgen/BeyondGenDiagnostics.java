package com.thebeyond.common.worldgen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One-shot flags gating the worldgen debug logs, held here (not as mixin statics) so {@link #reset()} can
 *  re-arm them on server stop instead of firing once ever in a singleplayer session. Purely diagnostic. */
public final class BeyondGenDiagnostics {
    private BeyondGenDiagnostics() {}

    public static volatile boolean loggedSilhouetteClear = false;
    public static volatile boolean loggedAccept = false;
    public static volatile boolean loggedReject = false;
    public static volatile boolean loggedError = false;
    public static final Set<String> loggedPools = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedMaskKeys = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedAutoProfile = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedDirtCover = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedLeaveAlone = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedCavityReject = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedMaskPieces = ConcurrentHashMap.newKeySet();
    /** Carve starts whose mask reached a chunk only via the far maskCache pass (>8 chunks out, no STRUCTURE_REFERENCES). */
    public static final Set<Integer> loggedFarMaskKeys = ConcurrentHashMap.newKeySet();
    /** Carve starts that placed blocks in a chunk which doesn't reference them (past vanilla's ±8 decoration window). */
    public static final Set<Integer> loggedFarBuild = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedDistributedY = ConcurrentHashMap.newKeySet();
    /** DISTRIBUTED starts whose footing + lip was suppressed because the instance is surface-projected (topmost, not re-anchored). */
    public static final Set<Integer> loggedFootingSuppressed = ConcurrentHashMap.newKeySet();

    public static void reset() {
        loggedSilhouetteClear = false;
        loggedAccept = false;
        loggedReject = false;
        loggedError = false;
        loggedPools.clear();
        loggedMaskKeys.clear();
        loggedAutoProfile.clear();
        loggedDirtCover.clear();
        loggedLeaveAlone.clear();
        loggedCavityReject.clear();
        loggedMaskPieces.clear();
        loggedFarMaskKeys.clear();
        loggedFarBuild.clear();
        loggedDistributedY.clear();
        loggedFootingSuppressed.clear();
    }
}
