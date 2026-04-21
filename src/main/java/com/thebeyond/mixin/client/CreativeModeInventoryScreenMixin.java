package com.thebeyond.mixin.client;

import com.thebeyond.common.knowledge.HiddenContentFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Filter hidden items out of the creative inventory search results.
 *
 * <p>Adapted from Sammy Semicolon's Malum mod
 * ({@code com.sammy.malum.mixin.client.CreativeModeInventoryScreenMixin}).
 * The search tab is populated differently from regular tabs — it calls
 * {@code NonNullList#addAll} to collect every item, so we filter
 * {@code menu.items} after that call.
 *
 * <p><b>Safety</b>: {@code require = 0} — if the MC patch changes the
 * injection target, the mixin silently skips instead of crashing mod load.
 *
 * <p>No effect in scaffolding (empty hidden-until tags).
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

    @Inject(
            method = "refreshSearchResults",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/NonNullList;addAll(Ljava/util/Collection;)Z",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void theBeyond$filterSearchResults(CallbackInfo ci) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        HiddenContentFilter.hide(screen.getMenu().items, player);
    }
}
