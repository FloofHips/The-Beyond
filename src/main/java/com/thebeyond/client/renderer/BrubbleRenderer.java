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
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/brubble/brubble.png");

    public BrubbleRenderer(EntityRendererProvider.Context context) {
        super(context,new BrubbleModel<>(context.bakeLayer(BeyondModelLayers.BRUBBLE)),0.5F);
    }

    @Override
    protected float getShadowRadius(BrubbleEntity entity) {
        return entity.hasRocket() ? 0.5f : 0.2f;
    }

    @Override
    public ResourceLocation getTextureLocation(BrubbleEntity entity) {
        return TEXTURE;
    }
}
