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
        basicBlock(BeyondBlocks.NACRE);
        basicBlock(BeyondBlocks.PORTELAIN);
        basicBlock(BeyondBlocks.PORTELAIN_TILES);
        basicBlock(BeyondBlocks.ENGRAVED_END_STONE);
        //basicBlock(BeyondBlocks.COBBLED_PEARL);
        //basicBlock(BeyondBlocks.COBBLED_PEARL_BRICKS);
        //rotatedPillarBlock(BeyondBlocks.XYLEM);
        //rotatedPillarBlock(BeyondBlocks.FERROJELLY_BLOCK);

        blocks.remove(BeyondBlocks.FERROJELLY_BLOCK);

        doorBlockWithRenderType((DoorBlock) BeyondBlocks.PORTELAIN_DOOR.get(), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/portelain_door_bottom"), ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID,"block/portelain_door_top"), "minecraft:cutout");

        DataHelper.takeAll(blocks, b -> b.get() instanceof RotatedPillarBlock).forEach(this::rotatedPillarBlock);
        DataHelper.takeAll(blocks, b -> b.get() instanceof StairBlock).forEach(this::stairsBlock);
        DataHelper.takeAll(blocks, b -> b.get() instanceof WallBlock).forEach(this::wallBlock);
        DataHelper.takeAll(blocks, b -> b.get() instanceof FenceBlock).forEach(this::fenceBlock);
        Collection<DeferredBlock<Block>> slabs = DataHelper.takeAll(blocks, b -> b.get() instanceof SlabBlock);
        slabs.forEach(this::slabBlock);
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
    private ResourceLocation prefix(String s) {
        return ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, s);
    }
}
