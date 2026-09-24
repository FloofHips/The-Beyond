package com.thebeyond.common.network;

import com.thebeyond.client.menu.MemoryBankMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MemoryBankPagePacket(int containerId, int page) implements CustomPacketPayload {
    public static final Type<MemoryBankPagePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("the_beyond", "memory_bank_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MemoryBankPagePacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MemoryBankPagePacket::containerId,
                    ByteBufCodecs.VAR_INT, MemoryBankPagePacket::page,
                    MemoryBankPagePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MemoryBankPagePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof MemoryBankMenu menu && menu.containerId == pkt.containerId()) {
                menu.setPage(pkt.page());
            }
        });
    }
}