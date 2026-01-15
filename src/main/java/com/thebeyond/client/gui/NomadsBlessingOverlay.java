package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.ModClientEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public class NomadsBlessingOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation TEXTURE_VIGNETTE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/overlay/vignette.png");
    private static final ResourceLocation TEXTURE_EYES = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/overlay/nomad_eyes.png");
    private float alpha = 1f;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (ModClientEvents.nomadEyes > 0) {
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.options.hideGui) return;

            alpha = Math.max(0.0f, alpha - 0.01f);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            guiGraphics.setColor(1f, alpha, 1f, alpha);
            guiGraphics.blit(TEXTURE_VIGNETTE, 0, 0, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight());

            guiGraphics.setColor(1f, alpha, 1f, alpha);
            //guiGraphics.pose().translate(0, alpha,0);
            guiGraphics.blit(TEXTURE_EYES, 0, 0, 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight());

            guiGraphics.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            if (alpha == 0) {
                ModClientEvents.nomadEyes = 0;
                alpha = 1;
            }
        }
    }
}
