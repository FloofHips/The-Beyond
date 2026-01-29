package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.BonfireBlockEntity;
import com.thebeyond.common.block.blockentities.MemorFaucetBlockEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(AGE)*3;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE)==5;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        BlockEntity bonfire = level.getBlockEntity(pos);
        if (bonfire instanceof BonfireBlockEntity bonfireBlockEntity) {
            if (bonfireBlockEntity.isItMyBirthdayToday()) {
                level.setBlockAndUpdate(pos, state.setValue(AGE, 0));
            }
        }
    }
}
