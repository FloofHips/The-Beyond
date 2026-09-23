package com.thebeyond.client.gui;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.menu.MemoryBankMenu;
import com.thebeyond.client.renderer.blockentities.SnapshotTextures;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.network.MemoryBankChangeBankPagePacket;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MemoryBankScreen  extends AbstractContainerScreen<MemoryBankMenu> {
    private static final ResourceLocation BANK = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/screen.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/overlay.png");

    protected int imageWidth = 362;
    protected int imageHeight = 198;

    public MemoryBankScreen(MemoryBankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;


        Button prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            PacketDistributor.sendToServer(new MemoryBankChangeBankPagePacket(menu.containerId, -1));
            }).bounds(x + 100, y - 10, 20, 18).build());

        Button nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            PacketDistributor.sendToServer(new MemoryBankChangeBankPagePacket(menu.containerId, 1));
        }).bounds(x + 124, y - 10, 20, 18).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        float startX = this.width / 2f;
        float startY = this.height / 2f;

        guiGraphics.drawString(this.font, String.valueOf(mouseX), 0, 0, 4210752, false);
        guiGraphics.drawString(this.font, String.valueOf(mouseY), 0, 10, 4210752, false);
        guiGraphics.blit(BANK, (int) (startX - imageWidth/2), (int) (startY - imageHeight/2), 0, 0, imageWidth, imageHeight, imageWidth,imageHeight);

        NonNullList<Slot> slots = this.getMenu().slots;
        for (Slot slot : slots) {
            if (slot!=null) {
                if (slot.index > 23) continue;
                ItemStack stack = slot.getItem();
                if (stack.has(BeyondComponents.SNAPSHOT_PIXELS)) {
                    Components.SnapshotPixelsComponent px = stack.get(BeyondComponents.SNAPSHOT_PIXELS.get());
                    ResourceLocation tex = SnapshotTextures.getDownsampled(px, Grades.NONE,32);
                    guiGraphics.blit(tex, (int) (startX - imageWidth/2) + slot.x + 85, (int) (startY - imageHeight/2) + slot.y + 8, 0F, 0F, 32, 32, 32, 32);
                    RenderUtils.renderMultiplicativeQuad(guiGraphics, OVERLAY, (int) (startX - imageWidth/2) + slot.x + 85, (int) (startY - imageHeight/2) + slot.y + 8, 0, 0, 32, 32, 32,32,-1);
                }
            }
        }

        guiGraphics.drawString(this.font, String.valueOf(getMenu().getBank().getPage()), (int) (startX - imageWidth/2), 10, 4210752, false);
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.index > 23)
            super.renderSlot(guiGraphics, slot);
    }
}