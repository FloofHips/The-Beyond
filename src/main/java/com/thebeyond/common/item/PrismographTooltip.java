package com.thebeyond.common.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Rendered by {@code ClientCameraTooltip}, and free of client imports so the record still loads on a dedicated server. */
public record PrismographTooltip(ItemStack film) implements TooltipComponent {
}
