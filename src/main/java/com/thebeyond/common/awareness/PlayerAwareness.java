package com.thebeyond.common.awareness;

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

/** What each player has discovered. Tracked in every mode so switching modes never loses data. */
public class PlayerAwareness implements INBTSerializable<CompoundTag> {

    private final Set<ResourceLocation> known = new HashSet<>();

    /** Counter clients watch to know when their cache is stale. Not saved to disk. */
    private int generation = 0;

    public int generation() { return generation; }

    public boolean isKnown(ResourceLocation key) {
        return known.contains(key);
    }

    /** @return {@code true} if this grant actually changed state. */
    public boolean grant(ResourceLocation key) {
        if (!known.add(key)) return false;
        generation++;
        return true;
    }

    /** @return {@code true} if this revoke actually changed state. */
    public boolean revoke(ResourceLocation key) {
        if (!known.remove(key)) return false;
        generation++;
        return true;
    }

    /** Merges another player's discoveries in without dropping any of ours. */
    public void copyFrom(PlayerAwareness other) {
        if (known.addAll(other.known)) generation++;
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
