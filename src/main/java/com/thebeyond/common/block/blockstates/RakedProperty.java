package com.thebeyond.common.block.blockstates;

import net.minecraft.util.StringRepresentable;

public enum RakedProperty implements StringRepresentable {
    NS("ns"),
    EW("ew"),
    NW("nw"),
    NE("ne"),
    SW("sw"),
    SE("se");

    private final String name;

    RakedProperty(String name) {
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
