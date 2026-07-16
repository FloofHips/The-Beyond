package com.thebeyond.common.block;

import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

public class MoltenMetalBlock extends Block {
    public MoltenMetalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.random.nextFloat() > 0.8f) level.setBlock(pos, BeyondBlocks.BRITTLE_METAL.get().defaultBlockState().setValue(BrittleMetalBlock.POWERED, true),3);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockPos blockpos = pos.above();
        if (level.getBlockState(blockpos).isAir() && !level.getBlockState(blockpos).isSolidRender(level, blockpos)) {
            double d0 = (double)pos.getX() + random.nextDouble();
            double d1 = (double)pos.getY() + (double)1.0F;
            double d2 = (double)pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, d0, d1, d2, (double)0.0F, (double)0.1F, (double)0.0F);

            if (random.nextInt(100) == 0) {
                level.addParticle(ParticleTypes.LAVA, d0, d1, d2, (double)0.0F, (double)0.0F, (double)0.0F);
                level.playLocalSound(d0, d1, d2, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }

            if (random.nextInt(200) == 0) {
                level.playLocalSound((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully() && entity instanceof LivingEntity) {
            entity.hurt(level.damageSources().hotFloor(), 1.0F);
        }

        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof ServerLevel serverLevel) {
            if (neighborState.is(BlockTags.ICE) || neighborState.is(Blocks.WATER)) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5f, pos.getY() + 0.7f, pos.getZ() + 0.5f, 10, 0.5f, 1, 0.5f, 0.01F);
                serverLevel.playSound(null, pos.getX(), pos.getY(), pos.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1, 1);
                level.removeBlock(pos, false);

                ItemEntity prismuth = new ItemEntity(serverLevel, pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5f, new ItemStack(BeyondItems.PRISMUTH.get(), ((ServerLevel) level).random.nextInt(1,3)));
                ItemEntity metalSheet = new ItemEntity(serverLevel, pos.getX() + 0.5f, pos.getY() + 1, pos.getZ() + 0.5f, new ItemStack(BeyondItems.BRITTLE_METAL_SHEET.get(), ((ServerLevel) level).random.nextInt(1,4)));

                level.addFreshEntity(prismuth);
                if (((ServerLevel) level).random.nextBoolean()) level.addFreshEntity(metalSheet);
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box((double)0.0F, (double)0.0F, (double)0.0F, (double)16.0F, (double)8.0F, (double)16.0F);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }
}
