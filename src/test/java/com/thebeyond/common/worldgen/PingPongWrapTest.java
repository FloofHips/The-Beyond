package com.thebeyond.common.worldgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link BeyondEndChunkGenerator#pingPongWrap(int, int, int)}.
 *
 * <p>These tests pin down the properties the terrain generator relies on:
 * (a) output stays within [min, max], (b) the wrap is continuous at the
 * pivot (no seam), (c) it's periodic with period 2*(max-min), and (d) for
 * a symmetric range, the wrap is symmetric around the origin.
 */
class PingPongWrapTest {

    private static final int R = 250000;

    @Test
    void boundariesAreIdempotent() {
        assertEquals(-R, BeyondEndChunkGenerator.pingPongWrap(-R, -R, R));
        assertEquals( R, BeyondEndChunkGenerator.pingPongWrap( R, -R, R));
        assertEquals( 0, BeyondEndChunkGenerator.pingPongWrap( 0, -R, R));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, 123456, -123456, R - 1, -(R - 1), R, -R,
                         R + 1, -(R + 1), 2 * R, 2 * R + 5,
                         10000000, -10000000, Integer.MAX_VALUE / 2})
    void outputAlwaysWithinRange(int input) {
        int out = BeyondEndChunkGenerator.pingPongWrap(input, -R, R);
        assertTrue(out >= -R && out <= R,
                () -> "wrap(" + input + ") = " + out + " not in [" + -R + ", " + R + "]");
    }

    /**
     * The "no seam" property — crossing the pivot at ±R must yield adjacent
     * output values. Vanilla modulo wrap would jump from +R to -R here, which
     * is exactly the visual seam we eliminated by choosing ping-pong. If this
     * test starts failing, terrain has regained a visible reflection line.
     */
    @Test
    void continuityAcrossPivot() {
        int a = BeyondEndChunkGenerator.pingPongWrap(R - 1, -R, R);
        int b = BeyondEndChunkGenerator.pingPongWrap(R,     -R, R);
        int c = BeyondEndChunkGenerator.pingPongWrap(R + 1, -R, R);
        assertEquals(R - 1, a);
        assertEquals(R,     b);
        assertEquals(R - 1, c, "crossing +R must reflect back, not jump to -R");
    }

    /**
     * Period is {@code 2 * (max - min)} — shifting input by that much must be
     * a no-op. For the symmetric range {@code [-R, R]}, range length is
     * {@code L = 2R} and the full triangle-wave period is {@code 2L = 4R}
     * (up-slope from -R→R takes L, then down-slope R→-R takes another L).
     * Shifting by just {@code 2R} would land on the mirrored half of the
     * triangle, not the same value.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 42, -17, 100000, -250000, 249999})
    void periodicityWithDoubleRange(int input) {
        int period = 4 * R; // 2 * (max - min) = 2 * 2R
        int base = BeyondEndChunkGenerator.pingPongWrap(input, -R, R);
        assertEquals(base, BeyondEndChunkGenerator.pingPongWrap(input + period, -R, R));
        assertEquals(base, BeyondEndChunkGenerator.pingPongWrap(input - period, -R, R));
        assertEquals(base, BeyondEndChunkGenerator.pingPongWrap(input + 5 * period, -R, R));
    }

    /** Symmetric range [-R, R] produces wrap(-x) == -wrap(x). */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, 12345, R - 1, R, R + 1, 3 * R / 2, 5000000})
    void symmetryAroundOrigin(int input) {
        int pos = BeyondEndChunkGenerator.pingPongWrap( input, -R, R);
        int neg = BeyondEndChunkGenerator.pingPongWrap(-input, -R, R);
        assertEquals(-pos, neg,
                () -> "symmetry failure: wrap(" + input + ")=" + pos
                    + ", wrap(" + -input + ")=" + neg);
    }
}
