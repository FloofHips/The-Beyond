package com.thebeyond.mixin;

import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guards vanilla {@code BeehiveDecorator.place} against IOOBE when {@code logs} is empty. */
@Mixin(BeehiveDecorator.class)
public class BeehiveDecoratorEmptyLogsMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void the_beyond$skipIfNoLogs(TreeDecorator.Context ctx, CallbackInfo ci) {
        if (ctx.logs().isEmpty()) ci.cancel();
    }
}
