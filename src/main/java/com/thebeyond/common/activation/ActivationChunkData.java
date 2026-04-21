package com.thebeyond.common.activation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-chunk activation state attachment.
 *
 * <p><b>What this stores</b>: for each {@link ResourceLocation} "activation kind"
 * (e.g. {@code the_beyond:polar_chain}, {@code the_beyond:bismuth_frozen}),
 * a set of short-packed local block positions (x,z only — activation is
 * typically a surface/column concept) within the chunk that are currently in
 * the "activated" state.
 *
 * <p><b>Why a {@link Map} keyed by kind</b>: the design doc ("biomes are
 * inactive until player interaction") applies to many independent systems —
 * Polar Antennae chain, Bismuth freeze propagation, Perka Stalks trigger,
 * Legacy Grove dig progress, future Farlands mechanics. Rather than adding a
 * new attachment per system (and a new save-data migration each time), we
 * share one attachment with a string discriminator.
 *
 * <p><b>Not wired yet</b>: this is scaffolding placed ahead of the content
 * sprints described in {@code IMPLEMENTATION_PLAN.md §2.1}. Registering the
 * attachment type is free — it adds zero ticks per frame and a single empty
 * {@link CompoundTag} per chunk on save. The moment the first caller
 * (probably {@code PolarAntennaBlock}) starts writing to it, the plumbing
 * already exists and save/load works.
 *
 * <p><b>Storage format (forward-compatible)</b>:
 * <pre>
 * {
 *   "kinds": [
 *     { "id": "the_beyond:polar_chain", "positions": [LongArray] },
 *     { "id": "the_beyond:perka_triggered", "positions": [LongArray] },
 *     ...
 *   ]
 * }
 * </pre>
 * Using a list of named entries (rather than a flat CompoundTag) means a
 * future refactor can extend each entry with extra data (timestamp, decay
 * ticks) without rewriting loaders.
 *
 * @see BeyondActivation for the static API all callers should use.
 */
public class ActivationChunkData implements INBTSerializable<CompoundTag> {

    /** Kind → set of packed local positions within the chunk. */
    private final Map<ResourceLocation, Set<Long>> byKind = new HashMap<>();

    /**
     * Pack a block position (absolute or local) into a single long. We pack
     * full XYZ (with reduced Y bits) so the same encoding works whether the
     * caller passes a world-space or chunk-local position — the caller is
     * responsible for being consistent. 20 bits X, 12 bits Y, 20 bits Z;
     * enough for any reasonable chunk-scoped use.
     */
    public static long packXYZ(int x, int y, int z) {
        long lx = ((long) x) & 0xFFFFFL;          // 20 bits
        long ly = ((long) y) & 0xFFFL;            // 12 bits
        long lz = ((long) z) & 0xFFFFFL;          // 20 bits
        return (lx << 32) | (ly << 20) | lz;
    }

    public static int unpackX(long packed) {
        int v = (int) ((packed >>> 32) & 0xFFFFFL);
        // sign-extend 20 bits to int
        return (v << 12) >> 12;
    }

    public static int unpackY(long packed) {
        int v = (int) ((packed >>> 20) & 0xFFFL);
        return (v << 20) >> 20;
    }

    public static int unpackZ(long packed) {
        int v = (int) (packed & 0xFFFFFL);
        return (v << 12) >> 12;
    }

    /** @return {@code true} if the given packed position is marked for the given kind. */
    public boolean isActivated(ResourceLocation kind, long packedPos) {
        Set<Long> set = byKind.get(kind);
        return set != null && set.contains(packedPos);
    }

    /** Marks a position as activated for the given kind. @return {@code true} if this changed state. */
    public boolean setActivated(ResourceLocation kind, long packedPos) {
        return byKind.computeIfAbsent(kind, k -> new HashSet<>()).add(packedPos);
    }

    /** Unmarks a position. @return {@code true} if this changed state. */
    public boolean clearActivated(ResourceLocation kind, long packedPos) {
        Set<Long> set = byKind.get(kind);
        if (set == null) return false;
        boolean removed = set.remove(packedPos);
        if (set.isEmpty()) byKind.remove(kind);
        return removed;
    }

    /** @return an unmodifiable view of all packed positions for a kind, or an empty set if the kind is absent. */
    public Set<Long> getActivated(ResourceLocation kind) {
        Set<Long> set = byKind.get(kind);
        return set == null ? Set.of() : java.util.Collections.unmodifiableSet(set);
    }

    /** @return true if no kinds have any activation state. Used to avoid serializing empty chunks. */
    public boolean isEmpty() {
        return byKind.isEmpty();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        ListTag kindsList = new ListTag();
        for (Map.Entry<ResourceLocation, Set<Long>> entry : byKind.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            CompoundTag kindTag = new CompoundTag();
            kindTag.putString("id", entry.getKey().toString());
            long[] positions = new long[entry.getValue().size()];
            int i = 0;
            for (long p : entry.getValue()) positions[i++] = p;
            kindTag.putLongArray("positions", positions);
            kindsList.add(kindTag);
        }
        if (!kindsList.isEmpty()) {
            root.put("kinds", kindsList);
        }
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        byKind.clear();
        if (!tag.contains("kinds", Tag.TAG_LIST)) return;
        ListTag kindsList = tag.getList("kinds", Tag.TAG_COMPOUND);
        for (int i = 0; i < kindsList.size(); i++) {
            CompoundTag kindTag = kindsList.getCompound(i);
            String idStr = kindTag.getString("id");
            ResourceLocation id = ResourceLocation.tryParse(idStr);
            if (id == null) continue;   // malformed entry — skip rather than crash the load
            long[] positions = kindTag.getLongArray("positions");
            if (positions.length == 0) continue;
            Set<Long> set = new HashSet<>(positions.length);
            for (long p : positions) set.add(p);
            byKind.put(id, set);
        }
    }
}
