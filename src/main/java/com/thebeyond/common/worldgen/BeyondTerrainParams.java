package com.thebeyond.common.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Datapack-configurable knobs for the Beyond End coordinate transform. Decoded from the
 *  dimension JSON's {@code terrain_params} field (optional, falls back to {@link #DEFAULTS}).
 *  Published to {@link BeyondEndChunkGenerator#activeTerrainParams}; reset on server stop. */
public record BeyondTerrainParams(int wrapRange, double warpAmplitude, double warpScale) {

    /** Wrap pivot outside the ~50k lore radius; low-frequency, low-magnitude warp fuzzes
     *  the pivot reflection line without visibly distorting the interior. */
    public static final BeyondTerrainParams DEFAULTS = new BeyondTerrainParams(
            500000, 50.0, 0.001);

    // Validation bounds. See class javadoc for the reasoning behind each.
    public static final int    MIN_WRAP_RANGE = 50000;
    public static final int    MAX_WRAP_RANGE = 1000000;
    public static final double MIN_WARP_AMPLITUDE = 0.0;     // 0 = disabled
    public static final double MAX_WARP_AMPLITUDE = 500.0;
    public static final double MIN_WARP_SCALE = 1.0e-6;
    public static final double MAX_WARP_SCALE = 1.0e-2;

    /** Validates at construction so the record is always in a sane state regardless of source. */
    public BeyondTerrainParams {
        if (wrapRange < MIN_WRAP_RANGE || wrapRange > MAX_WRAP_RANGE) {
            throw new IllegalArgumentException(
                    "wrap_range must be in [" + MIN_WRAP_RANGE + ", " + MAX_WRAP_RANGE
                            + "], got " + wrapRange);
        }
        if (!(warpAmplitude >= MIN_WARP_AMPLITUDE) || warpAmplitude > MAX_WARP_AMPLITUDE) {
            // `!(amp >= min)` also rejects NaN (any comparison against NaN is false).
            throw new IllegalArgumentException(
                    "warp_amplitude must be in [" + MIN_WARP_AMPLITUDE + ", " + MAX_WARP_AMPLITUDE
                            + "], got " + warpAmplitude);
        }
        if (!(warpScale >= MIN_WARP_SCALE) || warpScale > MAX_WARP_SCALE) {
            throw new IllegalArgumentException(
                    "warp_scale must be in [" + MIN_WARP_SCALE + ", " + MAX_WARP_SCALE
                            + "], got " + warpScale);
        }
        // If amplitude >= wrap_range the warp can push inputs across a full wrap
        // cycle and the transform degenerates into pure noise. Reject early.
        if (warpAmplitude >= wrapRange) {
            throw new IllegalArgumentException(
                    "warp_amplitude (" + warpAmplitude + ") must be smaller than wrap_range ("
                            + wrapRange + ")");
        }
    }

    /** Unvalidated intermediate decode target; see {@link #CODEC}. */
    private record Raw(int wrapRange, double warpAmplitude, double warpScale) {}

    /** Two-stage codec: decode raw fields, then {@code flatXmap} through the record's compact
     *  constructor so its {@link IllegalArgumentException} becomes {@link DataResult#error}
     *  (world load logs the message, doesn't crash). All fields default individually. */
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

    /** Full Codec form for nested use (e.g. {@code BeyondEndBiomeSource} wraps it with {@code optionalFieldOf}). */
    public static final Codec<BeyondTerrainParams> FULL_CODEC = CODEC.codec();
}
