package com.thebeyond.mixin.client;

import com.thebeyond.client.renderer.blockentities.ProjectorDepthMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renderers that fetch the main buffers instead of the one handed to them (GeckoLib armor) draw into the projector capture. */
@Mixin(RenderBuffers.class)
public abstract class RenderBuffersCaptureMixin {
    @Inject(method = "bufferSource", at = @At("HEAD"), cancellable = true)
    private void the_beyond$captureBufferSource(CallbackInfoReturnable<MultiBufferSource.BufferSource> cir) {
        MultiBufferSource.BufferSource redirect = ProjectorDepthMap.captureRedirect();
        if (redirect != null) {
            cir.setReturnValue(redirect);
        }
    }

    @Inject(method = "outlineBufferSource", at = @At("HEAD"), cancellable = true)
    private void the_beyond$captureOutlineBufferSource(CallbackInfoReturnable<OutlineBufferSource> cir) {
        OutlineBufferSource redirect = ProjectorDepthMap.captureOutlineRedirect();
        if (redirect != null) {
            cir.setReturnValue(redirect);
        }
    }
}
