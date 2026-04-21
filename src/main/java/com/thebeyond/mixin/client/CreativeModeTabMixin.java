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
 * Filter hidden items out of the creative inventory tab display.
 *
 * <p>Adapted from Sammy Semicolon's Malum mod
 * ({@code com.sammy.malum.mixin.client.CreativeModeTabMixin}) — same idea,
 * wired to our per-player {@link HiddenContentFilter} instead of a global
 * tag config.
 *
 * <p><b>Safety</b>: {@code require = 0} means a Mixin failure (e.g. the
 * method signature changed in a future MC version) is a silent skip rather
 * than a mod-load crash. The search-bar tab is skipped because its display
 * is handled by {@link CreativeModeInventoryScreenMixin} instead.
 *
 * <p><b>No effect without configured tags</b>: when all {@code hidden_until/*}
 * item tags are empty, {@link HiddenContentFilter#hide} is a no-op. The mixin
 * is registered unconditionally so populating a tag is sufficient to activate
 * the filter — no further infrastructure changes required.
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
        // Avoid touching the return value when nothing changed — reduces
        // ops cost for the common case (feature disabled or tags empty).
        if (items.size() != sizeBefore) {
            cir.setReturnValue(items);
        }
    }
}
