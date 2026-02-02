package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.block.blockstates.SizeProperty;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Arrays;

public class AmphoraBlock extends FallingBlock {
    public static final EnumProperty<SizeProperty> SIZE;

    public AmphoraBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((this.stateDefinition.any()).setValue(SIZE, SizeProperty.SMALL));
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return null;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{SIZE});
    }

    @Override
    public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity fallingBlock) {
        destroy(level, pos);
        level.destroyBlock(pos, true);
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockpos = hit.getBlockPos();
        if (!level.isClientSide && projectile.mayInteract(level, blockpos) && projectile.mayBreak(level)) {
            destroy(level, blockpos);
            level.destroyBlock(blockpos, true, projectile);
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        destroy(level, pos);
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    public void destroy(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.BLOCKS, 1, 0.5f + level.random.nextFloat() * 1.5f);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ColorUtils.dustOptions, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextInt(20, 50), 0.02,0.2,0.02,0.04);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(SIZE) == SizeProperty.SMALL) return SMALL_SHAPE;
        if (state.getValue(SIZE) == SizeProperty.MEDIUM) return MEDIUM_SHAPE;
        return LARGE_SHAPE;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(SIZE, Arrays.stream(SizeProperty.values()).toList().get((int) (Math.random()*2.99)));
    }

    private static final VoxelShape SMALL_SHAPE;
    private static final VoxelShape MEDIUM_SHAPE;
    private static final VoxelShape LARGE_SHAPE;

    static {
        SIZE = EnumProperty.create("size", SizeProperty.class);
        SMALL_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 9.0, 14.0);
        MEDIUM_SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
        LARGE_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    }
}
