package com.thebeyond.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.thebeyond.TheBeyond;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;

public final class CameraGuiBits {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int PANEL_HI = 0xFFFFFFFF;
    public static final int PANEL_LO = 0xFF555555;
    public static final int SLOT_FACE = 0xFF8B8B8B;
    public static final int SLOT_HI = 0xFFFFFFFF;
    public static final int SLOT_LO = 0xFF373737;
    public static final int OUTLINE = 0xFF1B1B1F;
    private static final int PAPER_FILL = 0x00555555;
    private static final int PAPER_OUTLINE = 0x00E6E6E6;
    private static final ResourceLocation PAPER_TEX = ResourceLocation.withDefaultNamespace("textures/item/paper.png");

    private static ResourceLocation ghostTex;
    private static boolean ghostFailed;

    private CameraGuiBits() {
    }

    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int face, int tl, int br) {
        g.fill(x, y, x + w, y + h, face);
        g.fill(x, y, x + w, y + 1, tl);
        g.fill(x, y, x + 1, y + h, tl);
        g.fill(x, y + h - 1, x + w, y + h, br);
        g.fill(x + w - 1, y, x + w, y + h, br);
    }

    public static void sunkenSlot(GuiGraphics g, int x, int y) {
        bevel(g, x, y, 18, 18, SLOT_FACE, SLOT_LO, SLOT_HI);
    }

    public static void nickedRect(GuiGraphics g, int x, int y, int w, int h) {
        int r = x + w, b = y + h;
        g.fill(x + 1, y, r - 1, y + 1, PANEL);
        g.fill(x, y + 1, r, b - 1, PANEL);
        g.fill(x + 1, b - 1, r - 1, b, PANEL);
        g.fill(x + 1, y, r - 1, y + 1, PANEL_HI);
        g.fill(x, y + 1, x + 1, b - 1, PANEL_HI);
        g.fill(r - 1, y + 1, r, b - 1, PANEL_LO);
        g.fill(x + 1, b - 1, r - 1, b, PANEL_LO);
        g.fill(x + 1, y - 1, r - 1, y, OUTLINE);
        g.fill(x + 1, b, r - 1, b + 1, OUTLINE);
        g.fill(x - 1, y + 1, x, b - 1, OUTLINE);
        g.fill(r, y + 1, r + 1, b - 1, OUTLINE);
        g.fill(x, y, x + 1, y + 1, OUTLINE);
        g.fill(r - 1, y, r, y + 1, OUTLINE);
        g.fill(x, b - 1, x + 1, b, OUTLINE);
        g.fill(r - 1, b - 1, r, b, OUTLINE);
    }

    public static void paperGhost(GuiGraphics g, int ix, int iy) {
        ResourceLocation tex = ghostTexture();
        if (tex != null) {
            g.blit(tex, ix, iy, 0f, 0f, 16, 16, 16, 16);
        } else {
            g.fill(ix + 2, iy + 1, ix + 14, iy + 15, 0xFF000000 | PAPER_FILL);
        }
    }

    private static ResourceLocation ghostTexture() {
        if (ghostTex != null || ghostFailed) {
            return ghostTex;
        }
        Minecraft mc = Minecraft.getInstance();
        try (InputStream is = mc.getResourceManager().getResourceOrThrow(PAPER_TEX).open()) {
            NativeImage src = NativeImage.read(is);
            int w = src.getWidth(), h = src.getHeight();
            boolean[] shape = new boolean[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    shape[y * w + x] = ((src.getPixelRGBA(x, y) >>> 24) & 0xFF) > 64;
                }
            }
            src.close();
            NativeImage out = new NativeImage(w, h, true);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (shape[y * w + x]) {
                        out.setPixelRGBA(x, y, 0xFF000000 | PAPER_FILL);
                    } else if (touchesShape(shape, w, h, x, y)) {
                        out.setPixelRGBA(x, y, 0xFF000000 | PAPER_OUTLINE);
                    } else {
                        out.setPixelRGBA(x, y, 0);
                    }
                }
            }
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "dynamic/paper_ghost");
            mc.getTextureManager().register(loc, new DynamicTexture(out));
            ghostTex = loc;
        } catch (Exception e) {
            ghostFailed = true; // latch: don't retry the read every frame
        }
        return ghostTex;
    }

    // 4-neighbour, not 8: 8-way fattens corners.
    private static boolean touchesShape(boolean[] shape, int w, int h, int x, int y) {
        return isShape(shape, w, h, x - 1, y) || isShape(shape, w, h, x + 1, y)
                || isShape(shape, w, h, x, y - 1) || isShape(shape, w, h, x, y + 1);
    }

    private static boolean isShape(boolean[] shape, int w, int h, int x, int y) {
        return x >= 0 && x < w && y >= 0 && y < h && shape[y * w + x];
    }
}
