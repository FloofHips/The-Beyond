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
import java.util.Optional;

@Mod(TheBeyond.MODID)
public class TheBeyond {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "the_beyond";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
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
            PackSelectionConfig selection = new PackSelectionConfig(false, Pack.Position.TOP, false);
            event.addRepositorySource(consumer ->
                    consumer.accept(Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, selection)));

            // Enderscape compat: override Beyond's dimension_type/the_end.json with a merged
            // version that preserves Beyond's effects/fixed_time/has_skylight but takes its
            // y-bounds from Enderscape (min_y=-64, height=384). Registered AFTER beyond_terrain
            // in the same event so it sits above Beyond's own pack in the resource manager and
            // wins the dimension_type resolution. Only ships one file — everything else still
            // comes from beyond_terrain. Lets AuroraciteLayerFeature generate at y=-64 (it
            // reads level.getMinBuildHeight() which comes from the dimension_type). If Enderscape
            // is not loaded, this block is skipped and Beyond's own 0..256 dimension_type stays
            // authoritative.
            if (ModList.get().isLoaded("enderscape")) {
                Path compatPath = ModList.get().getModFileById(MODID)
                        .getFile().findResource("resourcepacks/beyond_enderscape_bounds");
                PackLocationInfo compatInfo = new PackLocationInfo(
                        "mod/" + MODID + ":beyond_enderscape_bounds",
                        Component.literal("The Beyond - Enderscape Bounds Compat"),
                        PackSource.DEFAULT, Optional.empty());
                Pack.ResourcesSupplier compatSupplier = new PathPackResources.PathResourcesSupplier(compatPath);
                PackSelectionConfig compatSelection = new PackSelectionConfig(false, Pack.Position.TOP, false);
                event.addRepositorySource(consumer ->
                        consumer.accept(Pack.readMetaAndCreate(compatInfo, compatSupplier, PackType.SERVER_DATA, compatSelection)));
                LOGGER.info("[TheBeyond] Enderscape detected — registering beyond_enderscape_bounds compat pack (extends End y-bounds to -64..384).");
            }

        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }
}
