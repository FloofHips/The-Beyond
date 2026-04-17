package com.thebeyond.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Protects the auroracite layer ({@code minY}, {@code minY+1}) from being overwritten by
 * foreign mod features during world generation in the End dimension.
 *
 * <h2>Problem</h2>
 * Stellarity's frozen biomes (frozen_shrublands, frozen_spikes, frozen_marsh) place packed_ice
 * and other ice blocks during feature decoration at steps later than {@code raw_generation}.
 * Since the auroracite layer is placed at step 0 ({@code raw_generation}), these ice features
 * overwrite it, replacing the distinctive auroracite floor with packed_ice.
 *
 * <h2>Why not change the step?</h2>
 * Moving auroracite to {@code top_layer_modification} (step 10) causes:
 * <ul>
 *   <li>Feature order cycles with {@code ceiling_void_crystal} in the {@code true_void} biome</li>
 *   <li>Broken generation in non-Beyond terrain and Enderscape combo scenarios</li>
 * </ul>
 *
 * <h2>Why not post-decoration restoration?</h2>
 * A post-decoration pass in {@code BeyondEndChunkGenerator} that re-stamps auroracite would
 * also overwrite legitimate terrain blocks (island bases extending down to the floor),
 * clipping natural terrain formations.
 *
 * <h2>Solution</h2>
 * Intercept {@link WorldGenRegion#setBlock} at HEAD. When ALL of these conditions are met,
 * the call is vetoed (returns {@code false}):
 * <ol>
 *   <li>Y coordinate is {@code minBuildHeight} or {@code minBuildHeight + 1}</li>
 *   <li>The block being placed is an ice/snow variant (packed_ice, ice, blue_ice, snow_block)</li>
 *   <li>The dimension is {@code minecraft:the_end}</li>
 * </ol>
 *
 * <p>The Y check runs first and rejects &gt;99.9% of calls immediately — no measurable
 * performance impact. The auroracite feature itself places {@code BeyondBlocks.AURORACITE},
 * not ice, so it passes through unaffected.</p>
 */
@Mixin(WorldGenRegion.class)
public abstract class AuroraciteLayerProtectionMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void the_beyond$protectAuroraciteFromFrozenBiomes(
            BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {

        int y = pos.getY();
        WorldGenRegion self = (WorldGenRegion) (Object) this;
        int minY = self.getMinBuildHeight();

        // Fast exit: only the 2-block auroracite layer at the dimension floor.
        if (y != minY && y != minY + 1) return;

        // Only block ice/snow variants (Stellarity frozen biomes).
        if (!state.is(Blocks.PACKED_ICE) && !state.is(Blocks.ICE)
                && !state.is(Blocks.BLUE_ICE) && !state.is(Blocks.SNOW_BLOCK)) return;

        // Only in the End dimension.
        if (self.getLevel().dimension() != Level.END) return;

        // Veto — the auroracite layer is protected.
        cir.setReturnValue(false);
    }
}
