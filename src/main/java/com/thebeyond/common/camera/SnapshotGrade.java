package com.thebeyond.common.camera;

/** Luminance-mapped gradient blended over the real colors on the CPU at decode time; no shaders. */
public enum SnapshotGrade {
    // Stops are a luminance ramp, dark -> light (shadow / mid / highlight), as per-channel hex {0xRR, 0xGG, 0xBB}.
    NONE(null),
    SEPIA(new int[][]{{0x2b, 0x1d, 0x0e}, {0x7a, 0x52, 0x30}, {0xf0, 0xe0, 0xc0}}),
    BLUE(new int[][]{{0x0c, 0x08, 0x26}, {0x31, 0x33, 0x4b}, {0x79, 0x89, 0xa9}, {0xe8, 0xf4, 0xff}});

    private static final float STRENGTH = 0.45f; // 0 = real colors, 1 = full duotone
    private final int[][] stops;

    SnapshotGrade(int[][] stops) {
        this.stops = stops;
    }

    /** Returns ABGR packed as 0xFF_BB_GG_RR for NativeImage. */
    public int applyAbgr(int r, int g, int b) {
        if (stops == null) {
            return 0xFF000000 | (b << 16) | (g << 8) | r;
        }
        float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
        float pos = Math.max(0f, Math.min(1f, lum)) * (stops.length - 1);
        int i = Math.min((int) pos, stops.length - 2);
        float f = pos - i;
        int tr = (int) (stops[i][0] + (stops[i + 1][0] - stops[i][0]) * f);
        int tg = (int) (stops[i][1] + (stops[i + 1][1] - stops[i][1]) * f);
        int tb = (int) (stops[i][2] + (stops[i + 1][2] - stops[i][2]) * f);
        int nr = (int) (r + (tr - r) * STRENGTH);
        int ng = (int) (g + (tg - g) * STRENGTH);
        int nb = (int) (b + (tb - b) * STRENGTH);
        return 0xFF000000 | (nb << 16) | (ng << 8) | nr;
    }
}
