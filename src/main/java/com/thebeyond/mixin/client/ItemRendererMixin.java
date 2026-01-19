package com.thebeyond.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Redirect(
            method = "renderQuadList",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIZ)V"
            )
    )
    private void applyDynamicColorEffect(VertexConsumer instance, PoseStack.Pose pose, BakedQuad quad, float red, float green, float blue, float alpha, int packedLight, int packedOverlay, boolean readExistingColor, @Local(argsOnly = true) ItemStack itemStack) {
        Components.DynamicColorComponent color = itemStack.get(BeyondComponents.COLOR_COMPONENT.get());

        if (color == null) {
          instance.putBulkData(pose, quad, red, green, blue, alpha, packedLight, packedOverlay, readExistingColor);
            return;
        }

        float time = (float) Math.sin(System.currentTimeMillis() * 0.001);

        instance.putBulkData(pose, quad,
                color.red() + time * color.roffset(),
                color.green() + time * color.goffset(),
                color.blue() + time * color.boffset(),
                color.alpha() + time * color.aoffset(),
                color.brightness(),
                packedOverlay,
                true
        );
    }
}