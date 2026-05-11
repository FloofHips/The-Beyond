package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/** S2C sync for {@code PlayerKnowledge}. {@code replace=true} overwrites the client's set
 *  (login snapshot or revoke); {@code replace=false} unions for per-grant deltas. Set wire
 *  format keeps batched grants cheap. */
public record PlayerKnowledgeSyncPayload(Set<ResourceLocation> keys, boolean replace) implements CustomPacketPayload {
    public static final Type<PlayerKnowledgeSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "player_knowledge_sync"));

    public static final StreamCodec<ByteBuf, PlayerKnowledgeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC),
            PlayerKnowledgeSyncPayload::keys,
            ByteBufCodecs.BOOL, PlayerKnowledgeSyncPayload::replace,
            PlayerKnowledgeSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
