package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.LanternEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class LanternMediumModel<T extends LanternEntity> extends EntityModel<LanternEntity> {

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart top_fin;
    private final ModelPart tf_2;
    private final ModelPart tf_3;
    private final ModelPart bot_fin;
    private final ModelPart bf_2;
    private final ModelPart bf_3;
    private final ModelPart right_fin;
    private final ModelPart right_2;
    private final ModelPart right_3;
    private final ModelPart left_fin;
    private final ModelPart left_2;
    private final ModelPart left_3;

    public LanternMediumModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.left_fin = this.body.getChild("left_fin");
        this.left_2 = this.left_fin.getChild("left_2");
        this.left_3 = this.left_2.getChild("left_3");
        this.right_fin = this.body.getChild("right_fin");
        this.right_2 = this.right_fin.getChild("right_2");
        this.right_3 = this.right_2.getChild("right_3");
        this.top_fin = this.body.getChild("top_fin");
        this.tf_2 = this.top_fin.getChild("tf_2");
        this.tf_3 = this.tf_2.getChild("tf_3");
        this.bot_fin = this.body.getChild("bot_fin");
        this.bf_2 = this.bot_fin.getChild("bf_2");
        this.bf_3 = this.bf_2.getChild("bf_3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 2).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

        PartDefinition left_fin = body.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(48, 13).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 2.0F, 0.0F));

        PartDefinition left_2 = left_fin.addOrReplaceChild("left_2", CubeListBuilder.create().texOffs(49, 7).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));

        PartDefinition left_3 = left_2.addOrReplaceChild("left_3", CubeListBuilder.create().texOffs(49, 0).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition right_fin = body.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(48, 13).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, 2.0F, 0.0F));

        PartDefinition right_2 = right_fin.addOrReplaceChild("right_2", CubeListBuilder.create().texOffs(49, 7).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));

        PartDefinition right_3 = right_2.addOrReplaceChild("right_3", CubeListBuilder.create().texOffs(49, 0).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition top_fin = body.addOrReplaceChild("top_fin", CubeListBuilder.create().texOffs(34, 10).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 14.0F));

        PartDefinition tf_2 = top_fin.addOrReplaceChild("tf_2", CubeListBuilder.create().texOffs(35, 5).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 6.0F));

        PartDefinition tf_3 = tf_2.addOrReplaceChild("tf_3", CubeListBuilder.create().texOffs(36, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition bot_fin = body.addOrReplaceChild("bot_fin", CubeListBuilder.create().texOffs(34, 10).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.0F, 14.0F));

        PartDefinition bf_2 = bot_fin.addOrReplaceChild("bf_2", CubeListBuilder.create().texOffs(35, 5).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 6.0F));

        PartDefinition bf_3 = bf_2.addOrReplaceChild("bf_3", CubeListBuilder.create().texOffs(36, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        return LayerDefinition.create(meshdefinition, 64, 32);    }

    @Override
    public void setupAnim(LanternEntity lantern, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.z = -3;
        this.body.xRot = headPitch * 0.017453292F;
        this.body.yRot = netHeadYaw * 0.017453292F;
        if (lantern.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7)
            this.body.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);

        this.body.y = - 4 - (5F * Mth.sin(0.05F * ageInTicks));

        top_fin.xRot = -(limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.6F * ageInTicks);
        tf_2.xRot = (limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.9F * ageInTicks);
        tf_3.xRot = -(limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.2F * ageInTicks);

        bot_fin.xRot = (limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.7F * ageInTicks);
        bf_2.xRot = -(limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.8F * ageInTicks);
        bf_3.xRot = (limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.3F * ageInTicks);

        left_fin.xRot = -(limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.6F * ageInTicks);
        left_2.xRot = (limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.9F * ageInTicks);
        left_3.xRot = -(limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.2F * ageInTicks);

        right_fin.xRot = (limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.6F * ageInTicks);
        right_2.xRot = -(limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.9F * ageInTicks);
        right_3.xRot = (limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.2F * ageInTicks);

        left_fin.zRot = (limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.6F * ageInTicks);
        right_fin.zRot = -(limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.6F * ageInTicks);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}