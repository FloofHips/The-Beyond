package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.KnockbackSeedEntity;
import com.thebeyond.common.entity.UnstableSeedEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

public class UnstableSeedModel<T extends UnstableSeedEntity> extends EntityModel<T> {

    private final ModelPart bone;
    private final ModelPart fan;

    public UnstableSeedModel(ModelPart root) {
        this.bone = root.getChild("bone");
        this.fan = this.bone.getChild("fan");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 5).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition fan = bone.addOrReplaceChild("fan", CubeListBuilder.create(), PartPose.offset(0.0F, -10.5F, 0.0F));

        PartDefinition cube_r1 = fan.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -2.5F, 0.0F, 10.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

        PartDefinition cube_r2 = fan.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -2.5F, 0.0F, 10.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(T t, float v, float v1, float v2, float v3, float v4) {
        bone.xRot = (float) Math.PI;
        bone.y = 0.5f;
        fan.yRot = v2;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        bone.render(poseStack, vertexConsumer, i, i1, i2);
    }
}