package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.EnatiousTotemModel;
import com.thebeyond.client.model.LargeLanternModel;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class LanternRenderer extends MobRenderer<LanternEntity, LargeLanternModel<LanternEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/large_lantern.png");
    public LanternRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new LargeLanternModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_LARGE)),0F);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(LanternEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return BeyondRenderTypes.getEntityDepth(TEXTURE);
    }

    @Override
    public void render(LanternEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        //VertexConsumer vertexConsumer = buffer.getBuffer(BeyondRenderTypes.getEntityDepth(TEXTURE));


        float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float f1 = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float f2 = f1 - f;
        float f7;
        float f6 = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (isEntityUpsideDown(entity)) {
            f6 *= -1.0F;
            f2 *= -1.0F;
        }

        f2 = Mth.wrapDegrees(f2);
        f7 = entity.getScale();
        float f9 = this.getBob(entity, partialTicks);
        this.setupRotations(entity, poseStack, f9, f, partialTicks, f7);
        float f4 = 0.0F;
        float f5 = 0.0F;
        if (entity.isAlive()) {
            f4 = entity.walkAnimation.speed(partialTicks);
            f5 = entity.walkAnimation.position(partialTicks);
            if (entity.isBaby()) {
                f5 *= 3.0F;
            }

            if (f4 > 1.0F) {
                f4 = 1.0F;
            }
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);

        this.model.prepareMobModel(entity, f5, f4, partialTicks);
        this.model.setupAnim(entity, f5, f4, f9, f2, f6);

        VertexConsumer vertexConsumer = buffer.getBuffer(BeyondRenderTypes.unlitTranslucent(TEXTURE));
        int color = new Color(255,255,255, (int) (255*(Math.sin(entity.tickCount * 0.1F) * 0.5F + 0.5F))).getRGB();
        this.getModel().renderToBuffer(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color
        );

        this.model.prepareMobModel(entity, f5, -f4, partialTicks);
        this.model.setupAnim(entity, f5, -f4, f9, f2, f6);

        //poseStack.translate(0.1f * (entity.level().random.nextFloat() - 0.5f), 0.1f * (entity.level().random.nextFloat() - 0.5f), 0.1f * (entity.level().random.nextFloat() - 0.5f));
        vertexConsumer = buffer.getBuffer(BeyondRenderTypes.getEntityDepth(TEXTURE));
        color = new Color(255,255,255, (int) (255*((Math.sin(entity.tickCount + 6) * 0.1F) * 0.5F + 0.5F))).getRGB();
        this.getModel().renderToBuffer(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color
        );

        poseStack.popPose();
    }
    @Override
    public ResourceLocation getTextureLocation(LanternEntity lantern) {
        return TEXTURE;
    }
}