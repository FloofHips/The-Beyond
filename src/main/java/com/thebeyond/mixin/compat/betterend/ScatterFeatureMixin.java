package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code getCenterGround} re-resolves Y via {@code WORLD_SURFACE_WG}, collapsing to the
 * topmost pancake. Restore the placement origin Y when Beyond owns the End.
 */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.ScatterFeature", remap = false)
public abstract class ScatterFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/betterend/world/features/ScatterFeature;getCenterGround(Lorg/betterx/betterend/world/features/ScatterFeatureConfig;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            remap = false
        )
    )
    private BlockPos the_beyond$preserveCenterY(
            BlockPos original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin() : original;
    }

    @WrapOperation(
        method = "canSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        )
    )
    private int the_beyond$bypassYGate(BlockPos pos, Operation<Integer> op) {
        return BeyondTerrainState.isActive() ? Integer.MAX_VALUE : op.call(pos);
    }
}
