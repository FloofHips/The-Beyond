package com.thebeyond.util;

import net.minecraft.util.RandomSource;

public class RandomUtils {
    private static final RandomSource randomSource = RandomSource.createThreadSafe();

    public static boolean nextBoolean() {
        return randomSource.nextBoolean();
    }

    public static int nextInt() {
        return randomSource.nextInt();
    }

    public static int nextInt(int max) {
        return (int) Math.ceil(max * randomSource.nextFloat());
    }

    public static int nextInt(int min, int max) {
        return (int) Math.ceil(min + (max - min) * randomSource.nextFloat());
    }

    public static double nextDouble() {
        return randomSource.nextDouble();
    }

    public static double nextDouble(double max) {
        return max * randomSource.nextDouble();
    }

    public static double nextDouble(double min, double max) {
        return min + (max - min) * randomSource.nextDouble();
    }

    public static float nextFloat() {
        return randomSource.nextFloat();
    }

    public static float nextFloat(float max) {
        return max * randomSource.nextFloat();
    }

    public static float nextFloat(float min, float max) {
        return min + (max - min) * randomSource.nextFloat();
    }
}
