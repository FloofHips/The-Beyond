package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.fluid.GellidVoidBlock;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
        return state.is(BlockTags.INFINIBURN_END);
    }

    protected boolean canBurn(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }
}
