package com.thebeyond.common.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Datapack knobs of the End coordinate transform, from the dimension JSON's optional terrain_params. */
public record BeyondTerrainParams(int wrapRange, double warpAmplitude, double warpScale) {

    /** The wrap pivot sits past the 50k lore radius, and a faint warp blurs its reflection line. */
    public static final BeyondTerrainParams DEFAULTS = new BeyondTerrainParams(
            500000, 50.0, 0.001);

    // Validation bounds.
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
        // an amplitude of wrap_range or more pushes inputs across a whole cycle and the transform turns to noise
        if (warpAmplitude >= wrapRange) {
            throw new IllegalArgumentException(
                    "warp_amplitude (" + warpAmplitude + ") must be smaller than wrap_range ("
                            + wrapRange + ")");
        }
    }

    /** Unvalidated decode target for CODEC. */
    private record Raw(int wrapRange, double warpAmplitude, double warpScale) {}

    /** Raw fields default one by one, then the compact constructor turns a bad value into a logged DataResult error. */
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
