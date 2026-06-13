package com.thebeyond;

import com.thebeyond.common.network.BeyondNetworking;
import com.thebeyond.common.registry.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Mod(TheBeyond.MODID)
public class TheBeyond {
    public static final String MODID = "the_beyond";
    public static final Logger LOGGER = LogUtils.getLogger();

    // FML injects these constructor params.
    public TheBeyond(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BeyondBlocks.BLOCKS.register(modEventBus);
        BeyondBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        BeyondItems.ITEMS.register(modEventBus);
        BeyondArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        BeyondTabs.CREATIVE_MODE_TABS.register(modEventBus);
        BeyondEntityTypes.ENTITY_TYPES.register(modEventBus);
        BeyondParticleTypes.PARTICLE_TYPES.register(modEventBus);
        BeyondChunkGenerators.CHUNK_GENS.register(modEventBus);
        BeyondChunkGenerators.BIOME_SOURCES.register(modEventBus);
        BeyondSoundEvents.SOUND_EVENTS.register(modEventBus);
        BeyondFluids.FLUID_TYPES.register(modEventBus);
        BeyondFluids.FLUIDS.register(modEventBus);
        BeyondFeatures.FEATURES.register(modEventBus);
        BeyondEffects.MOB_EFFECTS.register(modEventBus);
        BeyondComponents.COMPONENTS.register(modEventBus);
        BeyondPoiTypes.POI_TYPES.register(modEventBus);
        BeyondBiomeModifiers.BIOME_MODIFIERS.register(modEventBus);
        BeyondProcessors.PROCESSOR_TYPES.register(modEventBus);
        BeyondAttachments.ATTACHMENT_TYPES.register(modEventBus);
        BeyondMenus.MENUS.register(modEventBus);
        BeyondCriteriaTriggers.TRIGGERS.register(modEventBus);

        modEventBus.addListener(BeyondTabs::addCreative);
        modEventBus.addListener(BeyondNetworking::onRegisterPayloads);
        modEventBus.addListener(com.thebeyond.common.data.BeyondDataMapTypes::onRegisterDataMaps);
        modEventBus.addListener(this::addBuiltinPacks);

        modContainer.registerConfig(ModConfig.Type.COMMON, BeyondConfig.COMMON_CONFIG);
        modContainer.registerConfig(ModConfig.Type.CLIENT, BeyondConfig.CLIENT_CONFIG);
    }

    private void addBuiltinPacks(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            Path resourcePath = ModList.get().getModFileById(MODID)
                    .getFile().findResource("resourcepacks/beyond_terrain");
            PackLocationInfo info = new PackLocationInfo(
                    "mod/" + MODID + ":beyond_terrain",
                    Component.literal("The Beyond - Custom End Terrain"),
                    PackSource.DEFAULT, Optional.empty());
            Pack.ResourcesSupplier supplier = new PathPackResources.PathResourcesSupplier(resourcePath);

            // Toggleable only with Convergence (isleweaver); else required and hidden.
            boolean convergencePresent = ModList.get().isLoaded("isleweaver");
            PackSelectionConfig selection =
                    new PackSelectionConfig(!convergencePresent, Pack.Position.TOP, false);

            // Addons contribute compat child packs (rewriting End dim-type y-bounds); highest priority wins.
            com.thebeyond.api.event.BeyondTerrainPackAssembleEvent assembleEvent =
                    new com.thebeyond.api.event.BeyondTerrainPackAssembleEvent(event);
            net.neoforged.fml.ModLoader.postEvent(assembleEvent);

            com.thebeyond.api.event.BeyondTerrainPackAssembleEvent.Contribution winner =
                    assembleEvent.resolveWinner();
            LOGGER.info(
                    "[TheBeyond] BeyondTerrainPackAssembleEvent resolved: {} total contribution(s), winner={}",
                    assembleEvent.getContributions().size(),
                    winner == null ? "<none>" : winner.packName() + " (priority " + winner.priority() + ")");
            final List<Pack> finalChildren = winner != null ? List.of(winner.pack()) : List.of();
            if (winner != null) {
                LOGGER.info("[TheBeyond] {}", winner.logMessage());
            }

            event.addRepositorySource(consumer -> {
                Pack beyondTerrain = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, selection);
                if (beyondTerrain == null) {
                    LOGGER.error("[TheBeyond] Failed to build beyond_terrain pack — terrain features will not be available.");
                    return;
                }
                Pack toAccept = finalChildren.isEmpty() ? beyondTerrain : beyondTerrain.withChildren(finalChildren);
                if (!convergencePresent) {
                    toAccept = toAccept.hidden();
                }
                LOGGER.info(
                        "[TheBeyond] Submitting beyond_terrain pack to repository source: id={} childCount={} visible={}",
                        toAccept.getId(), finalChildren.size(), convergencePresent);
                consumer.accept(toAccept);
            });
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("create")) {
            com.thebeyond.compat.create.BeyondCreateCompat.register();
        }
        if (ModList.get().isLoaded("sable")) {
            com.thebeyond.compat.sable.BeyondSableCompat.register();
        }

        // Lets addons register compat modules now that common-setup is done.
        net.neoforged.fml.ModLoader.postEvent(new com.thebeyond.api.event.BeyondCommonSetupEvent());
    }
}
