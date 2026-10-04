package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.PrismographBlockEntity;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.common.network.BlockCameraRenderRequestPayload;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.camera.SnapshotRequests;
import com.thebeyond.common.registry.BeyondSoundEvents;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

/** Capture is deferred via {@link Level#scheduleTick} to run on the tick thread (clip force-generates chunks). */
public class PrismographBlock extends BaseEntityBlock {
    public static final MapCodec<PrismographBlock> CODEC = simpleCodec(PrismographBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED; // prior signal, for rising-edge detection

    protected static final VoxelShape NORTH_AABB = Block.box(2.0F, 0, 6, 14.0F, 9F, 16.0F);
    protected static final VoxelShape SOUTH_AABB = Block.box(2.0F, 0, 0, 14.0F, 9F, 10.0F);
    protected static final VoxelShape EAST_AABB  = Block.box(0, 0, 2.0F, 10.0F, 9F, 14.0F);
    protected static final VoxelShape WEST_AABB  = Block.box(6, 0, 2.0F, 16.0F, 9F, 14.0F);

    private static final int CAPTURE_COOLDOWN = 10;

    public PrismographBlock(Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case Direction.NORTH -> NORTH_AABB;
            case Direction.SOUTH -> SOUTH_AABB;
            case Direction.WEST -> WEST_AABB;
            case Direction.EAST -> EAST_AABB;
            default -> SOUTH_AABB;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Seed POWERED so placing into a powered cell isn't read as a rising edge.
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof PrismographBlockEntity be) {
            be.setOwner(placer.getUUID()); // render client for redstone-fired captures
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrismographBlockEntity(pos, state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack drop = new ItemStack(BeyondItems.PRISMOGRAPH.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PrismographBlockEntity be && !be.isEmpty()) {
            drop.set(DataComponents.CONTAINER, be.toContainerContents());
        }
        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(BeyondItems.PRISMOGRAPH.get());
        if (level.getBlockEntity(pos) instanceof PrismographBlockEntity be && !be.isEmpty()) {
            stack.set(DataComponents.CONTAINER, be.toContainerContents());
        }
        return stack;
    }

    /** Creative break skips loot, so drop the loaded camera by hand. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()
                && level.getBlockEntity(pos) instanceof PrismographBlockEntity be && !be.isEmpty()) {
            ItemStack drop = new ItemStack(BeyondItems.PRISMOGRAPH.get());
            drop.set(DataComponents.CONTAINER, be.toContainerContents());
            ItemEntity ent = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            ent.setDefaultPickUpDelay();
            level.addFreshEntity(ent);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        boolean signal = level.hasNeighborSignal(pos);
        if (signal == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
        if (signal) {
            // Jitter so N cameras on one clock spread across ticks.
            int delay = 2 + (int) Math.floorMod(pos.asLong(), 4);
            level.scheduleTick(pos, this, delay);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        fireCapture(level, pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PrismographBlockEntity be) {
            level.scheduleTick(pos, this, 2);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(BeyondTags.PRISMOGRAPH_FILM)) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof PrismographBlockEntity be) {
                ItemStack film = be.getItem(0);

                if (film.isEmpty()) {
                    int toMove = Math.min(stack.getCount(), stack.getMaxStackSize());
                    be.setItem(0, stack.copyWithCount(toMove));
                    if (!player.getAbilities().instabuild) stack.shrink(toMove);
                    be.setChanged();
                    return ItemInteractionResult.sidedSuccess(false);
                }

                if (ItemStack.isSameItemSameComponents(film, stack)) {
                    int space = film.getMaxStackSize() - film.getCount();
                    if (space > 0) {
                        int toMove = Math.min(space, stack.getCount());
                        film.grow(toMove);
                        if (!player.getAbilities().instabuild) stack.shrink(toMove);
                        be.setChanged();
                    }
                    return ItemInteractionResult.sidedSuccess(false);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private static void fireCapture(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof PrismographBlockEntity be)) {
            return;
        }
        long now = level.getGameTime();
        long last = be.getLastCaptureTick();
        // MIN_VALUE means never captured, and skipping it avoids the now - MIN_VALUE overflow
        if (last != Long.MIN_VALUE && now - last < CAPTURE_COOLDOWN) {
            return;
        }
        if (!be.hasFilm()) {
            level.playSound(null, pos, BeyondSoundEvents.PRISMOGRAPH_EMPTY.value(), SoundSource.BLOCKS, 0.5f, 1f);
            return;
        }
        // fuel goes here once wired: return on !be.hasFuel() with a dry-fire sound, else be.consumeFuel()
        ServerPlayer client = pickRenderClient(level, pos, be.getOwner());
        if (client == null) {
            return;
        }
        be.setLastCaptureTick(now);
        be.consumeFilm();
        // inside a Sable sub-level these hooks map the block POV into the moving frame, elsewhere they do nothing
        Vec3 storedForward = Vec3.atLowerCornerOf(state.getValue(FACING).getNormal());
        Vec3 visForward = BeyondCompatHooks.toVisibleDir(level, pos, storedForward);
        Vec3 forward = visForward != null ? visForward.normalize() : storedForward;
        Vec3 eye = BeyondCompatHooks.visibleOrCenter(level, pos).add(forward.scale(0.5));
        // placed cameras stamp the default look, CAMERA_GRADE belongs to the handheld only
        long requestId = SnapshotRequests.issue(client, pos, Grades.NONE);
        PacketDistributor.sendToPlayer(client,
                new BlockCameraRenderRequestPayload(requestId, eye, forward));
        level.playSound(null, pos, BeyondSoundEvents.PRISMOGRAPH_SNAP.value(), SoundSource.BLOCKS, 0.7f, 1f);
    }

    private static ServerPlayer pickRenderClient(ServerLevel level, BlockPos pos, UUID owner) {
        double maxSq = 160.0 * 160.0; // client must have the POV chunks loaded
        Vec3 c = Vec3.atCenterOf(pos);
        if (owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer ownerP
                && ownerP.distanceToSqr(c) <= maxSq) {
            return ownerP;
        }
        ServerPlayer nearest = null;
        double best = maxSq;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(c);
            if (d <= best) {
                best = d;
                nearest = p;
            }
        }
        return nearest;
    }
}
