package com.thebeyond.compat.sable.client;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Called only when Sable is loaded, so the base rendering keeps no Sable reference. */
public final class BeyondSableClientCompat {
    private BeyondSableClientCompat() {
    }

    public static void registerRenderers() {
        try {
            BlockEntityRenderers.register(BeyondBlockEntities.MIRROR.get(), MirrorSableRenderer::new);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SableReflectionFrame::onRenderStage);
            ProjectorSableFrame.install(); // reuses the base projector BER inside sub-levels; registers none
            TheBeyond.LOGGER.info("[TheBeyond] Sable mirror + projector renderers registered.");
        } catch (Throwable t) {
            TheBeyond.LOGGER.error("[TheBeyond] Failed to register Sable renderers", t);
        }
    }
}
