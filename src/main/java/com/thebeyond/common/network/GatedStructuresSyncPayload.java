package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tells the client which region keys hide each gated structure, so it can gate {@code /locate structure}
 *  itself - structures aren't in the client registry, so it can't just read their tags. */
public record GatedStructuresSyncPayload(Map<ResourceLocation, Set<ResourceLocation>> gated) implements CustomPacketPayload {
    public static final Type<GatedStructuresSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "gated_structures_sync"));

    public static final StreamCodec<ByteBuf, GatedStructuresSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC,
                    ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC)),
            GatedStructuresSyncPayload::gated,
            GatedStructuresSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
