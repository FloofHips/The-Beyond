package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Lowers the {@code pos.getY() > 50} walkdown floor and forces the slice-index floor
 *  result non-negative — Java's signed {@code %} on a negative {@code floor(...)} yields
 *  a negative {@code slices[index]} access (crash). */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.piece.PaintedMountainPiece", remap = false)
public abstract class PaintedMountainPieceMixin {
    @ModifyExpressionValue(
        method = "postProcess",
        at = @At(value = "CONSTANT", args = "intValue=50")
    )
    private int the_beyond$lowerWalkdownFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY();
        return result;
    }

    @ModifyExpressionValue(
        method = "postProcess",
        at = @At(value = "INVOKE", target = "Lorg/betterx/bclib/util/MHelper;floor(F)I")
    )
    private int the_beyond$nonNegativeFloorForSliceIndex(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = original < 0 ? -original : original;
        return result;
    }
}
