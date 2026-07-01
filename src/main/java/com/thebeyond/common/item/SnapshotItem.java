package com.thebeyond.common.item;

import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.registry.BeyondComponents;
import com.thebeyond.common.camera.Grades;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Keeps the missing-texture model on purpose; the photo shows only as a tooltip image. */
public class SnapshotItem extends Item {
    public SnapshotItem(Properties properties) {
        super(properties);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        Components.SnapshotPixelsComponent px = stack.get(BeyondComponents.SNAPSHOT_PIXELS.get());
        if (px == null || !px.isRenderable()) {
            return Optional.empty();
        }
        return Optional.of(new SnapshotTooltip(px, Grades.photoGrade(stack)));
    }
}
