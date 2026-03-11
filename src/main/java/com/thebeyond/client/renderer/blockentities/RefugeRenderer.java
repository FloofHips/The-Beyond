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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
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

        poseStack.pushPose();

        int x1 = blockEntity.getBlockPos().getX();
        int z1 = blockEntity.getBlockPos().getZ();

        int x = x1 % 16;
        int z = z1 % 16;

        x = (x < 0 ? Math.abs(x) - 8 : 8 - x);
        z = (z < 0 ? Math.abs(z) - 8 : 8 - z);

        poseStack.translate(x, 0, z);

        //renderPart(poseStack, buffer.getBuffer(BeyondRenderTypes.entityTranslucent(ResourceLocation.withDefaultNamespace("block/white_concrete.png"))), 0, 16, -rad, -rad, rad, -rad, -rad, rad, rad, rad, 0.0F, 1.0F, 0.0F, 1.0F);

        for (int i = 1; i <= 3; i++) {
            int rad = 8*i*3;
            renderPart(poseStack, buffer.getBuffer(BeyondRenderTypes.entityTranslucent(ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"))), 0, 16, -rad, -rad, rad, -rad, -rad, rad, rad, rad, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        poseStack.popPose();
    }

    private static void renderPart(PoseStack poseStack, VertexConsumer consumer, int minY, int maxY, int x1, int z1, int x2, int z2, int x3, int z3, int x4, int z4, float minU, float maxU, float minV, float maxV) {
        PoseStack.Pose pose = poseStack.last();

        renderQuad(pose, consumer, minY, maxY, x1, z1, x2, z2, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, minY, maxY, x4, z4, x3, z3, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, minY, maxY, x2, z2, x4, z4, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, minY, maxY, x3, z3, x1, z1, minU, maxU, minV, maxV);

        renderQuad(pose, consumer, minY, -maxY, x1, z1, x2, z2, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, minY, -maxY, x4, z4, x3, z3, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, minY, -maxY, x2, z2, x4, z4, minU, maxU, minV, maxV);
        renderQuad(pose, consumer, minY, -maxY, x3, z3, x1, z1, minU, maxU, minV, maxV);

    }

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer consumer, int minY, int maxY, int minX, int minZ, int maxX, int maxZ, float minU, float maxU, float minV, float maxV) {
        int rgb = new Color(1,1,1,0).getRGB();
        addVertex(pose, consumer, rgb, minX, maxY, minZ, maxU, minV);
        addVertex(pose, consumer, Color.blue.getRGB(), minX, minY, minZ, maxU, maxV);
        addVertex(pose, consumer, Color.blue.getRGB(), maxX, minY, maxZ, minU, maxV);
        addVertex(pose, consumer, rgb, maxX, maxY, maxZ, minU, minV);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color, int x, int y, int z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

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
    public boolean shouldRenderOffScreen(RefugeBlockEntity blockEntity) {
        return true;
    }

    public int getViewDistance() {
        return 256;
    }

    @Override
    public boolean shouldRender(RefugeBlockEntity blockEntity, Vec3 cameraPos) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply((double)1.0F, (double)0.0F, (double)1.0F).closerThan(cameraPos.multiply((double)1.0F, (double)0.0F, (double)1.0F), (double)this.getViewDistance());
    }

    @Override
    public AABB getRenderBoundingBox(RefugeBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 145, pos.getY() - 64, pos.getZ() - 145, pos.getX() + 145, pos.getY() + 64, pos.getZ() + 145);
    }
}
