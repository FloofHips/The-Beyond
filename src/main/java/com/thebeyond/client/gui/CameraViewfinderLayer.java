package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.client.camera.CameraAim;
import com.thebeyond.common.item.CameraBlockItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/** Spyglass scope reused as a viewfinder: scope fills the captured square ({@code side = min(screenW, screenH)}), bars black out the rest, so it doubles as an accurate frame. */
public class CameraViewfinderLayer implements LayeredDraw.Layer {
    private static final ResourceLocation SPYGLASS_SCOPE = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");

    private float scopeScale = 0.5F; // grows 0.5 -> 1 while aiming, like the spyglass raise

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // Reset only when the aim truly ends or the camera's dropped — never on an F5 toggle, else the raise restarts.
        if (!CameraAim.isAiming() || mc.level == null || player == null || !holdingCamera(player)) {
            if (player == null || !holdingCamera(player)) {
                CameraAim.clear();
            }
            scopeScale = 0.5F;
            return;
        }

        // Advance the raise whenever aiming, even in third person, so an F5 toggle never restarts it.
        // Constants verbatim from Gui#renderSpyglassOverlay.
        scopeScale = Mth.lerp(0.5F * deltaTracker.getGameTimeDeltaTicks(), scopeScale, 1.125F);

        // Draw only in first person; third person keeps it ticking but hidden, so switching back resumes mid-raise.
        if (mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        float fMin = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        float scale = Math.min(guiGraphics.guiWidth() / fMin, guiGraphics.guiHeight() / fMin) * scopeScale;
        int i = Mth.floor(fMin * scale);
        int k = (guiGraphics.guiWidth() - i) / 2;
        int l = (guiGraphics.guiHeight() - i) / 2;
        int i1 = k + i;
        int j1 = l + i;

        RenderSystem.enableBlend();
        guiGraphics.blit(SPYGLASS_SCOPE, k, l, -90, 0.0F, 0.0F, i, i, i, i);
        RenderSystem.disableBlend();
        guiGraphics.fill(RenderType.guiOverlay(), 0, j1, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -90, 0xFF000000);
        guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiGraphics.guiWidth(), l, -90, 0xFF000000);
        guiGraphics.fill(RenderType.guiOverlay(), 0, l, k, j1, -90, 0xFF000000);
        guiGraphics.fill(RenderType.guiOverlay(), i1, l, guiGraphics.guiWidth(), j1, -90, 0xFF000000);
    }

    private static boolean holdingCamera(LocalPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof CameraBlockItem
                || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof CameraBlockItem;
    }
}
