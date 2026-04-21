package com.thebeyond.common.knowledge;

import com.thebeyond.BeyondConfig;
import com.thebeyond.common.network.PlayerKnowledgeSyncPayload;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

/**
 * Static API for knowledge queries and grants. Routes between {@link PlayerKnowledge}
 * (per-player modes) and {@link WorldKnowledge} (shared-world mode) based on config.
 * When the gate is off, {@link #isKnown} returns {@code true} for every key.
 */
public final class BeyondKnowledge {

    private BeyondKnowledge() {}

    // Config — null-safe for early class-init / test paths.
    public static boolean gateEnabled() {
        if (BeyondConfig.HIDE_UNDISCOVERED_CONTENT == null) return false;
        try { return BeyondConfig.HIDE_UNDISCOVERED_CONTENT.get(); }
        catch (IllegalStateException notLoadedYet) { return false; }
    }

    public static KnowledgeMode mode() {
        if (BeyondConfig.KNOWLEDGE_MODE == null) return KnowledgeMode.PER_PLAYER;
        try { return BeyondConfig.KNOWLEDGE_MODE.get(); }
        catch (IllegalStateException notLoadedYet) { return KnowledgeMode.PER_PLAYER; }
    }

    /** Gate off → {@code true}. Client reads from the attachment populated by {@link PlayerKnowledgeSyncPayload}. */
    public static boolean isKnown(Player player, ResourceLocation key) {
        if (!gateEnabled()) return true;
        if (player == null) return false;

        if (mode() == KnowledgeMode.SHARED_WORLD && player.level() instanceof ServerLevel sl) {
            return WorldKnowledge.get(sl).isKnown(key);
        }
        // PER_PLAYER or PER_PLAYER_WITH_IMPORT — consult the attachment.
        PlayerKnowledge pk = player.getData(BeyondAttachments.PLAYER_KNOWLEDGE);
        return pk.isKnown(key);
    }

    /**
     * Server-only. No-op when the gate is off so silent state doesn't accumulate: flipping
     * the feature on later would otherwise expose content the player never had to discover.
     * Successful changes are pushed to clients via {@link PlayerKnowledgeSyncPayload}.
     */
    public static boolean grant(ServerPlayer player, ResourceLocation key) {
        if (!gateEnabled()) return false;

        boolean changed;
        if (mode() == KnowledgeMode.SHARED_WORLD) {
            changed = WorldKnowledge.get(player.serverLevel()).grant(key);
            if (changed) {
                // World-scope unlock — every connected player's client needs the key.
                PacketDistributor.sendToAllPlayers(new PlayerKnowledgeSyncPayload(Set.of(key), false));
            }
        } else {
            PlayerKnowledge pk = player.getData(BeyondAttachments.PLAYER_KNOWLEDGE);
            changed = pk.grant(key);
            if (changed) {
                PacketDistributor.sendToPlayer(player, new PlayerKnowledgeSyncPayload(Set.of(key), false));
            }
        }
        return changed;
    }

    /** Admin/debug only; gameplay never revokes. Sends a full-replace snapshot because the
     *  delta payload can only union, not subtract. */
    public static boolean revoke(ServerPlayer player, ResourceLocation key) {
        if (!gateEnabled()) return false;

        boolean changed;
        if (mode() == KnowledgeMode.SHARED_WORLD) {
            WorldKnowledge wk = WorldKnowledge.get(player.serverLevel());
            changed = wk.revoke(key);
            if (changed) {
                PacketDistributor.sendToAllPlayers(
                        new PlayerKnowledgeSyncPayload(Set.copyOf(wk.all()), true));
            }
        } else {
            PlayerKnowledge pk = player.getData(BeyondAttachments.PLAYER_KNOWLEDGE);
            changed = pk.revoke(key);
            if (changed) {
                PacketDistributor.sendToPlayer(player,
                        new PlayerKnowledgeSyncPayload(Set.copyOf(pk.all()), true));
            }
        }
        return changed;
    }
}
