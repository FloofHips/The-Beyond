package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.BrubbleEntity;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class BrubbleModel <T extends BrubbleEntity> extends EntityModel<BrubbleEntity> {
    private final ModelPart all;
    private final ModelPart perching;
    private final ModelPart head_perching;
    private final ModelPart rocket;
    private final ModelPart standing;
    private final ModelPart head_standing;
    private final ModelPart legs_sitting;
    private final ModelPart right_arm_standing;
    private final ModelPart left_arm_standing;
    private final ModelPart legs_standing;
    private final ModelPart left_leg_standing;
    private final ModelPart right_leg_standing;

    public BrubbleModel(ModelPart root) {
        this.all = root.getChild("all");
        this.perching = this.all.getChild("perching");
        this.head_perching = this.perching.getChild("head_perching");
        this.rocket = this.all.getChild("rocket");
        this.standing = this.all.getChild("standing");
        this.head_standing = this.standing.getChild("head_standing");
        this.legs_sitting = this.standing.getChild("legs_sitting");
        this.right_arm_standing = this.standing.getChild("right_arm_standing");
        this.left_arm_standing = this.standing.getChild("left_arm_standing");
        this.legs_standing = this.standing.getChild("legs_standing");
        this.left_leg_standing = this.legs_standing.getChild("left_leg_standing");
        this.right_leg_standing = this.legs_standing.getChild("right_leg_standing");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 18.0F, 0.0F));

        PartDefinition perching = all.addOrReplaceChild("perching", CubeListBuilder.create().texOffs(48, 10).addBox(-2.0F, -11.0F, 0.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(51, 0).addBox(1.5F, -9.0F, -2.0F, 1.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(51, 0).addBox(-3.5F, -9.0F, -2.0F, 1.0F, 3.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(53, 0).mirror().addBox(-4.0F, -8.0F, 0.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(53, 0).addBox(1.0F, -8.0F, 0.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(-2, 0).addBox(-3.5F, -6.1F, -4.0F, 6.0F, 0.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 0.0F, 0.0F));

        PartDefinition head_perching = perching.addOrReplaceChild("head_perching", CubeListBuilder.create().texOffs(36, 0).addBox(-2.5F, -4.5F, -4.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, -9.5F, -0.5F));

        PartDefinition rocket = all.addOrReplaceChild("rocket", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(0, 24).addBox(-9.0F, -2.0F, -2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(0, 24).mirror().addBox(6.0F, -2.0F, -2.0F, 3.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition standing = all.addOrReplaceChild("standing", CubeListBuilder.create().texOffs(48, 10).addBox(-1.5F, -6.75F, -0.75F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.25F, -0.25F));

        PartDefinition head_standing = standing.addOrReplaceChild("head_standing", CubeListBuilder.create().texOffs(36, 0).addBox(-2.5F, -4.5F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -7.25F, 0.25F));

        PartDefinition legs_sitting = standing.addOrReplaceChild("legs_sitting", CubeListBuilder.create().texOffs(53, 0).mirror().addBox(-2.5F, 0.0F, -2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(53, 0).addBox(0.5F, 0.0F, -2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.75F, -0.75F));

        PartDefinition right_arm_standing = standing.addOrReplaceChild("right_arm_standing", CubeListBuilder.create().texOffs(51, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, -5.75F, 0.25F));

        PartDefinition left_arm_standing = standing.addOrReplaceChild("left_arm_standing", CubeListBuilder.create().texOffs(51, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, -5.75F, 0.25F));

        PartDefinition legs_standing = standing.addOrReplaceChild("legs_standing", CubeListBuilder.create(), PartPose.offset(1.0F, -3.75F, 0.25F));

        PartDefinition left_leg_standing = legs_standing.addOrReplaceChild("left_leg_standing", CubeListBuilder.create().texOffs(55, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_leg_standing = legs_standing.addOrReplaceChild("right_leg_standing", CubeListBuilder.create().texOffs(55, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    @Override
    public void setupAnim(BrubbleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.right_leg_standing.resetPose();
        this.left_leg_standing.resetPose();
        this.right_arm_standing.resetPose();
        this.left_arm_standing.resetPose();

        if (entity.hasRocket()) {
            this.rocket.visible = true;
            if (!entity.isStanding()) {
                this.standing.visible = false;
                this.perching.visible = true;
            } else {
                this.standing.visible = true;
                this.perching.visible = false;
            }

            this.all.yRot = netHeadYaw * ((float)Math.PI / 180F);
            this.all.xRot = headPitch * ((float) Math.PI / 180F);
            if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7)
                this.all.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);
            this.standing.xRot = -this.all.xRot;
        } else {
            this.rocket.visible = false;
            this.standing.visible = true;
            this.perching.visible = false;
            this.standing.xRot = this.standing.getInitialPose().xRot;
            this.all.resetPose();
        }

        handleElse(entity, limbSwing, limbSwingAmount, ageInTicks);
        handleHead(netHeadYaw, headPitch);
        handleLegs(entity);
    }

    private void handleElse(BrubbleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks) {
        int height = 0;
        if (!entity.hasRocket()) {
            if (entity.isStanding()) {
                height = 12;
                this.right_leg_standing.resetPose();
                this.left_leg_standing.resetPose();
                this.right_arm_standing.resetPose();
                this.left_arm_standing.resetPose();

                this.right_leg_standing.xRot += Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
                this.left_leg_standing.xRot += Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
                this.right_arm_standing.xRot += Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
                this.left_arm_standing.xRot += Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
            } else {
                height = 16;
            }
        }

        this.all.y = this.all.getInitialPose().y + height;
    }

    private void handleLegs(BrubbleEntity entity) {
        if (entity.isStanding()) {
            this.legs_sitting.visible = false;
            this.legs_standing.visible = true;
        } else {
            this.legs_sitting.visible = true;
            this.legs_standing.visible = false;
        }
    }

    private void handleHead(float netHeadYaw, float headPitch) {
        this.head_perching.xRot = headPitch * ((float)Math.PI / 180F);
        this.head_perching.yRot = netHeadYaw * ((float)Math.PI / 180F);
        this.head_standing.xRot = headPitch * ((float)Math.PI / 180F);
        this.head_standing.yRot = netHeadYaw * ((float)Math.PI / 180F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        all.render(poseStack, vertexConsumer, i, i1, i2);
    }
}

