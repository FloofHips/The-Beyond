package com.thebeyond.client.model.equipment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public class AnchorLeggingsModel extends MultipartArmorModel{
    public AnchorLeggingsModel() {
        super("anchor", 64, 32);
    }
    @Override
    protected Function<ModelPart, ArmorModel> getModelPartConstructor(EquipmentSlot slot) {

        return switch (slot) {
            case HEAD, BODY, CHEST, LEGS, FEET, MAINHAND, OFFHAND -> ArmorModel::new;
        };
    }

    @Override
    public void chestLayer(PartDefinition root) {
        }

    @Override
    public void legLayer(PartDefinition root) {

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(1.0F))
            .texOffs(16, 5).addBox(-6.0F, 9.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.1F))
            .texOffs(16, 5).mirror().addBox(2.0F, 9.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.1F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)).mirror(false)
                .texOffs(0, 0).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)).mirror(false)
                .texOffs(16, 0).addBox(-1.0F, 5.0F, -4.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.5F)), PartPose.offset(1.9F, 12.0F, 0.0F));

        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.2F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F))
                .texOffs(0, 0).addBox(-2.2F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F))
                .texOffs(16, 0).addBox(-2.2F, 5.0F, -4.0F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0.5F)), PartPose.offset(-1.9F, 12.0F, 0.0F));}
}
