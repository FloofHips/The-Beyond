package com.thebeyond.internal.worldgen;

import org.jetbrains.annotations.ApiStatus;

/** Mutable session state for the End. Read-only facade lives at
 *  {@code com.thebeyond.api.worldgen.BeyondTerrainState}. */
@ApiStatus.Internal
public final class BeyondTerrainStateInternal {
    public static volatile boolean active = false;
    public static volatile int dimMinY = 0;
    /** Exclusive — matches {@code LevelHeightAccessor.getMaxBuildHeight}. */
    public static volatile int dimMaxY = 256;

    private BeyondTerrainStateInternal() {}

    public static void markActive() { active = true; }
    public static void setDimMinY(int minY) { dimMinY = minY; }
    public static void setDimMaxY(int maxY) { dimMaxY = maxY; }
    public static void setDimBounds(int minY, int maxY) {
        dimMinY = minY;
        dimMaxY = maxY;
    }
    public static void reset() {
        active = false;
        dimMinY = 0;
        dimMaxY = 256;
    }
}
