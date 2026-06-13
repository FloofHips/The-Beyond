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

/** CPU mirror of {@code rendertype_refuge_gradient.fsh}; stops/weights below must stay in sync with it. */
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

    // EDIT HERE (Java is the source of truth), then mirror these 5 hex colors into rendertype_refuge_gradient.fsh.
    private static final int[][] GRADIENT_STOPS = {
            {0x0C, 0x08, 0x26}, // #0C0826
            {0x31, 0x33, 0x4B}, // #31334B
            {0x79, 0x89, 0xA9}, // #7989A9
            {0x87, 0xB1, 0xDA}, // #87B1DA
            {0xE8, 0xF4, 0xFF}  // #E8F4FF
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

                    int r = pixel & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = (pixel >> 16) & 0xFF;
                    int a = (pixel >> 24) & 0xFF;

                    if (a < 25) {
                        result.setPixelRGBA(x, y, 0x00000000);
                        continue;
                    }

                    // Luminance weights must match the shader.
                    float brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;

                    int[] mapped = gradientMap(brightness);

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

        return GRADIENT_STOPS[GRADIENT_STOPS.length - 1].clone();
    }

    public static int rampAbgr(float brightness, int alpha) {
        int[] rgb = gradientMap(brightness);
        return ((alpha & 0xFF) << 24) | (rgb[2] << 16) | (rgb[1] << 8) | rgb[0];
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
