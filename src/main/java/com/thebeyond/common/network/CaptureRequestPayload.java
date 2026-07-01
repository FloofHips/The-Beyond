package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: capture the screen and upload pixels for this request id. */
public record CaptureRequestPayload(long requestId) implements CustomPacketPayload {
    public static final Type<CaptureRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "capture_request"));

    public static final StreamCodec<ByteBuf, CaptureRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CaptureRequestPayload::requestId,
            CaptureRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
