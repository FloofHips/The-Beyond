package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.entity.EnderglopEntity;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.util.RandomUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.ToIntFunction;

public class PolarBulbBlock extends Block {
    public static final MapCodec<PolarBulbBlock> CODEC = simpleCodec(PolarBulbBlock::new);

    public static final IntegerProperty GLOP_CHARGE;
    private int growTicker;

    public static ToIntFunction<BlockState> STATE_TO_LUMINANCE = new ToIntFunction<>() {
        @Override
        public int applyAsInt(BlockState value) {
            return 4 - value.getValue(GLOP_CHARGE);
        }
    };

    public PolarBulbBlock(Properties properties) {
        super(properties);
        this.growTicker = 0;

        registerDefaultState(this.stateDefinition.any()
                .setValue(GLOP_CHARGE, 4)
        );
    }

    //VoxelShapes here
    private final VoxelShape FULL_CUBE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final VoxelShape OPEN_BULB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(GLOP_CHARGE) < 4 ? OPEN_BULB : FULL_CUBE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOP_CHARGE);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(GLOP_CHARGE) == 4) {
            level.setBlock(pos, state.setValue(GLOP_CHARGE, 0), 3);

            level.playSound(null, pos, SoundEvents.BREEZE_DEFLECT, SoundSource.BLOCKS, 3, level.random.nextFloat());
            EnderglopEntity enderglop = new EnderglopEntity(BeyondEntityTypes.ENDERGLOP.get(), level);
            enderglop.setPos(pos.getX()+0.5, pos.getY()+0.8, pos.getZ()+0.5);
            enderglop.setSize(level.isRaining() ? 3 : 2, false);
            level.addFreshEntity(enderglop);
            LivingEntity player = level.getNearestPlayer(enderglop, 10);
            Vec3 direction = player == null ? Vec3.ZERO : player.position().subtract(enderglop.position()).normalize();
            enderglop.setDeltaMovement(
                    direction.x,
                    RandomUtils.nextDouble(0.5, 0.75),
                    direction.z
            );
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(GLOP_CHARGE) != 4;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;

        if (this.growTicker < 3) { growTicker++;   return; }
        level.setBlock(pos, state.setValue(GLOP_CHARGE, state.getValue(GLOP_CHARGE) + 1), 3);
        growTicker = 0;
    }

    static {
        GLOP_CHARGE = IntegerProperty.create("glop_charge", 0, 4);
    }
}
