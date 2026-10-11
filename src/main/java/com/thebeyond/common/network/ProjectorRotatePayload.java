package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: turns the projected image steps times 90 degrees, the block facing stays. */
public record ProjectorRotatePayload(BlockPos pos, byte steps) implements CustomPacketPayload {
    public static final Type<ProjectorRotatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_rotate"));

    public static final StreamCodec<ByteBuf, ProjectorRotatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ProjectorRotatePayload::pos,
            ByteBufCodecs.BYTE, ProjectorRotatePayload::steps,
            ProjectorRotatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
