package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.common.block.blockentities.CameraBlockEntity;
import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.common.network.BlockCameraRenderRequestPayload;
import com.thebeyond.common.camera.Grades;
import com.thebeyond.common.camera.SnapshotRequests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

/** Capture is deferred via {@link Level#scheduleTick} to run on the tick thread (clip force-generates chunks). */
public class CameraBlock extends BaseEntityBlock {
    public static final MapCodec<CameraBlock> CODEC = simpleCodec(CameraBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED; // prior signal, for rising-edge detection

    private static final int CAPTURE_COOLDOWN = 10;

    public CameraBlock(Properties properties) {
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
        // Seed POWERED so placing into a powered cell isn't read as a rising edge.
        return this.defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof CameraBlockEntity be) {
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
        return new CameraBlockEntity(pos, state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack drop = new ItemStack(BeyondItems.PINHOLE_CAMERA.get());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CameraBlockEntity be && !be.isEmpty()) {
            drop.set(DataComponents.CONTAINER, be.toContainerContents());
        }
        return List.of(drop);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(BeyondItems.PINHOLE_CAMERA.get());
        if (level.getBlockEntity(pos) instanceof CameraBlockEntity be && !be.isEmpty()) {
            stack.set(DataComponents.CONTAINER, be.toContainerContents());
        }
        return stack;
    }

    /** Creative break skips loot, so drop the loaded camera by hand. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()
                && level.getBlockEntity(pos) instanceof CameraBlockEntity be && !be.isEmpty()) {
            ItemStack drop = new ItemStack(BeyondItems.PINHOLE_CAMERA.get());
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
        // GUI only; photos fire on a redstone rising edge, never from this interaction.
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CameraBlockEntity be) {
            player.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void fireCapture(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof CameraBlockEntity be)) {
            return;
        }
        long now = level.getGameTime();
        long last = be.getLastCaptureTick();
        // MIN_VALUE = never captured; also dodges now - MIN_VALUE overflow.
        if (last != Long.MIN_VALUE && now - last < CAPTURE_COOLDOWN) {
            return;
        }
        if (!be.hasFilm()) {
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5f, 0.6f);
            return;
        }
        // FUEL SCAFFOLD (inactive): when fuel is wired, gate + spend it here too, e.g.:
        //   if (!be.hasFuel()) { level.playSound(...dry-fire...); return; }
        //   be.consumeFuel();
        ServerPlayer client = pickRenderClient(level, pos, be.getOwner());
        if (client == null) {
            return;
        }
        be.setLastCaptureTick(now);
        be.consumeFilm();
        // In a Sable sub-level these hooks remap the block-frame POV into the visible moving frame; outside one they no-op.
        Vec3 storedForward = Vec3.atLowerCornerOf(state.getValue(FACING).getNormal());
        Vec3 visForward = BeyondCompatHooks.toVisibleDir(level, pos, storedForward);
        Vec3 forward = visForward != null ? visForward.normalize() : storedForward;
        Vec3 eye = BeyondCompatHooks.visibleOrCenter(level, pos).add(forward.scale(0.5));
        // Placed cameras stamp the default look; CAMERA_GRADE is a handheld-only concept.
        long requestId = SnapshotRequests.issue(client, pos, Grades.SEPIA);
        PacketDistributor.sendToPlayer(client,
                new BlockCameraRenderRequestPayload(requestId, eye, forward));
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.7f, 1.2f);
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
