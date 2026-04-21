package com.thebeyond.common.knowledge;

import net.minecraft.resources.ResourceLocation;

/** Core knowledge keys. ResourceLocations (not an enum) so addons can declare their own. */
public final class BeyondKnowledgeKeys {
    private BeyondKnowledgeKeys() {}

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("the_beyond", path);
    }

    /** Granted on first entry to a biome in {@code #the_beyond:region/farlands}. */
    public static final ResourceLocation FARLANDS_DISCOVERY = rl("farlands_discovery");

    /** Granted when the player gets within visual range of the north wall (z ≈ 10000). */
    public static final ResourceLocation WALL_PROXIMITY = rl("wall_proximity");

    /** Gates Beyond-dimension content. */
    public static final ResourceLocation BEYOND_ACCESS = rl("beyond_access");
}
