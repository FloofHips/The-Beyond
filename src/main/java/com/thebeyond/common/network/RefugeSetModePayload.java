package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent from client to server when the player confirms a mode selection in the Refuge GUI.
 */
public record RefugeSetModePayload(BlockPos pos, byte mode) implements CustomPacketPayload {
    public static final Type<RefugeSetModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "refuge_set_mode"));

    public static final StreamCodec<ByteBuf, RefugeSetModePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RefugeSetModePayload::pos,
            ByteBufCodecs.BYTE, RefugeSetModePayload::mode,
            RefugeSetModePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
