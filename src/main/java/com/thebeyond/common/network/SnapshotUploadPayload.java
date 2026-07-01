package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: snapshot pixels for a request id; server applies them to a fresh item. */
public record SnapshotUploadPayload(long requestId, int width, int height, byte[] rgb) implements CustomPacketPayload {
    public static final Type<SnapshotUploadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "snapshot_upload"));

    public static final StreamCodec<ByteBuf, SnapshotUploadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SnapshotUploadPayload::requestId,
            ByteBufCodecs.VAR_INT, SnapshotUploadPayload::width,
            ByteBufCodecs.VAR_INT, SnapshotUploadPayload::height,
            ByteBufCodecs.byteArray(256 * 256 * 3), SnapshotUploadPayload::rgb,
            SnapshotUploadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
