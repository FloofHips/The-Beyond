package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLOP = PARTICLE_TYPES.register("glop", () -> new SimpleParticleType(false) {});
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AURORACITE_STEP = PARTICLE_TYPES.register("auroracite_step", () -> new SimpleParticleType(false) {});
}
