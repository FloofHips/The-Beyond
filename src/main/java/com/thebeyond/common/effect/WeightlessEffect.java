package com.thebeyond.common.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class WeightlessEffect extends GenericEffect {
    public WeightlessEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (livingEntity instanceof Player player) {
            if (!player.isShiftKeyDown())
                livingEntity.fallDistance = 0;
        } else {
            livingEntity.fallDistance = 0;
        }
        return super.applyEffectTick(livingEntity, amplifier);
    }
}
