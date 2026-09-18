package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.PerkaStalkMouthBlockEntity;
import com.thebeyond.common.entity.StalkerEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class PerkaStalkMouthBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING;
    public static final MapCodec<PerkaStalkMouthBlock> CODEC = simpleCodec(PerkaStalkMouthBlock::new);
    private BlockPos listenerPos = BlockPos.ZERO;

    public PerkaStalkMouthBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = this.defaultBlockState();
        LevelReader levelreader = context.getLevel();
        BlockPos blockpos = context.getClickedPos();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis().isHorizontal()) {
                blockstate = blockstate.setValue(FACING, direction.getOpposite());
                if (blockstate.canSurvive(levelreader, blockpos)) {
                    return blockstate;
                }
            }
        }

        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        spawnStalker(level, pos, null);
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    public static boolean spawnStalker(Level level, BlockPos pos, Vec3 target) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.is(BeyondBlocks.PERKA_STALK_MOUTH)) return false;
        Direction value = state.getValue(FACING);
        if (level.getBlockState(pos.offset(value.getStepX(), value.getStepY(), value.getStepZ())).isAir() && level.getBlockState(pos.offset(value.getStepX()*2, value.getStepY()*2, value.getStepZ()*2)).isAir()) {
            StalkerEntity stalker = new StalkerEntity(BeyondEntityTypes.STALKER.get(), level);
            stalker.setPos(Vec3.atCenterOf(pos.offset(value.getStepX(), value.getStepY(), value.getStepZ())).add(0,-0.5,0));
            stalker.level().broadcastEntityEvent(stalker, StalkerEntity.SPREAD);
            stalker.setFacing(value);
            stalker.base = true;
            stalker.setOriginalTarget(target);
            level.addFreshEntity(stalker); return true;
        }
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    protected BlockState rotate(BlockState state, Rotation rot) {
        return (BlockState)state.setValue(FACING, rot.rotate((Direction)state.getValue(FACING)));
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockentity = level.getBlockEntity(pos);
        return blockentity == null ? false : blockentity.triggerEvent(id, param);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new PerkaStalkMouthBlockEntity(blockPos, blockState);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_222100_, BlockState p_222101_, BlockEntityType<T> p_222102_) {
        return p_222100_.isClientSide ? null : createTickerHelper(p_222102_, BeyondBlockEntities.PERKA_STALK_MOUTH.get(), PerkaStalkMouthBlockEntity::serverTick);
    }

    static {
        FACING = BlockStateProperties.HORIZONTAL_FACING;
    }
}
