package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondTags {
    public static final TagKey<Block> VOID_FLAME_BASE_BLOCKS = createBlock("void_flame_base_blocks");
    public static final TagKey<Block> END_DECORATOR_REPLACEABLE = createBlock("end_decorator_replaceable");
    public static final TagKey<Block> END_FLOOR_BLOCKS = createBlock("end_floor_blocks");
    public static final TagKey<Block> PORTELAIN_BLOCKS = createBlock("portelain_blocks");
    public static final TagKey<Block> OBIROOT_BLOCKS = createBlock("obiroot_blocks");
    public static final TagKey<Item> OBIROOTS = createItem("obiroots");
    public static final TagKey<Item> REMEMBRANCES = createItem("remembrances");
    public static final TagKey<Item> AURORACITE_INTERACTABLE = createItem("auroracite_interactable");

    private static TagKey<Block> createBlock(String id) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(MODID, id));
    }

    private static TagKey<Item> createItem(String id) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(MODID, id));
    }
}
