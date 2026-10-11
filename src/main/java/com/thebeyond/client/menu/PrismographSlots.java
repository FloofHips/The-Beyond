package com.thebeyond.client.menu;

import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.world.item.ItemStack;

/** Single source for the prismograph's slot layout and film cap. */
public final class PrismographSlots {
    public static final int SLOTS = 1;
    public static final int FILM = 0;
    public static final int MAX_FILM = 64; // film stack cap; also drives the item's durability-bar width

    private PrismographSlots() {
    }

    /** The slot a stack belongs in (FILM/FUEL), or -1 if it is neither. */
    public static int slotFor(ItemStack stack) {
        return stack.is(BeyondTags.PRISMOGRAPH_FILM) ? FILM : -1;
    }
}
