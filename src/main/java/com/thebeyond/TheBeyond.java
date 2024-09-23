package com.thebeyond;

import com.thebeyond.registers.RegisterBlocks;
import com.thebeyond.registers.RegisterEntities;
import com.thebeyond.registers.RegisterItems;
import com.thebeyond.registers.RegisterTabs;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
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

        RegisterBlocks.BLOCKS.register(modEventBus);
        RegisterItems.ITEMS.register(modEventBus);
        RegisterTabs.CREATIVE_MODE_TABS.register(modEventBus);
        RegisterEntities.ENTITY_TYPES.register(modEventBus);

        /**
         * Register ourselves for server and other game events we are interested in.
         * Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
         * Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        **/
        //NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(RegisterTabs::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
    }

}
