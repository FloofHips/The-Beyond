package com.thebeyond.common.blocks.blockstates;

import net.minecraft.util.StringRepresentable;

public enum StabilityProperty implements StringRepresentable {
    NONE("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    SEEKING("seeking");

    private final String name;

    StabilityProperty(String imbalance) {
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