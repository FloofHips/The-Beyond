package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.fluid.GellidVoidBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondTabs;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.util.TeleportUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class VoidFlameBlock extends BaseFireBlock {
    public static final MapCodec<VoidFlameBlock> CODEC = simpleCodec(VoidFlameBlock::new);

    public MapCodec<VoidFlameBlock> codec() {
        return CODEC;
    }

    public VoidFlameBlock(BlockBehaviour.Properties p_56653_) {
        super(p_56653_, 2.0F);
    }
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getState(context.getLevel(), context.getClickedPos());
    }
    public static BlockState getState(BlockGetter reader, BlockPos pos) {
        return BeyondBlocks.VOID_FLAME.get().defaultBlockState();
    }

    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        return this.canSurvive(state, level, currentPos) ? this.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {

        if (level.getBlockState(pos.below()).getBlock() instanceof GellidVoidBlock) return true;
        return canSurviveOnBlock(level.getBlockState(pos.below()));
    }

    public static boolean canSurviveOnBlock(BlockState state) {
        return state.is(BeyondTags.VOID_FLAME_BASE_BLOCKS);
    }

    protected boolean canBurn(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }

    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0) {
            level.playLocalSound((double)pos.getX() + (double)0.5F, (double)pos.getY() + (double)0.5F, (double)pos.getZ() + (double)0.5F, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
        }

        float i = level.isRaining() ? 0.3f : 0.1f;

        BlockPos blockpos = pos.below();
        BlockState blockstate = level.getBlockState(blockpos);
        if (!this.canBurn(blockstate) && !blockstate.isFaceSturdy(level, blockpos, Direction.UP)) {
            if (this.canBurn(level.getBlockState(pos.west()))) {
                for(int j = 0; j < 2; ++j) {
                    double d3 = (double)pos.getX() + random.nextDouble() * (double)0.1F;
                    double d8 = (double)pos.getY() + random.nextDouble();
                    double d13 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d3, d8, d13, (double)0.0F, (double)i, (double)0.0F);
                }
            }

            if (this.canBurn(level.getBlockState(pos.east()))) {
                for(int k = 0; k < 2; ++k) {
                    double d4 = (double)(pos.getX() + 1) - random.nextDouble() * (double)0.1F;
                    double d9 = (double)pos.getY() + random.nextDouble();
                    double d14 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d4, d9, d14, (double)0.0F, (double)i, (double)0.0F);
                }
            }

            if (this.canBurn(level.getBlockState(pos.north()))) {
                for(int l = 0; l < 2; ++l) {
                    double d5 = (double)pos.getX() + random.nextDouble();
                    double d10 = (double)pos.getY() + random.nextDouble();
                    double d15 = (double)pos.getZ() + random.nextDouble() * (double)0.1F;
                    level.addParticle(ParticleTypes.PORTAL, d5, d10, d15, (double)0.0F, (double)i, (double)0.0F);
                }
            }

            if (this.canBurn(level.getBlockState(pos.south()))) {
                for(int i1 = 0; i1 < 2; ++i1) {
                    double d6 = (double)pos.getX() + random.nextDouble();
                    double d11 = (double)pos.getY() + random.nextDouble();
                    double d16 = (double)(pos.getZ() + 1) - random.nextDouble() * (double)0.1F;
                    level.addParticle(ParticleTypes.PORTAL, d6, d11, d16, (double)0.0F, (double)i, (double)0.0F);
                }
            }

            if (this.canBurn(level.getBlockState(pos.above()))) {
                for(int j1 = 0; j1 < 2; ++j1) {
                    double d7 = (double)pos.getX() + random.nextDouble();
                    double d12 = (double)(pos.getY() + 1) - random.nextDouble() * (double)0.1F;
                    double d17 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.PORTAL, d7, d12, d17, (double)0.0F, (double)i, (double)0.0F);
                }
            }
        } else {
            for(int m = 0; m < 3; ++m) {
                double d0 = (double)pos.getX() + random.nextDouble();
                double d1 = (double)pos.getY() + random.nextDouble() * (double)0.5F + (double)0.5F;
                double d2 = (double)pos.getZ() + random.nextDouble();
                level.addParticle(ParticleTypes.PORTAL, d0, d1, d2, (double)0.0F, (double)i, (double)0.0F);
            }
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
    }
}
