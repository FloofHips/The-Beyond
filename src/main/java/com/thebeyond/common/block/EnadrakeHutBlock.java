package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockstates.HutHeightProperty;
import com.thebeyond.common.block.blockstates.PillarHeightProperty;
import com.thebeyond.common.fluid.GellidVoidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class EnadrakeHutBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<EnadrakeHutBlock> CODEC = simpleCodec(EnadrakeHutBlock::new);
    public static final EnumProperty<HutHeightProperty> HEIGHT;

    public EnadrakeHutBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(HEIGHT, HutHeightProperty.TIP)));

    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, HEIGHT});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if(state.getValue(HEIGHT) == HutHeightProperty.TIP)
            return TIP_SHAPE;
        return CORE_SHAPE;
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();

        if (context.getLevel().getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HEIGHT, HutHeightProperty.TOP);
        }

        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HEIGHT, HutHeightProperty.TIP);

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {

        if (!(level.getBlockState(pos.above()).getBlock() instanceof EnadrakeHutBlock)){
            if (level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) {
                return state.setValue(HEIGHT, HutHeightProperty.TOP);
            }
            return state.setValue(HEIGHT, HutHeightProperty.TIP);
        }
        else {
            if (level.getBlockState(pos.above()).getBlock() instanceof EnadrakeHutBlock && level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock) {
                return state.setValue(HEIGHT, HutHeightProperty.CORE);
            }
            if (!(level.getBlockState(pos.below()).getBlock() instanceof EnadrakeHutBlock)) {
                return state.setValue(HEIGHT, HutHeightProperty.BASE);
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private static final VoxelShape TIP_SHAPE;
    private static final VoxelShape CORE_SHAPE;

    static {
        HEIGHT = EnumProperty.create("height", HutHeightProperty.class);
        TIP_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 9.0, 15.0);
        CORE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    }
}
