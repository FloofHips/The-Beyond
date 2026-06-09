package com.thebeyond.api.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

/** Cast target for addons to read/mutate the Beyond End biome pool without depending
 *  on the internal {@code BeyondEndBiomeSource}. Absent on vanilla/other End mods. */
@ApiStatus.Experimental
public interface BeyondEndBiomeSourceApi {
    /** Injects discovered biomes into the tainted Voronoi pool at server start; dedups by
     *  {@code ResourceKey}. @return count added after dedup */
    int injectBiomesIntoTaintedPool(Collection<Holder<Biome>> biomes);

    /** Voronoi-cell biome at {@code (blockX, blockY, blockZ)} from the tainted pool,
     *  ignoring the air/solid density split. */
    Holder<Biome> voronoiCellBiomeIgnoringDensity(int blockX, int blockY, int blockZ);
}
