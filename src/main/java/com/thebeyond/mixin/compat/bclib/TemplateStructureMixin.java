package com.thebeyond.mixin.compat.bclib;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Lowers BCLib's {@code findGenerationPoint} sea-level floor to {@code dimMinY} so the
 *  floor/ceiling search loop covers pancakes below vanilla sea level. {@code MIN_VALUE}
 *  is unsafe: the loop iterates {@code y += searchStep} from seaLevel and would never
 *  terminate. */
@Pseudo
@Mixin(targets = "org.betterx.bclib.api.v2.levelgen.structures.TemplateStructure", remap = false)
public abstract class TemplateStructureMixin {
    @ModifyExpressionValue(
        method = "findGenerationPoint",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getSeaLevel()I"
        )
    )
    private int the_beyond$lowerSeaLevelFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY();
        return result;
    }
}
