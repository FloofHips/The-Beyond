package com.thebeyond.api.compat;

import com.thebeyond.api.worldgen.BeyondTerrain;
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

    private record SpotKey(long chunk, Object biomes) {
        @Override public boolean equals(Object o) { return o instanceof SpotKey k && k.chunk == chunk && k.biomes == biomes; }
        @Override public int hashCode() { return Long.hashCode(chunk) * 31 + System.identityHashCode(biomes); }
    }
    private static final ConcurrentMap<SpotKey, int[]> SPOT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Long, int[]> FIRST_SPOT = new ConcurrentHashMap<>();

    private PancakeScan() {}

    public static void clearCache() {
        SPOT_CACHE.clear();
        FIRST_SPOT.clear();
    }

    /** {@code structureBiomes} null falls back to {@code #is_end}. Cached. */
    public static int[] pickEndBiomeSpotInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes) {
        long chunk = makeKey(chunkX, chunkZ);
        SpotKey key = new SpotKey(chunk, structureBiomes);
        int[] cached = SPOT_CACHE.get(key);
        if (cached != null) return cached.length == 3 ? cached : null;

        int[] result = compute(gen, chunkX, chunkZ, level, rs, structureBiomes);
        if (SPOT_CACHE.size() < CACHE_LIMIT) {
            SPOT_CACHE.put(key, result != null ? result : NO_SPOT);
        }
        if (result != null && FIRST_SPOT.size() < CACHE_LIMIT) FIRST_SPOT.putIfAbsent(chunk, result);
        return result;
    }

    public static final int LAYER_MIN_HEADROOM = 8;

    /** Shallower than {@link #BURY_DEPTH}: surface structures sit nearly flush at this depth. */
    public static final int LAYER_BURY_DEPTH = 1;

    /** Only shifts preference among already-safe candidates — the topmost always clears the headroom gate. */
    public static final int LOWER_LAYER_WEIGHT = 3;

    public static int pickWeightedIndex(int[] weights, int n, long seed) {
        long total = 0;
        for (int i = 0; i < n; i++) total += weights[i];
        long r = Math.floorMod(seed, total);
        for (int i = 0; i < n; i++) {
            r -= weights[i];
            if (r < 0) return i;
        }
        return n - 1;
    }

    /** Null return means callers should fall back to topmost projection. */
    public static int[] pickEndBiomeLayerInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes, int minHeadroom) {
        return pickEndBiomeLayerInChunk(gen, chunkX, chunkZ, level, rs, structureBiomes, minHeadroom,
                FOOTPRINT_SUPPORT_RADIUS, FOOTPRINT_SUPPORT_STRIDE, FOOTPRINT_SUPPORT_FRACTION);
    }

    public static int[] pickEndBiomeLayerInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes, int minHeadroom,
                                                  int radius, int stride, double fraction) {
        return pickEndBiomeLayerInChunk(gen, chunkX, chunkZ, level, rs, structureBiomes, minHeadroom,
                radius, stride, fraction, Integer.MAX_VALUE);
    }

    /** {@code maxSeatY} caps the seat itself: terrain above a tall structure is the carve's problem, not a reason to reject. */
    public static int[] pickEndBiomeLayerInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes, int minHeadroom,
                                                  int radius, int stride, double fraction, int maxSeatY) {
        return pickEndBiomeLayerInChunk(gen, chunkX, chunkZ, level, rs, structureBiomes, minHeadroom,
                radius, stride, fraction, maxSeatY, 0);
    }

    public static int[] pickEndBiomeLayerInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes, int minHeadroom,
                                                  int radius, int stride, double fraction, int maxSeatY,
                                                  int flushTol) {
        return pickCandidate(layerCandidates(gen, chunkX, chunkZ, level, rs, structureBiomes, minHeadroom,
                radius, stride, fraction, maxSeatY, flushTol), chunkX, chunkZ);
    }

    /** Every layer seat of the chunk in weighted-draw order, the first being what {@link #pickEndBiomeLayerInChunk} returns. */
    public static java.util.List<int[]> orderedEndBiomeLayersInChunk(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes, int minHeadroom) {
        return orderByWeight(layerCandidates(gen, chunkX, chunkZ, level, rs, structureBiomes, minHeadroom,
                FOOTPRINT_SUPPORT_RADIUS, FOOTPRINT_SUPPORT_STRIDE, FOOTPRINT_SUPPORT_FRACTION, Integer.MAX_VALUE, 0),
                chunkX, chunkZ);
    }

    static java.util.List<int[]> orderByWeight(java.util.List<int[]> cands, int chunkX, int chunkZ) {
        java.util.List<int[]> left = new java.util.ArrayList<>(cands);
        java.util.List<int[]> out = new java.util.ArrayList<>(left.size());
        long seed = ((long) chunkX) * SEED_P1 ^ ((long) chunkZ) * SEED_P2;
        while (!left.isEmpty()) {
            int[] w = new int[left.size()];
            for (int i = 0; i < w.length; i++) w[i] = left.get(i)[3];
            out.add(left.remove(pickWeightedIndex(w, w.length, seed)));
            seed = seed * 6364136223846793005L + 1442695040888963407L;
        }
        return out;
    }

    private static java.util.List<int[]> layerCandidates(ChunkGenerator gen, int chunkX, int chunkZ,
                                                  LevelHeightAccessor level, RandomState rs,
                                                  HolderSet<Biome> structureBiomes, int minHeadroom,
                                                  int radius, int stride, double fraction, int maxSeatY,
                                                  int flushTol) {
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        java.util.List<int[]> cands = new java.util.ArrayList<>();
        for (int[] s : SAMPLES) {
            int wx = (chunkX << 4) + s[0], wz = (chunkZ << 4) + s[1];
            NoiseColumn col = gen.getBaseColumn(wx, wz, level, rs);
            int airRun = 0;
            boolean topOfColumn = true;
            for (int y = maxY; y >= minY; y--) {
                if (col.getBlock(y).isAir()) { airRun++; continue; }
                if (airRun >= minHeadroom && y - (MIN_THICKNESS - 1) >= minY
                        && !col.getBlock(y - 1).isAir() && !col.getBlock(y - 2).isAir()) {
                    Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(wx >> 2, y >> 2, wz >> 2, rs.sampler());
                    boolean matches = structureBiomes != null ? structureBiomes.contains(biome) : biome.is(END_TAG);
                    // Full footprint check (not a single cross probe): guards against a small qualifying pancake
                    // letting a large structure's base overhang into the void.
                    double cov = matches
                            ? footprintCoverage(wx, wz, y, radius, stride, BeyondTerrain::isSolidAt, flushTol)
                            : -1.0;
                    if (cov >= fraction) {
                        // One seatY for the cap and the candidate so LAYER_BURY_DEPTH cannot drift. Callers read [0..2].
                        int seatY = y - LAYER_BURY_DEPTH;
                        if (seatY <= maxSeatY)
                            cands.add(new int[]{wx, seatY, wz,
                                    (topOfColumn ? 1 : LOWER_LAYER_WEIGHT) * coverageWeight(cov)});
                        // Set regardless of the cap: it marks the terrain's top layer, not the caller's filter.
                        topOfColumn = false;
                    }
                }
                airRun = 0;
            }
        }
        return cands;
    }

    static int[] pickCandidate(java.util.List<int[]> cands, int chunkX, int chunkZ) {
        if (cands.isEmpty()) return null;
        long seed = ((long) chunkX) * SEED_P1 ^ ((long) chunkZ) * SEED_P2;
        int[] w = new int[cands.size()];
        for (int i = 0; i < w.length; i++) w[i] = cands.get(i)[3];
        return cands.get(pickWeightedIndex(w, w.length, seed));
    }

    /** Returns the solid top (not +1); {@link Integer#MIN_VALUE} if none qualifies. */
    public static int pickColumnPancakeY(ChunkGenerator gen, int x, int z, LevelHeightAccessor level, RandomState rs,
                                         HolderSet<Biome> structureBiomes, int minHeadroom, int radius) {
        return pickColumnPancakeY(gen, x, z, level, rs, structureBiomes, minHeadroom, radius,
                FOOTPRINT_SUPPORT_STRIDE, FOOTPRINT_SUPPORT_FRACTION);
    }

    /** As above, but a caller-chosen coverage {@code stride}/{@code fraction} so a wide-footprint structure can't
     *  qualify on a small pancake. Draws one qualifying pancake, lower layers weighted heavier. */
    public static int pickColumnPancakeY(ChunkGenerator gen, int x, int z, LevelHeightAccessor level, RandomState rs,
                                         HolderSet<Biome> structureBiomes, int minHeadroom, int radius, int stride, double fraction) {
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        NoiseColumn col = gen.getBaseColumn(x, z, level, rs);
        java.util.List<Integer> cands = new java.util.ArrayList<>();
        int airRun = 0;
        for (int y = maxY; y >= minY; y--) {
            if (col.getBlock(y).isAir()) { airRun++; continue; }
            if (airRun >= minHeadroom && y - (MIN_THICKNESS - 1) >= minY
                    && !col.getBlock(y - 1).isAir() && !col.getBlock(y - 2).isAir()) {
                Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, rs.sampler());
                boolean matches = structureBiomes != null ? structureBiomes.contains(biome) : biome.is(END_TAG);
                if (matches && hasFootprintSupport(x, z, y, radius, stride, fraction, BeyondTerrain::isSolidAt)) {
                    cands.add(y);
                }
            }
            airRun = 0;
        }
        if (cands.isEmpty()) return Integer.MIN_VALUE;
        long seed = ((long) (x >> 4)) * SEED_P1 ^ ((long) (z >> 4)) * SEED_P2;
        // Top-down scan: index 0 is the column's highest qualifier, de-weighted vs. lower layers.
        int[] w = new int[cands.size()];
        for (int i = 0; i < w.length; i++) w[i] = i == 0 ? 1 : LOWER_LAYER_WEIGHT;
        return cands.get(pickWeightedIndex(w, w.length, seed));
    }

    public record GroundedSeat(int seatY, int coverPct, int topmostLayerY, int validLayers, int totalLayers, int chosenLayerTop) {
        public boolean grounded() { return seatY != Integer.MIN_VALUE; }
    }

    /** Weighted-lottery seat for a grounded ruin: draws one of its column's island layers the base-beard can bridge to,
     *  seating at its surface median. A non-{@link GroundedSeat#grounded()} result ⇒ nothing grounds it, caller rejects. */
    public static GroundedSeat pickGroundedPancake(ChunkGenerator gen, int x, int z, LevelHeightAccessor level, RandomState rs,
            HolderSet<Biome> biomes, int minHeadroom, int radius, int stride, double minCover, int fill, int over) {
        record CandidateLayer(int seatMedian, int coverPct, int top) {}
        int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight() - 1;
        NoiseColumn col = gen.getBaseColumn(x, z, level, rs);
        java.util.List<Integer> layerTops = new java.util.ArrayList<>();
        int airRun = 0;
        for (int y = maxY; y >= minY; y--) {
            if (col.getBlock(y).isAir()) { airRun++; continue; }
            if (airRun >= minHeadroom && y - (MIN_THICKNESS - 1) >= minY
                    && !col.getBlock(y - 1).isAir() && !col.getBlock(y - 2).isAir()) {
                Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(x >> 2, y >> 2, z >> 2, rs.sampler());
                boolean matches = biomes != null ? biomes.contains(biome) : biome.is(END_TAG);
                if (matches) layerTops.add(y);
            }
            airRun = 0;
        }
        int topmost = layerTops.isEmpty() ? Integer.MIN_VALUE : layerTops.get(0);
        java.util.List<CandidateLayer> valid = new java.util.ArrayList<>();
        for (int yi : layerTops) {
            java.util.List<Integer> near = new java.util.ArrayList<>();
            int sampled = 0;
            // Evenly spaced steps always reach both edges and the centre, which a plain stride can miss.
            int steps = Math.max(2, (2 * radius) / Math.max(1, stride));
            for (int i = 0; i <= steps; i++) {
                int dx = -radius + (2 * radius * i) / steps;
                for (int j = 0; j <= steps; j++) {
                    int dz = -radius + (2 * radius * j) / steps;
                    sampled++;
                    int s = BeyondTerrain.findSurfaceTop(x + dx, z + dz, yi - fill, yi + over);
                    if (s != Integer.MIN_VALUE) near.add(s);
                }
            }
            if (sampled > 0 && (double) near.size() / sampled >= minCover) {
                near.sort(null);
                valid.add(new CandidateLayer(near.get(near.size() / 2), near.size() * 100 / sampled, yi));
            }
        }
        if (valid.isEmpty()) {
            return new GroundedSeat(Integer.MIN_VALUE, 0, topmost, 0, layerTops.size(), Integer.MIN_VALUE);
        }
        // Weighted draw among the grounded layers, lower heavier; chunk-seeded so it's deterministic.
        long seed = ((long) (x >> 4)) * SEED_P1 ^ ((long) (z >> 4)) * SEED_P2;
        int[] w = new int[valid.size()];
        for (int i = 0; i < w.length; i++) w[i] = i == 0 ? 1 : LOWER_LAYER_WEIGHT;
        CandidateLayer chosen = valid.get(pickWeightedIndex(w, w.length, seed));
        return new GroundedSeat(chosen.seatMedian(), chosen.coverPct(), topmost, valid.size(), layerTops.size(), chosen.top());
    }

    public static int[] getCachedSpot(int chunkX, int chunkZ) {
        return FIRST_SPOT.get(makeKey(chunkX, chunkZ));
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

    public static final int FOOTPRINT_SUPPORT_RADIUS = 10;
    public static final int FOOTPRINT_SUPPORT_STRIDE = 2;
    public static final double FOOTPRINT_SUPPORT_FRACTION = 0.5;

    public static final int FLUSH_TOLERANCE = 3;

    /** {radius, coverage, flush tolerance}, strict to loose. Late rungs narrow the radius, as low coverage accepts a needle. */
    public static final double[][] FOOTPRINT_COVERAGE_TIERS = {
            { FOOTPRINT_SUPPORT_RADIUS, FOOTPRINT_SUPPORT_FRACTION, FLUSH_TOLERANCE },
            { FOOTPRINT_SUPPORT_RADIUS, 0.30, FLUSH_TOLERANCE },
            { 6, FOOTPRINT_SUPPORT_FRACTION, FLUSH_TOLERANCE },
            { 2, FOOTPRINT_SUPPORT_FRACTION, 0 }
    };

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

    /** Abstracts the solidity source (production density model vs. a synthetic island in tests). */
    @FunctionalInterface
    public interface SolidProbe { boolean isSolid(int x, int y, int z); }

    public static boolean hasFootprintSupport(int cx, int cz, int y, int radius, int stride, double fraction, SolidProbe probe) {
        return footprintCoverage(cx, cz, y, radius, stride, probe) >= fraction;
    }

    public static boolean isFlushAt(int x, int z, int y, int tol, SolidProbe probe) {
        if (!probe.isSolid(x, y, z)) return false;
        return tol <= 0 || !probe.isSolid(x, y + tol, z);
    }

    public static double footprintCoverage(int cx, int cz, int y, int radius, int stride, SolidProbe probe) {
        return footprintCoverage(cx, cz, y, radius, stride, probe, 0);
    }

    public static double footprintCoverage(int cx, int cz, int y, int radius, int stride, SolidProbe probe, int tol) {
        int sampled = 0, solid = 0;
        for (int dx = -radius; dx <= radius; dx += stride) {
            for (int dz = -radius; dz <= radius; dz += stride) {
                sampled++;
                if (isFlushAt(cx + dx, cz + dz, y, tol, probe)) solid++;
            }
        }
        return sampled > 0 ? (double) solid / sampled : -1.0;
    }

    public static int coverageWeight(double coverage) {
        if (coverage <= 0.0) return 1;
        return 1 + (int) Math.round(coverage * COVERAGE_WEIGHT_SPAN);
    }

    public static final int COVERAGE_WEIGHT_SPAN = 8;

    /** Snaps a chunk-wide seat onto the structure's own column, since the pancake that fits the chunk can be a corner away. */
    public static int seatOnOwnColumn(ChunkGenerator gen, int x, int z, int scanY, int slack,
            LevelHeightAccessor height, net.minecraft.world.level.levelgen.RandomState random) {
        try {
            NoiseColumn column = gen.getBaseColumn(x, z, height, random);
            int top = Math.min(scanY + slack, height.getMaxBuildHeight() - 1);
            int floor = Math.max(scanY - slack, height.getMinBuildHeight() + 1);
            for (int y = top; y >= floor; y--) {
                if (!column.getBlock(y).isAir()) return y + 1;
            }
        } catch (Throwable ignored) { /* fail-soft: the chunk scan's own Y stands */ }
        return scanY;
    }
}
