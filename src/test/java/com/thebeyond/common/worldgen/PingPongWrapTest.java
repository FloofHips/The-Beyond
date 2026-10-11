package com.thebeyond.common.worldgen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link BeyondEndChunkGenerator#pingPongWrap(int, int, int)}: bounded output,
 * no seam at the pivot, period {@code 2*(max-min)}, and symmetry around the origin for symmetric ranges.
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
     * Crossing the pivot at ±R must yield adjacent output values — a modulo wrap would jump
     * from +R to -R, producing a visible reflection line in terrain.
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
     * Full triangle-wave period is {@code 2*(max-min)}, i.e. {@code 4R} for range {@code [-R,R]}
     * (up-slope and down-slope each take {@code 2R}) — shifting by only {@code 2R} hits the mirrored half.
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
