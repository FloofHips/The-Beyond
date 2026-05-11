package com.thebeyond.mixin.compat.betterend;

import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Same rationale as {@link MegaLakeStructureMixin}: cancel BetterEnd's small variant
 * so PancakeLakeFeature handles all megalake-biome lakes with adaptive sizing.
 */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.MegaLakeSmallStructure", remap = false)
public abstract class MegaLakeSmallStructureMixin {
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true, remap = true)
    private void the_beyond$bail(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (BeyondTerrainState.isActive()) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
