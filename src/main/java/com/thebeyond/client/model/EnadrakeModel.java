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
    private final ModelPart head;
    private final ModelPart body;

    public EnadrakeModel(ModelPart root) {
        this.root = root.getChild("root");
        this.right_arm = this.root.getChild("right_arm");
        this.left_arm = this.root.getChild("left_arm");
        this.head = this.root.getChild("head");
        this.body = this.head.getChild("body");
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

        PartDefinition body = head.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 13).addBox(-2.0F, -1.3333F, -1.5F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(14, 25).addBox(1.0F, 4.6667F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
                .texOffs(18, 25).addBox(-2.0F, 4.6667F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.3333F, 0.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(EnadrakeEntity enadrakeEntity, float v, float v1, float v2, float v3, float v4) {
        this.left_arm.xRot = Mth.cos(v) * 1.4F * v1;
        this.right_arm.xRot = Mth.cos(v + 0.5f) * 1.4F * v1;

        this.body.xRot = Mth.cos(v + Mth.PI) * 1.4F * v1;
        this.head.xRot = v4 * 0.017453292F;
        this.head.yRot = v3 * 0.017453292F;

        this.body.y = Mth.cos(v / 2f) * 1.4F * v1;
        this.head.y = - 8 + Mth.cos(v + Mth.PI) * 2F * v1;

    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}
