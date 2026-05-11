package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Relativizes the {@code if (blockPos.getY() < 5) return false} guard in Wover's
 *  {@code SingleEndPoolElement.place} to {@code dimMinY + 5}. */
@Pseudo
@Mixin(targets = "org.betterx.wover.structure.impl.pools.SingleEndPoolElement", remap = false)
public abstract class SingleEndPoolElementMixin {
    @ModifyExpressionValue(method = "place", at = @At(value = "CONSTANT", args = "intValue=5"))
    private int the_beyond$relativizeYFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }
}
