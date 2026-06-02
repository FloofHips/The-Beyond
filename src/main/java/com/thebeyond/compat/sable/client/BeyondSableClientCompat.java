package com.thebeyond.compat.sable.client;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * Client-side Sable compat. Loaded and called only when {@code ModList.isLoaded("sable")}, so the base
 * mirror rendering carries no Sable reference and no overhead when Sable is absent from the modpack.
 */
public final class BeyondSableClientCompat {
    private BeyondSableClientCompat() {
    }

    /** Registers the Sable-only mirror renderer (draws a mirror that sits inside a Sable sub-level) plus the
     *  per-frame main-camera snapshot its capture needs. */
    public static void registerRenderers() {
        try {
            BlockEntityRenderers.register(BeyondBlockEntities.MIRROR.get(), MirrorSableRenderer::new);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SableReflectionFrame::onRenderStage);
            TheBeyond.LOGGER.info("[TheBeyond] Sable mirror renderer registered.");
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[TheBeyond] Failed to register Sable mirror renderer", t);
        }
    }
}
