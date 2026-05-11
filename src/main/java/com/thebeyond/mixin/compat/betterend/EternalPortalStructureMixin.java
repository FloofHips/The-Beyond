package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Relativizes the {@code y < 5} early-skip threshold in {@code findGenerationPoint}
 *  to {@code dimMinY + 5}. Portal spawns at the real heightmap Y. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.EternalPortalStructure", remap = false)
public abstract class EternalPortalStructureMixin {
    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "CONSTANT", args = "intValue=5"))
    private int the_beyond$relativizeYFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }
}
