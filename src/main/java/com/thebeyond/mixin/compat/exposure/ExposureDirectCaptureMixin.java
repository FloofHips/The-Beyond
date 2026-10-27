package com.thebeyond.mixin.compat.exposure;

import com.thebeyond.compat.exposure.client.ExposureClientCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Exposure's direct capture (its default with Veil) screenshots a live frame, so imageless entities hide until the shot. */
@Pseudo
@Mixin(targets = "io.github.mortuusars.exposure.client.capture.task.DirectScreenshotCaptureTask", remap = false)
public abstract class ExposureDirectCaptureMixin {
    @Inject(method = "execute", at = @At("RETURN"), remap = false)
    private void the_beyond$trackShot(CallbackInfoReturnable<CompletableFuture<?>> cir) {
        ExposureClientCompat.trackDirectCapture(cir.getReturnValue());
    }
}
