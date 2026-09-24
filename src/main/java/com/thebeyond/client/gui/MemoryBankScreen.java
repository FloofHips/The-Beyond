package com.thebeyond.client.gui;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.menu.MemoryBankMenu;
import com.thebeyond.client.renderer.blockentities.SnapshotTextures;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.network.MemoryBankChangeBankPagePacket;
import com.thebeyond.common.network.MemoryBankMagnifyModePacket;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class MemoryBankScreen  extends AbstractContainerScreen<MemoryBankMenu> {
    private static final ResourceLocation BANK = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/screen.png");
    private static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/overlay.png");

    private static final ResourceLocation GLASS = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/glass.png");
    private static final ResourceLocation GLASS_OFF = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/glass_off.png");
    private static final ResourceLocation GLASS_MOUSE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/memory_bank/glass_mouse.png");

    protected int imageWidth = 362;
    protected int imageHeight = 198;

    public Button glass;

    public MemoryBankScreen(MemoryBankMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = (width / 2) - 44;
        int y = (height / 2);
        this.titleLabelX = -84;
        this.titleLabelY = -19;
        this.inventoryLabelX = 181;
        this.inventoryLabelY = -13;

        Button prevButton = addWidget(Button.builder(Component.literal("<"), b -> {
            PacketDistributor.sendToServer(new MemoryBankChangeBankPagePacket(menu.containerId, -1));
            }).bounds(x - 25 - 117, y - 15, 20, 18).build());

        Button nextButton = addWidget(Button.builder(Component.literal(">"), b -> {
            PacketDistributor.sendToServer(new MemoryBankChangeBankPagePacket(menu.containerId, 1));
        }).bounds(x + 5 + 117, y - 15, 20, 18).build());

        glass = addWidget(Button.builder(Component.empty(), b -> {
            boolean mag = menu.magnifyMode;
            PacketDistributor.sendToServer(new MemoryBankMagnifyModePacket(menu.containerId));
            menu.magnifyMode = !mag;
        }).bounds(x - 137, -25 + y + imageHeight/2, 38, 42).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (menu.getMagnifiedSlot() >= 0) {
            ItemStack s = menu.getMagnifiedStack();
            if (s!=null && s.has(BeyondComponents.SNAPSHOT_PIXELS)) {
                guiGraphics.pose().pushPose();
                this.renderTransparentBackground(guiGraphics);
                SnapshotScreen.renderSnapshot(guiGraphics, guiGraphics.pose(), this.width, this.height, 2, s, Minecraft.getInstance().font);
                guiGraphics.pose().popPose();
                return;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -1, true);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, -1, true);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        float startX = this.width / 2f;
        float startY = this.height / 2f;

        guiGraphics.blit(BANK, (int) (startX - imageWidth/2)-5, (int) (startY - imageHeight/2), 0, 0, imageWidth+5, imageHeight, imageWidth+5,imageHeight);
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

        guiGraphics.blit(getLocation(), glass.getX(), glass.getY(), 0F, 0F, 38, 42, 38, 42);
        if (getMenu().magnifyMode) {
            guiGraphics.blit(GLASS_MOUSE, mouseX+5, mouseY, 0F, 0F, 8, 8, 8, 8);
        }
    }

    private @NotNull ResourceLocation getLocation() {
        return this.getMenu().magnifyMode ? GLASS_OFF : GLASS;
    }

    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.index > 23)
            super.renderSlot(guiGraphics, slot);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.getMagnifiedSlot() >= 0) {
            menu.clearMagnify();
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (menu.getMagnifiedSlot() >= 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_E) {
                menu.clearMagnify();
                return true;
            }
        }

        return super.keyPressed(key, scan, mods);
    }
}