package com.thebeyond.common.worldgen;

/**
 * Session-scoped End-worldgen state. Reset on {@code ServerStoppedEvent}.
 *
 * <ul>
 *   <li>{@link #isActive()} — Beyond's biome source decoded this session
 *       (gates compat mixins and soup-mode fallbacks).</li>
 *   <li>{@link #getDimMinY()} / {@link #getDimMaxY()} — End dim build bounds.
 *       Beyond-só: [0, 256). Combo with {@code beyond_enderscape_bounds}: [-64, 320).
 *       Consumed by {@link BeyondEndBiomeSource} (anchor bottom_biome band) and by
 *       {@link BeyondEndChunkGenerator#getWorldHeight()} (scale worldHeight so the
 *       terrain loop, gradient and heightmap scan match the actual dim range).</li>
 * </ul>
 */
public final class BeyondTerrainState {

    private static volatile boolean active = false;
    private static volatile int dimMinY = 0;
    /**
     * Dim max build height (exclusive — same semantics as {@code LevelHeightAccessor.getMaxBuildHeight}).
     * Defaults to 256 so code reading this before the first chunk-gen callback still sees
     * a sane Beyond-só value.
     */
    private static volatile int dimMaxY = 256;

    private BeyondTerrainState() {}

    public static void markActive() { active = true; }
    public static boolean isActive() { return active; }

    public static void setDimMinY(int minY) { dimMinY = minY; }
    public static int getDimMinY() { return dimMinY; }

    public static void setDimMaxY(int maxY) { dimMaxY = maxY; }
    public static int getDimMaxY() { return dimMaxY; }

    /**
     * Convenience setter — publishes both bounds in one call so every chunk-gen entry
     * point that grabs {@code level.getMinBuildHeight()} / {@code level.getMaxBuildHeight()}
     * can pass them through together.
     */
    public static void setDimBounds(int minY, int maxY) {
        dimMinY = minY;
        dimMaxY = maxY;
    }

    /** Called from {@code ServerStoppedEvent} — next session re-detects from fresh. */
    public static void reset() {
        active = false;
        dimMinY = 0;
        dimMaxY = 256;
    }
}
