package com.thebeyond.common.blocks;

import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class AuroraciteBlock extends Block {

    public AuroraciteBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        //if (!level.isClientSide()) return;
        if(entity.getDeltaMovement().length() < 0.1f) return;

        level.addParticle(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5, 0, 0.1,0);

        super.stepOn(level, pos, state, entity);
    }
}
