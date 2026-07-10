package com.thebeyond.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marker only — no tick logic. On a mob it forces FOV+LOS-only targeting ({@code BeyondEntityEvents})
 * and reroutes vibration sensers like the Warden to their anger branch (Warden mixin).
 */
public class DeafenedEffect extends MobEffect {
    public DeafenedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
