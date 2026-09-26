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
    public static volatile boolean loggedFeatureVeto = false;
    public static volatile boolean loggedCrystalAllow = false;
    public static volatile boolean loggedGellidVeto = false;
    public static volatile boolean loggedDebris = false;
    public static final java.util.concurrent.atomic.AtomicInteger grainWarts = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger grainPits = new java.util.concurrent.atomic.AtomicInteger();
    public static final Set<String> loggedArbitration = ConcurrentHashMap.newKeySet();
    public static final java.util.concurrent.atomic.AtomicInteger debrisDropped = new java.util.concurrent.atomic.AtomicInteger();
    public static final java.util.concurrent.atomic.AtomicInteger debrisAttached = new java.util.concurrent.atomic.AtomicInteger();
    public static final Set<String> loggedSinks = ConcurrentHashMap.newKeySet();
    public static final java.util.concurrent.atomic.AtomicInteger kneeLaid = new java.util.concurrent.atomic.AtomicInteger();
    public static final Set<Long> loggedKnees = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedRestartLayer = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedBeardKept = ConcurrentHashMap.newKeySet();
    public static volatile boolean loggedKneeError = false;
    public static volatile boolean loggedDt3Fallback = false;
    public static volatile boolean loggedTemplateGateError = false;
    public static volatile boolean loggedGravitySuppressed = false;
    public static volatile int placementProbesLogged = 0;
    public static volatile boolean loggedMaskTemplateError = false;
    public static volatile boolean loggedEndCitySeatError = false;
    public static final Set<Long> loggedEndCitySeat = ConcurrentHashMap.newKeySet();
    public static final Set<Long> loggedEndCityTier = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedCarveVeto = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedCarveLedger = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedPools = ConcurrentHashMap.newKeySet();
    public static final Set<String> loggedMaskKeys = ConcurrentHashMap.newKeySet();
    /** Kept apart from loggedMaskKeys, whose per-chunk keys hit its cap before the last pedestal city. */
    public static final Set<String> loggedCityGround = ConcurrentHashMap.newKeySet();
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

    private static final java.util.concurrent.ConcurrentMap<String, Set<Long>> DECISIONS = new ConcurrentHashMap<>();

    public static void countDecision(String id, String cls, String decision, long chunk) {
        int cut = decision.indexOf(' ');
        String code = cut < 0 ? decision : decision.substring(0, cut);
        DECISIONS.computeIfAbsent(id + " class=" + cls + " " + code, k -> ConcurrentHashMap.newKeySet()).add(chunk);
    }

    static void dumpDecisions() {
        new java.util.TreeMap<>(DECISIONS).forEach((k, v) ->
                com.thebeyond.TheBeyond.LOGGER.info("[Beyond] structure decisions {} starts={}", k, v.size()));
        DECISIONS.clear();
    }

    private static final java.util.concurrent.atomic.AtomicLong CARVE_CHUNKS = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.LongAdder CARVE_NANOS = new java.util.concurrent.atomic.LongAdder();
    static final java.util.concurrent.atomic.LongAdder BEGIN_NANOS = new java.util.concurrent.atomic.LongAdder();
    private static final int CARVE_PERF_FIRST = 64, CARVE_PERF_EVERY = 4096;

    static void carveChunkDone(long nanos) {
        CARVE_NANOS.add(nanos);
        long n = CARVE_CHUNKS.incrementAndGet();
        // Doubling up to the steady interval, so a long session does not fill the log.
        boolean due = n < CARVE_PERF_EVERY ? n >= CARVE_PERF_FIRST && (n & (n - 1)) == 0 : n % CARVE_PERF_EVERY == 0;
        if (!due) return;
        double total = CARVE_NANOS.sum() / 1e6 / n, begin = BEGIN_NANOS.sum() / 1e6 / n;
        com.thebeyond.TheBeyond.LOGGER.info("[Beyond] carve-perf {} chunks with a carve mask: {} ms each, {} ms of it opening columns",
                n, String.format(java.util.Locale.ROOT, "%.2f", total), String.format(java.util.Locale.ROOT, "%.2f", begin));
    }

    public static void reset() {
        dumpDecisions();
        CARVE_CHUNKS.set(0);
        CARVE_NANOS.reset();
        BEGIN_NANOS.reset();
        loggedSilhouetteClear = false;
        loggedAccept = false;
        loggedReject = false;
        loggedError = false;
        loggedFeatureVeto = false;
        loggedCrystalAllow = false;
        loggedGellidVeto = false;
        loggedTemplateGateError = false;
        loggedMaskTemplateError = false;
        loggedEndCitySeatError = false;
        loggedDebris = false;
        debrisDropped.set(0);
        debrisAttached.set(0);
        grainWarts.set(0);
        grainPits.set(0);
        kneeLaid.set(0);
        loggedKneeError = false;
        loggedDt3Fallback = false;
        BeyondStructureCarver.the_beyond$resetApronCounters();
        BeyondStructureCarver.the_beyond$resetCarveCounters();
        loggedArbitration.clear();
        loggedEndCitySeat.clear();
        loggedEndCityTier.clear();
        loggedCarveVeto.clear();
        loggedCarveLedger.clear();
        loggedPools.clear();
        loggedMaskKeys.clear();
        loggedCityGround.clear();
        loggedAutoProfile.clear();
        loggedDirtCover.clear();
        loggedLeaveAlone.clear();
        loggedCavityReject.clear();
        loggedMaskPieces.clear();
        loggedFarMaskKeys.clear();
        loggedFarBuild.clear();
        loggedDistributedY.clear();
        loggedFootingSuppressed.clear();
        loggedSinks.clear();
        loggedKnees.clear();
        loggedRestartLayer.clear();
        loggedBeardKept.clear();
    }
}
