package com.thebeyond.util;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** SimplexNoise variant with the 256-entry permutation table replaced by SplitMix64
 *  hashing — period grows to 2^64. Callers should still wrap inputs (~±500k) so the
 *  skew/unskew math doesn't lose precision in {@code double}. */
public class HashSimplexNoise {
    protected static final int[][] GRADIENT = new int[][]{
            {1, 1, 0},
            {-1, 1, 0},
            {1, -1, 0},
            {-1, -1, 0},
            {1, 0, 1},
            {-1, 0, 1},
            {1, 0, -1},
            {-1, 0, -1},
            {0, 1, 1},
            {0, -1, 1},
            {0, 1, -1},
            {0, -1, -1},
            {1, 1, 0},
            {0, -1, 1},
            {-1, 1, 0},
            {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double F2 = 0.5 * (SQRT_3 - 1.0);
    private static final double G2 = (3.0 - SQRT_3) / 6.0;

    /** Per-instance seed mixed into every permutation hash. */
    private final long seed;

    public final double xo;
    public final double yo;
    public final double zo;

    public HashSimplexNoise(RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        // Full 64-bit seed from two nextInt() calls; RandomSource does not expose
        // nextLong() in the reduced API Minecraft uses here.
        long hi = ((long) random.nextInt()) << 32;
        long lo = ((long) random.nextInt()) & 0xFFFFFFFFL;
        this.seed = hi | lo;
    }

    /** SplitMix64 hash of {@code (index ^ seed)} masked to [0, 65536). */
    private int p(int index) {
        long h = ((long) index) ^ this.seed;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (int) (h & 0xFFFFL);
    }

    protected static double dot(int[] gradient, double x, double y, double z) {
        return (double) gradient[0] * x + (double) gradient[1] * y + (double) gradient[2] * z;
    }

    private double getCornerNoise3D(int gradientIndex, double x, double y, double z, double offset) {
        double d1 = offset - x * x - y * y - z * z;
        double d0;
        if (d1 < 0.0) {
            d0 = 0.0;
        } else {
            d1 *= d1;
            d0 = d1 * d1 * dot(GRADIENT[gradientIndex], x, y, z);
        }
        return d0;
    }

    public double getValue(double x, double y) {
        double d0 = (x + y) * F2;
        int i = Mth.floor(x + d0);
        int j = Mth.floor(y + d0);
        double d1 = (double) (i + j) * G2;
        double d2 = (double) i - d1;
        double d3 = (double) j - d1;
        double d4 = x - d2;
        double d5 = y - d3;
        int k;
        int l;
        if (d4 > d5) {
            k = 1;
            l = 0;
        } else {
            k = 0;
            l = 1;
        }

        double d6 = d4 - (double) k + G2;
        double d7 = d5 - (double) l + G2;
        double d8 = d4 - 1.0 + 2.0 * G2;
        double d9 = d5 - 1.0 + 2.0 * G2;
        // No mask needed: p() hashes over the full 32-bit int range and returns a
        // 16-bit result, so any int index produces a deterministic permutation.
        int k1 = this.p(i + this.p(j)) % 12;
        int l1 = this.p(i + k + this.p(j + l)) % 12;
        int i2 = this.p(i + 1 + this.p(j + 1)) % 12;
        double d10 = this.getCornerNoise3D(k1, d4, d5, 0.0, 0.5);
        double d11 = this.getCornerNoise3D(l1, d6, d7, 0.0, 0.5);
        double d12 = this.getCornerNoise3D(i2, d8, d9, 0.0, 0.5);
        return 70.0 * (d10 + d11 + d12);
    }

    public double getValue(double x, double y, double z) {
        double d1 = (x + y + z) * 0.3333333333333333;
        int i = Mth.floor(x + d1);
        int j = Mth.floor(y + d1);
        int k = Mth.floor(z + d1);
        double d3 = (double) (i + j + k) * 0.16666666666666666;
        double d4 = (double) i - d3;
        double d5 = (double) j - d3;
        double d6 = (double) k - d3;
        double d7 = x - d4;
        double d8 = y - d5;
        double d9 = z - d6;
        int l;
        int i1;
        int j1;
        int k1;
        int l1;
        int i2;
        if (d7 >= d8) {
            if (d8 >= d9) {
                l = 1;
                i1 = 0;
                j1 = 0;
                k1 = 1;
                l1 = 1;
                i2 = 0;
            } else if (d7 >= d9) {
                l = 1;
                i1 = 0;
                j1 = 0;
                k1 = 1;
                l1 = 0;
                i2 = 1;
            } else {
                l = 0;
                i1 = 0;
                j1 = 1;
                k1 = 1;
                l1 = 0;
                i2 = 1;
            }
        } else if (d8 < d9) {
            l = 0;
            i1 = 0;
            j1 = 1;
            k1 = 0;
            l1 = 1;
            i2 = 1;
        } else if (d7 < d9) {
            l = 0;
            i1 = 1;
            j1 = 0;
            k1 = 0;
            l1 = 1;
            i2 = 1;
        } else {
            l = 0;
            i1 = 1;
            j1 = 0;
            k1 = 1;
            l1 = 1;
            i2 = 0;
        }

        double d10 = d7 - (double) l + 0.16666666666666666;
        double d11 = d8 - (double) i1 + 0.16666666666666666;
        double d12 = d9 - (double) j1 + 0.16666666666666666;
        double d13 = d7 - (double) k1 + 0.3333333333333333;
        double d14 = d8 - (double) l1 + 0.3333333333333333;
        double d15 = d9 - (double) i2 + 0.3333333333333333;
        double d16 = d7 - 1.0 + 0.5;
        double d17 = d8 - 1.0 + 0.5;
        double d18 = d9 - 1.0 + 0.5;
        // Same reasoning as the 2D variant: hash accepts any int, no masking needed.
        int i3 = this.p(i + this.p(j + this.p(k))) % 12;
        int j3 = this.p(i + l + this.p(j + i1 + this.p(k + j1))) % 12;
        int k3 = this.p(i + k1 + this.p(j + l1 + this.p(k + i2))) % 12;
        int l3 = this.p(i + 1 + this.p(j + 1 + this.p(k + 1))) % 12;
        double d19 = this.getCornerNoise3D(i3, d7, d8, d9, 0.6);
        double d20 = this.getCornerNoise3D(j3, d10, d11, d12, 0.6);
        double d21 = this.getCornerNoise3D(k3, d13, d14, d15, 0.6);
        double d22 = this.getCornerNoise3D(l3, d16, d17, d18, 0.6);
        return 32.0 * (d19 + d20 + d21 + d22);
    }
}
