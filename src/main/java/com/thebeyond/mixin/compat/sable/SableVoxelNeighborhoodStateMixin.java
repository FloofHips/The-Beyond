package com.thebeyond.mixin.compat.sable;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
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

    /** Auroracite non-solid for Sable physics so contraptions pass through; vanilla entity collision is untouched. */
    @Inject(method = "isSolid", at = @At("HEAD"), cancellable = true, remap = false)
    private static void the_beyond$auroraciteNonSolidForPhysics(final BlockGetter blockGetter, final BlockPos pos,
                                                                final BlockState state,
                                                                final CallbackInfoReturnable<Boolean> cir) {
        if (state.is(BeyondBlocks.AURORACITE.get())) {
            cir.setReturnValue(false);
        }
    }
}
