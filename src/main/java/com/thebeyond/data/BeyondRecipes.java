package com.thebeyond.data;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.data.tags.BeyondBlockTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

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
        stair(recipeOutput, BeyondBlocks.PORTELAIN_STAIRS.asItem(), BeyondBlocks.PORTELAIN.asItem());
        door(recipeOutput, BeyondBlocks.PORTELAIN_DOOR.asItem(), BeyondBlocks.PORTELAIN.asItem());

        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_TILES.get(), BeyondBlocks.PORTELAIN.get());
        cut(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_MOSAIC.get(), BeyondBlocks.PORTELAIN_TILES.get());
        threeByThreePacker(recipeOutput, RecipeCategory.REDSTONE, BeyondBlocks.FERROJELLY_BLOCK.get(), BeyondItems.FERROJELLY.get());
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BeyondBlocks.GUSTER.get(), 4).define('C', BeyondBlocks.PORTELAIN.get()).define('B', Items.WIND_CHARGE).pattern(" C ").pattern("CBC").pattern(" C ").unlockedBy(getHasName(Items.WIND_CHARGE), has(Items.WIND_CHARGE)).unlockedBy(getHasName(BeyondBlocks.PORTELAIN.asItem()), has(BeyondBlocks.PORTELAIN.asItem())).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, BeyondItems.MAGNET.get(), 1).define('M', BeyondItems.FERROJELLY).define('P', BeyondItems.FERROPETAL).pattern("M M").pattern("P P").pattern("PPP").unlockedBy(getHasName(BeyondItems.FERROPETAL), has(BeyondItems.FERROPETAL)).unlockedBy(getHasName(BeyondItems.FERROJELLY), has(BeyondItems.FERROJELLY)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.PATHFINDER_BOOTS.get(), 1).define('M', BeyondItems.ABYSSAL_SHROUD).pattern("M M").pattern("M M").unlockedBy(getHasName(BeyondItems.ABYSSAL_SHROUD), has(BeyondItems.ABYSSAL_SHROUD)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.ETHER_CLOAK.get(), 1).define('M', BeyondItems.ABYSSAL_SHROUD).define('N', BeyondItems.LANTERN_SHED).pattern("MMM").pattern("M M").pattern("NNN").unlockedBy(getHasName(BeyondItems.ABYSSAL_SHROUD), has(BeyondItems.ABYSSAL_SHROUD)).unlockedBy(getHasName(BeyondItems.LANTERN_SHED), has(BeyondItems.LANTERN_SHED)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.ANCHOR_LEGGINGS.get(), 1).define('M', BeyondItems.FERROPETAL).define('N', BeyondBlocks.PLATE_BLOCK.asItem()).define('O', Items.HEAVY_CORE).pattern("NON").pattern("M M").pattern("N N").unlockedBy(getHasName(Items.HEAVY_CORE), has(Items.HEAVY_CORE)).unlockedBy(getHasName(BeyondBlocks.PLATE_BLOCK.asItem()), has(BeyondBlocks.PLATE_BLOCK.asItem())).unlockedBy(getHasName(BeyondItems.FERROPETAL), has(BeyondItems.FERROPETAL)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, BeyondItems.GRAVISTAR.get(), 2).define('M', BeyondItems.VOID_CRYSTAL).define('N', BeyondItems.FERROJELLY).pattern(" M ").pattern("MNM").pattern(" M ").unlockedBy(getHasName(BeyondItems.VOID_CRYSTAL), has(BeyondItems.VOID_CRYSTAL)).unlockedBy(getHasName(BeyondItems.FERROJELLY), has(BeyondItems.FERROJELLY)).save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BeyondBlocks.ECTOPLASM.get(), 4).define('M', BeyondItems.LANTERN_SHED).pattern(" M ").pattern("M M").pattern(" M ").unlockedBy(getHasName(BeyondItems.LANTERN_SHED), has(BeyondItems.LANTERN_SHED)).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BeyondItems.FERROJELLY.get(), 9).requires(BeyondBlocks.FERROJELLY_BLOCK.get()).unlockedBy(getHasName(BeyondBlocks.FERROJELLY_BLOCK.asItem()), has(BeyondBlocks.FERROJELLY_BLOCK.asItem())).save(recipeOutput);

        mosaicBuilder(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_PILLAR.asItem(), BeyondBlocks.PORTELAIN.asItem());
        mosaicBuilder(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.ENGRAVED_END_STONE.asItem(), Blocks.END_STONE.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_PILLAR.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_MOSAIC.asItem(), BeyondBlocks.PORTELAIN.asItem());
        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.PORTELAIN_TILES.asItem(), BeyondBlocks.PORTELAIN.asItem());

        stonecutterResultFromBase(recipeOutput, RecipeCategory.BUILDING_BLOCKS, BeyondBlocks.ENGRAVED_END_STONE.asItem(), Blocks.END_STONE.asItem());

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
