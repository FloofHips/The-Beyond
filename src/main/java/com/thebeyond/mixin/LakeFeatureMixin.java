package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.worldgen.GellidLakeFill;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records what a gellid lake by a structure wrote and what the veto kept out, for GellidLakeFill to settle later. */
@Mixin(LakeFeature.class)
public abstract class LakeFeatureMixin {

    // After the shape is drawn, before any read or write.
    @Inject(method = "place", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider;"
                    + "getState(Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"))
    private void the_beyond$armGellidLake(FeaturePlaceContext<LakeFeature.Configuration> context,
            CallbackInfoReturnable<Boolean> cir, @Local boolean[] shape, @Share("lake") LocalRef<GellidLakeFill.Lake> lake) {
        LakeFeature.Configuration c = context.config();
        if (!FeatureGuard.isArmed() || !(c.fluid() instanceof SimpleStateProvider)
                || !c.fluid().getState(context.random(), context.origin()).is(BeyondBlocks.GELLID_VOID.get())) {
            return;
        }
        lake.set(new GellidLakeFill.Lake(context.origin().below(4), shape));
    }

    // The guard still decides each write, so later lakes and layer scans see the world unchanged.
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean the_beyond$recordGellidLake(WorldGenLevel level, BlockPos pos, BlockState state, int flags,
            Operation<Boolean> original, @Share("lake") LocalRef<GellidLakeFill.Lake> lake) {
        GellidLakeFill.Lake l = lake.get();
        if (l == null) return original.call(level, pos, state, flags);
        BlockState found = level.getBlockState(pos);
        boolean written = original.call(level, pos, state, flags);
        l.record(pos, state, found, written);
        return written;
    }

    @Inject(method = "place", at = @At("RETURN"))
    private void the_beyond$holdGellidLake(FeaturePlaceContext<LakeFeature.Configuration> context,
            CallbackInfoReturnable<Boolean> cir, @Share("lake") LocalRef<GellidLakeFill.Lake> lake) {
        GellidLakeFill.Lake l = lake.get();
        if (l != null) GellidLakeFill.hold(context.level(), l, cir.getReturnValue());
    }
}
