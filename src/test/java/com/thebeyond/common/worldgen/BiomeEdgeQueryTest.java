package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.thebeyond.util.HashSimplexNoise;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Predicate;

class BiomeEdgeQueryTest {

    private static final long SEED = -109715808474535652L;
    private static final int MIN_Y = -64, MAX_Y = 320;
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(MIN_Y, MAX_Y - MIN_Y);
    private static final int MIN_Q = QuartPos.fromBlock(MIN_Y), MAX_Q = MIN_Q + QuartPos.fromBlock(MAX_Y - MIN_Y) - 1;
    private static final HolderOwner<Biome> OWNER = new HolderOwner<>() {};
    private static final int[] FLAT_RADII = {0, 1, 3, 6, 12, 20, 32, 40};
    private static final int[] BALL_RADII = {0, 2, 6, 12, 20, 28};
    private static final int CASES = Integer.getInteger("edge.cases", 8000);
    private static final Path OUT = Path.of("build", "test-run");

    private final List<Holder<Biome>> land = List.of(biome("test", "land_a"), biome("test", "land_b"), biome("test", "land_c"));
    private final List<Holder<Biome>> outer = List.of(biome("minecraft", "the_void"), biome("test", "void_a"), biome("test", "void_b"));
    private final List<Holder<Biome>> inner = List.of(biome("test", "inner_a"), biome("test", "inner_b"));
    private final Holder<Biome> center = biome("test", "center");
    private final Holder<Biome> paths = biome("test", "paths");

    private HashSimplexNoise sS, sBS, sWZ;
    private PerlinSimplexNoise sH, sV, sC;
    private BeyondTerrainParams sP;
    private long sSeed;
    private int sMin, sMax;

    @BeforeEach
    void setUp() {
        sS = BeyondEndChunkGenerator.simplexNoise; sBS = BeyondEndChunkGenerator.biomeSimplexNoise;
        sWZ = BeyondEndChunkGenerator.warpZSimplexNoise;
        sH = BeyondEndChunkGenerator.globalHOffsetNoise; sV = BeyondEndChunkGenerator.globalVOffsetNoise;
        sC = BeyondEndChunkGenerator.globalCOffsetNoise; sP = BeyondEndChunkGenerator.activeTerrainParams;
        sSeed = BeyondEndChunkGenerator.seed;
        sMin = BeyondTerrainState.getDimMinY(); sMax = BeyondTerrainState.getDimMaxY();
        // set up the noises the way BeyondEndChunkGenerator.computeNoisesIfNotPresent does for a world seed
        BeyondEndChunkGenerator.seed = SEED;
        BeyondEndChunkGenerator.simplexNoise = new HashSimplexNoise(RandomSource.create(SEED));
        BeyondEndChunkGenerator.globalHOffsetNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 1), List.of(1));
        BeyondEndChunkGenerator.globalVOffsetNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 2), List.of(1));
        BeyondEndChunkGenerator.globalCOffsetNoise = new PerlinSimplexNoise(RandomSource.create(SEED + 3), List.of(1));
        BeyondEndChunkGenerator.biomeSimplexNoise = new HashSimplexNoise(RandomSource.create(SEED + 4));
        BeyondEndChunkGenerator.warpZSimplexNoise = new HashSimplexNoise(RandomSource.create(SEED + 5));
        BeyondTerrainStateInternal.setDimBounds(MIN_Y, MAX_Y);
    }

    @AfterEach
    void tearDown() {
        BeyondEndChunkGenerator.simplexNoise = sS; BeyondEndChunkGenerator.biomeSimplexNoise = sBS;
        BeyondEndChunkGenerator.warpZSimplexNoise = sWZ;
        BeyondEndChunkGenerator.globalHOffsetNoise = sH; BeyondEndChunkGenerator.globalVOffsetNoise = sV;
        BeyondEndChunkGenerator.globalCOffsetNoise = sC; BeyondEndChunkGenerator.activeTerrainParams = sP;
        BeyondEndChunkGenerator.seed = sSeed;
        BeyondTerrainStateInternal.setDimBounds(sMin, sMax);
    }

    @Test
    void queryMatchesEveryBlockScan() throws IOException {
        BeyondEndBiomeSource source = source();
        List<Holder<Biome>> all = new ArrayList<>(land);
        all.addAll(outer);
        all.addAll(inner);
        all.add(center);
        all.add(paths);
        Random rng = new Random(11);
        Map<String, Integer> cells = new TreeMap<>();
        Map<String, Integer> kinds = new TreeMap<>();
        int failures = 0;
        StringBuilder firstFailures = new StringBuilder();
        for (int i = 0; i < CASES; i++) {
            boolean flat = rng.nextInt(4) != 0;
            int radius = flat ? FLAT_RADII[rng.nextInt(FLAT_RADII.length)] : BALL_RADII[rng.nextInt(BALL_RADII.length)];
            int roll = rng.nextInt(20);
            BiomeEdgeQuery.Density mode = roll < 14 ? BiomeEdgeQuery.Density.READ
                    : roll < 17 ? BiomeEdgeQuery.Density.VOID : BiomeEdgeQuery.Density.LAND;
            BlockPos start = point(rng);
            BiomeEdgeQuery query = new BiomeEdgeQuery(source, manager(), HEIGHT, mode);
            Reference ref = new Reference(source, mode);
            Holder<Biome> home = query.biome(start);
            if (home != ref.biome(start)) {
                failures++;
                note(firstFailures, "start biome " + start + " query " + name(home) + " reference " + name(ref.biome(start)));
                continue;
            }
            int which = rng.nextInt(25);
            Holder<Biome> other = all.get(rng.nextInt(all.size()));
            while (other == home) other = all.get(rng.nextInt(all.size()));
            Holder<Biome> second = other;
            Predicate<Holder<Biome>> inside = which < 20 ? h -> h == home : which < 23 ? h -> h == home || h == second : h -> h == second;
            BiomeEdgeQuery.Shape shape = flat ? BiomeEdgeQuery.Shape.HORIZONTAL : BiomeEdgeQuery.Shape.SPHERE;
            BiomeEdgeQuery.Edge edge = query.nearestOutside(start, inside, shape, radius);
            long scan = ref.nearestOutside(start, inside, flat, radius);
            String bad = null;
            if (edge == null) {
                if (scan != Long.MAX_VALUE) bad = "query found no edge, scan found d2 " + scan;
            } else if (d2(edge.pos(), start) != scan) {
                bad = "query d2 " + d2(edge.pos(), start) + " at " + edge.pos() + ", scan d2 " + scan;
            } else if (inside.test(ref.biome(edge.pos()))) {
                bad = "returned block " + edge.pos() + " is inside";
            } else if (edge.biome() != ref.biome(edge.pos())) {
                bad = "returned biome " + name(edge.biome()) + " reference " + name(ref.biome(edge.pos()));
            }
            if (bad != null) {
                failures++;
                note(firstFailures, shape + " r" + radius + " " + mode + " from " + start + ": " + bad);
            }
            cells.merge(shape + " r" + radius + " " + mode, 1, Integer::sum);
            if (mode == BiomeEdgeQuery.Density.READ) kinds.merge(kind(source, start, edge), 1, Integer::sum);
        }
        try (PrintWriter w = writer("edge_accuracy.txt")) {
            w.println("cases " + CASES + " failures " + failures);
            w.println("cells " + cells);
            w.println("edge kinds (READ, every predicate) " + kinds);
            w.print(firstFailures);
        }
        Assertions.assertEquals(0, failures, firstFailures.toString());
        for (String k : List.of("zero", "cell", "density", "none")) {
            Assertions.assertTrue(kinds.getOrDefault(k, 0) >= 30, "VOID liveness: " + k + " " + kinds);
        }
        Assertions.assertTrue(kinds.getOrDefault("fixed", 0) >= 5, "VOID liveness: fixed " + kinds);
    }

    @Test
    void costPerShapeAndRadius() throws IOException {
        BeyondEndBiomeSource source = source();
        Random rng = new Random(3);
        try (PrintWriter w = writer("edge_cost.txt")) {
            for (int pass = 0; pass < 2; pass++) {
                for (boolean flat : new boolean[]{true, false}) {
                    for (int radius : flat ? new int[]{8, 16, 32, 48} : new int[]{8, 16, 32}) {
                        int n = 300;
                        long[] us = new long[n], quarts = new long[n], reads = new long[n];
                        int found = 0;
                        for (int i = 0; i < n; i++) {
                            BlockPos start = outerPoint(rng);
                            BiomeEdgeQuery query = new BiomeEdgeQuery(source, manager(), HEIGHT, BiomeEdgeQuery.Density.READ);
                            long t0 = System.nanoTime();
                            Holder<Biome> home = query.biome(start);
                            BiomeEdgeQuery.Edge e = query.nearestOutside(start, h -> h == home,
                                    flat ? BiomeEdgeQuery.Shape.HORIZONTAL : BiomeEdgeQuery.Shape.SPHERE, radius);
                            us[i] = (System.nanoTime() - t0) / 1000;
                            quarts[i] = query.quartsRead();
                            reads[i] = query.densityReads();
                            if (e != null) found++;
                        }
                        if (pass == 0) continue;
                        long naive = naiveQuarts(flat, radius);
                        w.printf("%s r%d: us %s | quarts %s | density %s | edge found %d of %d | naive scan %d quarts%n",
                                flat ? "HORIZONTAL" : "SPHERE", radius, spread(us), spread(quarts), spread(reads), found, n, naive);
                    }
                }
            }
        }
    }

    private BeyondEndBiomeSource source() {
        return new BeyondEndBiomeSource(land, List.of(), Optional.empty(), outer, inner, List.of(), center, paths,
                BeyondTerrainParams.DEFAULTS);
    }

    private static BiomeManager manager() {
        return new BiomeManager((x, y, z) -> null, BiomeManager.obfuscateSeed(SEED));
    }

    private static BlockPos point(Random rng) {
        int ring = rng.nextInt(20);
        int r = ring < 2 ? 650 + rng.nextInt(90) : ring < 3 ? 100 + rng.nextInt(30) : 740 + rng.nextInt(40_000);
        double a = rng.nextDouble() * Math.PI * 2;
        int band = rng.nextInt(20);
        int y = band < 2 ? MIN_Y + rng.nextInt(45) : band < 3 ? MAX_Y - 1 - rng.nextInt(9) : MIN_Y + rng.nextInt(MAX_Y - MIN_Y);
        return new BlockPos((int) Math.round(r * Math.cos(a)), y, (int) Math.round(r * Math.sin(a)));
    }

    private static BlockPos outerPoint(Random rng) {
        int r = 1000 + rng.nextInt(40_000);
        double a = rng.nextDouble() * Math.PI * 2;
        return new BlockPos((int) Math.round(r * Math.cos(a)), MIN_Y + 40 + rng.nextInt(MAX_Y - MIN_Y - 80),
                (int) Math.round(r * Math.sin(a)));
    }

    private static String kind(BeyondEndBiomeSource source, BlockPos start, BiomeEdgeQuery.Edge edge) {
        if (edge == null) return "none";
        if (edge.distance() == 0) return "zero";
        long[] a = quartOf(start), b = quartOf(edge.pos());
        int ax = (int) a[0] * 4, ay = (int) a[1] * 4, az = (int) a[2] * 4, bx = (int) b[0] * 4, by = (int) b[1] * 4, bz = (int) b[2] * 4;
        if (source.densityFreeBiome(ax, ay, az) != null || source.densityFreeBiome(bx, by, bz) != null) return "fixed";
        boolean cell = source.getCell(ax, ay, az).cellId() != source.getCell(bx, by, bz).cellId();
        boolean side = source.isVoid(ax, ay, az) != source.isVoid(bx, by, bz);
        return cell && side ? "cell+density" : cell ? "cell" : side ? "density" : "band";
    }

    private static long[] quartOf(BlockPos pos) {
        long[] q = new long[3];
        new BiomeManager((x, y, z) -> {
            q[0] = x;
            q[1] = Mth.clamp(y, MIN_Q, MAX_Q);
            q[2] = z;
            return null;
        }, BiomeManager.obfuscateSeed(SEED)).getBiome(pos);
        return q;
    }

    private static long naiveQuarts(boolean flat, int radius) {
        int reach = (radius + 5) / 4 + 1;
        long limit = (long) radius * radius, n = 0;
        for (int kx = -reach; kx <= reach; kx++) {
            for (int kz = -reach; kz <= reach; kz++) {
                if (least(kx) + least(kz) > limit) continue;
                if (flat) {
                    n += 2;
                    continue;
                }
                for (int ky = -reach; ky <= reach; ky++) {
                    if (least(kx) + least(kz) + least(ky) <= limit) n++;
                }
            }
        }
        return n;
    }

    private static long least(int k) {
        long g = Math.max(0, 4 * Math.abs(k) - 5);
        return g * g;
    }

    private static final class Reference {
        private final BeyondEndBiomeSource source;
        private final BiomeEdgeQuery.Density mode;
        private final Long2ObjectOpenHashMap<Holder<Biome>> memo = new Long2ObjectOpenHashMap<>();
        private final BiomeManager zoom;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Reference(BeyondEndBiomeSource source, BiomeEdgeQuery.Density mode) {
            this.source = source;
            this.mode = mode;
            this.zoom = new BiomeManager(this::quart, BiomeManager.obfuscateSeed(SEED));
        }

        private Holder<Biome> quart(int qx, int qy, int qz) {
            int y = Mth.clamp(qy, MIN_Q, MAX_Q);
            return memo.computeIfAbsent(BlockPos.asLong(qx, y, qz), k -> {
                if (mode == BiomeEdgeQuery.Density.READ) return source.getNoiseBiome(qx, y, qz, null);
                int bx = QuartPos.toBlock(qx), by = QuartPos.toBlock(y), bz = QuartPos.toBlock(qz);
                Holder<Biome> fixed = source.densityFreeBiome(bx, by, bz);
                return fixed != null ? fixed : source.sideBiome(bx, by, bz, mode == BiomeEdgeQuery.Density.VOID);
            });
        }

        Holder<Biome> biome(BlockPos pos) {
            return zoom.getBiome(pos);
        }

        long nearestOutside(BlockPos start, Predicate<Holder<Biome>> inside, boolean flat, int radius) {
            if (flat && (start.getY() < MIN_Y || start.getY() >= MAX_Y)) return Long.MAX_VALUE;
            int ylo = flat ? start.getY() : Math.max(MIN_Y, start.getY() - radius);
            int yhi = flat ? start.getY() : Math.min(MAX_Y - 1, start.getY() + radius);
            long limit = (long) radius * radius, best = Long.MAX_VALUE;
            for (int y = ylo; y <= yhi; y++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        long dy = y - start.getY();
                        long d2 = (long) dx * dx + dy * dy + (long) dz * dz;
                        if (d2 > limit || d2 >= best) continue;
                        if (!inside.test(biome(cursor.set(start.getX() + dx, y, start.getZ() + dz)))) best = d2;
                    }
                }
            }
            return best;
        }
    }

    private static long d2(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static String spread(long[] values) {
        long[] v = values.clone();
        Arrays.sort(v);
        return "p50 " + v[v.length / 2] + " p90 " + v[(int) (v.length * 0.9)] + " max " + v[v.length - 1];
    }

    private static void note(StringBuilder sb, String line) {
        if (sb.length() < 4000) sb.append(line).append('\n');
    }

    private static PrintWriter writer(String file) throws IOException {
        Files.createDirectories(OUT);
        return new PrintWriter(Files.newBufferedWriter(OUT.resolve(file)));
    }

    private static String name(Holder<Biome> h) {
        return h == null ? "null" : h.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    private static Holder<Biome> biome(String namespace, String name) {
        return Holder.Reference.createStandAlone(OWNER, ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(namespace, name)));
    }
}
