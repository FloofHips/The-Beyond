package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.EnatiousTotemModel;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class EnatiousTotemRenderer extends MobRenderer<EnatiousTotemEntity, EnatiousTotemModel<EnatiousTotemEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enatious_totem/enatious_totem.png");
    private static final ResourceLocation TEXTURE_DOWN = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/enatious_totem/enatious_totem_down.png");
    public EnatiousTotemRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new EnatiousTotemModel<>(pContext.bakeLayer(BeyondModelLayers.ENATIOUS_TOTEM)),0F);
    }

    @Override
    public void render(EnatiousTotemEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if(entity.getSpawnProgress()>2)
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight) ;
    }

    @Override
    public ResourceLocation getTextureLocation(EnatiousTotemEntity enatiousTotemEntity) {
        return enatiousTotemEntity.getCooldown() == enatiousTotemEntity.getMaxCooldown() ? TEXTURE : TEXTURE_DOWN;
    }
}