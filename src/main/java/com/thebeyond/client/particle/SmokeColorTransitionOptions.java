package com.thebeyond.client.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

public record SmokeColorTransitionOptions(Vector3f fromColor, Vector3f toColor, float scale) implements ParticleOptions {
    public static final MapCodec<SmokeColorTransitionOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ExtraCodecs.VECTOR3F.fieldOf("from_color").forGetter(SmokeColorTransitionOptions::fromColor),
                    ExtraCodecs.VECTOR3F.fieldOf("to_color").forGetter(SmokeColorTransitionOptions::toColor),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(SmokeColorTransitionOptions::scale)
            ).apply(instance, SmokeColorTransitionOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SmokeColorTransitionOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VECTOR3F, SmokeColorTransitionOptions::fromColor,
                    ByteBufCodecs.VECTOR3F, SmokeColorTransitionOptions::toColor,
                    ByteBufCodecs.FLOAT, SmokeColorTransitionOptions::scale,
                    SmokeColorTransitionOptions::new
            );

    public Vector3f getFromColor() {
        return fromColor;
    }
    public Vector3f getToColor() {
        return toColor;
    }
    public float getScale() {
        return scale;
    }
    @Override
    public ParticleType<?> getType() {
        return BeyondParticleTypes.SMOKE.get();
    }
}
