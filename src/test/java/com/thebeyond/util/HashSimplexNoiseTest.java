package com.thebeyond.util;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link HashSimplexNoise}: per-seed determinism, finite output everywhere, and
 * permutation stability. Does not assert exact values against vanilla SimplexNoise, which samples differently.
 */
class HashSimplexNoiseTest {

    // ---------- determinism ----------

    @Test
    void sameSeedSameOutput2D() {
        HashSimplexNoise a = new HashSimplexNoise(RandomSource.create(42L));
        HashSimplexNoise b = new HashSimplexNoise(RandomSource.create(42L));
        for (double x = -5; x <= 5; x += 0.37) {
            for (double y = -5; y <= 5; y += 0.41) {
                assertEquals(a.getValue(x, y), b.getValue(x, y), 0.0,
                        () -> "seed reproducibility broken for 2D noise");
            }
        }
    }

    @Test
    void sameSeedSameOutput3D() {
        HashSimplexNoise a = new HashSimplexNoise(RandomSource.create(1234L));
        HashSimplexNoise b = new HashSimplexNoise(RandomSource.create(1234L));
        for (double x = -3; x <= 3; x += 0.53) {
            for (double y = -3; y <= 3; y += 0.47) {
                for (double z = -3; z <= 3; z += 0.41) {
                    assertEquals(a.getValue(x, y, z), b.getValue(x, y, z), 0.0,
                            () -> "seed reproducibility broken for 3D noise");
                }
            }
        }
    }

    @Test
    void differentSeedsDifferentOutputs() {
        HashSimplexNoise a = new HashSimplexNoise(RandomSource.create(1L));
        HashSimplexNoise b = new HashSimplexNoise(RandomSource.create(2L));
        // Sample enough points that identical streams across seeds are astronomically unlikely.
        int differences = 0;
        for (int i = 0; i < 50; i++) {
            if (a.getValue(i * 0.1, i * 0.2) != b.getValue(i * 0.1, i * 0.2)) {
                differences++;
            }
        }
        // Copy to an effectively-final local for the lambda capture.
        final int finalDifferences = differences;
        assertTrue(finalDifferences > 40,
                () -> "expected most samples to differ between seeds, got "
                    + finalDifferences + "/50");
    }

    // ---------- finite everywhere ----------

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "0.001, 0.001",
            "250, 250",                     // warp-sample input magnitude: globalX × warpScale at mid-wrap interior
            "30000, 30000",                 // post-wrap × max horizontal scale (wrapRange × 0.12)
            "250000, 250000",               // interior point below the ~720k Simplex precision cliff (pre-scale)
            "500, -500",
            "-1.5, 3.14159",
            "99999.9, 99999.9"
    })
    void noiseFiniteAtBoundaryInputs2D(double x, double y) {
        HashSimplexNoise n = new HashSimplexNoise(RandomSource.create(99L));
        double v = n.getValue(x, y);
        assertTrue(Double.isFinite(v),
                () -> "2D getValue(" + x + ", " + y + ") = " + v + " (not finite)");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 0",
            "1, 1, 1",
            "30000, 64, 30000",            // post-wrap×scale at high-y block
            "250, 256, -250",
            "-99999, 192, 99999"
    })
    void noiseFiniteAtBoundaryInputs3D(double x, double y, double z) {
        HashSimplexNoise n = new HashSimplexNoise(RandomSource.create(77L));
        double v = n.getValue(x, y, z);
        assertTrue(Double.isFinite(v),
                () -> "3D getValue(" + x + ", " + y + ", " + z + ") = " + v);
    }

    // ---------- output range ----------

    /**
     * Generous [-1.5, 1.5] bound (vs. canonical Simplex's ~[-1, 1]) so this only fails on real
     * regressions — like gradients escaping the expected 12-entry table — not normal wobble.
     */
    @Test
    void outputStaysWithinReasonableRange() {
        HashSimplexNoise n = new HashSimplexNoise(RandomSource.create(555L));
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 1000; i++) {
            double x = i * 0.123;
            double z = i * 0.456;
            double v = n.getValue(x, z);
            if (v < min) min = v;
            if (v > max) max = v;
        }
        // Copy to effectively-final locals for the lambda capture.
        final double finalMin = min;
        final double finalMax = max;
        assertTrue(finalMin > -1.5 && finalMax < 1.5,
                () -> "range [" + finalMin + ", " + finalMax + "] outside expected [-1.5, 1.5]");
    }

    // ---------- period sanity ----------

    /** Checks the hash permutation doesn't repeat at {@code step}, using non-integer offsets since
     *  Simplex returns exactly 0 on lattice points and would otherwise look falsely periodic. */
    @ParameterizedTest
    @ValueSource(ints = {256, 1024, 65536, 100000})
    void noVisiblePeriodAtVanillaBoundaries(int step) {
        HashSimplexNoise n = new HashSimplexNoise(RandomSource.create(4242L));
        // Several non-integer offsets, robust against any single accidental grid alignment.
        double[] offsets = {0.1, 0.37, 0.73, 1.23};
        boolean anyDiffers = false;
        for (double ox : offsets) {
            double a = n.getValue(ox, ox);
            double b = n.getValue(step + ox, ox);
            if (Math.abs(a - b) > 1e-9) {
                anyDiffers = true;
                break;
            }
        }
        assertTrue(anyDiffers,
                () -> "noise repeats at step " + step
                        + " across all sampled offsets — hash permutation broken");
    }
}
