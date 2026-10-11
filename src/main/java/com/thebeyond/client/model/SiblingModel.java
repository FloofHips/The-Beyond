package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.model.animation.EnatiousTotemAnimations;
import com.thebeyond.client.model.animation.SiblingAnimation;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import com.thebeyond.common.entity.SiblingEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class SiblingModel  <T extends SiblingEntity> extends HierarchicalModel<SiblingEntity> {
    private final ModelPart all;
    private final ModelPart root;
    private final ModelPart right_leg;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart left_arm;
    private final ModelPart right_arm;
    private final ModelPart left_leg;

    public SiblingModel(ModelPart root) {
        this.root = root;
        this.all = this.root.getChild("all");
        this.right_leg = this.all.getChild("right_leg");
        this.head = this.all.getChild("head");
        this.body = this.all.getChild("body");
        this.left_arm = this.all.getChild("left_arm");
        this.right_arm = this.all.getChild("right_arm");
        this.left_leg = this.all.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 4.6667F, 0.0F));

        PartDefinition right_leg = all.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.1F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.9F, 7.3333F, 0.0F));

        PartDefinition head = all.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.6667F, 0.0F));

        PartDefinition body = all.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.6667F, 0.0F));

        PartDefinition left_arm = all.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, -2.6667F, 0.0F));

        PartDefinition right_arm = all.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -2.6667F, 0.0F));

        PartDefinition left_leg = all.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1.9F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(1.9F, 7.3333F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(SiblingEntity entity, float v, float v1, float v2, float v3, float v4) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        if (entity.getBirth()) {
            this.animate(entity.birthAnimationState, SiblingAnimation.BIRTH, v2);
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
