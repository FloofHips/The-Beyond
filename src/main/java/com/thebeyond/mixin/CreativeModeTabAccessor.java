package com.thebeyond.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;

@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.item.CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor
    Collection<ItemStack> getDisplayItems();

    @Accessor
    void setDisplayItems(Collection<ItemStack> items);
}
