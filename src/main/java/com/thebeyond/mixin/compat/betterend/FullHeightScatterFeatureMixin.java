package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** {@code FullHeightScatterFeature.place} uses surface heightmap (topmost pancake only);
 *  use the placement origin Y when Beyond is active. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.features.FullHeightScatterFeature", remap = false)
public abstract class FullHeightScatterFeatureMixin {
    @WrapOperation(
        method = "place",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"
        )
    )
    private int the_beyond$preserveY(
            LevelReader world, Heightmap.Types type, int x, int z,
            Operation<Integer> op,
            @Local(ordinal = 0) BlockPos center) {
        if (BeyondTerrainState.isActive()) {
            return center.getY();
        }
        return op.call(world, type, x, z);
    }
}
