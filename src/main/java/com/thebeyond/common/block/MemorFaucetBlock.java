package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.MemorFaucetBlockEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MemorFaucetBlock extends BaseEntityBlock {
    public static final int MAX_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public MemorFaucetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{AGE, FACING});
    }
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public BlockState rotate(BlockState blockState, Rotation rotation) {
        return (BlockState)blockState.setValue(FACING, rotation.rotate((Direction)blockState.getValue(FACING)));
    }

    public BlockState mirror(BlockState blockState, Mirror mirror) {
        return blockState.rotate(mirror.getRotation((Direction)blockState.getValue(FACING)));
    }

    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (level.isClientSide) return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        if (state.getValue(AGE) < MAX_AGE-1) {
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1, 1);
            level.setBlockAndUpdate(pos, state.setValue(AGE, state.getValue(AGE) + 1));
            if (level instanceof ServerLevel serverLevel)
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        else {
            level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.BLOCKS, 1, 1);
            level.setBlockAndUpdate(pos, state.setValue(AGE, 5));
            if (level instanceof ServerLevel serverLevel)
                serverLevel.sendParticles(BeyondParticleTypes.AURORACITE_STEP.get(), pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);

            if (level.getBlockState(pos.below()).isAir()) {
                level.setBlockAndUpdate(pos.below(), Blocks.WATER.defaultBlockState());
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1, 0.5f);
                level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS, 1, 0.5f);
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return super.useWithoutItem(state, level, pos, player, hitResult);
        if (state.getValue(AGE) < MAX_AGE) {
            level.playSound(null, pos, SoundEvents.VAULT_DEACTIVATE, SoundSource.BLOCKS, 1, 1);
            return super.useWithoutItem(state, level, pos, player, hitResult);
        } else {
            if (level.getBlockState(pos.below()).isAir()) {
                level.setBlockAndUpdate(pos.below(), Blocks.WATER.defaultBlockState());
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1, 0.5f);
                level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS, 1, 1);
                return super.useWithoutItem(state, level, pos, player, hitResult);
            }
            if (level.getBlockState(pos.below()).is(Blocks.WATER)) {
                level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1, 0.5f);
                level.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS, 1, 0.5f);
                return super.useWithoutItem(state, level, pos, player, hitResult);
            }
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    public VoxelShape makeShape(Direction facing){
        VoxelShape shape = Shapes.empty();
        if (facing == Direction.SOUTH) {
            shape = Shapes.join(shape, Shapes.box(0.375, 0, 0.1875, 0.625, 0.1875, 0.4375), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.1875, 0.1875, 0, 0.8125, 1, 0.625), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.8125, 0.3125, 0.1875, 1, 0.8125, 0.4375), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0, 0.3125, 0.1875, 0.1875, 0.8125, 0.4375), BooleanOp.OR);

            return shape;
        }
        if (facing == Direction.WEST) {
            shape = Shapes.join(shape, Shapes.box(0.5625, 0, 0.375, 0.8125, 0.1875, 0.625), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.375, 0.1875, 0.1875, 1, 1, 0.8125), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.5625, 0.3125, 0.8125, 0.8125, 0.8125, 1), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.5625, 0.3125, 0, 0.8125, 0.8125, 0.1875), BooleanOp.OR);

            return shape;
        }
        if (facing == Direction.EAST) {
            shape = Shapes.join(shape, Shapes.box(0.1875, 0, 0.375, 0.4375, 0.1875, 0.625), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0, 0.1875, 0.1875, 0.625, 1, 0.8125), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.1875, 0.3125, 0.8125, 0.4375, 0.8125, 1), BooleanOp.OR);
            shape = Shapes.join(shape, Shapes.box(0.1875, 0.3125, 0, 0.4375, 0.8125, 0.1875), BooleanOp.OR);

            return shape;
        }

        shape = Shapes.join(shape, Shapes.box(0.1875, 0.1875, 0.375, 0.8125, 1, 1), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.8125, 0.3125, 0.5625, 1, 0.8125, 0.8125), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0, 0.3125, 0.5625, 0.1875, 0.8125, 0.8125), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.375, 0, 0.5625, 0.625, 0.1875, 0.8125), BooleanOp.OR);

        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return makeShape(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MemorFaucetBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BeyondBlockEntities.MEMOR_FAUCET.get(), MemorFaucetBlockEntity::tick);
    }

}
