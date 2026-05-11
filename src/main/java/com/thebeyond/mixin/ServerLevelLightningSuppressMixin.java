package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Suppresses lightning spawn in the End via the {@code isThundering()} check inside
 *  {@code tickChunk}; random ticks and ice/snow logic are preserved. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelLightningSuppressMixin {
    @WrapOperation(
        method = "tickChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z")
    )
    private boolean the_beyond$noThunderInEnd(ServerLevel self, Operation<Boolean> original) {
        if (self.dimension() == Level.END) return false;
        return original.call(self);
    }
}
