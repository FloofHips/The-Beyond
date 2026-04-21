package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * S2C sync for {@link com.thebeyond.common.knowledge.PlayerKnowledge}. Attachments don't auto-sync
 * across sides, so the client-side {@link com.thebeyond.common.knowledge.HiddenContentFilter}
 * would read an empty set without this packet.
 *
 * <p>{@link #replace} {@code true} → client overwrites its local set with {@link #keys}. Used for
 * the login snapshot and for revokes (delta can't express subtraction).
 * <p>{@link #replace} {@code false} → client unions {@link #keys} into its local set. Used for
 * per-grant deltas. {@link #keys} is typically a single element but the wire format is a set so
 * batched grants stay cheap.
 */
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
