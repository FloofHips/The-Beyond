package com.thebeyond.common.effect;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class GenericEffect extends MobEffect {
    public GenericEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    public GenericEffect(MobEffectCategory category, int color, ParticleOptions particle) {
        super(category, color, particle);
    }
}
