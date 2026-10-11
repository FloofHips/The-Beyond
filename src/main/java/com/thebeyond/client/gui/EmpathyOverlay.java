package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.ModClientEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public class EmpathyOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/overlay/empathy.png");
    private float alpha = 1f;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (ModClientEvents.empathy > 0) {
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.options.hideGui) return;

            alpha = Math.max(0.0f, alpha - 0.01f);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            int min = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());

            int x = (guiGraphics.guiWidth()/2)-min/2;
            int y = (guiGraphics.guiHeight()/2)-min/2;

            guiGraphics.setColor(1f, 1f, 1f, alpha);
            guiGraphics.blit(TEXTURE, x, y, 0, 0, min, min, min, min);
            guiGraphics.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();

            if (alpha == 0) {
                ModClientEvents.empathy = 0;
                alpha = 1;
            }
        }
    }
}
