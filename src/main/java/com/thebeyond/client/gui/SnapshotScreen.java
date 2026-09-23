package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.blockentities.SnapshotTextures;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.item.OcarinaItem;
import com.thebeyond.common.item.SnapshotItem;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class SnapshotScreen implements LayeredDraw.Layer{

    private static final int SIZE = 64;
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/snapshot/frame.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/snapshot/overlay.png");

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null || mc.options.hideGui) return;
        ItemStack snapshot = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(snapshot.getItem() instanceof SnapshotItem)) return;
        if (!snapshot.has(BeyondComponents.SNAPSHOT_PIXELS)) return;

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        float xRot = Math.clamp(90-(player.getXRot()), 0, 90)/90f;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();

        float ts = Math.clamp(Mth.lerp(xRot, -100, 180), 0,150);
        float alpha = -Math.clamp(Mth.lerp(xRot,-2, 2),-1,0);

        RenderSystem.setShaderColor(1,1,1, alpha);
        guiGraphics.fillGradient(0, 0, screenWidth, screenHeight, -1072689136, -804253680);
        RenderSystem.setShaderColor(1,1,1, 1);

        pose.translate(0, ts,0);

        float scale = Math.clamp(1+alpha, 1.5f, 2f);
        pose.scale(scale, scale, scale);

        float startX = screenWidth / (2f * scale);
        float startY = screenHeight / (2f * scale);

        guiGraphics.blit(FRAME, (int) (startX - 41), (int) (startY - 47), 0, 0, 82, 94, 82,94);
        renderImage((int) (startX - 41 + 9), (int) (startY - 47 + 11), guiGraphics, snapshot);
        RenderUtils.renderMultiplicativeQuad(guiGraphics, OVERLAY, (int) (startX - 41 + 9), (int) (startY - 47 + 11), 0, 0, 64, 64, 64,64,-1);
        if (snapshot.has(BeyondComponents.SNAPSHOT_DATE)) {
            Component text = snapshot.get(BeyondComponents.SNAPSHOT_DATE);
            guiGraphics.drawString(mc.font, text, (int) (startX - 33), (int) (startY + 32), 0x222A31, false);
            guiGraphics.drawString(mc.font, text, (int) (startX - 33), (int) (startY + 34), 0x486B73, false);
            guiGraphics.drawString(mc.font, text, (int) (startX - 33), (int) (startY + 33), 0x000000, false);
        }
        pose.popPose();
    }

    public void renderImage(int x, int y, GuiGraphics guiGraphics, ItemStack stack) {
        Components.SnapshotPixelsComponent px = stack.get(BeyondComponents.SNAPSHOT_PIXELS.get());
        ResourceLocation tex = SnapshotTextures.get(px, Grades.NONE);
        // DynamicTexture is not an atlas sprite, so blitSprite would fail.
        guiGraphics.blit(tex, x, y, 0F, 0F, SIZE, SIZE, SIZE, SIZE);
    }
}
