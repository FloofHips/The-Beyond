package com.thebeyond.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.renderer.blockentities.RefugeRenderer;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.block.blockentities.RefugeMenu;
import com.thebeyond.common.network.RefugeSetModePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

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

    int offset = 40;

    private byte selectedMode = -1;
    private RefugeRenderer refugeRenderer;

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

        Minecraft mc = Minecraft.getInstance();
        BlockEntityRendererProvider.Context rendererContext = new BlockEntityRendererProvider.Context(
                mc.getBlockEntityRenderDispatcher(), mc.getBlockRenderer(),
                mc.getItemRenderer(), mc.getEntityRenderDispatcher(),
                mc.getEntityModels(), mc.font
        );
        this.refugeRenderer = new RefugeRenderer(rendererContext);

        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        MINX = i-1 + 49 + offset;
        MAXX = i-1 + 49 + offset;

        MINY = j-1 + 49;
        MAXY = j-1 + 49;

        addRenderableWidget(new RefugeScreenButton(this.leftPos + offset + 36, this.topPos - 4, (byte) 0, HUNGER_SPRITE));
        addRenderableWidget(new RefugeScreenButton(this.leftPos + offset + 6,  this.topPos + 26, (byte) 1, EXPLOSION_SPRITE));
        addRenderableWidget(new RefugeScreenButton(this.leftPos + offset + 66, this.topPos + 26, (byte) 2, MOB_SPAWN_SPRITE));
        addRenderableWidget(new RefugeScreenButton(this.leftPos + offset + 36, this.topPos + 56, (byte) 3, FALL_DAMAGE_SPRITE));

        addRenderableWidget(new DecisionScreenButton(this.leftPos + offset + 66, this.topPos + 56, false, CANCEL_SPRITE));
        addRenderableWidget(new DecisionScreenButton(this.leftPos + offset + 6, this.topPos + 56, true, CONFIRM_SPRITE));
    }

    private void renderPlayerPreview(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || refugeRenderer == null) return;

        BlockEntity be = mc.level.getBlockEntity(this.menu.getBlockPos());
        if (!(be instanceof RefugeBlockEntity refuge)) return;

        byte displayMode = selectedMode >= 0 ? selectedMode : menu.getMode();

        // Set up transforms for raw PlayerModel rendering in GUI
        // In GUI space Y goes down, same as PlayerModel, so no Y flip needed
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos + 20, this.topPos + 4, 50.0);
        guiGraphics.pose().scale(35, 35, 35);
        guiGraphics.pose().mulPose(new Quaternionf().rotateY((float) Math.PI));

        Lighting.setupForEntityInInventory();

        RenderSystem.runAsFancy(() -> {
            refugeRenderer.renderPlayer(displayMode, guiGraphics.pose(), guiGraphics.bufferSource(), 15728880, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, refuge.getOwnerProfile());
        });

        guiGraphics.flush();
        guiGraphics.pose().popPose();
        Lighting.setupFor3DItems();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int i = (this.width - this.imageWidth) / 2;
        int j = (this.height - this.imageHeight) / 2;

        MINX = (int) Mth.lerp(0.05, MINX, i-1 + offset);
        MAXX = (int) Mth.lerp(0.05, MAXX, i-1+117 + offset);

        MINY = (int) Mth.lerp(0.05, MINY, j-1);
        MAXY = (int) Mth.lerp(0.05, MAXY, j+117);

        renderPlayerPreview(guiGraphics);

        guiGraphics.enableScissor(MINX, MINY, MAXX, MAXY);
        if (menu.getMode() == RefugeBlockEntity.MODE_HUNGER)
            guiGraphics.blit(UP, i-1 + offset, j-2,0,0, 98, 98, 98, 98);
        if (menu.getMode() == RefugeBlockEntity.MODE_EXPLOSION)
            guiGraphics.blit(LEFT, i-1 + offset, j,0,0, 98, 98, 98, 98);
        if (menu.getMode() == RefugeBlockEntity.MODE_MOB_SPAWN)
            guiGraphics.blit(RIGHT, i-1 + offset, j,0,0, 98, 98, 98, 98);
        if (menu.getMode() == RefugeBlockEntity.MODE_FALL_DAMAGE)
            guiGraphics.blit(DOWN, i-1 + offset, j,0,0, 98, 98, 98, 98);
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
        private final ResourceLocation resourcelocation;
        private final byte mode;

        protected RefugeScreenButton(int x, int y, byte mode, ResourceLocation resourcelocation) {
            super(x, y, 24, 24, CommonComponents.EMPTY);
            this.resourcelocation = resourcelocation;
            this.mode = mode;
        }

        @Override
        public void onPress() {
            if (selectedMode == -1 || selectedMode != menu.getMode())
                selectedMode = this.mode;
        }

        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.blit(this.resourcelocation, this.getX(), this.getY(),0,0, 24, 24, 24,24);

            if (this.isHovered() || selectedMode == this.mode) {
                guiGraphics.blit(OUTLINE, this.getX(), this.getY(),0,0, 24, 24, 24,24);
            }
        }

        public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }


    @OnlyIn(Dist.CLIENT)
    class DecisionScreenButton extends AbstractButton {
        private final boolean isConfirm;
        private final ResourceLocation resourcelocation;

        protected DecisionScreenButton(int x, int y, boolean isConfirm, ResourceLocation resourcelocation) {
            super(x, y, 24, 24, CommonComponents.EMPTY);
            this.resourcelocation = resourcelocation;
            this.isConfirm = isConfirm;
        }

        @Override
        public void onPress() {
            if (isConfirm) {
                if (selectedMode >= 0 && selectedMode <= 3 && menu.hasPayment()) {
                    if (menu.getMode() != selectedMode)
                        PacketDistributor.sendToServer(new RefugeSetModePayload(menu.getBlockPos(), selectedMode));
                }
            } else {
                if (menu.getMode() != selectedMode)
                    PacketDistributor.sendToServer(new RefugeSetModePayload(menu.getBlockPos(), (byte) -1));
            }
        }

        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean disabled = isConfirm && (selectedMode < 0 || !menu.hasPayment());
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
