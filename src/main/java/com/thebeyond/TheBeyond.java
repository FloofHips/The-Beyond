package com.thebeyond;

import com.thebeyond.common.registry.*;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

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

        modEventBus.addListener(BeyondTabs::addCreative);

        modContainer.registerConfig(ModConfig.Type.COMMON, BeyondConfig.COMMON_CONFIG);
        modContainer.registerConfig(ModConfig.Type.CLIENT, BeyondConfig.CLIENT_CONFIG);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }
}
