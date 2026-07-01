package com.thebeyond.client.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-spawn lifetime, so the jet length tracks the Bellow's redstone-driven reach. */
public record BellowJetOptions(int lifetime) implements ParticleOptions {
    public static final MapCodec<BellowJetOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("lifetime").forGetter(BellowJetOptions::lifetime)
            ).apply(instance, BellowJetOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BellowJetOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BellowJetOptions::lifetime,
                    BellowJetOptions::new
            );

    @Override
    public ParticleType<?> getType() {
        return BeyondParticleTypes.BELLOW_JET.get();
    }
}
