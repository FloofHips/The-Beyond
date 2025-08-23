package com.thebeyond.common.blocks;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.blocks.blockstates.PillarHeightProperty;
import com.thebeyond.common.blocks.blockstates.StabilityProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

import javax.annotation.Nullable;

public class VoidCrystalBlock extends Block {
    public static final MapCodec<VoidCrystalBlock> CODEC = simpleCodec(VoidCrystalBlock::new);
    public static final EnumProperty<PillarHeightProperty> HEIGHT;
    public static final BooleanProperty UP;
    public VoidCrystalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(UP, Boolean.TRUE)).setValue(HEIGHT, PillarHeightProperty.TIP)));
    }
    public MapCodec<VoidCrystalBlock> codec() {
        return CODEC;
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{UP, HEIGHT});
    }
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(UP) ? Direction.UP : Direction.DOWN;
        BlockPos supportingPos = pos.relative(direction.getOpposite());
        if (direction == Direction.UP && level.getBlockState(supportingPos).getBlock() instanceof GellidVoidBlock) return true;
        return canSupportCenter(level, supportingPos, direction);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();

        boolean pointingUp = clickedFace != Direction.DOWN;

        return this.defaultBlockState()
                .setValue(UP, pointingUp)
                .setValue(HEIGHT, PillarHeightProperty.TIP);

    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(UP)) {
            if (level.getBlockState(pos.below()).isAir()){
                return Blocks.AIR.defaultBlockState();
            }
           if (!(level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock)){
              return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.TIP);
           }
           else {
               if (level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock && level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock) {
                   return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.CORE);
               }
               if (!(level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock)) {
                   return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.BASE);
               }
           }
        }

        if (!state.getValue(UP)) {
            if (level.getBlockState(pos.above()).isAir()){
                return Blocks.AIR.defaultBlockState();
            }
            if (!(level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock)){
                return this.defaultBlockState().setValue(UP, false).setValue(HEIGHT, PillarHeightProperty.TIP);
            }
            else {
                if (level.getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock && level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock) {
                    return this.defaultBlockState().setValue(UP, false).setValue(HEIGHT, PillarHeightProperty.CORE);
                }
                if (!(level.getBlockState(pos.above()).getBlock() instanceof VoidCrystalBlock)) {
                    return this.defaultBlockState().setValue(UP, false).setValue(HEIGHT, PillarHeightProperty.BASE);
                }
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    static {
        UP = BlockStateProperties.UP;
        HEIGHT = EnumProperty.create("height", PillarHeightProperty.class);

//        TIP_SHAPE_UP = Block.box(5.0, 0.0, 5.0, 11.0, 11.0, 11.0);
//        TIP_SHAPE_DOWN = Block.box(5.0, 5.0, 5.0, 11.0, 16.0, 11.0);
//        CORE_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
//        BASE_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0);
    }
}
