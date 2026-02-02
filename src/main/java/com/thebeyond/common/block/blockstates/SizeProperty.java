package com.thebeyond.common.block.blockstates;

import net.minecraft.util.StringRepresentable;

public enum SizeProperty implements StringRepresentable {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    private final String name;

    SizeProperty(String imbalance) {this.name = imbalance;}

    @Override
    public String toString() {return this.name;}

    @Override
    public String getSerializedName() {return this.name;}
}