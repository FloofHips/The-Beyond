package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.model.animation.StalkerAnimation;
import com.thebeyond.common.entity.StalkerEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class StalkerModel<T extends StalkerEntity> extends HierarchicalModel<StalkerEntity> {

    private final ModelPart root;
    private final ModelPart base;
    private final ModelPart biter;
    private final ModelPart ankle;

    public StalkerModel(ModelPart root) {
        this.root = root.getChild("root");
        this.base = this.root.getChild("base");
        this.biter = this.base.getChild("biter");
        this.ankle = this.base.getChild("ankle");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 16.0F, 0.0F));

        PartDefinition base = root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(48, 0).addBox(-3.0F, -3.0F, -10.0F, 6.0F, 6.0F, 20.0F, new CubeDeformation(0.0F))
                .texOffs(48, 26).addBox(-4.0F, -4.0F, 2.0F, 8.0F, 8.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(36, 0).addBox(-1.0F, -6.0F, 4.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(36, 0).addBox(-1.0F, 4.0F, 4.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(36, 0).addBox(4.0F, -1.0F, 4.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(36, 0).addBox(-6.0F, -1.0F, 4.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition biter = base.addOrReplaceChild("biter", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(0, 48).addBox(-5.0F, -5.0F, -4.0F, 10.0F, 10.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -16.0F));

        PartDefinition ankle = base.addOrReplaceChild("ankle", CubeListBuilder.create().texOffs(0, 24).addBox(-6.0F, -6.0F, -6.0F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -16.0F));

        return LayerDefinition.create(meshdefinition, 128, 64);
    }



    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int i1, int i2) {
        root.render(poseStack, vertexConsumer, i, i1, i2);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(StalkerEntity stalkerEntity, float v, float v1, float v2, float v3, float v4) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.animate(stalkerEntity.attackAnimationState, StalkerAnimation.ATTACK, v2, 1);
        this.animate(stalkerEntity.spreadAnimationState, StalkerAnimation.SPREAD, v2, 1);
        this.animate(stalkerEntity.retreatAnimationState, StalkerAnimation.RETRACT, v2, 1);

        biter.visible = true;
        ankle.visible = false;

        if (stalkerEntity.isViolent()) {
            biter.visible = true;
            ankle.visible = false;
        } else {
            biter.visible = false;
            ankle.visible = true;
        }
    }
}
