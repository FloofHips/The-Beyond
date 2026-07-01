package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.platform.NativeImage;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.camera.Grade;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.item.components.Components;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SnapshotTextures {
    private static final int MAX = 64;

    /** {@code size == 0} -> native 1:1 decode; {@code size > 0} -> box-downsampled to size x size. */
    private record Key(Components.SnapshotPixelsComponent pixels, ResourceLocation gradeId, int size) {
    }

    private record Entry(ResourceLocation loc, DynamicTexture tex) {
    }

    private static final LinkedHashMap<Key, Entry> CACHE =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
                    if (size() > MAX) {
                        release(eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };
    private static int idCounter = 0;

    private SnapshotTextures() {
    }

    private static Grade resolve(ResourceLocation gradeId) {
        Minecraft mc = Minecraft.getInstance();
        RegistryAccess access = mc.level != null ? mc.level.registryAccess() : null;
        return Grades.resolve(access, gradeId);
    }

    /** Client main thread only (registers a texture). */
    public static ResourceLocation get(Components.SnapshotPixelsComponent pixels, ResourceLocation gradeId) {
        return register(new Key(pixels, gradeId, 0), decode(pixels, resolve(gradeId)));
    }

    public static ResourceLocation getDownsampled(Components.SnapshotPixelsComponent pixels, ResourceLocation gradeId, int outSize) {
        return register(new Key(pixels, gradeId, outSize), boxDownsample(pixels, resolve(gradeId), outSize));
    }

    private static ResourceLocation register(Key key, NativeImage image) {
        Entry hit = CACHE.get(key);
        if (hit != null) {
            image.close();
            return hit.loc();
        }
        DynamicTexture tex = new DynamicTexture(image);
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "dynamic/snapshot/" + (idCounter++));
        Minecraft.getInstance().getTextureManager().register(loc, tex);
        CACHE.put(key, new Entry(loc, tex));
        return loc;
    }

    private static NativeImage decode(Components.SnapshotPixelsComponent pixels, Grade grade) {
        int w = pixels.width(), h = pixels.height();
        byte[] rgb = pixels.rgb();
        NativeImage img = new NativeImage(w, h, true);
        for (int i = 0; i < w * h; i++) {
            int r = rgb[i * 3] & 0xFF, g = rgb[i * 3 + 1] & 0xFF, b = rgb[i * 3 + 2] & 0xFF;
            img.setPixelRGBA(i % w, i / w, grade.applyAbgr(r, g, b));
        }
        return img;
    }

    private static NativeImage boxDownsample(Components.SnapshotPixelsComponent pixels, Grade grade, int out) {
        int w = pixels.width(), h = pixels.height();
        byte[] rgb = pixels.rgb();
        NativeImage img = new NativeImage(out, out, true);
        for (int oy = 0; oy < out; oy++) {
            for (int ox = 0; ox < out; ox++) {
                // (k+1)*src/out bounds keep non-power-of-two sizes gapless and non-overlapping
                int sx0 = ox * w / out, sx1 = Math.max(sx0 + 1, (ox + 1) * w / out);
                int sy0 = oy * h / out, sy1 = Math.max(sy0 + 1, (oy + 1) * h / out);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy++) {
                    for (int sx = sx0; sx < sx1; sx++) {
                        int i = (sy * w + sx) * 3;
                        r += rgb[i] & 0xFF;
                        g += rgb[i + 1] & 0xFF;
                        b += rgb[i + 2] & 0xFF;
                        n++;
                    }
                }
                img.setPixelRGBA(ox, oy, grade.applyAbgr((int) (r / n), (int) (g / n), (int) (b / n)));
            }
        }
        return img;
    }

    private static void release(Entry e) {
        Minecraft.getInstance().getTextureManager().release(e.loc());
        e.tex().close();
    }

    public static void clear() {
        for (Entry e : CACHE.values()) {
            release(e);
        }
        CACHE.clear();
    }
}
