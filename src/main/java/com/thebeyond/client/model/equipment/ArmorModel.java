package com.thebeyond.client.model.equipment;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ArmorModel extends HumanoidModel<LivingEntity> {

    public ArmorModel(ModelPart root) {

        super(root);
    }

    public void setup(LivingEntity entity, ItemStack itemStack, EquipmentSlot slot, HumanoidModel<?> original) {

        this.attackTime = original.attackTime;
        this.riding = original.riding;
        this.young = original.young;
        this.leftArmPose = original.leftArmPose;
        this.rightArmPose = original.rightArmPose;
        this.crouching = original.crouching;
        this.head.copyFrom(original.head);
        this.hat.copyFrom(original.hat);
        this.body.copyFrom(original.body);
        this.rightArm.copyFrom(original.rightArm);
        this.leftArm.copyFrom(original.leftArm);
        this.rightLeg.copyFrom(original.rightLeg);
        this.leftLeg.copyFrom(original.leftLeg);
    }

    public static Supplier<LayerDefinition> wrap(Consumer<PartDefinition> definition, int width, int height) {

        return () -> {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            CubeListBuilder empty = CubeListBuilder.create();

            root.addOrReplaceChild("head", empty, PartPose.ZERO);
            root.addOrReplaceChild("hat", empty, PartPose.ZERO);
            root.addOrReplaceChild("body", empty, PartPose.ZERO);
            root.addOrReplaceChild("left_arm", empty, PartPose.ZERO);
            root.addOrReplaceChild("right_arm", empty, PartPose.ZERO);
            root.addOrReplaceChild("left_leg", empty, PartPose.ZERO);
            root.addOrReplaceChild("right_leg", empty, PartPose.ZERO);

            definition.accept(root);

            return LayerDefinition.create(mesh, width, height);
        };
    }

}
