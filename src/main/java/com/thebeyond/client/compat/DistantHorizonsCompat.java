package com.thebeyond.client.compat;

import com.thebeyond.client.renderer.RenderFailureLog;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A main target swap makes Iris give Distant Horizons the screen depth, which then draws its LODs over near terrain. */
public final class DistantHorizonsCompat {
    private static boolean resolved;
    private static boolean usable;
    private static Method pipelineManager;
    private static Method pipeline;
    private static Method dhCompat;
    private static Method instance;
    private static Field storedDepthTex;

    private DistantHorizonsCompat() {
    }

    public static void relinkIrisDepth() {
        if (!usable()) {
            return;
        }
        try {
            Object current = pipeline.invoke(pipelineManager.invoke(null));
            Object compat = current == null ? null : dhCompat.invoke(current);
            Object internal = compat == null ? null : instance.invoke(compat);
            if (internal == null) {
                return;
            }
            storedDepthTex.setInt(internal, -1);
        } catch (Throwable t) {
            usable = false;
            RenderFailureLog.error("[camera] Distant Horizons depth could not be re-linked, its LODs may cover near terrain until a shader reload", t);
        }
    }

    private static boolean usable() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("iris") && ModList.get().isLoaded("distanthorizons")) {
                try {
                    pipelineManager = Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager");
                    pipeline = pipelineManager.getReturnType().getMethod("getPipelineNullable");
                    dhCompat = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline").getMethod("getDHCompat");
                    instance = Class.forName("net.irisshaders.iris.compat.dh.DHCompat").getMethod("getInstance");
                    storedDepthTex = Class.forName("net.irisshaders.iris.compat.dh.DHCompatInternal").getDeclaredField("storedDepthTex");
                    storedDepthTex.setAccessible(true);
                    usable = true;
                } catch (Throwable t) {
                    RenderFailureLog.error("[camera] Iris internals for Distant Horizons not found, photos may leave its LODs over near terrain", t);
                }
            }
        }
        return usable;
    }
}
