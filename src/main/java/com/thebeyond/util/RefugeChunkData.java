package com.thebeyond.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class RefugeChunkData implements INBTSerializable<CompoundTag> {
    private final byte[] refugeModes = new byte[4];

    public void addRefuge(byte mode) {
        refugeModes[mode] = (byte)(refugeModes[mode]+1);
    }

    public void removeRefuge(byte mode) {
        refugeModes[mode] = (byte)(refugeModes[mode]-1);
    }

    public boolean shouldPreventHunger() {
        return refugeModes[0] > 0;
    }

    public boolean shouldPreventExplosion() {
        return refugeModes[1] > 0;
    }

    public boolean shouldPreventMobSpawn() {
        return refugeModes[2] > 0;
    }

    public boolean shouldPreventFallDamage() {
        return refugeModes[3] > 0;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("RefugeModes", refugeModes);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag compoundTag) {
        refugeModes[0] = refugeModes[1] = refugeModes[2] = refugeModes[3] = 0;

        byte[] loadedCounts = compoundTag.getByteArray("RefugeModes");
        if (loadedCounts.length == 4) {
            System.arraycopy(loadedCounts, 0, refugeModes, 0, 4);
        }
    }
}