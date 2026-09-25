package com.thebeyond.data;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.data.tags.BeyondBlockTags;
import com.thebeyond.data.tags.BeyondItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ArmorDyeRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

import java.util.concurrent.CompletableFuture;

public class BeyondRecipes extends RecipeProvider {
    public BeyondRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        super.buildRecipes(recipeOutput);

        planksFromLogs(recipeOutput, BeyondBlocks.XYLEM.asItem(), BeyondTags.OBIROOTS, 4);

        slab(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_SLAB.asItem(), BeyondBlocks.PORTELAIN.asItem());
        slab(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.BRITTLE_METAL_SLAB.asItem(), BeyondBlocks.BRITTLE_METAL_BLOCK.asItem());
        slab(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_SLAB.asItem(), BeyondBlocks.PEARL.asItem());
        slab(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_SLAB.asItem(), BeyondBlocks.PEARL_BRICKS.asItem());
        slab(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_SLAB.asItem(), BeyondBlocks.COBBLED_PEARL_BRICKS.asItem());

        stair(recipeOutput, BeyondBlocks.PORTELAIN_STAIRS.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stair(recipeOutput, BeyondBlocks.BRITTLE_METAL_STAIRS.asItem(), BeyondBlocks.BRITTLE_METAL_BLOCK.asItem());
        stair(recipeOutput, BeyondBlocks.PEARL_STAIRS.asItem(), BeyondBlocks.PEARL.asItem());
        stair(recipeOutput, BeyondBlocks.PEARL_BRICK_STAIRS.asItem(), BeyondBlocks.PEARL_BRICKS.asItem());
        stair(recipeOutput, BeyondBlocks.COBBLED_PEARL_BRICK_STAIRS.asItem(), BeyondBlocks.COBBLED_PEARL_BRICKS.asItem());

        wall(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_WALL.asItem(), BeyondBlocks.PORTELAIN.asItem());
        wall(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_WALL.asItem(), BeyondBlocks.PEARL.asItem());
        wall(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_WALL.asItem(), BeyondBlocks.PEARL_BRICKS.asItem());
        wall(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_WALL.asItem(), BeyondBlocks.COBBLED_PEARL_BRICKS.asItem());

        door(recipeOutput, BeyondBlocks.PORTELAIN_DOOR.asItem(), BeyondBlocks.PORTELAIN.asItem());
        door(recipeOutput, BeyondBlocks.BRITTLE_METAL_DOOR.asItem(), BeyondBlocks.BRITTLE_METAL_BLOCK.asItem());

        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.BRITTLE_METAL_BLOCK.get(), BeyondItems.BRITTLE_METAL_SHEET.get());
        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_TILES.get(), BeyondBlocks.PORTELAIN.get());
        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_MOSAIC.get(), BeyondBlocks.PORTELAIN_TILES.get());
        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL.get(), BeyondItems.PEARL_BEAD.get());
        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICKS.get(), BeyondBlocks.PEARL.get());
        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICKS.get(), BeyondBlocks.COBBLED_PEARL.get());

        threeByThreePacker(recipeOutput, RecipeCategory.REDSTONE, BeyondBlocks.FERROJELLY_BLOCK.get(), BeyondItems.FERROJELLY.get());
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BeyondBlocks.GUSTER.get(), 4).define('C', BeyondBlocks.PORTELAIN.get()).define('B', Items.WIND_CHARGE).pattern(" C ").pattern("CBC").pattern(" C ").unlockedBy(getHasName(Items.WIND_CHARGE), has(Items.WIND_CHARGE)).unlockedBy(getHasName(BeyondBlocks.PORTELAIN.asItem()), has(BeyondBlocks.PORTELAIN.asItem())).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BeyondBlocks.CHISELED_MEMOR.get(), 1).define('P', BeyondItems.PEARL_BEAD.get()).define('S', Tags.Items.RODS_WOODEN).pattern(" S ").pattern("P P").pattern(" P ").unlockedBy(getHasName(BeyondItems.PEARL_BEAD.get()), has(BeyondItems.PEARL_BEAD.get())).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, BeyondBlocks.BELLOW.get(), 1).requires(BeyondBlocks.GAUSS_VENT.get()).requires(Items.REDSTONE).unlockedBy(getHasName(BeyondBlocks.GAUSS_VENT.get()), has(BeyondBlocks.GAUSS_VENT.get())).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, BeyondItems.MEMORY_BANK.get(), 1).requires(BeyondTags.OBIROOTS).requires(Items.BOOK).requires(BeyondItems.PRISMUTH.get()).unlockedBy(getHasName(BeyondItems.PRISMOGRAPH), has(BeyondItems.PRISMOGRAPH)).unlockedBy(getHasName(BeyondItems.PRISMUTH), has(BeyondItems.PRISMUTH)).unlockedBy(getHasName(BeyondBlocks.PEEPING_OBIROOT), has(BeyondBlocks.PEEPING_OBIROOT)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BeyondBlocks.RICH_NACRE.get(), 8).define('N', BeyondBlocks.NACRE.get()).define('I', Tags.Items.NUGGETS_IRON).pattern("NNN").pattern("NIN").pattern("NNN").unlockedBy(getHasName(BeyondBlocks.NACRE.get()), has(BeyondBlocks.NACRE.get())).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BeyondBlocks.NACRE.get(), 8).define('N', BeyondBlocks.PALE_NACRE.get()).define('I', Tags.Items.NUGGETS_IRON).pattern("NNN").pattern("NIN").pattern("NNN").unlockedBy(getHasName(BeyondBlocks.PALE_NACRE.get()), has(BeyondBlocks.PALE_NACRE.get())).save(recipeOutput);


        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.BLACK_DYE, 4).requires(BeyondBlocks.RICH_NACRE.get()).unlockedBy(getHasName(BeyondBlocks.RICH_NACRE), has(BeyondBlocks.RICH_NACRE)).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.CYAN_DYE, 4).requires(BeyondBlocks.NACRE.get()).unlockedBy(getHasName(BeyondBlocks.NACRE), has(BeyondBlocks.NACRE)).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.WHITE_DYE, 4).requires(BeyondBlocks.PALE_NACRE.get()).unlockedBy(getHasName(BeyondBlocks.PALE_NACRE), has(BeyondBlocks.PALE_NACRE)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BeyondBlocks.PEARL_MIRROR.get(), 1).define('P', BeyondBlocks.PEARL.get()).pattern(" P ").pattern("P P").pattern(" P ").unlockedBy(getHasName(BeyondBlocks.PEARL.get()), has(BeyondBlocks.PEARL.get())).unlockedBy(getHasName(BeyondItems.PEARL_BEAD.get()), has(BeyondItems.PEARL_BEAD.get())).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, BeyondItems.OCARINA.get(), 1).define('P', BeyondItems.PEARL_BEAD.get()).pattern("PP ").pattern("P P").pattern("PP ").unlockedBy(getHasName(BeyondBlocks.PEARL.get()), has(BeyondBlocks.PEARL.get())).unlockedBy(getHasName(BeyondItems.PEARL_BEAD.get()), has(BeyondItems.PEARL_BEAD.get())).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, BeyondItems.PRISMOGRAPH.get(), 1).define('P', BeyondItems.PEARL_BEAD.get()).define('B', BeyondItems.PRISMUTH.get()).define('O', BeyondTags.OBIROOTS).pattern("BBB").pattern("OPO").pattern(" O ").unlockedBy(getHasName(BeyondItems.PRISMUTH.get()), has(BeyondItems.PRISMUTH.get())).unlockedBy(getHasName(BeyondItems.PEARL_BEAD.get()), has(BeyondItems.PEARL_BEAD.get())).unlockedBy(getHasName(BeyondBlocks.PEEPING_OBIROOT), has(BeyondBlocks.PEEPING_OBIROOT)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BeyondBlocks.PROJECTOR.get(), 1).define('P', BeyondTags.MIRRORS).define('B', BeyondItems.PRISMUTH.get()).define('O', BeyondTags.OBIROOTS).define('R', Items.REDSTONE).pattern("BBB").pattern("OPO").pattern("ORO").unlockedBy(getHasName(BeyondItems.PRISMUTH.get()), has(BeyondItems.PRISMUTH.get())).unlockedBy(getHasName(BeyondItems.PEARL_BEAD.get()), has(BeyondItems.PEARL_BEAD.get())).unlockedBy(getHasName(BeyondBlocks.PEEPING_OBIROOT), has(BeyondBlocks.PEEPING_OBIROOT)).unlockedBy(getHasName(BeyondItems.REMEMBRANCE_MEMORY), has(BeyondTags.REMEMBRANCES)).unlockedBy(getHasName(BeyondItems.SNAPSHOT), has(BeyondItems.SNAPSHOT)).unlockedBy(getHasName(BeyondItems.PRISMOGRAPH), has(BeyondItems.PRISMOGRAPH)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, BeyondItems.MAGNET.get(), 1).define('M', BeyondItems.FERROJELLY).define('P', BeyondItems.FERROPETAL).pattern("M M").pattern("P P").pattern("PPP").unlockedBy(getHasName(BeyondItems.FERROPETAL), has(BeyondItems.FERROPETAL)).unlockedBy(getHasName(BeyondItems.FERROJELLY), has(BeyondItems.FERROJELLY)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.PATHFINDER_BOOTS.get(), 1).define('M', BeyondItems.ABYSSAL_SHROUD).pattern("M M").pattern("M M").unlockedBy(getHasName(BeyondItems.ABYSSAL_SHROUD), has(BeyondItems.ABYSSAL_SHROUD)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.ETHER_CLOAK.get(), 1).define('M', BeyondItems.ABYSSAL_SHROUD).define('N', BeyondItems.LANTERN_SHED).pattern("MMM").pattern("M M").pattern("NNN").unlockedBy(getHasName(BeyondItems.ABYSSAL_SHROUD), has(BeyondItems.ABYSSAL_SHROUD)).unlockedBy(getHasName(BeyondItems.LANTERN_SHED), has(BeyondItems.LANTERN_SHED)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.ANCHOR_LEGGINGS.get(), 1).define('M', BeyondItems.FERROPETAL).define('N', BeyondBlocks.PLATE_BLOCK.asItem()).define('O', Items.HEAVY_CORE).pattern("NON").pattern("M M").pattern("N N").unlockedBy(getHasName(Items.HEAVY_CORE), has(Items.HEAVY_CORE)).unlockedBy(getHasName(BeyondBlocks.PLATE_BLOCK.asItem()), has(BeyondBlocks.PLATE_BLOCK.asItem())).unlockedBy(getHasName(BeyondItems.FERROPETAL), has(BeyondItems.FERROPETAL)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.GRAVISTAR.get(), 2).define('M', BeyondItems.VOID_CRYSTAL).define('N', BeyondItems.FERROJELLY).pattern(" M ").pattern("MNM").pattern(" M ").unlockedBy(getHasName(BeyondItems.VOID_CRYSTAL), has(BeyondItems.VOID_CRYSTAL)).unlockedBy(getHasName(BeyondItems.FERROJELLY), has(BeyondItems.FERROJELLY)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BeyondBlocks.REFUGE.get(), 1).define('R', BeyondTags.OBIROOTS).define('S', Blocks.END_STONE).define('F', BeyondBlocks.ENADRAKE_FLARE).pattern("RRR").pattern("RFR").pattern("SSS").unlockedBy(getHasName(BeyondBlocks.ENADRAKE_FLARE.asItem()), has(BeyondBlocks.ENADRAKE_FLARE.asItem())).save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BeyondBlocks.ECTOPLASM.get(), 4).requires(BeyondItems.LANTERN_SHED.get()).unlockedBy(getHasName(BeyondItems.LANTERN_SHED), has(BeyondItems.LANTERN_SHED)).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BeyondItems.FERROJELLY.get(), 9).requires(BeyondBlocks.FERROJELLY_BLOCK.get()).unlockedBy(getHasName(BeyondBlocks.FERROJELLY_BLOCK.asItem()), has(BeyondBlocks.FERROJELLY_BLOCK.asItem())).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BeyondItems.COILED_STALK.get(), 2).requires(BeyondItems.STALKER_SEGMENT.get()).requires(Ingredient.of(BeyondTags.ROOTS), 3).unlockedBy(getHasName(BeyondItems.STALKER_SEGMENT.asItem()), has(BeyondItems.STALKER_SEGMENT.asItem())).unlockedBy(getHasName(BeyondBlocks.BLEEDING_THORN.asItem()), has(BeyondTags.ROOTS)).save(recipeOutput);

        mosaicBuilder(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_PILLAR.asItem(), BeyondBlocks.PORTELAIN.asItem());
        mosaicBuilder(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.ENGRAVED_END_STONE.asItem(), Blocks.END_STONE.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_PILLAR.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_MOSAIC.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_STAIRS.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_SLAB.asItem(), BeyondBlocks.PORTELAIN.asItem(),2);
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_TILES.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_WALL.asItem(), BeyondBlocks.PORTELAIN.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_STAIRS.asItem(), BeyondBlocks.PEARL.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICKS.asItem(), BeyondBlocks.PEARL.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_SLAB.asItem(), BeyondBlocks.PEARL.asItem(),2);
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_WALL.asItem(), BeyondBlocks.PEARL.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_STAIRS.asItem(), BeyondBlocks.PEARL.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_SLAB.asItem(), BeyondBlocks.PEARL.asItem(),2);
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_WALL.asItem(), BeyondBlocks.PEARL.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_STAIRS.asItem(), BeyondBlocks.PEARL_BRICKS.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_SLAB.asItem(), BeyondBlocks.PEARL_BRICKS.asItem(),2);
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PEARL_BRICK_WALL.asItem(), BeyondBlocks.PEARL_BRICKS.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_STAIRS.asItem(), BeyondBlocks.COBBLED_PEARL.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_SLAB.asItem(), BeyondBlocks.COBBLED_PEARL.asItem(),2);
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_WALL.asItem(), BeyondBlocks.COBBLED_PEARL.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_STAIRS.asItem(), BeyondBlocks.COBBLED_PEARL_BRICKS.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_SLAB.asItem(), BeyondBlocks.COBBLED_PEARL_BRICKS.asItem(),2);
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.COBBLED_PEARL_BRICK_WALL.asItem(), BeyondBlocks.COBBLED_PEARL_BRICKS.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.BRITTLE_METAL_STAIRS.asItem(), BeyondBlocks.BRITTLE_METAL_BLOCK.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.BRITTLE_METAL_SLAB.asItem(), BeyondBlocks.BRITTLE_METAL_BLOCK.asItem(),2);

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.ENGRAVED_END_STONE.asItem(), Blocks.END_STONE.asItem());

        SpecialRecipeBuilder.special(ArmorDyeRecipe::new).save(recipeOutput, "smoke_fuse_dye");
        SpecialRecipeBuilder.special(ArmorDyeRecipe::new).save(recipeOutput, "memory_bank_dye");

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(BeyondBlocks.COBBLED_PEARL.asItem()),
                        RecipeCategory.BUILDING_BLOCKS,
                        BeyondBlocks.PEARL.asItem(),
                        0.1f,
                        200
                )
                .unlockedBy("has_cobbled_pearl", has(BeyondBlocks.COBBLED_PEARL.asItem()))
                .save(recipeOutput, "pearl_smelting");


        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(BeyondBlocks.RICH_NACRE.asItem()),
                        RecipeCategory.BUILDING_BLOCKS,
                        BeyondBlocks.NACRE.asItem(),
                        0.1f,
                        200
                )
                .unlockedBy("has_rich_nacre", has(BeyondBlocks.RICH_NACRE.asItem()))
                .save(recipeOutput, "nacre_smelting");

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(BeyondBlocks.NACRE.asItem()),
                        RecipeCategory.BUILDING_BLOCKS,
                        BeyondBlocks.PALE_NACRE.asItem(),
                        0.1f,
                        200
                )
                .unlockedBy("has_nacre", has(BeyondBlocks.NACRE.asItem()))
                .save(recipeOutput, "pale_nacre_smelting");

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(BeyondBlocks.ZYMOTE.asItem()),
                        RecipeCategory.BUILDING_BLOCKS,
                        BeyondBlocks.PORTELAIN.asItem(),
                        0.1f,
                        200
                )
                .unlockedBy("has_zymote", has(BeyondBlocks.ZYMOTE.asItem()))
                .save(recipeOutput, "portelain_smelting");
    }

    protected static void stair(RecipeOutput recipeOutput, ItemLike stair, ItemLike material) {
        stairBuilder(stair, Ingredient.of(new ItemLike[]{material})).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }
    protected static void door(RecipeOutput recipeOutput, ItemLike door, ItemLike material) {
        doorBuilder(door, Ingredient.of(new ItemLike[]{material})).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }
}
