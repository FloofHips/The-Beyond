package com.thebeyond.mixin;

import com.thebeyond.common.block.VoidFlameBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin extends Block {
    public BaseFireBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Inject(method = "getState", at = @At("HEAD"), cancellable = true)
    private static void beyond$getState(BlockGetter reader, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (VoidFlameBlock.canSurviveOnBlock(reader.getBlockState(pos.below()))) {
            cir.setReturnValue(BeyondBlocks.VOID_FLAME.get().defaultBlockState());
        }
    }
}
