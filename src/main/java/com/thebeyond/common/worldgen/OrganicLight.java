package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** What the light of a lit bonfire does to the light-shy organic network, and which spawned bonfires are lit. */
public final class OrganicLight {
    public enum Action { KEEP, REMOVE, ROCK, BREAKABLE }

    /** Setups whose network flees the light and corrupts the biome around it. */
    public static final Set<String> SHY = Arrays.stream(System.getProperty("beyond.sub.org.light", "slime_mold").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    public static final double RADIUS = Double.parseDouble(System.getProperty("beyond.sub.org.light.r", "8"));
    public static final double JITTER = Double.parseDouble(System.getProperty("beyond.sub.org.light.jitter", "2"));
    public static final double HALO = Double.parseDouble(System.getProperty("beyond.sub.org.halo", "6"));
    public static final String RELEASED = System.getProperty("beyond.sub.org.released", "genuine");
    public static final boolean EDGE_LIT = Boolean.parseBoolean(System.getProperty("beyond.sub.org.edgeLit", "true"));
    private static final double EDGE_A = Double.parseDouble(System.getProperty("beyond.sub.org.edgeLit.a", "0.7105"));
    private static final double EDGE_L = Double.parseDouble(System.getProperty("beyond.sub.org.edgeLit.L", "12"));
    private static final int EDGE_MAX = 48;
    // farthest a zone reaches from the pillar axis, used to pick the bonfires that can touch a chunk
    static final double REACH = RADIUS + Math.abs(JITTER) + 1.5;
    private static final ResourceKey<Structure> BONFIRE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("the_beyond", "bonfire"));
    private static final Map<Byte, Action> ACTIONS = actions(System.getProperty("beyond.sub.org.light.kinds",
            "tube=remove,film=remove,tunnel=rock,bulb=breakable"));
    private static final Map<Long, Bonfire> DECIDED = new ConcurrentHashMap<>();
    private static final Set<Long> NO_FIRE = ConcurrentHashMap.newKeySet();
    // extra lit bonfires with no structure (axis x, foot, fire, z per entry), to try the clearing on any network
    private static final List<Bonfire> EXTRA = extra(System.getProperty("beyond.sub.org.light.extra", ""));
    private static volatile long noiseSeed = Long.MIN_VALUE;
    private static volatile SimplexNoise noise;

    /** A spawned bonfire: the pillar axis at fx, fz from footY up to the fire block at fy. */
    public record Bonfire(long key, int fx, int fy, int fz, int footY, boolean templateLit, boolean lit, double edge,
                          double chance, boolean virtual) {
        /** Inside the clearing: within 8 +- 2 (smooth 3D noise) of the pillar's axis, from its foot to the fire. */
        public boolean reaches(double x, double y, double z) {
            double ax = fx + 0.5, az = fz + 0.5;
            double cy = Math.max(footY + 0.5, Math.min(fy + 0.5, y));
            double dx = x - ax, dy = y - cy, dz = z - az;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > REACH * REACH) return false;
            double r = RADIUS + JITTER * Math.max(-1, Math.min(1, noise().getValue(x / 10, y / 10, z / 10)));
            return d2 <= r * r;
        }

        /** Distance to the pillar's axis over the clearing's radius there, at most 1 inside the clearing. */
        public double ratio(double x, double y, double z) {
            double ax = fx + 0.5, az = fz + 0.5;
            double cy = Math.max(footY + 0.5, Math.min(fy + 0.5, y));
            double dx = x - ax, dy = y - cy, dz = z - az;
            double r = RADIUS + JITTER * Math.max(-1, Math.min(1, noise().getValue(x / 10, y / 10, z / 10)));
            return Math.sqrt(dx * dx + dy * dy + dz * dz) / r;
        }

        public Bonfire asLit() {
            return lit ? this : new Bonfire(key, fx, fy, fz, footY, templateLit, true, edge, chance, virtual);
        }
    }

    // bonfires players lit by hand, by fire block: worldgen reads them, so chunks generated later are born cleared
    private static final Map<Long, Bonfire> HAND_LIT = new ConcurrentHashMap<>();

    public static void handLit(Bonfire b) {
        HAND_LIT.put(BlockPos.asLong(b.fx(), b.fy(), b.fz()), b.asLit());
    }

    public static void dropHandLit(long fire) {
        HAND_LIT.remove(fire);
    }

    public static void clearHandLit() {
        HAND_LIT.clear();
    }

    /** A hand-lit bonfire read back from saved data, its pillar from foot to fire. */
    public static Bonfire litAt(int fx, int fy, int fz, int footY) {
        return new Bonfire(BlockPos.asLong(fx, fy, fz), fx, fy, fz, footY, false, true, Double.NaN, Double.NaN, true);
    }

    /** The bonfire whose fire block this is: a spawned one with its pillar, or one a player placed, with no pillar. */
    public static Bonfire forFire(WorldGenLevel level, BlockPos fire, BeyondEndBiomeSource source) {
        for (Bonfire b : near(level, new ChunkPos(fire), source)) {
            if (b.fx() == fire.getX() && b.fy() == fire.getY() && b.fz() == fire.getZ()) return b;
        }
        return new Bonfire(fire.asLong(), fire.getX(), fire.getY(), fire.getZ(), fire.getY(), false, false, Double.NaN,
                Double.NaN, true);
    }

    private OrganicLight() {}

    public static boolean shy(String setup) {
        return OrganicPresets.flag(setup, "light.shy", SHY);
    }

    public static Action action(byte kind) {
        return ACTIONS.getOrDefault(kind, Action.KEEP);
    }

    private static Map<Byte, Action> actions(String spec) {
        Map<String, Byte> names = Map.of("tube", OrganicNetwork.TUBE, "bulb", OrganicNetwork.BULB, "film", OrganicNetwork.FILM,
                "cross", OrganicNetwork.CROSS, "aerial", OrganicNetwork.AERIAL, "pendant", OrganicNetwork.PENDANT,
                "tunnel", OrganicNetwork.TUNNEL);
        Map<Byte, Action> out = new HashMap<>();
        for (String kv : spec.split(",")) {
            String[] p = kv.trim().split("=");
            Byte k = names.get(p[0]);
            if (k == null || p.length != 2) throw new IllegalArgumentException("unknown light rule " + kv);
            out.put(k, Action.valueOf(p[1].toUpperCase(Locale.ROOT)));
        }
        // cross links, aerial roots and pendants are tubes unless a rule names them
        Action tube = out.getOrDefault(OrganicNetwork.TUBE, Action.KEEP);
        for (byte k : new byte[]{OrganicNetwork.CROSS, OrganicNetwork.AERIAL, OrganicNetwork.PENDANT}) out.putIfAbsent(k, tube);
        return Map.copyOf(out);
    }

    private static SimplexNoise noise() {
        long seed = BeyondEndChunkGenerator.seed;
        if (noise == null || noiseSeed != seed) {
            synchronized (OrganicLight.class) {
                if (noise == null || noiseSeed != seed) {
                    noise = new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x6C69676874L));
                    noiseSeed = seed;
                }
            }
        }
        return noise;
    }

    /** Spawned bonfires within two chunks, the farthest whose clearing can change a block or quart of this chunk. */
    public static List<Bonfire> near(WorldGenLevel level, ChunkPos cp, BeyondEndBiomeSource source) {
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure bonfire = reg.get(BONFIRE);
        List<Bonfire> out = new ArrayList<>();
        if (bonfire == null) return out;
        BiomeEdgeQuery query = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                ChunkAccess c = level.getChunk(cp.x + dx, cp.z + dz);
                StructureStart start = c.getStartForStructure(bonfire);
                if (start == null || !start.isValid()) continue;
                long key = start.getChunkPos().toLong() ^ BeyondEndChunkGenerator.seed * 0x9E3779B97F4A7C15L;
                if (NO_FIRE.contains(key)) continue;
                Bonfire b = DECIDED.get(key);
                if (b == null) {
                    if (query == null) query = new BiomeEdgeQuery(source, level.getBiomeManager(), level,
                            BiomeEdgeQuery.Density.READ);
                    b = decide(start, key, query);
                    if (b == null) {
                        if (NO_FIRE.add(key)) TheBeyond.LOGGER.warn("ORG bonfire start {} has no fire piece, left out",
                                start.getChunkPos());
                        continue;
                    }
                    if (DECIDED.size() > 100_000) DECIDED.clear();
                    DECIDED.putIfAbsent(key, b);
                }
                out.add(HAND_LIT.containsKey(BlockPos.asLong(b.fx(), b.fy(), b.fz())) ? b.asLit() : b);
            }
        }
        for (Bonfire b : EXTRA) {
            if (Math.abs((b.fx() >> 4) - cp.x) <= 2 && Math.abs((b.fz() >> 4) - cp.z) <= 2) out.add(b);
        }
        for (Bonfire b : HAND_LIT.values()) {
            if (Math.abs((b.fx() >> 4) - cp.x) > 2 || Math.abs((b.fz() >> 4) - cp.z) > 2) continue;
            boolean known = false;
            for (Bonfire o : out) known |= o.fx() == b.fx() && o.fy() == b.fy() && o.fz() == b.fz();
            if (!known) out.add(b);
        }
        return out;
    }

    private static List<Bonfire> extra(String spec) {
        List<Bonfire> out = new ArrayList<>();
        for (String e : spec.split(";")) {
            if (e.isBlank()) continue;
            String[] v = e.trim().split(",");
            out.add(new Bonfire(-1 - out.size(), Integer.parseInt(v[0]), Integer.parseInt(v[2]), Integer.parseInt(v[3]),
                    Integer.parseInt(v[1]), false, true, Double.NaN, Double.NaN, true));
        }
        return List.copyOf(out);
    }

    /** Pillar foot and fire from the start's pieces, lit by the horizontal edge distance at the foot when edgeLit is on. */
    private static Bonfire decide(StructureStart start, long key, BiomeEdgeQuery query) {
        int footY = Integer.MAX_VALUE, fx = 0, fy = Integer.MIN_VALUE, fz = 0;
        boolean templateLit = false, fire = false;
        for (StructurePiece p : start.getPieces()) {
            BoundingBox bb = p.getBoundingBox();
            footY = Math.min(footY, bb.minY());
            if (p instanceof PoolElementStructurePiece pe && pe.getElement().toString().contains("bonfire/bonfire_")) {
                fire = true;
                fx = bb.minX();
                fy = bb.maxY();
                fz = bb.minZ();
                templateLit = pe.getElement().toString().contains("bonfire_lit");
            }
        }
        if (!fire) return null;
        if (!EDGE_LIT) return new Bonfire(key, fx, fy, fz, footY, templateLit, templateLit, Double.NaN, Double.NaN, false);
        BlockPos foot = new BlockPos(fx, footY, fz);
        Holder<Biome> here = query.biome(foot);
        BiomeEdgeQuery.Edge e = query.nearestOutside(foot, h -> h.equals(here), BiomeEdgeQuery.Shape.HORIZONTAL, EDGE_MAX);
        double d = e == null ? EDGE_MAX : e.distance();
        double chance = Math.min(1, EDGE_A * Math.exp(-d / EDGE_L));
        long h = key * 0xD1B54A32D192ED03L;
        h ^= h >>> 31;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        boolean lit = (h >>> 11) * 0x1.0p-53 < chance;
        return new Bonfire(key, fx, fy, fz, footY, templateLit, lit, d, chance, false);
    }

    /** Lit bonfires whose clearing can reach within margin blocks of the chunk's columns. */
    public static List<Bonfire> litReaching(List<Bonfire> all, ChunkPos cp, double margin) {
        List<Bonfire> out = new ArrayList<>();
        double lo = REACH + margin;
        for (Bonfire b : all) {
            if (!b.lit()) continue;
            double nx = Math.max(cp.getMinBlockX(), Math.min(cp.getMaxBlockX() + 1, b.fx() + 0.5));
            double nz = Math.max(cp.getMinBlockZ(), Math.min(cp.getMaxBlockZ() + 1, b.fz() + 0.5));
            double dx = nx - b.fx() - 0.5, dz = nz - b.fz() - 0.5;
            if (dx * dx + dz * dz <= lo * lo) out.add(b);
        }
        return out;
    }

    public static boolean inAny(List<Bonfire> lit, double x, double y, double z) {
        for (Bonfire b : lit) if (b.reaches(x, y, z)) return true;
        return false;
    }

    public static String describe(Bonfire b) {
        return String.format(Locale.ROOT, "ORG bonfire %d %d %d | foot %d | edge %.1f | chance %.3f | template %s | lit %s",
                b.fx(), b.fy(), b.fz(), b.footY(), b.edge(), b.chance(), b.templateLit() ? "lit" : "unlit", b.lit());
    }
}
