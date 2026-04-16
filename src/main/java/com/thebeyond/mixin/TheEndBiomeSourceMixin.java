package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.compat.EndBiomeInjector;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects Beyond biomes into the vanilla End's TheEndBiomeSource.
 *
 * Priority 500 ensures this runs before Phantasm (900) and UnusualEnd (1000),
 * which also inject at RETURN on getNoiseBiome with cancellable=true.
 *
 * Uses erosion noise (the only non-zero noise in vanilla End) to split
 * vanilla biome zones into sub-ranges for Beyond biomes.
 *
 * Vanilla erosion ranges (from endIslands function):
 *   > 0.25        → end_highlands
 *   -0.0625..0.25 → end_midlands
 *   < -0.21875    → small_end_islands
 *   else          → end_barrens
 *
 * Beyond biome placement (sub-ranges within vanilla zones):
 *   0.35..0.5     → attracta_expanse (within highlands)
 *   >= 0.5        → peer_lands (high highlands)
 *   < -0.35       → true_void (deep small islands)
 *   -0.21875..-0.1 → the_paths (within barrens)
 */
@Mixin(value = TheEndBiomeSource.class, priority = 500)
public class TheEndBiomeSourceMixin {

    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void the_beyond$injectBiomes(int x, int y, int z, Climate.Sampler sampler,
                                          CallbackInfoReturnable<Holder<Biome>> cir) {
        // Explicit defense-in-depth gate: when Beyond's native End is the active terrain provider,
        // this injector must be a no-op. The transitive guard via vanillaEndHolders == null already
        // covers the current code path (EndBiomeInjector.injectBiomes is itself gated in
        // ServerWorldEvents), but checking BeyondTerrainState here makes the contract local and
        // resilient to a future caller that populates the holders by another route.
        if (BeyondTerrainState.isActive()) return;

        EndBiomeInjector.VanillaEndHolders holders = EndBiomeInjector.vanillaEndHolders;
        if (holders == null) return;

        Holder<Biome> original = cir.getReturnValue();

        // Skip center island
        int blockX = QuartPos.toBlock(x);
        int blockZ = QuartPos.toBlock(z);
        int sectionX = SectionPos.blockToSectionCoord(blockX);
        int sectionZ = SectionPos.blockToSectionCoord(blockZ);
        if ((long) sectionX * (long) sectionX + (long) sectionZ * (long) sectionZ <= 4096L) return;

        // Compute erosion at the same position vanilla uses
        int j1 = (sectionX * 2 + 1) * 8;
        int k1 = (sectionZ * 2 + 1) * 8;
        double erosion = sampler.erosion().compute(
                new DensityFunction.SinglePointContext(j1, 0, k1));

        if (original.is(Biomes.END_HIGHLANDS)) {
            if (erosion >= 0.35 && erosion < 0.5 && holders.attractaExpanse != null) {
                cir.setReturnValue(holders.attractaExpanse);
            } else if (erosion >= 0.5 && holders.peerLands != null) {
                cir.setReturnValue(holders.peerLands);
            }
        } else if (original.is(Biomes.SMALL_END_ISLANDS)) {
            if (erosion < -0.35 && holders.trueVoid != null) {
                cir.setReturnValue(holders.trueVoid);
            }
        } else if (original.is(Biomes.END_BARRENS)) {
            if (erosion < -0.1 && holders.thePaths != null) {
                cir.setReturnValue(holders.thePaths);
            }
        }
    }
}
