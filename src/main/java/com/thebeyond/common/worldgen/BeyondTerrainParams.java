package com.thebeyond.common.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Datapack-configurable knobs that govern the Beyond End coordinate transform
 * ({@code wrap_range}, {@code warp_amplitude}, {@code warp_scale}).
 *
 * <h2>Why this is a data object, not a set of constants</h2>
 * Datapack authors who want to experiment with a tighter wrap (smaller worlds,
 * stress-testing the pivot) or a different warp feel can override any of these
 * via the {@code terrain_params} field on the dimension JSON, without forking
 * and recompiling.
 *
 * <h2>Why the validation is strict</h2>
 * Each knob has a narrow sane range:
 * <ul>
 *   <li>{@code wrap_range} too small (&lt; 50 000) makes the pivot visible in
 *       normal gameplay — every long elytra flight hits a reflection.
 *   <li>{@code wrap_range} too large (&gt; 1 000 000) re-enters the Simplex
 *       precision danger zone, re-introducing the stretching the wrap exists
 *       to prevent.
 *   <li>{@code warp_amplitude} too large (&gt; 500) produces visible
 *       low-frequency distortion EVERYWHERE, not just at the pivot — the cure
 *       becomes worse than the disease.
 *   <li>{@code warp_scale} outside {@code (0, 0.01]} yields warp wavelengths
 *       either shorter than the warp amplitude itself (produces a noisy
 *       texture-pattern rather than a gentle bend) or so long that the pivot
 *       seam is not effectively hidden.
 * </ul>
 * The codec rejects out-of-range values with an explicit error rather than
 * silently clamping. Silent clamping would mask a mis-configured datapack
 * and produce terrain that looks right to the author but broken to players
 * hundreds of thousands of blocks away — a diagnosis nightmare.
 *
 * <h2>Lifecycle</h2>
 * An instance is constructed from the dimension JSON by the biome source
 * codec (optional field; defaults to {@link #DEFAULTS}). The biome source
 * writes it to {@link BeyondEndChunkGenerator#activeTerrainParams} during
 * its own construction. The chunk generator's static
 * {@code computeWrappedCoords} reads from that static field on every
 * terrain sample. The field is reset to {@link #DEFAULTS} on server stop
 * alongside the noise instances, so no configuration leaks between worlds.
 */
public record BeyondTerrainParams(int wrapRange, double warpAmplitude, double warpScale) {

    /**
     * Default values used when a dimension JSON omits {@code terrain_params}.
     *
     * <ul>
     *   <li>{@code wrapRange=500000}: places the ping-pong pivot outside the
     *       lore-gameplay radius (~50 k).
     *   <li>{@code warpAmplitude=50} / {@code warpScale=0.001}: low-frequency,
     *       low-magnitude domain warp — fuzzes the pivot reflection line
     *       without producing visible distortion in the interior.
     * </ul>
     */
    public static final BeyondTerrainParams DEFAULTS = new BeyondTerrainParams(
            500000, 50.0, 0.001);

    // Validation bounds. See class javadoc for the reasoning behind each.
    public static final int    MIN_WRAP_RANGE = 50000;
    public static final int    MAX_WRAP_RANGE = 1000000;
    public static final double MIN_WARP_AMPLITUDE = 0.0;     // 0 = disabled
    public static final double MAX_WARP_AMPLITUDE = 500.0;
    public static final double MIN_WARP_SCALE = 1.0e-6;
    public static final double MAX_WARP_SCALE = 1.0e-2;

    /**
     * Compact constructor — validates at construction time so the record is
     * always in a sane state, regardless of source (codec, test, direct new).
     */
    public BeyondTerrainParams {
        if (wrapRange < MIN_WRAP_RANGE || wrapRange > MAX_WRAP_RANGE) {
            throw new IllegalArgumentException(
                    "wrap_range must be in [" + MIN_WRAP_RANGE + ", " + MAX_WRAP_RANGE
                            + "], got " + wrapRange);
        }
        if (!(warpAmplitude >= MIN_WARP_AMPLITUDE) || warpAmplitude > MAX_WARP_AMPLITUDE) {
            // Note: the NaN check is encoded via `!(amp >= min)` — NaN fails
            // any comparison so this rejects NaN even though "NaN" cannot
            // literally appear in JSON (JSON has no NaN), defensive anyway.
            throw new IllegalArgumentException(
                    "warp_amplitude must be in [" + MIN_WARP_AMPLITUDE + ", " + MAX_WARP_AMPLITUDE
                            + "], got " + warpAmplitude);
        }
        if (!(warpScale >= MIN_WARP_SCALE) || warpScale > MAX_WARP_SCALE) {
            throw new IllegalArgumentException(
                    "warp_scale must be in [" + MIN_WARP_SCALE + ", " + MAX_WARP_SCALE
                            + "], got " + warpScale);
        }
        // Sanity cross-check: warp must not be able to push inputs into the
        // next wrap cycle. If amplitude >= wrap_range the wrap behavior
        // degenerates into pure noise. Reject early.
        if (warpAmplitude >= wrapRange) {
            throw new IllegalArgumentException(
                    "warp_amplitude (" + warpAmplitude + ") must be smaller than wrap_range ("
                            + wrapRange + ")");
        }
    }

    /**
     * Unvalidated raw holder used as the intermediate decode target. Kept
     * private and inside this class because it exists only to decouple the
     * codec's field-by-field decoding from the record's strict compact
     * constructor — see {@link #CODEC} javadoc.
     */
    private record Raw(int wrapRange, double warpAmplitude, double warpScale) {}

    /**
     * Codec for datapack JSON. All three fields are optional; omitting a
     * field falls back to the {@link #DEFAULTS} value for that knob
     * individually. This means a dimension JSON can tweak a single parameter
     * without re-stating the others.
     *
     * <h2>Two-stage decode: raw tuple → validated record</h2>
     * {@code RecordCodecBuilder} calls the constructor reference passed to
     * {@code apply()} DURING decoding. If we pass {@code BeyondTerrainParams::new}
     * directly, the record's compact constructor throws
     * {@link IllegalArgumentException} for out-of-range values at that exact
     * point — BEFORE any {@code Codec#validate} or post-parse hook can run.
     * The IAE then propagates out of {@code Codec#parse} as a thrown
     * exception, crashing the world load instead of producing the
     * {@link DataResult#error} the caller expects.
     *
     * <p>To move the validation into the {@code DataResult} flow we decode
     * into an unvalidated {@link Raw} first and then {@code flatXmap} it
     * through the record constructor, catching the IAE and converting its
     * message to a clean {@code DataResult.error}. The JSON error message
     * is preserved verbatim, so datapack authors still see
     * {@code "wrap_range must be in [50000, 1000000], got 10"} in logs —
     * just without a stack trace or world crash.
     */
    public static final MapCodec<BeyondTerrainParams> CODEC = RecordCodecBuilder.<Raw>mapCodec(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("wrap_range", DEFAULTS.wrapRange())
                            .forGetter(Raw::wrapRange),
                    Codec.DOUBLE.optionalFieldOf("warp_amplitude", DEFAULTS.warpAmplitude())
                            .forGetter(Raw::warpAmplitude),
                    Codec.DOUBLE.optionalFieldOf("warp_scale", DEFAULTS.warpScale())
                            .forGetter(Raw::warpScale)
            ).apply(instance, Raw::new)
    ).flatXmap(
            raw -> {
                try {
                    return DataResult.success(new BeyondTerrainParams(
                            raw.wrapRange(), raw.warpAmplitude(), raw.warpScale()));
                } catch (IllegalArgumentException ex) {
                    return DataResult.error(ex::getMessage);
                }
            },
            params -> DataResult.success(new Raw(
                    params.wrapRange(), params.warpAmplitude(), params.warpScale()))
    );

    /**
     * Also expose the full Codec for nested use — {@code BeyondEndBiomeSource}
     * wraps it with {@code .optionalFieldOf(...)}.
     */
    public static final Codec<BeyondTerrainParams> FULL_CODEC = CODEC.codec();
}
