package com.thebeyond.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes isThundering() in the End dimension.
 *
 * Vanilla checks dimensionType().hasSkyLight() which is false for the End,
 * causing isThundering() to always return false. This breaks Beyond entity
 * goals (lantern migration, bonfire splits) that depend on thunder.
 */
@Mixin(Level.class)
public class EndWeatherMixin {

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void the_beyond$allowEndThunder(CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (self.dimension() == Level.END) {
            cir.setReturnValue((double) self.getThunderLevel(1.0F) > 0.9);
        }
    }
}
