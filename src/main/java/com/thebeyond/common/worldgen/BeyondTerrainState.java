package com.thebeyond.common.worldgen;

/** Server-session-scoped End-worldgen state. {@link #isActive()} gates compat mixins;
 *  {@link #getDimMinY()} / {@link #getDimMaxY()} expose the active dim's build bounds.
 *  Reset on {@code ServerStoppedEvent}. */
public final class BeyondTerrainState {

    private static volatile boolean active = false;
    private static volatile int dimMinY = 0;
    /** Exclusive — matches {@code LevelHeightAccessor.getMaxBuildHeight}. */
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
