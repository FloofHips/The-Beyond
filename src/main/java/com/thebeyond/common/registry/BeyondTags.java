package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondTags {
    public static final TagKey<Block> VOID_FLAME_BASE_BLOCKS = create("void_flame_base_blocks");
    public static final TagKey<Block> END_DECORATOR_REPLACEABLE = create("end_decorator_replaceable");
    public static final TagKey<Block> END_FLOOR_BLOCKS = create("end_floor_blocks");

    private static TagKey<Block> create(String id) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(MODID, id));
    }
}
