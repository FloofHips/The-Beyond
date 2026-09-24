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
    public static final int OUT = 64;            // photo px per side; higher = sharper but heavier NBT. Structural (the upload handler caps width at 256), not a free tweak.
    private static final int COLOR_STEP = 4;    // posterization quant step: 1 = full color, higher = coarser bands (~10 reads retro).

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
                    int sx = cx + (int) (((long) x * side) / OUT);
                    int sy = cy + (int) (((long) y * side) / OUT);

                    if (sx >= w) sx = w - 1;
                    if (sy >= h) sy = h - 1;

                    int p = full.getPixelRGBA(sx, sy);
                    int o = (y * OUT + x) * 3;
                    rgb[o]     = (byte) quant(p & 0xFF);
                    rgb[o + 1] = (byte) quant((p >> 8) & 0xFF);
                    rgb[o + 2] = (byte) quant((p >> 16) & 0xFF);
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
        int q = Math.round(v / (float) 15) * 15;
        return q > 255 ? 255 : q;
    }
}
