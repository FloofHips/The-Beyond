package com.thebeyond.client.gui;

import com.thebeyond.common.item.PrismographTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

public class ClientCameraTooltip implements ClientTooltipComponent {
    private static final int CELL = 18;
    private final ItemStack film;

    public ClientCameraTooltip(PrismographTooltip tooltip) {
        this.film = tooltip.film();
    }

    @Override
    public int getHeight() {
        return CELL + 2;
    }

    @Override
    public int getWidth(Font font) {
        return CELL;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        CameraGuiBits.sunkenSlot(guiGraphics, x, y);
        if (film.isEmpty()) {
            CameraGuiBits.paperGhost(guiGraphics, x + 1, y + 1);
        } else {
            guiGraphics.renderItem(film, x + 1, y + 1);
            guiGraphics.renderItemDecorations(font, film, x + 1, y + 1);
        }
    }
}
