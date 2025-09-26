package com.thebeyond.client.model;// Made with Blockbench 4.2.5
// Exported for Minecraft version 1.17 - 1.18 with Mojang mappings
// Paste this class into your mod and generate all required imports


import com.thebeyond.common.entity.EnderglopEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;

public class EnderglopModel<T extends EnderglopEntity> extends EnderdropModel<T> {

	private final ModelPart body;

	public EnderglopModel(ModelPart root) {
        super(root);
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

		PartDefinition bone = body.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(32, 16).addBox(-7.0F, -8.0F, -1.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.2F))
				.texOffs(32, 8).addBox(-7.0F, -5.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offset(3.0F, 3.6F, -1.0F));

		return LayerDefinition.create(meshdefinition, 64, 32);
	}
}