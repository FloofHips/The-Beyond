package com.thebeyond.mixin;

import net.minecraft.world.level.levelgen.feature.treedecorators.CocoaDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guards vanilla {@code CocoaDecorator.place} against IOOBE when {@code logs} is empty. */
@Mixin(CocoaDecorator.class)
public class CocoaDecoratorEmptyLogsMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void the_beyond$skipIfNoLogs(TreeDecorator.Context ctx, CallbackInfo ci) {
        if (ctx.logs().isEmpty()) ci.cancel();
    }
}
