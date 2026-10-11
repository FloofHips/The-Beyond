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
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Vector3f;

public class PearlChimesBlock extends Block {

    protected static final VoxelShape AABB = Block.box((double)2.0F, (double)2.0F, (double)2.0F, (double)14.0F, (double)16.0F, (double)14.0F);

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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AABB;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).isFaceSturdy(level, pos.above(), Direction.DOWN) && super.canSurvive(state, level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) && level.isRaining()) {
            level.playLocalSound(pos, BeyondSoundEvents.PEARL_CHIME.get(), SoundSource.BLOCKS, 1,0.5f + random.nextFloat(), false);
            level.setBlock(pos, state.setValue(LIT, true), 3);
        }
        if (state.getValue(LIT) && !level.isRaining()) level.setBlock(pos, state.setValue(LIT, false),3);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean raining = level.isRaining();

        if (!state.getValue(LIT) && random.nextBoolean()) {
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
