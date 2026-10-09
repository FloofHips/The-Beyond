package com.thebeyond.mixin.compat.betterfog;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.client.renderer.RenderFailureLog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Better Fog redoes vanilla's night vision fog math in its own copy, so that copy gets FogRendererMixin's factor too. */
@Pseudo
@Mixin(targets = "net.cloud.betterfog.CustomFogRenderer", remap = false)
public abstract class BetterFogNightVisionMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(FF)F", ordinal = 1))
    private static float theBeyond$nightVisionKeepsSign(float a, float b, Operation<Float> original) {
        if (Float.floatToRawIntBits(a) != Float.floatToRawIntBits(1.0F / fogRed)) {
            RenderFailureLog.error("[TheBeyond] Better Fog night vision fix skipped an unexpected Math.min", new IllegalStateException("a=" + a));
            return original.call(a, b);
        }
        if (fogRed < 0 || fogGreen < 0 || fogBlue < 0) {
            float big = Math.max(Math.abs(fogRed), Math.max(Math.abs(fogGreen), Math.abs(fogBlue)));
            return big >= Float.MIN_NORMAL ? 1.0F / big : 1.0F;
        }
        return original.call(a, b);
    }
}
