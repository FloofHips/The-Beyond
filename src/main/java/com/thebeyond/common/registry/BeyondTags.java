package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thebeyond.TheBeyond.MODID;

public class BeyondTags {
    public static final TagKey<Block> VOID_FLAME_BASE_BLOCKS = createBlock("void_flame_base_blocks");
    public static final TagKey<Block> END_DECORATOR_REPLACEABLE = createBlock("end_decorator_replaceable");
    public static final TagKey<Block> END_FLOOR_BLOCKS = createBlock("end_floor_blocks");
    public static final TagKey<Block> PORTELAIN_BLOCKS = createBlock("portelain_blocks");
    public static final TagKey<Block> OBIROOT_BLOCKS = createBlock("obiroot_blocks");
    public static final TagKey<Block> METAL_BLOCKS = createBlock("metal_blocks");
    public static final TagKey<Block> BRITTLE_METAL_BLOCKS = createBlock("brittle_metal_blocks");
    public static final TagKey<Block> PEARL_BLOCKS = createBlock("pearl_blocks");
    public static final TagKey<Block> NACRE = createBlock("nacre");

    public static final TagKey<Item> OBIROOTS = createItem("obiroots");
    public static final TagKey<Item> REMEMBRANCES = createItem("remembrances");
    public static final TagKey<Item> AURORACITE_INTERACTABLE = createItem("auroracite_interactable");
    public static final TagKey<Item> PRISMOGRAPH_FILM = createItem("prismograph_film");
    public static final TagKey<Item> BRITTLE_TOOLS = createItem("brittle_tools");
    public static final TagKey<Item> MIRRORS = createItem("mirrors");
    public static final TagKey<Item> ROOTS = createItem("roots");

    public static final TagKey<Structure> BONFIRE_LOCATABLE = createStructure("bonfire_locatable");
    public static final TagKey<Structure> NOMAD_PRAYER_SITE = createStructure("nomad_prayer_site");

    public static final TagKey<Biome> IS_FOGGY = createBiome("is_foggy");
    public static final TagKey<Biome> IS_EXTRA_FOGGY = createBiome("is_extra_foggy");

    /** Reverse opt-out: members ignore the deafening FOV-stealth rule (always notice the player). */
    public static final TagKey<EntityType<?>> IMMUNE_TO_DEAFENING = createEntity("immune_to_deafening");
    /**
     * Reverse opt-out: members are non-visual hunters (vibration/hearing/movement), so they're excluded from the FOV rule.
     * Suppression follows each creature's real path — Warden via its mixins, Ghoul (vestigial vibration) via the {@code setTarget} handler.
     */
    public static final TagKey<EntityType<?>> SENSES_VIA_VIBRATION = createEntity("senses_via_vibration");

    public static final TagKey<EntityType<?>> ENTROPIC_FORM = createEntity("entropic_form");

    private static TagKey<Block> createBlock(String id) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(MODID, id));
    }

    private static TagKey<Item> createItem(String id) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(MODID, id));
    }

    private static TagKey<Structure> createStructure(String id) {
        return TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(MODID, id));
    }

    private static TagKey<EntityType<?>> createEntity(String id) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MODID, id));
    }

    private static TagKey<Biome> createBiome(String id) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(MODID, id));
    }
}
