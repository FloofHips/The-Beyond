package com.thebeyond.common.worldgen;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for {@link BeyondTerrainParams} construction and codec
 * validation. The validation rules here exist specifically to prevent
 * datapack authors from silently producing a broken dimension — the test
 * suite locks the bounds so a loosening refactor has to explicitly update
 * both the class and the tests.
 */
class BeyondTerrainParamsTest {

    // ---------- construction ----------

    @Test
    void defaultsAreValid() {
        // Record compact-constructor validation runs on DEFAULTS at class init.
        // If DEFAULTS ever drifts outside its own bounds, class loading fails.
        assertDoesNotThrow(() -> new BeyondTerrainParams(
                BeyondTerrainParams.DEFAULTS.wrapRange(),
                BeyondTerrainParams.DEFAULTS.warpAmplitude(),
                BeyondTerrainParams.DEFAULTS.warpScale()));
    }

    @ParameterizedTest
    @CsvSource({
            "49999,   50.0, 0.001",   // wrap_range below min
            "1000001, 50.0, 0.001",   // wrap_range above max
            "250000, -1.0,  0.001",   // amplitude negative
            "250000, 501.0, 0.001",   // amplitude above max
            "250000, 50.0,  0.0",     // scale zero
            "250000, 50.0,  0.011",   // scale above max
            "100,    50.0,  0.001",   // wrap tiny, amplitude vs wrap sanity still
            "100000, 100001.0, 0.001" // amplitude > wrap_range (pathological)
    })
    void outOfRangeValuesThrow(int wrap, double amp, double scale) {
        assertThrows(IllegalArgumentException.class,
                () -> new BeyondTerrainParams(wrap, amp, scale));
    }

    @Test
    void warpAmplitudeZeroIsAllowed() {
        // Explicit "disable warp" is a valid configuration — the pivot seam
        // will be visible at the wrap boundary, but that's the author's call.
        assertDoesNotThrow(() -> new BeyondTerrainParams(250000, 0.0, 0.001));
    }

    @Test
    void amplitudeMustBeStrictlyLessThanWrap() {
        // Boundary: amplitude == wrap_range would mean a sample could be
        // pushed into the next wrap cycle. Reject.
        assertThrows(IllegalArgumentException.class,
                () -> new BeyondTerrainParams(400, 400.0, 0.001));
    }

    // ---------- codec ----------

    @Test
    void codecDecodesEmptyObjectAsDefaults() {
        JsonObject json = new JsonObject();
        DataResult<BeyondTerrainParams> result = BeyondTerrainParams.FULL_CODEC
                .parse(JsonOps.INSTANCE, json);

        BeyondTerrainParams parsed = result.result().orElseThrow();
        assertEquals(BeyondTerrainParams.DEFAULTS, parsed);
    }

    @Test
    void codecAllowsPartialOverride() {
        JsonObject json = new JsonObject();
        json.addProperty("wrap_range", 500000);
        // warp_amplitude and warp_scale unspecified → default fallback per-field

        BeyondTerrainParams parsed = BeyondTerrainParams.FULL_CODEC
                .parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(500000, parsed.wrapRange());
        assertEquals(BeyondTerrainParams.DEFAULTS.warpAmplitude(), parsed.warpAmplitude());
        assertEquals(BeyondTerrainParams.DEFAULTS.warpScale(), parsed.warpScale());
    }

    @Test
    void codecRejectsOutOfRangeWithDataResultError() {
        JsonObject json = new JsonObject();
        json.addProperty("wrap_range", 10);  // way below min

        DataResult<BeyondTerrainParams> result = BeyondTerrainParams.FULL_CODEC
                .parse(JsonOps.INSTANCE, json);

        assertTrue(result.error().isPresent(),
                "codec must surface validation failure as DataResult.error, not a crash");
        String msg = result.error().get().message();
        assertTrue(msg.contains("wrap_range"),
                () -> "error message should mention the offending field, got: " + msg);
    }

    @Test
    void codecRoundTrips() {
        BeyondTerrainParams original = new BeyondTerrainParams(300000, 40.0, 0.002);

        DataResult<JsonElement> encoded = BeyondTerrainParams.FULL_CODEC
                .encodeStart(JsonOps.INSTANCE, original);
        JsonElement json = encoded.result().orElseThrow();

        BeyondTerrainParams decoded = BeyondTerrainParams.FULL_CODEC
                .parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(original, decoded);
    }
}
