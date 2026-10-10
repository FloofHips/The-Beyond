package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.gui.toast.ToastManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShowCastingTutorialToastPacket() implements CustomPacketPayload {
    public static final Type<ShowCastingTutorialToastPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "show_casting_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowCastingTutorialToastPacket> STREAM_CODEC =
            StreamCodec.unit(new ShowCastingTutorialToastPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ShowCastingTutorialToastPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(ToastManager::showBrittleMetalTutorialToast);
    }
}
