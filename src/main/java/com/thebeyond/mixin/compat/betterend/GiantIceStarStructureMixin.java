package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Remaps the hard-coded {@code randRange(32, 128)} Y picker proportionally to
 *  {@code [12.5%, 75%]} of the active dim so the star roams across the dim's vertical span. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.GiantIceStarStructure", remap = false)
public abstract class GiantIceStarStructureMixin {
    @WrapOperation(
        method = "generatePieces",
        at = @At(value = "INVOKE", target = "Lorg/betterx/bclib/util/MHelper;randRange(IILnet/minecraft/util/RandomSource;)I")
    )
    private int the_beyond$rebaseYRange(int min, int max, RandomSource random, Operation<Integer> op) {
        int original = op.call(min, max, random);
        if (!BeyondTerrainState.isActive()) return original;
        // Only the (32, 128) Y picker is rebased; the (4, 12) chunk-local X/Z picks pass through.
        if (min == 32 && max == 128) {
            int dimMinY = BeyondTerrainState.getDimMinY();
            int dimMaxY = BeyondTerrainState.getDimMaxY();
            int range = dimMaxY - dimMinY;
            int newMin = dimMinY + range / 8;
            int newMax = dimMinY + (range * 3) / 4;
            int result = newMin + (original - 32) * (newMax - newMin) / 96;
            return result;
        }
        return original;
    }
}
