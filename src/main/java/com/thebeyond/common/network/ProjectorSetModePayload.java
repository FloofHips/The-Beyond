package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S; mode is 0..3. */
public record ProjectorSetModePayload(BlockPos pos, byte mode) implements CustomPacketPayload {
    public static final Type<ProjectorSetModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_set_mode"));

    public static final StreamCodec<ByteBuf, ProjectorSetModePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ProjectorSetModePayload::pos,
            ByteBufCodecs.BYTE, ProjectorSetModePayload::mode,
            ProjectorSetModePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
