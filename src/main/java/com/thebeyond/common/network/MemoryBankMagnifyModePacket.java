package com.thebeyond.common.network;

import com.thebeyond.client.menu.MemoryBankMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MemoryBankMagnifyModePacket(int containerId) implements CustomPacketPayload {
    public static final Type<MemoryBankMagnifyModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("the_beyond", "memory_bank_magnify_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MemoryBankMagnifyModePacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MemoryBankMagnifyModePacket::containerId,
                    MemoryBankMagnifyModePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MemoryBankMagnifyModePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof MemoryBankMenu menu && menu.containerId == pkt.containerId()) {
                menu.magnifyMode = !menu.magnifyMode;
                if (!menu.magnifyMode) menu.clearMagnify();
            }
        });
    }
}
