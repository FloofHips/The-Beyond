package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.camera.CameraAim;
import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.item.PrismographBlockItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/** It fills the captured square and black bars hide the rest, an accurate frame. */
public class CameraViewfinderLayer implements LayeredDraw.Layer {
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/prismograph/overlay.png");
    private static final ResourceLocation LENS = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/prismograph/lens.png");
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/prismograph/panel.png");

    private float scopeScale = 3F;
    private float panelProgress = 0F;
    private float color = 1f;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // reset only when the aim ends or the camera is dropped, an F5 toggle would restart the raise
        if (!CameraAim.isAiming() || mc.level == null || player == null || !holdingCamera(player)) {
            if (player == null || !holdingCamera(player)) {
                CameraAim.clear();
            }
            scopeScale = 3F;
            panelProgress = 0F;
            color = 1F;
            return;
        }

        // Advance the raise whenever aiming.
        float dt = deltaTracker.getGameTimeDeltaTicks();
        scopeScale = Mth.lerp(0.5F * dt, scopeScale, 1.3F);
        panelProgress = Mth.lerp(0.15F * dt, panelProgress, 7F);
        color = (float) Mth.lerp(0.1 * dt, color, 0f);

        // drawn only in first person, third person keeps it ticking so switching back resumes mid-raise
        if (mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        RenderSystem.enableBlend();
        int i = renderLayer(guiGraphics, scopeScale, LENS);

        renderRotatedLayer(guiGraphics, scopeScale, 90);
        renderRotatedLayer(guiGraphics, scopeScale, -90);
        renderRotatedLayer(guiGraphics, scopeScale, 0);
        renderRotatedLayer(guiGraphics, scopeScale, 180);

        renderLayer(guiGraphics, scopeScale, OVERLAY);

        int k = (guiGraphics.guiWidth() - i) / 2;
        int l = (guiGraphics.guiHeight() - i) / 2;
        int i1 = k + i;
        int j1 = l + i;
        RenderSystem.disableBlend();

        guiGraphics.fill(RenderType.gui(), 0, j1, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -90, 0xFF000000);
        guiGraphics.fill(RenderType.gui(), 0, 0, guiGraphics.guiWidth(), l, -90, 0xFF000000);
        guiGraphics.fill(RenderType.gui(), 0, l, k, j1, -90, 0xFF000000);
        guiGraphics.fill(RenderType.gui(), i1, l, guiGraphics.guiWidth(), j1, -90, 0xFF000000);
    }

    private int renderLayer(GuiGraphics guiGraphics, float baseScale, ResourceLocation texture) {
        float fMin = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        float scale = Math.min(guiGraphics.guiWidth() / fMin, guiGraphics.guiHeight() / fMin) * baseScale;

        int i = Mth.floor(fMin * scale);
        int k = (guiGraphics.guiWidth() - i) / 2;
        int l = (guiGraphics.guiHeight() - i) / 2;

        guiGraphics.blit(texture, k, l, -90, 0.0F, 0.0F, i, i, i, i);
        return i;
    }

    private int renderRotatedLayer(GuiGraphics guiGraphics, float baseScale, float rotation) {
        float fMin = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        float scale = Math.min(guiGraphics.guiWidth() / fMin, guiGraphics.guiHeight() / fMin) * baseScale;

        int i = Mth.floor(fMin * scale);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(guiGraphics.guiWidth() / 2f, guiGraphics.guiHeight() / 2f, 0f);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        guiGraphics.pose().translate(-i / 2f, -i / 2f, 0f);
        guiGraphics.pose().translate(0, -22*panelProgress, 0f);

        RenderSystem.setShaderColor(1-color, 1-color, 1-color, 1);
        guiGraphics.blit(PANEL, 0, 0, -90, 0.0F, 0.0F, i, i, i, i);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        guiGraphics.pose().popPose();

        return i;
    }

    private static boolean holdingCamera(LocalPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PrismographBlockItem
                || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof PrismographBlockItem;
    }
}
