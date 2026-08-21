package com.thebeyond.common.block;

import com.thebeyond.util.ColorUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BlindingThornsBlock extends ThornsBlock{
    public BlindingThornsBlock(Properties p_51707_) {
        super(p_51707_);
    }
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel serverLevel) serverLevel.sendParticles(ColorUtils.blackOptions, pos.getX()+0.1f,pos.getY()+0.1f,pos.getZ()+0.1f,50,1,1,1,0.07);
        super.attack(state, level, pos, player);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level instanceof ServerLevel serverLevel) serverLevel.sendParticles(ColorUtils.blackOptions, pos.getX()+0.1f,pos.getY()+0.1f,pos.getZ()+0.1f,50,1,1,1,0.07);
        super.tick(state, level, pos, random);
    }
}
