package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.block.blockentities.RefugeMenu;
import com.thebeyond.common.awareness.HiddenContentFilter;
import com.thebeyond.common.awareness.PlayerAwareness;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.compat.jei.JeiCompatBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashSet;

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

        registrar.playToClient(
                PlayerAwarenessSyncPayload.TYPE,
                PlayerAwarenessSyncPayload.STREAM_CODEC,
                BeyondNetworking::handleAwarenessSyncClient
        );

        registrar.playToClient(
                GatedStructuresSyncPayload.TYPE,
                GatedStructuresSyncPayload.STREAM_CODEC,
                BeyondNetworking::handleGatedStructuresClient
        );
    }

    /** Cache the gated structures so the client can gate /locate. */
    private static void handleGatedStructuresClient(GatedStructuresSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HiddenContentFilter.setClientGatedStructures(payload.gated()));
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
                    if (payload.mode() != -1 && !refugeMenu.hasPayment()) return;
                    refugeMenu.getSlot(0).remove(1);
                }

                refuge.setMode(payload.mode(), refuge);
                refuge.animating = 100;
                refuge.setChanged();
                level.sendBlockUpdated(pos, refuge.getBlockState(), refuge.getBlockState(), 3);

                for (ChunkPos chunkPos : refuge.getAffectedChunks()) {
                    BlockPos cpos = new BlockPos(chunkPos.x*16, be.getBlockPos().getY()+1, chunkPos.z*16);
                    be.getLevel().playSound(null, cpos.getX(), cpos.getY(), cpos.getZ(), BeyondSoundEvents.ROOTS_SPREAD, SoundSource.BLOCKS, 1, 0.8f + be.getLevel().random.nextFloat()*0.5f);
                }

                PacketDistributor.sendToPlayersTrackingChunk(level, level.getChunk(pos).getPos(),
                        new RefugeActivatePayload(pos, payload.mode()));
            }
        });
    }

    private static void handleActivateClient(RefugeActivatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BlockEntity be = context.player().level().getBlockEntity(payload.pos());
            if (be instanceof RefugeBlockEntity refuge) {
                refuge.animating = 100;
            }
        });
    }

    /** Apply a awareness sync to the local player and refresh JEI. */
    private static void handleAwarenessSyncClient(PlayerAwarenessSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player == null) return;
            PlayerAwareness pk = player.getData(BeyondAttachments.PLAYER_AWARENESS);
            if (payload.replace()) {
                // Copy first - can't iterate the live view while revoking from it.
                for (ResourceLocation existing : new HashSet<>(pk.all())) {
                    pk.revoke(existing);
                }
            }
            for (ResourceLocation key : payload.keys()) {
                pk.grant(key);
            }
            JeiCompatBridge.refresh();
        });
    }
}
