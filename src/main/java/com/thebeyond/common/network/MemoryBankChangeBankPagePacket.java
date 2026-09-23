package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MemoryBankChangeBankPagePacket(int containerId, int delta) implements CustomPacketPayload {
    public static final Type<MemoryBankChangeBankPagePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "memory_bank_change_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MemoryBankChangeBankPagePacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MemoryBankChangeBankPagePacket::containerId,
                    ByteBufCodecs.VAR_INT, MemoryBankChangeBankPagePacket::delta,
                    MemoryBankChangeBankPagePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
