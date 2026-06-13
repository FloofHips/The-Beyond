package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: step the projector's carousel index by {@code delta} (+1 / -1). */
public record ProjectorCarouselPayload(BlockPos pos, byte delta) implements CustomPacketPayload {
    public static final Type<ProjectorCarouselPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_carousel"));

    public static final StreamCodec<ByteBuf, ProjectorCarouselPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ProjectorCarouselPayload::pos,
            ByteBufCodecs.BYTE, ProjectorCarouselPayload::delta,
            ProjectorCarouselPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
