package com.thebeyond.client.renderer.renderlayers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thebeyond.client.model.EnadrakeModel;
import com.thebeyond.common.entity.EnadrakeEntity;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EnadrakeItemLayer extends RenderLayer<EnadrakeEntity, EnadrakeModel<EnadrakeEntity>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public EnadrakeItemLayer(RenderLayerParent<EnadrakeEntity, EnadrakeModel<EnadrakeEntity>> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, EnadrakeEntity livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
       poseStack.pushPose();

        poseStack.translate(((EnadrakeModel)this.getParentModel()).head.x / 16.0F, ((EnadrakeModel)this.getParentModel()).head.y / 16.0F, ((EnadrakeModel)this.getParentModel()).head.z / 16.0F);
        //float f1 = livingEntity.getHeadRollAngle(partialTicks);

        poseStack.mulPose(Axis.YP.rotationDegrees(netHeadYaw));

        poseStack.translate(0, 1F, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(headPitch));
        poseStack.mulPose(Axis.ZP.rotation(Mth.PI));
        //poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));

        ItemStack itemstack = livingEntity.getItemBySlot(EquipmentSlot.MAINHAND);
        this.itemInHandRenderer.renderItem(livingEntity, itemstack, ItemDisplayContext.GROUND, false, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
