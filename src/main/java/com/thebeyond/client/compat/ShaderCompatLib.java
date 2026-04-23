package com.thebeyond.client.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

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

    // Cached reflective handles into Iris/Oculus public API (net.irisshaders.iris.api.v0.IrisApi).
    // We cache the Method refs (one-shot Class.forName is the expensive part), but never cache
    // the invocation result — the pack state can flip at runtime when the user toggles packs.
    private static volatile boolean irisReflectionInitialized = false;
    private static Method irisGetInstance;
    private static Method irisIsPackInUse;

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
     * Returns {@code true} if a shader pack is live (loaded AND enabled). Flips to
     * {@code false} when the user toggles the pack off mid-session — unlike
     * {@link #isShaderModLoaded()}, which only reports mod presence.
     *
     * <p>Reflective call into {@code IrisApi#isShaderPackInUse()} (runtime-only, not on
     * the compile classpath) — absence is tolerated.</p>
     */
    public static boolean isShaderPackActive() {
        if (!isShaderModLoaded()) return false;
        initIrisReflection();
        if (irisGetInstance == null || irisIsPackInUse == null) return false;
        try {
            Object api = irisGetInstance.invoke(null);
            return api != null && Boolean.TRUE.equals(irisIsPackInUse.invoke(api));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void initIrisReflection() {
        if (irisReflectionInitialized) return;
        // Try modern classname first (Iris 1.20+ / Oculus current), fall back to
        // legacy net.coderbot namespace for older forks. If neither resolves,
        // both Method refs stay null → isShaderPackActive returns false.
        Class<?> api = null;
        for (String candidate : new String[] {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi",
        }) {
            try { api = Class.forName(candidate); break; } catch (Throwable ignored) {}
        }
        if (api != null) {
            try {
                irisGetInstance = api.getMethod("getInstance");
                irisIsPackInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                irisGetInstance = null;
                irisIsPackInUse = null;
            }
        }
        irisReflectionInitialized = true;
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
