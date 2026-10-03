package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.LanternEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class LanternLargeModel<T extends LanternEntity> extends EntityModel<LanternEntity> {

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart right_fin;
    private final ModelPart right_2;
    private final ModelPart right_3;
    private final ModelPart left_fin;
    private final ModelPart left_2;
    private final ModelPart left_3;
    private final ModelPart top_fin;
    private final ModelPart tf_2;
    private final ModelPart tf_3;
    private final ModelPart lower_fin;
    private final ModelPart lf_2;
    private final ModelPart lf_3;

    public LanternLargeModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.right_fin = this.body.getChild("right_fin");
        this.right_2 = this.right_fin.getChild("right_2");
        this.right_3 = this.right_2.getChild("right_3");
        this.left_fin = this.body.getChild("left_fin");
        this.left_2 = this.left_fin.getChild("left_2");
        this.left_3 = this.left_2.getChild("left_3");
        this.top_fin = this.body.getChild("top_fin");
        this.tf_2 = this.top_fin.getChild("tf_2");
        this.tf_3 = this.tf_2.getChild("tf_3");
        this.lower_fin = this.body.getChild("lower_fin");
        this.lf_2 = this.lower_fin.getChild("lf_2");
        this.lf_3 = this.lf_2.getChild("lf_3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -14.0F, -8.0F, 8.0F, 24.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -10.0F, 0.0F));

        PartDefinition right_fin = body.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(-7, 53).mirror().addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-6.0F, 6.0F, -1.0F));

        PartDefinition right_2 = right_fin.addOrReplaceChild("right_2", CubeListBuilder.create().texOffs(1, 53).mirror().addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition right_3 = right_2.addOrReplaceChild("right_3", CubeListBuilder.create().texOffs(9, 53).mirror().addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition left_fin = body.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(-7, 53).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 6.0F, -1.0F));

        PartDefinition left_2 = left_fin.addOrReplaceChild("left_2", CubeListBuilder.create().texOffs(1, 53).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition left_3 = left_2.addOrReplaceChild("left_3", CubeListBuilder.create().texOffs(9, 53).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition top_fin = body.addOrReplaceChild("top_fin", CubeListBuilder.create().texOffs(0, 31).addBox(0.0F, -7.0F, 0.0F, 0.0F, 13.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -17.0F, -1.0F));

        PartDefinition tf_2 = top_fin.addOrReplaceChild("tf_2", CubeListBuilder.create().texOffs(32, -8).addBox(0.0F, -7.0F, 0.0F, 0.0F, 13.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 9.0F));

        PartDefinition tf_3 = tf_2.addOrReplaceChild("tf_3", CubeListBuilder.create().texOffs(48, -8).addBox(0.0F, -7.0F, 0.0F, 0.0F, 13.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));

        PartDefinition lower_fin = body.addOrReplaceChild("lower_fin", CubeListBuilder.create().texOffs(18, 31).addBox(0.0F, -7.0F, 0.0F, 0.0F, 13.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 14.0F, -1.0F));

        PartDefinition lf_2 = lower_fin.addOrReplaceChild("lf_2", CubeListBuilder.create().texOffs(36, 32).addBox(0.0F, -7.0F, 0.0F, 0.0F, 13.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 9.0F));

        PartDefinition lf_3 = lf_2.addOrReplaceChild("lf_3", CubeListBuilder.create().texOffs(48, 8).addBox(0.0F, -7.0F, 0.0F, 0.0F, 13.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(LanternEntity lantern, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.body.xRot = headPitch * 0.017453292F;
        this.body.yRot = netHeadYaw * 0.017453292F;
        if (lantern.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7)
            this.body.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);

        this.root.y = 0.5F * Mth.sin(ageInTicks * 0.18f) + 24;

        this.root.xRot = 0.5f * 0.1F * Mth.cos(ageInTicks * 0.18f);
        this.root.yRot = 0.5f * 0.3F * Mth.cos((ageInTicks - 0.25f) * 0.09f);
        this.root.zRot = 0.5f * 0.3F * Mth.sin(ageInTicks * 0.09f);

        top_fin.zRot = (float) ((Math.sin(ageInTicks * 0.09f) * 0.15 * 0.5));
        top_fin.yRot = (float) ((Math.sin((ageInTicks - 5) * 0.09f) * 0.2));
        tf_2.yRot = (float) ((Math.sin((ageInTicks - 10f) * 0.09f) * 0.4));
        tf_3.yRot = (float) ((Math.sin((ageInTicks - 20f) * 0.09f) * 0.7));

        lower_fin.zRot = (float) ((Math.sin((ageInTicks) * 0.09f) * 0.15 * -0.5));
        lower_fin.yRot = (float) ((Math.sin((ageInTicks - 10) * 0.09f) * -0.025));
        lf_2.yRot = tf_2.yRot;
        lf_3.yRot = tf_3.yRot;

        left_fin.xRot = (float) ((Math.sin((ageInTicks) * 0.18f) * 0.1));
        left_2.xRot = (float) ((Math.sin((ageInTicks - 5f) * 0.18f) * 0.15 * 0.5));
        left_3.xRot = (float) ((Math.sin((ageInTicks - 10f) * 0.18f) * 0.1));

        right_fin.xRot = left_fin.xRot;
        right_2.xRot = left_2.xRot;
        right_3.xRot = left_3.xRot;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}