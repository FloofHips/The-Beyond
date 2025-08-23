package com.thebeyond.mixin;

import com.thebeyond.common.registry.BeyondSoundTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public class BlockBehaviourMixin {

    @Inject(at = @At("HEAD"), method = "getSoundType", cancellable = true)
    public void getSoundType(BlockState state, CallbackInfoReturnable<SoundType> cir) {
        if (state.is(Blocks.END_STONE)) cir.setReturnValue(BeyondSoundTypes.END_STONE);
    }
}
