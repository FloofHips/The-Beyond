package com.thebeyond.compat.create;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.thebeyond.common.block.MemorFaucetBlock;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.registry.BeyondCriteriaTriggers;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Full faucet logic at {@link MovementContext#position}: detection, item consumption,
 *  virtual age progression (stored in {@code context.data}), nomad spawn at age 1 and
 *  aurora/birthday at max. */
public class MemorFaucetMovementBehaviour implements MovementBehaviour {
    private static final int CHECK_INTERVAL = 50;
    private static final int DETECTION_RANGE = 5;
    private static final int NOMAD_RANGE = 32;
    private static final String K_TICK = "the_beyond$tick";
    private static final String K_AGE = "the_beyond$age";
    private static final String K_AGE_INIT = "the_beyond$ageInit";

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide) return;
        if (!(context.world instanceof ServerLevel server)) return;
        Vec3 p = context.position;
        if (p == null) return;

        if (!context.data.getBoolean(K_AGE_INIT)) {
            int initial = context.state.hasProperty(MemorFaucetBlock.AGE) ? context.state.getValue(MemorFaucetBlock.AGE) : 0;
            context.data.putInt(K_AGE, initial);
            context.data.putBoolean(K_AGE_INIT, true);
        }
        int age = context.data.getInt(K_AGE);
        if (age == MemorFaucetBlock.MAX_AGE) return;

        int tick = context.data.getInt(K_TICK) + 1;
        context.data.putInt(K_TICK, tick);

        if (tick % CHECK_INTERVAL == 0) {
            checkActivation(server, p);
            consumeNearby(server, p, context);
        }

        if (tick % (CHECK_INTERVAL + 5) == 0 && context.data.getInt(K_AGE) == 0) {
            routeNomads(server, p);
        }
    }

    private void checkActivation(ServerLevel server, Vec3 p) {
        AABB box = AABB.ofSize(p, DETECTION_RANGE * 2, DETECTION_RANGE * 2, DETECTION_RANGE * 2);
        for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box)) {
            boolean active = e instanceof AbyssalNomadEntity
                    || (e instanceof Player pl && (pl.getMainHandItem().is(BeyondTags.REMEMBRANCES)
                    || pl.getOffhandItem().is(BeyondTags.REMEMBRANCES)));
            if (active) {
                server.sendParticles(ParticleTypes.ENCHANT, p.x, p.y + 1.0, p.z, 6, 0.5, 0.5, 0.5, 0.1);
                return;
            }
        }
    }

    private void consumeNearby(ServerLevel server, Vec3 p, MovementContext context) {
        AABB itemBox = new AABB(p.x - 2, p.y - 4.5, p.z - 2, p.x + 2, p.y - 0.5, p.z + 2);
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class, itemBox);
        for (ItemEntity ie : items) {
            ItemStack stack = ie.getItem();
            if (!stack.is(BeyondTags.REMEMBRANCES)) continue;
            if (stack.getCount() > 1) {
                stack.shrink(1);
                ie.setItem(stack.copy());
            } else {
                ie.discard();
            }
            BlockPos bp = BlockPos.containing(p);
            server.playSound(null, bp, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1, 1);
            server.playSound(null, bp, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.BLOCKS, 0.5F, 1F);
            server.sendParticles(ParticleTypes.POOF, ie.getX(), ie.getY(), ie.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
            server.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), p.x, p.y - 0.5, p.z, 1, 0, 0, 0, 0);
            advanceAge(server, p, context);
            return;
        }
    }

    private void advanceAge(ServerLevel server, Vec3 p, MovementContext context) {
        int age = context.data.getInt(K_AGE);
        if (age >= MemorFaucetBlock.MAX_AGE) return;
        int newAge = age + 1;
        context.data.putInt(K_AGE, newAge);
        if (newAge == 1) {
            spawnNomads(server, p, context);
            affectNomads(server, p, (byte) 2);
        } else if (newAge >= MemorFaucetBlock.MAX_AGE) {
            affectNomads(server, p, (byte) 4);
        } else {
            affectNomads(server, p, (byte) 3);
        }
    }

    private void spawnNomads(ServerLevel server, Vec3 p, MovementContext context) {
        AABB box = AABB.ofSize(p, NOMAD_RANGE * 2, NOMAD_RANGE * 2, NOMAD_RANGE * 2);
        List<AbyssalNomadEntity> existing = server.getEntitiesOfClass(AbyssalNomadEntity.class, box);
        int target = Math.max((2 + server.random.nextInt(10)) - existing.size(), 0);
        if (target == 0) return;

        BlockPos base = BlockPos.containing(p);
        for (int i = 0; i < target; i++) {
            BlockPos ground = findGroundNear(server, base, 4, 8);
            if (ground == null) continue;
            AbyssalNomadEntity nomad = new AbyssalNomadEntity(BeyondEntityTypes.ABYSSAL_NOMAD.get(), server);
            nomad.setPos(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
            server.addFreshEntity(nomad);
        }
    }

    private static BlockPos findGroundNear(ServerLevel server, BlockPos center, int hRadius, int vDepth) {
        int dx = server.random.nextInt(hRadius * 2 + 1) - hRadius;
        int dz = server.random.nextInt(hRadius * 2 + 1) - hRadius;
        BlockPos cursor = center.offset(dx, 1, dz);
        for (int y = 0; y <= vDepth; y++) {
            BlockPos bp = cursor.below(y);
            if (server.getBlockState(bp).isFaceSturdy(server, bp, Direction.UP)) return bp;
        }
        return null;
    }

    private void routeNomads(ServerLevel server, Vec3 p) {
        AABB box = AABB.ofSize(p, 40, 40, 40);
        List<AbyssalNomadEntity> nomads = server.getEntitiesOfClass(AbyssalNomadEntity.class, box);
        BlockPos approx = BlockPos.containing(p);
        AbyssalNomadEntity nearest = null;
        double bestSq = 100.0; // nearest-within-10-blocks drop window
        for (AbyssalNomadEntity nomad : nomads) {
            Vec3 newPos = p.subtract(nomad.position()).normalize().scale(15);
            nomad.prayerSite = approx.offset((int) newPos.x, 0, (int) newPos.z);
            double d = nomad.position().distanceToSqr(p);
            if (d < bestSq) { bestSq = d; nearest = nomad; }
        }
        if (nearest != null) dropRemembranceFromNomad(server, nearest, p);
    }

    private void dropRemembranceFromNomad(ServerLevel server, AbyssalNomadEntity nomad, Vec3 targetPos) {
        ItemStack item = new ItemStack(BeyondItems.REMEMBRANCE_CLOTH.get());
        Vec3 startPos = nomad.position().add(0, nomad.getEyeHeight() + 1, 0);
        Vec3 endPos = targetPos.add(0, -0.5, 0);
        nomad.level().broadcastEntityEvent(nomad, (byte) 69);

        ItemEntity itemEntity = new ItemEntity(server, startPos.x, startPos.y, startPos.z, item);
        double dx = endPos.x - startPos.x;
        double dy = endPos.y - startPos.y;
        double dz = endPos.z - startPos.z;
        itemEntity.setNeverPickUp();
        itemEntity.setDeltaMovement(dx * 0.1, Math.max(0.2, dy * 0.05 + 0.2), dz * 0.1);
        server.addFreshEntity(itemEntity);
    }

    private void affectNomads(ServerLevel server, Vec3 p, byte b) {
        AABB box = AABB.ofSize(p, NOMAD_RANGE * 2, NOMAD_RANGE * 2, NOMAD_RANGE * 2);
        List<AbyssalNomadEntity> nomads = server.getEntitiesOfClass(AbyssalNomadEntity.class, box);
        BlockPos approx = BlockPos.containing(p);

        if (b == 2) {
            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = approx;
                nomad.sitDownCounter = 60 + server.random.nextInt(20);
            }
        } else if (b == 3) {
            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = approx;
                server.broadcastEntityEvent(nomad, (byte) 69);
            }
        } else if (b == 4) {
            server.sendParticles(ColorUtils.auroraOptions, p.x, p.y - 0.5, p.z, 5, 0.1, 0.1, 0.1, 0.05);
            server.playSound(null, approx, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.BLOCKS, 1, 1);
            for (AbyssalNomadEntity nomad : nomads) {
                nomad.lookAt = approx;
                server.playSound(null, nomad.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.5F, 0.8F);
                server.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(),
                        nomad.position().x, nomad.position().y + nomad.getEyeHeight() + 0.1, nomad.position().z, 1, 0, 0, 0, 0);
                nomad.dropCounter = 60 + server.random.nextInt(20);
            }
            Player nearestPlayer = server.getNearestPlayer(p.x, p.y, p.z, 16, false);
            if (nearestPlayer instanceof ServerPlayer sp) {
                BeyondCriteriaTriggers.FOUNTAIN_OFFERING.get().trigger(sp);
            }
        }
    }
}
