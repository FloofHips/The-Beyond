package com.thebeyond.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.RefugeMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.sql.Ref;

@OnlyIn(Dist.CLIENT)
public class RefugeScreen extends AbstractContainerScreen<RefugeMenu> {
    private static final ResourceLocation REFUGE_LOCATION = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/refuge/refuge.png");
    static final ResourceLocation CONFIRM_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/refuge/confirm.png");
    static final ResourceLocation CANCEL_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/refuge/cancel.png");

    static final ResourceLocation HUNGER_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/refuge/hunger.png");
    static final ResourceLocation EXPLOSION_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/refuge/explosion.png");
    static final ResourceLocation MOB_SPAWN_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/refuge/mob_spawn.png");
    static final ResourceLocation FALL_DAMAGE_SPRITE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/refuge/fall_damage.png");
    static final ResourceLocation OUTLINE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/refuge/outline.png");

    static final ResourceLocation LEFT = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/refuge/left.png");
    static final ResourceLocation UP = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/refuge/up.png");
    static final ResourceLocation RIGHT = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/refuge/right.png");
    static final ResourceLocation DOWN = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/refuge/down.png");

    protected int imageWidth = 176;
    protected int imageHeight = 186;

    int MINX;
    int MAXX;
    int MINY;
    int MAXY;

    public RefugeScreen(RefugeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 103;
    }

    @Override
    protected void init() {
        super.init();

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        MINX = i-1 + 49;
        MAXX = i-1 + 49;

        MINY = j-1 + 49;
        MAXY = j-1 + 49;

        addRenderableWidget(new RefugeScreenButton(this.leftPos + 36, this.topPos - 4, (byte) 0, HUNGER_SPRITE));
        addRenderableWidget(new RefugeScreenButton(this.leftPos + 6,  this.topPos + 26, (byte) 1, EXPLOSION_SPRITE));
        addRenderableWidget(new RefugeScreenButton(this.leftPos + 66, this.topPos + 26, (byte) 2, MOB_SPAWN_SPRITE));
        addRenderableWidget(new RefugeScreenButton(this.leftPos + 36, this.topPos + 56, (byte) 3, FALL_DAMAGE_SPRITE));

        addRenderableWidget(new DecisionScreenButton(this.leftPos + 66, this.topPos + 56, false, CANCEL_SPRITE));
        addRenderableWidget(new DecisionScreenButton(this.leftPos + 6, this.topPos + 56, true, CONFIRM_SPRITE));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        MINX = (int) Mth.lerp(0.05, MINX, i-1);
        MAXX = (int) Mth.lerp(0.05, MAXX, i-1+117);

        MINY = (int) Mth.lerp(0.05, MINY, j-1);
        MAXY = (int) Mth.lerp(0.05, MAXY, j+117);

        guiGraphics.enableScissor(MINX, MINY, MAXX, MAXY);
        guiGraphics.blit(UP, i-1, j-2,0,0, 98, 98, 98, 98);
        guiGraphics.blit(LEFT, i-1, j,0,0, 98, 98, 98, 98);
        guiGraphics.blit(RIGHT, i-1, j,0,0, 98, 98, 98, 98);
        guiGraphics.blit(DOWN, i-1, j,0,0, 98, 98, 98, 98);
        guiGraphics.disableScissor();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(REFUGE_LOCATION, i, j, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        //guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @OnlyIn(Dist.CLIENT)
    class RefugeScreenButton extends AbstractButton {
        private ResourceLocation resourcelocation;
        protected RefugeScreenButton(int x, int y, byte value, ResourceLocation resourcelocation) {
            super(x, y, 24, 24, CommonComponents.EMPTY);
            this.resourcelocation = resourcelocation;
        }

        protected RefugeScreenButton(int x, int y, Component message) {
            super(x, y, 24, 24, message);
        }

        @Override
        public void onPress() {

        }

        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

            guiGraphics.blit(this.resourcelocation, this.getX(), this.getY(),0,0, 24, 24, 24,24);

            if (this.isHovered()) {
                guiGraphics.blit(OUTLINE, this.getX(), this.getY(),0,0, 24, 24, 24,24);
            }
        }

        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }


    @OnlyIn(Dist.CLIENT)
    class DecisionScreenButton extends AbstractButton {
        boolean disabled;
        private ResourceLocation resourcelocation;
        protected DecisionScreenButton(int x, int y, boolean value, ResourceLocation resourcelocation) {
            super(x, y, 24, 24, CommonComponents.EMPTY);
            this.resourcelocation = resourcelocation;
        }

        protected DecisionScreenButton(int x, int y, Component message) {
            super(x, y, 24, 24, message);
        }

        @Override
        public void onPress() {

        }

        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

            guiGraphics.blit(this.resourcelocation, this.getX(), this.getY(), disabled ? 24 : 0, 0, 24, 24, 48,24);

            if (this.isHovered()) {
                guiGraphics.blit(OUTLINE, this.getX(), this.getY(),0,0, 24, 24, 24,24);
            }
        }

        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }
}
