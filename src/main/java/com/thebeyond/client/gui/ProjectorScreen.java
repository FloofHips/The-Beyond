package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.common.block.blockentities.ProjectorMenu;
import com.thebeyond.common.network.ProjectorCarouselAutoPayload;
import com.thebeyond.common.network.ProjectorCarouselPayload;
import com.thebeyond.common.network.ProjectorRotatePayload;
import com.thebeyond.common.network.ProjectorFlipPayload;
import com.thebeyond.common.network.ProjectorSetModePayload;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/** Buttons hold no local state: every change is a C2S payload, so they re-read active/visible/label from the menu each frame. */
@OnlyIn(Dist.CLIENT)
public class ProjectorScreen extends AbstractContainerScreen<ProjectorMenu> {

    private static final ResourceLocation BG_LOCATION = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/projector/background.png");
    static final ResourceLocation BACKPLATE_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/projector/backplate.png");
    static final ResourceLocation MAGAZINE_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/projector/magazine.png");
    static final ResourceLocation LIGHT_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/projector/light.png");

    private Button[] modeButtons;
    private Button prevButton;
    private Button nextButton;
    private Button autoButton;
    float rot;
    int rotTarget;

    public ProjectorScreen(ProjectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 167;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        rot = menu.getMode()*90;
        rotTarget = (int) rot;

        this.titleLabelX = 8;//(this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 44;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;


        this.addWidget(Button.builder(Component.literal("Previous Mode"),
                        b -> {
                            rotTarget = getNextMode(menu.getMode() - 1)*90;
                            PacketDistributor.sendToServer(new ProjectorSetModePayload(menu.getBlockPos(), (byte) getNextMode(menu.getMode() - 1)));
                        })
                .bounds(x + 48, y, 27, 27)
                .build());

        this.addWidget(Button.builder(Component.literal("Next Mode"),
                        b -> {
                            rotTarget = getNextMode(menu.getMode() + 1)*90;
                            PacketDistributor.sendToServer(new ProjectorSetModePayload(menu.getBlockPos(), (byte) getNextMode(menu.getMode() + 1)));
                        })
                .bounds(x + 100, y, 27, 27)
                .build());
    }

    public int getNextMode(int mode) {
        return mode%4;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Sync before super.render, else a button hidden this frame could still intercept the click.

        //for (int i = 0; i < modeButtons.length; i++) {
        //    modeButtons[i].active = menu.getMode() != i;
        //}
        //boolean carousel = menu.getMode() == ProjectorBlockEntity.MODE_CAROUSEL;
        //prevButton.visible = carousel;
        //nextButton.visible = carousel;
        //autoButton.visible = carousel;
        //autoButton.setMessage(autoLabel());

       if (rot != rotTarget) {
           rot = (float) Mth.rotLerp(0.1, rot, rotTarget);

           rot = (int) rot;
       }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        //int x = (this.width - this.imageWidth) / 2;
        //int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(BACKPLATE_SPRITE, x+48, y,0,0, 80, 80, 80, 80);

        guiGraphics.pose().pushPose();
        int radius = 40;
        int centerX = x+48 + radius;
        int centerY = y + radius;
        guiGraphics.pose().translate(centerX, centerY, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        guiGraphics.blit(MAGAZINE_SPRITE, -radius, -radius,0,0, 80, 80, 80, 80);
        guiGraphics.pose().popPose();

        RenderSystem.enableBlend();
        guiGraphics.blit(BG_LOCATION, x, y, 0,0,176, 167);
        RenderSystem.defaultBlendFunc();

        RenderUtils.renderAdditiveQuad(guiGraphics, LIGHT_SPRITE, x+71, y+23,0,0, 34, 34, 34, 34, 0);
    }
}
