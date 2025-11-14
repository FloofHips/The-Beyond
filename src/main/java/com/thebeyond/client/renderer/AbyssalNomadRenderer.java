package com.thebeyond.client.renderer;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.AbyssalNomadModel;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.renderer.renderlayers.AbyssalNomadGlowLayer;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CreeperPowerLayer;
import net.minecraft.resources.ResourceLocation;

public class AbyssalNomadRenderer extends MobRenderer<AbyssalNomadEntity, AbyssalNomadModel<AbyssalNomadEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/abyssal_nomad.png");
    public AbyssalNomadRenderer(EntityRendererProvider.Context pContext) {
        super(pContext,new AbyssalNomadModel<>(pContext.bakeLayer(BeyondModelLayers.ABYSSAL_NOMAD)),0.25F);
        this.addLayer(new AbyssalNomadGlowLayer(this, pContext.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(AbyssalNomadEntity E) {
        return TEXTURE;
    }
}