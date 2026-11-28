package com.thebeyond.common.worldgen;

import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.util.HashSimplexNoise;
import com.thebeyond.util.VoronoiNoise;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/** SubSites against the real biome source (world noises of one seed), the way a void biome feature would use it. */
class SubSitesSourceTest {

    private static final long SEED = -109715808474535652L;
    private static final int MIN_Y = 0, MAX_Y = 256;
    private static final HolderOwner<Biome> OWNER = new HolderOwner<>() {};
    private static final Path OUT = Path.of("build", "test-run");
    private static final List<String> LOG = new ArrayList<>();

    private final List<Holder<Biome>> land = List.of(biome("test", "land_a"), biome("test", "land_b"), biome("test", "land_c"));
    private final List<Holder<Biome>> outer = List.of(biome("minecraft", "the_void"), biome("test", "void_a"),
            biome("test", "void_b"));
    private final List<Holder<Biome>> inner = List.of(biome("test", "inner_a"), biome("test", "inner_b"));
    private final Holder<Biome> center = biome("test", "center");
    private final Holder<Biome> paths = biome("test", "paths");

    private HashSimplexNoise sS, sBS, sWZ;
    private PerlinSimplexNoise sH, sV, sC;
    private long sSeed;
    private int sMin, sMax;

    private static void log(String line) {
        System.out.println(line);
        LOG.add(line);
        try {
            Files.createDirectories(OUT);
            Files.write(OUT.resolve("subsites_source.txt"), LOG);
        } catch (IOException ignored) {
        }
    }

    @BeforeEach
    void setUp() {
        sS = BeyondEndChunkGenerator.simplexNoise;
        sBS = BeyondEndChunkGenerator.biomeSimplexNoise;
        sWZ = BeyondEndChunkGenerator.warpZSimplexNoise;
        sH = BeyondEndChunkGenerator.globalHOffsetNoise;
        sV = BeyondEndChunkGenerator.globalVOffsetNoise;
        sC = BeyondEndChunkGenerator.globalCOffsetNoise;
        sSeed = BeyondEndChunkGenerator.seed;
        sMin = BeyondTerrainState.getDimMinY();
        sMax = BeyondTerrainState.getDimMaxY();
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
        BeyondEndChunkGenerator.simplexNoise = sS;
        BeyondEndChunkGenerator.biomeSimplexNoise = sBS;
        BeyondEndChunkGenerator.warpZSimplexNoise = sWZ;
        BeyondEndChunkGenerator.globalHOffsetNoise = sH;
        BeyondEndChunkGenerator.globalVOffsetNoise = sV;
        BeyondEndChunkGenerator.globalCOffsetNoise = sC;
        BeyondEndChunkGenerator.seed = sSeed;
        BeyondTerrainStateInternal.setDimBounds(sMin, sMax);
    }

    private BeyondEndBiomeSource source() {
        return new BeyondEndBiomeSource(land, List.of(), Optional.empty(), outer, inner, List.of(), center, paths,
                BeyondTerrainParams.DEFAULTS);
    }

    private static Holder<Biome> biome(String namespace, String name) {
        return Holder.Reference.createStandAlone(OWNER, ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(namespace, name)));
    }

    private static Holder<Biome> voidSide(BeyondEndBiomeSource source, SubSites.Cell c) {
        int x = (int) Math.floor(c.x()), y = (int) Math.floor(c.y()), z = (int) Math.floor(c.z());
        Holder<Biome> fixed = source.densityFreeBiome(x, y, z);
        return fixed != null ? fixed : source.sideBiome(x, y, z, true);
    }

    /** Points of the given biome at 1500 to 2500 blocks from the origin, like the server windows. */
    private static List<BlockPos> centers(BeyondEndBiomeSource source, Holder<Biome> bog, int n, long seed) {
        Random r = new Random(seed);
        List<BlockPos> out = new ArrayList<>();
        while (out.size() < n) {
            double a = r.nextDouble() * 2 * Math.PI, d = 1500 + r.nextDouble() * 1000;
            BlockPos p = new BlockPos((int) (Math.cos(a) * d), 40 + r.nextInt(160), (int) (Math.sin(a) * d));
            if (source.getBiome(p.getX(), p.getY(), p.getZ()) == bog) out.add(p);
        }
        return out;
    }

    @Test
    void coldCostOfOneBog() throws IOException {
        BeyondEndBiomeSource source = source();
        Holder<Biome> bog = outer.get(1);
        SubSites s = new SubSites(new VoronoiNoise(SEED, (short) 0), 200, 200);
        List<BlockPos> centers = centers(source, bog, 40, 3);
        AtomicLong keepNanos = new AtomicLong(), keepCalls = new AtomicLong(), memberNanos = new AtomicLong(),
                memberCalls = new AtomicLong();
        Predicate<BlockPos> keep = p -> {
            long t0 = System.nanoTime();
            boolean in = source.getBiome(p.getX(), p.getY(), p.getZ()) == bog;
            keepNanos.addAndGet(System.nanoTime() - t0);
            keepCalls.incrementAndGet();
            return in;
        };
        Predicate<SubSites.Cell> member = c -> {
            long t0 = System.nanoTime();
            boolean in = voidSide(source, c) == bog;
            memberNanos.addAndGet(System.nanoTime() - t0);
            memberCalls.incrementAndGet();
            return in;
        };
        double spread = Math.sqrt(2) * 14 + 14;
        long[] total = new long[centers.size()], cells = new long[centers.size()], members = new long[centers.size()];
        long delaunayNanos = 0;
        int delaunaySets = 0, delaunayPoints = 0;
        Path jfr = OUT.resolve("subsites_cold.jfr");
        Files.createDirectories(OUT);
        try (Recording rec = new Recording()) {
            rec.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            rec.start();
            for (int i = 0; i < centers.size(); i++) {
                SubSites.clearGeometry();
                SubSites.Network net = s.network(new SubSites.Settings(BiomeEdgeQuery.Shape.SPHERE, SubSites.Count.PER_VOLUME,
                        60000, SubSites.Inner.DELAUNAY, SubSites.Outer.TREE, 0x5B5B5B5BL, keep, member, c -> -c.y()));
                BlockPos c = centers.get(i);
                long t0 = System.nanoTime();
                List<SubSites.Layout> got = gather(net, c, spread);
                total[i] = System.nanoTime() - t0;
                members[i] = got.size();
                cells[i] = seenCells;
                for (SubSites.Layout l : got) {
                    double[][] p = new double[l.subs().size() + 1][];
                    List<SubSites.Site> all = SubSites.sites(l);
                    for (int k = 0; k < p.length; k++) p[k] = new double[]{all.get(k).x(), all.get(k).y(), all.get(k).z()};
                    long d0 = System.nanoTime();
                    SubSites.delaunay(p);
                    delaunayNanos += System.nanoTime() - d0;
                    delaunaySets++;
                    delaunayPoints += p.length;
                }
            }
            rec.stop();
            rec.dump(jfr);
        }
        long sum = Arrays.stream(total).sum();
        log(String.format(java.util.Locale.ROOT, "cold bog gather, %d bogs (us p50/p90/max): %s | cells p50 %d max %d,"
                        + " members p50"
                        + " %d | keep %d calls %.1f ms (%.1f us each) | member %d calls %.1f ms | delaunay again %d sets of %.1f"
                        + " points %.1f ms | total %.1f ms", centers.size(), pct(total), med(cells), max(cells), med(members),
                keepCalls.get(), keepNanos.get() / 1e6, keepNanos.get() / 1e3 / Math.max(1, keepCalls.get()),
                memberCalls.get(), memberNanos.get() / 1e6, delaunaySets, delaunayPoints / (double) Math.max(1, delaunaySets),
                delaunayNanos / 1e6, sum / 1e6));

        keepCalls.set(0);
        keepNanos.set(0);
        long[] cold = new long[centers.size()], walked = new long[centers.size()], nSites = new long[centers.size()],
                nLinks = new long[centers.size()];
        long queryNanos = 0, atNanos = 0;
        int queries = 0, checks = 0, wrong = 0;
        Random r = new Random(5);
        try (Recording rec = new Recording()) {
            rec.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            rec.start();
            for (int i = 0; i < centers.size(); i++) {
                SubSites.clearGeometry();
                SubSites.Network net = s.network(new SubSites.Settings(BiomeEdgeQuery.Shape.SPHERE, SubSites.Count.PER_VOLUME,
                        60000, SubSites.Inner.DELAUNAY, SubSites.Outer.TREE, 0x5B5B5B5BL, keep, member, c -> -c.y()));
                BlockPos c = centers.get(i);
                double cx = c.getX() + 0.5, cy = c.getY() + 0.5, cz = c.getZ() + 0.5;
                long t0 = System.nanoTime();
                SubSites.Network.Region region = net.region(cx, cy, cz, spread, 400, 400);
                cold[i] = System.nanoTime() - t0;
                walked[i] = region.walked.size();
                nSites[i] = region.siteCount();
                nLinks[i] = region.linkCount();
                for (int q = 0; q < 600; q++) {
                    double x = cx + (r.nextDouble() * 2 - 1) * spread / Math.sqrt(3);
                    double y = cy + (r.nextDouble() * 2 - 1) * spread / Math.sqrt(3);
                    double z = cz + (r.nextDouble() * 2 - 1) * spread / Math.sqrt(3);
                    long q0 = System.nanoTime();
                    SubSites.Hit got = region.at(x, y, z);
                    queryNanos += System.nanoTime() - q0;
                    queries++;
                    if (q % 8 == 0) {
                        long a0 = System.nanoTime();
                        SubSites.Hit want = net.at(x, y, z, 400);
                        atNanos += System.nanoTime() - a0;
                        checks++;
                        if (Math.abs(got.siteDistance() - want.siteDistance()) > 1e-9
                                || Math.abs(got.linkDistance() - want.linkDistance()) > 1e-9) wrong++;
                    }
                }
            }
            rec.stop();
            rec.dump(OUT.resolve("subsites_region.jfr"));
        }
        log(String.format(java.util.Locale.ROOT, "region, same bogs, cold (us p50/p90/max): %s | cells walked p50 %d max %d,"
                        + " sites p50 %d, link parts p50 %d | keep %d calls %.1f ms | query %.2f us (at() %.1f us) | %d checks"
                        + " against at(), %d differ", pct(cold), med(walked), max(walked), med(nSites), med(nLinks),
                keepCalls.get(), keepNanos.get() / 1e6, queryNanos / 1e3 / queries, atNanos / 1e3 / Math.max(1, checks), checks,
                wrong));
        org.junit.jupiter.api.Assertions.assertEquals(0, wrong);
    }

    @Test
    void densityCost() {
        BeyondEndBiomeSource source = source();
        Random r = new Random(6);
        int n = 200000;
        double sink = 0;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 1500 + r.nextDouble() * 1000;
            sink += BeyondEndChunkGenerator.getTerrainDensity((int) (Math.cos(a) * d), r.nextInt(256), (int) (Math.sin(a) * d));
        }
        long t1 = System.nanoTime();
        int solid = 0;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 1500 + r.nextDouble() * 1000;
            if (source.getBiome((int) (Math.cos(a) * d), r.nextInt(256), (int) (Math.sin(a) * d)) == outer.get(1)) solid++;
        }
        long t2 = System.nanoTime();
        // a column walked in steps of one block, as a habitat search under islands would do
        long t3 = System.nanoTime();
        int under = 0;
        for (int c = 0; c < 2000; c++) {
            double a = r.nextDouble() * 2 * Math.PI, d = 1500 + r.nextDouble() * 1000;
            int x = (int) (Math.cos(a) * d), z = (int) (Math.sin(a) * d);
            boolean above = false;
            for (int y = MAX_Y - 1; y >= MIN_Y; y--) {
                boolean s = BeyondEndChunkGenerator.getTerrainDensity(x, y, z) >= 0.01f;
                if (above && !s) under++;
                above = s;
            }
        }
        long t4 = System.nanoTime();
        log(String.format(java.util.Locale.ROOT, "density %.2f us per call (sum %.1f), biome %.2f us per call (%d void_a),"
                        + " full column of 256 density reads %.0f us (%d island undersides in 2000 columns)",
                (t1 - t0) / 1e3 / n, sink, (t2 - t1) / 1e3 / n, solid, (t4 - t3) / 1e3 / 2000, under));
    }

    private int seenCells;

    /** Every member layout and outer link within reach of the bog at center. */
    private List<SubSites.Layout> gather(SubSites.Network net, BlockPos center, double spread) {
        double cx = center.getX() + 0.5, cy = center.getY() + 0.5, cz = center.getZ() + 0.5;
        SubSites.Hit hit = net.at(cx, cy, cz, 400);
        double range = Math.min(400, Math.max(hit.siteDistance(), hit.linkDistance())) + 2 * spread;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<SubSites.Cell> todo = new ArrayDeque<>();
        todo.add(hit.cell());
        seen.add(SubSites.Network.key(hit.cell()));
        List<SubSites.Layout> out = new ArrayList<>();
        while (!todo.isEmpty()) {
            SubSites.Cell c = todo.poll();
            SubSites.Layout l = net.layout(c);
            if (l != null) {
                out.add(l);
                net.outer(c);
            }
            for (SubSites.Cell n : net.geometry(c).neighbors) {
                if (!seen.add(SubSites.Network.key(n))) continue;
                double lb = Math.sqrt(Math.pow(n.x() - cx, 2) + Math.pow(n.y() - cy, 2) + Math.pow(n.z() - cz, 2))
                        - net.geometry(n).radius;
                if (lb <= range) todo.add(n);
            }
        }
        seenCells = seen.size();
        return out;
    }

    private static long med(long[] v) {
        long[] c = v.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }

    private static long max(long[] v) {
        return Arrays.stream(v).max().orElse(0);
    }

    private static String pct(long[] v) {
        long[] c = v.clone();
        Arrays.sort(c);
        return c[c.length / 2] / 1000 + "/" + c[c.length * 9 / 10] / 1000 + "/" + c[c.length - 1] / 1000;
    }
}
