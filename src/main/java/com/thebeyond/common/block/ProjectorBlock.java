package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import com.thebeyond.common.registry.BeyondBlockEntities;
import org.joml.Vector3f;

/** Same-group fragments forming a full picture fire a one-shot reveal, a rising redstone edge advances the carousel. */
public class ProjectorBlock extends BaseEntityBlock {
    public static final MapCodec<ProjectorBlock> CODEC = simpleCodec(ProjectorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    static boolean DIAG_LIT = true;

    public ProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(TRIGGERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, TRIGGERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        // seeded so a projector placed into a powered cell does not take that signal for a rising edge
        return state.setValue(POWERED, isLightBlock(state, level, pos)).setValue(TRIGGERED, level.hasNeighborSignal(pos));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            logLit(level, pos, state, "placed");
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);

        if(random.nextBoolean()) return;

        Direction dir = state.getValue(FACING);
        Vec3 particlePos = Vec3.atCenterOf(pos.relative(dir)).add(-0.3f + random.nextFloat()*0.3f, -0.3f + random.nextFloat()*0.3f, -0.3f + random.nextFloat()*0.3f);
        level.addParticle(new PixelColorTransitionOptions(
                new Vector3f(1.0f, 1.0f, 1.0f),
                new Vector3f(0.9f, 0.5f, 0.9f),
                0.2f
        ), particlePos.x, particlePos.y, particlePos.z, dir.getStepX(), 0.001f, dir.getStepZ());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProjectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null
                : createTickerHelper(blockEntityType, BeyondBlockEntities.PROJECTOR.get(), ProjectorBlockEntity::serverTick);
    }

    /** The block the projection drapes onto. */
    public static BlockPos frontOrigin(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // sneaking passes through so the held item can be placed, otherwise the GUI opens
        if (player.isSecondaryUseActive()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.getBlockEntity(pos) instanceof ProjectorBlockEntity be) {
            if (!level.isClientSide) {
                player.openMenu(be, buf -> buf.writeBlockPos(pos));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof ProjectorBlockEntity be)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (player.isShiftKeyDown()) {
            if (be.getMode() == ProjectorBlockEntity.MODE_CAROUSEL) {
                if (!level.isClientSide) {
                    be.advanceCarousel();
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }

        boolean lightBlock = isLightBlock(state, level, pos);
        boolean signal = level.hasNeighborSignal(pos);
        boolean rising = signal && !state.getValue(TRIGGERED);
        BlockState next = state.setValue(POWERED, lightBlock).setValue(TRIGGERED, signal);
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (lightBlock != state.getValue(POWERED)) {
                logLit(level, pos, next, "neighbour");
            }
        }

        if (rising && level.getBlockEntity(pos) instanceof ProjectorBlockEntity be && be.getMode() == ProjectorBlockEntity.MODE_CAROUSEL) {
            be.advanceCarousel();
            if (level instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 6; i++) {
                    makeParticle(serverLevel, pos, 1);
                }
            }
        }
    }

    private static void makeParticle(ServerLevel level, BlockPos pos, float alpha) {
        double d0 = (double)pos.getX() + (double)0.0F + level.getRandom().nextFloat();
        double d1 = (double)pos.getY() + (double)1.0F + level.getRandom().nextFloat();
        double d2 = (double)pos.getZ() + (double)0.0F + level.getRandom().nextFloat();
        level.sendParticles(new DustParticleOptions(DustParticleOptions.REDSTONE_PARTICLE_COLOR, alpha), d0, d1, d2, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void logLit(Level level, BlockPos pos, BlockState state, String cause) {
        if (!DIAG_LIT) {
            return;
        }
        BlockPos behind = pos.relative(state.getValue(FACING).getOpposite());
        BlockState behindState = level.getBlockState(behind);
        TheBeyond.LOGGER.info("[Projector] be@{} {} lit={} (behind: {}, light {})", pos.toShortString(), cause, state.getValue(POWERED),
                BuiltInRegistries.BLOCK.getKey(behindState.getBlock()), behindState.getLightEmission(level, behind));
    }

    static boolean isLightBlock(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(ProjectorBlock.FACING);
        BlockPos behind = pos.relative(facing.getOpposite());
        BlockState behindState = level.getBlockState(behind);
        return behindState.getLightEmission(level, behind) > 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ProjectorBlockEntity be) {
                Containers.dropContents(level, pos, be);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }
}
