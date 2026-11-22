package com.thebeyond.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.client.renderer.DynamicColorConsumer;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"
            )
    )
    private void the_beyond$applyDynamicColor(ItemRenderer self, BakedModel model, ItemStack stack, int light, int overlay,
                                              PoseStack poseStack, VertexConsumer consumer, Operation<Void> original) {
        Components.DynamicColorComponent color = stack.get(BeyondComponents.COLOR_COMPONENT.get());
        original.call(self, model, stack, light, overlay, poseStack, color == null ? consumer : new DynamicColorConsumer(consumer, color));
    }
}
