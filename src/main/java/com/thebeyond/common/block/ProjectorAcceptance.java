package com.thebeyond.common.block;

import net.minecraft.world.item.ItemStack;

/**
 * Any non-empty item is accepted. Display picks per item: snapshot shows captured pixels, a projector-texture data-map
 * entry shows that image, everything else falls back to the inventory (GUI) model.
 */
public final class ProjectorAcceptance {
    private ProjectorAcceptance() {
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty();
    }
}
