package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.block.FerroJellyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

// Inspired by carpet mod, thank you!
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private Direction pushDirection;

    @WrapOperation(method = "addBlockLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0))
    private boolean onAddBlockLineCanStickToEachOther(BlockState state, BlockState behindState, Operation<Boolean> original, @Local(ordinal = 1) BlockPos behindPos) {
        if (state.getBlock() instanceof FerroJellyBlock ferroJellyBlock) {
            return ferroJellyBlock.canStickTo(level, behindPos.relative(pushDirection), state, behindPos, behindState, pushDirection.getOpposite(), pushDirection);
        }

        return original.call(state, behindState);
    }

    @WrapOperation(method = "addBlockLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 1))
    private boolean removeSecondBlockLineCheck(BlockState state, BlockState behindState, Operation<Boolean> original) {
        return true;
    }

    @WrapOperation(method = "addBranchingBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0))
    private boolean onAddBranchingBlocksCanStickToEachOther(BlockState neighborState, BlockState state, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos, @Local(ordinal = 1) BlockPos neighborPos, @Local Direction direction) {
        if (state.getBlock() instanceof FerroJellyBlock ferroJellyBlock) {
            return ferroJellyBlock.canStickTo(level, pos, state, neighborPos, neighborState, pushDirection.getOpposite(), pushDirection);
        }

        return original.call(neighborState, state);
    }

    @WrapOperation(method = "addBranchingBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 1))
    private boolean removeSecondBranchingBlockCheck(BlockState neighborState, BlockState state, Operation<Boolean> original) {
        return true;
    }
}