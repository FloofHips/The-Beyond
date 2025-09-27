package com.thebeyond.common.registry;

import com.google.common.collect.Sets;
import com.thebeyond.common.block.*;
import com.thebeyond.common.fluid.GellidVoidBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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
    public static final DeferredBlock<Block> VOID_CRYSTAL = registerBlockWithoutItem("void_crystal",
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
                    .noOcclusion()
                    .replaceable())
    );
    public static final DeferredBlock<Block> ENGRAVED_END_STONE = registerBlock("engraved_end_stone", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOL)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F)
            .sound(BeyondSoundTypes.END_STONE))
    );
    public static final DeferredBlock<Block> PORTELAIN = registerBlock("portelain", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOL)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F)
            .sound(SoundType.NETHER_BRICKS))
    );
    public static final DeferredBlock<Block> PORTELAIN_PILLAR = registerBlock("portelain_pillar", () -> new RotatedPillarBlock(
            BlockBehaviour.Properties.ofFullCopy(PORTELAIN.get()).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<Block> PORTELAIN_TILES = registerBlock("portelain_tiles", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(PORTELAIN.get()).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<Block> PORTELAIN_MOSAIC = registerBlock("portelain_mosaic", () -> new GlazedTerracottaBlock(
            BlockBehaviour.Properties.ofFullCopy(PORTELAIN.get()).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<Block> PORTELAIN_DOOR = registerBlock("portelain_door", () -> new DoorBlock(
            BeyondBlockSetTypes.PORTELAIN,
            BlockBehaviour.Properties.ofFullCopy(PORTELAIN.get()).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<Block> PORTELAIN_STAIRS = registerBlock("portelain_stairs", () -> new StairBlock(
            PORTELAIN.get().defaultBlockState(),
            BlockBehaviour.Properties.ofFullCopy(PORTELAIN.get()).sound(SoundType.NETHER_BRICKS)));
    public static final DeferredBlock<Block> PORTELAIN_SLAB = registerBlock("portelain_slab", () -> new SlabBlock(
            BlockBehaviour.Properties.ofFullCopy(PORTELAIN.get()).sound(SoundType.NETHER_BRICKS)));

    //Path
    public static final DeferredBlock<Block> AURORACITE = registerBlock("auroracite",
            () -> new AuroraciteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .sound(SoundType.AMETHYST))
    );
    public static final DeferredBlock<Block> STARDUST = registerBlock("stardust",
            () -> new StardustBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .sound(SoundType.SMALL_AMETHYST_BUD)
                    .noOcclusion()
                    .noCollission())
    );
    //Attracta Expanse
    public static final DeferredBlock<Block> POLAR_PILLAR = registerBlock("polar_pillar",
            () -> new PolarPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .lightLevel(PolarPillarBlock.STATE_TO_LUMINANCE)
                    .randomTicks())
    );
    public static final DeferredBlock<Block> POLAR_BULB = registerBlock("polar_bulb",
            () -> new PolarBulbBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .lightLevel(PolarBulbBlock.STATE_TO_LUMINANCE)
                    .randomTicks())
    );
    public static final DeferredBlock<Block> POLAR_ANTENNA = registerBlock("polar_antenna",
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
    public static final DeferredBlock<Block> MAGNOLILLY = registerBlockWithoutItem("magnolilly",
            () -> new MagnolillyBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .sound(SoundType.ANCIENT_DEBRIS)
                    .forceSolidOff()
                    .noCollission()
                    .noOcclusion()
                    .offsetType(BlockBehaviour.OffsetType.XZ))

    );

    //PeerLands
    public static final DeferredBlock<Block> OBIROOT = registerBlock("obiroot",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.NETHER_WOOD))
    );
    public static final DeferredBlock<Block> PEEPING_OBIROOT = registerBlock("peeping_obiroot",
            () -> new ParanoiaBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.NETHER_WOOD))
    );
    public static final DeferredBlock<Block> ENADRAKE_HUT = registerBlock("enadrake_hut",
            () -> new EnadrakeHutBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .sound(BeyondSoundTypes.END_STONE))
    );
    public static final DeferredBlock<Block> ZYMOTE = registerBlock("zymote",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .sound(SoundType.NYLIUM))
    );
    public static final DeferredBlock<Block> REACHING_ZYMOTE = registerBlock("reaching_zymote",
            () -> new FloorGrowthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.CHERRY_SAPLING)
                    .offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.DESTROY))
    );
    public static final DeferredBlock<Block> CREEPING_ZYMOTE = registerBlock("creeping_zymote",
            () -> new GlowLichenBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.CHERRY_SAPLING)
                    .pushReaction(PushReaction.DESTROY))
    );
    //public static final DeferredBlock<Block> ENATIOUS_TOTEM_SEED = registerBlock("enatious_totem_seed",
    //        () -> new EnatiousTotemSeed(Block.Properties.of()
    //                .mapColor(MapColor.COLOR_PURPLE)
    //                .sound(SoundType.METAL)
    //                .lightLevel(state -> 7)
    //                .requiresCorrectToolForDrops()));

    // Pearlescent Plains
    public static final DeferredBlock<Block> NACRE = registerBlock("nacre",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .sound(SoundType.GRAVEL))
    );
    public static final DeferredBlock<Block> PEARL = registerBlock("pearl",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .sound(SoundType.AMETHYST))
    );
    public static final DeferredBlock<Block> PEARL_BRICKS = registerBlock("pearl_bricks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .sound(SoundType.AMETHYST))
    );
    public static final DeferredBlock<Block> COBBLED_PEARL = registerBlock("cobbled_pearl",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .sound(SoundType.AMETHYST))
    );
    public static final DeferredBlock<Block> COBBLED_PEARL_BRICKS = registerBlock("cobbled_pearl_bricks",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .sound(SoundType.AMETHYST))
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
//    private static <T extends Block> DeferredBlock<Block> registerSpecial(String name, Supplier<? extends T> supp) {
//        DeferredBlock<Block> toReturn = BLOCKS.register(name, supp);
//        CREATIVE_TAB_ITEMS.add(registerBlockItem(name, toReturn));
//        return toReturn;
//    }
        private static DeferredHolder<Item, BlockItem> registerBlockItem(String name, Supplier<? extends Block> block) {
        return BeyondItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static DeferredHolder<Item, BlockItem> registerIntegrationBlockItem(String name, Supplier<? extends Block> block) {
        return BeyondItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
