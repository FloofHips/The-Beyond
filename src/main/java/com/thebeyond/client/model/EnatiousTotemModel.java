package com.thebeyond.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.Entity;

public class EnatiousTotemModel<T extends Entity> extends HierarchicalModel {
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
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Entity entity, float v, float v1, float v2, float v3, float v4) {

    }
}
