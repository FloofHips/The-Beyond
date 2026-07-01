package com.thebeyond.common.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * A data-driven snapshot filter: a luminance ramp blended over the real colors at decode time (CPU, no shaders).
 * Loaded from {@code data/<ns>/the_beyond/grade/*.json}; empty stops = passthrough.
 */
public record Grade(int[][] stops, float strength) {
    /** A single stop, authored as a {@code "#RRGGBB"} hex string. */
    public static final Codec<int[]> COLOR = Codec.STRING.comapFlatMap(Grade::parseColor, Grade::colorToHex);

    public static final Codec<Grade> CODEC = RecordCodecBuilder.create(i -> i.group(
            COLOR.listOf().fieldOf("stops").forGetter(g -> Arrays.asList(g.stops())),
            Codec.FLOAT.optionalFieldOf("strength", 0.45f).forGetter(Grade::strength)
    ).apply(i, (stops, strength) -> new Grade(stops.toArray(int[][]::new), strength)));

    /** Small data — the network codec is just the full codec. */
    public static final Codec<Grade> NETWORK_CODEC = CODEC;

    private static DataResult<int[]> parseColor(String s) {
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.length() != 6) {
            return DataResult.error(() -> "Expected #RRGGBB color, got: " + s);
        }
        try {
            int v = Integer.parseInt(hex, 16);
            return DataResult.success(new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF});
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Invalid hex color: " + s);
        }
    }

    private static String colorToHex(int[] rgb) {
        return String.format("#%02x%02x%02x", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
    }

    /** Returns ABGR (0xFF_BB_GG_RR) for NativeImage. */
    public int applyAbgr(int r, int g, int b) {
        if (stops.length == 0) {
            return 0xFF000000 | (b << 16) | (g << 8) | r;
        }
        float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        float pos = Math.max(0f, Math.min(1f, lum)) * (stops.length - 1);
        int idx = Math.min((int) pos, stops.length - 2);
        if (idx < 0) {
            idx = 0; // single-stop grade: flat tint
        }
        int hi = Math.min(idx + 1, stops.length - 1);
        float f = pos - idx;
        int tr = (int) (stops[idx][0] + (stops[hi][0] - stops[idx][0]) * f);
        int tg = (int) (stops[idx][1] + (stops[hi][1] - stops[idx][1]) * f);
        int tb = (int) (stops[idx][2] + (stops[hi][2] - stops[idx][2]) * f);
        int nr = (int) (r + (tr - r) * strength);
        int ng = (int) (g + (tg - g) * strength);
        int nb = (int) (b + (tb - b) * strength);
        return 0xFF000000 | (nb << 16) | (ng << 8) | nr;
    }
}
