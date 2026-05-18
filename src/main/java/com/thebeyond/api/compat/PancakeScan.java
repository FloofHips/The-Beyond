package com.thebeyond.api.compat;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Deterministic per-chunk spot picker: chunk-seeded shuffle of the 4×4 Voronoi grid for
 *  the first pancake top whose biome matches the structure filter. */
@ApiStatus.Experimental
public final class PancakeScan {
    private static final TagKey<Biome> END_TAG = TagKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("is_end"));
    private static final long SEED_P1 = 341873128712L;
    private static final long SEED_P2 = 132897987541L;
    private static final int[][] SAMPLES = {
            {2, 2}, {2, 6}, {2, 10}, {2, 14},
            {6, 2}, {6, 6}, {6, 10}, {6, 14},
            {10, 2}, {10, 6}, {10, 10}, {10, 14},
            {14, 2}, {14, 6}, {14, 10}, {14, 14}
    };
    private static final int CACHE_LIMIT = 4096;
    private static final int[] NO_SPOT = new int[0];

    private static final ConcurrentMap<Long, int[]> SPOT_CACHE = new ConcurrentHashMap<>();

    private PancakeScan() {}

    /** Picks a {@code (worldX, Y, worldZ)} spot in the chunk whose pancake-top biome is in
     *  {@code structureBiomes} (or in {@code #is_end} if null). Cached. */
    public static int[] pickEndBiomeSpotInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes) {
        long key = makeKey(chunkX, chunkZ);
        int[] cached = SPOT_CACHE.get(key);
        if (cached != null) return cached.length == 3 ? cached : null;

        int[] result = compute(gen, chunkX, chunkZ, level, rs, structureBiomes);
        if (SPOT_CACHE.size() < CACHE_LIMIT) {
            SPOT_CACHE.put(key, result != null ? result : NO_SPOT);
        }
        return result;
    }

    public static int[] getCachedSpot(int chunkX, int chunkZ) {
        int[] cached = SPOT_CACHE.get(makeKey(chunkX, chunkZ));
        return (cached != null && cached.length == 3) ? cached : null;
    }

    public static int alignedY(int worldX, int worldZ, int fallback) {
        if (!BeyondTerrainState.isActive()) return fallback;
        int[] spot = getCachedSpot(worldX >> 4, worldZ >> 4);
        return spot != null ? spot[1] : fallback;
    }

    public static BlockPos alignedBlockPos(int x, int y, int z) {
        if (BeyondTerrainState.isActive()) {
            int[] spot = getCachedSpot(x >> 4, z >> 4);
            if (spot != null) return new BlockPos(spot[0], y, spot[2]);
        }
        return new BlockPos(x, y, z);
    }

    public static BlockPos.MutableBlockPos alignedSet(BlockPos.MutableBlockPos pos, int x, int y, int z) {
        if (BeyondTerrainState.isActive()) {
            int[] spot = getCachedSpot(x >> 4, z >> 4);
            if (spot != null) return pos.set(spot[0], y, spot[2]);
        }
        return pos.set(x, y, z);
    }

    private static long makeKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | ((long) chunkZ & 0xFFFFFFFFL);
    }

    private static int[] compute(ChunkGenerator gen, int chunkX, int chunkZ,
                                  LevelHeightAccessor level, RandomState rs,
                                  HolderSet<Biome> structureBiomes) {
        long seed = ((long) chunkX) * SEED_P1 ^ ((long) chunkZ) * SEED_P2;
        int[] order = new int[SAMPLES.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        for (int i = order.length - 1; i > 0; i--) {
            int j = (int) Math.floorMod(seed + ((long) i) * 31L, i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        for (int idx : order) {
            int wx = (chunkX << 4) + SAMPLES[idx][0];
            int wz = (chunkZ << 4) + SAMPLES[idx][1];
            int y = scanForMatchingPancake(gen, wx, wz, level, rs, structureBiomes);
            if (y != Integer.MIN_VALUE) return new int[]{wx, y, wz};
        }
        return null;
    }

    private static final int MIN_THICKNESS = 3;
    private static final int BURY_DEPTH = 3;
    private static final int MIN_SOLID_NEIGHBOURS = 3;

    private static int scanForMatchingPancake(ChunkGenerator gen, int x, int z,
                                                LevelHeightAccessor level, RandomState rs,
                                                HolderSet<Biome> structureBiomes) {
        NoiseColumn col = gen.getBaseColumn(x, z, level, rs);
        boolean inAir = true;
        int minY = level.getMinBuildHeight();
        for (int y = level.getMaxBuildHeight() - 1; y >= minY; y--) {
            boolean isAir = col.getBlock(y).isAir();
            if (inAir && !isAir) {
                if (y - (MIN_THICKNESS - 1) < minY
                        || col.getBlock(y - 1).isAir()
                        || col.getBlock(y - 2).isAir()) {
                    inAir = false;
                    continue;
                }
                int surfaceY = y;
                Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(
                        x >> 2, surfaceY >> 2, z >> 2, rs.sampler());
                boolean matches = structureBiomes != null
                        ? structureBiomes.contains(biome)
                        : biome.is(END_TAG);
                if (matches && hasLateralSupport(gen, x, z, surfaceY, level, rs)) {
                    return surfaceY - BURY_DEPTH;
                }
                inAir = false;
            } else if (!inAir && isAir) {
                inAir = true;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean hasLateralSupport(ChunkGenerator gen, int x, int z, int surfaceY,
                                              LevelHeightAccessor level, RandomState rs) {
        int solid = 0;
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int[] o : offsets) {
            NoiseColumn col = gen.getBaseColumn(x + o[0], z + o[1], level, rs);
            if (!col.getBlock(surfaceY).isAir()) solid++;
        }
        return solid >= MIN_SOLID_NEIGHBOURS;
    }
}
