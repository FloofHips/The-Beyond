package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.effect.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, TheBeyond.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> UNSTABLE = MOB_EFFECTS.register("unstable", () -> new UnstableEffect(
            MobEffectCategory.HARMFUL,
            0x8f2bad));
    public static final DeferredHolder<MobEffect, MobEffect> DEAFENED = MOB_EFFECTS.register("deafened", () -> new DeafenedEffect(
            MobEffectCategory.HARMFUL,
            0x1e68b3));
    public static final DeferredHolder<MobEffect, MobEffect> WEIGHTLESS = MOB_EFFECTS.register("weightless", () -> new WeightlessEffect(
            MobEffectCategory.HARMFUL,
            0x7d00ca).addAttributeModifier(Attributes.GRAVITY,
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "effect.weightless"),
            -0.3,
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    public static final DeferredHolder<MobEffect, MobEffect> NOMADS_BLESSING = MOB_EFFECTS.register("nomads_blessing", () -> new NomadsBlessingEffect(
            MobEffectCategory.BENEFICIAL,
            0x26ce55));

}
