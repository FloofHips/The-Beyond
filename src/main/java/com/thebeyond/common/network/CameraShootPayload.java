package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** C2S: player confirmed a handheld shot. */
public record CameraShootPayload(InteractionHand hand) implements CustomPacketPayload {
    public static final Type<CameraShootPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "camera_shoot"));

    public static final StreamCodec<ByteBuf, CameraShootPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, payload -> (byte) payload.hand.ordinal(),
            handByte -> new CameraShootPayload(InteractionHand.values()[handByte])
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
