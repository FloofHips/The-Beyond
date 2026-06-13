package com.thebeyond.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.network.SnapshotUploadPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.network.PacketDistributor;

/** Center-crops an offscreen capture FBO, downsamples to {@link #OUT} square, quantizes, and uploads it as raw RGB. */
public final class SnapshotCapture {
    public static final int OUT = 32;            // photo px per side; higher = sharper but heavier NBT. Structural (the upload handler caps width at 256), not a free tweak.
    private static final int COLOR_STEP = 10;    // posterization quant step: 1 = full color, higher = coarser bands (~10 reads retro).

    private SnapshotCapture() {
    }

    /** {@code finally} rebinds the main target for the following HUD pass. */
    public static void downsampleQuantizeUpload(RenderTarget src, long requestId) {
        NativeImage full = null;
        try {
            full = Screenshot.takeScreenshot(src); // ABGR; ours to close
            int w = full.getWidth(), h = full.getHeight();
            int side = Math.min(w, h);
            int cx = (w - side) / 2, cy = (h - side) / 2;
            byte[] rgb = new byte[OUT * OUT * 3];
            for (int y = 0; y < OUT; y++) {
                for (int x = 0; x < OUT; x++) {
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    int sx0 = cx + x * side / OUT, sx1 = cx + (x + 1) * side / OUT;
                    int sy0 = cy + y * side / OUT, sy1 = cy + (y + 1) * side / OUT;
                    for (int sy = sy0; sy < sy1; sy++) {
                        for (int sx = sx0; sx < sx1; sx++) {
                            int p = full.getPixelRGBA(sx, sy); // ABGR 0xAA_BB_GG_RR
                            r += p & 0xFF;
                            g += (p >> 8) & 0xFF;
                            b += (p >> 16) & 0xFF;
                            n++;
                        }
                    }
                    if (n == 0) {
                        n = 1;
                    }
                    int o = (y * OUT + x) * 3;
                    rgb[o] = (byte) quant((int) (r / n));
                    rgb[o + 1] = (byte) quant((int) (g / n));
                    rgb[o + 2] = (byte) quant((int) (b / n));
                }
            }
            PacketDistributor.sendToServer(new SnapshotUploadPayload(requestId, OUT, OUT, rgb));
        } catch (Exception e) {
            TheBeyond.LOGGER.error("[camera] capture failed", e);
        } finally {
            if (full != null) {
                full.close();
            }
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    private static int quant(int v) {
        int q = Math.round(v / (float) COLOR_STEP) * COLOR_STEP;
        return q > 255 ? 255 : q;
    }
}
