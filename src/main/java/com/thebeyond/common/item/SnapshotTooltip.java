package com.thebeyond.common.item;

import com.thebeyond.common.item.components.Components;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** No client/render imports so it stays server-loadable; {@code ClientSnapshotTooltip} draws it. */
public record SnapshotTooltip(Components.SnapshotPixelsComponent pixels, ResourceLocation gradeId) implements TooltipComponent {
}
