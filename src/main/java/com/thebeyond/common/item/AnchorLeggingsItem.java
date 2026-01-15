package com.thebeyond.common.item;

import com.thebeyond.client.model.equipment.MultipartArmorModel;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public class AnchorLeggingsItem extends ModelArmorItem {
    public AnchorLeggingsItem(Holder<ArmorMaterial> material, Type type, Properties properties, Supplier<MultipartArmorModel> modelSupplier) {
        super(material, type, properties, modelSupplier);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.POWER);
    }
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (slotId != 37) {
            super.inventoryTick(stack, level, entity, slotId, isSelected);
            return;
        }
        if (entity instanceof Player player) {
            if (player.isShiftKeyDown()) {
                Registry<Enchantment> enchantmentRegistry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
                Holder<Enchantment> powerHolder = enchantmentRegistry.getHolderOrThrow(Enchantments.POWER);
                int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(powerHolder, stack);

                player.stopFallFlying();
                player.setDeltaMovement(player.getDeltaMovement().subtract(0, 0.08 * (1 + 0.5 * powerLevel), 0));
                player.hurtMarked = true;
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }
}
