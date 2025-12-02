package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

public class GusterBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public GusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, true));
    }
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (state.getValue(POWERED) && entity.getKnownMovement().length() > 0.1F)
            level.playSound(entity, pos, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 1, 1);

        if (level instanceof ServerLevel serverLevel && state.getValue(POWERED) && entity.getKnownMovement().length() > 0.1F) {
            level.setBlockAndUpdate(pos, state.setValue(POWERED, false));
            level.scheduleTick(pos, this, 200);

            Vec3 lookAngle = entity.getLookAngle().normalize();
            float vertical = (float) Math.max(Math.abs(lookAngle.y), 0.5f);
            Vec3 pushVector = new Vec3(lookAngle.x, vertical, lookAngle.z).multiply(5.5, 4.5,5.5);

            entity.setDeltaMovement(entity.getDeltaMovement().add(pushVector));
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, pos.getX()+0.5, pos.getY()+1, pos.getZ()+0.5, 3, 0.1,0.1,0.1,1);
            serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, pos.getX()+0.5, pos.getY()+1, pos.getZ()+0.5, 3, 0.1,0.1,0.1,1);

            entity.hurtMarked = true;
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.setBlockAndUpdate(pos, state.setValue(POWERED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
