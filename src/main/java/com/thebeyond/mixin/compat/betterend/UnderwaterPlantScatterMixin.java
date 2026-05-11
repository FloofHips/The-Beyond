package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Removes the Y=0 scan-start and Y=128 scan-cap in BetterEnd's underwater plant scatter so
 *  algae/lotus/charnia find water in pancake lakes outside the vanilla End's surface band. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.UnderwaterPlantScatter", remap = false)
public abstract class UnderwaterPlantScatterMixin {
    @ModifyExpressionValue(method = "getCenterGround", at = @At(value = "CONSTANT", args = "intValue=0"))
    private int the_beyond$scanStart(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY();
        return result;
    }

    @ModifyExpressionValue(method = "getGround", at = @At(value = "CONSTANT", args = "intValue=128"))
    private int the_beyond$scanCap(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMaxY();
        return result;
    }

    @ModifyExpressionValue(method = "getGroundPlant", at = @At(value = "CONSTANT", args = "intValue=128"))
    private int the_beyond$plantYCap(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMaxY();
        return result;
    }
}
