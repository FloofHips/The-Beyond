package com.thebeyond.client.renderer.blockentities;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.client.renderer.AuroraBorealisRenderer;
import com.thebeyond.common.block.RefugeBlock;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static net.minecraft.client.renderer.blockentity.SkullBlockRenderer.SKIN_BY_TYPE;

public class RefugeRenderer implements BlockEntityRenderer<RefugeBlockEntity> {
    private final PlayerModel<AbstractClientPlayer> playerModel;

    public RefugeRenderer(BlockEntityRendererProvider.Context context) {
        EntityModelSet modelSet = context.getModelSet();
        this.playerModel = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER), true);
    }

    @Override
    public void render(RefugeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ResolvableProfile profile = blockEntity.getOwnerProfile();

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.scale(3f, -3.0f, 3f);
        poseStack.translate(0, -1.8, 0);
        renderModel(poseStack, bufferSource, packedLight, packedOverlay, profile);
        poseStack.popPose();

        if (blockEntity.animating > 0) renderRootShock(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    public void renderRootShock(RefugeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        float rootHeight = (float)Math.sin((blockEntity.getLevel().getGameTime() + partialTick) * 0.9f) * 0.2f;

        //poseStack.translate(0, rootHeight, 0);
        poseStack.mulPose(Axis.XP.rotation((float) Math.PI / 2f));
        poseStack.scale(144, 144, 16);
        poseStack.translate(-0.5, -0.5, -0.5);


        RenderUtils.renderModel(
                ModClientEvents.ROOT_MODEL,
                poseStack,
                buffer.getBuffer(BeyondRenderTypes.cutout()),
                packedLight,
                packedOverlay,
                1, 1, 1, 1
        );

        poseStack.popPose();
    }

    public void renderRoots(RefugeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        //Set<ChunkPos> affectedChunks = blockEntity.getAffectedChunks();
        //if (affectedChunks == null || affectedChunks.isEmpty()) return;

        poseStack.pushPose();

        float rootHeight = (float)Math.sin((blockEntity.getLevel().getGameTime() + partialTick) * 0.1f) * 0.2f;
        Font font = Minecraft.getInstance().font;

        for (int i = -4; i <= 4; i++) {
            for (int j = -4; j <= 4; j++) {
                poseStack.pushPose();

                double xOffset = (blockEntity.getBlockPos().getX() + i * 16 + 128);
                double zOffset = (blockEntity.getBlockPos().getZ() + j * 16);

                poseStack.translate(xOffset + 8.0, 0, zOffset + 8.0);

                poseStack.translate(0, rootHeight, 0);
                poseStack.scale(16,16,16);

                poseStack.translate(0, -0.4, 0);
                RenderUtils.renderModel(
                        ModClientEvents.ROOT_MODEL,
                        poseStack,
                        buffer.getBuffer(BeyondRenderTypes.cutout()),
                        packedLight,
                        packedOverlay,
                        1, 1, 1, 1
                );

                //poseStack.translate(0, 0, 1);
                //poseStack.mulPose(Axis.YP.rotationDegrees(90));
//
                //RenderUtils.renderModel(
                //        getRootsModel(i,j),
                //        poseStack,
                //        buffer.getBuffer(BeyondRenderTypes.cutout()),
                //        packedLight,
                //        packedOverlay,
                //        1, 1, 1, 1
                //);

                poseStack.pushPose();

                poseStack.translate(0, 1.0, 0);

                float textScale = 0.025f;
                poseStack.scale(textScale, -textScale, textScale);

                String text = "i=" + i;
                String text2 = "j=" + j;

                int width = font.width(text);
                int width2 = font.width(text2);

                font.drawInBatch(
                        text,
                        -width / 2.0f, 0,
                        0xFFFFFFFF,
                        false,
                        poseStack.last().pose(),
                        buffer,
                        Font.DisplayMode.NORMAL,
                        0,
                        packedLight
                );

                font.drawInBatch(
                        text2,
                        -width2 / 2.0f, 10,
                        0xFFFFFF00,
                        false,
                        poseStack.last().pose(),
                        buffer,
                        Font.DisplayMode.NORMAL,
                        0,
                        packedLight
                );

                poseStack.popPose();

                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }

    //private static @NotNull ResourceLocation getRootsModel(int x, int z) {
    //    return (Math.abs(x)%2 == Math.abs(z)%2) ? ModClientEvents.ROOTS_MODEL : ModClientEvents.CLOUD_2_MODEL;
    //}

    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, ResolvableProfile profile) {

        playerModel.setAllVisible(true);

        playerModel.head.xScale = 1.5f;
        playerModel.head.yScale = 1.5f;
        playerModel.head.zScale = 1.5f;

        playerModel.hat.xScale = 2.1f;
        playerModel.hat.yScale = 2.1f;
        playerModel.hat.y = -1f;
        playerModel.hat.zScale = 2.1f;

        SkinManager skinmanager = Minecraft.getInstance().getSkinManager();

        if (profile != null) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(skinmanager.getInsecureSkin(profile.gameProfile()).texture()));
            playerModel.renderEars(poseStack, vertexConsumer, packedLight, packedOverlay);

            playerModel.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 0xFFFFFFFF);
        } else {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityTranslucent(DefaultPlayerSkin.getDefaultTexture()));
            playerModel.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, 0xFFFFFFFF);
        }
    }

    private ResourceLocation getSkin(ResolvableProfile profile) {
        if (profile == null) {
            return DefaultPlayerSkin.getDefaultTexture();
        }

        GameProfile gameProfile = profile.gameProfile();
        if (gameProfile.getProperties().containsKey("textures")) {
            Minecraft mc = Minecraft.getInstance();
            return mc.getSkinManager().getInsecureSkin(gameProfile).texture();
        }

        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    public int getViewDistance() {
        return 90;
    }

    @Override
    public boolean shouldRenderOffScreen(RefugeBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(RefugeBlockEntity blockEntity, Vec3 cameraPos) {
        return true;
    }
}
