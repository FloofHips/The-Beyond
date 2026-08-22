package com.thebeyond.common.block;

import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class PearlChimesBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public PearlChimesBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 2;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) && level.isRaining()) level.setBlock(pos, state.setValue(LIT, true) ,3);
        if (state.getValue(LIT) && !level.isRaining()) level.setBlock(pos, state.setValue(LIT, false),3);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean raining = level.isRaining();

        if (state.getValue(LIT) ? random.nextFloat() > 0.2 : random.nextFloat() > 0.6) {
            level.playLocalSound(pos.getX(), pos.above().getY(), pos.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2, random.nextFloat() + (raining ? 1 : 0), false);
            spawnParticle(level, pos, random);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, true) ,3);
    }

    private static void spawnParticle(Level level, BlockPos pos, RandomSource random) {
        if (!level.isDay()) return;

        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        int maxDistance = Math.max(1, (skyLight/2));

        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            for (int distance = 1; distance <= maxDistance; distance++) {
                BlockPos checkPos = pos.relative(direction, distance);

                if (level.getBlockState(checkPos).isSolid()) {
                    double x = checkPos.getX() + 0.5 - direction.getStepX() * 0.5;
                    double y = checkPos.getY() + 0.5 - direction.getStepY() * 0.5;
                    double z = checkPos.getZ() + 0.5 - direction.getStepZ() * 0.5;

                    level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
                    break;
                }
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
