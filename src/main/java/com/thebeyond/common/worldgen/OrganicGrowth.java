package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.OrganicTerrain.Steps;
import com.thebeyond.common.worldgen.OrganicZoneLine.Line;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;

import static com.thebeyond.common.worldgen.OrganicExperiment.HALF;
import static com.thebeyond.common.worldgen.OrganicExperiment.HOME;
import static com.thebeyond.common.worldgen.OrganicExperiment.LINE;
import static com.thebeyond.common.worldgen.OrganicExperiment.style;

/** Each cell's network grown once off the generation queue and cached, and its atrophy once organs die. */
public final class OrganicGrowth {
    // Cells grow on these threads from the surface step on, so the feature step only waits for what is still growing.
    private static final int THREADS = Integer.getInteger("beyond.sub.org.threads",
            Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 2));
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ExecutorService POOL = THREADS <= 0 ? null : Executors.newFixedThreadPool(THREADS, r -> {
        Thread t = new Thread(r, "Beyond organic growth " + THREAD_ID.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    // cells covering the columns up to this many chunks around each chunk that reaches the surface step queue too.
    private static final int AHEAD = Math.max(0, Integer.getInteger("beyond.sub.org.ahead", 0));
    private static final Set<Long> AHEAD_SEEN = ConcurrentHashMap.newKeySet();
    static final Set<String> ATROPHY = Set.copyOf(Arrays.stream(System.getProperty("beyond.sub.org.atrophy",
            "root,strangler_fig,fungus,slime_mold").split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList());
    private static final boolean FLUX = Boolean.parseBoolean(System.getProperty("beyond.sub.org.v4", "true"));
    private static final String SETUP = System.getProperty("beyond.sub.org.setup", "auto");
    private static final String PRESET_TREE = System.getProperty("beyond.sub.org.preset.tree", "root");
    private static final String PRESET_MESH = System.getProperty("beyond.sub.org.preset.mesh", "slime_mold");
    private static final String BULGE = System.getProperty("beyond.sub.org.bulge");
    // faces each organism lives on: one mode for all, or per setup as "slime_mold=both,root=floor", ceiling where unset
    private static final Map<String, OrganicNetwork.Substrate> SUBSTRATE = substrates(
            System.getProperty("beyond.sub.org.substrate", "ceiling"));
    static final Map<Long, FutureTask<Grown>> FULL = new ConcurrentHashMap<>();
    private static final Map<Long, AtrophyJob> ATROPHIES = new ConcurrentHashMap<>();
    private static final Grown EMPTY = new Grown(0, 0, 0, 1, 1, 1, Map.of(), new int[0], new byte[0], new float[0],
            new int[0], new float[0], new float[0], new float[0], new float[0], new float[0], "", false, 0, new int[1],
            new int[0], new SubSites.Cell[0], new ConcurrentHashMap<>(), new int[0], new float[0], new byte[0], new BitSet());

    private OrganicGrowth() {}

    static Map<String, OrganicNetwork.Substrate> substrates(String spec) {
        Map<String, OrganicNetwork.Substrate> out = new HashMap<>();
        for (String kv : spec.split(",")) {
            String[] p = kv.trim().split("=");
            if (p[0].isEmpty()) continue;
            String mode = (p.length == 1 ? p[0] : p[1]).trim().toUpperCase(Locale.ROOT);
            out.put(p.length == 1 ? "" : p[0].trim(), OrganicNetwork.Substrate.valueOf(mode));
        }
        return Map.copyOf(out);
    }

    // a -Dbeyond.sub.org.substrate given on the command line wins over the presets
    private static final boolean SUBSTRATE_GIVEN = System.getProperty("beyond.sub.org.substrate") != null;

    static OrganicNetwork.Substrate substrate(String setup) {
        String name = setup.split(":", 2)[0];
        String v = SUBSTRATE_GIVEN ? null : OrganicPresets.value(name, "substrate");
        if (v != null) {
            try {
                return OrganicNetwork.Substrate.valueOf(v.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                TheBeyond.LOGGER.error("[Beyond] organic preset {}: substrate {} is not ceiling, floor or both", name, v);
            }
        }
        OrganicNetwork.Substrate s = SUBSTRATE.get(name);
        if (s == null) s = SUBSTRATE.get(OrganicPresets.base(name));
        return s != null ? s : SUBSTRATE.getOrDefault("", OrganicNetwork.Substrate.CEILING);
    }

    static boolean atrophies(String setup) {
        return OrganicPresets.flag(setup, "atrophy", ATROPHY);
    }

    /** One grown cell: its box, voxels by chunk, trunks for its children, zone line voxels from nNet on and each one's width. */
    record Grown(int x0, int y0, int z0, int nx, int ny, int nz, Map<Long, int[]> byChunk, int[] vox, byte[] kinds,
                         float[] dnet, int[] treeWorld, float[] treeD, float[] treeR, float[] treeA, float[] treeN,
                         float[] treeS, String setup, boolean linked, int nNet, int[] lineStart, int[] lineOf,
                         SubSites.Cell[] lineCells, Map<Long, Boolean> drawn, int[] owner, float[] organs, byte[] thick,
                         BitSet hidden) {}

    /** Full-growth voxels a cell loses once the organs in dead died, as sorted grid indices of its box. */
    record Atrophy(Set<Integer> dead, int[] gone, int x0, int y0, int z0, int ny, int nz) {
        boolean lost(int v) {
            return Arrays.binarySearch(gone, v) >= 0;
        }

        boolean lost(int x, int y, int z) {
            int dx = x - x0, dy = y - y0, dz = z - z0;
            return dx >= 0 && dy >= 0 && dz >= 0 && dy < ny && dz < nz && lost((dx * ny + dy) * nz + dz);
        }
    }

    private record AtrophyJob(Set<Integer> dead, FutureTask<Atrophy> task) {}

    /** Where cells grow: the bog's cell network, the biome source, the terrain steps and the world's height. */
    record Where(SubSites.Network net, BeyondEndBiomeSource source, Steps steps, int minY, int maxY) {
        static Where of(WorldGenLevel level, ChunkAccess chunk, BeyondEndBiomeSource source, ChunkGenerator gen) {
            int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
            Steps steps = OrganicTerrain.steps(level, chunk, source, gen);
            return new Where(SubSitesExperiment.network(source, HOME, minY, maxY), source, steps, minY, maxY);
        }
    }

    /** Climbs to the parent while this cell's network joined it. */
    static SubSites.Cell root(Where w, SubSites.Cell c) throws Exception {
        SubSites.Cell r = c;
        for (int i = 0; i < 256; i++) {
            SubSites.Cell p = w.net().parent(r);
            if (p == null) break;
            SubSites.Cell cur = r;
            if (!get(FULL, cur, () -> grow(w, cur, false)).linked()) break;
            r = p;
        }
        return r;
    }

    private static OrganicNetwork.Result withLine(OrganicNetwork.Result r, int[] line) {
        int n = r.voxels().length;
        int[] vox = Arrays.copyOf(r.voxels(), n + line.length);
        byte[] kinds = Arrays.copyOf(r.kinds(), n + line.length);
        float[] dnet = Arrays.copyOf(r.dnet(), n + line.length);
        int[] owner = Arrays.copyOf(r.owner(), n + line.length);
        byte[] thick = Arrays.copyOf(r.thick(), n + line.length);
        for (int i = 0; i < line.length; i++) {
            vox[n + i] = line[i];
            kinds[n + i] = LINE;
            owner[n + i] = -1;
        }
        return new OrganicNetwork.Result(vox, kinds, dnet, r.tree(), r.treeD(), r.treeR(), r.treeA(), r.treeN(), r.treeS(),
                r.report(), r.nodes(), r.linked(), owner, r.organs(), thick);
    }

    /** Member cells whose growth box (HALF around the core, full height) covers the column at cx, cz. */
    static List<SubSites.Cell> cellsCovering(Where w, double cx, double cz, double extra) {
        SubSites.Network net = w.net();
        int minY = w.minY(), maxY = w.maxY();
        List<SubSites.Cell> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<SubSites.Cell> todo = new ArrayDeque<>();
        for (int y = minY + 16; y < maxY; y += 64) {
            SubSites.Cell c = net.at(cx, y, cz, 1).cell();
            if (seen.add(SubSites.Network.key(c))) todo.add(c);
        }
        while (!todo.isEmpty()) {
            SubSites.Cell c = todo.poll();
            if (Math.abs(c.x() - cx) <= HALF + 8 + extra && Math.abs(c.z() - cz) <= HALF + 8 + extra
                    && net.layout(c) != null) {
                out.add(c);
            }
            for (SubSites.Cell nb : net.geometry(c).neighbors) {
                if (!seen.add(SubSites.Network.key(nb))) continue;
                double lb = Double.MAX_VALUE;
                for (int y = minY; y <= maxY; y += 32) lb = Math.min(lb, net.geometry(nb).lowerBound(cx, y, cz));
                if (lb <= 2 * HALF + extra) todo.add(nb);
            }
        }
        out.sort((a, b) -> Long.compare(SubSites.Network.key(a), SubSites.Network.key(b)));
        return out;
    }

    private interface Job {
        Grown run();
    }

    static Grown full(Where w, SubSites.Cell c) throws Exception {
        return get(FULL, c, () -> grow(w, c, false));
    }

    static Grown get(Map<Long, FutureTask<Grown>> map, SubSites.Cell c, Job job) throws Exception {
        long k = SubSites.Network.key(c);
        FutureTask<Grown> t = map.get(k);
        if (t == null) {
            FutureTask<Grown> mine = new FutureTask<>(job::run);
            t = map.putIfAbsent(k, mine);
            if (t == null) t = mine;
        }
        // Runs here unless a growth thread already took it, so nobody waits on a growth still queued behind busy threads.
        t.run();
        return t.get();
    }

    /** The cell's atrophy for its dead organs, grown once per set of dead organs and shared by every caller. */
    static Atrophy atrophied(Where w, SubSites.Cell c, Set<Integer> dead) throws Exception {
        Set<Integer> now = Set.copyOf(dead);
        AtrophyJob j = ATROPHIES.compute(SubSites.Network.key(c), (k, old) -> old != null && old.dead().equals(now) ? old
                : new AtrophyJob(now, new FutureTask<>(() -> atrophy(w, c, now))));
        j.task().run();
        return j.task().get();
    }

    private static Atrophy atrophy(Where w, SubSites.Cell c, Set<Integer> dead) throws Exception {
        Grown full = get(FULL, c, () -> grow(w, c, false));
        IntArrayList nodes = new IntArrayList();
        for (int o : dead) {
            if (o >= 0 && (o + 1) * OrganicNetwork.ORGAN <= full.organs().length) {
                nodes.add((int) full.organs()[o * OrganicNetwork.ORGAN + 7]);
            }
        }
        Grown thin = grow(w, c, false, nodes.toIntArray());
        IntOpenHashSet kept = new IntOpenHashSet(thin.vox());
        IntArrayList gone = new IntArrayList();
        for (int s = 0; s < full.vox().length; s++) {
            int v = full.vox()[s];
            if (full.kinds()[s] != LINE && !dead.contains(full.owner()[s]) && !kept.contains(v)) gone.add(v);
        }
        int[] lost = gone.toIntArray();
        Arrays.sort(lost);
        TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG atrophy cell %d %d %d | dead organs %s | voxels lost %d of %d",
                c.lx(), c.ly(), c.lz(), new TreeSet<>(dead), lost.length, full.vox().length));
        return new Atrophy(dead, lost, full.x0(), full.y0(), full.z0(), full.ny(), full.nz());
    }

    /** Grows the cell's atrophy off the server thread and hands it to done on the server thread. */
    static void atrophyLater(ServerLevel level, ChunkAccess chunk, long cell, double x, double z, Set<Integer> dead,
                             Consumer<Atrophy> done) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        if (!(gen.getBiomeSource() instanceof BeyondEndBiomeSource source)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                Where w = Where.of(level, chunk, source, gen);
                for (SubSites.Cell a : cellsCovering(w, x, z, 0)) {
                    if (SubSites.Network.key(a) == cell) return atrophied(w, a, dead);
                }
                return null;
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }, POOL != null ? POOL : ForkJoinPool.commonPool()).whenComplete((a, ex) -> level.getServer().execute(() -> {
            if (ex != null) TheBeyond.LOGGER.error("ORG_FAILURE atrophy cell {}", cell, ex);
            else if (a != null) done.accept(a);
        }));
    }

    static boolean atrophyFor(long cell, Set<Integer> dead) {
        AtrophyJob j = ATROPHIES.get(cell);
        return j != null && j.dead().equals(dead);
    }

    static boolean lostNow(long cell, Set<Integer> dead, int x, int y, int z) {
        AtrophyJob j = ATROPHIES.get(cell);
        if (j == null || !j.task().isDone() || !j.dead().equals(dead)) return false;
        try {
            return j.task().get().lost(x, y, z);
        } catch (Exception ex) {
            return false;
        }
    }

    static void forget() {
        FULL.clear();
        OrganicLab.TWIN.clear();
        ATROPHIES.clear();
        AHEAD_SEEN.clear();
    }

    /** Queues every cell this chunk will paint, parents first, to grow off the generation queue (AHEAD: columns 8 apart). */
    public static void prefetch(WorldGenLevel level, ChunkAccess chunk, BeyondEndBiomeSource source, ChunkGenerator gen) {
        if (POOL == null) return;
        ChunkPos cp = chunk.getPos();
        try {
            Where w = Where.of(level, chunk, source, gen);
            double extra = !OrganicLight.SHY.isEmpty() && OrganicLight.HALO > 0 ? OrganicHalo.reach() : 0;
            List<SubSites.Cell> cells = new ArrayList<>(cellsCovering(w, cp.getMinBlockX() + 8, cp.getMinBlockZ() + 8, extra));
            for (int ax = Math.floorDiv(cp.x - AHEAD, 8) * 8; AHEAD > 0 && ax <= cp.x + AHEAD; ax += 8) {
                for (int az = Math.floorDiv(cp.z - AHEAD, 8) * 8; az <= cp.z + AHEAD; az += 8) {
                    if (AHEAD_SEEN.add(ChunkPos.asLong(ax, az))) {
                        cells.addAll(cellsCovering(w, (ax << 4) + 8, (az << 4) + 8, extra));
                    }
                }
            }
            for (SubSites.Cell c : cells) {
                List<SubSites.Cell> line = new ArrayList<>();
                for (SubSites.Cell a = c; a != null && line.size() < 256; a = readParent(w.net(), a)) line.add(a);
                for (int i = line.size() - 1; i >= 0; i--) {
                    SubSites.Cell a = line.get(i);
                    long k = SubSites.Network.key(a);
                    if (FULL.containsKey(k)) continue;
                    FutureTask<Grown> t = new FutureTask<>(() -> grow(w, a, false));
                    if (FULL.putIfAbsent(k, t) != null) continue;
                    POOL.execute(t);
                    TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG prefetch cell %d %d %d | chunk %d %d", a.lx(), a.ly(),
                            a.lz(), cp.x, cp.z));
                }
            }
        } catch (Throwable ex) {
            TheBeyond.LOGGER.error("ORG_FAILURE prefetch chunk {} {}", cp.x, cp.z, ex);
        }
    }

    /** The parent whose growth this cell's growth reads, or null. */
    private static SubSites.Cell readParent(SubSites.Network net, SubSites.Cell c) {
        SubSites.Cell p = net.parent(c);
        return p != null && net.layout(p) != null ? p : null;
    }

    /** The cell's network in its box, linked to its parent's trunks, the twin using the other Tero solver and parent twin. */
    static Grown grow(Where w, SubSites.Cell c, boolean twin) {
        return grow(w, c, twin, null);
    }

    static Grown grow(Where w, SubSites.Cell c, boolean twin, int[] deadNodes) {
        SubSites.Network net = w.net();
        BeyondEndBiomeSource source = w.source();
        Steps steps = w.steps();
        int minY = w.minY(), maxY = w.maxY();
        long t0 = System.nanoTime();
        SubSites.Layout l = net.layout(c);
        if (l == null) return EMPTY;
        int x0 = (int) Math.floor(c.x() - HALF), z0 = (int) Math.floor(c.z() - HALF), n = (int) (2 * HALF);
        OrganicNetwork.Grid g = OrganicTerrain.sample(source, x0, minY, z0, n, maxY - minY, n);
        String dump = OrganicLab.DUMP ? String.format(Locale.ROOT, "org_steps_%d_%d_%d.bin", c.lx(), c.ly(), c.lz()) : null;
        int cones = OrganicTerrain.stampSteps(g, steps, source, dump);
        long t1 = System.nanoTime();
        List<SubSites.Cell> around = new ArrayList<>();
        around.add(c);
        SubSites.Cell parent = net.parent(c);
        for (SubSites.Cell nb : net.geometry(c).neighbors) around.add(nb);
        List<OrganicNetwork.Core> cores = new ArrayList<>();
        int parentIndex = -1;
        for (int i = 0; i < around.size(); i++) {
            if (parent != null && around.get(i).equals(parent)) parentIndex = i;
        }
        for (int i = 0; i < around.size(); i++) {
            SubSites.Cell a = around.get(i);
            cores.add(new OrganicNetwork.Core(SubSites.Network.key(a), a.x() - x0, a.y() - minY, a.z() - z0,
                    i == 0 ? parentIndex : -1, i == 0));
        }
        int[] ext = null;
        float[] extD = null, extR = null, extA = null, extN = null, extS = null;
        double coreD = Double.NaN;
        if (parent != null && parentIndex >= 0 && net.layout(parent) != null) {
            try {
                Grown p = get(twin ? OrganicLab.TWIN : FULL, parent, () -> grow(w, parent, twin));
                List<Integer> v = new ArrayList<>();
                List<Float> d = new ArrayList<>(), rr = new ArrayList<>(), aa = new ArrayList<>(), nn = new ArrayList<>();
                List<Float> ss = new ArrayList<>();
                for (int i = 0; i < p.treeWorld().length; i += 3) {
                    int x = p.treeWorld()[i] - x0, y = p.treeWorld()[i + 1] - minY, z = p.treeWorld()[i + 2] - z0;
                    if (!g.inside(x, y, z)) continue;
                    v.add(g.index(x, y, z));
                    d.add(p.treeD()[i / 3]);
                    rr.add(p.treeR() != null ? p.treeR()[i / 3] : Float.NaN);
                    aa.add(p.treeA() != null ? p.treeA()[i / 3] : Float.NaN);
                    nn.add(p.treeN() != null ? p.treeN()[i / 3] : Float.NaN);
                    ss.add(p.treeS() != null ? p.treeS()[i / 3] : Float.NaN);
                }
                ext = v.stream().mapToInt(Integer::intValue).toArray();
                extD = new float[d.size()];
                extR = new float[d.size()];
                extA = new float[d.size()];
                extN = new float[d.size()];
                extS = new float[d.size()];
                for (int i = 0; i < extD.length; i++) {
                    extD[i] = d.get(i);
                    extR[i] = rr.get(i);
                    extA[i] = aa.get(i);
                    extN[i] = nn.get(i);
                    extS[i] = ss.get(i);
                }
                coreD = Double.POSITIVE_INFINITY;
                for (float x : p.treeD()) coreD = Math.min(coreD, x);
            } catch (Exception ex) {
                TheBeyond.LOGGER.error("ORG_FAILURE parent of cell {}", SubSites.Network.key(c), ex);
            }
        }
        OrganicNetwork.Style style = style(c);
        String setup = SETUP.equals("auto") ? (style == OrganicNetwork.Style.MESH ? PRESET_MESH : PRESET_TREE) : SETUP.equals("v4")
                ? "" : SETUP;
        String forced = setup.isEmpty() ? null : OrganicPresets.value(setup, "style");
        if (forced != null) style = OrganicNetwork.Style.valueOf(forced.trim().toUpperCase(Locale.ROOT));
        OrganicNetwork.Settings st = setup.isEmpty() ? OrganicNetwork.Settings.of(style, BeyondEndChunkGenerator.seed ^ 0x6F726761L,
                FLUX, setup, substrate(setup)) : OrganicPresets.settings(setup, style, BeyondEndChunkGenerator.seed ^ 0x6F726761L,
                FLUX, substrate(setup));
        if (BULGE != null) st = OrganicPresets.bulge(st, BULGE);
        OrganicLab.keepNodes = OrganicLab.DUMP;
        OrganicNetwork.Parent up = new OrganicNetwork.Parent(ext, extD, coreD, extR, extA, extN, extS);
        OrganicNetwork.Result r = OrganicNetwork.grow(g, st, cores, up, twin != OrganicNetwork.IC0, deadNodes);
        int nNet = r.voxels().length;
        long tl = System.nanoTime();
        Line line = OrganicZoneLine.zoneLine(setup) && deadNodes == null
                ? OrganicZoneLine.zoneLine(g, st, net, c, x0, minY, z0, r.voxels()) : OrganicZoneLine.NO_LINE;
        tl = System.nanoTime() - tl;
        if (line.vox().length > 0) r = withLine(r, line.vox());
        if (OrganicLab.DUMP && !twin && r.nodes() != null) {
            String f = String.format(Locale.ROOT, "org_nodes_%d_%d_%d.bin", c.lx(), c.ly(), c.lz());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(Path.of(f)))) {
                out.writeInt(r.nodes().length / 8);
                out.writeInt(x0);
                out.writeInt(minY);
                out.writeInt(z0);
                out.writeInt(st.style() == OrganicNetwork.Style.MESH ? 1 : 0);
                for (float x : r.nodes()) out.writeFloat(x);
            } catch (Exception ex) {
                TheBeyond.LOGGER.error("ORG_FAILURE node dump {}", f, ex);
            }
        }
        long t2 = System.nanoTime();
        Map<Long, List<Integer>> buckets = new HashMap<>();
        int plane = g.ny * g.nz;
        for (int s = 0; s < r.voxels().length; s++) {
            int i = r.voxels()[s];
            int x = x0 + i / plane, z = z0 + i % g.nz;
            buckets.computeIfAbsent(ChunkPos.asLong(x >> 4, z >> 4), k -> new ArrayList<>()).add(s);
        }
        Map<Long, int[]> byChunk = new HashMap<>();
        for (Map.Entry<Long, List<Integer>> e : buckets.entrySet()) {
            byChunk.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }
        int[] tw = new int[r.tree().length * 3];
        for (int k = 0; k < r.tree().length; k++) {
            int i = r.tree()[k];
            tw[k * 3] = x0 + i / plane;
            tw[k * 3 + 1] = minY + (i / g.nz) % g.ny;
            tw[k * 3 + 2] = z0 + i % g.nz;
        }
        TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG cell %d %d %d %s %s %s | sample %d ms grow %d ms | step cones %d"
                        + " voxels | %d voxels, parent trunks %d, zone line %d (%d organisms, %d ms) | %s", c.lx(), c.ly(),
                c.lz(), twin ? "twin" : "full", st.style(), st.bio().on() ? "v6 " + setup : st.flux() ? "v4" : "v3",
                (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, cones, r.voxels().length,
                ext == null ? 0 : ext.length, line.vox().length, line.cells().length, tl / 1_000_000, r.report()));
        if (OrganicLab.DUMP_GROWN && deadNodes == null) OrganicLab.dumpGrown(c, parent, twin, x0, minY, z0, g, r, ext, nNet);
        Grown out = new Grown(x0, minY, z0, g.nx, g.ny, g.nz, byChunk, r.voxels(), r.kinds(), r.dnet(), tw, r.treeD(), r.treeR(),
                r.treeA(), r.treeN(), r.treeS(), setup, r.linked(), nNet, line.start(), line.of(), line.cells(),
                new ConcurrentHashMap<>(), r.owner(), r.organs(), r.thick(),
                OrganicHalo.hidden(r.voxels(), r.kinds(), r.owner(), r.thick(), LINE, g.nx, g.ny, g.nz));
        if (OrganicLab.TERO_CHECK && !twin && deadNodes == null && st.style() == OrganicNetwork.Style.MESH) {
            try {
                OrganicLab.compareTwin(c, out, get(OrganicLab.TWIN, c, () -> grow(w, c, true)));
            } catch (Exception ex) {
                TheBeyond.LOGGER.error("ORG_FAILURE twin of cell {}", SubSites.Network.key(c), ex);
            }
        }
        return out;
    }
}
