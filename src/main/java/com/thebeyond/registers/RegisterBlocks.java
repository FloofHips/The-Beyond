package com.thebeyond.registers;

import com.thebeyond.blocks.PolarPillarBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class RegisterBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    //example
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = RegisterItems.ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    public static final DeferredBlock<PolarPillarBlock> POLAR_PILLAR = BLOCKS.registerBlock("polar_pillar", PolarPillarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE));
    public static final DeferredItem<BlockItem> POLAR_PILLAR_ITEM = RegisterItems.ITEMS.registerSimpleBlockItem("polar_pillar", POLAR_PILLAR);
}
