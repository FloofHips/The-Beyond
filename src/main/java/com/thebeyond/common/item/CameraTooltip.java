package com.thebeyond.common.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Rendered by {@code ClientCameraTooltip}; keep free of client/render imports so it stays loadable server-side.
 */
public record CameraTooltip(ItemStack film, ItemStack fuel) implements TooltipComponent {
}
