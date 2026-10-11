package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BleedingThornsBlock extends ThornsBlock{
    public BleedingThornsBlock(Properties p_51707_) {
        super(p_51707_);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (player.hurt(level.damageSources().cactus(), 1.0F) && level instanceof ServerLevel serverLevel) serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.getX()+0.5f,pos.getY()+0.5f,pos.getZ()+0.5f,3,0.3,0.3,0.3,0.01);
        super.attack(state, level, pos, player);
    }

    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.hurt(level.damageSources().cactus(), 1.0F) && level instanceof ServerLevel serverLevel) serverLevel.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.getX()+0.5f,pos.getY()+0.5f,pos.getZ()+0.5f,3,0.3,0.3,0.3,0.01);
    }
}
