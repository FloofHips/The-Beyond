package com.thebeyond.api.worldgen;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Pool-excluded biome registry: biomes in {@code allBiomes} (so {@code /locate} works)
 *  but never rolled in the Voronoi pool — a {@link MacroRegionOverride} supplies them. */
@ApiStatus.Experimental
public final class BeyondBiomeRegistration {
    private static final Set<ResourceLocation> POOL_EXCLUDED = new CopyOnWriteArraySet<>();

    private BeyondBiomeRegistration() {}

    /** Marks {@code biomeId} overlay-only. Must be paired with a registered
     *  {@link MacroRegionOverride} that supplies it, or the biome never appears. */
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
