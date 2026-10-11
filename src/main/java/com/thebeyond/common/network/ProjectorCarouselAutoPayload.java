package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S. */
public record ProjectorCarouselAutoPayload(BlockPos pos, boolean auto) implements CustomPacketPayload {
    public static final Type<ProjectorCarouselAutoPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_carousel_auto"));

    public static final StreamCodec<ByteBuf, ProjectorCarouselAutoPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ProjectorCarouselAutoPayload::pos,
            ByteBufCodecs.BOOL, ProjectorCarouselAutoPayload::auto,
            ProjectorCarouselAutoPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
