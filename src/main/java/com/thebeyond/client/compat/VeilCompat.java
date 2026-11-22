package com.thebeyond.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.RenderFailureLog;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

/** Veil binds its own screen framebuffer for main_target draws while its lights need it, offscreen renders step around that. */
public final class VeilCompat {
    private static boolean resolved;
    private static boolean usable;
    private static MethodHandle renderer;
    private static MethodHandle bufferManager;
    private static MethodHandle activeBuffers;
    private static MethodHandle isEnabled;
    private static MethodHandle setEnabled;
    private static MethodHandle dynamicFbo;
    private static MethodHandle wrap;
    private static MethodHandle setWrapper;
    private static MethodHandle unbind;
    private static MethodHandle cullingFrustum;
    private static MethodHandle drawLights;
    private static MethodHandle compositeLights;
    private static Field perspective;
    private static AtomicInteger perspectiveId;
    @Nullable
    private static RenderTarget wrappedTarget;
    @Nullable
    private static Object wrapper;

    private VeilCompat() {
    }

    private static boolean usable() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded("veil")) {
                try {
                    resolve();
                    usable = true;
                } catch (Throwable t) {
                    TheBeyond.LOGGER.warn("[Beyond] Veil is loaded but its render hooks did not resolve, offscreen renders skip them", t);
                }
            }
        }
        return usable;
    }

    private static void resolve() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> system = Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
        Class<?> rendererClass = Class.forName("foundry.veil.api.client.render.VeilRenderer");
        Class<?> manager = Class.forName("foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferManager");
        Class<?> fbo = Class.forName("foundry.veil.api.client.render.framebuffer.AdvancedFbo");
        Class<?> frustum = Class.forName("foundry.veil.api.client.render.CullFrustum");
        Class<?> perspectiveRenderer = Class.forName("foundry.veil.api.client.render.VeilLevelPerspectiveRenderer");
        renderer = lookup.findStatic(system, "renderer", MethodType.methodType(rendererClass));
        bufferManager = lookup.findVirtual(rendererClass, "getDynamicBufferManger", MethodType.methodType(manager));
        activeBuffers = lookup.findVirtual(manager, "getActiveBuffers", MethodType.methodType(int.class));
        isEnabled = lookup.findVirtual(manager, "isEnabled", MethodType.methodType(boolean.class));
        setEnabled = lookup.findVirtual(manager, "setEnabled", MethodType.methodType(void.class, boolean.class));
        dynamicFbo = lookup.findVirtual(manager, "getDynamicFbo", MethodType.methodType(fbo, fbo));
        wrap = lookup.findStatic(Class.forName("foundry.veil.api.client.render.VeilRenderBridge"), "wrap",
                MethodType.methodType(fbo, RenderTarget.class));
        setWrapper = lookup.findVirtual(Class.forName("foundry.veil.ext.RenderTargetExtension"), "veil$setWrapper",
                MethodType.methodType(void.class, fbo));
        unbind = lookup.findStatic(fbo, "unbind", MethodType.methodType(void.class));
        cullingFrustum = lookup.findStatic(system, "getCullingFrustum", MethodType.methodType(frustum));
        drawLights = lookup.findStatic(system, "drawLights", MethodType.methodType(boolean.class, ProfilerFiller.class, frustum));
        compositeLights = lookup.findStatic(system, "compositeLights", MethodType.methodType(void.class, ProfilerFiller.class));
        perspective = perspectiveRenderer.getDeclaredField("renderingPerspective");
        perspective.setAccessible(true);
        Field id = perspectiveRenderer.getDeclaredField("ID");
        id.setAccessible(true);
        perspectiveId = (AtomicInteger) id.get(null);
    }

    /** Call around offscreen draws inside renderLevel and pass the result to {@link #resumeBuffers}. */
    public static boolean suspendBuffers() {
        if (!usable()) {
            return false;
        }
        try {
            Object manager = bufferManager.invoke(renderer.invoke());
            if (!(boolean) isEnabled.invoke(manager)) {
                return false;
            }
            setEnabled.invoke(manager, false);
            return true;
        } catch (Throwable t) {
            RenderFailureLog.error("[Beyond] Veil buffers could not be suspended", t);
            return false;
        }
    }

    public static void resumeBuffers(boolean suspended) {
        if (!suspended) {
            return;
        }
        try {
            setEnabled.invoke(bufferManager.invoke(renderer.invoke()), true);
        } catch (Throwable t) {
            RenderFailureLog.error("[Beyond] Veil buffers could not be resumed", t);
        }
    }

    public static void renderLevel(RenderTarget target, Runnable render) {
        if (!usable()) {
            render.run();
            return;
        }
        boolean wrapped = false;
        try {
            Object manager = bufferManager.invoke(renderer.invoke());
            if ((int) activeBuffers.invoke(manager) != 0) {
                boolean enabled = (boolean) isEnabled.invoke(manager);
                setEnabled.invoke(manager, true);
                Object drawFbo;
                try {
                    drawFbo = dynamicFbo.invoke(manager, wrapperOf(target));
                } finally {
                    setEnabled.invoke(manager, enabled);
                }
                setWrapper.invoke(target, drawFbo);
                wrapped = true;
                // Sodium's section marks are per perspective id, a reused id would cull every section as already drawn
                perspectiveId.incrementAndGet();
                perspective.setBoolean(null, true);
            }
        } catch (Throwable t) {
            RenderFailureLog.error("[Beyond] Veil offscreen setup failed, the capture runs without Veil buffers", t);
        }
        if (!wrapped) {
            render.run();
            return;
        }
        try {
            target.bindWrite(true);
            render.run();
            lights();
        } finally {
            try {
                perspective.setBoolean(null, false);
                setWrapper.invoke(target, null);
                unbind.invoke();
            } catch (Throwable t) {
                RenderFailureLog.error("[Beyond] Veil offscreen state could not be restored", t);
            }
        }
    }

    private static void lights() {
        try {
            ProfilerFiller profiler = Minecraft.getInstance().getProfiler();
            if ((boolean) drawLights.invoke(profiler, cullingFrustum.invoke())) {
                compositeLights.invoke(profiler);
            } else {
                unbind.invoke();
            }
        } catch (Throwable t) {
            RenderFailureLog.error("[Beyond] Veil lights failed on an offscreen capture", t);
        }
    }

    private static Object wrapperOf(RenderTarget target) throws Throwable {
        if (wrappedTarget != target) {
            wrapper = wrap.invoke(target);
            wrappedTarget = target;
        }
        return wrapper;
    }
}
