package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.StalkerModel;
import com.thebeyond.client.renderer.renderlayers.StalkerGenerationLayer;
import com.thebeyond.common.entity.StalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public class StalkerRenderer extends LivingEntityRenderer<StalkerEntity, StalkerModel<StalkerEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/stalker/stalker.png");
    public StalkerRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new StalkerModel<>(pContext.bakeLayer(BeyondModelLayers.STALKER)),0.25F);
        this.addLayer(new StalkerGenerationLayer(this, pContext.getModelSet()));
    }

    @Override
    protected void setupRotations(StalkerEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        Direction facing = entity.getFacing();

        if (facing == Direction.UP) {
            poseStack.translate(0.5f, 0.5f, 0);
            poseStack.mulPose(Axis.ZP.rotation((float) (Math.PI/2f)));
        } else if (facing == Direction.DOWN) {
            poseStack.translate(-0.5f, 0.5f, 0);
            poseStack.mulPose(Axis.ZP.rotation((float) (-Math.PI/2f)));
        }

        super.setupRotations(entity, poseStack, bob, facing.toYRot(), partialTick, scale);
    }

    @Override
    protected boolean shouldShowName(StalkerEntity entity) {
        return super.shouldShowName(entity) && (entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity);
    }

    @Override
    public ResourceLocation getTextureLocation(StalkerEntity E) {
        return TEXTURE;
    }
}
