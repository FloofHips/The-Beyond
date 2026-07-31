package com.thebeyond.common.network;

import com.thebeyond.TheBeyond;
import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.BellowJetOptions;
import com.thebeyond.common.block.BellowBlock;
import com.thebeyond.common.block.GaussVentBlock;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.block.blockentities.RefugeMenu;
import com.thebeyond.common.awareness.HiddenContentFilter;
import com.thebeyond.common.awareness.PlayerAwareness;
import com.thebeyond.common.registry.*;
import com.thebeyond.compat.jei.JeiCompatBridge;
import com.thebeyond.client.renderer.BlockCameraCapture;
import com.thebeyond.common.item.CameraBlockItem;
import com.thebeyond.common.camera.CameraGrade;
import com.thebeyond.common.item.components.Components;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.camera.SnapshotRequests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashSet;
import java.util.function.Consumer;
import net.minecraft.world.Containers;
import java.util.HashMap;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.UUID;

import static com.thebeyond.common.block.BellowBlock.*;

public class BeyondNetworking {

    // Unsynchronized: touched on server thread only.
    private static final int SNAPSHOT_REQ_WINDOW_TICKS = 20;
    private static final int SNAPSHOT_REQ_MAX_PER_WINDOW = 64;
    private static final Map<UUID, long[]> SNAPSHOT_REQ_WINDOW = new HashMap<>(); // uuid -> [windowStart, count]

    // (8 blocks)^2; shared reach gate for the projector and Refuge interactions.
    private static final double INTERACT_REACH_SQ = 64.0;

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
                GaussVentParticlePayload.TYPE,
                GaussVentParticlePayload.STREAM_CODEC,
                BeyondNetworking::handleGaussVentClient
        );

        registrar.playToClient(
                PlayerAwarenessSyncPayload.TYPE,
                PlayerAwarenessSyncPayload.STREAM_CODEC,
                BeyondNetworking::handleAwarenessSyncClient
        );

        registrar.playToClient(
                CaptureRequestPayload.TYPE,
                CaptureRequestPayload.STREAM_CODEC,
                BeyondNetworking::handleCaptureRequestClient
        );

        registrar.playToServer(
                SnapshotUploadPayload.TYPE,
                SnapshotUploadPayload.STREAM_CODEC,
                BeyondNetworking::handleSnapshotUpload
        );

        registrar.playToClient(
                BlockCameraRenderRequestPayload.TYPE,
                BlockCameraRenderRequestPayload.STREAM_CODEC,
                BeyondNetworking::handleBlockCameraRenderClient
        );

        registrar.playToServer(
                ProjectorSetModePayload.TYPE,
                ProjectorSetModePayload.STREAM_CODEC,
                BeyondNetworking::handleProjectorSetMode
        );

        registrar.playToServer(
                ProjectorCarouselPayload.TYPE,
                ProjectorCarouselPayload.STREAM_CODEC,
                BeyondNetworking::handleProjectorCarousel
        );

        registrar.playToServer(
                CameraShootPayload.TYPE,
                CameraShootPayload.STREAM_CODEC,
                BeyondNetworking::handleCameraShoot
        );

        registrar.playToClient(
                GatedStructuresSyncPayload.TYPE,
                GatedStructuresSyncPayload.STREAM_CODEC,
                BeyondNetworking::handleGatedStructuresClient
        );
    }

    private static void handleGaussVentClient(GaussVentParticlePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            BlockPos pos = payload.pos();
            boolean isBig = payload.isBig();
            BlockState block = context.player().level().getBlockState(pos);
            Level level = context.player().level();

            if (block.is(BeyondBlocks.GAUSS_VENT)) {
                Direction dir = Direction.UP;
                Vec3 visible = BeyondCompatHooks.visibleOnAnyLevel(level, pos);
                Vec3 origin = visible != null ? visible : Vec3.atCenterOf(pos);
                Vec3 wdir = BellowBlock.worldDir(context.player().level(), pos, dir);
                int strength = (isBig ? BellowBlock.effectiveReach(level, pos, Direction.UP, 40)*3 : Math.min(15, BellowBlock.effectiveReach(level, pos, Direction.UP, 15) * 3)) + level.random.nextInt(4) - 2;
                double reach = BellowBlock.reachBlocks(strength);

                Vec3 ref = Math.abs(wdir.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 u = wdir.cross(ref).normalize();
                Vec3 w = wdir.cross(u).normalize();

                BlockPos ahead = pos.relative(dir, Mth.floor(reach) + 1);
                boolean blocked = level.getBlockState(ahead).isSolidRender(level, ahead);
                double tipDist = blocked ? reach + 1.0 : reach + 0.5;
                int lifetime = isBig ? 40 : Math.max(2, (int) Math.round((tipDist - 0.6) / FLOW_SPEED));
                BellowJetOptions options = new BellowJetOptions(lifetime);
                Vec3 nozzle = origin.add(wdir.scale(0.6));
                RandomSource random = level.random;

                float offset = isBig ? 1.2f: 0.6f;

                for (int n = 0; n < STEM_COUNT; n++) {
                    double ang = random.nextDouble() * Math.PI * 2.0;
                    double pr = random.nextDouble() * STEM_RADIUS;
                    Vec3 p = nozzle.add(u.scale(Math.cos(ang) * pr)).add(w.scale(Math.sin(ang) * pr));
                    level.addParticle(options, p.x + (offset*level.random.nextFloat()-offset/2f), p.y, p.z + (offset*level.random.nextFloat()-offset/2f), wdir.x * FLOW_SPEED, wdir.y * FLOW_SPEED, wdir.z * FLOW_SPEED);
                }
                if (blocked) {
                    Vec3 face = origin.add(wdir.scale(reach + 0.5));
                    BellowJetOptions splash = new BellowJetOptions(lifetime);
                    for (int n = 0; n < SPLASH_COUNT; n++) {
                        double ang = random.nextDouble() * Math.PI * 2.0;
                        Vec3 radial = u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang)));
                        Vec3 sp = face.subtract(wdir.scale(0.1)).add(radial.scale(random.nextDouble() * 0.15));
                        Vec3 vel = radial.scale(SPLASH_OUT).subtract(wdir.scale(SPLASH_BACK));
                        level.addParticle(splash, sp.x, sp.y, sp.z, vel.x, vel.y, vel.z);
                    }
                }
            }
        });
    }

    /** ejectAt == null routes the snapshot back to the player. */
    private static void handleCameraShoot(CameraShootPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack camera = player.getItemInHand(payload.hand());
            if (!(camera.getItem() instanceof CameraBlockItem)
                    || !CameraBlockItem.hasFilm(camera)) {
                return;
            }
            CameraBlockItem.consumeFilm(camera);
            long requestId = SnapshotRequests.issue(player, null,
                    CameraGrade.get(camera));
            PacketDistributor.sendToPlayer(player, new CaptureRequestPayload(requestId));
            player.getCooldowns().addCooldown(camera.getItem(), 10);
            player.serverLevel().playSound(null, player.blockPosition(),
                    SoundEvents.UI_BUTTON_CLICK.value(),
                    SoundSource.PLAYERS, 0.7f, 1.2f);
        });
    }

    private static ProjectorBlockEntity projectorInReach(IPayloadContext context, BlockPos pos) {
        ServerPlayer player = (ServerPlayer) context.player();
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > INTERACT_REACH_SQ) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ProjectorBlockEntity be ? be : null;
    }

    private static void withProjector(IPayloadContext context, BlockPos pos, Consumer<ProjectorBlockEntity> action) {
        context.enqueueWork(() -> {
            ProjectorBlockEntity be = projectorInReach(context, pos);
            if (be != null) {
                action.accept(be);
            }
        });
    }

    private static void handleProjectorSetMode(ProjectorSetModePayload payload, IPayloadContext context) {
        withProjector(context, payload.pos(), be -> be.setMode(payload.mode()));
    }

    private static void handleProjectorCarousel(ProjectorCarouselPayload payload, IPayloadContext context) {
        withProjector(context, payload.pos(), be -> be.stepCarousel(payload.delta()));
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

            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > INTERACT_REACH_SQ) {
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RefugeBlockEntity refuge) {
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
                // copy first: revoke() mutates the backing set we'd be iterating
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

    private static void handleCaptureRequestClient(CaptureRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                BlockCameraCapture.requestSelf(payload.requestId()));
    }

    private static void handleSnapshotUpload(SnapshotUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!allowSnapshotRequest(player)) {
                return;
            }
            SnapshotRequests.Claim claim =
                    SnapshotRequests.claim(payload.requestId(), player);
            if (!claim.valid()) {
                return;
            }
            int w = payload.width(), h = payload.height();
            if (w <= 0 || h <= 0 || w > 256 || h > 256 || payload.rgb().length != w * h * 3) {
                return;
            }
            ItemStack snapshot = new ItemStack(BeyondItems.SNAPSHOT.get());
            snapshot.set(BeyondComponents.SNAPSHOT_PIXELS.get(),
                    new Components.SnapshotPixelsComponent(w, h, payload.rgb()));
            snapshot.set(BeyondComponents.SNAPSHOT_GRADE.get(),
                    claim.gradeId() != null ? claim.gradeId() : Grades.SEPIA);
            if (claim.ejectAt() == null) {
                ItemHandlerHelper.giveItemToPlayer(player, snapshot);
            } else {
                BlockPos at = claim.ejectAt();
                Containers.dropItemStack(player.serverLevel(), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, snapshot);
            }
        });
    }

    private static void handleBlockCameraRenderClient(BlockCameraRenderRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                BlockCameraCapture.request(payload.requestId(), payload.eye(), payload.forward()));
    }

    /** Server thread only: mutates the unsynchronized window map. */
    private static boolean allowSnapshotRequest(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long[] w = SNAPSHOT_REQ_WINDOW.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (now - w[0] >= SNAPSHOT_REQ_WINDOW_TICKS) {
            w[0] = now;
            w[1] = 0;
        }
        if (w[1] >= SNAPSHOT_REQ_MAX_PER_WINDOW) {
            return false;
        }
        w[1]++;
        return true;
    }

}
