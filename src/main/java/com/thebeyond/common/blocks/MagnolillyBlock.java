package com.thebeyond.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MagnolillyBlock extends Block {
    public MagnolillyBlock(Properties properties) {
        super(properties);
    }

    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        return pFacing == Direction.DOWN && !this.canSurvive(pState, pLevel, pCurrentPos) ? Blocks.AIR.defaultBlockState() : super.updateShape(pState, pFacing, pFacingState, pLevel, pCurrentPos, pFacingPos);
    }

    //protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
    //    FluidState fluidstate = level.getFluidState(pos);
    //    return (fluidstate.getType() == Fluids.WATER);
    //}
    //public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
    //    if (pLevel.getBlockState(pPos.below()).getBlock() instanceof PseudoFluidBlock)
    //        return true;
    //    else return false;
    //}
}
