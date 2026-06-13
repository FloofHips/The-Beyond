package com.thebeyond.common.camera;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gates pixel uploads: the server accepts one only if the id was issued to that same player and hasn't expired.
 * {@code ejectAt == null} gives the finished item to the player, else ejects at the camera block.
 */
public final class SnapshotRequests {
    public record Claim(boolean valid, @Nullable BlockPos ejectAt, @Nullable ResourceLocation gradeId) {
        public static final Claim INVALID = new Claim(false, null, null);
    }

    private record Pending(UUID player, long deadline, @Nullable BlockPos ejectAt, ResourceLocation gradeId) {
    }

    private static final long TTL_TICKS = 100;
    private static final Map<Long, Pending> PENDING = new HashMap<>();
    private static long counter = 1;

    private SnapshotRequests() {
    }

    public static synchronized long issue(ServerPlayer fulfiller, @Nullable BlockPos ejectAt, ResourceLocation gradeId) {
        long now = fulfiller.serverLevel().getGameTime();
        PENDING.values().removeIf(p -> p.deadline < now);
        long id = counter++;
        PENDING.put(id, new Pending(fulfiller.getUUID(), now + TTL_TICKS, ejectAt, gradeId));
        return id;
    }

    public static synchronized Claim claim(long id, ServerPlayer player) {
        Pending p = PENDING.remove(id);
        if (p == null || !p.player.equals(player.getUUID()) || p.deadline < player.serverLevel().getGameTime()) {
            return Claim.INVALID;
        }
        return new Claim(true, p.ejectAt, p.gradeId);
    }
}
