package com.thebeyond.mixin.compat.quark;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redirects Zeta's {@code offset=true} world-top biome sample to terrain-surface Y in End
 *  dims only. Beyond's 3D biome source returns {@code outerVoidBiomeList} at world top, so
 *  Quark generators that gate on a specific End biome would never match. Scoped to
 *  natural/non-ceiling/non-warm dims. */
@Pseudo
@Mixin(targets = "org.violetmoon.zeta.world.generator.Generator", remap = false)
public abstract class ZetaGeneratorGetBiomeMixin {

    @Inject(
        method = "getBiome(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Z)Lnet/minecraft/core/Holder;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void the_beyond$sampleAtSurfaceInEnd(
            LevelAccessor world, BlockPos pos, boolean offset,
            CallbackInfoReturnable<Holder<Biome>> cir) {

        if (!offset) return; // Generator's offset=false path is already correct.

        // Restrict to End-type dimensions. DimensionType lacks a direct isEnd(),
        // so use the established heuristic (matches vanilla End + typical modded
        // End dims). Overworld is natural=true; Nether has a ceiling + ultraWarm.
        DimensionType dim = world.dimensionType();
        if (dim.natural() || dim.hasCeiling() || dim.ultraWarm()) return;

        if (!(world instanceof LevelReader lr)) return;

        int surfaceY = lr.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        BlockPos sample = new BlockPos(pos.getX(), surfaceY, pos.getZ());
        cir.setReturnValue(world.getBiomeManager().getBiome(sample));
    }
}
