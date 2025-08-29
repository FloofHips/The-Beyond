package com.thebeyond.common.blocks;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.blocks.blockstates.PillarHeightProperty;
import com.thebeyond.common.blocks.blockstates.StabilityProperty;
import com.thebeyond.common.fluids.GellidVoidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 vec3 = state.getOffset(level, pos);

        if(state.getValue(HEIGHT) == PillarHeightProperty.TIP) {
            if(state.getValue(UP))
                return TIP_SHAPE.move(vec3.x, 0.0, vec3.z);
            return TIP_SHAPE_DOWN.move(vec3.x, 0.0, vec3.z);
        }
        return CORE_SHAPE.move(vec3.x, 0.0, vec3.z);
    }

    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = state.getValue(UP) ? Direction.UP : Direction.DOWN;
        BlockPos supportingPos = pos.relative(direction.getOpposite());
        if (level.getBlockState(supportingPos).getBlock() instanceof VoidCrystalBlock) return true;
        if (direction == Direction.UP && level.getBlockState(supportingPos).getBlock() instanceof GellidVoidBlock) return true;
        return canSupportCenter(level, supportingPos, direction);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction clickedFace = context.getClickedFace();
        BlockPos pos = context.getClickedPos();

        if (context.getLevel().getBlockState(pos.below()).getBlock() instanceof VoidCrystalBlock || context.getLevel().getBlockState(pos.below()).getBlock() instanceof GellidVoidBlock || canSupportCenter(level, pos.below(), Direction.UP) && clickedFace != Direction.DOWN) {
            return this.defaultBlockState().setValue(UP, true).setValue(HEIGHT, PillarHeightProperty.TIP);
        }

        return this.defaultBlockState()
                .setValue(UP, false)
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

    private static final VoxelShape TIP_SHAPE;
    private static final VoxelShape TIP_SHAPE_DOWN;
    private static final VoxelShape CORE_SHAPE;

    static {
        UP = BlockStateProperties.UP;
        HEIGHT = EnumProperty.create("height", PillarHeightProperty.class);

        TIP_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0);
        TIP_SHAPE_DOWN = Block.box(3.0, 3.0, 3.0, 13.0, 16.0, 13.0);
        CORE_SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    }
}
