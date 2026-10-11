package com.thebeyond.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;

/** Soft bridge to Curios (compileOnly), callers must check that curios is loaded or this fails to link. */
public final class BeyondCuriosCompat {
    private BeyondCuriosCompat() {}

    /** Copies and clears every equipped curio so the totem carries them back instead of Curios or Corpse. */
    public static List<ItemStack> collectAndClear(Player player) {
        List<ItemStack> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (ICurioStacksHandler sh : handler.getCurios().values()) {
                drainInto(sh.getStacks(), out);
                drainInto(sh.getCosmeticStacks(), out);
            }
        });
        return out;
    }

    private static void drainInto(IDynamicStackHandler stacks, List<ItemStack> out) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack s = stacks.getStackInSlot(i);
            if (!s.isEmpty()) {
                out.add(s.copy());
                stacks.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
