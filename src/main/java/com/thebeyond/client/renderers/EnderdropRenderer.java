package com.thebeyond.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.models.BeyondModelLayers;
import com.thebeyond.client.models.EnderdropModel;
import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;

public class EnderdropRenderer extends MobRenderer<EnderglopEntity, EnderdropModel<EnderglopEntity>> {
    private static final ResourceLocation ENDERDROP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enderglop/enderdrop.png");

    public EnderdropRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new EnderdropModel<>(pContext.bakeLayer(BeyondModelLayers.ENDERDROP_LAYER)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(EnderglopEntity enderglopEntity) {
        return ENDERDROP_TEXTURE;
    }

    public void render(EnderglopEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.shadowRadius = 0.25F * (float)entity.getSize();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    protected void scale(EnderglopEntity livingEntity, PoseStack poseStack, float partialTickTime) {
        float f = 0.999F;
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float f1 = (float)livingEntity.getSize();
        float f2 = Mth.lerp(partialTickTime, livingEntity.oSquish, livingEntity.squish) / (f1 * 0.5F + 1.0F);
        float f3 = 1.0F / (f2 + 1.0F);
        poseStack.scale(f3 * f1, 1.0F / f3 * f1, f3 * f1);
    }

}
