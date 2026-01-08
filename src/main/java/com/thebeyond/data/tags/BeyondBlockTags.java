package com.thebeyond.data.tags;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BeyondBlockTags extends BlockTagsProvider {
    public BeyondBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, TheBeyond.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {

        tag(BeyondTags.OBIROOT_BLOCKS)
                .add(BeyondBlocks.OBIROOT.get())
                .add(BeyondBlocks.PEEPING_OBIROOT.get());

        tag(BeyondTags.PORTELAIN_BLOCKS)
                .add(BeyondBlocks.PORTELAIN.get())
                .add(BeyondBlocks.PORTELAIN_DOOR.get())
                .add(BeyondBlocks.PORTELAIN_TILES.get())
                .add(BeyondBlocks.PORTELAIN_MOSAIC.get())
                .add(BeyondBlocks.PORTELAIN_PILLAR.get())
                .add(BeyondBlocks.PORTELAIN_SLAB.get())
                .add(BeyondBlocks.PORTELAIN_STAIRS.get())
                .add(BeyondBlocks.GUSTER.get());

        tag(BlockTags.PLANKS).add(BeyondBlocks.XYLEM.get());

        tag(BlockTags.MINEABLE_WITH_AXE)
                .add(BeyondBlocks.XYLEM.get())
                .addTag(BeyondTags.OBIROOT_BLOCKS);

        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(BeyondBlocks.ENGRAVED_END_STONE.get())
                .add(BeyondBlocks.PLATE_BLOCK.get())
                .add(BeyondBlocks.PLATED_END_STONE.get())
                .add(BeyondBlocks.POLAR_PILLAR.get())
                .add(BeyondBlocks.MAGNOLILLY.get())
                .add(BeyondBlocks.POLAR_ANTENNA.get())
                .add(BeyondBlocks.VOID_CRYSTAL.get())
                .addTag(BeyondTags.PORTELAIN_BLOCKS);

        tag(BlockTags.MINEABLE_WITH_HOE)
                .add(BeyondBlocks.ZYMOTE.get());

        tag(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(BeyondBlocks.NACRE.get());

        tag(BlockTags.SWORD_EFFICIENT)
                .add(BeyondBlocks.POLAR_ANTENNA.get());

        tag(BlockTags.INCORRECT_FOR_WOODEN_TOOL)
                .add(BeyondBlocks.VOID_CRYSTAL.get());

        tag(BlockTags.DRAGON_IMMUNE)
                .addTag(BeyondTags.PORTELAIN_BLOCKS);

        tag(BlockTags.DRAGON_TRANSPARENT)
                .addTag(BeyondTags.PORTELAIN_BLOCKS);

    }
}
