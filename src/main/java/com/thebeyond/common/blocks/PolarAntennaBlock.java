package com.thebeyond.common.blocks;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.blocks.blockstates.StabilityProperty;
import com.thebeyond.util.IMagneticReceiver;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;
import java.util.function.ToIntFunction;

public class PolarAntennaBlock extends Block implements IMagneticReceiver {
    public static final MapCodec<PolarAntennaBlock> CODEC = simpleCodec(PolarAntennaBlock::new);

    public static final BooleanProperty COOLDOWN;
    public static final IntegerProperty RANGE;
    public static final EnumProperty<StabilityProperty> STABILITY;

    public MapCodec<PolarAntennaBlock> codec() {
        return CODEC;
    }

    public static ToIntFunction<BlockState> STATE_TO_LUMINANCE = new ToIntFunction<>() {
        @Override
        public int applyAsInt(BlockState value) {
            return switch (value.getValue(STABILITY)) {
                case NONE -> 0;
                case LOW -> 1;
                case MEDIUM -> 2;
                case HIGH -> 3;
                case SEEKING -> 4;
            };
        }
    };

    public PolarAntennaBlock(Properties properties) {
        super(properties);

        registerDefaultState(this.stateDefinition.any()
                .setValue(COOLDOWN, false)
                .setValue(RANGE, 0)
                .setValue(STABILITY, StabilityProperty.NONE)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COOLDOWN, RANGE, STABILITY);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN &&
                !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() :
                super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) return;
        if (state.getValue(COOLDOWN)) return;

        StabilityProperty currentStability = state.getValue(STABILITY);
        StabilityProperty[] stabilityList = StabilityProperty.values();

        if (currentStability.ordinal() + 1 < stabilityList.length) {
            StabilityProperty nextStability = stabilityList[currentStability.ordinal() + 1];
            BlockState newState = nextStability == StabilityProperty.SEEKING ?
                    state.setValue(COOLDOWN, true).setValue(STABILITY, nextStability).setValue(RANGE, 15) :
                    state.setValue(COOLDOWN, true).setValue(STABILITY, nextStability);

            level.scheduleTick(pos, this, switch (nextStability) {case NONE, LOW, MEDIUM, HIGH -> 20; case SEEKING -> 3;});
            level.setBlock(pos, newState, 3);
        }
    }

    @Override
    public void receiveSignal(BlockPos pos, BlockState state, Level level, @Nullable BlockState senderState) {
        if (senderState == null) return;

        int newRange = senderState.getValue(RANGE) - 1;
        StabilityProperty nextStability = switch (newRange) {
            case 1, 2, 3 -> StabilityProperty.LOW;
            case 4, 5, 6, 7 -> StabilityProperty.MEDIUM;
            case 8, 9, 10, 11 -> StabilityProperty.HIGH;
            case 12, 13, 14, 15 -> StabilityProperty.SEEKING;
            default -> StabilityProperty.NONE;
        };

        level.scheduleTick(pos, this, 2);
        level.setBlock(pos, state.setValue(COOLDOWN, true).setValue(STABILITY, nextStability).setValue(RANGE, newRange), 3);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(COOLDOWN) &&
                state.getValue(RANGE) == 0 &&
                state.getValue(STABILITY) != StabilityProperty.NONE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StabilityProperty nextStability = StabilityProperty.values()[state.getValue(STABILITY).ordinal() - 1];
        level.setBlock(pos, state.setValue(STABILITY, nextStability), 3);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RANGE) > 0) {
            BlockBox searchBox = new BlockBox(
                    new BlockPos(pos.getX()-1, pos.getY()-1, pos.getZ()-1),
                    new BlockPos(pos.getX()+1, pos.getY()+1, pos.getZ()+1)
                    );

            searchBox.forEach((searchPos) -> {
                if (level.getBlockState(searchPos).getBlock() instanceof IMagneticReceiver && searchPos != pos) {
                    if (level.getBlockState(searchPos).is(this)) {
                        if (!level.getBlockState(searchPos).getValue(COOLDOWN))
                            ((IMagneticReceiver) level.getBlockState(searchPos).getBlock()).receiveSignal(searchPos, level.getBlockState(searchPos), level, state);
                    } else {
                        ((IMagneticReceiver) level.getBlockState(searchPos).getBlock()).receiveSignal(searchPos, level.getBlockState(searchPos), level, state);
                    }
                }
            });

            level.scheduleTick(pos, this, 6 - state.getValue(STABILITY).ordinal());
            int newRange = state.getValue(RANGE) - 1;
            if (newRange >= 0) {
                StabilityProperty nextStability = switch (newRange) {
                    case 1, 2, 3 -> StabilityProperty.LOW;
                    case 4, 5, 6, 7 -> StabilityProperty.MEDIUM;
                    case 8, 9, 10, 11 -> StabilityProperty.HIGH;
                    case 12, 13, 14, 15 -> StabilityProperty.SEEKING;
                    default -> StabilityProperty.NONE;
                };
                level.setBlock(pos, state.setValue(RANGE, newRange).setValue(STABILITY, nextStability), 3);
            }
        }

        if (state.getValue(COOLDOWN) && state.getValue(RANGE) == 0) level.setBlock(pos, state.setValue(COOLDOWN, false), 3);
    }

    static {
        COOLDOWN = BlockStateProperties.DISARMED;
        RANGE = IntegerProperty.create("range", 0, 15);
        STABILITY = EnumProperty.create("stability", StabilityProperty.class);
    }
}