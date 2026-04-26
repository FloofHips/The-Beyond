package com.thebeyond.mixin.compat.dimensional_tears;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels Dimensional Tears' {@code DimensionalTearsOceanFeature} so Beyond's
 * {@code AuroraciteLayerDTFeature} is the sole low-Y fluid authority in the End.
 * Without this, DT's 4-block ocean slab buries Beyond's auroracite islands.
 *
 * <p>Soft-targeted via {@code @Pseudo} — no-op without Dimensional Tears.
 * Does not affect DT's other features (pools, springs, obsidian patches).</p>
 */
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
