package com.thebeyond.api.worldgen;

import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.util.HashSimplexNoise;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.stream.IntStream;

/** Stable read-only entry points into Beyond's End worldgen samplers. */
@ApiStatus.Experimental
public final class BeyondTerrain {
    private BeyondTerrain() {}

    /** Descending pancake-top Y at {@code (x, z)} within {@code [minY, maxY]} (first solid
     *  above each air→solid transition); empty for a uniform column. */
    public static IntStream streamPancakeTops(int x, int z, int minY, int maxY) {
        BeyondEndChunkGenerator.ColumnScratch scratch = BeyondEndChunkGenerator.getColumnScratch();
        float distance = (float) Math.sqrt((double) x * x + (double) z * z);
        BeyondEndChunkGenerator.initColumnScratch(x, z, distance, scratch);
        IntStream.Builder b = IntStream.builder();
        boolean prevSolid = false;
        for (int y = maxY; y >= minY; y--) {
            boolean solid = BeyondEndChunkGenerator.isSolidTerrainScratch(y, scratch);
            if (!prevSolid && solid) b.add(y + 1);
            prevSolid = solid;
        }
        return b.build();
    }

    /** Beyond's biome-noise simplex field, or {@code null} before worldgen bootstraps.
     *  Read-only; for biome-aligned macro-region deformation. */
    @Nullable
    public static HashSimplexNoise biomeSimplexNoise() {
        return BeyondEndChunkGenerator.biomeSimplexNoise;
    }
}
