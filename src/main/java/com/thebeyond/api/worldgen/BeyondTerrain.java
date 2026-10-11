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

    /** Layer tops at (x, z) within [minY, maxY], highest first, empty for a uniform column. */
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

    /** Whether Beyond's End terrain is solid here by the chunks' own density, false outside it or before it is primed. */
    public static boolean isSolidAt(int x, int y, int z) {
        if (!BeyondTerrainState.isActive()) return false;
        try {
            BeyondEndChunkGenerator.ColumnScratch scratch = BeyondEndChunkGenerator.getColumnScratch();
            float distance = (float) Math.sqrt((double) x * x + (double) z * z);
            BeyondEndChunkGenerator.initColumnScratch(x, z, distance, scratch);
            return BeyondEndChunkGenerator.isSolidTerrainScratch(y, scratch);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Highest solid Y in [minY, maxY] at (x, z), MIN_VALUE when empty, not Beyond's, or not primed. */
    public static int findSurfaceTop(int x, int z, int minY, int maxY) {
        if (!BeyondTerrainState.isActive()) return Integer.MIN_VALUE;
        try {
            BeyondEndChunkGenerator.ColumnScratch scratch = BeyondEndChunkGenerator.getColumnScratch();
            float distance = (float) Math.sqrt((double) x * x + (double) z * z);
            BeyondEndChunkGenerator.initColumnScratch(x, z, distance, scratch);
            for (int y = maxY; y >= minY; y--) {
                if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scratch)) return y;
            }
            return Integer.MIN_VALUE;
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /** Beyond's biome-noise simplex, read only, null before worldgen bootstraps. */
    @Nullable
    public static HashSimplexNoise biomeSimplexNoise() {
        return BeyondEndChunkGenerator.biomeSimplexNoise;
    }
}
