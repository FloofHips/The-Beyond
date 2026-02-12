package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.EnadrakeModel;
import com.thebeyond.client.model.EnderdropModel;
import com.thebeyond.client.model.EnderglopModel;
import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

@OnlyIn(Dist.CLIENT)
public class EnderglopRenderer extends MobRenderer<EnderglopEntity, EnderdropModel<EnderglopEntity>> {

    private static final ResourceLocation ENDERGLOP = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enderglop/enderglop.png");
    private static final ResourceLocation ENDERGLOP_ARMORED = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enderglop/enderglop_armored.png");


    public EnderglopRenderer(EntityRendererProvider.Context pContext){
        super(pContext,new EnderdropModel<>(pContext.bakeLayer(BeyondModelLayers.ENDERGLOP_LAYER)),0.25F);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(EnderglopEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        ResourceLocation resourcelocation = this.getTextureLocation(livingEntity);
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(resourcelocation);
        } else if (bodyVisible) {
            return RenderType.entityTranslucent(resourcelocation);
        } else {
            return glowing ? RenderType.outline(resourcelocation) : null;
        }
    }

    public ResourceLocation getTextureLocation(EnderglopEntity pEntity){
        return pEntity.getIsArmored() ? ENDERGLOP_ARMORED : ENDERGLOP;
    }

    protected void scale(EnderglopEntity livingEntity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(0.999F, 0.999F, 0.999F);
        poseStack.translate(0.0F, 0.001F, 0.0F);
        float f1 = (float)livingEntity.getSize();
        float f2 = Mth.lerp(partialTickTime, livingEntity.oSquish, livingEntity.squish) / (f1 * 0.5F + 1.0F);

        float f3 = 1.0F / (f2 + 1.0F);
        poseStack.scale(f3 * f1, 1.0F / f3 * f1, f3 * f1);

    }
}
