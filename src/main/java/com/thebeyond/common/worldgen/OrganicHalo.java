package com.thebeyond.common.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Locale;

/** The corruption halo around light-shy tissue, uniform or widening with each root (FLOW), shared by painter and world. */
public final class OrganicHalo {
    public enum Mode { UNIFORM, FLOW }

    public static final Mode MODE = Mode.valueOf(System.getProperty("beyond.sub.org.halo.mode", "flow").toUpperCase(Locale.ROOT));
    // in blocks: MIN at the thinnest root, MAX from RREF thick, CORE more at a fed nucleus, edge noise of JITTER over SCALE
    static final double MIN = Double.parseDouble(System.getProperty("beyond.sub.org.halo.min", "4"));
    static final double MAX = Double.parseDouble(System.getProperty("beyond.sub.org.halo.max", "8"));
    static final double RREF = Double.parseDouble(System.getProperty("beyond.sub.org.halo.rRef", "3"));
    static final double JITTER = Double.parseDouble(System.getProperty("beyond.sub.org.halo.jitter", "1.5"));
    static final double SCALE = Double.parseDouble(System.getProperty("beyond.sub.org.halo.scale", "10"));
    static final double CORE = Math.max(0, Double.parseDouble(System.getProperty("beyond.sub.org.halo.core", "3")));
    // tissue at an exposed rock face leaves the corruption dripping down it, as far as the face drops, at most STREAK_MAX blocks
    static final boolean STREAKS = Boolean.getBoolean("beyond.sub.org.streaks");
    static final int STREAK_MAX = Math.min(31, Integer.getInteger("beyond.sub.org.streaks.max", 24));
    private static final double THIN = 0.5;

    private record Edge(long seed, SimplexNoise noise) {}

    private static volatile Edge edge;

    private OrganicHalo() {}

    static boolean flow() {
        return MODE == Mode.FLOW;
    }

    static double radius(int thick) {
        if (!flow() || thick < 0) return OrganicLight.HALO;
        double t = Mth.clamp((thick / 2.0 - THIN) / (RREF - THIN), 0, 1);
        return MIN + (MAX - MIN) * t;
    }

    /** Halo around a block whose widest sphere was thick half blocks (-1 unknown), CORE wider for a nucleus its subs feed. */
    static double radius(int thick, double fed) {
        return flow() ? radius(thick) + CORE * fed : radius(thick);
    }

    static byte drip(int length, int side) {
        return (byte) (Math.min(length, STREAK_MAX) | side << 5);
    }

    static int dripLength(int code) {
        return code & 31;
    }

    static int dripDx(int code) {
        int side = code >> 5 & 3;
        return side == 0 ? 1 : side == 1 ? -1 : 0;
    }

    static int dripDz(int code) {
        int side = code >> 5 & 3;
        return side == 2 ? 1 : side == 3 ? -1 : 0;
    }

    static int below() {
        return STREAKS && flow() ? STREAK_MAX : 0;
    }

    static double reach() {
        return flow() ? Math.max(Math.max(MIN, MAX) + CORE + Math.abs(JITTER), OrganicLight.HALO) : OrganicLight.HALO;
    }

    static double jitter(long seed, int qx, int qy, int qz) {
        return flow() ? edge(seed, qx, qy, qz) : 0;
    }

    static double edge(long seed, int qx, int qy, int qz) {
        if (JITTER == 0) return 0;
        Edge e = edge;
        if (e == null || e.seed() != seed) {
            e = new Edge(seed, new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x68616C6FL)));
            edge = e;
        }
        return JITTER * e.noise().getValue((qx * 4 + 2) / SCALE, (qy * 4 + 2) / SCALE, (qz * 4 + 2) / SCALE);
    }

    /** Voxels a face neighbour of the same bulb at least as wide always covers, so the stamp skips them (none when unsafe). */
    static BitSet hidden(int[] vox, byte[] kinds, int[] owner, byte[] thick, byte skip, int nx, int ny, int nz) {
        BitSet out = new BitSet(vox.length);
        if (!flow() || MIN < 2 + Math.abs(JITTER) || MAX < MIN) return out;
        Int2IntOpenHashMap at = new Int2IntOpenHashMap(vox.length * 2);
        at.defaultReturnValue(-1);
        for (int s = 0; s < vox.length; s++) if (kinds[s] != skip) at.put(vox[s], s);
        int plane = ny * nz;
        for (int s = 0; s < vox.length; s++) {
            if (kinds[s] == skip) continue;
            int i = vox[s], x = i / plane, y = (i / nz) % ny, z = i % nz;
            if (x == 0 || y == 0 || z == 0 || x == nx - 1 || y == ny - 1 || z == nz - 1) continue;
            boolean beaten = true;
            for (int n : new int[]{i + 1, i - 1, i + nz, i - nz, i + plane, i - plane}) {
                int t = at.get(n);
                if (t < 0 || owner[t] != owner[s] || thick[t] < thick[s]) {
                    beaten = false;
                    break;
                }
            }
            if (beaten) out.set(s);
        }
        return out;
    }

    /** Quarts of a box with their margin past the nearest halo edge, held within the jitter, and the nearest block's tick. */
    static final class Field {
        final int qx0, qy0, qz0, nx, ny, nz;
        private final float[] margin;
        private final int[] tick;
        private final double[] near;
        private final double slack;
        private final boolean rough;

        Field(int qx0, int qy0, int qz0, int qx1, int qy1, int qz1, boolean ticks) {
            this(qx0, qy0, qz0, qx1, qy1, qz1, ticks, flow());
        }

        Field(int qx0, int qy0, int qz0, int qx1, int qy1, int qz1, boolean ticks, boolean rough) {
            this.qx0 = qx0;
            this.qy0 = qy0;
            this.qz0 = qz0;
            nx = Math.max(0, qx1 - qx0 + 1);
            ny = Math.max(0, qy1 - qy0 + 1);
            nz = Math.max(0, qz1 - qz0 + 1);
            margin = new float[nx * ny * nz];
            Arrays.fill(margin, Float.POSITIVE_INFINITY);
            tick = ticks ? new int[margin.length] : null;
            if (tick != null) Arrays.fill(tick, Integer.MAX_VALUE);
            near = ticks ? new double[margin.length] : null;
            if (near != null) Arrays.fill(near, Double.MAX_VALUE);
            this.rough = rough;
            slack = rough ? Math.abs(JITTER) : 0;
        }

        /** Adds a block centred at px, py, pz whose halo has radius h, reached at tick t (ignored without ticks). */
        void add(double px, double py, double pz, double h, int t) {
            double d = h + slack;
            int xa = Math.max(qx0, Mth.floor((px - d) / 4)), xb = Math.min(qx0 + nx - 1, Mth.floor((px + d) / 4));
            int ya = Math.max(qy0, Mth.floor((py - d) / 4)), yb = Math.min(qy0 + ny - 1, Mth.floor((py + d) / 4));
            int za = Math.max(qz0, Mth.floor((pz - d) / 4)), zb = Math.min(qz0 + nz - 1, Mth.floor((pz + d) / 4));
            double d2 = d * d;
            for (int qx = xa; qx <= xb; qx++) {
                double ex = qx * 4 + 2 - px;
                for (int qy = ya; qy <= yb; qy++) {
                    double ey = qy * 4 + 2 - py;
                    for (int qz = za; qz <= zb; qz++) {
                        double ez = qz * 4 + 2 - pz, e2 = ex * ex + ey * ey + ez * ez;
                        if (e2 > d2) continue;
                        int i = ((qx - qx0) * ny + (qy - qy0)) * nz + (qz - qz0);
                        float m = (float) (Math.sqrt(e2) - h);
                        if (m < margin[i]) margin[i] = m;
                        if (tick != null && e2 < near[i]) {
                            near[i] = e2;
                            tick[i] = t;
                        }
                    }
                }
            }
        }

        void drip(int x, int y, int z, int code, int t) {
            int len = dripLength(code);
            if (len == 0) return;
            column(x >> 2, z >> 2, y - len, y - 1, x, y, z, t);
            column((x + dripDx(code)) >> 2, (z + dripDz(code)) >> 2, y - len, y - 1, x, y, z, t);
        }

        private void column(int qx, int qz, int yLow, int yHigh, int x, int y, int z, int t) {
            if (qx < qx0 || qz < qz0 || qx >= qx0 + nx || qz >= qz0 + nz) return;
            for (int qy = Math.max(qy0, Math.floorDiv(yLow, 4)); qy <= Math.min(qy0 + ny - 1, Math.floorDiv(yHigh, 4)); qy++) {
                int i = ((qx - qx0) * ny + (qy - qy0)) * nz + (qz - qz0);
                margin[i] = Float.NEGATIVE_INFINITY;
                if (tick == null) continue;
                double ex = qx * 4 + 2 - x - 0.5, ey = qy * 4 + 2 - y - 0.5, ez = qz * 4 + 2 - z - 0.5;
                double e2 = ex * ex + ey * ey + ez * ez;
                if (e2 < near[i]) {
                    near[i] = e2;
                    tick[i] = t;
                }
            }
        }

        boolean inside(int qx, int qy, int qz) {
            return qx >= qx0 && qy >= qy0 && qz >= qz0 && qx < qx0 + nx && qy < qy0 + ny && qz < qz0 + nz;
        }

        boolean held(long seed, int qx, int qy, int qz) {
            if (!inside(qx, qy, qz)) return false;
            float m = margin[((qx - qx0) * ny + (qy - qy0)) * nz + (qz - qz0)];
            return m != Float.POSITIVE_INFINITY && m <= (rough ? edge(seed, qx, qy, qz) : 0);
        }

        int tick(int qx, int qy, int qz) {
            return tick == null || !inside(qx, qy, qz) ? Integer.MAX_VALUE
                    : tick[((qx - qx0) * ny + (qy - qy0)) * nz + (qz - qz0)];
        }
    }
}
