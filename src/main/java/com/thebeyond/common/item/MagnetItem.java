package com.thebeyond.common.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class MagnetItem extends Item {
    public final double range;
    public MagnetItem(Properties properties, double range) {
        super(properties);
        this.range = range;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (slotId < 9 || slotId == 40) {
            double halfRange = range/2;
            level.getEntitiesOfClass(ItemEntity.class, new AABB(entity.position().subtract(halfRange, halfRange, halfRange), entity.position().add(halfRange, halfRange, halfRange)))
                    .forEach(itemEntity -> {
                                double factor = 0.01*range / itemEntity.position().vectorTo(entity.position()).length();
                                itemEntity.addDeltaMovement(itemEntity.position().vectorTo(entity.position()).multiply(factor, factor, factor));
                            }
                    );
        }
    }
}
