package com.thebeyond.mixin.client;

import com.thebeyond.common.awareness.HiddenContentFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filters hidden items out of the creative inventory search by post-filtering
 *  {@code menu.items} after {@code NonNullList#addAll}. {@code require = 0} for safety. */
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
