package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.LanternEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class LanternLeviathanModel<T extends LanternEntity> extends EntityModel<LanternEntity> {
    private final ModelPart root;
    private final ModelPart bone;
    private final ModelPart left_fin;
    private final ModelPart lf2;
    private final ModelPart lf3;
    private final ModelPart right_fin;
    private final ModelPart rf2;
    private final ModelPart rf3;
    private final ModelPart tail;
    private final ModelPart t2;
    private final ModelPart t3;

    public LanternLeviathanModel(ModelPart root) {
        this.root = root.getChild("root");
        this.bone = this.root.getChild("bone");
        this.left_fin = this.bone.getChild("left_fin");
        this.lf2 = this.left_fin.getChild("lf2");
        this.lf3 = this.lf2.getChild("lf3");
        this.right_fin = this.bone.getChild("right_fin");
        this.rf2 = this.right_fin.getChild("rf2");
        this.rf3 = this.rf2.getChild("rf3");
        this.tail = this.bone.getChild("tail");
        this.t2 = this.tail.getChild("t2");
        this.t3 = this.t2.getChild("t3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(-15.0769F, -13.9615F, -6.4615F, 32.0F, 27.0F, 80.0F, new CubeDeformation(0.0F))
                .texOffs(138, 52).addBox(-5.0769F, 9.0385F, -6.4615F, 6.0F, 0.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(138, 58).addBox(-5.0769F, 3.0385F, -6.4615F, 6.0F, 0.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(144, 58).addBox(0.9231F, 3.0385F, -6.4615F, 0.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(144, 64).addBox(-5.0769F, 3.0385F, -6.4615F, 0.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(136, 36).addBox(-10.0769F, -0.9615F, -6.4615F, 8.0F, 0.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(136, 44).addBox(-10.0769F, -8.9615F, -6.4615F, 8.0F, 0.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(160, 28).addBox(-10.0769F, -8.9615F, -6.4615F, 0.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(160, 36).addBox(-2.0769F, -8.9615F, -6.4615F, 0.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(136, 36).addBox(3.9231F, 3.0385F, -6.4615F, 8.0F, 0.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(160, 28).addBox(3.9231F, -4.9615F, -6.4615F, 0.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(160, 36).addBox(11.9231F, -4.9615F, -6.4615F, 0.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(136, 44).addBox(3.9231F, -4.9615F, -6.4615F, 8.0F, 0.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.9231F, -13.0385F, -25.5385F));

        PartDefinition left_fin = bone.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(132, 24).mirror().addBox(-8.0F, 0.0F, -4.0F, 16.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-23.0769F, 7.0385F, 15.5385F));

        PartDefinition lf2 = left_fin.addOrReplaceChild("lf2", CubeListBuilder.create().texOffs(132, 12).mirror().addBox(-8.0F, 0.0F, 0.0F, 16.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 8.0F));

        PartDefinition lf3 = lf2.addOrReplaceChild("lf3", CubeListBuilder.create().texOffs(132, 0).mirror().addBox(-8.0F, 0.0F, 0.0F, 16.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition right_fin = bone.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(132, 24).addBox(-8.0F, 0.0F, -4.0F, 16.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(24.9231F, 7.0385F, 15.5385F));

        PartDefinition rf2 = right_fin.addOrReplaceChild("rf2", CubeListBuilder.create().texOffs(132, 12).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 8.0F));

        PartDefinition rf3 = rf2.addOrReplaceChild("rf3", CubeListBuilder.create().texOffs(132, 0).addBox(-8.0F, 0.0F, 0.0F, 16.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition tail = bone.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(116, 107).addBox(-32.0F, 0.0F, 0.0F, 64.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.9231F, 13.0385F, 73.5385F));

        PartDefinition t2 = tail.addOrReplaceChild("t2", CubeListBuilder.create().texOffs(-12, 107).addBox(-32.0F, 0.0F, 0.0F, 64.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition t3 = t2.addOrReplaceChild("t3", CubeListBuilder.create().texOffs(-12, 119).addBox(-32.0F, 0.0F, 0.0F, 64.0F, 0.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 12.0F));

        return LayerDefinition.create(meshdefinition, 256, 256);
    }

    @Override
    public void setupAnim(LanternEntity lantern, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.z = 16;
        this.root.y = 32;
        this.bone.xRot = headPitch * 0.017453292F;
        this.bone.yRot = netHeadYaw * 0.017453292F;
        if (lantern.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7)
            this.bone.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);

        this.bone.y = - 13 - (5F * Mth.sin(0.01F * ageInTicks));

        tail.xRot = -(limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.1F * ageInTicks);
        t2.xRot = (limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.2F * ageInTicks);
        t3.xRot = -(limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.1F * ageInTicks);

        left_fin.xRot = -(limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.1F * ageInTicks);
        lf2.xRot = (limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.2F * ageInTicks);
        lf3.xRot = -(limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.1F * ageInTicks);

        right_fin.xRot = (limbSwingAmount + 0.1f) * 0.2F * Mth.sin(0.1F * ageInTicks);
        rf2.xRot = -(limbSwingAmount + 0.5f) * 0.3F * Mth.sin(0.2F * ageInTicks);
        rf3.xRot = (limbSwingAmount + 0.5f) * 0.45F * Mth.sin(0.1F * ageInTicks);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}