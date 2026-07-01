package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: set the projector's snapshot filter by data-driven grade id. */
public record ProjectorSetGradePayload(BlockPos pos, ResourceLocation gradeId) implements CustomPacketPayload {
    public static final Type<ProjectorSetGradePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "projector_set_grade"));

    public static final StreamCodec<ByteBuf, ProjectorSetGradePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ProjectorSetGradePayload::pos,
            ResourceLocation.STREAM_CODEC, ProjectorSetGradePayload::gradeId,
            ProjectorSetGradePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
