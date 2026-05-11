package com.thebeyond.mixin.compat.dimensional_tears;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels DT's {@code DimensionalTearsOceanFeature} (a 4-block ocean slab that buries
 *  Beyond's auroracite islands). Beyond's {@code AuroraciteLayerDTFeature} owns low-Y fluid. */
@Pseudo
@Mixin(targets = "com.ordana.dimensional_tears.worldgen_features.DimensionalTearsOceanFeature")
public abstract class DimensionalTearsOceanFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true, remap = false)
    private void the_beyond$cancelDTOceanLayer(
            FeaturePlaceContext<?> context,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
