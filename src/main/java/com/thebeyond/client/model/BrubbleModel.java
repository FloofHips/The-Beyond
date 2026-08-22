package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.entity.BrubbleEntity;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.registry.BeyondRenderTypes;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class BrubbleModel <T extends BrubbleEntity> extends EntityModel<BrubbleEntity> {
    private final ModelPart fly;
    private final ModelPart eye_float;
    private final ModelPart bone;
    private final ModelPart ground;
    private final ModelPart eye;

    public BrubbleModel(ModelPart root) {
        this.fly = root.getChild("fly");
        this.eye_float = this.fly.getChild("eye_float");
        this.bone = this.fly.getChild("bone");
        this.ground = root.getChild("ground");
        this.eye = this.ground.getChild("eye");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition fly = partdefinition.addOrReplaceChild("fly", CubeListBuilder.create().texOffs(0, 0).addBox(-7.5F, -7.5F, -7.5F, 15.0F, 15.0F, 15.0F, new CubeDeformation(0.0F))
                .texOffs(0, 30).addBox(-6.5F, -6.5F, -6.5F, 13.0F, 13.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.5F, 0.0F));

        PartDefinition eye_float = fly.addOrReplaceChild("eye_float", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -6.6F));

        PartDefinition bone = fly.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(45, 0).addBox(-10.5F, -10.5F, 0.0F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F))
                .texOffs(45, 0).mirror().addBox(2.5F, -10.5F, 0.0F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(45, 30).mirror().addBox(2.5F, 2.5F, 0.0F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false)
                .texOffs(45, 30).addBox(-10.5F, 2.5F, 0.0F, 8.0F, 8.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition ground = partdefinition.addOrReplaceChild("ground", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -4.5F, -4.5F, 9.0F, 9.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(0, 18).addBox(-7.5F, -7.5F, 0.0F, 15.0F, 15.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 19.5F, 0.0F));

        PartDefinition eye = ground.addOrReplaceChild("eye", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -4.6F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(BrubbleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        eye.resetPose();
        eye_float.resetPose();
        this.bone.zRot += 0.3f*limbSwingAmount;

        if (entity.isFloating()) {
            this.ground.visible = false;
            this.fly.visible = true;
            this.fly.xRot = headPitch * 0.017453292F;
            this.fly.yRot = netHeadYaw * 0.017453292F;
            if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-7)
                this.fly.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);
            this.eye_float.x = this.eye_float.getInitialPose().x - (Math.clamp((int)(netHeadYaw / 10), -1, 1));
            this.eye_float.y = this.eye_float.getInitialPose().y + (Math.clamp( (int) (headPitch / 10), -1, 1));
        } else {
            this.ground.visible = true;
            this.fly.visible = false;
            this.eye.x = this.eye.getInitialPose().x - (Math.clamp((int) (netHeadYaw / 10), -1, 1));
            this.eye.y = this.eye.getInitialPose().y + (Math.clamp((int) (headPitch / 10), 0, 1));
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        ground.render(poseStack, vertexConsumer, i, i1, i2);
        fly.render(poseStack, vertexConsumer, i, i1, i2);
    }
}

