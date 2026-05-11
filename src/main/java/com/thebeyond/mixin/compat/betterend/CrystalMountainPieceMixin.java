package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Relativizes the absolute Y thresholds in Crystal Mountain placement to {@code dimMinY + N}.
 *  Inert while {@code FeatureBaseStructureMixin} redirects {@code mountain} to a zero-piece
 *  stub; kept so the original placement still works if the redirect ever returns the structure. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.piece.CrystalMountainPiece", remap = false)
public abstract class CrystalMountainPieceMixin {
    @ModifyExpressionValue(method = "postProcess", at = @At(value = "CONSTANT", args = "intValue=60"))
    private int the_beyond$bigCrystalYFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }

    @ModifyExpressionValue(method = "postProcess", at = @At(value = "CONSTANT", args = "intValue=20"))
    private int the_beyond$smallCrystalYFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }

    @ModifyExpressionValue(method = "placeMountain", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int the_beyond$placeMountainMinY(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }

    @ModifyExpressionValue(method = "placeMountain", at = @At(value = "CONSTANT", args = "intValue=56"))
    private int the_beyond$placeMountainSearchFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }
}
