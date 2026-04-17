package com.thebeyond.client.compat;

import net.neoforged.fml.ModList;

/**
 * Detects whether shader-altering mods (Iris, Oculus) or alternative renderers
 * (Sodium, Embeddium) are loaded.
 *
 * <p>When shader mods are present, custom RenderTypes with custom shaders break due to
 * G-Buffer/depth-test incompatibilities — we fall back to CPU-side
 * texture processing with vanilla-compatible RenderTypes.</p>
 *
 * <p>When alternative renderers are present, out-of-range fog/lightmap color channels
 * wrap modulo-style instead of clamping (e.g. -0.3 → 0.7), tinting fog green.
 * {@link #isModdedRendererLoaded()} gates explicit [0,1] clamping in
 * {@code EndSpecialEffects} so vanilla-only installs skip the extra work.</p>
 *
 * <p>We check mod PRESENCE, not shader/renderer ACTIVITY, because these mods corrupt
 * OpenGL state even after toggling off mid-session.</p>
 */
public class ShaderCompatLib {
    private static Boolean cachedShaderResult = null;
    private static Boolean cachedRendererResult = null;

    /**
     * Returns {@code true} if Iris or Oculus is installed (shader mods).
     */
    public static boolean isShaderModLoaded() {
        if (cachedShaderResult == null) {
            boolean iris = ModList.get().isLoaded("iris");
            boolean oculus = ModList.get().isLoaded("oculus");
            cachedShaderResult = iris || oculus;
        }
        return cachedShaderResult;
    }

    /**
     * Returns {@code true} if any modded renderer or shader mod is installed
     * (Sodium, Embeddium, Iris, Oculus). Used to gate fog/lightmap color clamping
     * in {@code EndSpecialEffects} — vanilla handles out-of-range channels gracefully,
     * but these mods wrap them modulo-style causing green tinting.
     */
    public static boolean isModdedRendererLoaded() {
        if (cachedRendererResult == null) {
            boolean sodium = ModList.get().isLoaded("sodium");
            boolean embeddium = ModList.get().isLoaded("embeddium");
            cachedRendererResult = sodium || embeddium || isShaderModLoaded();
        }
        return cachedRendererResult;
    }
}
