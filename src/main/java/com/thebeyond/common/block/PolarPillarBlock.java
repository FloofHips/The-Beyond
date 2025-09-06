package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.util.IMagneticReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import oshi.util.tuples.Pair;

import javax.annotation.Nullable;
import java.util.function.ToIntFunction;

public class PolarPillarBlock extends Block implements IMagneticReceiver {
    public static final MapCodec<PolarPillarBlock> CODEC = simpleCodec(PolarPillarBlock::new);

    private final int TICK_DELAY = 2;
    public static ToIntFunction<BlockState> STATE_TO_LUMINANCE = new ToIntFunction<>() {
        @Override
        public int applyAsInt(BlockState value) {
            return value.getValue(POLAR_CHARGE);
        }
    };
    public static final IntegerProperty POLAR_CHARGE;

    public MapCodec<PolarPillarBlock> codec() {
        return CODEC;
    }

    public PolarPillarBlock(Properties properties) {
        super(properties);

        registerDefaultState(this.stateDefinition.any()
                .setValue(POLAR_CHARGE, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POLAR_CHARGE);
    }

    //VoxelShapes here
    private final VoxelShape FULL_CUBE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public void activatePillar(BlockPos pos, BlockState state, Level level) {
        Pair<BlockPos, BlockState> lastPillar = new Pair<>(pos, state);

        for (int offset = 1; offset <= 8; offset++) {
            Pair<BlockPos, BlockState> newBlockFound = new Pair<>(new BlockPos(pos.getX(), pos.getY() - offset, pos.getZ()), level.getBlockState(new BlockPos(pos.getX(), pos.getY() - offset, pos.getZ())));

            if (newBlockFound.getB().getBlock() instanceof PolarPillarBlock || newBlockFound.getB().getBlock() instanceof PolarBulbBlock) {
                if (!(newBlockFound.getB().getBlock() instanceof PolarBulbBlock)) lastPillar = newBlockFound;
            } else {
                level.setBlock(lastPillar.getA(), lastPillar.getB().setValue(POLAR_CHARGE, 1), 3);
                level.scheduleTick(lastPillar.getA(), lastPillar.getB().getBlock(), TICK_DELAY, TickPriority.HIGH);
                break;
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_CUBE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public void receiveSignal(BlockPos pos, BlockState state, Level level, @Nullable BlockState senderState) {
        if (state.getValue(POLAR_CHARGE) > 0) return;
        this.activatePillar(pos, state, level);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (state.getValue(POLAR_CHARGE)) {
            case 1: {
                level.setBlock(pos, state.setValue(POLAR_CHARGE, 2), 3);
                level.scheduleTick(pos, state.getBlock(), TICK_DELAY, TickPriority.HIGH);
                break;
            }
            case 2: {
                level.setBlock(pos, state.setValue(POLAR_CHARGE, 3), 3);
                level.scheduleTick(pos, state.getBlock(), TICK_DELAY, TickPriority.HIGH);

                if (level.getBlockState(pos.above()).getBlock() instanceof PolarBulbBlock) {
                    level.scheduleTick(pos.above(), level.getBlockState(pos.above()).getBlock(), TICK_DELAY, TickPriority.HIGH);
                    break;
                }
                if (level.getBlockState(pos.above()).getBlock() instanceof PolarPillarBlock) {
                    level.setBlock(pos.above(), level.getBlockState(pos.above()).setValue(POLAR_CHARGE, 1), 3);
                    level.scheduleTick(pos.above(), state.getBlock(), TICK_DELAY, TickPriority.HIGH);
                }
                break;
            }
            case 3: {
                level.setBlock(pos, state.setValue(POLAR_CHARGE, 4), 3);
                level.scheduleTick(pos, state.getBlock(), TICK_DELAY, TickPriority.HIGH);
                break;
            }
            case 4: {
                level.setBlock(pos, state.setValue(POLAR_CHARGE, 0), 3);
            }
        }
    }

    static {
        //0 - none | 1 - lower 50% | 2 - lower 100%, upper 50% | 3 - lower 50%, upper 100% | 4 - upper 50%
        POLAR_CHARGE = IntegerProperty.create("polar_charge", 0, 4);
    }
}