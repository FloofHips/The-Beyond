package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.effect.DeafenedEffect;
import com.thebeyond.common.effect.UnstableEffect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, TheBeyond.MODID);

    public static final DeferredHolder<MobEffect, UnstableEffect> UNSTABLE = MOB_EFFECTS.register("unstable", () -> new UnstableEffect(
            MobEffectCategory.NEUTRAL,
            0x8f2bad));
    public static final DeferredHolder<MobEffect, DeafenedEffect> DEAFENED = MOB_EFFECTS.register("deafened", () -> new DeafenedEffect(
            MobEffectCategory.HARMFUL,
            0x1e68b3));
}
