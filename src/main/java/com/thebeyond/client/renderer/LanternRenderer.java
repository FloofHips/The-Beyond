package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.*;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import org.jetbrains.annotations.Nullable;

// java.awt.Color removed — unavailable on headless server JVMs, replaced with bit math

public class LanternRenderer extends MobRenderer<LanternEntity, LanternLargeModel<LanternEntity>> {
    private static final ResourceLocation TEXTURE_LEVIATHAN = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/leviathan_lantern.png");
    private static final ResourceLocation TEXTURE_LARGE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/large_lantern.png");
    private static final ResourceLocation TEXTURE_MEDIUM = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/medium_lantern.png");
    private static final ResourceLocation TEXTURE_SMALL = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/lantern/small_lantern.png");
    protected LanternSmallModel small;
    protected LanternMediumModel medium;
    protected LanternLeviathanModel leviathan;
    public LanternRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new LanternLargeModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_LARGE)),0F);
        this.medium = new LanternMediumModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_MEDIUM));
        this.small = new LanternSmallModel(pContext.bakeLayer(BeyondModelLayers.LANTERN_SMALL));
        this.leviathan = new LanternLeviathanModel<>(pContext.bakeLayer(BeyondModelLayers.LANTERN_LEVIATHAN));
    }

    @Nullable
    @Override
    protected RenderType getRenderType(LanternEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // Iris/Oculus replace the G-Buffer pipeline entirely — BeyondShaders' custom
        // depth overlay shader is silently ignored, making the entity invisible.
        // Fall back to a CPU-processed "depth" texture + vanilla-compatible render type.
        // Uses entityTranslucentCulled (CULL enabled) instead of vanilla entityTranslucent
        // (NO_CULL) because Lantern fins are zero-thickness cubes — without culling, the
        // two coplanar quads generated for each fin z-fight ("scribbled" artifact).
        if (ShaderCompatLib.isShaderModLoaded()) {
            ResourceLocation depthTexture = LanternDepthTextureManager.getOrCreate(getTextureLocation(livingEntity));
            return BeyondRenderTypes.entityTranslucentCulled(depthTexture);
        }
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

        float distance = Math.clamp(Minecraft.getInstance().cameraEntity.distanceTo(entity), 0, 10);

        // Shader mod fallback: BeyondRenderTypes.unlitTranslucent() uses a NeoForge unlit
        // shader that Iris/Oculus strip from the G-Buffer pipeline. Use a CPU-processed
        // depth texture + entityTranslucentCulled (CULL enabled) to replicate the custom
        // shader's unlit white-to-gray depth mapping. CULL is critical — Lantern fins are
        // zero-thickness cubes whose two coplanar quads z-fight without back-face culling.
        VertexConsumer vertexConsumer;
        boolean isShaderFallback = ShaderCompatLib.isShaderModLoaded();
        if (isShaderFallback) {
            ResourceLocation depthTexture = LanternDepthTextureManager.getOrCreate(getTextureLocation(entity));
            vertexConsumer = buffer.getBuffer(BeyondRenderTypes.entityTranslucentCulled(depthTexture));
        } else if (entity.getSize() == 3) {
            vertexConsumer = buffer.getBuffer(NeoForgeRenderTypes.getUnlitTranslucent(getTextureLocation(entity)));
        } else {
            vertexConsumer = buffer.getBuffer(BeyondRenderTypes.unlitTranslucent(getTextureLocation(entity)));
        }
        //float distance = 0;

        int transMax = 10;
        float alpha = Math.max(((((transMax - distance)/(float) transMax))), entity.level().getRainLevel(partialTicks));

        // Shader fallback uses standard alpha blending (entityTranslucent) instead of the
        // original multiply blend (CRUMBLING_TRANSPARENCY) used by the ENTITY_DEPTH render
        // type. Alpha blending makes low-alpha pixels invisible faster, causing the lantern
        // to abruptly disappear at distance. Square root curve compensates, keeping the
        // lantern visible longer — closer to the original multiply-blend fade aesthetic.
        if (isShaderFallback) {
            alpha = (float) Math.sqrt(alpha);
        }

        int finalAlpha = (int) Math.max(255 * alpha, entity.getAlpha());
        // Pack ARGB manually: Color(r=255, g=finalAlpha, b=255, a=finalAlpha)
        int color = (finalAlpha << 24) | (0xFF << 16) | (finalAlpha << 8) | 0xFF;

        // Shader fallback: use full brightness to replicate the unlit/emissive appearance
        // of the original ENTITY_DEPTH and unlitTranslucent render types. Without this,
        // ambient lighting darkens the lantern, breaking the ethereal glow aesthetic.
        int lightLevel = isShaderFallback ? LightTexture.FULL_BRIGHT : packedLight;

        this.getModel(entity).renderToBuffer(
                poseStack,
                vertexConsumer,
                lightLevel,
                OverlayTexture.NO_OVERLAY,
                color
        );

        // Emissive bloom overlay (shader mod only): entityTranslucentEmissive triggers Iris
        // shader pack bloom/glow effects. The primary pass above already established depth.
        // Rendered at reduced alpha so bloom is a subtle luminous halo, not a solid duplicate.
        // Uses CULL-enabled emissive variant — same reason as the base pass: zero-thickness
        // fin quads generate two coplanar faces that z-fight without back-face culling.
        if (isShaderFallback && finalAlpha > 10) {
            ResourceLocation depthTexture = LanternDepthTextureManager.getOrCreate(getTextureLocation(entity));
            VertexConsumer emissiveConsumer = buffer.getBuffer(BeyondRenderTypes.entityTranslucentEmissiveCulled(depthTexture));
            int bloomAlpha = Math.max((int) (finalAlpha * 0.4f), 1);
            int bloomColor = (bloomAlpha << 24) | (0xFF << 16) | (bloomAlpha << 8) | 0xFF;
            this.getModel(entity).renderToBuffer(
                    poseStack,
                    emissiveConsumer,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    bloomColor
            );
        }

        //this.getModel(entity).prepareMobModel(entity, f5, f4, partialTicks);
        //this.getModel(entity).setupAnim(entity, f5, f4, f9, f2, f6);
//
        //vertexConsumer = buffer.getBuffer(BeyondRenderTypes.getEntityDepth(getTextureLocation(entity)));
//
        //int crumbFarthest = 15;
        //int crumbHalf = 10;
//
        //distance = Math.clamp(Minecraft.getInstance().cameraEntity.distanceTo(entity), 0, crumbFarthest);
//
//
        //if (distance < crumbHalf) {
        //    distance = -1 + (crumbHalf + distance)/(float)crumbHalf;
        //    distance*=distance;
        //}
        //if (distance >= crumbHalf) {
        //    distance = (crumbFarthest - distance)/(float)(crumbFarthest-crumbHalf);
        //}
        //color = new Color(255,255,255, (int) (255*(distance))).getRGB();
        //poseStack.pushPose();
        //poseStack.scale(1.05f, 1.05f, 1.05f);
        //poseStack.translate(0,0.07,0);
        //this.getModel(entity).renderToBuffer(
        //        poseStack,
        //        vertexConsumer,
        //        packedLight,
        //        OverlayTexture.NO_OVERLAY,
        //        color
        //);
        //poseStack.popPose();
        poseStack.popPose();
    }
    @Override
    public ResourceLocation getTextureLocation(LanternEntity lantern) {
        int size = lantern.getSize();
        if (size == 0) return TEXTURE_SMALL;
        if (size == 1) return TEXTURE_MEDIUM;
        if (size == 3) return TEXTURE_LEVIATHAN;
        return TEXTURE_LARGE;
    }

    public EntityModel<LanternEntity> getModel(LanternEntity lantern) {
        int size = lantern.getSize();
        if (size == 0) return small;
        if (size == 1) return medium;
        if (size == 3) return leviathan;
        return super.getModel();
    }
}