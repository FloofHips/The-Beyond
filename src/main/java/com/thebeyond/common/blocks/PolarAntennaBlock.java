package com.thebeyond.common.blocks;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.blocks.blockstates.StabilityProperty;
import com.thebeyond.util.IMagneticReceiver;
import com.thebeyond.util.RandomUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

public class PolarAntennaBlock extends Block implements IMagneticReceiver {
    public static final MapCodec<PolarAntennaBlock> CODEC = simpleCodec(PolarAntennaBlock::new);

    private static final float STOP_CHANCE = 0.125f;
    private static final int DELAY = 3;

    public static final BooleanProperty COOLDOWN;
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
                .setValue(STABILITY, StabilityProperty.NONE)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COOLDOWN, STABILITY);
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
        if (state.getValue(COOLDOWN) || !(entity instanceof Player)) return;

        StabilityProperty currentStability = state.getValue(STABILITY);
        StabilityProperty[] stabilityList = StabilityProperty.values();

        if (currentStability.ordinal() + 1 < stabilityList.length) {
            StabilityProperty nextStability = stabilityList[currentStability.ordinal() + 1];

            level.scheduleTick(pos, this, switch (nextStability) {case NONE, LOW, MEDIUM, HIGH -> DELAY*3; case SEEKING -> DELAY;});
            level.setBlock(pos, state.setValue(COOLDOWN, true).setValue(STABILITY, nextStability), 3);
        }
    }

    @Override
    public void receiveSignal(BlockPos pos, BlockState state, Level level, @Nullable BlockState senderState) {
        level.scheduleTick(pos, this, DELAY);
        level.setBlock(pos, state.setValue(COOLDOWN, true).setValue(STABILITY, StabilityProperty.SEEKING), 3);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(COOLDOWN) && state.getValue(STABILITY) != StabilityProperty.NONE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StabilityProperty nextStability = StabilityProperty.values()[state.getValue(STABILITY).ordinal() - 1];
        level.setBlock(pos, state.setValue(STABILITY, nextStability), 3);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(STABILITY) == StabilityProperty.SEEKING) {
            ArrayList<BlockPos> affectedList = new ArrayList<>();

            List<Direction> horizontalDirections = new ArrayList<>(Arrays.asList(Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new)));
            Collections.shuffle(horizontalDirections);

            int numDirections = RandomUtils.nextInt(0, 2);
            List<Direction> chosenDirections = horizontalDirections.subList(0, numDirections);

            for (Direction horizontal : chosenDirections) {
                affectedList.add(pos.relative(horizontal));
                affectedList.add(pos.relative(horizontal).above());
                affectedList.add(pos.relative(horizontal).below());
            }

            if (RandomUtils.nextFloat() <= STOP_CHANCE) return;

            affectedList.forEach((affectedPos) -> {
                if (level.getBlockState(affectedPos).getBlock() instanceof IMagneticReceiver) {
                    level.scheduleTick(affectedPos, level.getBlockState(affectedPos).getBlock(), DELAY);
                    if (level.getBlockState(affectedPos).is(this)) {
                        if (level.getBlockState(affectedPos).getValue(STABILITY) == StabilityProperty.NONE)
                            ((IMagneticReceiver) level.getBlockState(affectedPos).getBlock()).receiveSignal(affectedPos, level.getBlockState(affectedPos), level, null);
                    } else {
                        ((IMagneticReceiver) level.getBlockState(affectedPos).getBlock()).receiveSignal(affectedPos, level.getBlockState(affectedPos), level, null);
                    }
                }
            });
        }

        if (state.getValue(COOLDOWN)) level.setBlock(pos, state.setValue(COOLDOWN, false), 3);
    }

    static {
        COOLDOWN = BlockStateProperties.DISARMED;
        STABILITY = EnumProperty.create("stability", StabilityProperty.class);
    }
}