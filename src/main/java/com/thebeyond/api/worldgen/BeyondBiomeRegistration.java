package com.thebeyond.api.worldgen;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Biomes kept out of the Voronoi pool but listed for /locate, a MacroRegionOverride places them. */
@ApiStatus.Experimental
public final class BeyondBiomeRegistration {
    private static final Set<ResourceLocation> POOL_EXCLUDED = new CopyOnWriteArraySet<>();

    private BeyondBiomeRegistration() {}

    /** Marks biomeId overlay-only, it needs a registered MacroRegionOverride or it never appears. */
    public static void addPoolExcludedBiome(ResourceLocation biomeId) {
        POOL_EXCLUDED.add(biomeId);
    }

    public static boolean isPoolExcluded(ResourceLocation biomeId) {
        return POOL_EXCLUDED.contains(biomeId);
    }

    public static Set<ResourceLocation> poolExcludedBiomes() {
        return Set.copyOf(POOL_EXCLUDED);
    }
}
