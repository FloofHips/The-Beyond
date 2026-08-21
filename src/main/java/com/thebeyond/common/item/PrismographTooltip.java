package com.thebeyond.common.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Rendered by {@code ClientPrismographTooltip}; keep free of client/render imports so it stays loadable server-side.
 */
public record PrismographTooltip(ItemStack film, ItemStack fuel) implements TooltipComponent {
}
