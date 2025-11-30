package com.thebeyond.common.registry;

import com.google.common.collect.Sets;
import com.thebeyond.client.model.equipment.EtherCloakModel;
import com.thebeyond.common.item.*;
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

    public static final DeferredItem<Item> LINER = registerItem("liner", () -> new LinerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FILLER = registerItem("filler", () -> new FillerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HOLLOWER = registerItem("hollower", () -> new HollowFillerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> MAGNET = registerItem("magnet", () -> new MagnetItem(new Item.Properties().stacksTo(1), 8));
    public static final DeferredItem<Item> VOID_CRYSTAL = registerItem("void_crystal", () -> new AlsoPlaceableOnFluidBlockItem(BeyondBlocks.VOID_CRYSTAL.get(), new Item.Properties()));
    public static final DeferredItem<Item> MAGNOLILLY = registerItem("magnolilly", () -> new PlaceOnWaterBlockItem(BeyondBlocks.MAGNOLILLY.get(), new Item.Properties()));
    public static final DeferredItem<Item> GELLID_VOID_BUCKET = registerItem("gellid_void_bucket", () -> new BucketItem(BeyondFluids.GELLID_VOID.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<Item> ABYSSAL_SHROUD = registerItem("abyssal_shroud", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> LANTERN_SHED = registerItem("lantern_shed", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TOTEM_OF_RESPITE = registerItem("totem_of_respite", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> PATHFINDER_BOOTS = registerItem("pathfinder_boots", () -> new ModelArmorItem(BeyondArmorMaterials.SHROUD_ARMOR, ArmorItem.Type.BOOTS, new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(10)).stacksTo(1), EtherCloakModel::new) {});
    public static final DeferredItem<Item> ETHER_CLOAK = registerItem("ether_cloak", () -> new ModelArmorItem(BeyondArmorMaterials.SHROUD_ARMOR, ArmorItem.Type.HELMET, new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(10)).stacksTo(1), EtherCloakModel::new) {});
    public static final DeferredItem<Item> ECTOPLASM = registerItem("ectoplasm", () -> new AirPlaceableBlockItem(BeyondBlocks.ECTOPLASM.get(), new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> LANTERN_SPAWN_EGG  = ITEMS.register("lantern_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.LANTERN.get(),15136255, 16777215,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ABYSSAL_NOMAD_SPAWN_EGG  = ITEMS.register("abyssal_nomad_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ABYSSAL_NOMAD.get(),-7693156, -13703706,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ENDERGLOP_SPAWN_EGG  = ITEMS.register("enderglop_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENDERGLOP.get(),0x7127f8, -297995,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ENADRAKE_SPAWN_EGG  = ITEMS.register("enadrake_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENADRAKE.get(),-13213601, -297995,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ENATIOUS_TOTEM_SPAWN_EGG  = ITEMS.register("enatious_totem_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENATIOUS_TOTEM.get(),-13213601, -2169180,new Item.Properties()));

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
