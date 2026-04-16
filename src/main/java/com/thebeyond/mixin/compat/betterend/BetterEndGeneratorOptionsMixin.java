package com.thebeyond.mixin.compat.betterend;

import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces BetterEnd's {@code replacePortal}, {@code replacePillars}, and {@code hasCentralIsland}
 * to return {@code false} when Stellarity is also loaded, ceding central-island ownership to
 * Stellarity's datapack-driven chain.
 *
 * <p>{@code hasDragonFights()} is left alone (disabling it would break EndDragonFight entirely).
 * Soft-targeted via {@code @Pseudo} — no-op without BetterEnd. Only fires when Stellarity is loaded.</p>
 */
@Pseudo
@Mixin(targets = "org.betterx.betterend.world.generator.GeneratorOptions", remap = false)
public abstract class BetterEndGeneratorOptionsMixin {

    @Inject(method = "replacePortal", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void theBeyond$disablePortalReplace(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "replacePillars", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void theBeyond$disablePillarReplace(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hasCentralIsland", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void theBeyond$disableCentralIsland(CallbackInfoReturnable<Boolean> cir) {
        if (ModList.get().isLoaded("stellarity")) {
            cir.setReturnValue(false);
        }
    }

    // NOTE: Do NOT override hasPillars() here. BetterEnd's SpikeFeatureMixin.be_place reads
    // !hasPillars() at HEAD of vanilla SpikeFeature.place, and Stellarity's own ring is a
    // minecraft:end_spike configured feature that goes through SpikeFeature.place the same way.
    // Forcing hasPillars=false would cancel Stellarity's ring alongside BetterEnd's, leaving
    // the dragons_den pillar-less. The pillars the player sees ARE Stellarity's; leave them.
}
