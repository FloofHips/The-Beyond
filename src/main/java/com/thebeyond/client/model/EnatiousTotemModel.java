package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class EnatiousTotemModel <T extends EnatiousTotemEntity> extends EntityModel<EnatiousTotemEntity> {
    private final ModelPart root;
    private final ModelPart bone3;
    private final ModelPart bone2;
    private final ModelPart bone;

    public EnatiousTotemModel(ModelPart root) {
        this.root = root.getChild("root");
        this.bone = this.root.getChild("bone");
        this.bone2 = this.root.getChild("bone2");
        this.bone3 = this.root.getChild("bone3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition bone3 = root.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -8.0F, -12.0F, 24.0F, 16.0F, 24.0F, new CubeDeformation(0.0F))
                .texOffs(96, 0).addBox(-5.0F, -5.0F, -16.0F, 10.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -40.0F, 0.0F));

        PartDefinition bone2 = root.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(0, 40).addBox(-12.0F, -7.5F, -12.0F, 24.0F, 16.0F, 24.0F, new CubeDeformation(0.0F))
                .texOffs(96, 12).addBox(-5.0F, -4.5F, -16.0F, 10.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -24.5F, 0.0F));

        PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 80).addBox(-12.0F, -7.5F, -12.0F, 24.0F, 16.0F, 24.0F, new CubeDeformation(0.0F))
                .texOffs(96, 24).addBox(-5.0F, -4.5F, -16.0F, 10.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.5F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(EnatiousTotemEntity enatiousTotemEntity, float v, float v1, float v2, float v3, float v4) {
        this.bone3.yRot = v3 * 0.01453292F;
        this.bone2.yRot = (v3 / 2) * 0.05453292F;
        if ((int)v2 % 4 != 0) {
            return;
        }

        float flag = enatiousTotemEntity.getTarget()==null ? 5 : 10;

        this.bone.x = Mth.cos(v2 / 4f) * 0.2F * flag;
        this.bone.z = Mth.cos(v2 / 4.5f) * 0.2F * flag;

        this.bone2.x = Mth.cos(v2 / 3f) * 0.2F * flag;
        this.bone2.z = Mth.cos(v2 / 2.5f) * 0.2F * flag;

        this.bone3.x = Mth.cos(v2 / 2f) * 0.2F * flag;
        this.bone3.z = Mth.cos(v2 / 2.5f) * 0.2F * flag;
        //this.bone.xRot = v4 * 0.017453292F;
        //this.bone2.xRot = v4 * 0.017453292F / 3;
        this.bone2.y = - 24 + Mth.cos(v2 / 4f) * 0.2F;
        this.bone2.xScale = 0.9999f;
        this.bone2.zScale = 0.9999f;
        this.bone3.y = - (24 + 16) + Mth.cos((v2 / 5f) + Mth.PI + 5) * 0.2F;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }
}
