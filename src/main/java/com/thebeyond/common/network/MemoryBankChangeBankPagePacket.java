package com.thebeyond.common.network;

import com.thebeyond.client.menu.MemoryBankMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MemoryBankChangeBankPagePacket(int containerId, int delta) implements CustomPacketPayload {
    public static final Type<MemoryBankChangeBankPagePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("the_beyond", "memory_bank_change_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MemoryBankChangeBankPagePacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MemoryBankChangeBankPagePacket::containerId,
                    ByteBufCodecs.VAR_INT, MemoryBankChangeBankPagePacket::delta,
                    MemoryBankChangeBankPagePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MemoryBankChangeBankPagePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof MemoryBankMenu menu && menu.containerId == pkt.containerId()) {
                menu.changePage(pkt.delta(), ctx.player());
            }
        });
    }
}