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

/** Beyond-owned static state lifecycle and {@link BeyondServerLifecycleEvent} dispatch.
 *  State recompute runs first (HIGHEST), API event fires last (LOWEST) so subscribers see resolved state. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class BeyondCoreLifecycle {
    private BeyondCoreLifecycle() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void recomputeState(ServerAboutToStartEvent event) {
        // Reset before re-detection — active flag can be stale from CreateWorldScreen
        // constructing a BeyondEndBiomeSource. LEVEL_STEM registry is the source of truth.
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
        NeoForge.EVENT_BUS.post(new BeyondServerLifecycleEvent.AboutToStart(
                event.getServer(), BeyondTerrainState.isActive()));
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
        AuroraciteLayerFeature.resetNoise();
        AuroraciteLayerDTFeature.resetNoise();
        AnchorLeggingsItem.clearCreativeTracking();
    }
}
