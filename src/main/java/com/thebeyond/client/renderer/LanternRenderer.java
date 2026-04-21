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
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
        // Used by MobRenderer only for secondary effects (leash/outline/shadow) —
        // the body is rendered via the split-pass in render() below. Returns the
        // standard entity_translucent shader so visuals stay identical under Iris.
        return BeyondRenderTypes.entityTranslucentNoCulled(getTextureLocation(livingEntity));
    }

    @Override
    public void render(LanternEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

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

        EntityModel<LanternEntity> model = this.getModel(entity);
        model.prepareMobModel(entity, f5, f4, partialTicks);
        model.setupAnim(entity, f5, f4, f9, f2, f6);

        float distance = Math.clamp(Minecraft.getInstance().cameraEntity.distanceTo(entity), 0, 10);
        int transMax = 10;
        float alpha = Math.max(((transMax - distance) / (float) transMax), entity.level().getRainLevel(partialTicks));

        int finalAlpha = (int) Math.max(255 * alpha, entity.getAlpha());
        // Pack ARGB manually: Color(r=255, g=finalAlpha, b=255, a=finalAlpha).
        // Magenta/pink tint as finalAlpha drops — the ghostly fade aesthetic.
        int color = (finalAlpha << 24) | (0xFF << 16) | (finalAlpha << 8) | 0xFF;

        // Lantern is an unlit ethereal entity — FULL_BRIGHT + standard
        // entity_translucent shader gives identical output in vanilla and Iris.
        int lightLevel = LightTexture.FULL_BRIGHT;

        // Split-pass: body NO_CULL (volumetric translucent — back face then front),
        // fins CULL (zero-thickness quads z-fight without culling). Selection is
        // driven by each model's getRoot()/getMainPart().
        ResourceLocation textureLocation = getTextureLocation(entity);
        RenderType bodyType = BeyondRenderTypes.entityTranslucentNoCulled(textureLocation);
        RenderType finsType = BeyondRenderTypes.entityTranslucentCulled(textureLocation);

        ModelPart root = getModelRoot(model);
        ModelPart mainPart = getModelMainPart(model);

        if (root != null && mainPart != null) {
            renderBodyPass(poseStack, root, mainPart, buffer.getBuffer(bodyType), lightLevel, color);
            renderFinsPass(poseStack, root, mainPart, buffer.getBuffer(finsType), lightLevel, color);
        } else {
            // Fallback if a model is missing getRoot()/getMainPart(): single-pass
            // CULL render — hollow-body look returns, but won't crash.
            model.renderToBuffer(poseStack, buffer.getBuffer(finsType), lightLevel, OverlayTexture.NO_OVERLAY, color);
        }

        // Additive bloom halo for shader-mod setups only. Uses the emissive
        // render type (COLOR_WRITE, CULL) so fin quads don't z-fight and the
        // glow isn't double-applied to coplanar pairs.
        if (ShaderCompatLib.isShaderModLoaded() && finalAlpha > 10) {
            VertexConsumer emissiveConsumer = buffer.getBuffer(BeyondRenderTypes.entityTranslucentEmissiveCulled(textureLocation));
            int bloomAlpha = Math.max((int) (finalAlpha * 0.4f), 1);
            int bloomColor = (bloomAlpha << 24) | (0xFF << 16) | (bloomAlpha << 8) | 0xFF;
            model.renderToBuffer(
                    poseStack,
                    emissiveConsumer,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    bloomColor
            );
        }

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

    // Split-pass helpers — dispatch by instanceof since the Lantern models
    // don't share a base class. Null return triggers single-pass fallback.

    @Nullable
    private static ModelPart getModelRoot(EntityModel<LanternEntity> model) {
        if (model instanceof LanternSmallModel<?> m) return m.getRoot();
        if (model instanceof LanternMediumModel<?> m) return m.getRoot();
        if (model instanceof LanternLargeModel<?> m) return m.getRoot();
        if (model instanceof LanternLeviathanModel<?> m) return m.getRoot();
        return null;
    }

    @Nullable
    private static ModelPart getModelMainPart(EntityModel<LanternEntity> model) {
        if (model instanceof LanternSmallModel<?> m) return m.getMainPart();
        if (model instanceof LanternMediumModel<?> m) return m.getMainPart();
        if (model instanceof LanternLargeModel<?> m) return m.getMainPart();
        if (model instanceof LanternLeviathanModel<?> m) return m.getMainPart();
        return null;
    }

    /** NO_CULL body pass: hides all descendants of mainPart so only its own
     *  cubes render, then restores visibility. */
    private static void renderBodyPass(PoseStack poseStack, ModelPart root, ModelPart mainPart,
                                        VertexConsumer buffer, int lightLevel, int color) {
        // Hide everything below mainPart (all fins are descendants of it).
        mainPart.getAllParts().forEach(p -> { if (p != mainPart) p.visible = false; });
        try {
            root.render(poseStack, buffer, lightLevel, OverlayTexture.NO_OVERLAY, color);
        } finally {
            mainPart.getAllParts().forEach(p -> { if (p != mainPart) p.visible = true; });
        }
    }

    /** CULL fins pass: skipDraw on mainPart skips its own cube but still
     *  recurses into children, preserving transform and animation. */
    private static void renderFinsPass(PoseStack poseStack, ModelPart root, ModelPart mainPart,
                                        VertexConsumer buffer, int lightLevel, int color) {
        boolean prevSkip = mainPart.skipDraw;
        mainPart.skipDraw = true;
        try {
            root.render(poseStack, buffer, lightLevel, OverlayTexture.NO_OVERLAY, color);
        } finally {
            mainPart.skipDraw = prevSkip;
        }
    }
}