package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.BrubbleModel;
import com.thebeyond.client.model.LanternLargeModel;
import com.thebeyond.client.model.PoisonSeedModel;
import com.thebeyond.common.entity.BrubbleEntity;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.entity.PoisonSeedEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class BrubbleRenderer extends MobRenderer<BrubbleEntity, BrubbleModel<BrubbleEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/brubble/brubble_ground.png");
    private static final ResourceLocation TEXTURE_FLY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/brubble/brubble_fly.png");

    public BrubbleRenderer(EntityRendererProvider.Context context) {
        super(context,new BrubbleModel<>(context.bakeLayer(BeyondModelLayers.BRUBBLE)),0.5F);
    }

    @Override
    protected @Nullable RenderType getRenderType(BrubbleEntity livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        ResourceLocation resourcelocation = this.getTextureLocation(livingEntity);
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(resourcelocation);
        } else if (bodyVisible) {
            return BeyondRenderTypes.entityCutout(resourcelocation);
        } else {
            return glowing ? RenderType.outline(resourcelocation) : null;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(BrubbleEntity entity) {
        return entity.isFloating() ? TEXTURE_FLY : TEXTURE;
    }
}
