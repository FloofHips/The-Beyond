package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Same Y-collapse fix as {@link SpireFeatureMixin}, applied to {@code FloatingSpireFeature.place}'s
 * call to {@code DefaultFeature.getYOnSurface(level, x, z)}.
 */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.terrain.FloatingSpireFeature", remap = false)
public abstract class FloatingSpireFeatureMixin {
    @ModifyExpressionValue(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lorg/betterx/betterend/world/features/DefaultFeature;getYOnSurface(Lnet/minecraft/world/level/WorldGenLevel;II)I",
            remap = false
        )
    )
    private int the_beyond$preserveOriginY(
            int original,
            @Local(argsOnly = true) FeaturePlaceContext<?> ctx) {
        return BeyondTerrainState.isActive() ? ctx.origin().getY() : original;
    }
}
