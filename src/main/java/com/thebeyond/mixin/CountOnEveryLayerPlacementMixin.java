package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
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

/** Column scan emitting one position per solid→non-solid transition, replacing vanilla's
 *  single-tier {@code findOnGroundYPosition} which only sees the topmost pancake. */
@Mixin(CountOnEveryLayerPlacement.class)
public abstract class CountOnEveryLayerPlacementMixin {
    @Shadow @Final private IntProvider count;

    private static final int MAX_LAYERS = 32;
    private static final ThreadLocal<ChunkColumnCache> CACHE = ThreadLocal.withInitial(ChunkColumnCache::new);

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void the_beyond$multiLayerScan(
            PlacementContext ctx, RandomSource random, BlockPos origin,
            CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!BeyondTerrainState.isActive()) return;

        int baseX = origin.getX();
        int baseZ = origin.getZ();
        int minY = ctx.getMinBuildHeight();
        int maxY = ctx.getLevel().getMaxBuildHeight() - 1;

        List<BlockPos> positions = new ArrayList<>();
        for (int layer = 0; layer < MAX_LAYERS; layer++) {
            int n = count.sample(random);
            boolean foundAny = false;
            for (int i = 0; i < n; i++) {
                int x = baseX + random.nextInt(16);
                int z = baseZ + random.nextInt(16);
                int y = findLayerSurfaceY(ctx, x, z, layer, minY, maxY);
                if (y != Integer.MAX_VALUE) {
                    positions.add(new BlockPos(x, y, z));
                    foundAny = true;
                }
            }
            if (!foundAny) {
                boolean layerExists = false;
                int[][] probes = {{4, 4}, {4, 12}, {12, 4}, {12, 12}, {8, 8}};
                for (int[] p : probes) {
                    if (findLayerSurfaceY(ctx, baseX + p[0], baseZ + p[1], layer, minY, maxY) != Integer.MAX_VALUE) {
                        layerExists = true;
                        break;
                    }
                }
                if (!layerExists) break;
            }
        }
        cir.setReturnValue(positions.stream());
    }

    /** Y above the {@code targetLayer}-th solid→non-solid transition (top-down, 0=topmost). MAX_VALUE if none. Fluids count as non-solid so lake surfaces produce their own layer. */
    private static int findLayerSurfaceY(PlacementContext ctx, int x, int z,
                                         int targetLayer, int minY, int maxY) {
        int[] surfaces = getOrComputeSurfaces(ctx, x, z, minY, maxY);
        return targetLayer < surfaces.length ? surfaces[targetLayer] : Integer.MAX_VALUE;
    }

    private static int[] getOrComputeSurfaces(PlacementContext ctx, int x, int z, int minY, int maxY) {
        long chunkKey = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        long columnKey = ((long) x << 32) | (z & 0xFFFFFFFFL);
        ChunkColumnCache cache = CACHE.get();
        int[] cached = cache.get(chunkKey, columnKey);
        if (cached != null) return cached;
        int[] surfaces = scanColumn(ctx, x, z, minY, maxY);
        cache.put(chunkKey, columnKey, surfaces);
        return surfaces;
    }

    private static int[] scanColumn(PlacementContext ctx, int x, int z, int minY, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);
        int[] tmp = new int[8];
        int n = 0;
        boolean prevWasSolid = false;
        for (int y = maxY; y >= minY; y--) {
            pos.setY(y);
            BlockState state = ctx.getBlockState(pos);
            boolean isSolid = !state.isAir() && state.getFluidState().isEmpty();
            if (!prevWasSolid && isSolid) {
                if (n == tmp.length) {
                    int[] grown = new int[tmp.length * 2];
                    System.arraycopy(tmp, 0, grown, 0, n);
                    tmp = grown;
                }
                tmp[n++] = y + 1;
            }
            prevWasSolid = isSolid;
        }
        if (n == tmp.length) return tmp;
        int[] result = new int[n];
        System.arraycopy(tmp, 0, result, 0, n);
        return result;
    }

    private static final class ChunkColumnCache {
        long currentChunk = Long.MIN_VALUE;
        final Map<Long, int[]> columns = new HashMap<>();

        int[] get(long chunkKey, long columnKey) {
            return chunkKey != currentChunk ? null : columns.get(columnKey);
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
