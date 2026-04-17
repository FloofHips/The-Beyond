package com.thebeyond.client.renderer;

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
 * CPU-side fallback for the Lantern's custom depth-to-gray shader effect.
 *
 * <p>When Iris/Oculus is active, custom shaders registered via {@code ShaderInstance}
 * are silently ignored by the G-Buffer pipeline. This manager pre-processes the
 * Lantern textures on the CPU, converting them into "depth" variants that replicate
 * the shader's visual effect:</p>
 *
 * <ul>
 *   <li>Opaque pixels (alpha = 1.0) → white</li>
 *   <li>Semi-transparent pixels → proportionally gray</li>
 *   <li>Nearly transparent pixels (alpha &lt; 0.1) → discarded</li>
 * </ul>
 *
 * <p>This creates the ethereal ghost aesthetic that defines the Lantern's visual
 * identity. The processed textures are cached as {@link DynamicTexture} instances
 * and reused across frames.</p>
 *
 * <p>Formula (matching {@code rendertype_entity_depth.fsh} exactly):</p>
 * <pre>
 *   depthColor = mix(vec3(1.0), vec3(0.5), 1.0 - alpha)
 *              = vec3(0.5 + 0.5 * alpha)
 * </pre>
 *
 * @see com.thebeyond.client.renderer.blockentities.RefugeGradientTextureManager
 */
public class LanternDepthTextureManager {

    private static final Map<ResourceLocation, CachedTexture> cache = new HashMap<>();

    private static class CachedTexture {
        public final DynamicTexture texture;
        public final ResourceLocation location;

        public CachedTexture(DynamicTexture texture, ResourceLocation location) {
            this.texture = texture;
            this.location = location;
        }
    }

    /**
     * Returns the depth-processed variant of the given Lantern texture.
     * Creates and caches the processed texture on first call per source.
     *
     * @param sourceTexture the original Lantern texture ResourceLocation
     * @return the processed texture ResourceLocation (dynamic), or the source as fallback on error
     */
    public static ResourceLocation getOrCreate(ResourceLocation sourceTexture) {
        CachedTexture cached = cache.get(sourceTexture);
        if (cached != null) {
            return cached.location;
        }
        return processTexture(sourceTexture);
    }

    private static ResourceLocation processTexture(ResourceLocation sourceTexture) {
        try {
            Minecraft mc = Minecraft.getInstance();
            AbstractTexture tex = mc.getTextureManager().getTexture(sourceTexture);
            if (tex == null) return sourceTexture;

            int texId = tex.getId();
            RenderSystem.bindTexture(texId);

            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);

            if (width <= 0 || height <= 0) return sourceTexture;

            NativeImage source = new NativeImage(width, height, false);
            source.downloadTexture(0, false);

            NativeImage result = new NativeImage(width, height, true);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = source.getPixelRGBA(x, y);

                    // NativeImage stores ABGR: [A:31-24][B:23-16][G:15-8][R:7-0]
                    int a = (pixel >> 24) & 0xFF;

                    // Discard transparent pixels (matching shader: alpha < 0.1)
                    if (a < 25) {
                        result.setPixelRGBA(x, y, 0x00000000);
                        continue;
                    }

                    // Replicate the depth shader's formula:
                    //   depthColor = mix(vec3(1.0), vec3(0.5), 1.0 - normalizedAlpha)
                    //              = vec3(0.5 + 0.5 * normalizedAlpha)
                    // Opaque → white, semi-transparent → gray
                    float normalizedAlpha = a / 255.0f;
                    int gray = (int) ((0.5f + 0.5f * normalizedAlpha) * 255.0f);

                    // Write in ABGR format (R=gray, G=gray, B=gray, A=original)
                    int newPixel = (a << 24) | (gray << 16) | (gray << 8) | gray;
                    result.setPixelRGBA(x, y, newPixel);
                }
            }

            source.close();

            DynamicTexture dynamicTexture = new DynamicTexture(result);
            String safeName = sourceTexture.getPath().replaceAll("[^a-z0-9_]", "_");
            ResourceLocation newLocation = ResourceLocation.fromNamespaceAndPath(
                    TheBeyond.MODID, "dynamic_lantern_depth_" + safeName
            );
            mc.getTextureManager().register(newLocation, dynamicTexture);

            cache.put(sourceTexture, new CachedTexture(dynamicTexture, newLocation));
            return newLocation;

        } catch (Exception e) {
            TheBeyond.LOGGER.error("LanternDepthTextureManager: failed to process {}", sourceTexture, e);
            return sourceTexture;
        }
    }

    public static void clearCache() {
        for (CachedTexture cached : cache.values()) {
            cached.texture.close();
        }
        cache.clear();
    }
}
