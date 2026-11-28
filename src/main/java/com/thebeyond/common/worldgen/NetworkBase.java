package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The organic network's settings, constants and the state its pipeline stages share. */
abstract class NetworkBase {
    public enum Style { TREE, MESH }

    public static final byte TUBE = 1, BULB = 2, FILM = 3, CROSS = 4, AERIAL = 5, PENDANT = 6, TUNNEL = 8;

    // node kinds of the grown network
    static final byte K_CORE = 0, K_TRUNK = 1, K_SUB = 2, K_CROSS = 3, K_PILLAR = 6, K_CABLE = 7, K_FRINGE = 8;

    /** The faces an organism lives on besides walls: open floors cost double under ceilings, hanging air under floors. */
    public enum Substrate {
        CEILING(true, false, 2, 1), FLOOR(false, true, 1, 2), BOTH(true, true, 1, 1);

        final boolean ceiling, floor;
        final double floorPen, ceilPen;

        Substrate(boolean ceiling, boolean floor, double floorPen, double ceilPen) {
            this.ceiling = ceiling;
            this.floor = floor;
            this.floorPen = floorPen;
            this.ceilPen = ceilPen;
        }
    }

    public record Settings(Style style, long seed, double lf, double kappa, double floorPen, double ceilPen, double noise,
                           double noiseL,
                           double rSub, int mSub, double reach, double reachX, double rAtt, double di, double dk,
                           double step, int iters, double spread, double nExp, double rTip, double rMax, double wSub,
                           double rCore, double rSubBulb, double film, double minTwig, double keepTwig, int pendEvery,
                           double pendMin, double pendMax, double pendR, double meshR, int tSteps, double tI0,
                           double tGamma, double tDt, double tKeep, boolean colonies, boolean ceiling, boolean wall,
                           boolean floor, double pierce, double pierceCost, boolean ligaments, double linkMax,
                           double linkMaxH, double linkAnc, double linkAng, int linkK, double linkCost, double linkCostV,
                           double linkSag, double linkSway, boolean linkOutside, double linkR0, double linkRe, double linkRm,
                           boolean flux, double fluxL0, double fluxX, double vSub, double linkRMin, double pTop,
                           double pBot, double cMid, double cEnd, boolean catenary, double sagMin, Bio bio, int bulge,
                           double bulgeH) {

        public static Settings of(Style style, long seed) {
            return of(style, seed, true);
        }

        /** The style's own growth: flux sizes roots by vigor and distance instead of a fixed cap. */
        public static Settings of(Style style, long seed, boolean flux) {
            return of(style, seed, flux, null);
        }

        /** setup (needs flux) picks an organism of Bio.of, and an empty or null one keeps the style's own growth. */
        public static Settings of(Style style, long seed, boolean flux, String setup) {
            return of(style, seed, flux, setup, Substrate.CEILING);
        }

        public static Settings of(Style style, long seed, boolean flux, String setup, Substrate where) {
            boolean mesh = style == Style.MESH;
            Bio bio = flux && setup != null && !setup.isEmpty() ? Bio.of(setup) : Bio.OFF;
            return new Settings(style, seed, 8, 2.5, where.floorPen, where.ceilPen, 0.6, 22, 26, 24, 140, 200, 5, 12, 3.5,
                    1.6, 45, 16, 2.5, 0.5, flux ? 3.0 : 2.6, 6, 4.5, 2.5, 1.6, mesh ? 0 : 4, 0.25, 24, 3, 12, 1.6, 3.5, 400,
                    mesh ? 0.2 : 2.0, mesh ? 1.15 : 1.8, 0.1, 0.02, !mesh, where.ceiling, true, where.floor, 4, 3, true, 96, 56,
                    8, 50, 2, 2.5, 1.5, 0.15, 4, true, 1.2, 1.6, 0.9, flux, 40, 1, 0.15, bio.on() ? 0.8 : 1, 0.75, 1.2, 0.7,
                    1.15, flux, 0.04, bio, 0, 1.8);
        }
    }

    /** One organism's biology: collars, age, gravitropism, reach by vigor, foraging, the fig's bundle, sag, fusion, loops. */
    public record Bio(boolean on, double kb, double kc, double ko, double fj, double fjLink, double zj, double aAge,
                      double vFront, boolean grav, double gUp, double gUpLink, double gDn, boolean vigReach, double bReach,
                      boolean forage, double fR, double fAmp, double fCord, int comp, double compPhi, boolean meshBulge,
                      boolean sagAll, double fusD, double load, boolean foodLoops, double loopR, double loopD,
                      double loopK, int loopN, double loopF) {

        private static final String[] KEYS = {"kb", "kc", "ko", "fj", "fjLink", "zj", "aAge", "vFront", "grav", "gUp", "gUpLink",
                "gDn", "vigReach", "bReach", "forage", "fR", "fAmp", "fCord", "comp", "compPhi", "meshBulge", "sagAll", "fusD",
                "load", "foodLoops", "loopR", "loopD", "loopK", "loopN", "loopF"};
        private static final double[] BASE = {0.67, 1, 0.6, 1.2, 1.4, 3, 0.3, 0.5, 0, 1, 2, 0.25, 0, 0.5, 0, 24, 0.35, 0.15,
                0, 0.35, 1, 0, 0, 0, 0, 16, 6, 3, 3, 0.8};
        private static final Map<String, String> SETUPS = Map.of(
                "root", "grav=1,gUp=1,gUpLink=2,gDn=0.25,ko=0.6,fj=1.2,aAge=0.3,comp=0,meshBulge=1,"
                        + "vigReach=1,forage=1,sagAll=1,fusD=8",
                "strangler_fig", "grav=1,gUp=1,gUpLink=2,gDn=0.25,ko=0.6,fj=1.2,aAge=0.3,comp=2,meshBulge=1,"
                        + "vigReach=1,forage=1,sagAll=1,fusD=24,foodLoops=1",
                "fungus", "grav=0,ko=0.9,fj=1,aAge=0.3,comp=0,meshBulge=0,vigReach=1,forage=1,sagAll=1,fusD=24,foodLoops=1",
                "slime_mold", "grav=1,gUp=0.3,gUpLink=0.6,gDn=0.1,ko=1,fj=1,aAge=0,comp=0,meshBulge=0,"
                        + "vigReach=1,forage=1,sagAll=1,fusD=24");

        public static final Bio OFF = make(BASE, false);

        public static String[] keys() {
            return KEYS.clone();
        }

        /** The values a setup gives every key, overrides included, in the order of keys(). */
        public static double[] values(String spec) {
            String[] parts = spec.split(":", 2);
            String setup = SETUPS.get(parts[0]);
            if (setup == null) throw new IllegalArgumentException("unknown organism setup " + parts[0]);
            double[] v = BASE.clone();
            apply(v, setup);
            if (parts.length > 1) apply(v, parts[1]);
            return v;
        }

        /** "root", "strangler_fig", "fungus" or "slime_mold", optionally followed by overrides: "fungus:load=1.5,fusD=0". */
        public static Bio of(String spec) {
            String[] parts = spec.split(":", 2);
            String setup = SETUPS.get(parts[0]);
            if (setup == null) throw new IllegalArgumentException("unknown organism setup " + parts[0]);
            double[] v = BASE.clone();
            apply(v, setup);
            if (parts.length > 1) apply(v, parts[1]);
            return make(v, true);
        }

        private static void apply(double[] v, String kvs) {
            for (String kv : kvs.split(",")) {
                String[] p = kv.trim().split("=");
                int i = Arrays.asList(KEYS).indexOf(p[0]);
                if (i < 0 || p.length != 2) throw new IllegalArgumentException("unknown organism key " + kv);
                v[i] = Double.parseDouble(p[1]);
            }
        }

        private static Bio make(double[] v, boolean on) {
            return new Bio(on, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8] != 0, v[9], v[10], v[11], v[12] != 0, v[13],
                    v[14] != 0, v[15], v[16], v[17], (int) v[18], v[19], v[20] != 0, v[21] != 0, v[22], v[23], v[24] != 0, v[25],
                    v[26], v[27], (int) v[28], v[29]);
        }
    }

    /** Solid and bog per block of a world box, as bitsets. */
    public static final class Grid {
        public final int x0, y0, z0, nx, ny, nz;
        private final long[] solid, bog;

        public Grid(int x0, int y0, int z0, int nx, int ny, int nz) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            long n = (long) nx * ny * nz;
            solid = new long[(int) ((n + 63) >> 6)];
            bog = new long[solid.length];
        }

        public int index(int x, int y, int z) {
            return (x * ny + y) * nz + z;
        }

        public boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz;
        }

        /** The solid bits of indices i to i + 63, as bit 0 to 63, past the end as 0. */
        long solid64(int i) {
            return bits64(solid, i);
        }

        long bog64(int i) {
            return bits64(bog, i);
        }

        private static long bits64(long[] set, int i) {
            int w = i >>> 6, o = i & 63;
            long a = set[w] >>> o;
            if (o != 0 && w + 1 < set.length) a |= set[w + 1] << (64 - o);
            return a;
        }

        public void set(int x, int y, int z, boolean isSolid, boolean isBog) {
            int i = index(x, y, z);
            if (isSolid) solid[i >> 6] |= 1L << i;
            if (isBog) bog[i >> 6] |= 1L << i;
        }

        public boolean solid(int i) {
            return (solid[i >> 6] & 1L << i) != 0;
        }

        public boolean bog(int i) {
            return (bog[i >> 6] & 1L << i) != 0;
        }

        boolean sameBits(Grid o) {
            return Arrays.equals(solid, o.solid) && Arrays.equals(bog, o.bog);
        }

        boolean solidAt(int x, int y, int z) {
            return inside(x, y, z) && solid(index(x, y, z));
        }

        boolean solidAt(double x, double y, double z) {
            return solidAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
    }

    /** A cell core in box coordinates: parent is an index in the same list or -1, and grow false only labels blocks. */
    public record Core(long id, double x, double y, double z, int parent, boolean grow) {}

    /** Voxels with kind, network distance, bulb and widest sphere, the trunks a child cell links to and whether it joined. */
    public record Result(int[] voxels, byte[] kinds, float[] dnet, int[] tree, float[] treeD, float[] treeR, float[] treeA,
                         float[] treeN, float[] treeS, String report, float[] nodes, boolean linked, int[] owner,
                         float[] organs, byte[] thick) {}

    public static final int ORGAN = 8;

    static final int[][] OFF26 = new int[26][];

    static final double[] LEN26 = new double[26];

    // The slime mold solver is preconditioned by an incomplete Cholesky factor, and "jacobi" goes back to the diagonal.
    static final boolean IC0 = !"jacobi".equals(System.getProperty("beyond.sub.org.teroSolver", "ic0"));

    static final int[][] OFF6 = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    static {
        int k = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    OFF26[k] = new int[]{dx, dy, dz};
                    LEN26[k++] = Math.sqrt(dx * dx + dy * dy + dz * dz);
                }
            }
        }
    }

    final Grid g;

    final Settings st;

    final List<Core> cores;

    final StringBuilder report = new StringBuilder();

    boolean linked;

    // graph nodes are passable blocks, numbered per 4x4x4 brick: a node is its brick's first plus its rank in the mask
    int[] brickBase;

    long[] brickMask;

    int bricksY, bricksZ;

    boolean[] hasLink;

    int n;

    int[] nVox = new int[1024];

    short[] nX = new short[1024], nY = new short[1024], nZ = new short[1024];

    float[] nDist = new float[1024], nMult = new float[1024];

    byte[] nFlag = new byte[1024];

    int[] nLab = new int[1024];

    static final byte F_ROCK = 1, F_HAB = 2, F_FLOOR = 4, F_CEIL = 8, F_HUG = 16, F_PENAL = 32;

    final Map<Integer, List<double[]>> linkAdj = new HashMap<>();

    // grown network
    int m;

    double[] px = new double[1024], py = new double[1024], pz = new double[1024], dn = new double[1024];

    double[] peso = new double[1024], rf = new double[1024];

    int[] pai = new int[1024], cel = new int[1024];

    byte[] kind = new byte[1024];

    boolean[] via = new boolean[1024];

    final Map<Long, Integer> noVox = new HashMap<>();

    final List<double[]> links = new ArrayList<>();

    final List<int[]> meshEdges = new ArrayList<>();

    final List<double[]> meshRadius = new ArrayList<>();

    final int[] extVox;

    final float[] extD;

    double extCoreD;

    float[] extR, extA, extN, extS;

    double[] rFlow, nodeR = new double[0], accOut, normOut, distOut;

    final List<double[]> outAnchors = new ArrayList<>();

    final List<int[]> fusions = new ArrayList<>();

    final List<Integer> farSubs = new ArrayList<>();

    boolean ic0 = IC0;

    int[] deadNodes;

    double[] atrophyF;

    boolean[] starved;

    static final double STARVED = 0.02;

    // foraging: prefix sums of substrate nodes over 2-block bricks
    static final int RB = 2;

    int[] richSum;

    int rbx, rby, rbz;

    double richLo, richHi = 1;

    /** Upper end first (ties by position), so both ends compute the same curve. */
    static boolean swapEnds(double[] a, double[] b) {
        return b[1] > a[1] || (b[1] == a[1] && (b[0] < a[0] || (b[0] == a[0] && b[2] < a[2])));
    }

    static boolean steep(double[] a, double[] b, double angle) {
        double h = Math.hypot(b[0] - a[0], b[2] - a[2]);
        return Math.toDegrees(Math.atan2(Math.abs(b[1] - a[1]), h)) >= angle;
    }

    // Mixed by a bijection so HashMap spreads the cells: the packed coordinates alone piled them into tree bins.
    static long key(int x, int y, int z) {
        long k = (((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF)) * 0x9E3779B97F4A7C15L;
        return k ^ (k >>> 29);
    }

    static long vk(int c, int u) {
        return ((long) c << 32) | (u & 0xFFFFFFFFL);
    }

    // fraction of each link to the parent grown by the parent's front, the youngest point of the link
    final Map<Integer, Double> meet = new HashMap<>();

    static int[] components(int mm, int[] ea, int[] eb) {
        int[] parent = new int[mm];
        for (int i = 0; i < mm; i++) parent[i] = i;
        for (int e = 0; e < ea.length; e++) {
            int a = find(parent, ea[e]), b = find(parent, eb[e]);
            if (a != b) parent[a] = b;
        }
        for (int i = 0; i < mm; i++) parent[i] = find(parent, i);
        return parent;
    }

    static int find(int[] p, int i) {
        while (p[i] != i) {
            p[i] = p[p[i]];
            i = p[i];
        }
        return i;
    }

    static double[] meshDist(int mm, int[] ea, int[] eb, double[] el, int root) {
        double[] d = new double[mm];
        Arrays.fill(d, Double.POSITIVE_INFINITY);
        List<List<double[]>> adj = new ArrayList<>();
        for (int i = 0; i < mm; i++) adj.add(new ArrayList<>());
        for (int e = 0; e < ea.length; e++) {
            adj.get(ea[e]).add(new double[]{eb[e], el[e]});
            adj.get(eb[e]).add(new double[]{ea[e], el[e]});
        }
        OrganicHeap h = new OrganicHeap();
        d[root] = 0;
        h.push(0, root);
        while (h.size > 0) {
            double k = h.topKey();
            int u = h.pop();
            if (k > d[u]) continue;
            for (double[] e : adj.get(u)) {
                int v = (int) e[0];
                if (k + e[1] < d[v]) {
                    d[v] = k + e[1];
                    h.push(d[v], v);
                }
            }
        }
        return d;
    }

    static int mix(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    static int[][] sphere(double r) {
        int R = (int) Math.ceil(r);
        List<int[]> out = new ArrayList<>();
        for (int a = -R; a <= R; a++) {
            for (int b = -R; b <= R; b++) {
                for (int c = -R; c <= R; c++) {
                    if (a * a + b * b + c * c <= r * r + 0.25) out.add(new int[]{a, b, c});
                }
            }
        }
        return out.toArray(new int[0][]);
    }

    static double sq(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    static double dist(double[] a, double[] b) {
        return Math.sqrt(sq(a[0] - b[0], a[1] - b[1], a[2] - b[2]));
    }

    NetworkBase(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        this.g = g;
        this.st = st;
        this.cores = cores;
        this.extVox = extVox;
        this.extD = extD;
        this.extCoreD = extCoreD;
    }

    double[] nodePos(int i) {
        return new double[]{px[i], py[i], pz[i]};
    }

    /** Radius at s blocks from the nearest anchor of a mesh ligament, with the collar of a tree anchor. */
    double colarPt(double r, double s, double f) {
        double rc = st.bio().fjLink() * f * r;
        return r + (rc - r) * 0.5 * (1 + Math.cos(Math.PI * Math.min(1, s / Math.max(st.bio().zj() * rc, 1e-6))));
    }

    double[] cargaF(double vx, double vy, double vz) {
        double c = st.bio().load() * Math.abs(vy) / Math.max(Math.sqrt(vx * vx + vy * vy + vz * vz), 1e-9);
        return new double[]{Math.sqrt((1 + c) / (1 + c / 2)), Math.sqrt(1 / (1 + c / 2))};
    }
}
