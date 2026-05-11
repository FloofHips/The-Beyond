package com.thebeyond.mixin.compat.sable;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState", remap = false)
public abstract class SableVoxelNeighborhoodStateMixin {

    @Inject(method = "isLiquid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void the_beyond$treatLiquidBlockAsLiquid(final BlockState state, final CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof LiquidBlock && !state.getFluidState().isEmpty()) {
            cir.setReturnValue(true);
        }
    }
}
