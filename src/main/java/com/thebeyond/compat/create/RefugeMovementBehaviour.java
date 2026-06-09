package com.thebeyond.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.util.RefugeChunkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/** Protection follows the contraption: re-applies {@link RefugeChunkData#addRefuge} to a
 *  9-chunk square around {@link MovementContext#position}, removing from the prior area
 *  when crossing a chunk boundary or mode change. */
public class RefugeMovementBehaviour implements MovementBehaviour {
    private static final int PROTECTION_RADIUS = 9;
    private static final int CHUNK_RADIUS = PROTECTION_RADIUS / 2;
    private static final int CYCLE = 5;
    private static final String K_TICK = "the_beyond$tick";
    private static final String K_LAST_CX = "the_beyond$lastCx";
    private static final String K_LAST_CZ = "the_beyond$lastCz";
    private static final String K_LAST_MODE = "the_beyond$lastMode";
    private static final String K_APPLIED = "the_beyond$applied";

    @Override
    public void startMoving(MovementContext context) {
        applyIfNeeded(context);
    }

    @Override
    public void stopMoving(MovementContext context) {
        // Final transfer to the disassembly position so the reconstructed BlockEntity
        // (same 9-chunk square via makeChunks) doesn't drop protection until re-toggled.
        if (!(context.world instanceof ServerLevel server)) return;
        Vec3 p = context.position;
        if (p == null) return;
        byte mode = readMode(context);
        if (mode < 0 || mode > 3) { clearApplied(context); return; }

        BlockPos bp = BlockPos.containing(p);
        int cx = bp.getX() >> 4;
        int cz = bp.getZ() >> 4;
        if (context.data.getBoolean(K_APPLIED)) {
            int lastCx = context.data.getInt(K_LAST_CX);
            int lastCz = context.data.getInt(K_LAST_CZ);
            byte lastMode = context.data.contains(K_LAST_MODE) ? context.data.getByte(K_LAST_MODE) : mode;
            if (cx != lastCx || cz != lastCz || mode != lastMode) {
                applyToArea(server, lastCx, lastCz, lastMode, false);
                applyToArea(server, cx, cz, mode, true);
            }
        } else {
            applyToArea(server, cx, cz, mode, true);
        }
        context.data.putBoolean(K_APPLIED, false);
    }

    @Override
    public void tick(MovementContext context) {
        int tick = context.data.getInt(K_TICK) + 1;
        context.data.putInt(K_TICK, tick);
        if (tick != 1 && tick % CYCLE != 0) return;
        applyIfNeeded(context);
    }

    private void applyIfNeeded(MovementContext context) {
        if (context.world.isClientSide) return;
        if (!(context.world instanceof ServerLevel server)) return;
        Vec3 p = context.position;
        if (p == null) return;
        byte mode = readMode(context);
        if (mode < 0 || mode > 3) {
            clearApplied(context);
            return;
        }

        BlockPos bp = BlockPos.containing(p);
        int cx = bp.getX() >> 4;
        int cz = bp.getZ() >> 4;
        boolean wasApplied = context.data.getBoolean(K_APPLIED);
        int lastCx = context.data.getInt(K_LAST_CX);
        int lastCz = context.data.getInt(K_LAST_CZ);
        byte lastMode = context.data.contains(K_LAST_MODE) ? context.data.getByte(K_LAST_MODE) : -1;

        if (wasApplied && (cx != lastCx || cz != lastCz || mode != lastMode)) {
            applyToArea(server, lastCx, lastCz, lastMode, false);
            wasApplied = false;
        }

        if (!wasApplied) {
            applyToArea(server, cx, cz, mode, true);
            context.data.putBoolean(K_APPLIED, true);
            context.data.putInt(K_LAST_CX, cx);
            context.data.putInt(K_LAST_CZ, cz);
            context.data.putByte(K_LAST_MODE, mode);
        }
    }

    private void clearApplied(MovementContext context) {
        if (!context.data.getBoolean(K_APPLIED)) return;
        if (!(context.world instanceof ServerLevel server)) return;
        byte lastMode = context.data.contains(K_LAST_MODE) ? context.data.getByte(K_LAST_MODE) : -1;
        if (lastMode >= 0 && lastMode <= 3) {
            applyToArea(server, context.data.getInt(K_LAST_CX), context.data.getInt(K_LAST_CZ), lastMode, false);
        }
        context.data.putBoolean(K_APPLIED, false);
    }

    private byte readMode(MovementContext context) {
        if (context.blockEntityData != null && context.blockEntityData.contains("CurrentMode")) {
            return context.blockEntityData.getByte("CurrentMode");
        }
        return -1;
    }

    private void applyToArea(ServerLevel level, int centerCx, int centerCz, byte mode, boolean add) {
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerCx + dx, centerCz + dz, false);
                if (chunk == null) continue;
                RefugeChunkData data = chunk.getData(BeyondAttachments.REFUGE_DATA);
                if (add) data.addRefuge(mode); else data.removeRefuge(mode);
            }
        }
    }
}
