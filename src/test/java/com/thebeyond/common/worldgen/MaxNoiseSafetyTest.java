package com.thebeyond.common.worldgen;

import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Safety gate for the air-gap density-skip's {@code |getValue| <= MAX_NOISE} assumption, which
 * {@link HashSimplexNoise#getValue} has no analytic clamp to guarantee — must be measured instead.
 */
class MaxNoiseSafetyTest {

    @Test
    void getValuePeakStaysBelowMaxNoise() {
        final double bound = BeyondEndChunkGenerator.MAX_NOISE;
        double peak = 0.0;
        double pkx = 0, pky = 0, pkz = 0;
        // Many seeds (different gradient permutations) × a broad random sweep over the coord ranges the terrain
        // sampler actually reaches (wrapped XZ * band freqs ~ small; sampleY = shiftedY*vScale).
        long[] seeds = { 42L, 1L, 7L, 123456L, 99999L, 2024L };
        for (long seed : seeds) {
            HashSimplexNoise noise = new HashSimplexNoise(RandomSource.create(seed));
            java.util.Random r = new java.util.Random(seed ^ 0x9E3779B97F4A7C15L);
            for (int i = 0; i < 6_000_000; i++) {
                double x = (r.nextDouble() - 0.5) * 20000.0;
                double y = (r.nextDouble() - 0.5) * 2000.0;
                double z = (r.nextDouble() - 0.5) * 20000.0;
                double v = Math.abs(noise.getValue(x, y, z));
                if (v > peak) { peak = v; pkx = x; pky = y; pkz = z; }
            }
            // Hill-climb from the random peak — local search catches a sharper maximum the sweep stepped over.
            double step = 0.5;
            for (int round = 0; round < 4000; round++) {
                double bx = pkx, by = pky, bz = pkz, bv = peak;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            double v = Math.abs(noise.getValue(pkx + dx * step, pky + dy * step, pkz + dz * step));
                            if (v > bv) { bv = v; bx = pkx + dx * step; by = pky + dy * step; bz = pkz + dz * step; }
                        }
                if (bv > peak) { peak = bv; pkx = bx; pky = by; pkz = bz; }
                else step *= 0.5;   // converged at this scale → refine
                if (step < 1e-6) break;
            }
        }
        System.out.printf("[MaxNoiseSafety] measured peak |getValue| = %.5f  (MAX_NOISE = %.2f, headroom %.1f%%)%n",
                peak, bound, 100.0 * (bound - peak) / bound);
        Assertions.assertTrue(peak < bound,
                "measured |HashSimplexNoise.getValue| peak " + peak + " >= MAX_NOISE " + bound
                        + " — the air-gap density-skip's bound is INVALID and could delete a solid block. RAISE MAX_NOISE.");
    }
}
