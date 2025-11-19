package com.thebeyond.client.model.equipment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public class EtherCloakModel extends MultipartArmorModel{
    public EtherCloakModel() {
        super("shroud", 64, 64);
    }
    @Override
    protected Function<ModelPart, ArmorModel> getModelPartConstructor(EquipmentSlot slot) {

        return switch (slot) {
            case HEAD -> Head::new;
            case BODY, FEET, CHEST, LEGS, MAINHAND, OFFHAND -> ArmorModel::new;
        };
    }

    @Override
    public void headLayer(PartDefinition root) {

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.75F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition f1 = head.addOrReplaceChild("f1", CubeListBuilder.create().texOffs(27, 11).addBox(-4.0F, 0.0F, -0.75F, 8.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 4.75F));

        PartDefinition f2 = f1.addOrReplaceChild("f2", CubeListBuilder.create().texOffs(27, 6).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 4.25F));

        PartDefinition f3 = f2.addOrReplaceChild("f3", CubeListBuilder.create().texOffs(26, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 0.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 5.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(24, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.6F))
                .texOffs(28, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F, new CubeDeformation(0.8F)), PartPose.offset(0.0F, 0.0F, 0.0F));


        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(16, 59).mirror().addBox(0.0F, -2.25F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(1.5F)).mirror(false), PartPose.offset(5.0F, 2.0F, 0.0F));

        PartDefinition left_front = left_arm.addOrReplaceChild("left_front", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-5.0F, -2.0F, -2.0F, 9.0F, 12.0F, 4.0F, new CubeDeformation(1.2F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition left_back = left_arm.addOrReplaceChild("left_back", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-4.0F, -2.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(1.1F)).mirror(false), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(16, 59).addBox(-4.0F, -2.25F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(1.5F)), PartPose.offset(-5.0F, 2.0F, 0.0F));

        PartDefinition right_front = right_arm.addOrReplaceChild("right_front", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(1.2F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition right_back = right_arm.addOrReplaceChild("right_back", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(1.1F)), PartPose.offset(0.0F, 0.0F, 0.0F));
    }

    @Override
    public void feetLayer(PartDefinition root) {
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)), PartPose.offset(1.9F, 12.0F, 0.0F));

        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 48).addBox(-2.2F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.75F)), PartPose.offset(-1.9F, 12.0F, 0.0F));
    }

    public static class Head extends ArmorModel {

        private final ModelPart head;
        private final ModelPart f1;
        private final ModelPart f2;
        private final ModelPart f3;

        private final ModelPart left_front;
        private final ModelPart left_back;
        private final ModelPart right_front;
        private final ModelPart right_back;

        public Head(ModelPart root) {
            super(root);
            this.head = root.getChild("head");
            this.f1 = this.head.getChild("f1");
            this.f2 = this.f1.getChild("f2");
            this.f3 = this.f2.getChild("f3");
            this.left_front = leftArm.getChild("left_front");
            this.left_back = leftArm.getChild("left_back");
            this.right_front = rightArm.getChild("right_front");
            this.right_back = rightArm.getChild("right_back");
        }

        @Override
        public void setup(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            super.setup(entity, stack, slot, original);

            float p = 0;
            if (Minecraft.getInstance() != null)
                p = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

            f1.xRot = (float) ((Math.sin((entity.tickCount + p) * 0.09f) * 0.20));
            f2.xRot = (float) ((Math.sin((entity.tickCount + p - 10f) * 0.09f) * 0.25));
            f3.xRot = (float) ((Math.sin((entity.tickCount + p - 20f) * 0.09f) * 0.3));

            leftArm.zRot = (float) (Math.sin((entity.tickCount + p) * 0.02f) * 0.09);
            left_front.xRot = Math.min(0, -original.leftArm.xRot);
            left_back.xRot = Math.max(0, -original.leftArm.xRot);

            rightArm.zRot = (float) -(Math.sin((entity.tickCount + p) * 0.02f) * 0.09);
            right_front.xRot = Math.min(0, -original.rightArm.xRot);
            right_back.xRot = Math.max(0, -original.rightArm.xRot);
        }
    }
}
