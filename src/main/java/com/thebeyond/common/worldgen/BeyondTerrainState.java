package com.thebeyond.common.worldgen;

/**
 * Tracks whether Beyond's End biome source is the active provider this session.
 * Set from {@link BeyondEndBiomeSource}'s constructor, reset on server stop.
 * Used to gate compat mixins and fallback injection paths ("soup mode").
 */
public final class BeyondTerrainState {

    private static volatile boolean active = false;

    private BeyondTerrainState() {}

    /** Called from {@link BeyondEndBiomeSource}'s constructor during codec decode. */
    public static void markActive() {
        active = true;
    }

    /** Called from a {@code ServerStoppedEvent} listener so the next session starts clean. */
    public static void reset() {
        active = false;
    }

    /** {@code true} when Beyond's biome source decoded successfully this session. */
    public static boolean isActive() {
        return active;
    }
}
