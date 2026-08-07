package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.entity.PerkaStalkerEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class PerkaStalkMouthBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<PerkaStalkMouthBlock> CODEC = simpleCodec(PerkaStalkMouthBlock::new);

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
        Direction value = state.getValue(FACING);
        if (level.getBlockState(pos.offset(value.getStepX(), value.getStepY(), value.getStepZ())).isAir() && level.getBlockState(pos.offset(value.getStepX()*2, value.getStepY()*2, value.getStepZ()*2)).isAir()) {
            PerkaStalkerEntity stalker = new PerkaStalkerEntity(BeyondEntityTypes.PERKA_STALKER.get(), level);
            stalker.setPos(Vec3.atCenterOf(pos.offset(value.getStepX(), value.getStepY(), value.getStepZ())).add(0,-0.5,0));
            stalker.level().broadcastEntityEvent(stalker, PerkaStalkerEntity.SPREAD);
            stalker.setFacing(value);
            stalker.base = true;
            level.addFreshEntity(stalker);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
