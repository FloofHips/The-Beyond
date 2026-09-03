package com.thebeyond.common.creative;

import net.minecraft.world.item.CreativeModeTab;

public class BeyondCreativeModeTab extends CreativeModeTab {
    protected BeyondCreativeModeTab(Builder builder) {
        super(builder);
    }

    @Override
    public boolean isAlignedRight() {
        return false;
    }
}
