package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.LanternLargeModel;
import com.thebeyond.client.model.LanternMediumModel;
import com.thebeyond.client.model.LanternSmallModel;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class LanternRenderer extends MobRenderer<LanternEntity, LanternLargeModel<LanternEntity>> {
    private static final ResourceLocation TEXTURE_LARGE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/large_lantern.png");
    private static final ResourceLocation TEXTURE_MEDIUM = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/medium_lantern.png");
    private static final ResourceLocation TEXTURE_SMALL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/small_lantern.png");
    protected LanternSmallModel small;
    protected LanternMediumModel medium;
    public LanternRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new LanternLargeModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_LARGE)),0F);
        this.medium = new LanternMediumModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_MEDIUM));
        this.small = new LanternSmallModel(pContext.bakeLayer(BeyondModelLayers.LANTERN_SMALL));
        //this.large = new LanternMediumModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_LARGE));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(LanternEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return BeyondRenderTypes.getEntityDepth(getTextureLocation(livingEntity));
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

        this.getModel(entity).prepareMobModel(entity, f5, f4, partialTicks);
        this.getModel(entity).setupAnim(entity, f5, f4, f9, f2, f6);

        //VertexConsumer vertexConsumer = buffer.getBuffer(BeyondRenderTypes.unlitTranslucent(getTextureLocation(entity)));
        float distance = Math.clamp(Minecraft.getInstance().cameraEntity.distanceTo(entity), 0, 10);
        VertexConsumer vertexConsumer = buffer.getBuffer(BeyondRenderTypes.unlitTranslucent(getTextureLocation(entity)));
        //float distance = 0;

        int transMax = 10;

        int color = new Color(255,255,255, (int) (255*(((transMax - distance)/(float) transMax)))).getRGB();
        this.getModel(entity).renderToBuffer(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color
        );

        this.getModel(entity).prepareMobModel(entity, f5, f4+0.1f, partialTicks);
        this.getModel(entity).setupAnim(entity, f5, f4+0.1f, f9, f2, f6);

        vertexConsumer = buffer.getBuffer(BeyondRenderTypes.getEntityDepth(getTextureLocation(entity)));

        int crumbFarthest = 15;
        int crumbHalf = 10;

        distance = Math.clamp(Minecraft.getInstance().cameraEntity.distanceTo(entity), 0, crumbFarthest);


        if (distance < crumbHalf) {
            distance = -1 + (crumbHalf + distance)/(float)crumbHalf;
            distance*=distance;
        }
        if (distance >= crumbHalf) {
            distance = (crumbFarthest - distance)/(float)(crumbFarthest-crumbHalf);
        }
        color = new Color(255,255,255, (int) (255*(distance))).getRGB();
        poseStack.pushPose();
        poseStack.scale(0.95f, 0.95f, 0.95f);
        poseStack.translate(0,0.07,0);
        this.getModel(entity).renderToBuffer(
                poseStack,
                vertexConsumer,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                color
        );
        poseStack.popPose();
        poseStack.popPose();
    }
    @Override
    public ResourceLocation getTextureLocation(LanternEntity lantern) {
        int size = lantern.getSize();
        if (size == 0) return TEXTURE_SMALL;
        if (size == 1) return TEXTURE_MEDIUM;
        return TEXTURE_LARGE;
    }

    public EntityModel<LanternEntity> getModel(LanternEntity lantern) {
        int size = lantern.getSize();
        if (size == 0) return small;
        if (size == 1) return medium;
        return super.getModel();
    }
}