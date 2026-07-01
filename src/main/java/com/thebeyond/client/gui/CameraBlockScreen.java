package com.thebeyond.client.gui;

import com.thebeyond.common.block.blockentities.CameraBlockEntity;
import com.thebeyond.common.block.blockentities.CameraBlockMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CameraBlockScreen extends AbstractContainerScreen<CameraBlockMenu> {
    private static final int HUMP_W = 52;
    private static final int SPLIT = 32; // hump height above the panel

    public CameraBlockScreen(CameraBlockMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 130;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 34;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int humpX = x + (this.imageWidth - HUMP_W) / 2;
        int humpR = humpX + HUMP_W;
        int splitY = y + SPLIT;
        int right = x + this.imageWidth;
        int bottom = y + this.imageHeight;
        int p = CameraGuiBits.PANEL, hi = CameraGuiBits.PANEL_HI, lo = CameraGuiBits.PANEL_LO, o = CameraGuiBits.OUTLINE;

        g.fill(humpX + 1, y, humpR - 1, y + 1, p);
        g.fill(humpX, y + 1, humpR, splitY, p);
        g.fill(x + 1, splitY, right - 1, splitY + 1, p);
        g.fill(x, splitY + 1, right, bottom - 1, p);
        g.fill(x + 1, bottom - 1, right - 1, bottom, p);

        g.fill(humpX + 1, y, humpR - 1, y + 1, hi);
        g.fill(humpX, y + 1, humpX + 1, splitY - 1, hi);
        g.fill(x + 1, splitY, humpX - 1, splitY + 1, hi);
        g.fill(humpR + 1, splitY, right - 1, splitY + 1, hi);
        g.fill(x, splitY + 1, x + 1, bottom - 1, hi);
        g.fill(humpR - 1, y + 1, humpR, splitY - 1, lo);
        g.fill(right - 1, splitY + 1, right, bottom - 1, lo);
        g.fill(x + 1, bottom - 1, right - 1, bottom, lo);

        g.fill(humpX - 1, splitY - 1, humpX, splitY, hi);
        g.fill(humpR, splitY - 1, humpR + 1, splitY, lo);

        g.fill(humpX + 1, y - 1, humpR - 1, y, o);
        g.fill(humpX, y, humpX + 1, y + 1, o);
        g.fill(humpR - 1, y, humpR, y + 1, o);
        g.fill(humpX - 1, y + 1, humpX, splitY - 1, o);
        g.fill(humpR, y + 1, humpR + 1, splitY - 1, o);
        g.fill(x + 1, splitY - 1, humpX - 1, splitY, o);
        g.fill(humpR + 1, splitY - 1, right - 1, splitY, o);
        g.fill(x, splitY, x + 1, splitY + 1, o);
        g.fill(right - 1, splitY, right, splitY + 1, o);
        g.fill(x - 1, splitY + 1, x, bottom - 1, o);
        g.fill(right, splitY + 1, right + 1, bottom - 1, o);
        g.fill(x + 1, bottom, right - 1, bottom + 1, o);
        g.fill(x, bottom - 1, x + 1, bottom, o);
        g.fill(right - 1, bottom - 1, right, bottom, o);

        for (Slot slot : this.menu.slots) {
            CameraGuiBits.sunkenSlot(g, x + slot.x - 1, y + slot.y - 1);
        }
        Slot film = this.menu.slots.get(CameraBlockEntity.FILM);
        if (!film.hasItem()) {
            CameraGuiBits.paperGhost(g, x + film.x, y + film.y);
        }
    }
}
