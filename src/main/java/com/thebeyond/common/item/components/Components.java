package com.thebeyond.common.item.components;

public class Components {
    public record DynamicColorComponent (float red, float green, float blue, float alpha, float roffset, float goffset, float boffset, float aoffset, int brightness) {}

    /** On the stack so it syncs to clients; raw RGB, 3 bytes/pixel. */
    public record SnapshotPixelsComponent(int width, int height, byte[] rgb) {
        /** The pixel buffer is present and matches the declared dimensions (3 bytes/pixel). */
        public boolean isRenderable() {
            return width > 0 && height > 0 && rgb != null && rgb.length == width * height * 3;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof SnapshotPixelsComponent c && width == c.width && height == c.height
                    && java.util.Arrays.equals(rgb, c.rgb);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * width + height) + java.util.Arrays.hashCode(rgb);
        }

        @Override
        public String toString() {
            return "SnapshotPixels[" + width + "x" + height + "]";
        }
    }
}



