package com.thebeyond.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.thebeyond.common.entity.EnadrakeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Accepts feeding from nearby enadrakes (writes to {@code blockEntityData.item}) and
 *  drains the held item over time. Stored enadrake NBT preserved; rehydrates on stop. */
public class EnadrakeHutMovementBehaviour implements MovementBehaviour {
    private static final double FEED_CHANCE = 0.05 * 0.5;
    private static final int CYCLE = 20;
    private static final int FEED_RADIUS = 3;
    private static final String K_TICK = "the_beyond$tick";

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) return;
        if (!(context.world instanceof ServerLevel server)) return;
        if (context.blockEntityData == null) return;
        Vec3 p = context.position;
        if (p == null) return;

        int tick = context.data.getInt(K_TICK) + 1;
        context.data.putInt(K_TICK, tick);
        if (tick % CYCLE != 0) return;

        CompoundTag be = context.blockEntityData;
        boolean empty = !be.contains("item", 10);
        if (empty) {
            tryFeedFromNearbyEnadrake(server, p, be);
            return;
        }

        // Drain the held item slowly to mirror "expansion uses the item".
        CompoundTag item = be.getCompound("item");
        int count = item.contains("count") ? item.getInt("count") : 1;
        if (count <= 0) return;
        if (server.random.nextDouble() < FEED_CHANCE) {
            count -= 1;
            if (count <= 0) {
                be.remove("item");
            } else {
                item.putInt("count", count);
                be.put("item", item);
            }
        }
    }

    private void tryFeedFromNearbyEnadrake(ServerLevel server, Vec3 p, CompoundTag be) {
        AABB box = AABB.ofSize(p, FEED_RADIUS * 2, FEED_RADIUS * 2, FEED_RADIUS * 2);
        List<EnadrakeEntity> nearby = server.getEntitiesOfClass(EnadrakeEntity.class, box);
        for (EnadrakeEntity e : nearby) {
            ItemStack held = e.getMainHandItem();
            if (held.isEmpty()) continue;
            ItemStack one = held.copyWithCount(1);
            held.shrink(1);
            e.setItemInHand(InteractionHand.MAIN_HAND, held);
            Tag saved = one.save(server.registryAccess());
            if (saved instanceof CompoundTag ct) {
                be.put("item", ct);
                server.playSound(null, BlockPos.containing(p), SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
                server.sendParticles(ParticleTypes.DUST_PLUME, p.x, p.y + 1.2, p.z, 7, 0, 0, 0, 0);
            }
            return;
        }
    }
}
