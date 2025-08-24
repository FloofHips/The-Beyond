package com.thebeyond.common.registry;

import com.google.common.collect.Sets;
import com.thebeyond.common.blocks.AuroraciteBlock;
import com.thebeyond.common.blocks.MagnolillyBlock;
import com.thebeyond.common.blocks.PolarAntennaBlock;
import com.thebeyond.common.blocks.PolarPillarBlock;
import com.thebeyond.common.blocks.*;
import com.thebeyond.common.fluids.GellidVoidBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashSet;
import java.util.function.Supplier;

import static com.thebeyond.TheBeyond.MODID;

@SuppressWarnings("unused")
public class BeyondBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    public static LinkedHashSet<DeferredHolder<Item, BlockItem>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    //Generic
    public static final DeferredBlock<Block> VOID_FLAME = registerBlock("void_flame",
            () -> new VoidFlameBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.SHROOMLIGHT))

    );
    public static final DeferredBlock<Block> VOID_CRYSTAL = registerBlock("void_crystal",
            () -> new VoidCrystalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.SHROOMLIGHT)
                    .noOcclusion()
                    .offsetType(BlockBehaviour.OffsetType.XZ)
                    .dynamicShape())

    );
    public static final DeferredBlock<GellidVoidBlock> GELLID_VOID = registerBlockWithoutItem("gellid_void_block",
            () -> new GellidVoidBlock(BeyondFluids.GELLID_VOID_FLOWING.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .forceSolidOff()
                    .noCollission()
                    .noOcclusion())
    );
    //Path
    public static final DeferredBlock<Block> AURORACITE = registerBlock("auroracite",
            () -> new AuroraciteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .sound(SoundType.SHROOMLIGHT))
    );
    //Attracta Expanse
    public static final DeferredBlock<PolarPillarBlock> POLAR_PILLAR = registerBlock("polar_pillar",
            () -> new PolarPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .lightLevel(PolarPillarBlock.STATE_TO_LUMINANCE)
                    .randomTicks())
    );
    public static final DeferredBlock<PolarBulbBlock> POLAR_BULB = registerBlock("polar_bulb",
            () -> new PolarBulbBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .lightLevel(PolarBulbBlock.STATE_TO_LUMINANCE)
                    .randomTicks())
    );
    public static final DeferredBlock<PolarAntennaBlock> POLAR_ANTENNA = registerBlock("polar_antenna",
            () -> new PolarAntennaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .lightLevel(PolarAntennaBlock.STATE_TO_LUMINANCE)
                    .randomTicks()
                    .forceSolidOff()
                    .noCollission()
                    .noOcclusion()
                    .sound(BeyondSoundTypes.POLAR_ANTENNA)
                    .offsetType(BlockBehaviour.OffsetType.XZ))
    );
    public static final DeferredBlock<Block> PLATE_BLOCK = registerBlock("plate_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .sound(BeyondSoundTypes.PLATE_BLOCK))
    );
    public static final DeferredBlock<Block> PLATED_END_STONE = registerBlock("plated_end_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .sound(BeyondSoundTypes.PLATED_END_STONE_BLOCK))
    );
    public static final DeferredBlock<MagnolillyBlock> MAGNOLILLY = registerBlock("magnolilly",
            () -> new MagnolillyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .sound(SoundType.ANCIENT_DEBRIS)
                    .forceSolidOff()
                    .noCollission()
                    .noOcclusion()
                    .offsetType(BlockBehaviour.OffsetType.XZ))

    );

    @SuppressWarnings("unchecked")
    private static <T extends Block> DeferredBlock<T> registerBlockWithoutItem(String name, Supplier<? extends Block> block) {
        DeferredBlock<Block> toReturn = BLOCKS.register(name, block);
        return (DeferredBlock<T>) toReturn;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<? extends Block> block) {
        DeferredBlock<Block> toReturn = BLOCKS.register(name, block);
        CREATIVE_TAB_ITEMS.add(registerBlockItem(name, toReturn));
        return (DeferredBlock<T>) toReturn;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Block> DeferredBlock<T> registerIntegrationBlockWithoutItem(String name, Supplier<? extends Block> block, String modId) {
        if (!ModList.get().isLoaded(modId)) return null;
        DeferredBlock<Block> toReturn = BLOCKS.register(name, block);
        return (DeferredBlock<T>) toReturn;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Block> DeferredBlock<T> registerIntegrationBlock(String name, Supplier<? extends Block> block, String modId) {
        if (!ModList.get().isLoaded(modId)) return null;
        DeferredBlock<Block> toReturn = BLOCKS.register(name, block);
        CREATIVE_TAB_ITEMS.add(registerIntegrationBlockItem(name, toReturn));
        return (DeferredBlock<T>) toReturn;
    }

    private static DeferredHolder<Item, BlockItem> registerBlockItem(String name, Supplier<? extends Block> block) {
        return BeyondItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static DeferredHolder<Item, BlockItem> registerIntegrationBlockItem(String name, Supplier<? extends Block> block) {
        return BeyondItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
