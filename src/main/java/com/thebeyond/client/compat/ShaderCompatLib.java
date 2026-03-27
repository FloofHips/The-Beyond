package com.thebeyond.client.compat;

import net.neoforged.fml.ModList;

/**
 * Detects whether shader-altering mods (Iris, Oculus) are loaded.
 * When present, custom RenderTypes with custom shaders break due to
 * G-Buffer/depth-test incompatibilities - we fall back to CPU-side
 * texture processing with vanilla-compatible RenderTypes.
 *
 * We check mod PRESENCE, not shader ACTIVITY, because Iris corrupts
 * OpenGL state even after toggling shaders off mid-session.
 */
public class ShaderCompatLib {
    private static Boolean cachedResult = null;

    public static boolean isShaderModLoaded() {
        if (cachedResult == null) {
            boolean iris = ModList.get().isLoaded("iris");
            boolean oculus = ModList.get().isLoaded("oculus");
            cachedResult = iris || oculus;
        }
        return cachedResult;
    }
}
