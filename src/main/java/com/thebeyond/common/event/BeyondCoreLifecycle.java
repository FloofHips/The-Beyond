package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.api.event.BeyondServerLifecycleEvent;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.common.item.AnchorLeggingsItem;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.features.AuroraciteLayerDTFeature;
import com.thebeyond.common.worldgen.features.AuroraciteLayerFeature;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Beyond's static state lifecycle: recompute at HIGHEST, the API event at LOWEST so subscribers see settled state. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class BeyondCoreLifecycle {
    private BeyondCoreLifecycle() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void recomputeState(ServerAboutToStartEvent event) {
        // reset first, CreateWorldScreen may have left the flag stale, the LEVEL_STEM registry decides
        BeyondTerrainStateInternal.reset();
        Registry<LevelStem> levelStems = event.getServer().registryAccess()
                .registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = levelStems.get(LevelStem.END);
        if (endStem != null && endStem.generator().getBiomeSource() instanceof BeyondEndBiomeSource) {
            BeyondTerrainStateInternal.markActive();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fireAboutToStart(ServerAboutToStartEvent event) {
        // Addons host their own structure types in this event, so what they mark here is warm-up too.
        com.thebeyond.api.worldgen.BeyondForeignStructureProfiles.warming(true);
        try {
            NeoForge.EVENT_BUS.post(new BeyondServerLifecycleEvent.AboutToStart(
                    event.getServer(), BeyondTerrainState.isActive()));
            com.thebeyond.common.worldgen.AutoHostWarmUp.run(event.getServer());
        } finally {
            com.thebeyond.api.worldgen.BeyondForeignStructureProfiles.warming(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fireStopped(ServerStoppedEvent event) {
        // Fire while Beyond state is still live so subscribers can read it during teardown.
        NeoForge.EVENT_BUS.post(new BeyondServerLifecycleEvent.Stopped(
                event.getServer(), BeyondTerrainState.isActive()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void resetCoreState(ServerStoppedEvent event) {
        // Reset Beyond's own static world-bound state so the next start re-detects.
        BeyondTerrainStateInternal.reset();
        BeyondEndChunkGenerator.resetNoises();
        com.thebeyond.common.worldgen.BeyondGenDiagnostics.reset();   // re-arm one-shot gen logs for next world
        com.thebeyond.api.worldgen.BeyondForeignStructureProfiles.clearLayerDistributed();
        com.thebeyond.common.worldgen.AutoHostWarmUp.reset();
        com.thebeyond.common.worldgen.BeyondStructureArbiter.reset();
        com.thebeyond.api.compat.PancakeScan.clearCache();
        com.thebeyond.common.worldgen.StructureShape.reset();
        com.thebeyond.common.worldgen.StructureReadings.reset();
        AuroraciteLayerFeature.resetNoise();
        AuroraciteLayerDTFeature.reset();
        AnchorLeggingsItem.clearCreativeTracking();
    }
}
