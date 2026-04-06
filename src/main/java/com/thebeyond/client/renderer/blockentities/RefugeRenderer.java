package com.thebeyond.client.renderer.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.block.RefugeBlock;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.registry.BeyondRenderTypes;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.Minecraft;
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
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

public class RefugeRenderer implements BlockEntityRenderer<RefugeBlockEntity> {
    private final PlayerModel<AbstractClientPlayer> playerModelWide;
    private final PlayerModel<AbstractClientPlayer> playerModelSlim;

    public RefugeRenderer(BlockEntityRendererProvider.Context context) {
        EntityModelSet modelSet = context.getModelSet();
        this.playerModelWide = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER), false);
        this.playerModelSlim = new PlayerModel<>(modelSet.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(RefugeBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getBlockState().getValue(RefugeBlock.POWERED)) {
            ResolvableProfile profile = blockEntity.getOwnerProfile();

            poseStack.pushPose();

            poseStack.translate(0.5D, 0.0D, 0.5D);
            poseStack.scale(3f, -3.0f, 3f);
            poseStack.translate(0, -1.8, 0);

            float deg = (float) Math.PI / 180f;

            poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotation((float) (-blockEntity.rot - Math.PI/2f)));
                renderPlayer(blockEntity.getMode(), poseStack, bufferSource, packedLight, packedOverlay, profile);
            poseStack.popPose();

            poseStack.popPose();

            if (blockEntity.animating > 0)
                renderRootShock(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    public void renderPlayer(byte mode, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, ResolvableProfile profile) {
        PlayerModel<AbstractClientPlayer> activeModel = getModelForProfile(profile);
        applyPose(activeModel, mode);
        renderAdditional(mode, poseStack, bufferSource, packedLight, packedOverlay);
        renderModel(activeModel, poseStack, bufferSource, packedLight, packedOverlay, profile);
    }

    private void renderAdditional(byte mode, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ResourceLocation item = getExtraItem(mode);
        float deg = (float) Math.PI / 180f;
        poseStack.pushPose();
        poseStack.scale(1,-1,1);

        if (mode == (byte) 0) {
            poseStack.scale(0.6f,0.6f,0.6f);
            poseStack.translate(-0.1,-2,-1.1);
            poseStack.mulPose(Axis.YP.rotation(-30*deg));
            RenderUtils.renderModel(
                    item,
                    poseStack,
                    buffer.getBuffer(BeyondRenderTypes.cutout()),
                    packedLight,
                    packedOverlay,
                    1, 1, 1, 1
            );
        }

        if (mode == (byte) 1) {
            poseStack.mulPose(Axis.YP.rotation(30*deg));
            poseStack.scale(0.5f,0.5f,0.5f);
            poseStack.translate(-0.5,-2.25,-0.9);
            RenderUtils.renderModel(
                    item,
                    poseStack,
                    buffer.getBuffer(BeyondRenderTypes.cutout()),
                    packedLight,
                    packedOverlay,
                    1, 1, 1, 1
            );
        }

        if (mode == (byte) 2) {
            poseStack.scale(0.5f,0.5f,0.5f);
            poseStack.translate(-0.3,-1.3,-0.9);
            poseStack.mulPose(Axis.ZP.rotation(50*deg));
            RenderUtils.renderModel(
                    item,
                    poseStack,
                    buffer.getBuffer(BeyondRenderTypes.cutout()),
                    packedLight,
                    packedOverlay,
                    1, 1, 1, 1
            );
        }

        if (mode == (byte) 3) {
            poseStack.scale(0.6f,0.6f,0.6f);
            poseStack.translate(-0.4,-2.64,-0.38);
            poseStack.mulPose(Axis.XP.rotation(-10*deg));
            RenderUtils.renderModel(
                    item,
                    poseStack,
                    buffer.getBuffer(BeyondRenderTypes.cutout()),
                    packedLight,
                    packedOverlay,
                    1, 1, 1, 1
            );
            poseStack.translate(-0.2,0.1,-0.02);
            RenderUtils.renderModel(
                    item,
                    poseStack,
                    buffer.getBuffer(BeyondRenderTypes.cutout()),
                    packedLight,
                    packedOverlay,
                    1, 1, 1, 1
            );
        }




        poseStack.popPose();
    }

    private static ResourceLocation getExtraItem(byte mode) {
        ResourceLocation item = ModClientEvents.ROOT_FOOD;
        switch (mode) {
            case 1 : {
                item = ModClientEvents.ROOT_SHIELD;
                break;
            }
            case 2 : {
                item = ModClientEvents.ROOT_SWORD;
                break;
            }
            case 3 : {
                item = ModClientEvents.ROOT_BOOT;
                break;
            }
        }
        return item;
    }

    private PlayerModel<AbstractClientPlayer> getModelForProfile(ResolvableProfile profile) {
        if (profile != null) {
            SkinManager skinManager = Minecraft.getInstance().getSkinManager();
            PlayerSkin skin = skinManager.getInsecureSkin(profile.gameProfile());
            if (skin.model() == PlayerSkin.Model.SLIM) {
                return playerModelSlim;
            }
        }
        return playerModelWide;
    }

    private void applyPose(PlayerModel<AbstractClientPlayer> playerModel, byte mode) {
        // Reset all rotations
        playerModel.head.resetPose();
        playerModel.hat.resetPose();
        playerModel.body.resetPose();
        playerModel.rightArm.resetPose();
        playerModel.leftArm.resetPose();
        playerModel.rightLeg.resetPose();
        playerModel.leftLeg.resetPose();

        float deg = (float) Math.PI / 180f;

        switch (mode) {
            case 0: // MODE_HUNGER - both arms slightly raised, one leg slightly back
                playerModel.rightArm.xRot = -45 * deg;
                playerModel.rightArm.zRot = -15 * deg;
                playerModel.leftArm.xRot = -45 * deg;
                playerModel.leftArm.zRot = 15 * deg;
                playerModel.rightLeg.xRot = 10 * deg;
                break;

            case 1: // MODE_EXPLOSION - one arm forward, one arm back, legs staggered
                playerModel.head.xRot = 10 * deg;
                playerModel.rightArm.xRot = -150 * deg;
                playerModel.rightArm.zRot = 90 * deg;
                playerModel.rightArm.z = -5.0f;
                playerModel.leftArm.xRot = 30 * deg;
                playerModel.leftArm.zRot = 5 * deg;
                //playerModel.rightLeg.xRot = -15 * deg;
                //playerModel.rightLeg.zRot = 5 * deg;
                playerModel.rightLeg.z = -2.0f;
                playerModel.leftLeg.xRot = 15 * deg;
                playerModel.leftLeg.zRot = -5 * deg;
                break;

            case 2: // MODE_MOB_SPAWN - one arm fully extended up, other arm back, head tilted, legs apart
                playerModel.rightArm.xRot = -180 * deg;
                playerModel.rightArm.zRot = -15 * deg;
                playerModel.leftArm.xRot = 20 * deg;
                playerModel.leftArm.zRot = -30 * deg;
                playerModel.head.xRot = -20 * deg;
                playerModel.head.zRot = 8 * deg;
                playerModel.rightLeg.xRot = -12 * deg;
                playerModel.rightLeg.zRot = 6 * deg;
                playerModel.leftLeg.xRot = 12 * deg;
                playerModel.leftLeg.zRot = -6 * deg;
                break;

            case 3: // MODE_FALL_DAMAGE - both arms up, one leg raised forward
                playerModel.head.xRot = 20 * deg;
                playerModel.rightArm.xRot = -150 * deg;
                playerModel.rightArm.zRot = -15 * deg;
                playerModel.leftArm.xRot = -140 * deg;
                playerModel.leftArm.zRot = 15 * deg;
                playerModel.rightLeg.xRot = 10 * deg;
                playerModel.rightLeg.y = 10.0f;
                playerModel.rightLeg.z = -1.0f;
                playerModel.leftLeg.xRot = 10 * deg;
                break;

            default: // No mode selected - neutral standing
                playerModel.rightArm.zRot = 5 * deg;
                playerModel.leftArm.zRot = -5 * deg;
                break;
        }

        // Copy rotations from body parts to their overlay counterparts
        playerModel.hat.xRot = playerModel.head.xRot;
        playerModel.hat.yRot = playerModel.head.yRot;
        playerModel.hat.zRot = playerModel.head.zRot;

        playerModel.jacket.xRot = playerModel.body.xRot;
        playerModel.jacket.yRot = playerModel.body.yRot;
        playerModel.jacket.zRot = playerModel.body.zRot;

        playerModel.rightSleeve.xRot = playerModel.rightArm.xRot;
        playerModel.rightSleeve.yRot = playerModel.rightArm.yRot;
        playerModel.rightSleeve.zRot = playerModel.rightArm.zRot;
        playerModel.rightSleeve.z = playerModel.rightArm.z;

        playerModel.leftSleeve.xRot = playerModel.leftArm.xRot;
        playerModel.leftSleeve.yRot = playerModel.leftArm.yRot;
        playerModel.leftSleeve.zRot = playerModel.leftArm.zRot;

        playerModel.rightPants.xRot = playerModel.rightLeg.xRot;
        playerModel.rightPants.yRot = playerModel.rightLeg.yRot;
        playerModel.rightPants.zRot = playerModel.rightLeg.zRot;
        playerModel.rightPants.y = playerModel.rightLeg.y;
        playerModel.rightPants.z = playerModel.rightLeg.z;

        playerModel.leftPants.xRot = playerModel.leftLeg.xRot;
        playerModel.leftPants.yRot = playerModel.leftLeg.yRot;
        playerModel.leftPants.zRot = playerModel.leftLeg.zRot;
        playerModel.leftPants.y = playerModel.leftLeg.y;
    }

    private void renderModel(PlayerModel<AbstractClientPlayer> playerModel, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, ResolvableProfile profile) {
        playerModel.setAllVisible(true);

        playerModel.hat.xScale = 1.5f;
        playerModel.hat.yScale = 1.5f;
        playerModel.hat.zScale = 1.5f;

        playerModel.renderToBuffer(poseStack, getVertexConsumer(bufferSource, profile), packedLight, packedOverlay, 0xFFFFFFFF);
    }

    private static VertexConsumer getVertexConsumer(MultiBufferSource bufferSource, ResolvableProfile profile) {
        if (profile == null) {
            ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/entity/enadrake/refuge_base.png");
            return bufferSource.getBuffer(RenderType.entityCutoutNoCull(resourceLocation));
        }

        SkinManager skinmanager = Minecraft.getInstance().getSkinManager();
        ResourceLocation skinTexture = skinmanager.getInsecureSkin(profile.gameProfile()).texture();

        VertexConsumer vertexConsumer;
        if (ShaderCompatLib.isShaderModLoaded()) {
            // Shader mods break custom shaders - apply gradient map on CPU and use vanilla RenderType
            ResourceLocation processedTexture = RefugeGradientTextureManager.getOrCreate(skinTexture);
            vertexConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(processedTexture));
        } else {
            vertexConsumer = bufferSource.getBuffer(BeyondRenderTypes.getRefugeGradient(skinTexture));
        }

        return vertexConsumer;
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


        for (int i = 1; i <= 3; i++) {

            float delayedTime = (float) blockEntity.animating - (20 * (3 - i + 1));

            if (delayedTime <= 0) continue;

            float yi = Math.min(1.0f, delayedTime / 100);
            float y = Mth.sin((float) (Math.PI * yi));

            int rad = 8*i*3;
            renderPart(poseStack, buffer.getBuffer(BeyondRenderTypes.eyes(ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"))), 0, 16*y, -rad, -rad, rad, -rad, -rad, rad, rad, rad, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        poseStack.popPose();
    }

    private static void renderPart(PoseStack poseStack, VertexConsumer consumer, int minY, float maxY, int x1, int z1, int x2, int z2, int x3, int z3, int x4, int z4, float minU, float maxU, float minV, float maxV) {
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

    private static void renderQuad(PoseStack.Pose pose, VertexConsumer consumer, int minY, float maxY, int minX, int minZ, int maxX, int maxZ, float minU, float maxU, float minV, float maxV) {
        int rgb = new Color(0,0,0,1).getRGB();
        int color = new Color(0.1f,0.6f,0.8f,1).getRGB();
        addVertex(pose, consumer, rgb, minX, maxY, minZ, maxU, minV);
        addVertex(pose, consumer, color, minX, minY, minZ, maxU, maxV);
        addVertex(pose, consumer, color, maxX, minY, maxZ, minU, maxV);
        addVertex(pose, consumer, rgb, maxX, maxY, maxZ, minU, minV);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer, int color, int x, float y, int z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
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
        return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(cameraPos.multiply(1.0, 0.0, 1.0), this.getViewDistance());
    }

    @Override
    public AABB getRenderBoundingBox(RefugeBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX() - 145, pos.getY() - 64, pos.getZ() - 145, pos.getX() + 145, pos.getY() + 64, pos.getZ() + 145);
    }
}
