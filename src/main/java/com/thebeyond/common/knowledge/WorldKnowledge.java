package com.thebeyond.common.knowledge;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-wide knowledge store for {@link KnowledgeMode#SHARED_WORLD}. Saved on
 * the overworld's {@code DataStorage} (canonical dim-agnostic slot). Ignored
 * in per-player modes.
 */
public class WorldKnowledge extends SavedData {

    /** Stable across versions — renaming breaks save compat. */
    public static final String DATA_NAME = "the_beyond_world_knowledge";

    private final Set<ResourceLocation> known = new HashSet<>();

    public boolean isKnown(ResourceLocation key) {
        return known.contains(key);
    }

    public boolean grant(ResourceLocation key) {
        boolean changed = known.add(key);
        if (changed) setDirty();
        return changed;
    }

    public boolean revoke(ResourceLocation key) {
        boolean changed = known.remove(key);
        if (changed) setDirty();
        return changed;
    }

    public Set<ResourceLocation> all() {
        return Collections.unmodifiableSet(known);
    }

    public static Factory<WorldKnowledge> factory() {
        return new Factory<>(WorldKnowledge::new, WorldKnowledge::load);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (ResourceLocation id : known) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("known", list);
        return tag;
    }

    public static WorldKnowledge load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldKnowledge data = new WorldKnowledge();
        if (!tag.contains("known", Tag.TAG_LIST)) return data;
        ListTag list = tag.getList("known", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
            if (rl != null) data.known.add(rl);
        }
        return data;
    }

    /** Always resolves via overworld DataStorage so all dims share one store. */
    public static WorldKnowledge get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage()
                .computeIfAbsent(factory(), DATA_NAME);
    }
}
