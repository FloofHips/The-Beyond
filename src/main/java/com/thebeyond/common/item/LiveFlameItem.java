package com.thebeyond.common.item;

import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class LiveFlameItem extends Item {
    public LiveFlameItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        int damage = 1;
        if (entity instanceof LivingEntity livingEntity) {
            damage*= livingEntity.isFallFlying() ? 5 : 1;
            if (livingEntity instanceof Player player) {
                damage*= player.getAbilities().flying ? 20 : 1;
            }
            damage*= livingEntity.isSprinting() ? 2 : 1;
            if (!isSelected) {
                damage*=4;
            }
            stack.hurtAndBreak(damage, livingEntity, EquipmentSlot.MAINHAND);
            if (level.random.nextBoolean())
                livingEntity.level().addParticle(stack.is(BeyondItems.LIVID_FLAME) ? BeyondParticleTypes.VOID_FLAME.get() : ParticleTypes.SOUL_FIRE_FLAME, livingEntity.getX() + level.random.nextGaussian()*0.2, livingEntity.getY() + 1 + level.random.nextGaussian()*0.2, livingEntity.getZ() + level.random.nextGaussian()*0.2,level.random.nextGaussian()*0.01, 0.02, level.random.nextGaussian()*0.01);
        }
    }

    @Override
    public SoundEvent getBreakingSound() {
        return SoundEvents.FIRECHARGE_USE;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return super.isBarVisible(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return -16711696;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return super.getBarWidth(stack);
    }
}
