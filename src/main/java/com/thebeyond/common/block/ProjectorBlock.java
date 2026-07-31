package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.client.particle.PixelColorTransitionOptions;
import com.thebeyond.common.block.blockentities.ProjectorBlockEntity;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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

/** Same-group fragments forming a full picture fire a one-shot reveal; a redstone rising edge advances the carousel. */
public class ProjectorBlock extends BaseEntityBlock {
    public static final MapCodec<ProjectorBlock> CODEC = simpleCodec(ProjectorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Seed POWERED so placing into a powered cell isn't read as a rising edge later.
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
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
        // Sneak passes through so the held item can place against the block; otherwise open the GUI.
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
        level.setBlock(pos, state.setValue(POWERED, lightBlock), Block.UPDATE_CLIENTS);

        boolean signal = level.hasNeighborSignal(pos);

        if (signal && level.getBlockEntity(pos) instanceof ProjectorBlockEntity be && be.getMode() == ProjectorBlockEntity.MODE_CAROUSEL) {
            be.advanceCarousel();
            for (int i = 0; i < 6; i++) {
                makeParticle(level, pos, 1);
            }
        }
    }

    private static void makeParticle(LevelAccessor level, BlockPos pos, float alpha) {
        double d0 = (double)pos.getX() + (double)0.0F + level.getRandom().nextFloat();
        double d1 = (double)pos.getY() + (double)1.0F + level.getRandom().nextFloat();
        double d2 = (double)pos.getZ() + (double)0.0F + level.getRandom().nextFloat();
        level.addParticle(new DustParticleOptions(DustParticleOptions.REDSTONE_PARTICLE_COLOR, alpha), d0, d1, d2, (double)0.0F, (double)0.0F, (double)0.0F);
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
