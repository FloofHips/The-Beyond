package com.thebeyond.common.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/** The graph nodes, passable blocks near rock and thin rock when roots pierce it. */
abstract class NetworkGraph extends NetworkBase {
    NetworkGraph(Grid g, Settings st, List<Core> cores, int[] extVox, float[] extD, double extCoreD) {
        super(g, st, cores, extVox, extD, extCoreD);
    }

    void buildNodes() {
        int bx = (g.nx + 3) >> 2, by = (g.ny + 3) >> 2, bz = (g.nz + 3) >> 2;
        boolean[] rockBrick = new boolean[bx * by * bz];
        bricksY = by;
        bricksZ = bz;
        brickBase = new int[bx * by * bz];
        brickMask = new long[bx * by * bz];
        for (int x = 0; x < g.nx; x++) {
            for (int y = 0; y < g.ny; y++) {
                for (int z = 0; z < g.nz; z++) {
                    if (g.solid(g.index(x, y, z))) rockBrick[((x >> 2) * by + (y >> 2)) * bz + (z >> 2)] = true;
                }
            }
        }
        double half = st.lf() / 2, reach = Math.max(half, st.pierce());
        OrganicNear dist = new OrganicNear(g, reach, st.pierce() > 0);
        long checked = 0, differ = 0;
        List<double[]> offs = new ArrayList<>();
        int R = (int) Math.ceil(reach);
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > 0 && d <= reach) offs.add(new double[]{dx, dy, dz, d});
                }
            }
        }
        offs.sort((a, b) -> Double.compare(a[3], b[3]));
        int no = offs.size();
        int[] ox = new int[no], oy = new int[no], oz = new int[no];
        double[] od = new double[no];
        for (int i = 0; i < no; i++) {
            ox[i] = (int) offs.get(i)[0];
            oy[i] = (int) offs.get(i)[1];
            oz[i] = (int) offs.get(i)[2];
            od[i] = offs.get(i)[3];
        }
        double[] noiseDir = new double[24];
        SplittableRandom rnd = new SplittableRandom(st.seed() ^ 0x4F52474EL);
        for (int k = 0; k < 6; k++) {
            double a = rnd.nextGaussian(), b = rnd.nextGaussian(), c = rnd.nextGaussian(), l = Math.sqrt(a * a + b * b + c * c);
            double f = 2 * Math.PI / (st.noiseL() * (0.7 + 0.6 * rnd.nextDouble()));
            noiseDir[k * 4] = a / l * f;
            noiseDir[k * 4 + 1] = b / l * f;
            noiseDir[k * 4 + 2] = c / l * f;
            noiseDir[k * 4 + 3] = rnd.nextDouble() * 2 * Math.PI;
        }
        for (int cx = 0; cx < bx; cx++) {
            for (int cy = 0; cy < by; cy++) {
                for (int cz = 0; cz < bz; cz++) {
                    boolean near = false;
                    for (int ax = Math.max(0, cx - 1); ax <= Math.min(bx - 1, cx + 1) && !near; ax++) {
                        for (int ay = Math.max(0, cy - 1); ay <= Math.min(by - 1, cy + 1) && !near; ay++) {
                            for (int az = Math.max(0, cz - 1); az <= Math.min(bz - 1, cz + 1); az++) {
                                if (rockBrick[(ax * by + ay) * bz + az]) {
                                    near = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!near) continue;
                    brickBase[(cx * by + cy) * bz + cz] = n;
                    for (int x = cx << 2; x < Math.min(g.nx, (cx << 2) + 4); x++) {
                        for (int y = cy << 2; y < Math.min(g.ny, (cy << 2) + 4); y++) {
                            for (int z = cz << 2; z < Math.min(g.nz, (cz << 2) + 4); z++) {
                                int i = g.index(x, y, z);
                                if (!g.bog(i)) continue;
                                boolean rock = g.solid(i);
                                if (rock && st.pierce() <= 0) continue;
                                double lim = rock ? st.pierce() : half;
                                int q = dist.sq(x, y, z);
                                double d = q != Integer.MAX_VALUE && Math.sqrt(q) <= lim ? Math.sqrt(q) : -1;
                                if (OrganicLab.NODE_CHECK) {
                                    double d0 = -1;
                                    for (int o = 0; o < no && od[o] <= lim; o++) {
                                        int qx = x + ox[o], qy = y + oy[o], qz = z + oz[o];
                                        if (!g.inside(qx, qy, qz)) continue;
                                        if (g.solid(g.index(qx, qy, qz)) != rock) {
                                            d0 = od[o];
                                            break;
                                        }
                                    }
                                    checked++;
                                    if (Double.compare(d0, d) != 0) differ++;
                                }
                                if (d < 0) continue;
                                addNode(i, x, y, z, rock ? 0f : (float) d, rock, noiseDir);
                            }
                        }
                    }
                }
            }
        }
        hasLink = new boolean[n];
        int hab = 0, rock = 0;
        for (int u = 0; u < n; u++) {
            if ((nFlag[u] & F_HAB) != 0) hab++;
            if ((nFlag[u] & F_ROCK) != 0) rock++;
        }
        label();
        report.append(String.format(Locale.ROOT, "nodes %d (substrate %d, thin rock %d)", n, hab, rock));
        if (OrganicLab.NODE_CHECK) report.append(String.format(Locale.ROOT, " | node check %d blocks, %d differ", checked, differ));
        if (st.bio().forage()) buildRichness();
    }

    /** Substrate share in a cube of 2 fR + 1 around a point, 0 at its 10th percentile and 1 at its 90th. */
    void buildRichness() {
        rbx = (g.nx + RB - 1) / RB + 1;
        rby = (g.ny + RB - 1) / RB + 1;
        rbz = (g.nz + RB - 1) / RB + 1;
        int[] c = new int[rbx * rby * rbz];
        int hab = 0;
        for (int u = 0; u < n; u++) {
            if ((nFlag[u] & F_HAB) == 0) continue;
            c[((nX[u] / RB + 1) * rby + nY[u] / RB + 1) * rbz + nZ[u] / RB + 1]++;
            hab++;
        }
        for (int x = 1; x < rbx; x++) {
            for (int y = 1; y < rby; y++) {
                for (int z = 1; z < rbz; z++) {
                    c[(x * rby + y) * rbz + z] += c[((x - 1) * rby + y) * rbz + z] + c[(x * rby + y - 1) * rbz + z]
                            + c[(x * rby + y) * rbz + z - 1] - c[((x - 1) * rby + y - 1) * rbz + z]
                            - c[((x - 1) * rby + y) * rbz + z - 1] - c[(x * rby + y - 1) * rbz + z - 1]
                            + c[((x - 1) * rby + y - 1) * rbz + z - 1];
                }
            }
        }
        richSum = c;
        double[] d = new double[hab];
        int k = 0;
        for (int u = 0; u < n; u++) {
            if ((nFlag[u] & F_HAB) != 0) d[k++] = richRaw(nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5);
        }
        Arrays.sort(d);
        if (hab > 0) {
            richLo = d[(int) (0.1 * (hab - 1))];
            richHi = d[(int) (0.9 * (hab - 1))];
        }
        report.append(String.format(Locale.ROOT, " | foraging: substrate fraction in a cube of %.0f, p10 %.4f p90 %.4f",
                2 * st.bio().fR() + 1, richLo, richHi));
    }

    double richRaw(double x, double y, double z) {
        double R = st.bio().fR();
        int a0 = Math.max(0, (int) Math.floor((x - R) / RB)), a1 = Math.min(rbx - 2, (int) Math.floor((x + R) / RB));
        int b0 = Math.max(0, (int) Math.floor((y - R) / RB)), b1 = Math.min(rby - 2, (int) Math.floor((y + R) / RB));
        int c0 = Math.max(0, (int) Math.floor((z - R) / RB)), c1 = Math.min(rbz - 2, (int) Math.floor((z + R) / RB));
        if (a0 > a1 || b0 > b1 || c0 > c1) return 0;
        a1++;
        b1++;
        c1++;
        long sum = (long) ps(a1, b1, c1) - ps(a0, b1, c1) - ps(a1, b0, c1) - ps(a1, b1, c0) + ps(a0, b0, c1) + ps(a0, b1, c0)
                + ps(a1, b0, c0) - ps(a0, b0, c0);
        return sum / Math.pow(2 * R + 1, 3);
    }

    int ps(int x, int y, int z) {
        return richSum[(x * rby + y) * rbz + z];
    }

    /** Foraging richness in [0, 1] at a point, 0.5 without foraging. */
    double rich(double x, double y, double z) {
        if (richSum == null) return 0.5;
        return Math.max(0, Math.min(1, (richRaw(x, y, z) - richLo) / Math.max(richHi - richLo, 1e-9)));
    }

    /** Spacing of each point scaled by foraging: wider where poor (guerrilla), tighter where rich (phalanx). */
    double[] forageSpacing(List<Integer> pts, double r) {
        if (richSum == null) return null;
        double[] out = new double[pts.size()];
        for (int i = 0; i < out.length; i++) {
            int u = pts.get(i);
            out[i] = r * (1 + st.bio().fAmp() * (1 - 2 * rich(nX[u] + 0.5, nY[u] + 0.5, nZ[u] + 0.5)));
        }
        return out;
    }

    void addNode(int i, int x, int y, int z, float d, boolean rock, double[] noiseDir) {
        if (n == nVox.length) {
            int c = n * 2;
            nVox = Arrays.copyOf(nVox, c);
            nX = Arrays.copyOf(nX, c);
            nY = Arrays.copyOf(nY, c);
            nZ = Arrays.copyOf(nZ, c);
            nDist = Arrays.copyOf(nDist, c);
            nMult = Arrays.copyOf(nMult, c);
            nFlag = Arrays.copyOf(nFlag, c);
            nLab = Arrays.copyOf(nLab, c);
        }
        double s = 0;
        for (int k = 0; k < 6; k++) {
            s += Math.sin(noiseDir[k * 4] * x + noiseDir[k * 4 + 1] * y + noiseDir[k * 4 + 2] * z + noiseDir[k * 4 + 3]);
        }
        double nz = Math.max(0, Math.min(1, (s + 4.5) / 9));
        byte f = 0;
        double mult;
        if (rock) {
            f = F_ROCK;
            mult = (1 + st.noise() * nz) * st.pierceCost();
        } else {
            boolean up = g.solidAt(x, y + 1, z), down = g.solidAt(x, y - 1, z);
            boolean side = g.solidAt(x + 1, y, z) || g.solidAt(x - 1, y, z) || g.solidAt(x, y, z + 1) || g.solidAt(x, y, z - 1);
            boolean roof = up || g.solidAt(x, y + 2, z) || g.solidAt(x, y + 3, z);
            if ((st.ceiling() && up) || (st.wall() && side) || (st.floor() && down)) f |= F_HAB;
            if (down && !up) f |= F_FLOOR;
            if (up) f |= F_CEIL;
            if (d <= 1.8) f |= F_HUG;
            mult = (1 + st.kappa() * Math.max(0, d - 1)) * (1 + st.noise() * nz);
            if (down && !roof) {
                mult *= st.floorPen();
                f |= F_PENAL;
            }
            if (st.ceilPen() != 1 && up && !down && !g.solidAt(x, y - 2, z) && !g.solidAt(x, y - 3, z)) {
                mult *= st.ceilPen();
                f |= F_PENAL;
            }
        }
        nVox[n] = i;
        nX[n] = (short) x;
        nY[n] = (short) y;
        nZ[n] = (short) z;
        nDist[n] = d;
        nMult[n] = (float) mult;
        nFlag[n] = f;
        brickMask[((x >> 2) * bricksY + (y >> 2)) * bricksZ + (z >> 2)] |= 1L << ((x & 3) << 4 | (y & 3) << 2 | (z & 3));
        n++;
    }

    int nodeAt(int x, int y, int z) {
        int b = ((x >> 2) * bricksY + (y >> 2)) * bricksZ + (z >> 2), bit = (x & 3) << 4 | (y & 3) << 2 | (z & 3);
        long m = brickMask[b];
        return (m >>> bit & 1) == 0 ? -1 : brickBase[b] + Long.bitCount(m & ((1L << bit) - 1));
    }

    int nodeAt(int i) {
        return nodeAt(i / (g.ny * g.nz), i / g.nz % g.ny, i % g.nz);
    }

    void label() {
        for (int u = 0; u < n; u++) {
            double best = Double.MAX_VALUE;
            int bi = -1;
            for (int c = 0; c < cores.size(); c++) {
                Core k = cores.get(c);
                double dx = nX[u] + 0.5 - k.x(), dy = nY[u] + 0.5 - k.y(), dz = nZ[u] + 0.5 - k.z();
                double d = dx * dx + dy * dy + dz * dz;
                if (d < best) {
                    best = d;
                    bi = c;
                }
            }
            nLab[u] = bi;
        }
    }

    int[] cellOrder() {
        List<Integer> out = new ArrayList<>();
        boolean[] seen = new boolean[cores.size()];
        List<Integer> queue = new ArrayList<>();
        for (int c = 0; c < cores.size(); c++) {
            int p = cores.get(c).parent();
            if (p < 0 || !cores.get(p).grow()) queue.add(c);
        }
        while (!queue.isEmpty()) {
            int c = queue.remove(0);
            if (seen[c]) continue;
            seen[c] = true;
            out.add(c);
            for (int k = 0; k < cores.size(); k++) {
                if (cores.get(k).parent() == c && !seen[k]) queue.add(k);
            }
        }
        for (int c = 0; c < cores.size(); c++) {
            if (!seen[c]) out.add(c);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
