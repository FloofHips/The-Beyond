package com.thebeyond.client.gui;

import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.common.block.blockentities.ProjectorMenu;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.network.ProjectorCarouselAutoPayload;
import com.thebeyond.common.network.ProjectorCarouselPayload;
import com.thebeyond.common.network.ProjectorRotatePayload;
import com.thebeyond.common.network.ProjectorFlipPayload;
import com.thebeyond.common.network.ProjectorSetGradePayload;
import com.thebeyond.common.network.ProjectorSetModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Buttons hold no local state: every change is a C2S payload, so they re-read active/visible/label from the menu each frame. */
@OnlyIn(Dist.CLIENT)
public class ProjectorScreen extends AbstractContainerScreen<ProjectorMenu> {
    private Button[] modeButtons;
    private Button prevButton;
    private Button nextButton;
    private Button autoButton;
    private Button gradeButton;

    public ProjectorScreen(ProjectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200;
        this.imageHeight = 200;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 22;
        this.inventoryLabelY = 106;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        modeButtons = new Button[4];
        for (int i = 0; i < 4; i++) {
            final byte mode = (byte) i;
            modeButtons[i] = Button.builder(Component.literal(ProjectorBlockEntity.MODE_NAMES[i]),
                            b -> PacketDistributor.sendToServer(new ProjectorSetModePayload(menu.getBlockPos(), mode)))
                    .bounds(x + 8, y + 20 + i * 19, 64, 18)
                    .build();
            addRenderableWidget(modeButtons[i]);
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.the_beyond.projector.rotate"),
                        b -> PacketDistributor.sendToServer(new ProjectorRotatePayload(menu.getBlockPos(), (byte) 1)))
                .bounds(x + 128, y + 20, 64, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.the_beyond.projector.flip"),
                        b -> PacketDistributor.sendToServer(new ProjectorFlipPayload(menu.getBlockPos())))
                .bounds(x + 128, y + 39, 64, 18).build());
        gradeButton = Button.builder(gradeLabel(),
                        b -> PacketDistributor.sendToServer(new ProjectorSetGradePayload(menu.getBlockPos(), nextGradeId())))
                .bounds(x + 128, y + 58, 64, 18).build();
        addRenderableWidget(gradeButton);

        // Added unconditionally; render() toggles visibility to Carousel mode only.
        prevButton = Button.builder(Component.literal("<"),
                        b -> PacketDistributor.sendToServer(new ProjectorCarouselPayload(menu.getBlockPos(), (byte) -1)))
                .bounds(x + 86, y + 58, 18, 18).build();
        nextButton = Button.builder(Component.literal(">"),
                        b -> PacketDistributor.sendToServer(new ProjectorCarouselPayload(menu.getBlockPos(), (byte) 1)))
                .bounds(x + 106, y + 58, 18, 18).build();
        autoButton = Button.builder(autoLabel(),
                        b -> PacketDistributor.sendToServer(new ProjectorCarouselAutoPayload(menu.getBlockPos(), !menu.isCarouselAuto())))
                .bounds(x + 82, y + 80, 60, 18).build();
        addRenderableWidget(prevButton);
        addRenderableWidget(nextButton);
        addRenderableWidget(autoButton);
    }

    private Component autoLabel() {
        return Component.literal(menu.isCarouselAuto() ? "Auto: On" : "Auto: Off");
    }

    private Component gradeLabel() {
        return Component.translatable("screen.the_beyond.projector.tone").append(Grades.label(menu.getGradeId()));
    }

    private ResourceLocation nextGradeId() {
        var cycle = Grades.cycleOrder(Minecraft.getInstance().level.registryAccess());
        int idx = Math.max(0, cycle.indexOf(menu.getGradeId()));
        return cycle.get((idx + 1) % cycle.size());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Sync before super.render, else a button hidden this frame could still intercept the click.
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].active = menu.getMode() != i;
        }
        boolean carousel = menu.getMode() == ProjectorBlockEntity.MODE_CAROUSEL;
        prevButton.visible = carousel;
        nextButton.visible = carousel;
        autoButton.visible = carousel;
        autoButton.setMessage(autoLabel());
        gradeButton.setMessage(gradeLabel());

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        CameraGuiBits.nickedRect(guiGraphics, x, y, this.imageWidth, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            CameraGuiBits.sunkenSlot(guiGraphics, x + slot.x - 1, y + slot.y - 1);
        }
    }
}
