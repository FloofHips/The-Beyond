package com.thebeyond.client.model;// Made with Blockbench 4.2.5
// Exported for Minecraft version 1.17 - 1.18 with Mojang mappings
// Paste this class into your mod and generate all required imports


import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class EnderglopModel<T extends EnderglopEntity> extends EnderdropModel<T> {

	private final ModelPart body;

	public EnderglopModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 45).addBox(-11.5F, -22.0F, -8.0F, 23.0F, 22.0F, 23.0F, new CubeDeformation(1.0F)).texOffs(0, 0).addBox(-11.5F, -22.0F, -8.0F, 23.0F, 22.0F, 23.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}
}