package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.MemorFaucetBlock;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class MemorFaucetBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 100;
    private static final int DETECTION_RANGE = 5;
    private static final int NOMAD_RANGE = 32;
    private int tickCounter = 0;

    public MemorFaucetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public MemorFaucetBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.MEMOR_FAUCET.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MemorFaucetBlockEntity be) {
        be.tickCounter++;

        if (be.tickCounter >= CHECK_INTERVAL) {
            be.tickCounter = 0;
            be.checkForActivation(level, pos);
            be.consumeItems(level, pos);
        }
    }

    private void checkForActivation(Level level, BlockPos pos) {
        AABB detectionBox = new AABB(pos).inflate(DETECTION_RANGE);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, detectionBox);

        for (LivingEntity entity : entities) {
            if (shouldActivate(entity)) {
                activateFaucet(level, pos);
                break;
            }
        }
    }

    private boolean shouldActivate(LivingEntity entity) {
        if (entity instanceof AbyssalNomadEntity) {
            return true;
        }

        if (entity instanceof Player player) {
            return player.getMainHandItem().is(BeyondTags.REMEMBRANCES) ||
                    player.getOffhandItem().is(BeyondTags.REMEMBRANCES);
        }

        return false;
    }

    private void activateFaucet(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private void consumeItems(Level level, BlockPos pos) {
        AABB itemBox = new AABB(pos).inflate(3.0);
        List<net.minecraft.world.entity.item.ItemEntity> items =
                level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, itemBox);

        if (!items.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity item = items.get(0);

            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1, 1);
            level.playSound(null, pos, SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.BLOCKS, 0.5F, 1.0F);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, item.getX(), item.getY(), item.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), item.position().x, item.position().y + 0.1, item.position().z, 1, 0, 0, 0, 0);
            }

            item.discard();
            increaseAge(level, pos);
        }
    }

    private void increaseAge(Level level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        int currentAge = currentState.getValue(MemorFaucetBlock.AGE);

        if (currentAge < 5) {
            int newAge = currentAge + 1;
            level.setBlock(pos, currentState.setValue(MemorFaucetBlock.AGE, newAge), 3);

            if (newAge >= MemorFaucetBlock.MAX_AGE) {
                affectNomads(level, pos);
            }
        }
    }

    private void affectNomads(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(NOMAD_RANGE);
        List<AbyssalNomadEntity> nomads = level.getEntitiesOfClass(AbyssalNomadEntity.class, box);

        level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.BLOCKS, 1, 1);

        for (AbyssalNomadEntity nomad : nomads) {
            nomad.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0));
            level.playSound(null, nomad.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.5F, 0.8F);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), nomad.position().x, nomad.position().y + nomad.getEyeHeight() + 0.1, nomad.position().z, 1, 0, 0, 0, 0);
            }
        }

        level.setBlock(pos, level.getBlockState(pos).setValue(MemorFaucetBlock.AGE, 0), 3);
    }

    public static void onBlockEntityRegister(BlockEntityType<MemorFaucetBlockEntity> type) {

    }
}