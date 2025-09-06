package com.thebeyond.common.block.blockstates;

import net.minecraft.util.StringRepresentable;

public enum HutHeightProperty implements StringRepresentable {
    TOP("top"),
    TIP("tip"),
    CORE("core"),
    BASE("base");

    private final String name;

    HutHeightProperty(String name) {
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
