package com.thebeyond.mixin.compat.stellarity;

import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Attempts to neutralise BetterEnd's {@code getHeight()} surface-relative override by returning
 * the raw {@code height} field at higher mixin priority (1500 vs BetterEnd's 1000).
 *
 * <p>Note: mixin HEAD ordering across separate configs is not fully deterministic. The primary
 * height fix is the static lookup table in {@code SpikeFeaturePlacementStellarityMixin}. This
 * mixin is a best-effort belt-and-suspenders for callers outside our control (e.g.
 * {@code DragonRespawnAnimation.SUMMONING_PILLARS}).</p>
 *
 * <p>Gated on: Stellarity loaded, {@link BeyondTerrainState#isActive()}.</p>
 */
@Mixin(value = SpikeFeature.EndSpike.class, priority = 1500)
public class EndSpikeHeightFixMixin {

    @Shadow
    @Final
    private int height;

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void theBeyond$returnRawHeight(CallbackInfoReturnable<Integer> cir) {
        if (!ModList.get().isLoaded("stellarity")) {
            return;
        }
        if (!BeyondTerrainState.isActive()) {
            return;
        }
        cir.setReturnValue(this.height);
    }
}
