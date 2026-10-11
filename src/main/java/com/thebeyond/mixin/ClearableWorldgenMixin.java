package com.thebeyond.mixin;

import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips clearing a block entity just built from a DUMMY tag, which has no level, so a jukebox's clear would throw. */
@Mixin(Clearable.class)
public interface ClearableWorldgenMixin {

    @Inject(method = "tryClear", at = @At("HEAD"), cancellable = true)
    private static void the_beyond$skipLevellessBlockEntity(Object target, CallbackInfo ci) {
        if (target instanceof BlockEntity be && !be.hasLevel()) ci.cancel();
    }
}
