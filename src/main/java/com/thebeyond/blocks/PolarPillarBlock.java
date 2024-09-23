package com.thebeyond.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PolarPillarBlock extends Block {
    public static final MapCodec<PolarPillarBlock> CODEC = simpleCodec(PolarPillarBlock::new);

    public static final BooleanProperty IS_BULB;
    public static final IntegerProperty GLOP_CHARGE;
    public static final IntegerProperty POLAR_CHARGE;

    public MapCodec<PolarPillarBlock> codec() {
        return CODEC;
    }

    public PolarPillarBlock(Properties properties) {
        super(properties);

        registerDefaultState(this.stateDefinition.any()
                .setValue(IS_BULB, false)
                .setValue(GLOP_CHARGE, 4)
                .setValue(POLAR_CHARGE, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IS_BULB, GLOP_CHARGE, POLAR_CHARGE);
    }

    //VoxelShapes here
    //

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        state.setValue(IS_BULB, false);
        state.setValue(GLOP_CHARGE, 4);
        state.setValue(POLAR_CHARGE, 0);
    }

    static {
        IS_BULB = BooleanProperty.create("is_bulb");
        GLOP_CHARGE = IntegerProperty.create("glop_charge", 0, 4);
        //0 - none | 1 - lower 50% | 2 - lower 100%, upper 50% | 3 - lower 50%, upper 100% | 4 - upper 50%
        POLAR_CHARGE = IntegerProperty.create("polar_charge", 0, 4);
    }
}