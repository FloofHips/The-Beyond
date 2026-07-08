package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GaussVentParticlePayload(BlockPos pos, boolean isBig) implements CustomPacketPayload {
    public static final Type<GaussVentParticlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "gauss_vent_particle"));

    public static final StreamCodec<ByteBuf, GaussVentParticlePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GaussVentParticlePayload::pos,
            ByteBufCodecs.BOOL, GaussVentParticlePayload::isBig,
            GaussVentParticlePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
