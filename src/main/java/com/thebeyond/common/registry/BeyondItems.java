package com.thebeyond.common.registry;

import com.google.common.collect.Sets;
import com.thebeyond.common.item.AlsoPlaceableOnFluidBlockItem;
import com.thebeyond.common.item.MagnetItem;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.function.Supplier;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static LinkedHashSet<DeferredItem<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    public static final DeferredItem<Item> MAGNET = registerItem("magnet", () -> new MagnetItem(new Item.Properties().stacksTo(1), 8));
    public static final DeferredItem<Item> VOID_CRYSTAL = registerItem("void_crystal", () -> new AlsoPlaceableOnFluidBlockItem(BeyondBlocks.VOID_CRYSTAL.get(), new Item.Properties()));
    public static final DeferredItem<Item> MAGNOLILLY = registerItem("magnolilly", () -> new PlaceOnWaterBlockItem(BeyondBlocks.MAGNOLILLY.get(), new Item.Properties()));
    public static final DeferredItem<Item> GELLID_VOID_BUCKET = registerItem("gellid_void_bucket", () -> new BucketItem(BeyondFluids.GELLID_VOID.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<SpawnEggItem> ENDERGLOP_SPAWN_EGG  = ITEMS.register("enderglop_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENDERGLOP.get(),0x7127f8, 0xc126ff,new Item.Properties()));


    @SuppressWarnings("unchecked")
    public static <T extends Item> DeferredItem<T> registerItem(final String name, final Supplier<? extends Item> item) {
        DeferredItem<Item> toReturn = ITEMS.register(name, item);
        CREATIVE_TAB_ITEMS.add(toReturn);
        return (DeferredItem<T>) toReturn;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Item> DeferredItem<T> registerIntegrationItem(final String name, final Supplier<? extends Item> item, String modId) {
        if (!ModList.get().isLoaded(modId)) return null;
        DeferredItem<Item> toReturn = ITEMS.register(name, item);
        CREATIVE_TAB_ITEMS.add(toReturn);
        return (DeferredItem<T>) toReturn;
    }
}
