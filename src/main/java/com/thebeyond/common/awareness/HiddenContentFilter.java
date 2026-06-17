package com.thebeyond.common.awareness;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Hides not-yet-unlocked items, structures and biomes from creative search, JEI and the compass mods. */
public final class HiddenContentFilter {

    private HiddenContentFilter() {}

    private static ResourceLocation tagId(String path) {
        return ResourceLocation.fromNamespaceAndPath("the_beyond", "hidden_until/" + path);
    }

    /** Internal/convention tags that are never real /locate targets, so keep them out of tab-complete. */
    public static boolean isUtilityLocateTag(ResourceLocation tag) {
        return tag.getPath().startsWith("hidden_from_")
                || (tag.getNamespace().equals("the_beyond") && tag.getPath().startsWith("hidden_until/"));
    }

    // ---------------------------------------------------------------------
    //  Item tags — consumed by creative tab/search + JEI.
    // ---------------------------------------------------------------------

    public static final TagKey<Item> HIDDEN_UNTIL_FARLANDS =
            TagKey.create(Registries.ITEM, tagId("farlands"));
    public static final TagKey<Item> HIDDEN_UNTIL_WALL =
            TagKey.create(Registries.ITEM, tagId("wall"));
    public static final TagKey<Item> HIDDEN_UNTIL_BEYOND =
            TagKey.create(Registries.ITEM, tagId("beyond"));

    private static final Map<TagKey<Item>, ResourceLocation> ITEM_TAG_TO_KEY = Map.of(
            HIDDEN_UNTIL_FARLANDS, BeyondAwarenessKeys.FARLANDS_DISCOVERY,
            HIDDEN_UNTIL_WALL,     BeyondAwarenessKeys.WALL_PROXIMITY,
            HIDDEN_UNTIL_BEYOND,   BeyondAwarenessKeys.BEYOND_ACCESS
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
            STRUCTURE_HIDDEN_UNTIL_FARLANDS, BeyondAwarenessKeys.FARLANDS_DISCOVERY,
            STRUCTURE_HIDDEN_UNTIL_WALL,     BeyondAwarenessKeys.WALL_PROXIMITY,
            STRUCTURE_HIDDEN_UNTIL_BEYOND,   BeyondAwarenessKeys.BEYOND_ACCESS
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
            BIOME_HIDDEN_UNTIL_FARLANDS, BeyondAwarenessKeys.FARLANDS_DISCOVERY,
            BIOME_HIDDEN_UNTIL_WALL,     BeyondAwarenessKeys.WALL_PROXIMITY,
            BIOME_HIDDEN_UNTIL_BEYOND,   BeyondAwarenessKeys.BEYOND_ACCESS
    );

    // =====================================================================
    //  Item queries.
    // =====================================================================

    /** Should this item be hidden from the viewer right now? */
    public static boolean isHidden(ItemStack stack, Player viewer) {
        if (stack.isEmpty()) return false;
        if (!BeyondAwareness.gateEnabled()) return false;
        return isHidden(stack, BeyondAwareness.knownSnapshot(viewer));
    }

    public static boolean isHidden(ItemStack stack, Set<ResourceLocation> known) {
        if (stack.isEmpty()) return false;
        boolean gatedByUnknownKey = false;
        for (Map.Entry<TagKey<Item>, ResourceLocation> entry : ITEM_TAG_TO_KEY.entrySet()) {
            if (stack.is(entry.getKey()) && !known.contains(entry.getValue())) {
                gatedByUnknownKey = true;
                break;
            }
        }
        if (!gatedByUnknownKey) return false;
        // Still gated by region, but actually holding the item unlocks it on its own.
        return !known.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** Drops every hidden item from the collection in place. */
    public static void hide(Collection<ItemStack> items, Player viewer) {
        if (!BeyondAwareness.gateEnabled()) return;
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(viewer);
        items.removeIf(stack -> isHidden(stack, known));
    }

    /** Is this item one that can ever be hidden? */
    public static boolean isGated(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (TagKey<Item> tag : ITEM_TAG_TO_KEY.keySet()) {
            if (stack.is(tag)) return true;
        }
        return false;
    }

    /** Short, collision-proof marker for a biome - keeps its namespace, just prefixes the path. */
    public static ResourceLocation biomeMarker(ResourceLocation biome) {
        return ResourceLocation.fromNamespaceAndPath(biome.getNamespace(), "biome/" + biome.getPath());
    }

    public static ResourceLocation structureMarker(ResourceLocation structure) {
        return ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), "structure/" + structure.getPath());
    }

    /** Unlocks whatever the player is carrying, standing in, or standing on right now. */
    public static void discoverNearby(ServerPlayer player) {
        if (!BeyondAwareness.gateEnabled()) return;
        Set<ResourceLocation> found = new HashSet<>();
        Inventory inv = player.getInventory();
        collectGated(inv.items, found);
        collectGated(inv.armor, found);
        collectGated(inv.offhand, found);
        collectLocation(player, found);
        if (!found.isEmpty()) BeyondAwareness.grantAll(player, found);
    }

    private static void collectGated(List<ItemStack> stacks, Set<ResourceLocation> out) {
        for (ItemStack stack : stacks) {
            if (isGated(stack)) out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }

    /** Marks the biome the player is in and any structure they're standing inside. */
    private static void collectLocation(ServerPlayer player, Set<ResourceLocation> out) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        Holder<Biome> biome = level.getBiome(pos);
        if (anyTag(biome, BIOME_TAG_TO_KEY.keySet())) {
            biome.unwrapKey().ifPresent(k -> out.add(biomeMarker(k.location())));
        }

        StructureManager structures = level.structureManager();
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure structure : structures.getAllStructuresAt(pos).keySet()) {
            if (anyTag(reg.wrapAsHolder(structure), STRUCTURE_TAG_TO_KEY.keySet())) {
                ResourceLocation id = reg.getKey(structure);
                if (id != null) out.add(structureMarker(id));
            }
        }
    }

    private static <T> boolean anyTag(Holder<T> holder, Set<TagKey<T>> tags) {
        for (TagKey<T> tag : tags) if (holder.is(tag)) return true;
        return false;
    }

    /** Hidden ids in a registry, walked from the small gated tags (not the whole registry) so lookups stay O(1) regardless of pack size. */
    private static <T> Set<ResourceLocation> hiddenIds(Registry<T> reg, Map<TagKey<T>, ResourceLocation> tagToKey,
                                                       Set<ResourceLocation> known,
                                                       Function<ResourceLocation, ResourceLocation> marker) {
        Set<ResourceLocation> hidden = new HashSet<>();
        for (Map.Entry<TagKey<T>, ResourceLocation> entry : tagToKey.entrySet()) {
            if (known.contains(entry.getValue())) continue;   // region unlocked → this tag hides nothing
            reg.getTag(entry.getKey()).ifPresent(tag -> {
                for (Holder<T> holder : tag) {
                    holder.unwrapKey().ifPresent(k -> {
                        if (!known.contains(marker.apply(k.location()))) hidden.add(k.location());
                    });
                }
            });
        }
        return hidden;
    }

    /** Every item that can be gated - used for command tab-complete. */
    public static Set<ResourceLocation> gateableItemIds() {
        Set<ResourceLocation> out = new HashSet<>();
        for (TagKey<Item> tag : ITEM_TAG_TO_KEY.keySet()) {
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> {
                for (Holder<Item> holder : set) {
                    holder.unwrapKey().ifPresent(key -> out.add(key.location()));
                }
            });
        }
        return out;
    }

    // =====================================================================
    //  Structure queries (Explorer's Compass).
    // =====================================================================

    /** Batched hidden-test for filtering structure id lists/streams: builds the hidden set once, then O(1) per check. */
    public static Predicate<ResourceLocation> structureHiddenTest(ServerPlayer viewer) {
        if (!BeyondAwareness.gateEnabled() || viewer == null) return id -> false;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> hidden = hiddenIds(reg, STRUCTURE_TAG_TO_KEY,
                BeyondAwareness.knownSnapshot(viewer), HiddenContentFilter::structureMarker);
        return hidden::contains;
    }

    /** Client-side copy of the gated-structure map; empty on the server. */
    private static volatile Map<ResourceLocation, Set<ResourceLocation>> clientGatedStructures = Map.of();

    /** Maps each gated structure to the regions that unlock it, so the client can gate /locate without the structure registry (which it never receives). */
    public static Map<ResourceLocation, Set<ResourceLocation>> gatedStructureMap(ServerPlayer viewer) {
        Registry<Structure> reg = viewer.level().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Map<ResourceLocation, Set<ResourceLocation>> out = new HashMap<>();
        reg.holders().forEach(holder -> {
            for (Map.Entry<TagKey<Structure>, ResourceLocation> e : STRUCTURE_TAG_TO_KEY.entrySet()) {
                if (holder.is(e.getKey())) {
                    out.computeIfAbsent(holder.key().location(), k -> new HashSet<>()).add(e.getValue());
                }
            }
        });
        return out;
    }

    public static void setClientGatedStructures(Map<ResourceLocation, Set<ResourceLocation>> map) {
        clientGatedStructures = map;
    }

    /** Client-side version - no structure registry here, so it leans on the pushed gated-structure map instead. */
    public static boolean isStructureHiddenClient(ResourceLocation id, Player viewer) {
        if (!BeyondAwareness.gateEnabled()) return false;
        Set<ResourceLocation> keys = clientGatedStructures.get(id);
        if (keys == null) return false;
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(viewer);
        if (known.contains(structureMarker(id))) return false;
        for (ResourceLocation key : keys) {
            if (!known.contains(key)) return true;
        }
        return false;
    }

    public static List<ResourceLocation> filterStructureKeys(List<ResourceLocation> keys, ServerPlayer viewer) {
        if (!BeyondAwareness.gateEnabled()) return keys;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(viewer);
        List<ResourceLocation> out = new ArrayList<>(keys.size());
        for (ResourceLocation key : keys) {
            if (!isStructureHidden(reg, key, known)) out.add(key);
        }
        return out;
    }

    /** Same filtering for the reverse map, so group searches can't leak hidden structures. */
    public static Map<ResourceLocation, ResourceLocation> filterStructureKeysToTypeKeys(
            Map<ResourceLocation, ResourceLocation> map, ServerPlayer viewer) {
        if (!BeyondAwareness.gateEnabled()) return map;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(viewer);
        Map<ResourceLocation, ResourceLocation> out = new HashMap<>(map.size());
        for (Map.Entry<ResourceLocation, ResourceLocation> e : map.entrySet()) {
            if (!isStructureHidden(reg, e.getKey(), known)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** And the forward multimap too, same reason. */
    public static ListMultimap<ResourceLocation, ResourceLocation> filterTypeKeysToStructureKeys(
            ListMultimap<ResourceLocation, ResourceLocation> map, ServerPlayer viewer) {
        if (!BeyondAwareness.gateEnabled()) return map;
        Registry<Structure> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(viewer);
        ListMultimap<ResourceLocation, ResourceLocation> out = ArrayListMultimap.create();
        for (Map.Entry<ResourceLocation, ResourceLocation> e : map.entries()) {
            if (!isStructureHidden(reg, e.getValue(), known)) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    private static boolean isStructureHidden(Registry<Structure> reg, ResourceLocation key, Set<ResourceLocation> known) {
        if (known.contains(structureMarker(key))) return false;   // personally entered → visible
        Holder<Structure> holder = reg.getHolder(ResourceKey.create(Registries.STRUCTURE, key)).orElse(null);
        if (holder == null) return false;
        for (Map.Entry<TagKey<Structure>, ResourceLocation> entry : STRUCTURE_TAG_TO_KEY.entrySet()) {
            if (holder.is(entry.getKey()) && !known.contains(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  Biome queries (Nature's Compass).
    // =====================================================================

    /** Should this biome stay hidden from the viewer? */
    public static boolean isBiomeHidden(ResourceLocation key, Player viewer) {
        if (!BeyondAwareness.gateEnabled()) return false;
        Registry<Biome> reg = viewer.level().registryAccess().registryOrThrow(Registries.BIOME);
        return isBiomeHidden(reg, key, BeyondAwareness.knownSnapshot(viewer));
    }

    /** Batched hidden-test for filtering biome id lists/streams: builds the hidden set once, then O(1) per check. */
    public static Predicate<ResourceLocation> biomeHiddenTest(Player viewer) {
        if (!BeyondAwareness.gateEnabled() || viewer == null) return id -> false;
        Registry<Biome> reg = viewer.level().registryAccess().registryOrThrow(Registries.BIOME);
        Set<ResourceLocation> hidden = hiddenIds(reg, BIOME_TAG_TO_KEY,
                BeyondAwareness.knownSnapshot(viewer), HiddenContentFilter::biomeMarker);
        return hidden::contains;
    }

    /** Filtering just this list is enough - the compass builds everything else from it. */
    public static List<ResourceLocation> filterBiomeKeys(List<ResourceLocation> keys, ServerPlayer viewer) {
        if (!BeyondAwareness.gateEnabled()) return keys;
        Registry<Biome> reg = viewer.serverLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Set<ResourceLocation> known = BeyondAwareness.knownSnapshot(viewer);
        List<ResourceLocation> out = new ArrayList<>(keys.size());
        for (ResourceLocation key : keys) {
            if (!isBiomeHidden(reg, key, known)) out.add(key);
        }
        return out;
    }

    private static boolean isBiomeHidden(Registry<Biome> reg, ResourceLocation key, Set<ResourceLocation> known) {
        if (known.contains(biomeMarker(key))) return false;   // personally visited → visible
        Holder<Biome> holder = reg.getHolder(ResourceKey.create(Registries.BIOME, key)).orElse(null);
        if (holder == null) return false;
        for (Map.Entry<TagKey<Biome>, ResourceLocation> entry : BIOME_TAG_TO_KEY.entrySet()) {
            if (holder.is(entry.getKey()) && !known.contains(entry.getValue())) {
                return true;
            }
        }
        return false;
    }
}
