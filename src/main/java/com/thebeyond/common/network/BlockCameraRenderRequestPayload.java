package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server -> client: render from this POV, upload pixels back for requestId. */
public record BlockCameraRenderRequestPayload(long requestId, Vec3 eye, Vec3 forward) implements CustomPacketPayload {
    public static final Type<BlockCameraRenderRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "block_camera_render"));

    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y,
            ByteBufCodecs.DOUBLE, Vec3::z,
            Vec3::new);

    public static final StreamCodec<ByteBuf, BlockCameraRenderRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BlockCameraRenderRequestPayload::requestId,
            VEC3, BlockCameraRenderRequestPayload::eye,
            VEC3, BlockCameraRenderRequestPayload::forward,
            BlockCameraRenderRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
