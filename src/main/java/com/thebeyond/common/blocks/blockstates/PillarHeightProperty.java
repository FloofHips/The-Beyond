package com.thebeyond.common.blocks.blockstates;

import net.minecraft.util.StringRepresentable;

public enum PillarHeightProperty implements StringRepresentable {
    TIP("tip"),
    CORE("core"),
    BASE("base");

    private final String name;

    PillarHeightProperty(String imbalance) {
        this.name = imbalance;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
