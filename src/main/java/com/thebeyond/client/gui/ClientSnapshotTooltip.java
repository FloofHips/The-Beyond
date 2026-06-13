package com.thebeyond.client.gui;

import com.thebeyond.client.renderer.blockentities.SnapshotTextures;
import com.thebeyond.common.item.SnapshotTooltip;
import com.thebeyond.common.item.components.Components;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

/** Pixels live on the item, so the texture is the cached {@link SnapshotTextures} entry — no network fetch. */
public class ClientSnapshotTooltip implements ClientTooltipComponent {
    private static final int SIZE = 32;

    private final Components.SnapshotPixelsComponent pixels;
    private final ResourceLocation gradeId;

    public ClientSnapshotTooltip(SnapshotTooltip tooltip) {
        this.pixels = tooltip.pixels();
        this.gradeId = tooltip.gradeId();
    }

    @Override
    public int getHeight() {
        return SIZE + 2; // gap below the photo
    }

    @Override
    public int getWidth(Font font) {
        return SIZE;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        ResourceLocation tex = SnapshotTextures.get(pixels, gradeId);
        // DynamicTexture is not an atlas sprite, so blitSprite would fail.
        guiGraphics.blit(tex, x, y, 0F, 0F, SIZE, SIZE, SIZE, SIZE);
    }
}
