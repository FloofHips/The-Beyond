package com.thebeyond.mixin.client;

import com.thebeyond.common.knowledge.HiddenContentFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Filters hidden items out of creative inventory tab display. Wired to per-player
 * {@link HiddenContentFilter}; search-bar tab is skipped (handled by
 * {@link CreativeModeInventoryScreenMixin}). {@code require = 0} so a target-signature
 * change silently skips rather than crashes mod load.
 *
 * <p>Adapted from Malum ({@code com.sammy.malum.mixin.client.CreativeModeTabMixin}).
 */
@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {

    @Inject(
            method = "getDisplayItems",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void theBeyond$filterHiddenItems(CallbackInfoReturnable<Collection<ItemStack>> cir) {
        CreativeModeTab tab = (CreativeModeTab) (Object) this;
        if (tab.hasSearchBar()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Collection<ItemStack> items = new ArrayList<>(cir.getReturnValue());
        int sizeBefore = items.size();
        HiddenContentFilter.hide(items, player);
        // Skip the setReturnValue when no items were filtered.
        if (items.size() != sizeBefore) {
            cir.setReturnValue(items);
        }
    }
}
