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

    static boolean changed = false;
    static boolean biomes = false;

    static ResourceLocation BIOME = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/creative_inventory/biome.png");
    static ResourceLocation CATEGORY = ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"textures/gui/container/creative_inventory/category.png");

    static int buttonX = 174;
    static int buttonY = 5;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        category_items = new ArrayList<ItemStack>();
        biome_items = new ArrayList<ItemStack>();

        createCategoryItems();
        createBiomeItems();
    }

    private static void createCategoryItems() {
        Map<String, Collection<ItemStack>> categories = new HashMap<>();

        categories.put("building", new ArrayList<ItemStack>());
        categories.put("functional", new ArrayList<ItemStack>());
        categories.put("equipment", new ArrayList<ItemStack>());
        categories.put("ingredients", new ArrayList<ItemStack>());
        categories.put("artifacts", new ArrayList<ItemStack>());
        categories.put("mobs", new ArrayList<ItemStack>());

        categories.get("building").add(createHeadertack(headerAlpha+=0.001f, Component.literal("building")));
        categories.get("functional").add(createHeadertack(headerAlpha+=0.001f, Component.literal("functional")));
        categories.get("equipment").add(createHeadertack(headerAlpha+=0.001f, Component.literal("equipment")));
        categories.get("ingredients").add(createHeadertack(headerAlpha+=0.001f, Component.literal("ingredients")));
        categories.get("mobs").add(createHeadertack(headerAlpha+=0.001f, Component.literal("mobs")));
        categories.get("artifacts").add(createHeadertack(headerAlpha+=0.001f, Component.literal("artifacts")));

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
        categories.get("equipment").add(PotionContents.createItemStack(Items.SPLASH_POTION, BeyondPotions.DEAFENING));
        categories.get("equipment").add(PotionContents.createItemStack(Items.LINGERING_POTION, BeyondPotions.DEAFENING));

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


    private static void createBiomeItems() {
        Map<String, Collection<ItemStack>> categories = new HashMap<>();

        categories.put("the end", new ArrayList<ItemStack>());
        categories.put("attracta expanse", new ArrayList<ItemStack>());
        categories.put("peer lands", new ArrayList<ItemStack>());
        categories.put("the paths", new ArrayList<ItemStack>());
        categories.put("lustrous echoes", new ArrayList<ItemStack>());
        categories.put("fumarole uplands", new ArrayList<ItemStack>());
        categories.put("chestral hollows", new ArrayList<ItemStack>());

        categories.get("the end").add(createHeadertack(headerAlpha+=0.001f, Component.literal("the_end")));
        categories.get("attracta expanse").add(createHeadertack(headerAlpha+=0.001f, Component.literal("attracta_expanse")));
        categories.get("peer lands").add(createHeadertack(headerAlpha+=0.001f, Component.literal("peer_lands")));
        categories.get("the paths").add(createHeadertack(headerAlpha+=0.001f, Component.literal("the_paths")));
        categories.get("lustrous echoes").add(createHeadertack(headerAlpha+=0.001f, Component.literal("lustrous_echoes")));
        categories.get("fumarole uplands").add(createHeadertack(headerAlpha+=0.001f, Component.literal("fumarole_uplands")));
        categories.get("chestral hollows").add(createHeadertack(headerAlpha+=0.001f, Component.literal("chestral_hollows")));

        for (Collection<ItemStack> c : categories.values()) {
            initCategory(c);
        }

        categories.get("the end").add(Blocks.END_STONE.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.END_STONE_BRICKS.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.END_STONE_BRICK_STAIRS.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.END_STONE_BRICK_SLAB.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.END_STONE_BRICK_WALL.asItem().getDefaultInstance());
        categories.get("the end").add(BeyondBlocks.ENGRAVED_END_STONE.toStack());
        categories.get("the end").add(Blocks.PURPUR_BLOCK.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.PURPUR_PILLAR.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.PURPUR_STAIRS.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.PURPUR_SLAB.asItem().getDefaultInstance());
        categories.get("the end").add(Items.SHULKER_SPAWN_EGG.getDefaultInstance());
        categories.get("the end").add(Items.SHULKER_SHELL.getDefaultInstance());
        categories.get("the end").add(Items.SHULKER_BOX.getDefaultInstance());
        categories.get("the end").add(Items.ELYTRA.getDefaultInstance());
        categories.get("the end").add(Blocks.CHORUS_PLANT.asItem().getDefaultInstance());
        categories.get("the end").add(Blocks.CHORUS_FLOWER.asItem().getDefaultInstance());
        categories.get("the end").add(Items.CHORUS_FRUIT.getDefaultInstance());
        categories.get("the end").add(Items.POPPED_CHORUS_FRUIT.getDefaultInstance());
        categories.get("the end").add(Blocks.END_ROD.asItem().getDefaultInstance());
        categories.get("the end").add(BeyondBlocks.ZYMOTE.toStack());
        categories.get("the end").add(BeyondBlocks.REACHING_ZYMOTE.toStack());
        categories.get("the end").add(BeyondBlocks.CREEPING_ZYMOTE.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN.toStack());
        categories.get("the end").add(BeyondBlocks.GUSTER.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN_PILLAR.toStack());
        categories.get("the end").add(BeyondBlocks.AMPHORA.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN_TILES.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN_MOSAIC.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN_STAIRS.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN_SLAB.toStack());
        categories.get("the end").add(BeyondBlocks.PORTELAIN_DOOR.toStack());
        categories.get("the end").add(BeyondBlocks.BONFIRE.toStack());
        categories.get("the end").add(BeyondItems.VOID_CRYSTAL.toStack());
        categories.get("the end").add(BeyondItems.GRAVISTAR.toStack());

        categories.get("the end").add(BeyondItems.LANTERN_SPAWN_EGG.toStack());
        categories.get("the end").add(BeyondItems.LANTERN_SHED.toStack());
        categories.get("the end").add(BeyondItems.ECTOPLASM.toStack());
        categories.get("the end").add(BeyondItems.TOTEM_OF_RESPITE.toStack());
        categories.get("the end").add(BeyondItems.ETHER_CLOAK.toStack());

        categories.get("the end").add(BeyondItems.REMEMBRANCE_BEADS.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_IDOL.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_BRACE.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_RING.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_MEMORY.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_HORN.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_LACE.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_BROCHE.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_LIFE.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_HOME.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_EYE.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_SPIKE.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_ORNAMENT.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_MOUNT.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_HAND.toStack());
        categories.get("the end").add(BeyondItems.REMEMBRANCE_CLOTH.toStack());

        categories.get("the paths").add(BeyondBlocks.AURORACITE.toStack());
        categories.get("the paths").add(BeyondItems.ABYSSAL_NOMAD_SPAWN_EGG.toStack());
        categories.get("the paths").add(BeyondItems.ABYSSAL_SHROUD.toStack());
        categories.get("the paths").add(BeyondItems.PATHFINDER_BOOTS.toStack());
        categories.get("the paths").add(BeyondBlocks.MEMOR.toStack());
        categories.get("the paths").add(BeyondBlocks.CHISELED_MEMOR.toStack());
        categories.get("the paths").add(BeyondBlocks.MEMOR_PILLAR.toStack());
        categories.get("the paths").add(BeyondBlocks.MEMOR_STAIRS.toStack());
        categories.get("the paths").add(BeyondBlocks.MEMOR_FAUCET.toStack());

        categories.get("attracta expanse").add(BeyondBlocks.PLATE_BLOCK.toStack());
        categories.get("attracta expanse").add(BeyondBlocks.PLATED_END_STONE.toStack());
        categories.get("attracta expanse").add(BeyondBlocks.POLAR_PILLAR.toStack());
        categories.get("attracta expanse").add(BeyondBlocks.POLAR_BULB.toStack());
        categories.get("attracta expanse").add(BeyondBlocks.POLAR_ANTENNA.toStack());
        categories.get("attracta expanse").add(BeyondItems.MAGNOLILLY.toStack());
        categories.get("attracta expanse").add(BeyondItems.ANCHOR_LEGGINGS.toStack());
        categories.get("attracta expanse").add(BeyondItems.MAGNET.toStack());
        categories.get("attracta expanse").add(BeyondItems.FERROPETAL.toStack());
        categories.get("attracta expanse").add(BeyondItems.ENDERGLOP_SPAWN_EGG.toStack());
        categories.get("attracta expanse").add(BeyondItems.FERROJELLY.toStack());
        categories.get("attracta expanse").add(BeyondBlocks.FERROJELLY_BLOCK.toStack());

        categories.get("peer lands").add(BeyondBlocks.OBIROOT.toStack());
        categories.get("peer lands").add(BeyondBlocks.PEEPING_OBIROOT.toStack());
        categories.get("peer lands").add(BeyondBlocks.XYLEM.toStack());
        categories.get("peer lands").add(BeyondItems.ENADRAKE_SPAWN_EGG.toStack());
        categories.get("peer lands").add(BeyondBlocks.OBIROOT_SPROUT.toStack());
        categories.get("peer lands").add(PotionContents.createItemStack(Items.SPLASH_POTION, BeyondPotions.DEAFENING));
        categories.get("peer lands").add(PotionContents.createItemStack(Items.LINGERING_POTION, BeyondPotions.DEAFENING));
        categories.get("peer lands").add(BeyondBlocks.ENADRAKE_HUT.toStack());
        categories.get("peer lands").add(BeyondBlocks.ENADRAKE_FLARE.toStack());
        categories.get("peer lands").add(BeyondBlocks.REFUGE.toStack());
        categories.get("peer lands").add(BeyondBlocks.OBIROOT_ARM.toStack());
        categories.get("peer lands").add(BeyondItems.ENATIOUS_TOTEM_SPAWN_EGG.toStack());
        categories.get("peer lands").add(BeyondBlocks.ENATIOUS_TOTEM_SEED.toStack());

        categories.get("fumarole uplands").add(BeyondBlocks.GAUSSANITE.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.GAUSS_VENT.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BELLOW.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.SOOT_BLOCK.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BRITTLE_METAL.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.MOLTEN_METAL.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BRITTLE_METAL_BLOCK.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BRITTLE_METAL_STAIRS.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BRITTLE_METAL_SLAB.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BRITTLE_METAL_DOOR.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRITTLE_SWORD.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRITTLE_SHOVEL.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRITTLE_PICKAXE.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRITTLE_AXE.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRITTLE_HOE.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRITTLE_METAL_SHEET.toStack());
        categories.get("fumarole uplands").add(BeyondItems.PRISMUTH.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.PROJECTOR.toStack());
        categories.get("fumarole uplands").add(BeyondItems.PRISMOGRAPH.toStack());
        categories.get("fumarole uplands").add(BeyondBlocks.BLINDING_THORN.toStack());
        categories.get("fumarole uplands").add(BeyondItems.SMOKE_FUSE.toStack());
        categories.get("fumarole uplands").add(BeyondItems.BRUBBLE_SPAWN_EGG.toStack());

        categories.get("lustrous echoes").add(BeyondBlocks.NACRE.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.UNSTABLE_NACRE.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.PEARL.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.PEARL_BRICKS.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.COBBLED_PEARL.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.COBBLED_PEARL_BRICKS.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.PEARL_MIRROR.toStack());
        categories.get("lustrous echoes").add(BeyondBlocks.PEARL_CHIMES.toStack());
        categories.get("lustrous echoes").add(BeyondItems.OCARINA.toStack());
        categories.get("lustrous echoes").add(BeyondItems.TRINKET_BUCKET.toStack());

        categories.get("chestral hollows").add(BeyondBlocks.VILET.toStack());
        categories.get("chestral hollows").add(BeyondBlocks.VILE_GROWTH.toStack());
        categories.get("chestral hollows").add(BeyondBlocks.BLEEDING_THORN.toStack());
        categories.get("chestral hollows").add(BeyondBlocks.PERKA_STALK.toStack());
        categories.get("chestral hollows").add(BeyondBlocks.PERKA_STALK_MOUTH.toStack());
        categories.get("chestral hollows").add(BeyondItems.STALKER_SEGMENT.toStack());
        categories.get("chestral hollows").add(BeyondItems.COILED_STALK.toStack());
        categories.get("chestral hollows").add(BeyondBlocks.COIL_VERTEBRAE.toStack());

        for (Collection<ItemStack> c : categories.values()) {
            padOutCategory(c);
        }

        biome_items.addAll(categories.get("attracta expanse"));
        biome_items.addAll(categories.get("peer lands"));
        biome_items.addAll(categories.get("the paths"));
        biome_items.addAll(categories.get("lustrous echoes"));
        biome_items.addAll(categories.get("fumarole uplands"));
        biome_items.addAll(categories.get("chestral hollows"));
        biome_items.addAll(categories.get("the end"));
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

        if (!changed && selectedTab instanceof CreativeModeTabAccessor creativeTab) {
            CreativeModeInventoryScreen inventoryScreen = (CreativeModeInventoryScreen) creativeScreen;
            inventoryScreen.getMenu().items.clear();
            inventoryScreen.getMenu().items.addAll(category_items);
            changed = true;
        }

        for (int i = 0; i < 5; i++) {
            Slot slot = mainScreen.getMenu().slots.get(i*9);
            if (slot.getItem().has(DataComponents.CUSTOM_NAME)) {
                Component icon = slot.getItem().get(DataComponents.CUSTOM_NAME);
                Component text = Component.translatable("inventory.the_beyond.category." + slot.getItem().get(DataComponents.CUSTOM_NAME).getString());
                //TODO change the gray to match inventory gray
                guiGraphics.fill(mainScreen.getGuiLeft() + slot.x-1, mainScreen.getGuiTop() + slot.y-1,mainScreen.getGuiLeft() + slot.x + 162, mainScreen.getGuiTop() + slot.y + 17, new Color(198, 198,198).getRGB());
                guiGraphics.blit(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "textures/gui/container/creative_inventory/"+icon.getString()+".png"), mainScreen.getGuiLeft() + slot.x, mainScreen.getGuiTop() + slot.y,0,0,16,16,16,16);
                guiGraphics.drawString(Minecraft.getInstance().font, text, mainScreen.getGuiLeft() + slot.x + 19, mainScreen.getGuiTop() + slot.y+4, Color.DARK_GRAY.getRGB(), false);
            }
        }

        guiGraphics.blit(getTexture(), mainScreen.getGuiLeft() + buttonX, mainScreen.getGuiTop() + buttonY,0,0,14,8,14,8);
    }

    public static ResourceLocation getTexture() {
        return biomes ? BIOME : CATEGORY;
    }

    @SubscribeEvent
    public static void onRenderToolTip(RenderTooltipEvent.Pre event) {
        if (event.getItemStack().getItem() == BeyondItems.GRAVISTAR.get()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof CreativeModeInventoryScreenAccessor creativeScreen)) return;
        CreativeModeTab selectedTab = CreativeModeInventoryScreenAccessor.getSelectedTab();
        if (selectedTab == null) return;
        ResourceLocation tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(selectedTab);
        if (tabKey == null || !(tabKey.toString().equals("the_beyond:the_beyond"))) return;
        CreativeModeInventoryScreen mainScreen = (CreativeModeInventoryScreen) creativeScreen;

        if (selectedTab instanceof CreativeModeTabAccessor creativeTab) {
            if(!isOverArea(mainScreen.getGuiLeft() + buttonX, mainScreen.getGuiTop() + buttonY, 14, 8, event.getMouseX(), event.getMouseY())) return;
            if (biomes) {
                creativeTab.setDisplayItems(category_items);
                biomes = !biomes;
            }
                else {
                creativeTab.setDisplayItems(biome_items);
                biomes = !biomes;
            }
            creativeScreen.callRefreshSearchResults();
        }
    }

    public static boolean isOverArea(int x, int y, int xSize, int ySize, double mouseX, double mouseY) {
        if (mouseX < x || mouseX > x+xSize) return false;
        if (mouseY < y || mouseY > y+ySize) return false;
        return true;
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
