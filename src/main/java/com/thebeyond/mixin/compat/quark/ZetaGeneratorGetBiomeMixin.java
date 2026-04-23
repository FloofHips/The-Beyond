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

/**
 * Replaces Zeta {@code Generator#getBiome}'s "sample at world top" branch with
 * "sample at terrain-surface Y" whenever the generator runs in an End-type
 * dimension.
 *
 * <p>Problem: Zeta's helper, when called with {@code offset=true}, relocates
 * the sample to {@code world.getMaxBuildHeight() - 1}. In vanilla End this is
 * harmless (biomes are 2D). In Beyond's End the biome source is 3D — at the
 * world top {@code getTerrainDensity} is ~0, so every sample returns an
 * {@code outerVoidBiomeList} entry. Quark generators that gate spawning on a
 * specific End biome (ChorusVegetation's {@code end_highlands} check,
 * BigStoneClusters' {@code myalite} whitelist, etc.) can never match, so their
 * content silently never spawns regardless of user config.</p>
 *
 * <p>Scope: only rewrites the {@code offset=true} path, only in End-type dims
 * (natural=false, hasCeiling=false, ultraWarm=false). Overworld, Nether, and
 * any ceilinged/warm custom dim are untouched.</p>
 *
 * <p>Soft-targeted via {@code @Pseudo} — no-op without Quark/Zeta.</p>
 */
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
