package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.KnockBackSeedModel;
import com.thebeyond.common.entity.KnockbackSeedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class KnockBackSeedRenderer extends EntityRenderer<KnockbackSeedEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/knockback_seed.png");
    private final KnockBackSeedModel model;

    public KnockBackSeedRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.model = new KnockBackSeedModel<>(context.bakeLayer(BeyondModelLayers.KNOCKBACK_SEED));
    }

    @Override
    public void render(KnockbackSeedEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        float f = (float)entity.tickCount + partialTicks;
        int i = entity.getFuse();
        if ((float)i - partialTicks + 1.0F < 10.0F) {
            float s = 1.0F - ((float)i - partialTicks + 1.0F) / 10.0F;
            s = Mth.clamp(s, 0.0F, 1.0F);
            s *= s;
            s *= s;
            float f1 = 1.0F + s * 0.3F;
            poseStack.scale(f1, f1, f1);
        }

        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.setupAnim(entity, 0.0F, 0.0F, f, 0.0F, 0.0F);
        this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, (i / 5 % 2 == 0 && i != 40) ? OverlayTexture.pack(OverlayTexture.u(1.0F), 10) : OverlayTexture.NO_OVERLAY);
    }

    @Override
    public ResourceLocation getTextureLocation(KnockbackSeedEntity knockbackSeedEntity) {
        return TEXTURE;
    }
}
