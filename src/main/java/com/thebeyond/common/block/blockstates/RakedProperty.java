package com.thebeyond.common.block.blockstates;

import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;

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

    public static RakedProperty getRandom(int i) {
        return switch (i) {
            case 1 -> EW;
            case 2 -> NW;
            case 3 -> NE;
            case 4 -> SW;
            case 5 -> SE;
            default -> NS;
        };
    }
}
