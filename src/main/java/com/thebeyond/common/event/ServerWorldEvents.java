package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.item.AnchorLeggingsItem;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.compat.EndBiomeDiscovery;
import com.thebeyond.common.worldgen.compat.EndBiomeInjector;
import com.thebeyond.common.worldgen.compat.SurfaceRuleMerger;
import com.thebeyond.common.worldgen.features.AuroraciteLayerFeature;
import com.thebeyond.common.worldgen.features.AuroraciteLayerDTFeature;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.List;

@EventBusSubscriber(modid = TheBeyond.MODID)
public class ServerWorldEvents {

    private static final ResourceLocation END_DIMENSION = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "dimension/the_end.json");

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // When Beyond's biome source decoded successfully (BeyondTerrainState.isActive() == true)
        // the End is owned by Beyond's native terrain — there is nothing to inject and the fallback
        // paths would only confuse things. Skip them entirely.
        //
        // When Beyond is NOT the active End provider (player disabled the beyond_terrain datapack
        // or it was overridden), run the fallback compat paths so Beyond biomes still appear in the
        // foreign terrain — "soup mode".
        if (BeyondTerrainState.isActive()) {
            TheBeyond.LOGGER.info("[TheBeyond] Beyond terrain is active — running auto-discovery for foreign End biomes");
            EndBiomeDiscovery.discoverAndInject(event.getServer());
            SurfaceRuleMerger.mergeSurfaceRules(event.getServer(), true);
            return;
        }

        // Soup mode diagnostic: identify which pack actually provided the_end.json so debugging
        // unexpected biome/lighting/structure-loss reports doesn't require log archaeology.
        // After Phase C wrapping, this stack should contain at most one entry (the foreign pack
        // that won when Beyond was disabled). Multiple entries would indicate a wrapping bypass.
        try {
            List<Resource> stack = event.getServer().getResourceManager().getResourceStack(END_DIMENSION);
            if (stack.isEmpty()) {
                TheBeyond.LOGGER.warn("[TheBeyond] Soup mode active but no pack provides {} — End dimension will be vanilla", END_DIMENSION);
            } else {
                List<String> sources = stack.stream().map(Resource::sourcePackId).toList();
                TheBeyond.LOGGER.info("[TheBeyond] Soup mode: End dimension provided by {} pack(s): {}", sources.size(), sources);
            }
        } catch (Exception e) {
            TheBeyond.LOGGER.warn("[TheBeyond] Failed to inspect End dimension provider stack", e);
        }

        TheBeyond.LOGGER.info("[TheBeyond] Beyond terrain not active — running fallback biome injection");
        SurfaceRuleMerger.mergeSurfaceRules(event.getServer());
        EndBiomeInjector.injectBiomes(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Reset all static world-bound state so the next server start re-detects whether
        // Beyond owns the End and re-seeds noises from the new world seed. Critical for
        // single-player sessions where the JVM survives multiple world loads.
        BeyondTerrainState.reset();
        BeyondEndChunkGenerator.resetNoises();
        AuroraciteLayerFeature.resetNoise();
        AuroraciteLayerDTFeature.resetNoise();
        EndBiomeInjector.vanillaEndHolders = null;
        AnchorLeggingsItem.clearCreativeTracking();
    }
}
