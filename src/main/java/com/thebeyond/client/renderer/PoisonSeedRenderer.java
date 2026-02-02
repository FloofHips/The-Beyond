package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.KnockBackSeedModel;
import com.thebeyond.client.model.PoisonSeedModel;
import com.thebeyond.common.entity.KnockbackSeedEntity;
import com.thebeyond.common.entity.PoisonSeedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PoisonSeedRenderer extends EntityRenderer<PoisonSeedEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enatious_totem/poison_seed.png");
    private final PoisonSeedModel model;

    public PoisonSeedRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.model = new PoisonSeedModel(context.bakeLayer(BeyondModelLayers.POISON_SEED));
    }

    @Override
    public void render(PoisonSeedEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        float f = (float)entity.tickCount + partialTicks;

        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.setupAnim(entity, 0.0F, 0.0F, f, 0.0F, 0.0F);
        this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public ResourceLocation getTextureLocation(PoisonSeedEntity poisonSeedEntity) {
        return TEXTURE;
    }
}