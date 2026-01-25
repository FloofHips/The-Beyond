package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.model.animation.AbyssalNomadAnimations;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class AbyssalNomadModel <T extends AbyssalNomadEntity> extends HierarchicalModel<AbyssalNomadEntity> {
    private final ModelPart all;
    private final ModelPart body;
    private final ModelPart r1;
    private final ModelPart r2;
    private final ModelPart r3;
    private final ModelPart r7;
    private final ModelPart r8;
    private final ModelPart r9;
    private final ModelPart r13;
    private final ModelPart r14;
    private final ModelPart r15;
    private final ModelPart r4;
    private final ModelPart r5;
    private final ModelPart r6;
    private final ModelPart r10;
    private final ModelPart r11;
    private final ModelPart r12;
    private final ModelPart r16;
    private final ModelPart r17;
    private final ModelPart r18;
    private final ModelPart right_arm;
    private final ModelPart left_arm;
    private final ModelPart leg;
    private final ModelPart root;
    public boolean glow;
    public AbyssalNomadModel(ModelPart root) {
        glow = false;
        this.root = root;
        this.all = this.root.getChild("all");
        this.body = this.all.getChild("body");
        this.r1 = this.body.getChild("r1");
        this.r2 = this.r1.getChild("r2");
        this.r3 = this.r2.getChild("r3");
        this.r7 = this.body.getChild("r7");
        this.r8 = this.r7.getChild("r8");
        this.r9 = this.r8.getChild("r9");
        this.r13 = this.body.getChild("r13");
        this.r14 = this.r13.getChild("r14");
        this.r15 = this.r14.getChild("r15");
        this.r4 = this.body.getChild("r4");
        this.r5 = this.r4.getChild("r5");
        this.r6 = this.r5.getChild("r6");
        this.r10 = this.body.getChild("r10");
        this.r11 = this.r10.getChild("r11");
        this.r12 = this.r11.getChild("r12");
        this.r16 = this.body.getChild("r16");
        this.r17 = this.r16.getChild("r17");
        this.r18 = this.r17.getChild("r18");
        this.right_arm = this.all.getChild("right_arm");
        this.left_arm = this.all.getChild("left_arm");
        this.leg = this.all.getChild("leg");
    }

    public static LayerDefinition createBodyLayer(CubeDeformation cubeDeformation) {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(96, 0).addBox(-6.0F, -12.0F, 6.0F, 12.0F, 12.0F, 12.0F, cubeDeformation)
                .texOffs(48, 24).addBox(6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F, cubeDeformation)
                .texOffs(48, 0).addBox(-18.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F, cubeDeformation)
                .texOffs(0, 0).addBox(-6.0F, -36.0F, -6.0F, 12.0F, 48.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, -33.0F, -1.0F));

        PartDefinition r1 = body.addOrReplaceChild("r1", CubeListBuilder.create().texOffs(84, 48).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(12.0F, -12.0F, 6.0F));

        PartDefinition r2 = r1.addOrReplaceChild("r2", CubeListBuilder.create().texOffs(84, 36).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r3 = r2.addOrReplaceChild("r3", CubeListBuilder.create().texOffs(84, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r7 = body.addOrReplaceChild("r7", CubeListBuilder.create().texOffs(84, 48).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(-12.0F, -12.0F, 6.0F));

        PartDefinition r8 = r7.addOrReplaceChild("r8", CubeListBuilder.create().texOffs(84, 36).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r9 = r8.addOrReplaceChild("r9", CubeListBuilder.create().texOffs(84, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r13 = body.addOrReplaceChild("r13", CubeListBuilder.create().texOffs(108, 48).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, -36.0F, 6.0F));

        PartDefinition r14 = r13.addOrReplaceChild("r14", CubeListBuilder.create().texOffs(108, 36).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r15 = r14.addOrReplaceChild("r15", CubeListBuilder.create().texOffs(108, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r4 = body.addOrReplaceChild("r4", CubeListBuilder.create().texOffs(84, 48).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(12.0F, 0.0F, 6.0F));

        PartDefinition r5 = r4.addOrReplaceChild("r5", CubeListBuilder.create().texOffs(84, 36).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r6 = r5.addOrReplaceChild("r6", CubeListBuilder.create().texOffs(84, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r10 = body.addOrReplaceChild("r10", CubeListBuilder.create().texOffs(84, 48).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(-12.0F, 0.0F, 6.0F));

        PartDefinition r11 = r10.addOrReplaceChild("r11", CubeListBuilder.create().texOffs(84, 36).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r12 = r11.addOrReplaceChild("r12", CubeListBuilder.create().texOffs(84, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r16 = body.addOrReplaceChild("r16", CubeListBuilder.create().texOffs(108, 48).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, -33.0F, 6.0F));

        PartDefinition r17 = r16.addOrReplaceChild("r17", CubeListBuilder.create().texOffs(108, 36).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));

        PartDefinition r18 = r17.addOrReplaceChild("r18", CubeListBuilder.create().texOffs(108, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 12.0F, cubeDeformation), PartPose.offset(0.0F, 0.0F, 12.0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(216, 12).addBox(-6.0F, -3.0F, -1.0F, 12.0F, 6.0F, 6.0F, cubeDeformation)
                .texOffs(144, 30).addBox(-6.0F, -3.0F, -7.0F, 12.0F, 24.0F, 6.0F, cubeDeformation)
                .texOffs(216, 0).addBox(-6.0F, 15.0F, -1.0F, 12.0F, 6.0F, 6.0F, cubeDeformation), PartPose.offset(-14.0F, -21.0F, -8.0F));

        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(216, 12).addBox(-6.0F, -3.0F, -1.0F, 12.0F, 6.0F, 6.0F, cubeDeformation)
                .texOffs(180, 30).addBox(-6.0F, -3.0F, -7.0F, 12.0F, 24.0F, 6.0F, cubeDeformation)
                .texOffs(216, 0).addBox(-6.0F, 15.0F, -1.0F, 12.0F, 6.0F, 6.0F, cubeDeformation), PartPose.offset(14.0F, -21.0F, -8.0F));

        PartDefinition leg = root.addOrReplaceChild("leg", CubeListBuilder.create().texOffs(216, 24).addBox(-6.0F, -3.0F, -13.0F, 12.0F, 6.0F, 6.0F, cubeDeformation)
                .texOffs(180, 0).addBox(-6.0F, -3.0F, -7.0F, 12.0F, 24.0F, 6.0F, cubeDeformation)
                .texOffs(216, 36).addBox(-6.0F, 15.0F, -1.0F, 12.0F, 6.0F, 6.0F, cubeDeformation), PartPose.offset(0.0F, -21.0F, 28.0F));

        return LayerDefinition.create(meshdefinition, 256, 64);
    }



    @Override
    public void setupAnim(AbyssalNomadEntity abyssalNomadEntity, float v, float v1, float v2, float v3, float v4) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animateWalk(AbyssalNomadAnimations.walk, v, v1, 6, 10);

        this.animate(abyssalNomadEntity.nodAnimationState, AbyssalNomadAnimations.nod, v2, 1);
        this.animate(abyssalNomadEntity.sitAnimationState, AbyssalNomadAnimations.sit, v2, 1);
        this.animate(abyssalNomadEntity.sitPoseAnimationState, AbyssalNomadAnimations.sitting, v2, 1);
        this.animate(abyssalNomadEntity.standUpAnimationState, AbyssalNomadAnimations.standup, v2, 1);
        this.animate(abyssalNomadEntity.attackAnimationState, AbyssalNomadAnimations.attack, v2, 1);

        this.body.y += Mth.sin(((v2) * 0.09f) - 2f);
        this.body.yRot += v3 * 0.005F;
        if (glow) {
            r1. xScale = 0;
            r4. xScale = 0;
            r7. xScale = 0;
            r10.xScale = 0;
            r13.xScale = 0;
            r16.xScale = 0;

            r1. zScale = 0;
            r4. zScale = 0;
            r7. zScale = 0;
            r10.zScale = 0;
            r13.zScale = 0;
            r16.zScale = 0;
        } else {
            r1.xRot = (float) ((Math.sin((v2) * 0.09f) * 0.20));
            r2.xRot = (float) ((Math.sin((v2 - 10f) * 0.09f) * 0.25));
            r3.xRot = (float) ((Math.sin((v2 - 20f) * 0.09f) * 0.3));

            r4.xRot = (float) ((Math.sin((v2) * 0.09f) * 0.20));
            r5.xRot = (float) ((Math.sin((v2 + 5f) * 0.09f) * 0.25));
            r6.xRot = (float) ((Math.sin((v2 + 10f) * 0.09f) * 0.3));

            r7.xRot = (float) ((Math.sin((v2) * 0.09f) * 0.20));
            r8.xRot = (float) ((Math.sin((v2 - 10f) * 0.09f) * 0.25));
            r9.xRot = (float) ((Math.sin((v2 - 20f) * 0.09f) * 0.3));

            r10.xRot = (float) ((Math.sin((v2) * 0.09f) * 0.20));
            r11.xRot = (float) ((Math.sin((v2 + 5f) * 0.09f) * 0.25));
            r12.xRot = (float) ((Math.sin((v2 + 10f) * 0.09f) * 0.3));

            r13.xRot = (float) ((Math.sin((v2) * 0.09f) * 0.20 * v1));
            r14.xRot = (float) ((Math.sin((v2 - 10f) * 0.09f) * 0.25));
            r15.xRot = (float) ((Math.sin((v2 - 20f) * 0.09f) * 0.3));

            r16.xRot = (float) ((Math.sin((v2) * 0.09f) * 0.20 * v1));
            r17.xRot = (float) ((Math.sin((v2 - 15f) * 0.09f) * 0.25));
            r18.xRot = (float) ((Math.sin((v2 - 25f) * 0.09f) * 0.3));
        }


    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        all.render(poseStack, vertexConsumer, i, i1, i2);
    }

    @Override
    public ModelPart root() {
        return this.all;
    }
}
