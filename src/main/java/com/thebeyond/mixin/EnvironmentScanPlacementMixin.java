package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.ScanSkip;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/** Raises environment_scan's max_steps (cap 96) on Beyond's End so layer gaps are bridged. */
@Mixin(EnvironmentScanPlacement.class)
public abstract class EnvironmentScanPlacementMixin {
    private static final int the_beyond$HARD_CAP = 96;

    @Shadow @Final private net.minecraft.core.Direction directionOfSearch;
    @Shadow @Final private BlockPredicate targetCondition;
    @Shadow @Final private BlockPredicate allowedSearchCondition;
    @Shadow @Final private int maxSteps;
    @Unique private volatile int[] the_beyond$reads;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void the_beyond$boundedExtendedScan(
            PlacementContext ctx, RandomSource random, BlockPos origin,
            CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!(ctx.generator() instanceof BeyondEndChunkGenerator)) return;

        WorldGenLevel level = ctx.getLevel();
        int[] reads = this.the_beyond$reads;
        if (reads == null) {
            reads = ScanSkip.reads(level.registryAccess(), this.targetCondition, this.allowedSearchCondition);
            this.the_beyond$reads = reads;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos().set(origin);
        int dy = directionOfSearch.getStepY();
        int minY = ctx.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int extended = Math.min(the_beyond$HARD_CAP, Math.max(maxSteps, maxSteps * 4));

        for (int step = 0; step < extended; step++) {
            int y = pos.getY();
            if (y < minY || y > maxY) break;
            if (this.targetCondition.test(level, pos)) {
                cir.setReturnValue(Stream.of(pos.immutable()));
                return;
            }
            if (!this.allowedSearchCondition.test(level, pos)) {
                break;
            }
            if (reads.length > 0) step += ScanSkip.skip(level, pos, dy, reads, minY, maxY, extended - 1 - step);
            pos.move(0, dy, 0);
        }
        cir.setReturnValue(Stream.empty());
    }
}
