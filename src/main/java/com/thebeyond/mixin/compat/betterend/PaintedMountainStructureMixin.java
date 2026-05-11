package com.thebeyond.mixin.compat.betterend;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.compat.BeyondPancakeScan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Relativizes the {@code y > 50} Y threshold; reads the cached End-biome spot from
 *  {@code FeatureBaseStructureMixin} and redirects placement to it so the piece center lands
 *  on the same Voronoi cell that passed the spawn check. {@code ordinal=0} on the constant
 *  is required so the {@code randRange(50, 100)} radius bound isn't shifted. */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.structures.features.PaintedMountainStructure", remap = false)
public abstract class PaintedMountainStructureMixin {
    @ModifyExpressionValue(method = "generatePieces", at = @At(value = "CONSTANT", args = "intValue=50", ordinal = 0))
    private int the_beyond$relativizeYFloor(int original) {
        if (!BeyondTerrainState.isActive()) return original;
        int result = BeyondTerrainState.getDimMinY() + original;
        return result;
    }

    @WrapOperation(
        method = "generatePieces",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getBaseHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I")
    )
    private int the_beyond$readSpotYForPlacement(
            ChunkGenerator gen, int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random, Operation<Integer> op) {
        int original = op.call(gen, x, z, type, level, random);
        int aligned = BeyondPancakeScan.alignedY(x, z, original);
        return aligned;
    }

    @Redirect(
        method = "generatePieces",
        at = @At(value = "NEW", target = "(III)Lnet/minecraft/core/BlockPos;")
    )
    private BlockPos the_beyond$alignPieceCenter(int x, int y, int z) {
        return BeyondPancakeScan.alignedBlockPos(x, y, z);
    }
}
