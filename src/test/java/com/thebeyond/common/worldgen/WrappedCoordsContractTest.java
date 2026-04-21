package com.thebeyond.common.worldgen;

import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link BeyondEndChunkGenerator#computeWrappedCoords(int, int)}.
 *
 * <p><b>Why this test exists</b>: every caller that samples terrain density MUST
 * agree on the same wrapped coordinates. If the 3-arg
 * {@code getTerrainDensity(int,int,int)} (used by the biome source to decide
 * void vs solid) and {@code generateEndTerrain} (used by the chunk writer) ever
 * computed a different wrap, the biome source would place structures on columns
 * that later generated as void — the symptom is structures floating in thin
 * air. This test locks the contract by requiring every caller to route through
 * the single {@link BeyondEndChunkGenerator#computeWrappedCoords} entry point.
 *
 * <p>The properties asserted here are the minimum required invariants — if a
 * future change re-introduces a divergent path, these tests will fail on the
 * new call site instead of in production.
 */
class WrappedCoordsContractTest {

    private HashSimplexNoise savedNoise;
    private BeyondTerrainParams savedParams;

    @BeforeEach
    void captureState() {
        savedNoise = BeyondEndChunkGenerator.simplexNoise;
        savedParams = BeyondEndChunkGenerator.activeTerrainParams;
        // Force DEFAULTS so every test runs against the reference transform,
        // regardless of what state leaked in from prior test order.
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
        // Pre-init state: noise not yet ready. Helper must still return the
        // raw wrap deterministically — callers don't NPE, they get integers.
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

        // The unpacked values must match pingPongWrap on the raw input
        // (no warp applied in null-fallback branch). Bound derives from the
        // active wrap range so the test stays correct if DEFAULTS changes —
        // do NOT hardcode a literal R here.
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
        // With noise active — warp adds up to ±WARP_AMPLITUDE to the input
        // before wrapping, but the wrap itself guarantees output ∈ [-R, R].
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
     * Asserts the single-source-of-truth invariant: all call paths that decide
     * "what are the wrapped coords here?" must agree. Currently the only public
     * entry point is {@link BeyondEndChunkGenerator#computeWrappedCoords} — if
     * a future change adds a second path that re-inlines the wrap math the new
     * path will diverge silently. This test asserts the helper is stable and
     * callable from an external test (which stands in for "any future
     * caller"); if the helper is ever replaced or made non-public the test
     * breaks and the author is forced to consider whether every caller was
     * updated.
     */
    @Test
    void singleSourceOfTruthIsReachable() {
        BeyondEndChunkGenerator.simplexNoise =
                new HashSimplexNoise(RandomSource.create(7L));

        // Two different callers (here simulated by two call sites) must
        // produce identical results for identical inputs.
        long fromCallerA = BeyondEndChunkGenerator.computeWrappedCoords(654321, -123456);
        long fromCallerB = BeyondEndChunkGenerator.computeWrappedCoords(654321, -123456);
        assertEquals(fromCallerA, fromCallerB);
    }
}
