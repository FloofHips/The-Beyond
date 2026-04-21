package com.thebeyond.common.knowledge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges {@code hidden_until/*} tags (items, structures, biomes) to {@link BeyondKnowledge}.
 * Consumed by creative tab/search, Explorer's/Nature's Compass, and JEI.
 *
 * <p>To add a new gate: declare a key in {@link BeyondKnowledgeKeys}, add matching
 * {@link TagKey} fields and {@code *_TAG_TO_KEY} entries, drop tag JSONs under
 * {@code data/the_beyond/tags/{item,worldgen/structure,worldgen/biome}/hidden_until/}, and
 * wire the detection event that grants the key. Empty tags are no-ops.
 */
public final class HiddenContentFilter {

    private HiddenContentFilter() {}

    private static ResourceLocation tagId(String path) {
        return ResourceLocation.fromNamespaceAndPath("the_beyond", "hidden_until/" + path);
    }

    // ---------------------------------------------------------------------
    //  Item tags — consumed by creative tab/search + JEI.
    // ---------------------------------------------------------------------

    /** Items hidden until the player discovers the Farlands. */
    public static final TagKey<Item> HIDDEN_UNTIL_FARLANDS =
            TagKey.create(Registries.ITEM, tagId("farlands"));

    /** Items hidden until the player gets within visual range of the north wall. */
    public static final TagKey<Item> HIDDEN_UNTIL_WALL =
            TagKey.create(Registries.ITEM, tagId("wall"));

    /** Items hidden until the player receives Life Itself's Beyond-access gift. */
    public static final TagKey<Item> HIDDEN_UNTIL_BEYOND =
            TagKey.create(Registries.ITEM, tagId("beyond"));

    private static final Map<TagKey<Item>, ResourceLocation> ITEM_TAG_TO_KEY = Map.of(
            HIDDEN_UNTIL_FARLANDS, BeyondKnowledgeKeys.FARLANDS_DISCOVERY,
            HIDDEN_UNTIL_WALL,     BeyondKnowledgeKeys.WALL_PROXIMITY,
            HIDDEN_UNTIL_BEYOND,   BeyondKnowledgeKeys.BEYOND_ACCESS
    );

    // ---------------------------------------------------------------------
    //  Structure tags — consumed by Explorer's Compass.
    // ---------------------------------------------------------------------

    public static final TagKey<Structure> STRUCTURE_HIDDEN_UNTIL_FARLANDS =
            TagKey.create(Registries.STRUCTURE, tagId("farlands"));
    public static final TagKey<Structure> STRUCTURE_HIDDEN_UNTIL_WALL =
            TagKey.create(Registries.STRUCTURE, tagId("wall"));
    public static final TagKey<Structure> STRUCTURE_HIDDEN_UNTIL_BEYOND =
            TagKey.create(Registries.STRUCTURE, tagId("beyond"));

    private static final Map<TagKey<Structure>, ResourceLocation> STRUCTURE_TAG_TO_KEY = Map.of(
            STRUCTURE_HIDDEN_UNTIL_FARLANDS, BeyondKnowledgeKeys.FARLANDS_DISCOVERY,
            STRUCTURE_HIDDEN_UNTIL_WALL,     BeyondKnowledgeKeys.WALL_PROXIMITY,
            STRUCTURE_HIDDEN_UNTIL_BEYOND,   BeyondKnowledgeKeys.BEYOND_ACCESS
    );

    // ---------------------------------------------------------------------
    //  Biome tags — consumed by Nature's Compass.
    // ---------------------------------------------------------------------

    public static final TagKey<Biome> BIOME_HIDDEN_UNTIL_FARLANDS =
            TagKey.create(Registries.BIOME, tagId("farlands"));
    public static final TagKey<Biome> BIOME_HIDDEN_UNTIL_WALL =
            TagKey.create(Registries.BIOME, tagId("wall"));
    public static final TagKey<Biome> BIOME_HIDDEN_UNTIL_BEYOND =
            TagKey.create(Registries.BIOME, tagId("beyond"));

    private static final Map<TagKey<Biome>, ResourceLocation> BIOME_TAG_TO_KEY = Map.of(
            BIOME_HIDDEN_UNTIL_FARLANDS, BeyondKnowledgeKeys.FARLANDS_DISCOVERY,
            BIOME_HIDDEN_UNTIL_WALL,     BeyondKnowledgeKeys.WALL_PROXIMITY,
            BIOME_HIDDEN_UNTIL_BEYOND,   BeyondKnowledgeKeys.BEYOND_ACCESS
    );

    // =====================================================================
    //  Item queries.
    // =====================================================================

    /** {@code true} iff the stack has a hidden_until tag whose key the viewer hasn't unlocked. */
    public static boolean isHidden(ItemStack stack, Player viewer) {
        if (stack.isEmpty()) return false;
        if (!BeyondKnowledge.gateEnabled()) return false;
        for (Map.Entry<TagKey<Item>, ResourceLocation> entry : ITEM_TAG_TO_KEY.entrySet()) {
            if (stack.is(entry.getKey()) && !BeyondKnowledge.isKnown(viewer, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** In-place removal. Short-circuits when the gate is off. */
    public static void hide(Collection<ItemStack> items, Player viewer) {
        if (!BeyondKnowledge.gateEnabled()) return;
        items.removeIf(stack -> isHidden(stack, viewer));
    }

    // =====================================================================
    //  Structure queries (Explorer's Compass).
    // =====================================================================

    /** Fail-open: unknown registry entries aren't hidden. */
    public static boolean isStructureHidden(ResourceLocation key, ServerPlayer viewer) {
        if (!BeyondKnowledge.gateEnabled()) return false;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = reg.getHolder(ResourceKey.create(Registries.STRUCTURE, key)).orElse(null);
        if (holder == null) return false;
        for (Map.Entry<TagKey<Structure>, ResourceLocation> entry : STRUCTURE_TAG_TO_KEY.entrySet()) {
            if (holder.is(entry.getKey()) && !BeyondKnowledge.isKnown(viewer, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Returns the input list unchanged when the gate is off. */
    public static List<ResourceLocation> filterStructureKeys(List<ResourceLocation> keys, ServerPlayer viewer) {
        if (!BeyondKnowledge.gateEnabled()) return keys;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ResourceLocation> out = new ArrayList<>(keys.size());
        for (ResourceLocation key : keys) {
            if (!isStructureHiddenCached(reg, key, viewer)) out.add(key);
        }
        return out;
    }

    /** Strips hidden structures from the reverse map (prevents client group-search leak). */
    public static Map<ResourceLocation, ResourceLocation> filterStructureKeysToTypeKeys(
            Map<ResourceLocation, ResourceLocation> map, ServerPlayer viewer) {
        if (!BeyondKnowledge.gateEnabled()) return map;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<ResourceLocation, ResourceLocation> out = new HashMap<>(map.size());
        for (Map.Entry<ResourceLocation, ResourceLocation> e : map.entrySet()) {
            if (!isStructureHiddenCached(reg, e.getKey(), viewer)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** Strips hidden structures from the forward multimap (same leak-prevention as above). */
    public static ListMultimap<ResourceLocation, ResourceLocation> filterTypeKeysToStructureKeys(
            ListMultimap<ResourceLocation, ResourceLocation> map, ServerPlayer viewer) {
        if (!BeyondKnowledge.gateEnabled()) return map;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        ListMultimap<ResourceLocation, ResourceLocation> out = ArrayListMultimap.create();
        for (Map.Entry<ResourceLocation, ResourceLocation> e : map.entries()) {
            if (!isStructureHiddenCached(reg, e.getValue(), viewer)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static boolean isStructureHiddenCached(Registry<Structure> reg, ResourceLocation key, ServerPlayer viewer) {
        Holder<Structure> holder = reg.getHolder(ResourceKey.create(Registries.STRUCTURE, key)).orElse(null);
        if (holder == null) return false;
        for (Map.Entry<TagKey<Structure>, ResourceLocation> entry : STRUCTURE_TAG_TO_KEY.entrySet()) {
            if (holder.is(entry.getKey()) && !BeyondKnowledge.isKnown(viewer, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  Biome queries (Nature's Compass).
    // =====================================================================

    public static boolean isBiomeHidden(ResourceLocation key, ServerPlayer viewer) {
        if (!BeyondKnowledge.gateEnabled()) return false;
        Registry<Biome> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> holder = reg.getHolder(ResourceKey.create(Registries.BIOME, key)).orElse(null);
        if (holder == null) return false;
        for (Map.Entry<TagKey<Biome>, ResourceLocation> entry : BIOME_TAG_TO_KEY.entrySet()) {
            if (holder.is(entry.getKey()) && !BeyondKnowledge.isKnown(viewer, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Nature's Compass derives xpLevels + dimensionKeys from this list downstream, so one pass covers the full SyncPacket. */
    public static List<ResourceLocation> filterBiomeKeys(List<ResourceLocation> keys, ServerPlayer viewer) {
        if (!BeyondKnowledge.gateEnabled()) return keys;
        Registry<Biome> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.BIOME);
        List<ResourceLocation> out = new ArrayList<>(keys.size());
        for (ResourceLocation key : keys) {
            if (!isBiomeHiddenCached(reg, key, viewer)) out.add(key);
        }
        return out;
    }

    private static boolean isBiomeHiddenCached(Registry<Biome> reg, ResourceLocation key, ServerPlayer viewer) {
        Holder<Biome> holder = reg.getHolder(ResourceKey.create(Registries.BIOME, key)).orElse(null);
        if (holder == null) return false;
        for (Map.Entry<TagKey<Biome>, ResourceLocation> entry : BIOME_TAG_TO_KEY.entrySet()) {
            if (holder.is(entry.getKey()) && !BeyondKnowledge.isKnown(viewer, entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
