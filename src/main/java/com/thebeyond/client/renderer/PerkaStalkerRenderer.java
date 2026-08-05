package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.PerkaStalkerModel;
import com.thebeyond.client.renderer.renderlayers.PerkaStalkerGenerationLayer;
import com.thebeyond.common.entity.PerkaStalkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public class PerkaStalkerRenderer extends LivingEntityRenderer<PerkaStalkerEntity, PerkaStalkerModel<PerkaStalkerEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/perka_stalker/perka_stalker.png");
    public PerkaStalkerRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new PerkaStalkerModel<>(pContext.bakeLayer(BeyondModelLayers.PERKA_STALKER)),0.25F);
        this.addLayer(new PerkaStalkerGenerationLayer(this, pContext.getModelSet()));
    }

    @Override
    protected void setupRotations(PerkaStalkerEntity entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        Direction facing = entity.getFacing();

        //poseStack.pushPose();

        if (facing == Direction.UP) {
            poseStack.translate(0.5f, 0.5f, 0);
            poseStack.mulPose(Axis.ZP.rotation((float) (Math.PI/2f)));
        } else if (facing == Direction.DOWN) {
            poseStack.translate(-0.5f, 0.5f, 0);
            poseStack.mulPose(Axis.ZP.rotation((float) (-Math.PI/2f)));
        }

        super.setupRotations(entity, poseStack, bob, facing.toYRot(), partialTick, scale);

        //poseStack.popPose();
    }

    @Override
    protected boolean shouldShowName(PerkaStalkerEntity entity) {
        return super.shouldShowName(entity) && (entity.shouldShowName() || entity.hasCustomName() && entity == this.entityRenderDispatcher.crosshairPickEntity);
    }

    @Override
    public ResourceLocation getTextureLocation(PerkaStalkerEntity E) {
        return TEXTURE;
    }
}
