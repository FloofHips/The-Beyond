package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.EnadrakeModel;
import com.thebeyond.client.model.EnatiousTotemModel;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class EnatiousTotemRenderer extends MobRenderer<EnatiousTotemEntity, EnatiousTotemModel<EnatiousTotemEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enatious_totem.png");
    public EnatiousTotemRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new EnatiousTotemModel<>(pContext.bakeLayer(BeyondModelLayers.ENATIOUS_TOTEM)),1F);
    }

    @Override
    public void render(EnatiousTotemEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        boolean flag = entity.hurtTime > 0;
        super.render(entity, entityYaw, 0, poseStack, buffer, packedLight);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YN.rotationDegrees(entity.yBodyRot));
        poseStack.translate(entity.blockPosition().getX() - entity.getX(),3,entity.blockPosition().getZ() - entity.getZ());
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(BeyondBlocks.VOID_FLAME.get().defaultBlockState(), poseStack, buffer, 255, OverlayTexture.pack(0.0F, flag));
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(EnatiousTotemEntity enatiousTotemEntity) {
        return TEXTURE;
    }
}