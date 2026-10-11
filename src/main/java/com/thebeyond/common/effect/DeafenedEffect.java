package com.thebeyond.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Marker only: on a mob it forces cone-and-sight targeting and sends vibration sensers to their anger branch. */
public class DeafenedEffect extends MobEffect {
    public DeafenedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
