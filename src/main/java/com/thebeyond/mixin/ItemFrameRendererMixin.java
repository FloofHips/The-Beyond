package com.thebeyond.mixin;

import com.thebeyond.common.item.SnapshotItem;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrameRenderer.class)
public class ItemFrameRendererMixin<T extends ItemFrame> {

    @Shadow @Final private static ModelResourceLocation FRAME_LOCATION;
    @Shadow @Final private static ModelResourceLocation MAP_FRAME_LOCATION;
    @Shadow @Final private static ModelResourceLocation GLOW_FRAME_LOCATION;
    @Shadow @Final private static ModelResourceLocation GLOW_MAP_FRAME_LOCATION;

    @Inject(method = "getFrameModelResourceLoc", at = @At("HEAD"), cancellable = true)
    private void beyond$getFrameModelResourceLoc(T entity, ItemStack item, CallbackInfoReturnable<ModelResourceLocation> cir) {
        boolean flag = entity.getType() == EntityType.GLOW_ITEM_FRAME;
        ModelResourceLocation result;

        if (item.getItem() instanceof SnapshotItem) {
            result = flag ? GLOW_MAP_FRAME_LOCATION : MAP_FRAME_LOCATION;
        } else {
            result = flag ? GLOW_FRAME_LOCATION : FRAME_LOCATION;
        }
        cir.setReturnValue(result);
    }
}
