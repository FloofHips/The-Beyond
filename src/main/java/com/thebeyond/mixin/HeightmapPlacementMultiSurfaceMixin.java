package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Emits one position per pancake top when Beyond owns the End; single-surface columns
 *  fall through to vanilla so other mods' {@code getPositions} mixins still execute. */
@Mixin(HeightmapPlacement.class)
public abstract class HeightmapPlacementMultiSurfaceMixin {
    @Shadow @Final private Heightmap.Types heightmap;

    /** Per-thread cache: chunkKey + columnKey → cached surface Y array. */
    private static final ThreadLocal<ChunkColumnCache> CACHE =
            ThreadLocal.withInitial(ChunkColumnCache::new);

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void the_beyond$multiSurfaceScan(
            PlacementContext ctx, RandomSource random, BlockPos origin,
            CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!BeyondTerrainState.isActive()) return;

        int x = origin.getX();
        int z = origin.getZ();

        int topY = ctx.getHeight(this.heightmap, x, z);
        int minY = ctx.getMinBuildHeight();
        if (topY <= minY) {
            cir.setReturnValue(Stream.empty());
            return;
        }

        long chunkKey = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        long columnKey = ((long) x << 32) | (z & 0xFFFFFFFFL);
        ChunkColumnCache cache = CACHE.get();
        int[] cached = cache.get(chunkKey, columnKey);
        if (cached == null) {
            cached = scanColumn(ctx, x, z, minY, topY);
            cache.put(chunkKey, columnKey, cached);
        }

        // Single-surface column: vanilla behaviour. Skip the override so other mods'
        // mixins on getPositions (and vanilla logic) execute normally.
        if (cached.length <= 1) return;

        List<BlockPos> positions = new ArrayList<>(cached.length);
        for (int y : cached) positions.add(new BlockPos(x, y, z));
        cir.setReturnValue(positions.stream());
    }

    private static int[] scanColumn(PlacementContext ctx, int x, int z, int minY, int topY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, minY, z);
        // Pre-size assumes ≤8 pancakes per column; grow if needed.
        int[] tmp = new int[8];
        int n = 0;
        boolean wasSolid = false;
        for (int y = minY; y <= topY; y++) {
            pos.setY(y);
            boolean isSolid = !ctx.getBlockState(pos).isAir();
            // Surface = first air block above a solid one. Place feature here.
            if (wasSolid && !isSolid) {
                if (n == tmp.length) {
                    int[] grown = new int[tmp.length * 2];
                    System.arraycopy(tmp, 0, grown, 0, n);
                    tmp = grown;
                }
                tmp[n++] = y;
            }
            wasSolid = isSolid;
        }
        if (n == tmp.length) return tmp;
        int[] result = new int[n];
        System.arraycopy(tmp, 0, result, 0, n);
        return result;
    }

    /**
     * Single-chunk LRU: invalidates entries when the active chunk changes.
     * Chunks are decorated sequentially per thread, so a one-chunk window is enough.
     */
    private static final class ChunkColumnCache {
        long currentChunk = Long.MIN_VALUE;
        Map<Long, int[]> columns = new HashMap<>();

        int[] get(long chunkKey, long columnKey) {
            if (chunkKey != currentChunk) return null;
            return columns.get(columnKey);
        }

        void put(long chunkKey, long columnKey, int[] surfaces) {
            if (chunkKey != currentChunk) {
                currentChunk = chunkKey;
                columns.clear();
            }
            columns.put(columnKey, surfaces);
        }
    }
}
