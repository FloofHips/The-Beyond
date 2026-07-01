package com.thebeyond.common.registry;

import com.google.common.collect.Sets;
import com.thebeyond.client.model.equipment.AnchorLeggingsModel;
import com.thebeyond.client.model.equipment.EtherCloakModel;
import com.thebeyond.common.item.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static LinkedHashSet<DeferredItem<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    public static final DeferredItem<Item> LINER = registerItem("liner", () -> new LinerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FILLER = registerItem("filler", () -> new FillerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> HOLLOWER = registerItem("hollower", () -> new HollowFillerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> FERROPETAL = registerItem("ferropetal", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MAGNET = registerItem("magnet", () -> new MagnetItem(new Item.Properties().stacksTo(1), 8));
    // Block-click places the camera block; in-air or sneaking shoots it handheld.
    public static final DeferredItem<Item> PINHOLE_CAMERA = registerItem("pinhole_camera", () -> new CameraBlockItem(BeyondBlocks.CAMERA.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SNAPSHOT = registerItem("snapshot", () -> new SnapshotItem(new Item.Properties().stacksTo(16).rarity(BeyondEnums.REMEMBRANCE.getValue())));
    public static final DeferredItem<Item> FERROJELLY = registerItem("ferrojelly", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> MAGNOLILLY = registerItem("magnolilly", () -> new PlaceOnWaterBlockItem(BeyondBlocks.MAGNOLILLY.get(), new Item.Properties()));

    public static final DeferredItem<Item> VOID_CRYSTAL = registerItem("void_crystal", () -> new AlsoPlaceableOnFluidBlockItem(BeyondBlocks.VOID_CRYSTAL.get(), new Item.Properties()));
    public static final DeferredItem<Item> GRAVISTAR = registerItem("gravistar", () -> new GravistarItem(new Item.Properties()));
    public static final DeferredItem<Item> GELLID_VOID_BUCKET = registerItem("gellid_void_bucket", () -> new BucketItem(BeyondFluids.GELLID_VOID.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<Item> ABYSSAL_SHROUD = registerItem("abyssal_shroud", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> LANTERN_SHED = registerItem("lantern_shed", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> TOTEM_OF_RESPITE = registerItem("totem_of_respite", () -> new Item(new Item.Properties().rarity(Rarity.RARE)) {
        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
            super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
            tooltipComponents.add(Component.literal("When in Hand:").withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal(" Preserve items on Death").withStyle(ChatFormatting.AQUA));
        }
    });
    public static final DeferredItem<Item> ETHER_CLOAK = registerItem("ether_cloak", () -> new ModelArmorItem(BeyondArmorMaterials.SHROUD_ARMOR, ArmorItem.Type.HELMET, new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(10)).stacksTo(1), EtherCloakModel::new) {
        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
            super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
            tooltipComponents.add(Component.literal("Passive:").withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal(" Hide Nametag").withStyle(ChatFormatting.BLUE));
            tooltipComponents.add(Component.literal(" Lower Aggro Distance").withStyle(ChatFormatting.BLUE));
        }
    });
    public static final DeferredItem<Item> ANCHOR_LEGGINGS = registerItem("anchor_leggings", () -> new AnchorLeggingsItem(BeyondArmorMaterials.ANCHOR_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(10)).stacksTo(1).rarity(Rarity.EPIC), AnchorLeggingsModel::new) {
        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
            super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
            tooltipComponents.add(Component.literal("On Crouch Mid Air:").withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal(" Land with KnockBack Impact").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    });
    public static final DeferredItem<Item> PATHFINDER_BOOTS = registerItem("pathfinder_boots", () -> new ModelArmorItem(BeyondArmorMaterials.SHROUD_ARMOR, ArmorItem.Type.BOOTS, new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(10)).stacksTo(1), EtherCloakModel::new) {
        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
            super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
            tooltipComponents.add(Component.literal("Passive:").withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal(" Walk on Auroracite").withStyle(ChatFormatting.DARK_AQUA));
        }
    });
    public static final DeferredItem<Item> ECTOPLASM = registerItem("ectoplasm", () -> new AirPlaceableBlockItem(BeyondBlocks.ECTOPLASM.get(), new Item.Properties()));

    public static final DeferredItem<Item> LIVE_FLAME = registerItem("live_flame", () -> new LiveFlameItem(new Item.Properties().durability(12000)));
    public static final DeferredItem<Item> LIVID_FLAME = registerItem("livid_flame", () -> new LiveFlameItem(new Item.Properties().durability(6000).rarity(Rarity.RARE)));

    public static final DeferredItem<SpawnEggItem> LANTERN_SPAWN_EGG  = ITEMS.register("lantern_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.LANTERN.get(),15136255, 16777215,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ABYSSAL_NOMAD_SPAWN_EGG  = ITEMS.register("abyssal_nomad_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ABYSSAL_NOMAD.get(),-7693156, -13703706,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ENDERGLOP_SPAWN_EGG  = ITEMS.register("enderglop_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENDERGLOP.get(),0x7127f8, -297995,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ENADRAKE_SPAWN_EGG  = ITEMS.register("enadrake_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENADRAKE.get(),-13213601, -297995,new Item.Properties()));
    public static final DeferredItem<SpawnEggItem> ENATIOUS_TOTEM_SPAWN_EGG  = ITEMS.register("enatious_totem_spawn_egg", () -> new SpawnEggItem(BeyondEntityTypes.ENATIOUS_TOTEM.get(),-13213601, -2169180,new Item.Properties()));

    public static final DeferredItem<Item> REMEMBRANCE_BEADS    = registerRemembrance("beads_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_IDOL     = registerRemembrance("idol_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_BRACE    = registerRemembrance("brace_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_RING     = registerRemembrance("ring_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_MEMORY   = registerRemembrance("memory_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_HORN     = registerRemembrance("horn_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_LACE     = registerRemembrance("lace_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_BROCHE   = registerRemembrance("broche_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_LIFE     = registerRemembrance("life_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_HOME     = registerRemembrance("home_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_EYE      = registerRemembrance("eye_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_SPIKE    = registerRemembrance("spike_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_ORNAMENT = registerRemembrance("ornament_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_MOUNT    = registerRemembrance("mount_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_HAND     = registerRemembrance("hand_remembrance");
    public static final DeferredItem<Item> REMEMBRANCE_CLOTH    = registerRemembrance("cloth_remembrance");

    @SuppressWarnings("unchecked")
    public static <T extends Item> DeferredItem<T> registerItem(final String name, final Supplier<? extends Item> item) {
        DeferredItem<Item> toReturn = ITEMS.register(name, item);
        CREATIVE_TAB_ITEMS.add(toReturn);
        return (DeferredItem<T>) toReturn;
    }

    /** The remembrances register identically save the name; keep the order — registration order drives the creative tab and datagen. */
    private static DeferredItem<Item> registerRemembrance(String name) {
        return registerItem(name, () -> new Item(new Item.Properties().rarity(BeyondEnums.REMEMBRANCE.getValue())));
    }

}
