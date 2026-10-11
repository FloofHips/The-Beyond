package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.LanternEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class LanternSmallModel<T extends LanternEntity> extends EntityModel<LanternEntity> {

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart top_fin;
    private final ModelPart tf_2;
    private final ModelPart tf_3;
    private final ModelPart bot_fin;
    private final ModelPart bf_2;
    private final ModelPart bf_3;

    public LanternSmallModel(ModelPart root) {
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
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

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -4.0F, 0.0F, 4.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition top_fin = body.addOrReplaceChild("top_fin", CubeListBuilder.create().texOffs(19, 11).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 5.0F));

        PartDefinition tf_2 = top_fin.addOrReplaceChild("tf_2", CubeListBuilder.create().texOffs(19, 6).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition tf_3 = tf_2.addOrReplaceChild("tf_3", CubeListBuilder.create().texOffs(20, 2).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition bot_fin = body.addOrReplaceChild("bot_fin", CubeListBuilder.create().texOffs(19, 11).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition bf_2 = bot_fin.addOrReplaceChild("bf_2", CubeListBuilder.create().texOffs(19, 6).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition bf_3 = bf_2.addOrReplaceChild("bf_3", CubeListBuilder.create().texOffs(20, 2).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        return LayerDefinition.create(meshdefinition, 32, 16);
    }

    @Override
    public void setupAnim(LanternEntity lantern, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.z = -3;
        this.body.xRot = headPitch * 0.017453292F;
        this.body.yRot = netHeadYaw * 0.017453292F;
        if (lantern.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7)
            this.body.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);

        this.root.y = 0.5F * Mth.sin(ageInTicks * 0.18f) + 24;

        this.root.xRot = 0.5f * 0.3F * Mth.cos(ageInTicks * 0.18f);
        this.root.yRot = 0.5f * 0.5F * Mth.cos((ageInTicks - 0.25f) * 0.09f);
        this.root.zRot = 0.5f * 0.5F * Mth.sin(ageInTicks * 0.09f);

        top_fin.xRot = (float) ((Math.sin((ageInTicks) * 0.18f) * 0.1));
        tf_2.xRot = (float) ((Math.sin((ageInTicks - 5f) * 0.18f) * 0.15 * 0.5));
        tf_3.xRot = (float) ((Math.sin((ageInTicks - 10f) * 0.18f) * 0.1));

        bot_fin.xRot = (float) ((Math.sin((ageInTicks - 2.5f) * 0.18f) * 0.1));
        bot_fin.yRot = (float) ((Math.cos((ageInTicks - 5f) * 0.09f) * 0.05));
        bf_2.xRot = (float) ((Math.sin((ageInTicks - 7.5f) * 0.18f) * 0.15 * 0.5));
        bf_3.xRot = (float) ((Math.sin((ageInTicks - 12.5f) * 0.18f) * 0.1));
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}