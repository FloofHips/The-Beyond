package com.thebeyond.mixin.compat.sable;

import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter", remap = false)
public class SableEntityGetterLogThrottleMixin {

    private static volatile long the_beyond$lastLogMillis = 0L;

    @Inject(method = "logError", at = @At("HEAD"), cancellable = true, remap = false)
    private static void the_beyond$throttleAabbAbortLog(final AABB aabb, final CallbackInfo ci) {
        long now = System.currentTimeMillis();
        if (now - the_beyond$lastLogMillis < 5000L) {
            ci.cancel();
            return;
        }
        the_beyond$lastLogMillis = now;
    }
}
