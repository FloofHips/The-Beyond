package com.thebeyond.common.blocks;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.util.IMagneticReceiver;
import com.thebeyond.util.RandomUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
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

    public static final BooleanProperty IS_BULB;
    public static final IntegerProperty GLOP_CHARGE;
    public static final IntegerProperty POLAR_CHARGE;
    private int growTicker;

    public MapCodec<PolarPillarBlock> codec() {
        return CODEC;
    }

    public PolarPillarBlock(Properties properties) {
        super(properties);
        this.growTicker = 0;

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
    private final VoxelShape FULL_CUBE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final VoxelShape OPEN_BULB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    public void activatePillar(BlockPos pos, BlockState state, Level level) {
        for (int offset = 1; offset <= 8; offset++) {
            Pair<BlockPos, BlockState> newBlockFound = new Pair<>(new BlockPos(pos.getX(), pos.getY() + offset, pos.getZ()), level.getBlockState(new BlockPos(pos.getX(), pos.getY() - offset, pos.getZ())));
            if (newBlockFound.getB().is(this)) {
                if (newBlockFound.getB().getValue(IS_BULB)) {
                    if (newBlockFound.getB().getValue(GLOP_CHARGE) != 4) return;
                } else return;
            } else break;
        }

        Pair<BlockPos, BlockState> lastPillar = new Pair<>(pos, state);
        for (int offset = 1; offset <= 8; offset++) {
            Pair<BlockPos, BlockState> newBlockFound = new Pair<>(new BlockPos(pos.getX(), pos.getY() - offset, pos.getZ()), level.getBlockState(new BlockPos(pos.getX(), pos.getY() - offset, pos.getZ())));
            if (newBlockFound.getB().is(this)) {
                if (!newBlockFound.getB().getValue(IS_BULB)) lastPillar = newBlockFound;
            } else {
                level.setBlock(lastPillar.getA(), lastPillar.getB().setValue(POLAR_CHARGE, 1), 3);
                level.scheduleTick(lastPillar.getA(), lastPillar.getB().getBlock(), TICK_DELAY, TickPriority.HIGH);
                break;
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(IS_BULB)) return FULL_CUBE;
        return state.getValue(GLOP_CHARGE) < 4 ? OPEN_BULB : FULL_CUBE;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.sidedSuccess(level.isClientSide);

        this.activatePillar(pos, state, level);

        return InteractionResult.sidedSuccess(false);
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
                if (level.getBlockState(pos.above()).is(this)) level.setBlock(pos.above(), level.getBlockState(pos.above()).setValue(POLAR_CHARGE, 1), 3);
                level.scheduleTick(pos, state.getBlock(), TICK_DELAY, TickPriority.HIGH);
                level.scheduleTick(pos.above(), state.getBlock(), TICK_DELAY, TickPriority.HIGH);

                if (state.getValue(GLOP_CHARGE) == 4 && state.getValue(IS_BULB)) {
                    level.setBlock(pos, state.setValue(GLOP_CHARGE, 0), 3);

                    Entity enderglop = new EnderglopEntity(BeyondEntityTypes.ENDERGLOP.get(), level);
                    enderglop.setPos(pos.getX()+0.5, pos.getY()+0.8, pos.getZ()+0.5);
                    enderglop.setDeltaMovement(
                            RandomUtils.nextDouble(0.2, 0.4) * (RandomUtils.nextBoolean() ? -1 : 1),
                            RandomUtils.nextDouble(0.5, 0.75),
                            RandomUtils.nextDouble(0.2, 0.4) * (RandomUtils.nextBoolean() ? -1 : 1)
                    );

                    level.addFreshEntity(enderglop);
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

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(GLOP_CHARGE) != 4 && state.getValue(IS_BULB);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;

        if (this.growTicker < 3) { growTicker++;   return; }
        level.setBlock(pos, state.setValue(GLOP_CHARGE, state.getValue(GLOP_CHARGE) + 1), 3);
        growTicker = 0;
    }

    static {
        IS_BULB = BooleanProperty.create("is_bulb");
        GLOP_CHARGE = IntegerProperty.create("glop_charge", 0, 4);
        //0 - none | 1 - lower 50% | 2 - lower 100%, upper 50% | 3 - lower 50%, upper 100% | 4 - upper 50%
        POLAR_CHARGE = IntegerProperty.create("polar_charge", 0, 4);
    }
}