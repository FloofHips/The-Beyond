package com.thebeyond.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thebeyond.client.renderer.BlockCameraCapture;
import com.thebeyond.common.block.MirrorBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides imageless entities from Prismograph photos and from Iris's shadow pass, which also calls renderEntity. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererPhotoHideMixin {
    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void the_beyond$hideFromPhoto(Entity entity, double camX, double camY, double camZ, float partialTick,
                                          PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (BlockCameraCapture.isCapturing() && !MirrorBlock.showsInImages(entity)) {
            ci.cancel();
        }
    }
}
