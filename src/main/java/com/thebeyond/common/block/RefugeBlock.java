package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.RefugeBlockEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;

public class RefugeBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public RefugeBlock(Properties properties) {
        super(properties);
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{POWERED});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(POWERED, false);
    }
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    public static boolean isActive(BlockState state) {
        return state.getValue(POWERED);
    }

    //@Override
    //protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    //    super.onPlace(state, level, pos, oldState, movedByPiston);
    //    BlockEntity be = level.getBlockEntity(pos);
    //    if (be instanceof RefugeBlockEntity refugeBlockEntity) {
    //        refugeBlockEntity.setOwner();
    //    }
    //}

    //protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    //    if (level.isClientSide) {
    //        return InteractionResult.SUCCESS;
    //    } else {
    //        BlockEntity blockEntity = level.getBlockEntity(pos);
    //        if (blockEntity instanceof RefugeBlockEntity refugeBlockEntity) {
    //            if (refugeBlockEntity.getOwner() == null)
    //                refugeBlockEntity.setOwner(player.getUUID());
    //            refugeBlockEntity.setMode(cycleModes(refugeBlockEntity.getMode()), refugeBlockEntity);
    //            player.displayClientMessage(Component.literal("This area is protected!!! " + refugeBlockEntity.getMode()), true);
    //        }
    //        return InteractionResult.CONSUME;
    //    }
    //}


    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(Items.PLAYER_HEAD)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RefugeBlockEntity refugeBlockEntity) {
                refugeBlockEntity.setOwner(stack);
            }
            return ItemInteractionResult.CONSUME;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        //if (level.isClientSide) {
        //    return InteractionResult.SUCCESS;
        //} else {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RefugeBlockEntity refugeBlockEntity) {
                if (refugeBlockEntity.getOwnerProfile() == null) {
                    refugeBlockEntity.setOwner(new ResolvableProfile(player.getGameProfile()));
                    player.displayClientMessage(Component.literal("added " + player.getGameProfile().getName()), true);
                } else {
                    player.displayClientMessage(Component.literal(refugeBlockEntity.getOwnerProfile().gameProfile().getName()), true);
                }

                if (level.isClientSide){
                    if (player.isShiftKeyDown()) {
                        refugeBlockEntity.animating = 80;
                    }

                        //player.openMenu(refugeBlockEntity);
                    //else
                    //    refugeBlockEntity.print();
                }
                //refugeBlockEntity.setMode(cycleModes(refugeBlockEntity.getMode()), refugeBlockEntity);
                //player.displayClientMessage(Component.literal("This area is protected!!! " + refugeBlockEntity.getMode()), true);
            }

            return InteractionResult.CONSUME;
        //}
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RefugeBlockEntity refugeBlockEntity) {
            refugeBlockEntity.remove();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public byte cycleModes(byte i) {
        if (i == (byte) 0) return (byte) 1;
        if (i == (byte) 1) return (byte) 2;
        if (i == (byte) 2) return (byte) 3;
        if (i == (byte) 3) return (byte) 0;
        if (i == (byte) -1) return (byte) 0;
        return (byte) -1;
    }

    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(POWERED) ? 14 : 5;
    }
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new RefugeBlockEntity(blockPos, blockState);
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BeyondBlockEntities.REFUGE.get(), RefugeBlockEntity::tick);
    }
}
