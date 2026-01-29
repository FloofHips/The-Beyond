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

public record PixelColorTransitionOptions(Vector3f fromColor, Vector3f toColor, float scale) implements ParticleOptions {
    public static final MapCodec<PixelColorTransitionOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ExtraCodecs.VECTOR3F.fieldOf("from_color").forGetter(PixelColorTransitionOptions::fromColor),
                    ExtraCodecs.VECTOR3F.fieldOf("to_color").forGetter(PixelColorTransitionOptions::toColor),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(PixelColorTransitionOptions::scale)
            ).apply(instance, PixelColorTransitionOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PixelColorTransitionOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VECTOR3F, PixelColorTransitionOptions::fromColor,
                    ByteBufCodecs.VECTOR3F, PixelColorTransitionOptions::toColor,
                    ByteBufCodecs.FLOAT, PixelColorTransitionOptions::scale,
                    PixelColorTransitionOptions::new
            );

    public Vector3f getFromColor() {
        return fromColor;
    }
    public Vector3f getToColor() {
        return toColor;
    }

    @Override
    public ParticleType<?> getType() {
        return BeyondParticleTypes.PIXEL.get();
    }
}