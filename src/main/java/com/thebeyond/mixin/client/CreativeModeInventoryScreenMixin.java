package com.thebeyond.mixin.client;

import com.thebeyond.client.event.CreativeTabEvents;
import com.thebeyond.common.awareness.HiddenContentFilter;
import com.thebeyond.common.registry.BeyondTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filters hidden items out of the creative search and keeps the Beyond tab on its category or biome layout. */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeModeInventoryScreenMixin {

    @Shadow private EditBox searchBox;
    @Shadow private static CreativeModeTab selectedTab;

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

    // the Beyond tab has a search bar, so every refill (open, tab switch, resize, tab rebuild, cleared search) ends here
    @Inject(method = "refreshSearchResults", at = @At("TAIL"))
    private void theBeyond$keepBeyondLayout(CallbackInfo ci) {
        if (selectedTab != BeyondTabs.THE_BEYOND.get() || !this.searchBox.getValue().isEmpty()) return;
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        screen.getMenu().items.clear();
        screen.getMenu().items.addAll(CreativeTabEvents.layout());
        screen.getMenu().scrollTo(0.0F);
    }
}
