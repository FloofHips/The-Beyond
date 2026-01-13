package com.thebeyond.data.tags;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BeyondItemTags extends ItemTagsProvider {

    public BeyondItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, TheBeyond.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BeyondTags.OBIROOTS)
                .add(BeyondBlocks.OBIROOT.asItem())
                .add(BeyondBlocks.PEEPING_OBIROOT.asItem());

        tag(ItemTags.PLANKS)
                .add(BeyondBlocks.XYLEM.asItem());

        tag(ItemTags.LOGS_THAT_BURN)
                .addTags(BeyondTags.OBIROOTS);

        tag(ItemTags.LOGS)
                .addTags(BeyondTags.OBIROOTS);

        tag(ItemTags.STAIRS)
                .add(BeyondBlocks.PORTELAIN_STAIRS.asItem());
        tag(ItemTags.SLABS)
                .add(BeyondBlocks.PORTELAIN_SLAB.asItem());

        tag(ItemTags.FOOT_ARMOR)
                .add(BeyondItems.PATHFINDER_BOOTS.get());

        tag(ItemTags.HEAD_ARMOR)
                .add(BeyondItems.ETHER_CLOAK.get());

        tag(ItemTags.FOOT_ARMOR_ENCHANTABLE)
                .add(BeyondItems.PATHFINDER_BOOTS.get());

        tag(ItemTags.HEAD_ARMOR_ENCHANTABLE)
                .add(BeyondItems.ETHER_CLOAK.get());

        tag(ItemTags.ARMOR_ENCHANTABLE)
                .add(BeyondItems.PATHFINDER_BOOTS.get())
                .add(BeyondItems.ETHER_CLOAK.get());

        tag(ItemTags.DURABILITY_ENCHANTABLE)
                .add(BeyondItems.PATHFINDER_BOOTS.get())
                .add(BeyondItems.ETHER_CLOAK.get());

        tag(ItemTags.EQUIPPABLE_ENCHANTABLE)
                .add(BeyondItems.PATHFINDER_BOOTS.get())
                .add(BeyondItems.ETHER_CLOAK.get());


        tag(BeyondTags.REMEMBRANCES)
                .add(BeyondItems.REMEMBRANCE_BEADS.get())
                .add(BeyondItems.REMEMBRANCE_IDOL.get())
                .add(BeyondItems.REMEMBRANCE_BRACE.get())
                .add(BeyondItems.REMEMBRANCE_RING.get())
                .add(BeyondItems.REMEMBRANCE_MEMORY.get())
                .add(BeyondItems.REMEMBRANCE_HORN.get())
                .add(BeyondItems.REMEMBRANCE_LACE.get())
                .add(BeyondItems.REMEMBRANCE_BROCHE.get())
                .add(BeyondItems.REMEMBRANCE_LIFE.get())
                .add(BeyondItems.REMEMBRANCE_HOME.get())
                .add(BeyondItems.REMEMBRANCE_EYE.get())
                .add(BeyondItems.REMEMBRANCE_SPIKE.get())
                .add(BeyondItems.REMEMBRANCE_ORNAMENT.get())
                .add(BeyondItems.REMEMBRANCE_MOUNT.get())
                .add(BeyondItems.REMEMBRANCE_HAND.get())
                .add(BeyondItems.REMEMBRANCE_CLOTH.get());

        tag(BeyondTags.AURORACITE_INTERACTABLE)
                .add(BeyondItems.ABYSSAL_NOMAD_SPAWN_EGG.get())
                .add(BeyondBlocks.AURORACITE.asItem())
                .add(BeyondItems.ECTOPLASM.get());

    }
}
