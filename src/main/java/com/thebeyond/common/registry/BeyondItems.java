package com.thebeyond.common.registry;

import com.google.common.collect.Sets;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.function.Supplier;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static LinkedHashSet<DeferredItem<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    @SuppressWarnings("unchecked")
    public static <T extends Item> DeferredItem<T> registerItem(final String name, final Supplier<? extends Item> item) {
        DeferredItem<Item> toReturn = ITEMS.register(name, item);
        CREATIVE_TAB_ITEMS.add(toReturn);
        return (DeferredItem<T>) toReturn;
    }

    public static final DeferredItem<SpawnEggItem> ENDERGLOP_SPAWN_EGG  = ITEMS.register("enderglop_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENDERGLOP.get(),0x7127f8, 0xc126ff,new Item.Properties()));

    @SuppressWarnings("unchecked")
    public static <T extends Item> DeferredItem<T> registerIntegrationItem(final String name, final Supplier<? extends Item> item, String modId) {
        if (!ModList.get().isLoaded(modId)) return null;
        DeferredItem<Item> toReturn = ITEMS.register(name, item);
        CREATIVE_TAB_ITEMS.add(toReturn);
        return (DeferredItem<T>) toReturn;
    }
}
