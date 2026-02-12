package com.thebeyond.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.model.animation.EnatiousTotemAnimations;
import com.thebeyond.common.entity.EnadrakeEntity;
import com.thebeyond.common.entity.EnatiousTotemEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class EnatiousTotemModel <T extends EnatiousTotemEntity> extends HierarchicalModel<EnatiousTotemEntity> {
    private final ModelPart all;
    private final ModelPart root;
    private final ModelPart bone3;
    private final ModelPart bone2;
    private final ModelPart bone;

    public EnatiousTotemModel(ModelPart root) {
        this.root = root;
        this.all = this.root.getChild("all");
        this.bone = this.all.getChild("bone");
        this.bone2 = this.all.getChild("bone2");
        this.bone3 = this.all.getChild("bone3");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition root = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

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
        this.root().getAllParts().forEach(ModelPart::resetPose);

        int max = enatiousTotemEntity.getMaxCooldown();
        float flag = enatiousTotemEntity.getCooldown() == max ? 5 : (enatiousTotemEntity.getCooldown() < max/2f ? 0 : 10);

        //int f = (int) (v2/4) * 4;
        //int f2 = (int) (v2/2) * 2;

        float f = v2;
        float f2 = f;

        boolean charging = (enatiousTotemEntity.getCountdown() < 27 && enatiousTotemEntity.getCountdown() > 0);
        boolean cooldown = (enatiousTotemEntity.getCooldown() < 91 && enatiousTotemEntity.getCooldown() > 0);
        boolean spawning = (enatiousTotemEntity.getSpawnProgress() < 30 && enatiousTotemEntity.getSpawnProgress() > 0);

        if (enatiousTotemEntity.getCountdown() < 27 && enatiousTotemEntity.getCountdown() > 0) {
            this.animate(enatiousTotemEntity.shootAnimationState, EnatiousTotemAnimations.SHOOT, f2);
        }

        if (enatiousTotemEntity.getCooldown() < 81 && enatiousTotemEntity.getCooldown() > 0) {
            this.animate(enatiousTotemEntity.rechargeAnimationState, EnatiousTotemAnimations.RECHARGE, f2);
        }

        if (enatiousTotemEntity.getSpawnProgress() < 30 && enatiousTotemEntity.getSpawnProgress() > 0) {
            this.animate(enatiousTotemEntity.spawnAnimationState, EnatiousTotemAnimations.SPAWN, f2);
            this.bone3.yRot += v3 * 0.01453292F;
            this.bone2.yRot += (v3 / 2) * 0.05453292F;
        } else {
            this.bone3.yRot = v3 * 0.01453292F;
            this.bone2.yRot = (v3 / 2) * 0.05453292F;
            this.bone2.xScale = 0.95f;
            this.bone2.zScale = 0.95f;
            this.bone2.y = - 24 + Mth.cos(f / 4.5f) * 0.15F * flag;
            this.bone3.y = - (24 + 16) + Mth.cos((f / 5f) + Mth.PI + 5) * 0.15F * flag;
            this.bone.x  = Mth.cos(f / 2f) * 0.05F * flag;
            this.bone.z  = Mth.sin(f / 2f) * 0.05F * flag;
            this.bone2.x = Mth.cos(f / 2f) * 0.1F * flag;
            this.bone2.z = Mth.sin(f / 2f) * 0.1F * flag;
            this.bone3.x = Mth.cos(f / 2f) * 0.2F * flag;
            this.bone3.z = Mth.sin(f / 2f) * 0.2F * flag;
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
