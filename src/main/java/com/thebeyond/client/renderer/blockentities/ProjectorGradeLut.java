package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.platform.NativeImage;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.camera.Grade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-grade luminance-ramp LUT (256x1) for the item-icon grade shader: holds the pure {@link Grade#rampRgb} colour,
 * the shader blends at the grade's strength. GL thread only; cleared on disconnect (grade registry re-syncs on reconnect).
 */
public final class ProjectorGradeLut {
    private static final int N = 256;
    private static final Map<ResourceLocation, ResourceLocation> CACHE = new HashMap<>();
    private static int idCounter = 0;

    private ProjectorGradeLut() {
    }

    /** Cached LUT texture id; null when the grade is passthrough (no stops). */
    public static ResourceLocation get(ResourceLocation gradeId, Grade grade) {
        if (grade == null || grade.stops().length == 0) {
            return null;
        }
        ResourceLocation cached = CACHE.get(gradeId);
        if (cached != null) {
            return cached;
        }
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, N, 1, false);
        for (int i = 0; i < N; i++) {
            int rgb = grade.rampRgb(i / (float) (N - 1));
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            img.setPixelRGBA(i, 0, 0xFF000000 | (b << 16) | (g << 8) | r); // NativeImage packs ABGR
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "dynamic/grade_lut/" + (idCounter++));
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
        CACHE.put(gradeId, loc);
        return loc;
    }

    public static void clear() {
        for (ResourceLocation loc : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        CACHE.clear();
    }
}
