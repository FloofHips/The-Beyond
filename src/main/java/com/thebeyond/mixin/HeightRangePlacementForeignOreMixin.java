package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.ForeignOreHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(HeightRangePlacement.class)
public abstract class HeightRangePlacementForeignOreMixin {
    @Shadow @Final private HeightProvider height;

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void the_beyond$foreignOreOnIslands(PlacementContext ctx, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Stream<BlockPos>> cir) {
        if (!ForeignOreHeight.applies(ctx)) return;
        cir.setReturnValue(Stream.of(pos.atY(ForeignOreHeight.sample(height, random, ctx))));
    }
}
