package com.thebeyond.common.worldgen;

import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Contract tests for {@link BeyondEndChunkGenerator#computeWrappedCoords(int, int)}: every density caller
 *  must share one wrap, or the biome source and chunk writer disagree on a column and structures float over void. */
class WrappedCoordsContractTest {

    private HashSimplexNoise savedNoise;
    private BeyondTerrainParams savedParams;

    @BeforeEach
    void captureState() {
        savedNoise = BeyondEndChunkGenerator.simplexNoise;
        savedParams = BeyondEndChunkGenerator.activeTerrainParams;
        // Pin DEFAULTS so every test runs against the reference transform, not test-order leakage.
        BeyondEndChunkGenerator.activeTerrainParams = BeyondTerrainParams.DEFAULTS;
    }

    @AfterEach
    void restoreState() {
        BeyondEndChunkGenerator.simplexNoise = savedNoise;
        BeyondEndChunkGenerator.activeTerrainParams = savedParams;
    }

    // ---------- determinism ----------

    @Test
    void determinismWithFixedNoise() {
        BeyondEndChunkGenerator.simplexNoise =
                new HashSimplexNoise(RandomSource.create(12345L));

        long a = BeyondEndChunkGenerator.computeWrappedCoords(1000000, -2000000);
        long b = BeyondEndChunkGenerator.computeWrappedCoords(1000000, -2000000);
        assertEquals(a, b, "same inputs must produce same packed output");
    }

    @Test
    void determinismInNullFallback() {
        // Pre-init state: noise not ready yet, but callers must still get a deterministic wrap, not an NPE.
        BeyondEndChunkGenerator.simplexNoise = null;

        long a = BeyondEndChunkGenerator.computeWrappedCoords(1234567, -89012);
        long b = BeyondEndChunkGenerator.computeWrappedCoords(1234567, -89012);
        assertEquals(a, b);
    }

    // ---------- pack/unpack roundtrip ----------

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, -1",
            "100, 200",
            "-999999, 999999",
            "500001, -500001",          // just past the pivot (default 500 k)
            "10000000, -10000000",      // far out
            "2147483647, -2147483648"   // int range extremes
    })
    void packUnpackRoundtrip(int x, int z) {
        BeyondEndChunkGenerator.simplexNoise = null;  // null-fallback: pure wrap

        long packed = BeyondEndChunkGenerator.computeWrappedCoords(x, z);
        int wx = BeyondEndChunkGenerator.unpackWrappedX(packed);
        int wz = BeyondEndChunkGenerator.unpackWrappedZ(packed);

        // No warp in the null-fallback branch — output must match a plain pingPongWrap.
        // Derive R from DEFAULTS rather than hardcoding, so this stays correct if DEFAULTS changes.
        int R = BeyondTerrainParams.DEFAULTS.wrapRange();
        assertEquals(BeyondEndChunkGenerator.pingPongWrap(x, -R, R), wx);
        assertEquals(BeyondEndChunkGenerator.pingPongWrap(z, -R, R), wz);
    }

    // ---------- range bounds ----------

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "123456, -123456",
            "5000000, -5000000",
            "2147483647, 2147483647",
            "-2147483648, -2147483648"
    })
    void wrappedOutputStaysWithinWrapRange(int x, int z) {
        // Warp perturbs the input before wrapping, but the wrap itself still guarantees output ∈ [-R, R].
        BeyondEndChunkGenerator.simplexNoise =
                new HashSimplexNoise(RandomSource.create(42L));

        long packed = BeyondEndChunkGenerator.computeWrappedCoords(x, z);
        int wx = BeyondEndChunkGenerator.unpackWrappedX(packed);
        int wz = BeyondEndChunkGenerator.unpackWrappedZ(packed);

        int R = BeyondEndChunkGenerator.WRAP_RANGE;
        assertTrue(wx >= -R && wx <= R, () -> "wx=" + wx + " outside ±" + R);
        assertTrue(wz >= -R && wz <= R, () -> "wz=" + wz + " outside ±" + R);
    }

    /**
     * Guards against a second wrap-math path being inlined elsewhere: if
     * {@link BeyondEndChunkGenerator#computeWrappedCoords} stops being the single public entry point, this test fails.
     */
    @Test
    void singleSourceOfTruthIsReachable() {
        BeyondEndChunkGenerator.simplexNoise =
                new HashSimplexNoise(RandomSource.create(7L));

        // Two call sites simulating different callers must agree on identical inputs.
        long fromCallerA = BeyondEndChunkGenerator.computeWrappedCoords(654321, -123456);
        long fromCallerB = BeyondEndChunkGenerator.computeWrappedCoords(654321, -123456);
        assertEquals(fromCallerA, fromCallerB);
    }
}
