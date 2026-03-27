package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent from server to client when the refuge mode is confirmed.
 * Triggers the activation animation on the client renderer.
 */
public record RefugeActivatePayload(BlockPos pos, byte mode) implements CustomPacketPayload {
    public static final Type<RefugeActivatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "refuge_activate"));

    public static final StreamCodec<ByteBuf, RefugeActivatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RefugeActivatePayload::pos,
            ByteBufCodecs.BYTE, RefugeActivatePayload::mode,
            RefugeActivatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
