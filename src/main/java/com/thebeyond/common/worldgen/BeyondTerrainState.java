package com.thebeyond.common.worldgen;

/**
 * Server-session-scoped End-worldgen state; reset on {@code ServerStoppedEvent}.
 * {@link #isActive()} reports whether Beyond's biome source decoded for the
 * current server (gates compat mixins and soup-mode fallbacks).
 * {@link #getDimMinY()} / {@link #getDimMaxY()} expose the End dim build bounds
 * (Beyond-only: [0, 256); Enderscape combo: [-64, 320)) and are consumed by
 * {@link BeyondEndBiomeSource} to anchor the bottom_biome band and by
 * {@link BeyondEndChunkGenerator#getWorldHeight()} to scale worldHeight so the
 * terrain loop, gradient and heightmap scan match the actual dim range.
 */
public final class BeyondTerrainState {

    private static volatile boolean active = false;
    private static volatile int dimMinY = 0;
    /**
     * Dim max build height, exclusive (same semantics as {@code LevelHeightAccessor.getMaxBuildHeight}).
     * Defaults to 256 so callers reading this before the first chunk-gen callback still see
     * a sane Beyond-only value.
     */
    private static volatile int dimMaxY = 256;

    private BeyondTerrainState() {}

    public static void markActive() { active = true; }
    public static boolean isActive() { return active; }

    public static void setDimMinY(int minY) { dimMinY = minY; }
    public static int getDimMinY() { return dimMinY; }

    public static void setDimMaxY(int maxY) { dimMaxY = maxY; }
    public static int getDimMaxY() { return dimMaxY; }

    /** Publishes both bounds in one call; used by every chunk-gen entry point that reads them from {@code level}. */
    public static void setDimBounds(int minY, int maxY) {
        dimMinY = minY;
        dimMaxY = maxY;
    }

    /** Called from {@code ServerStoppedEvent}; next world load re-detects from fresh. */
    public static void reset() {
        active = false;
        dimMinY = 0;
        dimMaxY = 256;
    }
}
