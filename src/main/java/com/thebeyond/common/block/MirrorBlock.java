package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.block.blockentities.MirrorBlockEntity;
import com.thebeyond.common.entity.AbyssalNomadEntity;
import com.thebeyond.common.entity.BaubleEntity;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Face reflectivity survives breaking via the block's copy_state loot table. */
public class MirrorBlock extends BaseEntityBlock {
    public static final MapCodec<MirrorBlock> CODEC = simpleCodec(MirrorBlock::new);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public static final Map<Direction, BooleanProperty> FACE_PROPERTIES = Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
            Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN);

    public MirrorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (!level.getBlockState(pos.below()).is(BeyondBlocks.GELLID_VOID.get())) return;
        if (random.nextBoolean()) return;

        AABB detectionBox = new AABB(pos).inflate(5);
        List<BaubleEntity> entities = level.getEntitiesOfClass(BaubleEntity.class, detectionBox);
        if (entities.size() < 5) {
            Iterable<BlockPos> positions = BlockPos.randomBetweenClosed(random, 2, pos.getX()-4, pos.getY(), pos.getZ()-4,pos.getX()+4, pos.getY(), pos.getZ()+4);
            for (BlockPos blockPos : positions) {
                if (!level.getBlockState(blockPos).isAir() && level.getBlockState(blockPos.above()).isAir()) {
                    BaubleEntity bauble = new BaubleEntity(BeyondEntityTypes.BAUBLE.get(), level);
                    bauble.setPos(blockPos.above().getCenter());
                    level.addFreshEntity(bauble);
                    level.sendParticles(BeyondParticleTypes.WIND.get(), blockPos.above().getX()+0.5f,blockPos.above().getY()+0.5f,blockPos.above().getZ()+0.5f,5,0.0,0.0,0.0,0.02);
                }
            }
        }
    }

    // Queried per mirror per frame by the reflection pass; at most 64 states, and BlockStates are interned.
    private static final Map<BlockState, List<Direction>> FACE_CACHE = new ConcurrentHashMap<>();

    public static List<Direction> reflectiveFaces(BlockState state) {
        return FACE_CACHE.computeIfAbsent(state, s -> {
            List<Direction> faces = new ArrayList<>(4);
            for (Map.Entry<Direction, BooleanProperty> e : FACE_PROPERTIES.entrySet()) {
                if (s.getValue(e.getValue())) {
                    faces.add(e.getKey());
                }
            }
            return List.copyOf(faces);
        });
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = context.getNearestLookingDirection().getOpposite();
        return this.defaultBlockState().setValue(FACE_PROPERTIES.get(toward), true);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof AxeItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        BooleanProperty face = FACE_PROPERTIES.get(hit.getDirection());
        if (face == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(face), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MirrorBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        switch (rot) {
            case CLOCKWISE_180 -> {
                return (((state.setValue(NORTH, state.getValue(SOUTH))).setValue(EAST, state.getValue(WEST))).setValue(SOUTH, state.getValue(NORTH))).setValue(WEST, state.getValue(EAST));
            }
            case COUNTERCLOCKWISE_90 -> {
                return (((state.setValue(NORTH, state.getValue(EAST))).setValue(EAST, state.getValue(SOUTH))).setValue(SOUTH, state.getValue(WEST))).setValue(WEST, state.getValue(NORTH));
            }
            case CLOCKWISE_90 -> {
                return (((state.setValue(NORTH, state.getValue(WEST))).setValue(EAST, state.getValue(NORTH))).setValue(SOUTH, state.getValue(EAST))).setValue(WEST, state.getValue(SOUTH));
            }
            default -> {
                return state;
            }
        }
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT -> {
                return (state.setValue(NORTH, state.getValue(SOUTH))).setValue(SOUTH, state.getValue(NORTH));
            }
            case FRONT_BACK -> {
                return (state.setValue(EAST, state.getValue(WEST))).setValue(WEST, state.getValue(EAST));
            }
            default -> {
                return super.mirror(state, mirror);
            }
        }
    }
}
