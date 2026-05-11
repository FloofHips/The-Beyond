package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** {@code SpireFeature.place}'s WORLD_SURFACE_WG lookup collapses to the topmost pancake;
 *  use the placement origin Y when Beyond owns the End. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.SpireFeature", remap = false)
public abstract class SpireFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/betterend/world/features/DefaultFeature;getPosOnSurfaceWG(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            remap = false
        )
    )
    private BlockPos the_beyond$preserveOrigin(
            BlockPos original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin() : original;
    }
}
