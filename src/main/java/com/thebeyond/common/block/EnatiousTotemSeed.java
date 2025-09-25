package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class EnatiousTotemSeed extends Block implements GameEventListener {
    private BlockPos listenerPos = BlockPos.ZERO;
    @Nullable
    private ServerLevel currentLevel;

    public EnatiousTotemSeed(Properties properties) {
        super(properties);
    }

    @Override
    public PositionSource getListenerSource() {
        return new BlockPositionSource(listenerPos);
    }

    @Override
    public int getListenerRadius() {
        return 16;
    }

    @Override
    public boolean handleGameEvent(ServerLevel serverLevel, Holder<GameEvent> holder, GameEvent.Context context, Vec3 vec3) {
        if (context.sourceEntity() instanceof Player player) {
            //if (cow.distanceToSqr(listenerPos.getX(), listenerPos.getY(), listenerPos.getZ()) <= getListenerRadius() * getListenerRadius()) {
                serverLevel.playSound(null, listenerPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
                spawnParticles(serverLevel, player.position(), true);
                spawnParticles(serverLevel, Vec3.atLowerCornerOf(listenerPos), false);
                player.push(0,1,0);
                player.hurtMarked = true;
                return true;
            //}
        }
        return false;
    }

    private void spawnParticles(ServerLevel level, Vec3 pos, boolean f) {
        if(f)
            for (int i = 0; i < 10; i++) {
                double x = pos.x + (level.random.nextDouble() - 0.5) * 2.0;
                double y = pos.y + level.random.nextDouble() * 2.0;
                double z = pos.z + (level.random.nextDouble() - 0.5) * 2.0;
                level.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0, 0, 0, 0.1);
            }
        else
            for (int i = 0; i < 5; i++) {
                double x = pos.x + (level.random.nextDouble() - 0.5) * 1.5;
                double y = pos.y + level.random.nextDouble() * 1.5;
                double z = pos.z + (level.random.nextDouble() - 0.5) * 1.5;
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0, 0, 0.05);
            }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            setListenerPosition(pos, serverLevel);
            registerListener(serverLevel);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            unregisterListener(serverLevel);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void setListenerPosition(BlockPos pos, ServerLevel level) {
        this.listenerPos = pos;
        this.currentLevel = level;
    }

    private void registerListener(ServerLevel level) {
        level.getChunk(listenerPos).getListenerRegistry(listenerPos.getY()/16).register(this);
    }

    private void unregisterListener(ServerLevel level) {
        level.getChunk(listenerPos).getListenerRegistry(listenerPos.getY()/16).unregister(this);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!pos.equals(listenerPos)) {
                unregisterListener(serverLevel);
                setListenerPosition(pos, serverLevel);
                registerListener(serverLevel);
            }
        }
    }
}