package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import com.thebeyond.client.gui.toast.ToastManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShowBonfireTutorialToastPacket() implements CustomPacketPayload {
    public static final Type<ShowBonfireTutorialToastPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "show_bonfire_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowBonfireTutorialToastPacket> STREAM_CODEC =
            StreamCodec.unit(new ShowBonfireTutorialToastPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ShowBonfireTutorialToastPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(ToastManager::showBonfireTutorialToast);
    }
}
