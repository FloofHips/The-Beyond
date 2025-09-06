package com.thebeyond.common.block.blockstates;

import net.minecraft.util.StringRepresentable;

public enum PillarHeightProperty implements StringRepresentable {
    TIP("tip"),
    CORE("core"),
    BASE("base");

    private final String name;

    PillarHeightProperty(String name) {
        this.name = name;
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
