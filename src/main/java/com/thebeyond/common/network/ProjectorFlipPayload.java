package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: toggle the projector's horizontal flip. */
public record ProjectorFlipPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ProjectorFlipPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_flip"));

    public static final StreamCodec<ByteBuf, ProjectorFlipPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ProjectorFlipPayload::pos,
            ProjectorFlipPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
