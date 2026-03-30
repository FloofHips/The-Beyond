package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.block.blockentities.RefugeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class BeyondNetworking {

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TheBeyond.MODID);

        registrar.playToServer(
                RefugeSetModePayload.TYPE,
                RefugeSetModePayload.STREAM_CODEC,
                BeyondNetworking::handleSetMode
        );

        registrar.playToClient(
                RefugeActivatePayload.TYPE,
                RefugeActivatePayload.STREAM_CODEC,
                BeyondNetworking::handleActivateClient
        );
    }

    private static void handleSetMode(RefugeSetModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ServerLevel level = player.serverLevel();
            BlockPos pos = payload.pos();

            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RefugeBlockEntity refuge) {
                // Consume the payment item from the player's open menu
                if (player.containerMenu instanceof RefugeMenu refugeMenu) {
                    if (!refugeMenu.hasPayment()) return;
                    refugeMenu.getSlot(0).remove(1);
                }

                refuge.setMode(payload.mode(), refuge);
                refuge.animating = 20;
                refuge.setChanged();
                level.sendBlockUpdated(pos, refuge.getBlockState(), refuge.getBlockState(), 3);

                PacketDistributor.sendToPlayersTrackingChunk(level, level.getChunk(pos).getPos(),
                        new RefugeActivatePayload(pos, payload.mode()));
            }
        });
    }

    private static void handleActivateClient(RefugeActivatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // context.player().level() is safe on client side
            BlockEntity be = context.player().level().getBlockEntity(payload.pos());
            if (be instanceof RefugeBlockEntity refuge) {
                refuge.animating = 20;
            }
        });
    }
}
