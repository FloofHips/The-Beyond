package com.thebeyond.common.block;

import com.thebeyond.client.particle.CloudColorTransitionOptions;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.util.ColorUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

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
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new CloudColorTransitionOptions(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(0.1f, 0.0f, 0.2f),
                    2.5f
            ), pos.getX()+0.3f,pos.getY()+0.3f,pos.getZ()+0.3f,20,1,1,1,0.01);
            serverLevel.sendParticles(new SmokeColorTransitionOptions(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Vector3f(0.1f, 0.0f, 0.2f),
                    2.5f
            ), pos.getX()+0.3f,pos.getY()+0.3f,pos.getZ()+0.3f,15,1.1,1.1,1.1,0.02);
        }
        super.tick(state, level, pos, random);
    }
}
