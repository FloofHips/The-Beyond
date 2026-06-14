package com.thebeyond.client.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Detects loaded shader mods (Iris/Oculus) and alt renderers (Sodium/Embeddium); callers
 *  fall back to CPU texturing / clamp fog channels accordingly. Presence-based. */
public class ShaderCompatLib {
    private static Boolean cachedShaderResult = null;
    private static Boolean cachedRendererResult = null;

    // Cached Iris/Oculus API method refs (Class.forName is costly); never cache the result — pack state flips at runtime.
    private static volatile boolean irisReflectionInitialized = false;
    private static Method irisGetInstance;
    private static Method irisIsPackInUse;

    /** {@code true} if a shader mod (Iris/Oculus) is installed. */
    public static boolean isShaderModLoaded() {
        if (cachedShaderResult == null) {
            boolean iris = ModList.get().isLoaded("iris");
            boolean oculus = ModList.get().isLoaded("oculus");
            cachedShaderResult = iris || oculus;
        }
        return cachedShaderResult;
    }

    /** {@code true} only when a pack is loaded AND enabled — unlike {@link #isShaderModLoaded()}
     *  this flips on/off mid-session via reflective {@code IrisApi#isShaderPackInUse()}. */
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

    /** True if Sodium/Embeddium/Iris/Oculus is installed; gates fog/lightmap channel
     *  clamping (these wrap out-of-range modulo and tint the End green otherwise). */
    public static boolean isModdedRendererLoaded() {
        if (cachedRendererResult == null) {
            boolean sodium = ModList.get().isLoaded("sodium");
            boolean embeddium = ModList.get().isLoaded("embeddium");
            cachedRendererResult = sodium || embeddium || isShaderModLoaded();
        }
        return cachedRendererResult;
    }
}
