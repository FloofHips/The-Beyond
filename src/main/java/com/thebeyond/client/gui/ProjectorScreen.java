package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.ProjectorBlock;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.navigation.ScreenRectangle;
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

    float rot;
    float rotTarget;
    ScreenRectangle currentMode;
    ScreenRectangle nextMode;
    ScreenRectangle previousMode;

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

        currentMode = new ScreenRectangle(x+79, y+5, 18, 18);
        nextMode = new ScreenRectangle(x+100, y, 27, 39);
        previousMode = new ScreenRectangle(x+48, y, 27, 39);

        rot = menu.getMode() *90;
        rotTarget = rot;

        this.titleLabelX = 8;
        this.titleLabelY = 44;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 73;

        this.addWidget(Button.builder(Component.literal("Previous Mode"),
                        b -> {
                            rotTarget = cycleMode(menu.getMode() - 1)*90;
                            PacketDistributor.sendToServer(new ProjectorSetModePayload(menu.getBlockPos(), (byte) cycleMode(menu.getMode() - 1)));
                        })
                .bounds(x + 48, y, 27, 39)
                .build());

        this.addWidget(Button.builder(Component.literal("Next Mode"),
                        b -> {
                            rotTarget = cycleMode(menu.getMode() + 1)*90;
                            PacketDistributor.sendToServer(new ProjectorSetModePayload(menu.getBlockPos(), (byte) cycleMode(menu.getMode() + 1)));
                        })
                .bounds(x + 100, y, 27, 39)
                .build());
    }

    public int cycleMode(int mode) {
        return Math.floorMod(mode, 4);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
       if (rot != rotTarget) {
           rot = (float) Mth.rotLerp(0.3, rot, rotTarget);
       }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        if (currentMode.containsPoint(mouseX, mouseY))
            guiGraphics.renderTooltip(this.font, Component.literal(ProjectorBlockEntity.MODE_NAMES[menu.getMode()]), mouseX, mouseY);
        if (nextMode.containsPoint(mouseX, mouseY))
            guiGraphics.renderTooltip(this.font, Component.literal(">"), mouseX, mouseY);
        if (previousMode.containsPoint(mouseX, mouseY))
            guiGraphics.renderTooltip(this.font, Component.literal("<"), mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

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

        if(menu.isLit() == 1)
            RenderUtils.renderAdditiveQuad(guiGraphics, LIGHT_SPRITE, x+71, y+23,0,0, 34, 34, 34, 34, 0);
    }
}
