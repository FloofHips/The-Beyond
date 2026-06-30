package com.thebeyond.mixin.client;

import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;

/** Hides locked items from creative tabs. The result is cached since the game asks for it constantly. */
@Mixin(CreativeModeTab.class)
public class CreativeModeTabMixin {

    @Unique private Collection<ItemStack> theBeyond$cachedResult;
    @Unique private Collection<ItemStack> theBeyond$cachedSource;
    @Unique private int theBeyond$cachedGen = Integer.MIN_VALUE;

    @Inject(
            method = "getDisplayItems",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void theBeyond$filterHiddenItems(CallbackInfoReturnable<Collection<ItemStack>> cir) {
        CreativeModeTab tab = (CreativeModeTab) (Object) this;
        if (tab.hasSearchBar()) return;
        // Nothing to hide when the gate is off.
        if (!BeyondAwareness.gateEnabled()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Collection<ItemStack> source = cir.getReturnValue();
        int gen = player.getData(BeyondAttachments.PLAYER_AWARENESS).generation();
        if (source == theBeyond$cachedSource && gen == theBeyond$cachedGen && theBeyond$cachedResult != null) {
            cir.setReturnValue(theBeyond$cachedResult);
            return;
        }

        Collection<ItemStack> items = new ArrayList<>(source);
        int sizeBefore = items.size();
        HiddenContentFilter.hide(items, player);
        // If we hid nothing, reuse the original list so we don't keep a needless copy.
        Collection<ItemStack> result = (items.size() != sizeBefore) ? items : source;

        theBeyond$cachedSource = source;
        theBeyond$cachedGen = gen;
        theBeyond$cachedResult = result;
        cir.setReturnValue(result);
    }
}
