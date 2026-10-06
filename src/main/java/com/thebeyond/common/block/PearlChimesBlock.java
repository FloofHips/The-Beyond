package com.thebeyond.common.block;

import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondParticleTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.util.RenderUtils;
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
import net.neoforged.neoforge.client.event.ScreenEvent;
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
            if (random.nextFloat() > 0.8) level.playLocalSound(pos.getX(), pos.above().getY(), pos.getZ(), BeyondSoundEvents.PEARL_CHIME.get(), SoundSource.BLOCKS, 1, random.nextFloat() + (raining ? 1 : 0.5f), false);
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
                    RenderUtils.spawnBlockFaceParticle(level, random, direction, checkPos);
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
