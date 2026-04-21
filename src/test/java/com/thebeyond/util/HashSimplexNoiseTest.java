package com.thebeyond.util;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link HashSimplexNoise}.
 *
 * <p>These tests focus on properties the Beyond terrain generator relies on:
 * determinism per seed, finite output everywhere (no NaN/Infinity even at
 * wrap-boundary coordinates), and stability of the SplitMix64-based
 * permutation hash. They do NOT assert exact output values against vanilla
 * SimplexNoise — this class intentionally produces a different sampling of
 * the infinite noise field (see class javadoc).
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
        // Sample enough points that IDENTICAL output streams across seeds
        // are astronomically unlikely. Checking a single point is fragile;
        // a handful is overwhelmingly robust.
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
     * Canonical Simplex output stays within roughly [-1, 1]. Because we mirror
     * vanilla SimplexNoise's {@code * 70.0} / {@code * 32.0} scaling constants
     * the exact bound can drift slightly, but values outside [-1.5, 1.5] would
     * indicate the permutation hash is producing gradients outside the
     * expected 12-entry table. Use a generous bound so this test fails LOUDLY
     * only on real regressions, not on normal simplex wobble.
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

    /**
     * Vanilla SimplexNoise has a 256-unit permutation period — spaced samples
     * at 0 and 256 would be correlated. {@link HashSimplexNoise}'s hash-based
     * permutation should NOT produce that correlation. Sampling across a
     * neighborhood at 0 and at {@code step} (including 65 536, the old
     * wide-table period) should give independent values somewhere. Not a
     * tight statistical test — just smokes out a regression where someone
     * reintroduces a 256-entry table.
     *
     * <h2>Why we sample a neighborhood instead of a single integer point</h2>
     * A single-point comparison like {@code getValue(0,0) vs getValue(step,0)}
     * is FRAGILE on integer-aligned coordinates. Canonical Simplex returns
     * exactly 0 at every lattice point: the {@code (x-floor(...))*G2} skew/
     * unskew round-trip lands on the cell origin, and {@code dot(gradient, 0,
     * 0, 0) = 0} for every gradient. Additionally, at large integer inputs
     * the skewed fractional offsets drift outside the simplex radial cutoff
     * (because the cumulative rounding in {@code (i+j)*G2} drifts a whole
     * simplex cell away), which also produces exactly 0 through the
     * {@code max(0, 0.5 - r²)⁴} falloff. Both endpoints being 0 at specific
     * {@code step} values (empirically: 65 536 and 100 000 with seed 4242)
     * gave a spurious "period detected" result that had nothing to do with
     * the permutation function. By sampling several non-integer offsets in
     * each neighborhood and asking for ANY pair to differ, we ask the exact
     * question the test name promises — "is there a visible period?" —
     * without letting simplex's grid-point degeneracy answer for us.
     */
    @ParameterizedTest
    @ValueSource(ints = {256, 1024, 65536, 100000})
    void noVisiblePeriodAtVanillaBoundaries(int step) {
        HashSimplexNoise n = new HashSimplexNoise(RandomSource.create(4242L));
        // Non-integer offsets chosen to avoid any special alignment with the
        // skewed simplex grid (F2, G2 irrational → no finite-decimal offset
        // lands on a grid boundary, but mix a few to be robust to any single
        // accidental alignment).
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
