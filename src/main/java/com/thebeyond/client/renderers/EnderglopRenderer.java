package com.thebeyond.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.models.BeyondModelLayers;
import com.thebeyond.client.models.EnderdropModel;
import com.thebeyond.client.models.EnderglopModel;
import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class EnderglopRenderer extends MobRenderer<EnderglopEntity, EnderdropModel<EnderglopEntity>> {

    private static final ResourceLocation ENDERDROP_TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enderglop/enderdrop.png");
    private static final ResourceLocation PURPLESLIME_LOCATION = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enderglop/enderglop_naked.png");
    private static final ResourceLocation PURPLESLIME_ARMOR_LOCATION = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enderglop/enderglop_armored.png");

    private final EnderglopModel<EnderglopEntity> enderglopModel;
    private final EnderdropModel<EnderglopEntity> enderdropModel;

    public EnderglopRenderer(EntityRendererProvider.Context pContext){
        super(pContext,new EnderdropModel<>(pContext.bakeLayer(BeyondModelLayers.ENDERDROP_LAYER)),0.25F);
        this.enderglopModel = new EnderglopModel<>(pContext.bakeLayer(BeyondModelLayers.ENDERGLOP_LAYER));
        this.enderdropModel = new EnderdropModel<>(pContext.bakeLayer(BeyondModelLayers.ENDERDROP_LAYER));
    }

    @Override
    public void render(EnderglopEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        if (entity.isTiny()){
            this.model = enderdropModel;
        }else {
            this.model = enderglopModel;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    public ResourceLocation getTextureLocation(EnderglopEntity pEntity){
        return pEntity.isTiny() ? ENDERDROP_TEXTURE : PURPLESLIME_LOCATION;
        //return pEntity.isCharged() ? PURPLESLIME_ARMOR_LOCATION : PURPLESLIME_LOCATION;
    }

    protected void scale(EnderglopEntity livingEntity, PoseStack poseStack, float partialTickTime) {
        float f = 0.999F;
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float f1 = (float)livingEntity.getSize();
        float f2 = Mth.lerp(partialTickTime, livingEntity.oSquish, livingEntity.squish) / (f1 * 0.5F + 1.0F);
        float f3 = 1.0F / (f2 + 1.0F);
        float f4 = livingEntity.isTiny() ? 0.8F : 0.35F;
        poseStack.scale(f3 * f1 * f4, 1.0F / f3 * f1 * f4, f3 * f1 * f4);
    }

//    @Nullable
//    @Override
//    protected RenderType getRenderType(EnderglopEntity pEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
//        return RenderType.crumbling(pEntity.isTiny() ? ENDERDROP_TEXTURE : PURPLESLIME_LOCATION);
//    }
}
