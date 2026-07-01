package com.thebeyond.common.block;

import com.mojang.serialization.MapCodec;
import com.thebeyond.api.compat.BeyondCompatHooks;
import com.thebeyond.client.particle.BellowJetOptions;
import com.thebeyond.common.block.blockentities.BellowBlockEntity;
import com.thebeyond.common.registry.BeyondBlockEntities;
import com.thebeyond.common.registry.BeyondParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BellowBlock extends BaseEntityBlock {
    public static final MapCodec<BellowBlock> CODEC = simpleCodec(BellowBlock::new);
    public static final int MAX_HEIGHT = 5;             // max reach, in blocks
    private static final int MAX_LEVEL = 15;            // STRENGTH resolution = redstone range
    private static final int LEVELS_PER_BLOCK = MAX_LEVEL / MAX_HEIGHT; // 3 sub-steps per block

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    /** 0 = off; 1..15 = wall-capped redstone level, synced so the client jet matches. reach = STRENGTH / 3 blocks. */
    public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 0, MAX_LEVEL);

    // 10x10x16, long axis along the facing direction
    private static final VoxelShape SHAPE_Y = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape SHAPE_Z = Block.box(3, 3, 0, 13, 13, 16);
    private static final VoxelShape SHAPE_X = Block.box(0, 3, 3, 16, 13, 13);

    private static final double GUST_RADIUS = 0.5;
    private static final double BASE_SPEED = 0.7;
    private static final double VERTICAL_BOOST = 1.6; // up/down fights gravity
    private static final double ACCEL = 0.2;          // per-tick velocity cap toward the gust speed
    private static final double SETTLE = 0.6;         // eases the push toward terminal speed (lower = gentler)
    private static final double MIN_FALLOFF = 0.15;   // residual push at the far edge
    private static final int STEM_COUNT = 3;
    private static final double STEM_RADIUS = 0.1;
    private static final double FLOW_SPEED = 0.3;     // constant; the puff lifetime sets the reach
    private static final int SPLASH_COUNT = 3;
    private static final int SPLASH_LIFETIME = 7;
    private static final double SPLASH_OUT = 0.13;
    private static final double SPLASH_BACK = 0.04;

    public BellowBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STRENGTH, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STRENGTH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
    }

    /** Breaks unless backed by a sturdy block opposite the facing. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BellowBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BeyondBlockEntities.BELLOW.get(),
                level.isClientSide ? BellowBlockEntity::clientTick : BellowBlockEntity::serverTick);
    }

    /** Redstone input: the block's own signal, plus a copper-bulb support's signal when mounted on one. */
    public static int inputSignal(Level level, BlockPos pos, BlockState state) {
        int direct = level.getBestNeighborSignal(pos);
        if (direct >= 15) {
            return 15;
        }
        BlockPos support = pos.relative(state.getValue(FACING).getOpposite());
        if (level.getBlockState(support).getBlock() instanceof CopperBulbBlock) {
            return Math.max(direct, level.getBestNeighborSignal(support));
        }
        return direct;
    }

    /** Synced level 0..15: the redstone signal capped by the open blocks ahead; 0 when off or blocked at the nozzle. */
    public static int computeLevel(Level level, BlockPos pos, BlockState state, int signal) {
        if (signal <= 0) {
            return 0;
        }
        return Math.min(signal, effectiveReach(level, pos, state.getValue(FACING)) * LEVELS_PER_BLOCK);
    }

    /** Continuous reach in blocks for a synced level, scaling smoothly with the redstone (1/3-block steps). */
    private static double reachBlocks(int strength) {
        return (double) strength / LEVELS_PER_BLOCK;
    }

    /** Server: pushes entities in the gust toward the facing; force scales with the signal and fades over the reach. */
    public static void serverPush(ServerLevel level, BlockPos pos, BlockState state, int signal, int strength) {
        Direction dir = state.getValue(FACING);
        Vec3 origin = BeyondCompatHooks.visibleOrCenter(level, pos);
        Vec3 worldDir = worldDir(level, pos, dir);
        double reach = reachBlocks(strength);
        Vec3 end = origin.add(worldDir.scale(reach + 0.5));
        AABB box = new AABB(origin, end).inflate(GUST_RADIUS + 0.5);
        boolean vertical = dir.getAxis().isVertical();
        double maxSpeed = BASE_SPEED * (vertical ? VERTICAL_BOOST : 1.0) * (signal / 15.0);

        for (Entity e : level.getEntitiesOfClass(Entity.class, box, EntitySelector.NO_SPECTATORS)) {
            // measured from the feet, so tall entities still lift to the full reach
            double along = e.position().subtract(origin).dot(worldDir);
            if (along < 0 || along > reach + 0.5) {
                continue;
            }
            Vec3 c = e.getBoundingBox().getCenter().subtract(origin);
            double radial = c.subtract(worldDir.scale(c.dot(worldDir))).length();
            if (radial > GUST_RADIUS + e.getBbWidth() * 0.5) {
                continue;
            }
            double falloff = Math.max(MIN_FALLOFF, 1.0 - along / (reach + 0.5));
            double target = maxSpeed * falloff;
            Vec3 v = e.getDeltaMovement();
            double curAlong = v.dot(worldDir);
            if (curAlong < target) {
                double step = Math.min(ACCEL, (target - curAlong) * SETTLE);
                e.setDeltaMovement(v.add(worldDir.scale(step)));
                e.hurtMarked = true;
                if (dir == Direction.UP) {
                    e.resetFallDistance(); // an updraft shouldn't bank fall damage
                }
            }
        }
    }

    /** Client: a constant-speed smoke jet sized to the reach via the puff lifetime; no network cost. */
    public static void clientJet(Level level, BlockPos pos, BlockState state, int strength) {
        Direction dir = state.getValue(FACING);
        Vec3 visible = BeyondCompatHooks.visibleOnAnyLevel(level, pos);
        Vec3 origin = visible != null ? visible : Vec3.atCenterOf(pos);
        Vec3 wdir = worldDir(level, pos, dir);
        double reach = reachBlocks(strength);
        Vec3 ref = Math.abs(wdir.y) > 0.5 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = wdir.cross(ref).normalize();
        Vec3 w = wdir.cross(u).normalize();
        // run the puffs into a blocking wall (hidden by its face) so the smoke visually reaches it
        BlockPos ahead = pos.relative(dir, Mth.floor(reach) + 1);
        boolean blocked = level.getBlockState(ahead).isSolidRender(level, ahead);
        double tipDist = blocked ? reach + 1.0 : reach + 0.5;
        int lifetime = Math.max(2, (int) Math.round((tipDist - 0.6) / FLOW_SPEED));
        BellowJetOptions options = new BellowJetOptions(lifetime);
        Vec3 nozzle = origin.add(wdir.scale(0.6));
        RandomSource random = level.random;
        for (int n = 0; n < STEM_COUNT; n++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double pr = random.nextDouble() * STEM_RADIUS;
            Vec3 p = nozzle.add(u.scale(Math.cos(ang) * pr)).add(w.scale(Math.sin(ang) * pr));
            level.addParticle(options, p.x, p.y, p.z, wdir.x * FLOW_SPEED, wdir.y * FLOW_SPEED, wdir.z * FLOW_SPEED);
        }
        if (blocked) {
            // splash off the wall: fresh puffs at the impact face spreading outward, with a slight bounce back
            Vec3 face = origin.add(wdir.scale(reach + 0.5));
            BellowJetOptions splash = new BellowJetOptions(SPLASH_LIFETIME);
            for (int n = 0; n < SPLASH_COUNT; n++) {
                double ang = random.nextDouble() * Math.PI * 2.0;
                Vec3 radial = u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang)));
                Vec3 sp = face.subtract(wdir.scale(0.1)).add(radial.scale(random.nextDouble() * 0.15));
                Vec3 vel = radial.scale(SPLASH_OUT).subtract(wdir.scale(SPLASH_BACK));
                level.addParticle(splash, sp.x, sp.y, sp.z, vel.x, vel.y, vel.z);
            }
        }
    }

    /** Facing axis in world space: the plain axis in the host level, pose-rotated inside a Sable sub-level. */
    private static Vec3 worldDir(Level level, BlockPos pos, Direction dir) {
        Vec3 axis = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        Vec3 rotated = BeyondCompatHooks.toVisibleDir(level, pos, axis);
        return (rotated == null || rotated.lengthSqr() < 1.0e-6) ? axis : rotated.normalize();
    }

    /** Open blocks the gust travels in {@code dir} before a solid block stops it, capped at {@link #MAX_HEIGHT}. */
    private static int effectiveReach(Level level, BlockPos pos, Direction dir) {
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        int reach = 0;
        for (int i = 1; i <= MAX_HEIGHT; i++) {
            cur.set(pos.getX() + dir.getStepX() * i, pos.getY() + dir.getStepY() * i, pos.getZ() + dir.getStepZ() * i);
            if (level.getBlockState(cur).isSolidRender(level, cur)) {
                break;
            }
            reach = i;
        }
        return reach;
    }
}
