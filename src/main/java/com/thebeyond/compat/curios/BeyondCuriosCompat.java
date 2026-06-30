package com.thebeyond.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;

/** Soft-dep bridge to Curios (compileOnly); callers MUST gate on {@code ModList.get().isLoaded("curios")} or this fails to link. */
public final class BeyondCuriosCompat {
    private BeyondCuriosCompat() {}

    /** Copies + clears every equipped curio (functional + cosmetic) so the totem carries them back, not another handler (Curios/Corpse). */
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
