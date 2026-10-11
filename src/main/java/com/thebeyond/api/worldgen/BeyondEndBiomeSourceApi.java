package com.thebeyond.api.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;

/** Lets addons read and change the Beyond End biome pool without the internal class, absent on other End sources. */
@ApiStatus.Experimental
public interface BeyondEndBiomeSourceApi {
    /** Adds discovered biomes to the tainted Voronoi pool at server start and returns how many were new. */
    int injectBiomesIntoTaintedPool(Collection<Holder<Biome>> biomes);

    Holder<Biome> voronoiCellBiomeIgnoringDensity(int blockX, int blockY, int blockZ);
}
