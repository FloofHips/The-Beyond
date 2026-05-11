package com.thebeyond.mixin.compat.stellarity;

import com.google.common.collect.ImmutableList;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Returns Stellarity's hardcoded 10-spike ring instead of vanilla's seed-random positions so
 *  {@code EndDragonFight} dragon-fight callers (crystal count, respawn animation, etc.) hit
 *  the actual pillars. Values mirror {@code ring.json} — keep synced with
 *  {@code StellarityBetterEndCompatEvents.STELLARITY_RING_POSITIONS}. */
@Mixin(SpikeFeature.class)
public abstract class SpikeFeatureStellarityCompatMixin {

    /** Stellarity's 10-spike ring from {@code ring.json}. All unguarded. */
    private static final List<SpikeFeature.EndSpike> THE_BEYOND$STELLARITY_RING_SPIKES = ImmutableList.of(
            new SpikeFeature.EndSpike( 63,   0, 5, 100, false),
            new SpikeFeature.EndSpike( 50,  36, 4, 105, false),
            new SpikeFeature.EndSpike( 18,  59, 4,  94, false),
            new SpikeFeature.EndSpike(-19,  59, 5, 106, false),
            new SpikeFeature.EndSpike(-51,  36, 4, 105, false),
            new SpikeFeature.EndSpike(-63,   0, 5,  93, false),
            new SpikeFeature.EndSpike(-51, -39, 6, 100, false),
            new SpikeFeature.EndSpike(-19, -60, 6,  96, false),
            new SpikeFeature.EndSpike( 18, -60, 5,  87, false),
            new SpikeFeature.EndSpike( 50, -39, 8,  95, false)
    );

    @Inject(method = "getSpikesForLevel", at = @At("HEAD"), cancellable = true)
    private static void theBeyond$substituteStellarityRingSpikes(
            WorldGenLevel level, CallbackInfoReturnable<List<SpikeFeature.EndSpike>> cir) {
        if (!ModList.get().isLoaded("stellarity")) {
            return;
        }
        if (!BeyondTerrainState.isActive()) {
            return;
        }
        cir.setReturnValue(THE_BEYOND$STELLARITY_RING_SPIKES);
    }
}
