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

            // Enderscape compat: override Beyond's dimension_type/the_end.json with a merged
            // version that preserves Beyond's effects/fixed_time/has_skylight but takes its
            // y-bounds from Enderscape (min_y=-64, height=384). Attached as a CHILD pack of
            // beyond_terrain (via Pack.withChildren), which means:
            //   (1) It's automatically hidden from the datapack UI (NeoForge auto-hides
            //       children — see Pack.<init>(List<Pack>) flattening logic), so it
            //       never surfaces in the datapack UI.
            //   (2) Its lifecycle is bound to beyond_terrain: enabling/disabling the parent
            //       enables/disables the child. This avoids a "bounds active while
            //       beyond_terrain is off" state in which the bounds pack would override
            //       foreign mods' dim_types.
            //   (3) Children are placed AFTER the parent in the selected pack list (see
            //       ResourcePackLoader.expandAndRemoveRootChildren), and FallbackResourceManager
            //       scans packs end-to-start, so the child STILL wins the dimension_type
            //       resolution against its parent — exactly what we need for the min_y=-64 override.
            // Only ships one file (dimension_type/the_end.json) — everything else still comes
            // from beyond_terrain. If Enderscape is not loaded, no child is attached and
            // Beyond's own 0..256 dimension_type stays authoritative.
            List<Pack> children = List.of();
            if (ModList.get().isLoaded("enderscape")) {
                Path compatPath = ModList.get().getModFileById(MODID)
                        .getFile().findResource("resourcepacks/beyond_enderscape_bounds");
                PackLocationInfo compatInfo = new PackLocationInfo(
                        "mod/" + MODID + ":beyond_enderscape_bounds",
                        Component.literal("The Beyond - Enderscape Bounds Compat"),
                        PackSource.DEFAULT, Optional.empty());
                Pack.ResourcesSupplier compatSupplier = new PathPackResources.PathResourcesSupplier(compatPath);
                // defaultPosition / fixedPosition are ignored for children (NeoForge forces
                // them into the child slot below the parent). Required=false also forced.
                PackSelectionConfig compatSelection = new PackSelectionConfig(false, Pack.Position.TOP, false);
                Pack compatPack = Pack.readMetaAndCreate(compatInfo, compatSupplier, PackType.SERVER_DATA, compatSelection);
                if (compatPack != null) {
                    children = List.of(compatPack);
                    LOGGER.info("[TheBeyond] Enderscape detected — attaching beyond_enderscape_bounds as hidden child of beyond_terrain (extends End y-bounds to -64..384 whenever beyond_terrain is active).");
                } else {
                    LOGGER.warn("[TheBeyond] Enderscape detected but failed to build beyond_enderscape_bounds pack — falling back to Beyond's own 0..256 dimension_type.");
                }
            }

            final List<Pack> finalChildren = children;
            event.addRepositorySource(consumer -> {
                Pack beyondTerrain = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, selection);
                if (beyondTerrain == null) {
                    LOGGER.error("[TheBeyond] Failed to build beyond_terrain pack — terrain features will not be available.");
                    return;
                }
                consumer.accept(finalChildren.isEmpty() ? beyondTerrain : beyondTerrain.withChildren(finalChildren));
            });
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }
}
