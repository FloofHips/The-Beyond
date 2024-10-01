package com.thebeyond.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> THE_BEYOND = CREATIVE_MODE_TABS.register("the_beyond", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.the_beyond"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> BeyondBlocks.POLAR_PILLAR.asItem().getDefaultInstance())
            .displayItems((parameters, output) -> {
                BeyondItems.ITEMS.getEntries().forEach((i) -> {
                            output.accept(i.get().asItem());
                        }
                );
            }).build());

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
            event.accept(BeyondBlocks.POLAR_PILLAR.get());
    }
}
