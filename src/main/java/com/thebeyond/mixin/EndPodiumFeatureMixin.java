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

/**
 * Cancels vanilla {@link EndPodiumFeature#place} in the Beyond+BetterEnd+Stellarity combo
 * to prevent a duplicate portal/egg from spawning at the wrong Y (BetterEnd rewrites the
 * origin to WORLD_SURFACE, which lands ~y=260 on Beyond's terrain). Stellarity owns the
 * real podium and dragon egg via its own mcfunction chain.
 *
 * <p>Gated on: stellarity + betterend loaded, {@link BeyondTerrainState#isActive()}.</p>
 */
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
