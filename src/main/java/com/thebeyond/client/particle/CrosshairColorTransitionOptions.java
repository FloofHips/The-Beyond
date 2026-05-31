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

public record CrosshairColorTransitionOptions(Vector3f fromColor, Vector3f toColor, float scale) implements ParticleOptions {
    public static final MapCodec<CrosshairColorTransitionOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ExtraCodecs.VECTOR3F.fieldOf("from_color").forGetter(CrosshairColorTransitionOptions::fromColor),
                    ExtraCodecs.VECTOR3F.fieldOf("to_color").forGetter(CrosshairColorTransitionOptions::toColor),
                    ExtraCodecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(CrosshairColorTransitionOptions::scale)
            ).apply(instance, CrosshairColorTransitionOptions::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrosshairColorTransitionOptions> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VECTOR3F, CrosshairColorTransitionOptions::fromColor,
                    ByteBufCodecs.VECTOR3F, CrosshairColorTransitionOptions::toColor,
                    ByteBufCodecs.FLOAT, CrosshairColorTransitionOptions::scale,
                    CrosshairColorTransitionOptions::new
            );

    public Vector3f getFromColor() {
        return fromColor;
    }
    public Vector3f getToColor() {
        return toColor;
    }

    @Override
    public ParticleType<?> getType() {
        return BeyondParticleTypes.CROSSHAIR.get();
    }
}