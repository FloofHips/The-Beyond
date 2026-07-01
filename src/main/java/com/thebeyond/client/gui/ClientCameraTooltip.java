package com.thebeyond.client.gui;

import com.thebeyond.common.item.CameraTooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

public class ClientCameraTooltip implements ClientTooltipComponent {
    private static final int CELL = 18;

    private final ItemStack film;
    private final ItemStack fuel;

    public ClientCameraTooltip(CameraTooltip tooltip) {
        this.film = tooltip.film();
        this.fuel = tooltip.fuel();
    }

    @Override
    public int getHeight() {
        return CELL + 2;
    }

    @Override
    public int getWidth(Font font) {
        return CELL * 2;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        int fuelX = x + CELL;
        CameraGuiBits.sunkenSlot(guiGraphics, x, y);
        CameraGuiBits.sunkenSlot(guiGraphics, fuelX, y);
        if (film.isEmpty()) {
            CameraGuiBits.paperGhost(guiGraphics, x + 1, y + 1);
        } else {
            guiGraphics.renderItem(film, x + 1, y + 1);
            guiGraphics.renderItemDecorations(font, film, x + 1, y + 1);
        }
        if (!fuel.isEmpty()) {
            guiGraphics.renderItem(fuel, fuelX + 1, y + 1);
            guiGraphics.renderItemDecorations(font, fuel, fuelX + 1, y + 1);
        }
    }
}
