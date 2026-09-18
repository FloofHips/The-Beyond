package com.thebeyond.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.model.BeyondModelLayers;
import com.thebeyond.client.model.LanternMediumModel;
import com.thebeyond.client.model.SiblingModel;
import com.thebeyond.common.entity.LanternEntity;
import com.thebeyond.common.entity.SiblingEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SiblingRenderer extends MobRenderer<SiblingEntity, EntityModel<SiblingEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/entity/sibling/sibling.png");
    protected EntityModel<SiblingEntity> siblingModel;
    private final EntityModel<SiblingEntity> playerModel;

    public SiblingRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), .5f);
        this.playerModel = this.model;
        this.siblingModel = new SiblingModel<>(context.bakeLayer(BeyondModelLayers.SIBLING));
    }

    @Override
    public ResourceLocation getTextureLocation(SiblingEntity siblingEntity) {
        return TEXTURE;
    }

    @Override
    public void render(SiblingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (this.playerModel instanceof HumanoidModel<SiblingEntity> pModel) pModel.crouching = entity.isCrouching();

        this.model = entity.getBirth() ? this.siblingModel : this.playerModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

}
