package com.thebeyond.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets isThundering be true in the End, where hasSkyLight is false, for the thunder-driven lanterns and bonfires. */
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
