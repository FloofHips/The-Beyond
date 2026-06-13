package com.thebeyond.client.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class ShaderCompatLib {
    private static Boolean cachedShaderResult = null;
    private static Boolean cachedRendererResult = null;

    // Cache Method refs only, never results: pack state flips on toggle.
    private static volatile boolean irisReflectionInitialized = false;
    private static Method irisGetInstance;
    private static Method irisIsPackInUse;
    private static Method irisIsShadowPass;
    private static boolean irisProperNamespace;

    public static boolean isShaderModLoaded() {
        if (cachedShaderResult == null) {
            boolean iris = ModList.get().isLoaded("iris");
            boolean oculus = ModList.get().isLoaded("oculus");
            cachedShaderResult = iris || oculus;
        }
        return cachedShaderResult;
    }

    /** Flips mid-session as packs toggle, unlike {@link #isShaderModLoaded()}. */
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
        Class<?> api = null;
        for (String candidate : new String[] {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi",
        }) {
            try {
                api = Class.forName(candidate);
                irisProperNamespace = candidate.startsWith("net.irisshaders");
                break;
            } catch (Throwable ignored) {}
        }
        if (api != null) {
            // Resolve independently: a missing method on older Iris must not null the rest.
            try { irisGetInstance = api.getMethod("getInstance"); } catch (Throwable ignored) { irisGetInstance = null; }
            try { irisIsPackInUse = api.getMethod("isShaderPackInUse"); } catch (Throwable ignored) { irisIsPackInUse = null; }
            try { irisIsShadowPass = api.getMethod("isRenderingShadowPass"); } catch (Throwable ignored) { irisIsShadowPass = null; }
        }
        irisReflectionInitialized = true;
    }

    public static boolean isShadowPass() {
        if (!isShaderModLoaded()) return false;
        initIrisReflection();
        if (irisGetInstance == null || irisIsShadowPass == null) return false;
        try {
            Object api = irisGetInstance.invoke(null);
            return api != null && Boolean.TRUE.equals(irisIsShadowPass.invoke(api));
        } catch (Throwable t) {
            return false;
        }
    }

    /** False under legacy Oculus, which keeps the conservative fallbacks. */
    public static boolean isIrisProper() {
        initIrisReflection();
        return irisProperNamespace;
    }

    /** Gates fog/lightmap clamping: these renderers tint the End green without it. */
    public static boolean isModdedRendererLoaded() {
        if (cachedRendererResult == null) {
            boolean sodium = ModList.get().isLoaded("sodium");
            boolean embeddium = ModList.get().isLoaded("embeddium");
            cachedRendererResult = sodium || embeddium || isShaderModLoaded();
        }
        return cachedRendererResult;
    }
}
