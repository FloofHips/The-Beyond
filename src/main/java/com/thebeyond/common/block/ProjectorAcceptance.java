package com.thebeyond.common.block;

import net.minecraft.world.item.ItemStack;

/** Accepts any item: a snapshot shows its pixels, a data-map texture its image, anything else its inventory model. */
public final class ProjectorAcceptance {
    private ProjectorAcceptance() {
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty();
    }
}
