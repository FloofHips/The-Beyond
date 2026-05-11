package com.thebeyond.mixin.compat.betterend;

import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Cancels MegaLakeStructure when Beyond owns the End — fixed-size carve bleeds across
 *  pancakes. {@code PancakeLakeFeature} replaces it with island-aware sizing. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.MegaLakeStructure", remap = false)
public abstract class MegaLakeStructureMixin {
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true, remap = true)
    private void the_beyond$bailFindGenerationPoint(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (BeyondTerrainState.isActive()) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
