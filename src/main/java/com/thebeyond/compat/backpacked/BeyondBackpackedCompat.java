package com.thebeyond.compat.backpacked;

import com.mrcrayfish.backpacked.BackpackHelper;
import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Soft bridge to Backpacked (compileOnly), callers must check that backpacked is loaded or this fails to link. */
public final class BeyondBackpackedCompat {
    private BeyondBackpackedCompat() {}

    /** Unequips every backpack, which Backpacked would add to the cancelled drops, flushing contents it writes a tick late. */
    public static List<ItemStack> collectAndClear(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (Config.BACKPACK.equipable.keepOnDeath.get()) return out;
        if (player instanceof BackpackedInventoryAccess access) {
            access.backpacked$streamNonNullBackpackInventories().forEach(BackpackInventory::saveItemsToStack);
        }
        for (ItemStack stack : BackpackHelper.removeAllBackpacks(player)) {
            if (!stack.isEmpty()) out.add(stack);
        }
        return out;
    }
}
