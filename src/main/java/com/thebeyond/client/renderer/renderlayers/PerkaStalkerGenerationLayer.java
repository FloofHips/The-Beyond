package com.thebeyond.client.renderer.renderlayers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.compat.ShaderCompatLib;
import com.thebeyond.client.model.AbyssalNomadModel;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.PerkaStalkerModel;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.PerkaStalkerEntity;
import com.thebeyond.util.RenderUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.awt.*;

import static com.thebeyond.common.entity.PerkaStalkerEntity.MAX_GENERATION;

public class PerkaStalkerGenerationLayer extends RenderLayer<PerkaStalkerEntity, PerkaStalkerModel<PerkaStalkerEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/entity/perka_stalker/perka_stalker_tint.png");
    private final PerkaStalkerModel<PerkaStalkerEntity> model;

    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);

    public PerkaStalkerGenerationLayer(RenderLayerParent<PerkaStalkerEntity, PerkaStalkerModel<PerkaStalkerEntity>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new PerkaStalkerModel<>(modelSet.bakeLayer(BeyondModelLayers.PERKA_STALKER));
    }

    protected EntityModel<PerkaStalkerEntity> model() {
        return this.model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, PerkaStalkerEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean flag = entity.hurtTime > 0;

        //int i = FastColor.ARGB32.lerp(20f/Mth.clamp((entity.getGeneration()+1), 0, 20), -4755063, -927849);
        int i = RenderUtils.lerpColor((float) Mth.clamp((entity.getGeneration()), 0, MAX_GENERATION) / MAX_GENERATION, -4755063, -927849);
        this.model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.model.renderToBuffer(poseStack, bufferSource.getBuffer(RENDER_TYPE), packedLight, OverlayTexture.pack(0.0F, flag), i);
    }
}
