package com.thebeyond.client.model;// Made with Blockbench 4.9.3
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;


public class EnderdropModel<T extends EnderglopEntity> extends HierarchicalModel<T> {

	private final ModelPart body;

	public EnderdropModel(ModelPart root) {
		this.body = root.getChild("body");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -4.4F, -2.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
				.texOffs(2, 8).addBox(-4.0F, -1.4F, -3.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(6, 0).addBox(-2.0F, -2.4F, 0.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(0, 0).addBox(-3.0F, -0.4F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(0, 3).addBox(1.0F, -0.4F, -1.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 20.4F, -2.0F));

		return LayerDefinition.create(meshdefinition, 64, 32);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		body.yRot = 0;
		if(entity.getIsCharging()){
			body.yRot += Mth.cos(ageInTicks*3) * 0.06f;
		}
		if(entity.getIsArmored()){
			body.yRot += entity.squish * Mth.cos(ageInTicks);
		}
		body.xRot = entity.squish;
	}

	@Override
	public ModelPart root() {
		return body;
	}

}