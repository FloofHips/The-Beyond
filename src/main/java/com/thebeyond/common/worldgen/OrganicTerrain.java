package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.features.TerrainStepFeature;
import com.thebeyond.util.VoronoiNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.stream.IntStream;

import static com.thebeyond.common.worldgen.OrganicExperiment.HOME;
import static com.thebeyond.common.worldgen.OrganicExperiment.VOID_BOG;

/** The terrain a cell grows into, sampled column by column, with the step feature's cones stamped in. */
final class OrganicTerrain {
    private static final ResourceLocation STEP_FEATURE = ResourceLocation.fromNamespaceAndPath("the_beyond", "void_bog");
    // Solid and bog bits of whole chunks, shared by every cell box over them: both depend only on the world position.
    private static final Map<Long, FutureTask<long[]>> COLUMNS = new ConcurrentHashMap<>();
    private static final int COLUMN_CAP = 4096;
    private static final boolean COLUMN_CHECK = Boolean.getBoolean("beyond.sub.org.columnCheck");
    private static volatile long columnKey;
    static volatile Steps steps;

    private OrganicTerrain() {}

    /** Terrain from the generator's density and the bog from the biome source by quart, each chunk sampled once only. */
    static OrganicNetwork.Grid sample(BeyondEndBiomeSource source, int x0, int y0, int z0, int nx, int ny, int nz) {
        long key = BeyondEndChunkGenerator.seed * 31 + ((long) y0 << 20 | ny);
        if (columnKey != key) {
            COLUMNS.clear();
            columnKey = key;
        }
        int w = (ny + 63) >> 6, qy0 = y0 >> 2, qny = ((y0 + ny - 1) >> 2) - qy0 + 1, qw = (qny + 63) >> 6;
        int cx0 = x0 >> 4, cz0 = z0 >> 4, cnx = ((x0 + nx - 1) >> 4) - cx0 + 1, cnz = ((z0 + nz - 1) >> 4) - cz0 + 1;
        long[][] chunk = new long[cnx * cnz][];
        List<FutureTask<long[]>> mine = new ArrayList<>(), all = new ArrayList<>(chunk.length);
        for (int i = 0; i < chunk.length; i++) {
            int cx = cx0 + i / cnz, cz = cz0 + i % cnz;
            long k = ChunkPos.asLong(cx, cz);
            FutureTask<long[]> t = COLUMNS.get(k);
            if (t == null) {
                FutureTask<long[]> f = new FutureTask<>(() -> columns(source, cx, cz, y0, ny, qy0, qny));
                t = COLUMNS.putIfAbsent(k, f);
                if (t == null) {
                    t = f;
                    mine.add(f);
                }
            }
            all.add(t);
        }
        mine.parallelStream().forEach(FutureTask::run);
        try {
            for (int i = 0; i < chunk.length; i++) chunk[i] = all.get(i).get();
        } catch (Exception ex) {
            throw new IllegalStateException("column sampling failed", ex);
        }
        if (COLUMNS.size() > COLUMN_CAP) evictColumns(cx0 + cnx / 2, cz0 + cnz / 2);
        OrganicNetwork.Grid g = new OrganicNetwork.Grid(x0, y0, z0, nx, ny, nz);
        for (int x = 0; x < nx; x++) {
            int gx = x0 + x, cx = (gx >> 4) - cx0;
            for (int z = 0; z < nz; z++) {
                int gz = z0 + z;
                long[] c = chunk[cx * cnz + (gz >> 4) - cz0];
                int col = ((gx & 15) << 4 | gz & 15) * w, qcol = 256 * w + (((gx >> 2) & 3) << 2 | (gz >> 2) & 3) * qw;
                for (int y = 0; y < ny; y++) {
                    int qy = ((y0 + y) >> 2) - qy0;
                    g.set(x, y, z, (c[col + (y >> 6)] >>> y & 1) != 0, (c[qcol + (qy >> 6)] >>> qy & 1) != 0);
                }
            }
        }
        if (COLUMN_CHECK) {
            long t = System.nanoTime();
            boolean same = g.sameBits(sampleDirect(source, x0, y0, z0, nx, ny, nz));
            TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG column check box %d %d %d | chunks %d new %d cached %d"
                    + " | equal %s | check ms %d", x0, y0, z0, chunk.length, mine.size(), COLUMNS.size(), same,
                    (System.nanoTime() - t) / 1_000_000));
        }
        return g;
    }

    private static long[] columns(BeyondEndBiomeSource source, int cx, int cz, int y0, int ny, int qy0, int qny) {
        int w = (ny + 63) >> 6, qw = (qny + 63) >> 6;
        long[] c = new long[256 * w + 16 * qw];
        BeyondEndChunkGenerator.ColumnScratch s = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
        for (int lx = 0; lx < 16; lx++) {
            int gx = (cx << 4) + lx;
            for (int lz = 0; lz < 16; lz++) {
                int gz = (cz << 4) + lz;
                float d = (float) Math.sqrt((double) gx * gx + (double) gz * gz);
                if (d < 650) continue;
                BeyondEndChunkGenerator.initColumnScratch(gx, gz, d, s);
                int col = (lx << 4 | lz) * w;
                for (int y = 0; y < ny; y++) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y0 + y, s)) c[col + (y >> 6)] |= 1L << y;
                }
            }
        }
        for (int a = 0; a < 4; a++) {
            for (int b = 0; b < 4; b++) {
                int qcol = 256 * w + (a << 2 | b) * qw;
                for (int qy = 0; qy < qny; qy++) {
                    Holder<Biome> at = source.getBiome(((cx << 2) + a) << 2, (qy0 + qy) << 2, ((cz << 2) + b) << 2);
                    if (SubSitesExperiment.in(at, HOME)) {
                        c[qcol + (qy >> 6)] |= 1L << qy;
                    }
                }
            }
        }
        return c;
    }

    // Drops the chunks farthest from the box being sampled: growth moves with the generated area.
    private static synchronized void evictColumns(int cx, int cz) {
        List<Long> keys = new ArrayList<>(COLUMNS.keySet());
        keys.sort(Comparator.comparingLong(k -> -((long) (ChunkPos.getX(k) - cx) * (ChunkPos.getX(k) - cx)
                + (long) (ChunkPos.getZ(k) - cz) * (ChunkPos.getZ(k) - cz))));
        for (int i = 0; i < keys.size() - COLUMN_CAP * 3 / 4; i++) COLUMNS.remove(keys.get(i));
    }

    private static OrganicNetwork.Grid sampleDirect(BeyondEndBiomeSource source, int x0, int y0, int z0, int nx, int ny,
                                                    int nz) {
        OrganicNetwork.Grid g = new OrganicNetwork.Grid(x0, y0, z0, nx, ny, nz);
        int qx0 = x0 >> 2, qy0 = y0 >> 2, qz0 = z0 >> 2;
        int qnx = ((x0 + nx - 1) >> 2) - qx0 + 1, qny = ((y0 + ny - 1) >> 2) - qy0 + 1, qnz = ((z0 + nz - 1) >> 2) - qz0 + 1;
        boolean[] bog = new boolean[qnx * qny * qnz];
        IntStream.range(0, qnx).parallel().forEach(qx -> {
            for (int qy = 0; qy < qny; qy++) {
                for (int qz = 0; qz < qnz; qz++) {
                    bog[(qx * qny + qy) * qnz + qz] = SubSitesExperiment.in(source.getBiome((qx0 + qx) << 2, (qy0 + qy) << 2,
                            (qz0 + qz) << 2), HOME);
                }
            }
        });
        byte[][] solid = new byte[nx][];
        IntStream.range(0, nx).parallel().forEach(x -> {
            byte[] col = new byte[ny * nz];
            BeyondEndChunkGenerator.ColumnScratch s = BeyondEndChunkGenerator.PROBE_SCRATCH.get();
            int gx = x0 + x;
            for (int z = 0; z < nz; z++) {
                int gz = z0 + z;
                float d = (float) Math.sqrt((double) gx * gx + (double) gz * gz);
                if (d < 650) continue;
                BeyondEndChunkGenerator.initColumnScratch(gx, gz, d, s);
                for (int y = 0; y < ny; y++) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y0 + y, s)) col[y * nz + z] = 1;
                }
            }
            solid[x] = col;
        });
        for (int x = 0; x < nx; x++) {
            int qx = ((x0 + x) >> 2) - qx0;
            for (int y = 0; y < ny; y++) {
                int qy = ((y0 + y) >> 2) - qy0;
                for (int z = 0; z < nz; z++) {
                    int qz = ((z0 + z) >> 2) - qz0;
                    g.set(x, y, z, solid[x][y * nz + z] != 0, bog[(qx * qny + qy) * qnz + qz]);
                }
            }
        }
        return g;
    }

    /** What replaying the bog's step feature needs, set up the way ChunkGenerator.applyBiomeDecoration does. */
    record Steps(long seed, int index, PositionalRandomFactory region, BiomeManager biomes, int yMin, int yMax,
                 LevelHeightAccessor heights) {}

    static Steps steps(WorldGenLevel level, ChunkAccess chunk, BeyondEndBiomeSource source, ChunkGenerator gen) {
        Steps s = steps;
        if (s != null && s.seed() == level.getSeed()) return s;
        PlacedFeature placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(STEP_FEATURE);
        int index = -1;
        if (placed != null) {
            List<FeatureSorter.StepFeatureData> per = FeatureSorter.buildFeaturesPerStep(List.copyOf(source.possibleBiomes()),
                    b -> gen.getBiomeGenerationSettings(b).features(), true);
            if (!per.isEmpty()) index = per.get(0).features().indexOf(placed);
        }
        RandomState rs = level.getLevel().getChunkSource().randomState();
        WorldGenerationContext ctx = new WorldGenerationContext(gen, chunk);
        int qMin = QuartPos.fromBlock(level.getMinBuildHeight()), qMax = qMin + QuartPos.fromBlock(level.getHeight()) - 1;
        s = new Steps(level.getSeed(), index, rs.getOrCreateRandomFactory(ResourceLocation.withDefaultNamespace(
                "worldgen_region_random")), new BiomeManager((x, y, z) -> source.getNoiseBiome(x, Mth.clamp(y, qMin, qMax), z,
                rs.sampler()), BiomeManager.obfuscateSeed(level.getSeed())), VerticalAnchor.aboveBottom(32).resolveY(ctx),
                VerticalAnchor.belowTop(90).resolveY(ctx), LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()));
        TheBeyond.LOGGER.info("ORG steps: feature index {} in step 0, origins y {}..{}", index, s.yMin(), s.yMax());
        steps = s;
        return s;
    }

    /** Stamps the step feature's cones into the grid, whose union is known before the neighbours decorate. */
    static int stampSteps(OrganicNetwork.Grid g, Steps s, BeyondEndBiomeSource source, String dump) {
        if (s == null || s.index() < 0) return -1;
        int cx0 = (g.x0 >> 4) - 1, cx1 = ((g.x0 + g.nx - 1) >> 4) + 1, cz0 = (g.z0 >> 4) - 1, cz1 = ((g.z0 + g.nz - 1) >> 4) + 1;
        int w = cx1 - cx0 + 1, h = cz1 - cz0 + 1;
        int[][] parts = new int[w * h][];
        IntStream.range(0, w * h).parallel().forEach(i -> parts[i] = cones(s, source, cx0 + i / h, cz0 + i % h, g));
        int count = 0;
        for (int[] p : parts) {
            for (int k = 0; k < p.length; k += 3) g.set(p[k], p[k + 1], p[k + 2], true, false);
            count += p.length / 3;
        }
        if (dump != null) {
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(Path.of(dump)))) {
                out.writeInt(count);
                for (int[] p : parts) {
                    for (int k = 0; k < p.length; k += 3) {
                        out.writeInt(g.x0 + p[k]);
                        out.writeInt(g.y0 + p[k + 1]);
                        out.writeInt(g.z0 + p[k + 2]);
                    }
                }
            } catch (Exception ex) {
                TheBeyond.LOGGER.error("ORG_FAILURE step dump {}", dump, ex);
            }
        }
        return count;
    }

    private static int[] cones(Steps s, BeyondEndBiomeSource source, int cx, int cz, OrganicNetwork.Grid g) {
        WorldgenRandom r = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long deco = r.setDecorationSeed(s.seed(), cx << 4, cz << 4);
        r.setFeatureSeed(deco, s.index(), 0);
        RandomSource region = s.region().at(new BlockPos(cx << 4, 0, cz << 4));
        int[] out = new int[0];
        int n = 0;
        for (int c = 0; c < 5; c++) {
            int x = r.nextInt(16) + (cx << 4), z = r.nextInt(16) + (cz << 4);
            int y = s.yMin() > s.yMax() ? s.yMin() : Mth.randomBetweenInclusive(r, s.yMin(), s.yMax());
            BlockPos origin = new BlockPos(x, y, z);
            if (!s.biomes().getBiome(origin).is(VOID_BOG)) continue;
            VoronoiNoise.CellResult3D cell = source.getCell(origin.getX(), origin.getY(), origin.getZ());
            int groupX = Math.floorDiv(origin.getX() >> 4, 3), groupZ = Math.floorDiv(origin.getZ() >> 4, 3);
            RandomSource chunkRandom = RandomSource.create(groupX * 31L + groupZ * 17L);
            int yOffset = chunkRandom.nextInt(-5, 5);
            int fixedY = (int) (yOffset + (cell.cellY() / 4) * 4);
            int radius = region.nextInt(7, 13);
            SimplexNoise noise = new SimplexNoise(r);
            TerrainStepFeature.Fit fit = TerrainStepFeature.fit(s.heights(), s.biomes(), source, origin, fixedY, radius);
            if (fit == null) continue;
            int height = (int) Mth.lerp(fit.t(), 13, 7);
            for (int j = -radius; j <= radius; ++j) {
                for (int k = -radius; k <= radius; ++k) {
                    for (int i = 0; i >= -height; i--) {
                        int bx = origin.getX() + j, by = fixedY + i, bz = origin.getZ() + k;
                        double nv = noise.getValue(bx * 0.05, by * 0.01, bz * 0.05);
                        float rr = radius + i;
                        double noisy = Math.max(rr + (nv * 2), 0);
                        if (rr <= 0) break;
                        if ((float) (j * j + k * k) > noisy * noisy) continue;
                        int lx = bx - g.x0, ly = by - g.y0, lz = bz - g.z0;
                        if (!g.inside(lx, ly, lz)) continue;
                        if (n + 3 > out.length) out = Arrays.copyOf(out, Math.max(48, out.length * 2));
                        out[n++] = lx;
                        out[n++] = ly;
                        out[n++] = lz;
                    }
                }
            }
        }
        return Arrays.copyOf(out, n);
    }
}
