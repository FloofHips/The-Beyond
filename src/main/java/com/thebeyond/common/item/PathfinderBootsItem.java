package com.thebeyond.common.item;

import com.thebeyond.client.model.equipment.MultipartArmorModel;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.function.Supplier;

public class PathfinderBootsItem extends ModelArmorItem {
    public PathfinderBootsItem(Holder<ArmorMaterial> material, Type slot, Properties properties, Supplier<MultipartArmorModel> modelSupplier) {
        super(material, slot, properties, modelSupplier);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {

        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }
}
