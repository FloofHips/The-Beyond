package com.thebeyond.client.event;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondPotions;
import com.thebeyond.mixin.CreativeModeInventoryScreenAccessor;
import com.thebeyond.mixin.CreativeModeTabAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.thebeyond.common.registry.BeyondTabs.THE_BEYOND;

@EventBusSubscriber(modid = TheBeyond.MODID, value = Dist.CLIENT)
public class CreativeTabEvents {

    public static Collection<ItemStack> category_items;
    public static Collection<ItemStack> biome_items;
    static float headerAlpha = 0f;
    static float emptyAlpha = 0f;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        category_items = new ArrayList<ItemStack>();
        Map<String, Collection<ItemStack>> categories = new HashMap<>();

        categories.put("building", new ArrayList<ItemStack>());
        categories.put("functional", new ArrayList<ItemStack>());
        categories.put("equipment", new ArrayList<ItemStack>());
        categories.put("ingredients", new ArrayList<ItemStack>());
        categories.put("artifacts", new ArrayList<ItemStack>());
        categories.put("mobs", new ArrayList<ItemStack>());

        categories.get("building").add(createHeadertack(headerAlpha+=0.001f, Component.literal("Building")));
        categories.get("functional").add(createHeadertack(headerAlpha+=0.001f, Component.literal("Functional")));
        categories.get("equipment").add(createHeadertack(headerAlpha+=0.001f, Component.literal("Equipment")));
        categories.get("ingredients").add(createHeadertack(headerAlpha+=0.001f, Component.literal("Ingredients")));
        categories.get("mobs").add(createHeadertack(headerAlpha+=0.001f, Component.literal("Mobs")));
        categories.get("artifacts").add(createHeadertack(headerAlpha+=0.001f, Component.literal("Artifacts")));

        for (Collection<ItemStack> c : categories.values()) {
            initCategory(c);
        }

        categories.get("building").add(Blocks.END_STONE.asItem().getDefaultInstance());
        categories.get("building").add(Blocks.END_STONE_BRICKS.asItem().getDefaultInstance());
        categories.get("building").add(Blocks.END_STONE_BRICK_STAIRS.asItem().getDefaultInstance());
        categories.get("building").add(Blocks.END_STONE_BRICK_SLAB.asItem().getDefaultInstance());
        categories.get("building").add(Blocks.END_STONE_BRICK_WALL.asItem().getDefaultInstance());
        categories.get("building").add(BeyondBlocks.ENGRAVED_END_STONE.toStack());
        categories.get("building").add(BeyondBlocks.MEMOR.toStack());
        categories.get("building").add(BeyondBlocks.CHISELED_MEMOR.toStack());
        categories.get("building").add(BeyondBlocks.MEMOR_PILLAR.toStack());
        categories.get("building").add(BeyondBlocks.MEMOR_STAIRS.toStack());
        categories.get("building").add(BeyondBlocks.MEMOR_FAUCET.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN.toStack());
        categories.get("building").add(BeyondBlocks.GUSTER.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN_PILLAR.toStack());
        categories.get("building").add(BeyondBlocks.AMPHORA.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN_TILES.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN_MOSAIC.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN_STAIRS.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN_SLAB.toStack());
        categories.get("building").add(BeyondBlocks.PORTELAIN_DOOR.toStack());
        categories.get("building").add(BeyondBlocks.AURORACITE.toStack());
        categories.get("building").add(BeyondBlocks.PLATE_BLOCK.toStack());
        categories.get("building").add(BeyondBlocks.PLATED_END_STONE.toStack());
        categories.get("building").add(BeyondBlocks.OBIROOT.toStack());
        categories.get("building").add(BeyondBlocks.PEEPING_OBIROOT.toStack());
        categories.get("building").add(BeyondBlocks.XYLEM.toStack());
        categories.get("building").add(BeyondBlocks.ZYMOTE.toStack());
        categories.get("building").add(BeyondBlocks.REACHING_ZYMOTE.toStack());
        categories.get("building").add(BeyondBlocks.CREEPING_ZYMOTE.toStack());
        categories.get("building").add(BeyondBlocks.GAUSSANITE.toStack());
        categories.get("building").add(BeyondBlocks.GAUSS_VENT.toStack());
        categories.get("building").add(BeyondBlocks.BRITTLE_METAL_BLOCK.toStack());
        categories.get("building").add(BeyondBlocks.BRITTLE_METAL_STAIRS.toStack());
        categories.get("building").add(BeyondBlocks.BRITTLE_METAL_SLAB.toStack());
        categories.get("building").add(BeyondBlocks.BRITTLE_METAL_DOOR.toStack());
        categories.get("building").add(BeyondBlocks.NACRE.toStack());
        categories.get("building").add(BeyondBlocks.PEARL.toStack());
        categories.get("building").add(BeyondBlocks.PEARL_BRICKS.toStack());
        categories.get("building").add(BeyondBlocks.COBBLED_PEARL.toStack());
        categories.get("building").add(BeyondBlocks.COBBLED_PEARL_BRICKS.toStack());
        categories.get("building").add(BeyondBlocks.PEARL_MIRROR.toStack());
        categories.get("building").add(BeyondBlocks.PEARL_CHIMES.toStack());
        categories.get("building").add(BeyondBlocks.VILET.toStack());
        categories.get("building").add(BeyondBlocks.VILE_GROWTH.toStack());
        categories.get("building").add(BeyondBlocks.PERKA_STALK.toStack());

        categories.get("functional").add(BeyondBlocks.MEMOR_FAUCET.toStack());
        categories.get("functional").add(BeyondBlocks.BONFIRE.toStack());
        categories.get("functional").add(BeyondItems.ECTOPLASM.toStack());
        categories.get("functional").add(BeyondBlocks.GUSTER.toStack());
        categories.get("functional").add(BeyondBlocks.AMPHORA.toStack());
        categories.get("functional").add(BeyondBlocks.POLAR_PILLAR.toStack());
        categories.get("functional").add(BeyondBlocks.POLAR_BULB.toStack());
        categories.get("functional").add(BeyondBlocks.POLAR_ANTENNA.toStack());
        categories.get("functional").add(BeyondItems.MAGNOLILLY.toStack());
        categories.get("functional").add(BeyondBlocks.FERROJELLY_BLOCK.toStack());
        categories.get("functional").add(BeyondBlocks.BLEEDING_THORN.toStack());
        categories.get("functional").add(BeyondBlocks.BLINDING_THORN.toStack());
        categories.get("functional").add(BeyondBlocks.OBIROOT_SPROUT.toStack());
        categories.get("functional").add(BeyondBlocks.OBIROOT_ARM.toStack());
        categories.get("functional").add(BeyondBlocks.ENADRAKE_HUT.toStack());
        categories.get("functional").add(BeyondBlocks.ENADRAKE_FLARE.toStack());
        categories.get("functional").add(BeyondBlocks.ENATIOUS_TOTEM_SEED.toStack());
        categories.get("functional").add(BeyondBlocks.REFUGE.toStack());
        categories.get("functional").add(BeyondBlocks.GAUSS_VENT.toStack());
        categories.get("functional").add(BeyondBlocks.BELLOW.toStack());
        categories.get("functional").add(BeyondBlocks.SOOT_BLOCK.toStack());
        categories.get("functional").add(BeyondBlocks.MOLTEN_METAL.toStack());
        categories.get("functional").add(BeyondBlocks.BRITTLE_METAL.toStack());
        categories.get("functional").add(BeyondBlocks.PROJECTOR.toStack());
        categories.get("functional").add(BeyondItems.PRISMOGRAPH.toStack());
        categories.get("functional").add(BeyondBlocks.UNSTABLE_NACRE.toStack());
        categories.get("functional").add(BeyondBlocks.PEARL_MIRROR.toStack());
        categories.get("functional").add(BeyondBlocks.PERKA_STALK_MOUTH.toStack());
        categories.get("functional").add(BeyondBlocks.COIL_VERTEBRAE.toStack());

        categories.get("ingredients").add(BeyondItems.FERROPETAL.toStack());
        categories.get("ingredients").add(BeyondItems.FERROJELLY.toStack());
        categories.get("ingredients").add(BeyondItems.VOID_CRYSTAL.toStack());
        categories.get("ingredients").add(BeyondItems.STALKER_SEGMENT.toStack());
        categories.get("ingredients").add(BeyondItems.ABYSSAL_SHROUD.toStack());
        categories.get("ingredients").add(BeyondItems.LANTERN_SHED.toStack());
        categories.get("ingredients").add(BeyondItems.BRITTLE_METAL_SHEET.toStack());
        categories.get("ingredients").add(BeyondItems.PRISMUTH.toStack());

        categories.get("equipment").add(BeyondItems.ETHER_CLOAK.toStack());
        categories.get("equipment").add(Items.ELYTRA.getDefaultInstance());
        categories.get("equipment").add(BeyondItems.ANCHOR_LEGGINGS.toStack());
        categories.get("equipment").add(BeyondItems.PATHFINDER_BOOTS.toStack());
        categories.get("equipment").add(BeyondItems.BRITTLE_SWORD.toStack());
        categories.get("equipment").add(BeyondItems.BRITTLE_SHOVEL.toStack());
        categories.get("equipment").add(BeyondItems.BRITTLE_PICKAXE.toStack());
        categories.get("equipment").add(BeyondItems.BRITTLE_AXE.toStack());
        categories.get("equipment").add(BeyondItems.BRITTLE_HOE.toStack());
        categories.get("equipment").add(BeyondItems.MAGNET.toStack());
        categories.get("equipment").add(BeyondItems.GRAVISTAR.toStack());
        categories.get("equipment").add(BeyondItems.TOTEM_OF_RESPITE.toStack());
        categories.get("equipment").add(BeyondItems.OCARINA.toStack());
        categories.get("equipment").add(BeyondItems.SMOKE_FUSE.toStack());
        categories.get("equipment").add(BeyondItems.COILED_STALK.toStack());
        categories.get("functional").add(PotionContents.createItemStack(Items.SPLASH_POTION, BeyondPotions.DEAFENING));
        categories.get("functional").add(PotionContents.createItemStack(Items.LINGERING_POTION, BeyondPotions.DEAFENING));

        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_BEADS.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_IDOL.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_BRACE.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_RING.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_MEMORY.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_HORN.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_LACE.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_BROCHE.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_LIFE.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_HOME.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_EYE.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_SPIKE.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_ORNAMENT.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_MOUNT.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_HAND.toStack());
        categories.get("artifacts").add(BeyondItems.REMEMBRANCE_CLOTH.toStack());

        categories.get("mobs").add(BeyondItems.LANTERN_SPAWN_EGG.toStack());
        categories.get("mobs").add(BeyondItems.ABYSSAL_NOMAD_SPAWN_EGG.toStack());
        categories.get("mobs").add(BeyondItems.ENDERGLOP_SPAWN_EGG.toStack());
        categories.get("mobs").add(BeyondItems.ENADRAKE_SPAWN_EGG.toStack());
        categories.get("mobs").add(BeyondItems.ENATIOUS_TOTEM_SPAWN_EGG.toStack());
        categories.get("mobs").add(BeyondItems.BRUBBLE_SPAWN_EGG.toStack());
        categories.get("mobs").add(BeyondItems.TRINKET_BUCKET.toStack());

        for (Collection<ItemStack> c : categories.values()) {
            padOutCategory(c);
        }

        category_items.addAll(categories.get("building"));
        category_items.addAll(categories.get("functional"));
        category_items.addAll(categories.get("equipment"));
        category_items.addAll(categories.get("ingredients"));
        category_items.addAll(categories.get("mobs"));
        category_items.addAll(categories.get("artifacts"));
    }

    public static void initCategory(Collection<ItemStack> items) {
        for (int i = 0; i < 8; i++) {
            items.add(createEmptyStack(emptyAlpha+=0.001f));
        }
    }

    public static void padOutCategory(Collection<ItemStack> items) {
        int toAdd = (9 - items.size() % 9) % 9;

        for (int i = 0; i < toAdd; i++) {
            items.add(createEmptyStack(emptyAlpha+=0.001f));
        }
    }

    public static ItemStack createHeadertack(float alpha, Component name) {
        Components.DynamicColorComponent color = new Components.DynamicColorComponent(0, alpha, 0, 0, 0, 0, 0, 0, 0xF000F0);
        ItemStack stack = (BeyondItems.GRAVISTAR.toStack());
        stack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        stack.set(DataComponents.CUSTOM_NAME, name);
        stack.set(BeyondComponents.COLOR_COMPONENT, color);
        return stack;
    }

    public static ItemStack createEmptyStack(float alpha) {
        Components.DynamicColorComponent color = new Components.DynamicColorComponent(0, 0, 0, alpha, 0, 0, 0, 0, 0xF000F0);
        ItemStack stack = ItemStack.EMPTY;
        stack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        stack.set(BeyondComponents.COLOR_COMPONENT, color);
        return stack;
    }

    @SubscribeEvent
    public static void onRenderCreativeInventory(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof CreativeModeInventoryScreenAccessor creativeScreen)) return;
        CreativeModeTab selectedTab = CreativeModeInventoryScreenAccessor.getSelectedTab();
        if (selectedTab == null) return;
        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab);
        if (tabKey == null || !(tabKey.toString().equals("the_beyond:the_beyond"))) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        CreativeModeInventoryScreen mainScreen = (CreativeModeInventoryScreen) creativeScreen;

        for (int i = 0; i < 5; i++) {
            Slot slot = mainScreen.getMenu().slots.get(i*9);
            if (slot.getItem().has(DataComponents.CUSTOM_NAME)) {
                Component text = slot.getItem().get(DataComponents.CUSTOM_NAME);
                guiGraphics.fill(mainScreen.getGuiLeft() + slot.x-1, mainScreen.getGuiTop() + slot.y-1,mainScreen.getGuiLeft() + slot.x + 162, mainScreen.getGuiTop() + slot.y + 17, Color.LIGHT_GRAY.getRGB());
                guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/creative_inventory/"+text.getString().toLowerCase()+".png"), mainScreen.getGuiLeft() + slot.x, mainScreen.getGuiTop() + slot.y,0,0,16,16,16,16);
                guiGraphics.drawString(Minecraft.getInstance().font, text, mainScreen.getGuiLeft() + slot.x + 18, mainScreen.getGuiTop() + slot.y+4, Color.DARK_GRAY.getRGB(), false);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderToolTip(RenderTooltipEvent.Pre event) {
        if (event.getItemStack().getItem() == BeyondItems.GRAVISTAR.get()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Pre event) {
        //TODO this thing doesn't even work LOL
        //you still have to reopen the inventory to refresh.
        Screen screen = event.getScreen();
        if (!(screen instanceof CreativeModeInventoryScreenAccessor creativeScreen)) return;
        CreativeModeTab selectedTab = CreativeModeInventoryScreenAccessor.getSelectedTab();
        if (selectedTab == null) return;
        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab);
        if (tabKey == null || !(tabKey.toString().equals("the_beyond:the_beyond"))) return;

        if (selectedTab instanceof CreativeModeTabAccessor creativeTab) {
                //creativeScreen.callRefreshCurrentTabContents(category_items);
                creativeTab.setDisplayItems(category_items);
                //creativeScreen.callRefreshSearchResults();
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof CreativeModeInventoryScreenAccessor creativeScreen)) return;
        CreativeModeTab selectedTab = CreativeModeInventoryScreenAccessor.getSelectedTab();
        if (selectedTab == null) return;
        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab);
        if (tabKey == null || !(tabKey.toString().equals("the_beyond:the_beyond"))) return;

        if (selectedTab instanceof CreativeModeTabAccessor creativeTab) {
            if (creativeTab.getDisplayItems().size()<2) {
                creativeScreen.callRefreshCurrentTabContents(category_items);
                creativeTab.setDisplayItems(category_items);
                //creativeScreen.callRefreshSearchResults();
            }
        }
    }


    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(BeyondItems.LINER.get());
            event.accept(BeyondItems.HOLLOWER.get());
            event.accept(BeyondItems.FILLER.get());
        }
        if (event.getTabKey() == THE_BEYOND.getKey()) {
            List<ItemStack> uniqueItems = new ArrayList<>();
            Set<Item> addedItems = new HashSet<>();

            for (ItemStack stack : category_items) {
                if (stack.isEmpty()) continue;
                if (stack.has(BeyondComponents.COLOR_COMPONENT)) continue;

                if (addedItems.add(stack.getItem())) {
                    uniqueItems.add(stack);
                }
            }

            event.acceptAll(uniqueItems);
        }
    }
}
