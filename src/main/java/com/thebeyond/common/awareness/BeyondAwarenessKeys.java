package com.thebeyond.common.awareness;

import net.minecraft.resources.ResourceLocation;

/** Core awareness keys. ResourceLocations (not an enum) so addons can declare their own. */
public final class BeyondAwarenessKeys {
    private BeyondAwarenessKeys() {}

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("the_beyond", path);
    }

    /** Granted on first entry to its discovery region. */
    public static final ResourceLocation FARLANDS_DISCOVERY = rl("farlands_discovery");

    /** Granted when the player nears its region boundary. */
    public static final ResourceLocation WALL_PROXIMITY = rl("wall_proximity");

    /** Gates Beyond-dimension content. */
    public static final ResourceLocation BEYOND_ACCESS = rl("beyond_access");
}
