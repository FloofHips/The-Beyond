package com.thebeyond.common.worldgen.compat;

import it.unimi.dsi.fastutil.ints.IntList;

import java.util.HashMap;
import java.util.Map;

/** Per-thread one-chunk cache for the reef-Voronoi-aware Y placement scan. Outside the
 *  mixin package so the Mixin classloader doesn't reject it. */
public final class ChunkColumnReefCache {
    public long currentChunk = Long.MIN_VALUE;
    public final Map<Long, IntList> reefYsByColumn = new HashMap<>();

    public IntList get(long chunkKey, long colKey) {
        return chunkKey != currentChunk ? null : reefYsByColumn.get(colKey);
    }

    public void put(long chunkKey, long colKey, IntList reefYs) {
        if (chunkKey != currentChunk) {
            currentChunk = chunkKey;
            reefYsByColumn.clear();
        }
        reefYsByColumn.put(colKey, reefYs);
    }
}
