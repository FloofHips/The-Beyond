package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.TheBeyond;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

/**
 * CPU-side gradient map processor for player skin textures.
 * Used as a fallback when shader mods (Iris/Oculus) are present,
 * since our custom refuge_gradient shader would be overridden.
 *
 * Replicates the same gradient map from rendertype_refuge_gradient.fsh
 * but applies it per-pixel on the CPU, caching the result as a DynamicTexture.
 */
public class RefugeGradientTextureManager {

    private static final Map<ResourceLocation, CachedTexture> cache = new HashMap<>();

    private static class CachedTexture {
        public DynamicTexture texture;
        public ResourceLocation location;

        public CachedTexture(DynamicTexture texture, ResourceLocation location) {
            this.texture = texture;
            this.location = location;
        }
    }

    // Gradient map colors (matching the fragment shader exactly)
    // NativeImage pixel format is ABGR: [A:31-24][B:23-16][G:15-8][R:7-0]
    private static final int[][] GRADIENT_STOPS = {
            {12, 8, 38},    // #0c0826 at t=0.00
            {49, 51, 75},   // #31334b at t=0.30
            {121, 137, 169},// #7989a9 at t=0.75
            {135, 177, 218},// #87b1da at t=0.92
            {232, 244, 255} // #e8f4ff at t=1.00
    };
    private static final float[] GRADIENT_POSITIONS = {0.00f, 0.30f, 0.75f, 0.92f, 1.00f};

    public static ResourceLocation getOrCreate(ResourceLocation skinTexture) {
        CachedTexture cached = cache.get(skinTexture);
        if (cached != null) {
            return cached.location;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            AbstractTexture tex = mc.getTextureManager().getTexture(skinTexture);
            if (tex == null) return skinTexture;

            int texId = tex.getId();
            RenderSystem.bindTexture(texId);

            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

            if (width <= 0 || height <= 0) return skinTexture;

            NativeImage source = new NativeImage(width, height, false);
            source.downloadTexture(0, false);

            NativeImage result = new NativeImage(width, height, true);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = source.getPixelRGBA(x, y);

                    // Extract ABGR components
                    int r = pixel & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = (pixel >> 16) & 0xFF;
                    int a = (pixel >> 24) & 0xFF;

                    // Discard transparent pixels (alpha < 0.1 * 255 ≈ 25)
                    if (a < 25) {
                        result.setPixelRGBA(x, y, 0x00000000);
                        continue;
                    }

                    // Luminance (same formula as the shader)
                    float brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;

                    // Apply gradient map
                    int[] mapped = gradientMap(brightness);

                    // Write back in ABGR format
                    int newPixel = (a << 24) | (mapped[2] << 16) | (mapped[1] << 8) | mapped[0];
                    result.setPixelRGBA(x, y, newPixel);
                }
            }

            source.close();

            DynamicTexture dynamicTexture = new DynamicTexture(result);
            String safeName = skinTexture.getPath().replaceAll("[^a-z0-9_]", "_");
            ResourceLocation newLocation = ResourceLocation.fromNamespaceAndPath(
                    TheBeyond.MODID, "dynamic_refuge_gradient_" + safeName
            );
            mc.getTextureManager().register(newLocation, dynamicTexture);

            cache.put(skinTexture, new CachedTexture(dynamicTexture, newLocation));
            return newLocation;

        } catch (Exception e) {
            e.printStackTrace();
            return skinTexture;
        }
    }

    private static int[] gradientMap(float brightness) {
        brightness = Math.max(0.0f, Math.min(1.0f, brightness));

        // Find the two stops to interpolate between
        for (int i = 0; i < GRADIENT_POSITIONS.length - 1; i++) {
            if (brightness <= GRADIENT_POSITIONS[i + 1]) {
                float t = (brightness - GRADIENT_POSITIONS[i]) / (GRADIENT_POSITIONS[i + 1] - GRADIENT_POSITIONS[i]);
                t = Math.max(0.0f, Math.min(1.0f, t));
                return new int[]{
                        (int) (GRADIENT_STOPS[i][0] + t * (GRADIENT_STOPS[i + 1][0] - GRADIENT_STOPS[i][0])),
                        (int) (GRADIENT_STOPS[i][1] + t * (GRADIENT_STOPS[i + 1][1] - GRADIENT_STOPS[i][1])),
                        (int) (GRADIENT_STOPS[i][2] + t * (GRADIENT_STOPS[i + 1][2] - GRADIENT_STOPS[i][2]))
                };
            }
        }

        // Fallback to last color
        return GRADIENT_STOPS[GRADIENT_STOPS.length - 1].clone();
    }

    public static void clearCache() {
        for (CachedTexture cached : cache.values()) {
            cached.texture.close();
        }
        cache.clear();
    }

    public static void clearCache(ResourceLocation skinTexture) {
        CachedTexture cached = cache.remove(skinTexture);
        if (cached != null) {
            cached.texture.close();
        }
    }
}
