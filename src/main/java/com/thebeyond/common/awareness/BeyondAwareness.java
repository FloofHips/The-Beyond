package com.thebeyond.common.awareness;

import com.thebeyond.BeyondConfig;
import com.thebeyond.common.network.PlayerAwarenessSyncPayload;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

/** What does a player know? Per-player or shared-world depending on config.
 *  With the gate off, everything counts as known. */
public final class BeyondAwareness {

    private BeyondAwareness() {}

    // Null-safe so early init and tests don't blow up.
    public static boolean gateEnabled() {
        if (BeyondConfig.HIDE_UNDISCOVERED_CONTENT == null) return false;
        try { return BeyondConfig.HIDE_UNDISCOVERED_CONTENT.get(); }
        catch (IllegalStateException notLoadedYet) { return false; }
    }

    public static AwarenessMode mode() {
        if (BeyondConfig.AWARENESS_MODE == null) return AwarenessMode.PER_PLAYER;
        try { return BeyondConfig.AWARENESS_MODE.get(); }
        catch (IllegalStateException notLoadedYet) { return AwarenessMode.PER_PLAYER; }
    }

    /** Everything this player knows right now, for filtering a batch in one shot. */
    public static Set<ResourceLocation> knownSnapshot(Player viewer) {
        if (viewer == null) return Set.of();
        if (mode() == AwarenessMode.SHARED_WORLD && viewer.level() instanceof ServerLevel sl) {
            return WorldAwareness.get(sl).all();
        }
        return viewer.getData(BeyondAttachments.PLAYER_AWARENESS).all();
    }

    /** Does this player know the key? Always true when the gate is off. */
    public static boolean isKnown(Player player, ResourceLocation key) {
        if (!gateEnabled()) return true;
        if (player == null) return false;

        if (mode() == AwarenessMode.SHARED_WORLD && player.level() instanceof ServerLevel sl) {
            return WorldAwareness.get(sl).isKnown(key);
        }
        PlayerAwareness pk = player.getData(BeyondAttachments.PLAYER_AWARENESS);
        return pk.isKnown(key);
    }

    /** Grant a bunch of keys at once, then send a single sync for whatever actually changed. */
    public static boolean grantAll(ServerPlayer player, Set<ResourceLocation> keys) {
        if (!gateEnabled() || keys.isEmpty()) return false;
        java.util.Set<ResourceLocation> added = new java.util.HashSet<>();
        if (mode() == AwarenessMode.SHARED_WORLD) {
            WorldAwareness wk = WorldAwareness.get(player.serverLevel());
            for (ResourceLocation key : keys) if (wk.grant(key)) added.add(key);
            if (!added.isEmpty()) PacketDistributor.sendToAllPlayers(new PlayerAwarenessSyncPayload(added, false));
        } else {
            PlayerAwareness pk = player.getData(BeyondAttachments.PLAYER_AWARENESS);
            for (ResourceLocation key : keys) if (pk.grant(key)) added.add(key);
            if (!added.isEmpty()) PacketDistributor.sendToPlayer(player, new PlayerAwarenessSyncPayload(added, false));
        }
        return !added.isEmpty();
    }

    /** Grant one key. Does nothing with the gate off, otherwise syncs the change to clients. */
    public static boolean grant(ServerPlayer player, ResourceLocation key) {
        if (!gateEnabled()) return false;

        boolean changed;
        if (mode() == AwarenessMode.SHARED_WORLD) {
            changed = WorldAwareness.get(player.serverLevel()).grant(key);
            if (changed) {
                // World unlock, so tell everyone.
                PacketDistributor.sendToAllPlayers(new PlayerAwarenessSyncPayload(Set.of(key), false));
            }
        } else {
            PlayerAwareness pk = player.getData(BeyondAttachments.PLAYER_AWARENESS);
            changed = pk.grant(key);
            if (changed) {
                PacketDistributor.sendToPlayer(player, new PlayerAwarenessSyncPayload(Set.of(key), false));
            }
        }
        return changed;
    }

    /** Admin/debug only. Sends a full snapshot since the sync can only add, never remove. */
    public static boolean revoke(ServerPlayer player, ResourceLocation key) {
        if (!gateEnabled()) return false;

        boolean changed;
        if (mode() == AwarenessMode.SHARED_WORLD) {
            WorldAwareness wk = WorldAwareness.get(player.serverLevel());
            changed = wk.revoke(key);
            if (changed) {
                PacketDistributor.sendToAllPlayers(
                        new PlayerAwarenessSyncPayload(Set.copyOf(wk.all()), true));
            }
        } else {
            PlayerAwareness pk = player.getData(BeyondAttachments.PLAYER_AWARENESS);
            changed = pk.revoke(key);
            if (changed) {
                PacketDistributor.sendToPlayer(player,
                        new PlayerAwarenessSyncPayload(Set.copyOf(pk.all()), true));
            }
        }
        return changed;
    }
}
