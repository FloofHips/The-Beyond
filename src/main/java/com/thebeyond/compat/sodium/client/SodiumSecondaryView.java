package com.thebeyond.compat.sodium.client;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.BlockCameraCapture;
import com.thebeyond.mixin.compat.sodium.SodiumWorldRendererAccessor;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

/** A spare RenderSectionManager context is swapped in to force terrain rebuild for an off-camera POV, then swapped back. */
public final class SodiumSecondaryView {
    private SodiumSecondaryView() {
    }

    /** Call once, only when Sodium is loaded. */
    public static void install() {
        BlockCameraCapture.secondaryViewHook = SodiumSecondaryView::renderHostView;
    }

    private static boolean logged;

    /** Returns false = "call again": Sodium builds the swapped graph asynchronously over a few frames. */
    private static boolean renderHostView(DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        SodiumWorldRenderer swr = SodiumWorldRenderer.instanceNullable();
        if (swr == null) {
            mc.gameRenderer.renderLevel(delta);
            return true;
        }
        RenderSectionManager rsm;
        try {
            rsm = ((SodiumWorldRendererAccessor) swr).the_beyond$renderSectionManager();
        } catch (Throwable t) {
            logOnce("[camera] Sodium accessor mixin NOT applied (" + t + ") -> blank fallback");
            mc.gameRenderer.renderLevel(delta);
            return true;
        }
        if (!(rsm instanceof ISodiumSectionManagerSwap swap)) {
            logOnce("[camera] Sodium RenderSectionManager mixin NOT applied -> blank fallback");
            mc.gameRenderer.renderLevel(delta);
            return true;
        }
        int dist = Math.max(2, Math.min(8, mc.options.getEffectiveRenderDistance()));
        SodiumRenderContext ctx = new SodiumRenderContext(dist);
        boolean complete = true;
        switchContext(swr, swap, ctx);
        try {
            mc.gameRenderer.renderLevel(delta);
            int visible = swr.getVisibleChunkCount();
            complete = visible > 0 && swr.isTerrainRenderComplete();
            logOnce("[camera] Sodium secondary view active: visibleChunks=" + visible + " complete=" + complete);
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[camera] Sodium secondary view failed", t);
        } finally {
            switchContext(swr, swap, ctx);
        }
        return complete;
    }

    private static void logOnce(String msg) {
        if (!logged) {
            logged = true;
            TheBeyond.LOGGER.info(msg);
        }
    }

    private static void switchContext(SodiumWorldRenderer swr, ISodiumSectionManagerSwap swap, SodiumRenderContext ctx) {
        swr.scheduleTerrainUpdate();   // rebuild happens on the next setupTerrain inside renderLevel
        swap.the_beyond$swapContext(ctx);
        swr.scheduleTerrainUpdate();
    }
}
