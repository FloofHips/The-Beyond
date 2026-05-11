package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/** Extends {@code environment_scan}'s max_steps (cap 96) when Beyond owns the End so
 *  pancake gaps are bridged without blowing up high-count features (e.g. crying_ducts). */
@Mixin(EnvironmentScanPlacement.class)
public abstract class EnvironmentScanPlacementMixin {
    private static final int the_beyond$HARD_CAP = 96;

    @Shadow @Final private net.minecraft.core.Direction directionOfSearch;
    @Shadow @Final private BlockPredicate targetCondition;
    @Shadow @Final private BlockPredicate allowedSearchCondition;
    @Shadow @Final private int maxSteps;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void the_beyond$boundedExtendedScan(
            PlacementContext ctx, RandomSource random, BlockPos origin,
            CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!BeyondTerrainState.isActive()) return;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos().set(origin);
        int dy = directionOfSearch.getStepY();
        int minY = ctx.getMinBuildHeight();
        int maxY = ctx.getLevel().getMaxBuildHeight() - 1;
        int extended = Math.min(the_beyond$HARD_CAP, Math.max(maxSteps, maxSteps * 4));

        for (int step = 0; step < extended; step++) {
            int y = pos.getY();
            if (y < minY || y > maxY) break;
            BlockState state = ctx.getBlockState(pos);
            if (this.targetCondition.test(ctx.getLevel(), pos)) {
                cir.setReturnValue(Stream.of(pos.immutable()));
                return;
            }
            if (!this.allowedSearchCondition.test(ctx.getLevel(), pos)) {
                break;
            }
            pos.move(0, dy, 0);
        }
        cir.setReturnValue(Stream.empty());
    }
}
