package com.thebeyond.common.awareness;

import net.minecraft.resources.ResourceLocation;

/** Core awareness keys. ResourceLocations (not an enum) so addons can declare their own. */
public final class BeyondAwarenessKeys {
    private BeyondAwarenessKeys() {}

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("the_beyond", path);
    }

    public static final ResourceLocation FARLANDS_DISCOVERY = rl("farlands_discovery");

    public static final ResourceLocation WALL_PROXIMITY = rl("wall_proximity");

    public static final ResourceLocation BEYOND_ACCESS = rl("beyond_access");
}
