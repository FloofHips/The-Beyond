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
                double zPos = zCur + valueNoise2D(xCur, zCur, new Random(seed).nextLong());
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
                Random cellRandom = new Random(cellSeed);

                double xPos = xCur + valueNoise2D(xCur, zCur, cellSeed);
                double zPos = zCur + valueNoise2D(xCur, zCur, cellRandom.nextLong());

                double xDist = xPos - x;
                double zDist = zPos - z;
                double dist = xDist * xDist + zDist * zDist;

                if (dist < minDist) {
                    minDist = dist;
                    closestCellId = cellSeed; // Use the seed as cell ID
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

    public record CellResult(long cellId, double distance, double cellX, double cellZ) {}
}