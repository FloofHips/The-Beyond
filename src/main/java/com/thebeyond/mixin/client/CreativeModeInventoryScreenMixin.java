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
 * Filters hidden items out of the creative inventory search. The search tab populates via
 * {@code NonNullList#addAll}, so {@code menu.items} is filtered after that call.
 * {@code require = 0} so a target-signature change silently skips rather than crashes.
 *
 * <p>Adapted from Malum ({@code com.sammy.malum.mixin.client.CreativeModeInventoryScreenMixin}).
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
