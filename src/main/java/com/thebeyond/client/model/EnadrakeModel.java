package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.EnadrakeEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class EnadrakeModel <T extends EnadrakeEntity> extends EntityModel<EnadrakeEntity> {
    private final ModelPart root;
    private final ModelPart right_arm;
    private final ModelPart left_arm;
    public final ModelPart head;
    private final ModelPart body;
    private final ModelPart left_leg;
    private final ModelPart right_leg;

    public EnadrakeModel(ModelPart root) {
        this.root = root.getChild("root");
        this.right_arm = this.root.getChild("right_arm");
        this.left_arm = this.root.getChild("left_arm");
        this.head = this.root.getChild("head");
        this.body = this.root.getChild("body");
        this.left_leg = this.body.getChild("left_leg");
        this.right_leg = this.body.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(9, 22).addBox(0.0F, 0.0F, 0.0F, 1.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 22).addBox(-1.0F, 7.0F, -1.0F, 3.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, -7.0F, -1.0F));

        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(11, 22).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 7.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(0, 25).addBox(-2.0F, 7.0F, -1.0F, 3.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, -7.0F, -1.0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -3.5F, 8.0F, 6.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, -0.5F));

        PartDefinition cube_r1 = head.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(14, 19).addBox(-4.5F, -3.0F, 0.0F, 9.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -8.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

        PartDefinition cube_r2 = head.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(14, 13).addBox(-4.5F, -3.0F, 0.0F, 9.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -8.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 13).addBox(-2.0F, -1.3333F, -1.5F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -7.6667F, -0.5F));

        PartDefinition left_leg = body.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(14, 25).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, 4.6667F, 0.0F));

        PartDefinition right_leg = body.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(18, 25).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 4.6667F, 0.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(EnadrakeEntity enadrakeEntity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.body.xRot = 0;
        this.body.yRot = 0;
        this.body.y = -8;

        this.head.y = -8;
        this.head.xRot = headPitch * 0.017453292F;
        this.head.yRot = netHeadYaw * 0.017453292F;

        this.left_arm.xRot = 0;
        this.right_arm.xRot = 0;
        this.left_arm.zRot = 0;
        this.right_arm.zRot = 0;
        this.left_arm.yRot = 0;
        this.right_arm.yRot = 0;

        this.left_leg.xRot = 0;
        this.right_leg.xRot = 0;

        if (enadrakeEntity.getMainHandItem().isEmpty()) {
            this.left_arm.xRot += Mth.cos(limbSwing) * 1.4F * limbSwingAmount;
            this.right_arm.xRot += Mth.cos(limbSwing + 0.5f) * 1.4F * limbSwingAmount;
            this.body.xRot += Mth.cos(limbSwing + Mth.PI) * 1.4F * limbSwingAmount;
            this.body.y += Mth.cos(limbSwing / 2f) * 1.4F * limbSwingAmount + Mth.cos(limbSwing + Mth.PI) * 4F * limbSwingAmount;
            this.head.y += Mth.cos(limbSwing + Mth.PI) * 4F * limbSwingAmount;

        } else {
            this.left_arm.xRot += Mth.cos(limbSwing) * 0.1F * limbSwingAmount;
            this.right_arm.xRot += Mth.cos(limbSwing) * 0.1F * limbSwingAmount;
            this.left_arm.zRot += Mth.PI + 0.01f;
            this.right_arm.zRot += -Mth.PI - 0.01f;

            this.left_arm.yRot += Mth.HALF_PI;
            this.right_arm.yRot += -Mth.HALF_PI;

            this.body.y = -5.8f;
            this.body.yRot = Mth.cos(limbSwing * 2.2f) * 1.5F * limbSwingAmount;
            this.head.y = -5.8f + Mth.cos(limbSwing * 2) * 2F * limbSwingAmount;

            this.left_leg.xRot += Mth.cos(limbSwing) * 1.4F * limbSwingAmount;
            this.right_leg.xRot -= Mth.cos(limbSwing + 0.5f) * 1.4F * limbSwingAmount;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}
