package com.thebeyond.common.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.item.AnchorLeggingsItem;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.compat.EndBiomeDiscovery;
import com.thebeyond.common.worldgen.compat.EndBiomeInjector;
import com.thebeyond.common.worldgen.compat.SurfaceRuleMerger;
import com.thebeyond.common.worldgen.features.AuroraciteLayerFeature;
import com.thebeyond.common.worldgen.features.AuroraciteLayerDTFeature;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.dimension.LevelStem;
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
        // Recompute BeyondTerrainState.active from the server's actual End LevelStem.
        // The flag can be stale from CreateWorldScreen's UI phase, which decodes
        // WorldDataConfiguration.DEFAULT (auto-adding beyond_terrain) and constructs a
        // BeyondEndBiomeSource — if the pack is then deselected before clicking Create,
        // the real server's End uses vanilla TheEndBiomeSource but the static flag still
        // says active. The LEVEL_STEM registry is the one source of truth here.
        BeyondTerrainState.reset();
        Registry<LevelStem> levelStems = event.getServer().registryAccess()
                .registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = levelStems.get(LevelStem.END);
        if (endStem != null && endStem.generator().getBiomeSource() instanceof BeyondEndBiomeSource) {
            BeyondTerrainState.markActive();
        }

        // Beyond active: its native terrain owns the End, skip fallback injection.
        // Beyond inactive ("soup mode"): run compat paths so Beyond biomes still appear
        // in foreign End terrain.
        if (BeyondTerrainState.isActive()) {
            TheBeyond.LOGGER.info("[TheBeyond] Beyond terrain is active — running auto-discovery for foreign End biomes");
            EndBiomeDiscovery.discoverAndInject(event.getServer());
            SurfaceRuleMerger.mergeSurfaceRules(event.getServer(), true);
            return;
        }

        // Log which pack provided the_end.json. With the dimension-JSON wrapper mixin
        // installed this stack should have at most one entry (the foreign pack that won);
        // multiple entries indicate a wrapping bypass.
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
        // Beyond owns the End and re-seeds noises from the new world seed. Required for
        // single-player where the JVM survives multiple world loads.
        BeyondTerrainState.reset();
        BeyondEndChunkGenerator.resetNoises();
        AuroraciteLayerFeature.resetNoise();
        AuroraciteLayerDTFeature.resetNoise();
        EndBiomeInjector.vanillaEndHolders = null;
        AnchorLeggingsItem.clearCreativeTracking();
    }
}
