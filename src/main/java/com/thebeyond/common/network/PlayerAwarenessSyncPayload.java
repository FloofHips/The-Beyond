package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/** Server-to-client awareness sync: replace overwrites the client's set, otherwise the keys are added. */
public record PlayerAwarenessSyncPayload(Set<ResourceLocation> keys, boolean replace) implements CustomPacketPayload {
    public static final Type<PlayerAwarenessSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "player_awareness_sync"));

    public static final StreamCodec<ByteBuf, PlayerAwarenessSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC),
            PlayerAwarenessSyncPayload::keys,
            ByteBufCodecs.BOOL, PlayerAwarenessSyncPayload::replace,
            PlayerAwarenessSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
