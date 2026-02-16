package com.thebeyond.common.registry;

import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondParticleTypes {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, TheBeyond.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLOP = PARTICLE_TYPES.register("glop", () -> new SimpleParticleType(false) {});
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> AURORACITE_STEP = PARTICLE_TYPES.register("auroracite_step", () -> new SimpleParticleType(false) {});
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOID_FLAME = PARTICLE_TYPES.register("void_flame", () -> new SimpleParticleType(false) {});

    public static final DeferredHolder<ParticleType<?>, ParticleType<SmokeColorTransitionOptions>> SMOKE = PARTICLE_TYPES.register("smoke", BeyondParticleTypes::createSmokeParticleType);
    public static final DeferredHolder<ParticleType<?>, ParticleType<PixelColorTransitionOptions>> PIXEL = PARTICLE_TYPES.register("pixel", BeyondParticleTypes::createPixelParticleType);

    private static ParticleType<SmokeColorTransitionOptions> createSmokeParticleType() {
        return new ParticleType<SmokeColorTransitionOptions>(false) {
            @Override
            public MapCodec<SmokeColorTransitionOptions> codec() {
                return SmokeColorTransitionOptions.CODEC;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, SmokeColorTransitionOptions> streamCodec() {
                return SmokeColorTransitionOptions.STREAM_CODEC;
            }
        };
    }

    private static ParticleType<PixelColorTransitionOptions> createPixelParticleType() {
        return new ParticleType<PixelColorTransitionOptions>(false) {
            @Override
            public MapCodec<PixelColorTransitionOptions> codec() {
                return PixelColorTransitionOptions.CODEC;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, PixelColorTransitionOptions> streamCodec() {
                return PixelColorTransitionOptions.STREAM_CODEC;
            }
        };
    }
}
