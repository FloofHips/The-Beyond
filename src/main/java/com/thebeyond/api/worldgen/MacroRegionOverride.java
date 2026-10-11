package com.thebeyond.api.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** A biome at (blockX, blockZ) or null, asked before the Voronoi roll, and implementations must be stateless. */
@ApiStatus.Experimental
@FunctionalInterface
public interface MacroRegionOverride {
    @Nullable Holder<Biome> biomeAt(int blockX, int blockZ);
}
