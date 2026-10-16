package com.thebeyond.util;

import java.util.Random;

//https://github.com/TJHJava/libnoiseforjava/blob/master/src/libnoiseforjava/module/Voronoi.java
public class VoronoiNoise {

    private static final double SQRT_2 = 1.4142135623730950488;
    private static final double SQRT_3 = 1.7320508075688772935;

    private long seed;
    private short distanceMethod;


    public VoronoiNoise(long seed, short distanceMethod) {
        this.seed = seed;
        this.distanceMethod = distanceMethod;
    }

    private double getDistance(double xDist, double zDist) {
        final double v = Math.sqrt(xDist * xDist + zDist * zDist) / SQRT_2;
        return switch (distanceMethod) {
            case 0 -> v;
            case 1 -> xDist + zDist;
            case 2 -> Math.pow(Math.E, v) / Math.E;
            default -> 1.0;
        };
    }

    private double getDistance(double xDist, double yDist, double zDist) {
        return switch (distanceMethod) {
            case 0 -> Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist) / SQRT_3;
            case 1 -> xDist + yDist + zDist;
            default -> 1.0;
        };
    }

    public short getDistanceMethod() {
        return distanceMethod;
    }

    public long getSeed() {
        return seed;
    }

    public double noise(double x, double z, double frequency) {
        x *= frequency;
        z *= frequency;

        int xInt = (x > .0? (int)x: (int)x - 1);
        int zInt = (z > .0? (int)z: (int)z - 1);

        double minDist = 32000000.0;

        double xCandidate = 0;
        double zCandidate = 0;

        for(int zCur = zInt - 2; zCur <= zInt + 2; zCur++) {
            for(int xCur = xInt - 2; xCur <= xInt + 2; xCur++) {

                double xPos = xCur + valueNoise2D(xCur, zCur, seed);
                double zPos = zCur + valueNoise2D(xCur, zCur, seed+1);
                double xDist = xPos - x;
                double zDist = zPos - z;
                double dist = xDist * xDist + zDist * zDist;

                if(dist < minDist) {
                    minDist = dist;
                    xCandidate = xPos;
                    zCandidate = zPos;
                }
            }
        }

        double xDist = xCandidate - x;
        double zDist = zCandidate - z;

        return getDistance(xDist, zDist);
    }

    public double noise(double x, double y, double z, double frequency) {
        x *= frequency;
        y *= frequency;
        z *= frequency;

        int xInt = (x > .0? (int)x: (int)x - 1);
        int yInt = (y > .0? (int)y: (int)y - 1);
        int zInt = (z > .0? (int)z: (int)z - 1);

        double minDist = 32000000.0;

        double xCandidate = 0;
        double yCandidate = 0;
        double zCandidate = 0;

        Random rand = new Random(seed);

        for(int zCur = zInt - 2; zCur <= zInt + 2; zCur++) {
            for(int yCur = yInt - 2; yCur <= yInt + 2; yCur++) {
                for(int xCur = xInt - 2; xCur <= xInt + 2; xCur++) {

                    double xPos = xCur + valueNoise3D (xCur, yCur, zCur, seed);
                    double yPos = yCur + valueNoise3D (xCur, yCur, zCur, rand.nextLong());
                    double zPos = zCur + valueNoise3D (xCur, yCur, zCur, rand.nextLong());
                    double xDist = xPos - x;
                    double yDist = yPos - y;
                    double zDist = zPos - z;
                    double dist = xDist * xDist + yDist * yDist + zDist * zDist;

                    if(dist < minDist) {
                        minDist = dist;
                        xCandidate = xPos;
                        yCandidate = yPos;
                        zCandidate = zPos;
                    }
                }
            }
        }

        double xDist = xCandidate - x;
        double yDist = yCandidate - y;
        double zDist = zCandidate - z;

        return getDistance(xDist, yDist, zDist);
    }

    public void setDistanceMethod(short distanceMethod) {
        this.distanceMethod = distanceMethod;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public static double valueNoise2D (int x, int z, long seed) {
        long n = (1619 * x + 6971 * z + 1013 * seed) & 0x7fffffff;
        n = (n >> 13) ^ n;
        return 1.0 - ((double)((n * (n * n * 60493 + 19990303) + 1376312589) & 0x7fffffff) / 1073741824.0);
    }

    public static double valueNoise3D (int x, int y, int z, long seed) {
        long n = (1619 * x + 31337 * y + 6971 * z + 1013 * seed) & 0x7fffffff;
        n = (n >> 13) ^ n;
        return 1.0 - ((double)((n * (n * n * 60493 + 19990303) + 1376312589) & 0x7fffffff) / 1073741824.0);
    }

    public CellResult getCell(double x, double z, double frequency) {
        x *= frequency;
        z *= frequency;

        int xInt = (int)(x > 0 ? x : x - 1);
        int zInt = (int)(z > 0 ? z : z - 1);

        double minDist = Double.MAX_VALUE;
        long closestCellId = 0;
        double xCandidate = 0;
        double zCandidate = 0;

        for (int zCur = zInt - 2; zCur <= zInt + 2; zCur++) {
            for (int xCur = xInt - 2; xCur <= xInt + 2; xCur++) {
                long cellSeed = (long)xCur * 1619L + (long)zCur * 6971L + this.seed;
                // Random(cellSeed).nextLong() inlined, same output with no allocation
                long s = (cellSeed ^ 0x5DEECE66DL) & 0xFFFFFFFFFFFFL;
                s = (s * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
                int h = (int)(s >>> 16);
                s = (s * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
                int l = (int)(s >>> 16);
                long seed1 = ((long) h << 32) + l;

                double xPos = xCur + valueNoise2D(xCur, zCur, cellSeed);
                double zPos = zCur + valueNoise2D(xCur, zCur, seed1);

                double xDist = xPos - x;
                double zDist = zPos - z;
                double dist = xDist * xDist + zDist * zDist;

                if (dist < minDist) {
                    minDist = dist;
                    closestCellId = cellSeed;
                    xCandidate = xPos;
                    zCandidate = zPos;
                }
            }
        }

        return new CellResult(
                closestCellId,
                getDistance(xCandidate - x, zCandidate - z),
                xCandidate / frequency, // Return world space position
                zCandidate / frequency
        );
    }

    private static final int[] NEAREST_FIRST = {0, -1, 1, -2, 2};

    private static double boxGap(int cur, double v) {
        double below = cur - 1 - v;
        if (below > 0.0) return below * below;
        double above = v - (cur + 1);
        return above > 0.0 ? above * above : 0.0;
    }

    public CellResult3D getCell(double x, double y, double z, double frequency) {
        x *= frequency;
        y *= frequency;
        z *= frequency;

        Nearest n = this.nearest(x, y, z, false);
        return new CellResult3D(
                n.cellId(),
                getDistance(n.px() - x, n.py() - y, n.pz() - z),
                n.px() / frequency,
                n.py() / frequency,
                n.pz() / frequency
        );
    }

    private record Nearest(long cellId, double px, double py, double pz, double dist, double secondDist,
                           int xInt, int yInt, int zInt) {}

    private static final class NearCell {
        private VoronoiNoise owner;
        private double frequency;
        private int xInt;
        private int yInt;
        private int zInt;
        private double x;
        private double y;
        private double z;
        private double reachSq = -1.0;
        private long cellId;
    }

    private static final ThreadLocal<NearCell> NEAR_CELL = ThreadLocal.withInitial(NearCell::new);

    /** getCell(x, y, z, frequency).cellId(), reusing this thread's last answer while no other candidate can be nearer. */
    public long cellId3D(double x, double y, double z, double frequency) {
        x *= frequency;
        y *= frequency;
        z *= frequency;
        NearCell near = NEAR_CELL.get();
        if (near.owner == this && near.frequency == frequency
                && near.xInt == (int)(x > 0 ? x : x - 1) && near.yInt == (int)(y > 0 ? y : y - 1)
                && near.zInt == (int)(z > 0 ? z : z - 1)) {
            double dx = x - near.x;
            double dy = y - near.y;
            double dz = z - near.z;
            if (dx * dx + dy * dy + dz * dz < near.reachSq) {
                return near.cellId;
            }
        }
        Nearest n = this.nearest(x, y, z, true);
        // same 125 candidates, so the answer holds while the point moves less than half the gap to the runner-up
        double reach = (Math.sqrt(n.secondDist()) - Math.sqrt(n.dist())) * 0.5 - 1.0e-6;
        near.owner = this;
        near.frequency = frequency;
        near.xInt = n.xInt();
        near.yInt = n.yInt();
        near.zInt = n.zInt();
        near.x = x;
        near.y = y;
        near.z = z;
        near.reachSq = reach > 0.0 ? reach * reach : -1.0;
        near.cellId = n.cellId();
        return n.cellId();
    }

    private Nearest nearest(double x, double y, double z, boolean withSecond) {
        int xInt = (int)(x > 0 ? x : x - 1);
        int yInt = (int)(y > 0 ? y : y - 1);
        int zInt = (int)(z > 0 ? z : z - 1);

        double minDist = Double.MAX_VALUE;
        double secondDist = Double.MAX_VALUE;
        double limit = Double.MAX_VALUE;
        int bestIdx = Integer.MAX_VALUE;
        long closestCellId = 0;

        double xCandidate = 0;
        double yCandidate = 0;
        double zCandidate = 0;

        // a cell's point lies within one unit of its corner, so a plane, row or cell beyond the limit cannot change the answer
        double[] gap = new double[15];
        for (int o = 0; o < 5; o++) {
            gap[o] = boxGap(xInt + o - 2, x);
            gap[5 + o] = boxGap(yInt + o - 2, y);
            gap[10 + o] = boxGap(zInt + o - 2, z);
        }

        for (int iz = 0; iz < 5; iz++) {
            int dz = NEAREST_FIRST[iz];
            double gz = gap[10 + dz + 2];
            if (gz > limit) continue;
            int zCur = zInt + dz;
            for (int iy = 0; iy < 5; iy++) {
                int dy = NEAREST_FIRST[iy];
                double gzy = gz + gap[5 + dy + 2];
                if (gzy > limit) continue;
                int yCur = yInt + dy;
                for (int ix = 0; ix < 5; ix++) {
                    int dx = NEAREST_FIRST[ix];
                    if (gzy + gap[dx + 2] > limit) continue;
                    int xCur = xInt + dx;

                    long cellSeed = (long)xCur * 1619L
                            + (long)yCur * 31337L
                            + (long)zCur * 6971L
                            + this.seed;

                    // two Random(cellSeed).nextLong() inlined, same output with no allocation
                    long s = (cellSeed ^ 0x5DEECE66DL) & 0xFFFFFFFFFFFFL;
                    s = (s * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
                    int h1 = (int)(s >>> 16);
                    s = (s * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
                    int l1 = (int)(s >>> 16);
                    long seed1 = ((long) h1 << 32) + l1;
                    s = (s * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
                    int h2 = (int)(s >>> 16);
                    s = (s * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
                    int l2 = (int)(s >>> 16);
                    long seed2 = ((long) h2 << 32) + l2;

                    double xPos = xCur + valueNoise3D(xCur, yCur, zCur, cellSeed);
                    double yPos = yCur + valueNoise3D(xCur, yCur, zCur, seed1);
                    double zPos = zCur + valueNoise3D(xCur, yCur, zCur, seed2);

                    double xDist = xPos - x;
                    double yDist = yPos - y;
                    double zDist = zPos - z;

                    double dist = xDist * xDist + yDist * yDist + zDist * zDist;

                    // ties go to the cell the plain z,y,x loop meets first
                    int idx = ((dz + 2) * 5 + (dy + 2)) * 5 + (dx + 2);
                    if (dist < minDist || (dist == minDist && idx < bestIdx)) {
                        secondDist = minDist;
                        minDist = dist;
                        limit = (withSecond ? secondDist : dist) * 1.000000001 + 1.0e-12;
                        bestIdx = idx;
                        closestCellId = cellSeed;
                        xCandidate = xPos;
                        yCandidate = yPos;
                        zCandidate = zPos;
                    } else if (dist < secondDist) {
                        secondDist = dist;
                        if (withSecond) {
                            limit = dist * 1.000000001 + 1.0e-12;
                        }
                    }
                }
            }
        }

        return new Nearest(closestCellId, xCandidate, yCandidate, zCandidate, minDist, secondDist, xInt, yInt, zInt);
    }

    public record CellResult(long cellId, double distance, double cellX, double cellZ) {}
    public record CellResult3D(long cellId, double distance, double cellX, double cellY, double cellZ) {}
}