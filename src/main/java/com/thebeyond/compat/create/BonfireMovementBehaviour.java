package com.thebeyond.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.entity.LanternEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** When {@link BonfireBlock#LIT}, spawns/splits lanterns capped at 10 around
 *  {@link MovementContext#position}. Player attributed to nearest within 16 blocks. */
public class BonfireMovementBehaviour implements MovementBehaviour {
    private static final int CYCLE = 5;
    private static final int RADIUS = 20;
    private static final int CAP = 10;
    private static final String K_TICK = "the_beyond$tick";

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) return;
        if (!(context.world instanceof ServerLevel server)) return;
        if (!context.state.hasProperty(BonfireBlock.LIT) || !context.state.getValue(BonfireBlock.LIT)) return;
        Vec3 p = context.position;
        if (p == null) return;

        int tick = context.data.getInt(K_TICK) + 1;
        context.data.putInt(K_TICK, tick);
        if (tick % CYCLE != 0) return;

        Player nearest = server.getNearestPlayer(p.x, p.y, p.z, RADIUS, false);
        if (nearest == null) return;

        AABB box = AABB.ofSize(p, RADIUS * 2, RADIUS * 2, RADIUS * 2);
        List<LanternEntity> lanterns = server.getEntitiesOfClass(LanternEntity.class, box);
        int count = lanterns.size();

        if (count > 0) {
            LanternEntity rl = lanterns.get(server.random.nextInt(lanterns.size()));
            server.sendParticles(ParticleTypes.HEART, rl.getX(), rl.getY(), rl.getZ(), 3, 0.25, 0.25, 0.25, 0.015);
        }

        if (count < CAP && server.random.nextFloat() < 0.9f) {
            BlockPos spawn = BlockPos.containing(
                    p.x + (server.random.nextDouble() - 0.5) * 10,
                    p.y,
                    p.z + (server.random.nextDouble() - 0.5) * 10);
            LanternEntity.spawnSelf(server, spawn, nearest);
        }

        if (!lanterns.isEmpty() && server.random.nextFloat() < 0.4f) {
            LanternEntity rl = lanterns.get(server.random.nextInt(lanterns.size()));
            rl.split(server, nearest);
        }
    }
}
