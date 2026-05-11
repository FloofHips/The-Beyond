package com.thebeyond.mixin;

import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vetoes foreign block writes at the End's auroracite floor. Whitelist: Beyond's
 *  auroracite, plus DT's source fluid when DT is loaded. */
@Mixin(WorldGenRegion.class)
public abstract class AuroraciteLayerProtectionMixin {

    private static final ResourceLocation DT_FLUID_ID = ResourceLocation.parse("dimensional_tears:dimensional_tears");
    private static volatile Block dtFluidBlock;
    private static volatile boolean dtFluidResolved;

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void the_beyond$protectAuroraciteLayer(
            BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {

        int y = pos.getY();
        WorldGenRegion self = (WorldGenRegion) (Object) this;
        int minY = self.getMinBuildHeight();

        // Fast exit: only the 2-block auroracite layer at the dimension floor.
        if (y != minY && y != minY + 1) return;

        // Only in the End dimension.
        if (self.getLevel().dimension() != Level.END) return;

        // Allow Beyond's auroracite — its own features write here.
        if (state.is(BeyondBlocks.AURORACITE.get())) return;

        // Allow DT fluid when Dimensional Tears is loaded — Beyond's DT feature writes it here.
        Block dt = the_beyond$resolveDTFluid();
        if (dt != null && state.is(dt)) return;

        // Veto — anything else is foreign and would create a hole in the auroracite layer.
        cir.setReturnValue(false);
    }

    private static Block the_beyond$resolveDTFluid() {
        if (dtFluidResolved) return dtFluidBlock;
        Block resolved = null;
        Block lookup = BuiltInRegistries.BLOCK.get(DT_FLUID_ID);
        if (lookup != null && lookup != Blocks.AIR) {
            resolved = lookup;
        }
        dtFluidBlock = resolved;
        dtFluidResolved = true;
        return resolved;
    }
}
