package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.feature.EndPodiumFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels vanilla podium in the Beyond+BetterEnd+Stellarity combo. Stellarity places it
 *  via mcfunction; BetterEnd rewrites the vanilla origin to WORLD_SURFACE (~y=260). */
@Mixin(EndPodiumFeature.class)
public class EndPodiumFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void the_beyond$skipVanillaPodiumInStellarityCombo(
            FeaturePlaceContext<NoneFeatureConfiguration> ctx,
            CallbackInfoReturnable<Boolean> cir) {

        if (!BeyondTerrainState.isActive()) {
            return;
        }
        if (!ModList.get().isLoaded("stellarity") || !ModList.get().isLoaded("betterend")) {
            return;
        }

        // Three-way combo with Beyond terrain active: Stellarity owns the exit portal.
        // Silently drop the vanilla placement — the duplicate bedrock/egg in the sky from
        // BetterEnd's position rewrite is exactly what we don't want.
        cir.setReturnValue(false);
    }
}
