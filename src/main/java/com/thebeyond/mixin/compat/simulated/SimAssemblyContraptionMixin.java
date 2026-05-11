package com.thebeyond.mixin.compat.simulated;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Whitelists Beyond's actor blocks past Simulated's {@code destroySpeed == -1} hard reject. */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption", remap = false)
public abstract class SimAssemblyContraptionMixin {

    @Inject(method = "movementAllowed", at = @At("HEAD"), cancellable = true, remap = false)
    private void the_beyond$whitelist(BlockState state, Level world, BlockPos pos,
                                       CallbackInfoReturnable<Boolean> cir) {
        Block b = state.getBlock();
        if (b == BeyondBlocks.MEMOR_FAUCET.get()
                || b == BeyondBlocks.BONFIRE.get()
                || b == BeyondBlocks.ENADRAKE_HUT.get()
                || b == BeyondBlocks.REFUGE.get()) {
            cir.setReturnValue(true);
        }
    }
}
