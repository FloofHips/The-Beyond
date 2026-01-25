package com.thebeyond.client.renderer.renderlayers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.AbyssalNomadModel;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;

import java.awt.*;

public class AbyssalNomadGlowLayer extends RenderLayer<AbyssalNomadEntity, AbyssalNomadModel<AbyssalNomadEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/abyssal_nomad_glow.png");
    private final AbyssalNomadModel<AbyssalNomadEntity> model;

    public AbyssalNomadGlowLayer(RenderLayerParent<AbyssalNomadEntity, AbyssalNomadModel<AbyssalNomadEntity>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new AbyssalNomadModel<>(modelSet.bakeLayer(BeyondModelLayers.ABYSSAL_NOMAD_GLOW));
    }

    protected EntityModel<AbyssalNomadEntity> model() {
        return this.model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbyssalNomadEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!this.model.glow)
            this.model.glow = true;
        this.model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(NeoForgeRenderTypes.getUnlitTranslucent(TEXTURE));

        Color color = new Color(1,(255 - entity.getCorruption())/255f,1,1);

        this.model.renderToBuffer(poseStack, vertexConsumer, 255, OverlayTexture.NO_OVERLAY, color.getRGB());
    }
}