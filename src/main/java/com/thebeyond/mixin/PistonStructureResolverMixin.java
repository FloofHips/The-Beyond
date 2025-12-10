package com.thebeyond.mixin;

import com.thebeyond.common.block.FerroJellyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

// Inspired by carpet mod, thank you!
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {


    @Inject(
            method = "addBlockLine",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddBlockLine(BlockPos originPos, Direction direction,
                                CallbackInfoReturnable<Boolean> cir) {
    }
}