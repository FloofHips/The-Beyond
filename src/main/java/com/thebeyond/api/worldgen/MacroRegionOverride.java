package com.thebeyond.api.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Returns a biome at {@code (blockX, blockZ)} or {@code null} for no overlay, queried
 *  before the Voronoi roll. Implementations must be stateless. */
@ApiStatus.Experimental
@FunctionalInterface
public interface MacroRegionOverride {
    @Nullable Holder<Biome> biomeAt(int blockX, int blockZ);
}
