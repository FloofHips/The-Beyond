package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.world.item.ItemStack;

/** Single source for the camera's slot layout and film cap. */
public final class CameraSlots {
    public static final int SLOTS = 2;
    public static final int FILM = 0;
    public static final int FUEL = 1;
    public static final int MAX_FILM = 10; // film stack cap; also drives the item's durability-bar width

    private CameraSlots() {
    }

    /** The slot a stack belongs in (FILM/FUEL), or -1 if it is neither. */
    public static int slotFor(ItemStack stack) {
        return stack.is(BeyondTags.CAMERA_FILM) ? FILM
                : stack.is(BeyondTags.CAMERA_FUEL) ? FUEL : -1;
    }
}
