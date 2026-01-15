package com.thebeyond.data;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.HashSet;
import java.util.Set;

public class BeyondBlockLoot extends BlockLootSubProvider {

    private final Set<Block> generatedLootTables = new HashSet<>();

    public BeyondBlockLoot(HolderLookup.Provider holder) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), holder);
    }

    @Override
    protected void generate() {
        dropSelf(BeyondBlocks.ENGRAVED_END_STONE.get());

        dropSelf(BeyondBlocks.ZYMOTE.get());
        dropSelf(BeyondBlocks.PORTELAIN.get());
        dropSelf(BeyondBlocks.PORTELAIN_STAIRS.get());
        dropSelf(BeyondBlocks.PORTELAIN_PILLAR.get());
        dropSelf(BeyondBlocks.PORTELAIN_MOSAIC.get());
        dropSelf(BeyondBlocks.PORTELAIN_TILES.get());
        dropSelf(BeyondBlocks.GUSTER.get());
        dropSelf(BeyondBlocks.OBIROOT_SPROUT.get());

        dropSelf(BeyondBlocks.POLAR_PILLAR.get());
        dropSelf(BeyondBlocks.POLAR_ANTENNA.get());
        dropSelf(BeyondBlocks.PLATE_BLOCK.get());

        add(BeyondBlocks.VOID_CRYSTAL.get(), createSilkTouchOnlyTable(BeyondItems.VOID_CRYSTAL.get()));
        add(BeyondBlocks.MAGNOLILLY.get(), createOreDrop(BeyondBlocks.MAGNOLILLY.get(), BeyondItems.FERROPETAL.get()));

        add(BeyondBlocks.OBIROOT.get(), block -> createSilkTouchDispatchTable(
                block,
                LootItem.lootTableItem(BeyondBlocks.XYLEM.get())
        ));
        add(BeyondBlocks.PEEPING_OBIROOT.get(), block -> createSilkTouchDispatchTable(
                block,
                LootItem.lootTableItem(BeyondBlocks.XYLEM.get())
        ));
        add(BeyondBlocks.PLATED_END_STONE.get(), block -> createSilkTouchDispatchTable(
                block,
                LootItem.lootTableItem(Blocks.END_STONE)
        ));
        add(BeyondBlocks.REACHING_ZYMOTE.get(), block -> createShearsDispatchTable(block, LootItem.lootTableItem(block).apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))));
        add(BeyondBlocks.CREEPING_ZYMOTE.get(), createMultifaceBlockDrops(BeyondBlocks.CREEPING_ZYMOTE.get(), HAS_SHEARS));

        add(BeyondBlocks.PORTELAIN_DOOR.get(), this::createDoorTable);
        add(BeyondBlocks.PORTELAIN_SLAB.get(), this::createSlabItemTable);
    }

    @Override
    protected void add(Block block, LootTable.Builder builder) {
        this.generatedLootTables.add(block);
        this.map.put(block.getLootTable(), builder);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return generatedLootTables;
    }
}
