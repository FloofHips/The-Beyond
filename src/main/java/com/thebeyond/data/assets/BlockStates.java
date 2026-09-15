package com.thebeyond.data.assets;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BlockStates extends BlockStateProvider {
    public BlockStates(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, TheBeyond.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        Set<DeferredBlock<Block>> blocks = new HashSet(BeyondBlocks.BLOCKS.getEntries());

        basicBlock(BeyondBlocks.OBIROOT);
        basicBlock(BeyondBlocks.GAUSSANITE);
        basicBlock(BeyondBlocks.PORTELAIN);
        basicBlock(BeyondBlocks.PORTELAIN_TILES);
        basicBlock(BeyondBlocks.ENGRAVED_END_STONE);
        basicBlock(BeyondBlocks.MEMOR);
        basicBlock(BeyondBlocks.NACRE);
        basicBlock(BeyondBlocks.CHISELED_MEMOR);
        basicBlock(BeyondBlocks.VILET);
        customBlock(BeyondBlocks.BONFIRE);
        basicBlock(BeyondBlocks.COBBLED_PEARL);
        basicBlock(BeyondBlocks.COBBLED_PEARL_BRICKS);
        basicBlock(BeyondBlocks.PEARL);
        basicBlock(BeyondBlocks.PEARL_BRICKS);
        basicBlock(BeyondBlocks.SOOT_BLOCK);
        basicBlock(BeyondBlocks.RICH_NACRE);
        basicBlock(BeyondBlocks.PALE_NACRE);
        basicBlock(BeyondBlocks.BEDAZZLED_END_STONE);

        //rotatedPillarBlock(BeyondBlocks.XYLEM);
        //rotatedPillarBlock(BeyondBlocks.FERROJELLY_BLOCK);

        blocks.remove(BeyondBlocks.FERROJELLY_BLOCK);
        blocks.remove(BeyondBlocks.COIL_VERTEBRAE);

        blocks.remove(BeyondBlocks.BRITTLE_METAL_SLAB);
        slabBlock((SlabBlock) BeyondBlocks.BRITTLE_METAL_SLAB.get(),
                ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/brittle_metal_block"),
                ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/brittle_metal"),
                ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/brittle_metal"),
                ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/brittle_metal"));

        doorBlock((DoorBlock) BeyondBlocks.PORTELAIN_DOOR.get(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/portelain_door_bottom"), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/portelain_door_top"));
        doorBlock((DoorBlock) BeyondBlocks.BRITTLE_METAL_DOOR.get(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/brittle_metal_door_bottom"), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/brittle_metal_door_top"));

        blocks.remove(BeyondBlocks.COBBLED_PEARL_BRICK_STAIRS);
        blocks.remove(BeyondBlocks.COBBLED_PEARL_BRICK_SLAB);
        blocks.remove(BeyondBlocks.COBBLED_PEARL_BRICK_WALL);

        blocks.remove(BeyondBlocks.PEARL_BRICK_STAIRS);
        blocks.remove(BeyondBlocks.PEARL_BRICK_SLAB);
        blocks.remove(BeyondBlocks.PEARL_BRICK_WALL);

        brickStairsBlock(BeyondBlocks.COBBLED_PEARL_BRICK_STAIRS);
        brickWallBlock(BeyondBlocks.COBBLED_PEARL_BRICK_WALL);

        brickStairsBlock(BeyondBlocks.PEARL_BRICK_STAIRS);
        brickWallBlock(BeyondBlocks.PEARL_BRICK_WALL);

        DataHelper.takeAll(blocks, b -> b.get() instanceof RotatedPillarBlock).forEach(this::rotatedPillarBlock);
        DataHelper.takeAll(blocks, b -> b.get() instanceof StairBlock).forEach(this::stairsBlock);
        DataHelper.takeAll(blocks, b -> b.get() instanceof WallBlock).forEach(this::wallBlock);
        DataHelper.takeAll(blocks, b -> b.get() instanceof FenceBlock).forEach(this::fenceBlock);
        Collection<DeferredBlock<Block>> slabs = DataHelper.takeAll(blocks, b -> b.get() instanceof SlabBlock);

        slabs.remove(BeyondBlocks.COBBLED_PEARL_BRICK_SLAB);
        slabs.remove(BeyondBlocks.PEARL_BRICK_SLAB);
        slabs.forEach(this::slabBlock);

        brickSlabBlock(BeyondBlocks.COBBLED_PEARL_BRICK_SLAB);
        brickSlabBlock(BeyondBlocks.PEARL_BRICK_SLAB);
    }

    public void basicBlock(DeferredBlock<Block> block) {
        simpleBlock(block.get());
    }

    public void customBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        ModelFile model = models().getExistingFile(prefix("block/" + name));
        getVariantBuilder(blockRegistryObject.get()).forAllStates(s -> ConfiguredModel.builder().modelFile(model).build());
    }
    public void rotatedBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        ModelFile file = models().cubeAll(name, prefix("block/" + name));

        getVariantBuilder(blockRegistryObject.get()).partialState().modelForState()
                .modelFile(file)
                .nextModel().modelFile(file).rotationY(90)
                .nextModel().modelFile(file).rotationY(180)
                .nextModel().modelFile(file).rotationY(270)
                .addModel();
    }
    public void fenceBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 6);
        fenceBlock((FenceBlock) blockRegistryObject.get(), prefix("block/" + baseName));
    }
    public void rotatedPillarBlock(DeferredBlock<Block> blockRegistryObject) {
        logBlock((RotatedPillarBlock) blockRegistryObject.get());
    }

    public void wallBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 5);
        wallBlock((WallBlock) blockRegistryObject.get(), prefix("block/" + baseName));
    }

    public void stairsBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 7);
        stairsBlock((StairBlock) blockRegistryObject.get(), prefix("block/" + baseName));
    }
    public void slabBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 5);
        slabBlock((SlabBlock) blockRegistryObject.get(), prefix(baseName), prefix("block/" + baseName));
    }
    public void brickWallBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 5)+"s";
        wallBlock((WallBlock) blockRegistryObject.get(), prefix("block/" + baseName));
    }
    public void brickStairsBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 7)+"s";
        stairsBlock((StairBlock) blockRegistryObject.get(), prefix("block/" + baseName));
    }
    public void brickSlabBlock(DeferredBlock<Block> blockRegistryObject) {
        String name = blockRegistryObject.getId().getPath();
        String baseName = name.substring(0, name.length() - 5)+"s";
        slabBlock((SlabBlock) blockRegistryObject.get(), prefix(baseName), prefix("block/" + baseName));
    }
    private ResourceLocation prefix(String s) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, s);
    }
}
