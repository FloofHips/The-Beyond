package com.thebeyond.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Accessor
    static CreativeModeTab getSelectedTab() {
        throw new UnsupportedOperationException();
    }

    @Invoker
    void callRefreshSearchResults();

    @Invoker
    void callRefreshCurrentTabContents(Collection<ItemStack> items);
}
