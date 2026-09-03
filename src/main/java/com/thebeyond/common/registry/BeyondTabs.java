package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.creative.BeyondCreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> THE_BEYOND = CREATIVE_MODE_TABS.register(MODID, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.the_beyond"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .withSearchBar()
            .icon(() -> BeyondItems.REMEMBRANCE_LACE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                BeyondItems.ITEMS.getEntries().forEach((i) -> {
                            output.accept(i.get().asItem());
                        }
                );
                // Deafening potion is a thrown tool: list the functional splash + lingering variants. The
                // drinkable shell is only the brew intermediate (no effect on drink), so it is not listed here.
                output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, BeyondPotions.DEAFENING));
                output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, BeyondPotions.DEAFENING));})
            .build());



    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(BeyondItems.LINER.get());
            event.accept(BeyondItems.HOLLOWER.get());
            event.accept(BeyondItems.FILLER.get());
        }
        if (event.getTabKey() == THE_BEYOND.getKey()) {
            
        }
    }
}
