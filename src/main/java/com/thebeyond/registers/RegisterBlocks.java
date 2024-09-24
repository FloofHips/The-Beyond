package com.thebeyond.registers;

import com.thebeyond.common.blocks.PolarPillarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class RegisterBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    public static final DeferredBlock<PolarPillarBlock> POLAR_PILLAR = BLOCKS.registerBlock("polar_pillar", PolarPillarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .lightLevel(PolarPillarBlock.STATE_TO_LUMINANCE)
                    .randomTicks()
    );
    public static final DeferredItem<BlockItem> POLAR_PILLAR_ITEM = RegisterItems.ITEMS.registerSimpleBlockItem("polar_pillar", POLAR_PILLAR);
}
