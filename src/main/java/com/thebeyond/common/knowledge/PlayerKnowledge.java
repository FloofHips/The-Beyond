package com.thebeyond.common.knowledge;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-player attachment: set of discovered knowledge keys. Survives death ({@code copyOnDeath})
 * and is populated even in {@code SHARED_WORLD} mode so an admin can switch modes without
 * losing per-player history. Synced S2C via
 * {@link com.thebeyond.common.network.PlayerKnowledgeSyncPayload} (login snapshot + per-grant delta).
 */
public class PlayerKnowledge implements INBTSerializable<CompoundTag> {

    private final Set<ResourceLocation> known = new HashSet<>();

    public boolean isKnown(ResourceLocation key) {
        return known.contains(key);
    }

    /** @return {@code true} if this grant actually changed state. */
    public boolean grant(ResourceLocation key) {
        return known.add(key);
    }

    /** @return {@code true} if this revoke actually changed state. */
    public boolean revoke(ResourceLocation key) {
        return known.remove(key);
    }

    /** Union import (no key is lost). Used by {@link KnowledgeMode#PER_PLAYER_WITH_IMPORT}. */
    public void copyFrom(PlayerKnowledge other) {
        known.addAll(other.known);
    }

    public Set<ResourceLocation> all() {
        return Collections.unmodifiableSet(known);
    }

    public boolean isEmpty() {
        return known.isEmpty();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (ResourceLocation id : known) {
            list.add(StringTag.valueOf(id.toString()));
        }
        if (!list.isEmpty()) root.put("known", list);
        return root;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        known.clear();
        if (!tag.contains("known", Tag.TAG_LIST)) return;
        ListTag list = tag.getList("known", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
            if (rl != null) known.add(rl);
        }
    }
}
