package com.thebeyond.common.blocks;

import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class AuroraciteBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public AuroraciteBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        level.setBlockAndUpdate(pos, state.setValue(POWERED, false));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if(entity.getDeltaMovement().length() < 0.1f) return;

        if (!state.getValue(POWERED)) {
            level.setBlockAndUpdate(pos, state.setValue(POWERED, true));
            level.addParticle(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() + 1.01, pos.getZ() + 0.5, 0, 0.1,0);
            level.scheduleTick(pos, this, 20);
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }
}
