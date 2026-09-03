package com.thebeyond.common.entity.util.livingblock;

import com.thebeyond.common.registry.BeyondEntityDataSerializers;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.common.entity.util.livingblock.movement.*;
import com.thebeyond.common.entity.util.livingblock.LivingBlockShapeFactory;
import com.thebeyond.util.MathHelpers;

import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.slf4j.Logger;

import javax.annotation.Nullable;

public class LivingBlock extends Mob {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean shouldLog = false;

    protected static final EntityDataAccessor<MovementData> MOVEMENT_DATA = SynchedEntityData.defineId(LivingBlock.class, BeyondEntityDataSerializers.MOVEMENT_DATA.get());
    protected static final EntityDataAccessor<Target> MOVEMENT_TARGET = SynchedEntityData.defineId(LivingBlock.class, BeyondEntityDataSerializers.TARGET.get());
    protected static final EntityDataAccessor<Direction> DATA_CLIMBING_DIRECTION = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.DIRECTION);
    protected static final EntityDataAccessor<Integer> DATA_SHAPE_SEED = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_FEATURE_SEED = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_GROWTH = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Byte> DATA_ORIENTATION = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Boolean> DATA_ORIENTATION_SETTLED = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Quaternionf> DATA_ROTATION = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.QUATERNION);
    protected static final EntityDataAccessor<Vector3f> DATA_CLIMB_PIVOT_LOCAL = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Vector3f> DATA_CLIMB_PIVOT_WORLD = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Boolean> DATA_CLIMB_PIVOT_ACTIVE = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Boolean> DATA_STEP_PHASE = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Boolean> DATA_HULL_ORIENTED = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.BOOLEAN);

    protected static final EntityDataAccessor<Integer> DATA_MOVEMENT_TYPE = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.INT);

    private static final double SETTLE_ANGLE_EPSILON = LivingBlockPivot.SETTLE_ANGLE_EPSILON;
    private static final double SETTLE_FIT_EPSILON = 1.0E-4;
    private static final double SETTLE_SPEED_EPSILON_SQR = 1.0E-6;

    private static final double SETTLED_POSE_DEGREES = 0.01;
    private static final double SETTLED_SNAP_DEGREES = 0.5;
    private static final double MIN_SNAP_SPAN_DEGREES = 1.0E-4;

    private static final double UNSETTLE_ANGLE = LivingBlockPivot.UNSETTLE_ANGLE_EPSILON;
    private static final double UNSETTLE_SPEED_SQR = 4.0E-4;
    private static final float CLIENT_ROTATION_CATCHUP = 0.5F;
    private static final double WALL_PROBE_SIZE = 0.2;
    private static final int SEPARATION_IDLE_BACKOFF = 3;
    private static final int NUDGE_HYSTERESIS_TICKS = 60;
    private static final int NUDGE_MIN_TICKS = 6;
    private static final int SOLVER_BLOCKED_TO_NUDGE = 5;
    private static final double SPIN_LOG_DEGREES = 20.0;
    private static final int SPIN_LOG_INTERVAL = 20;
    private static final double TRACE_OVERLAP = 0.06;
    private static final double TRACE_BURIED = 1.0E-3;
    private static final int TRACE_AIR_TICKS = 3;
    private static final double GRID_RECENTRE_MIN = 1.0E-6;

    private static final double GRID_RECENTRE_MAX = 0.125;

    private static final double TRACE_TILT_DEGREES = 5.0;
    private static final double TRACE_STILL_SQR = 1.0E-6;
    private static final int TRACE_DENSE_TICKS = 40;
    private static final int TRACE_SPARSE_INTERVAL = 10;
    private static final double SEPARATION_STILL_EPSILON_SQR = 1.0E-8;
    private int rollBlocked;
    private int rollAirTicks;
    private static final int ROLL_AIR_GRACE = 2;
    private static final float AIR_DRAG_ROLL = 0.5F;
    private static final int VBLOCK_LOG_INTERVAL = 10;
    private int vblockLogTick = -VBLOCK_LOG_INTERVAL;
    private static final int ENTZERO_MIN_RUN = 3;
    private static final int ENTZERO_LONG_RUN = 20;
    private static final int ENTZERO_QUIET_TICKS = 20;
    private int entityZeroRun;
    private int entityZeroQuietUntil;
    private boolean entityZeroActive;
    private static final double ROT_OVERLAP_DELTA = 0.02;
    private static final double ROT_OVERLAP_BUDGET = 0.01;
    private static final double ROLL_GATE_BUDGET = 0.005;
    private static final int ROLL_GATE_PASSES = 2;
    private static final double ROLL_UNWIND_SCALE = 0.25;
    private static final double ROLL_UNWIND_TILT = 0.14;
    private static final float[] ARC_SAMPLES = {0.34F, 0.67F, 1.0F};
    private static final double ARC_CLEAR_BUDGET = 1.0E-3;
    private static final double ARC_PROBE_REACH = 1.5;
    private int arcRefusals;
    private int terrainRefinements;
    private static final double ROTATION_SYNC_EPSILON = 0.05;
    private final Quaternionf syncedRotation = new Quaternionf();
    private boolean hasSyncedRotation;
    private static final float ROLL_GATE_EPSILON = 1.0E-5F;
    private static final double ROLL_GATE_MAX_REACH = 1.0;
    private static final int ROLL_GATE_SUM_INTERVAL = 100;
    private int rollGateBites;
    private int rollGateLogTick = -2;
    private int rollGateSumTick = -ROLL_GATE_SUM_INTERVAL;
    private double rollGateWorstRaw;
    private double rollGateWorstGain;
    private int slideLogTick = -2;
    private int rotClampLogTick = -2;
    private int quietLoggedState = -1;
    private int quietLogTick = -QUIET_LOG_INTERVAL;
    private static final int QUIET_LOG_INTERVAL = 10;
    private static final double PIVOT_LOG_DEGREE_BUCKET = 30.0;
    private static final int ROT_LOG_INTERVAL = 20;
    private static final int QTURN_LOG_INTERVAL = 20;
    private static final int QTURN_REST = 0;
    private static final int QTURN_AXIS = 1;
    private int qturnLogTick = -QTURN_LOG_INTERVAL;
    private int rollAxisFlips;

    private static final MovementStrategy<?> MV_POGO = new BouncingMovement();
    private static final MovementStrategy<?> MV_NUDGE = new BouncingMovement(0.9, 8, 0.4, false);
    private static final MovementStrategy<?> MV_DRIFT = new FloatingMovement();
    private static final MovementStrategy<?> MV_ROLL_FLOAT = new FluidFloatingMovement();

    private static final double AGGRO_RANGE = 24.0;

    private static final int GROWTH_TICK_CHANCE = 750;

    protected final Quaternionf lastRotation = new Quaternionf();
    protected final Quaternionf drawnFrom = new Quaternionf();
    protected final Quaternionf rotation = new Quaternionf();

    protected float rollDeltaX;
    protected float rollDeltaZ;
    protected float rollCarryX;
    protected float rollCarryZ;
    protected int rollPhaseAxis = LivingBlockRoll.AXIS_NONE;
    protected double rollPhase;
    protected int rollPendingAxis = LivingBlockRoll.AXIS_NONE;
    protected double rollPending;
    protected int rollFallbacks;
    protected int rollRestores;
    protected int orientationRejects;
    protected boolean pendingOrientationRestore;
    protected int nudgeStart;
    protected int rollSoundTime;

    private static final int ROLL_SOUND_MIN_TIME = 4;
    private static final double ROLL_SOUND_MIN_ANGLE = 10.0;
    private static final float ROLL_ROTATION_DELTA_EPSILON = 1.0E-5F;

    private static final float FLOP_VOLUME = 1.0F;
    private static final float FLOP_PITCH = 0.75F;
    private static final float FLOP_MIN_CONTACT = 0.35F;
    private static final float FLOP_PITCH_SPREAD = 0.3F;
    private static final double DRAG_TUMBLE_SPEED_SQR = 0.01;
    private static final double TUMBLE_MIN_SPEED = 0.02;
    private static final float TUMBLE_PIVOT_BIAS = 0.15F;
    private static final double TUMBLE_LIFT_CAP = 0.45;
    private static final double TUMBLE_MAX_SLOPE = 2.0;
    private static final double TUMBLE_MIN_LIFT = 0.08;
    private static final double TUMBLE_LAUNCH_MARGIN = 1.0;
    private static final double IMPACT_SQUASH_MIN_SPEED = 0.1;
    private static final double IMPACT_SQUASH_RANGE = 0.5;
    private static final double IMPACT_SQUASH_MAX = 0.35;
    private static final int IMPACT_SQUASH_TICKS = 2;
    private static final int IMPACT_RECOVER_TICKS = 4;

    private static final double DEPEN_EPSILON = 1.0E-6;
    private static final double CLIMB_PIVOT_REACH = 0.12;
    private static final double CLIMB_PIVOT_CONTACT = 2.0E-3;
    private static final double CLIMB_APPROACH_SPEED = 0.05;
    private static final double CLIMB_PIVOT_SLOP = 1.0E-3;
    private static final int CLIMB_APPROACH_STUCK = 4;
    private static final double CLIMB_ALIGN_MIN_TILT = 0.05;
    private static final double CLIMB_TOP_SETTLE_DEGREES = 0.25;
    private static final int CLIMB_PIVOT_STEPS = 8;
    private static final double DEPEN_MAX = 0.05;
    public static final float LOW_STEP_HEIGHT = (float)LivingBlockStep.HEIGHT;
    public static final float LOW_STEP_SLACK = (float)LivingBlockStep.SLACK;
    private final Vector3f lastEscape = new Vector3f();
    private static final boolean FEATURE_GEOMETRY = false;
    private boolean axisAlignedLogged;
    private boolean rollUngatedLogged;
    private boolean arcVetoSkipLogged;
    private boolean shapeGeometryLogged;

    private MovementStrategy<?> movement = MV_ROLL_FLOAT;
    private float maxUpStep = 0.6F;
    private float rollAngle;
    private double traceSpin;
    private double traceMoved;
    private final Quaternionf tickStartRotation = new Quaternionf();
    private final Map<Integer, Long> riderHold = new HashMap<>();
    private long riderHoldUntil = Long.MIN_VALUE;
    private int floorBodyLogged = -1;
    private boolean riderHoldLogged;
    private LivingBlockPivot.WallPivot climbPivot;
    private Direction climbPivotDirection = Direction.DOWN;
    private int climbPivotStep;
    private int climbPivotLimit;
    private double climbPivotDegreesRemaining;
    private double climbPivotDegreesTotal;
    private boolean climbPivotCompletes;
    private boolean climbPivotSequence;
    private boolean climbPivotAligning;
    private final Quaternionf climbPivotAlignmentTarget = new Quaternionf();
    private boolean climbPivotMovedThisTick;

    private static final double FLOOR_LIFT_MAX = 0.05;
    private static final int LANDING_WATCH_TICKS = 25;
    private int landingWatch;
    private int landingTick;
    @Nullable private Direction landingDir;

    private boolean vanillaOrderThisTick;

    private String recentreVerdict = "";

    private String pivotPhaseLast = "";
    private int climbPivotEdgeStartTick;
    private int climbPivotLastAdvanceTick = -1;
    private int climbPivotMaxAdvanceGap;
    private double climbPivotRuntimeDrift;
    private Vec3 climbPivotExpectedPosition = Vec3.ZERO;
    private int climbPivotVisualReleaseTick = -1;
    private int climbPivotLogTick = -ROT_LOG_INTERVAL;
    private int climbApproachTicks;
    private int tipLogTick = Integer.MIN_VALUE;
    private int stepWhyTick = Integer.MIN_VALUE;
    private String stepWhyLast = "";
    private boolean tipLastSeated;
    private DescentLook descentHeldLook;
    private boolean descentSequence;
    private boolean descentQuarterOnly;
    private int quarterReleaseTick = -1000;
    private boolean stepLifting;
    private static final double STEP_PHASE_RATE = 0.5 / 3.0;
    private static final double STEP_APPROACH_RATE = 0.10;
    private boolean stepApproaching;
    private double stepTarget;
    private double stepSeatedY = Double.NaN;
    private static final int QUARTER_HOLD_TICKS = 2;
    private int stepHold;
    private boolean stepHolding;
    private boolean stepHoldToArc;
    private boolean stepDropping;

    private static final double LOW_STEP_DEGREES = 90.0 / 5.0;
    private LivingBlockPivot.WallSurface descentWall;
    private Direction descentWallDir = Direction.DOWN;
    private int descentWallTick = Integer.MIN_VALUE;
    private int descentEndTick = Integer.MIN_VALUE;
    private int descentApproachTicks;
    private Vec3 descentApproachFrom = Vec3.ZERO;
    private Direction descentEndDir = Direction.DOWN;
    private static final int DESCENT_SETTLE_TICKS = 20;
    private int descentDumpTick = Integer.MIN_VALUE;
    private int descentRouteTick = Integer.MIN_VALUE;
    private Vec3 descentWritePos = Vec3.ZERO;
    private final Quaternionf descentWriteRot = new Quaternionf();
    private String descentRouteLast = "";
    private String descentDumpVerdict = "";
    private Direction descentHeldDir = Direction.DOWN;
    private int descentHeldTick = Integer.MIN_VALUE;
    private int climbFlowTick = Integer.MIN_VALUE;
    private double climbFlowY;
    private int climbFlowRoutes;
    private String climbFlowLastRoute = "-";
    private boolean climbFlowOrdered;
    private int climbWhyTick = Integer.MIN_VALUE;
    private String climbWhyLast = "";
    private final Map<String, Integer> beadWhyTicks = new HashMap<>();
    private int climbPivotAnchorId = -1;
    private @Nullable Vec3 climbPivotAnchorLocal;
    private boolean climbPivotStalled;
    private boolean descentWallBody;
    private Vec3 climbApproachFrom = Vec3.ZERO;
    private Vec3 traceIn = Vec3.ZERO;
    private Vec3 traceWorld = Vec3.ZERO;
    private Vec3 traceFull = Vec3.ZERO;
    private Vec3 traceEscape = Vec3.ZERO;
    private Vec3 tracePush = Vec3.ZERO;
    private Vec3 traceApplied = Vec3.ZERO;
    private double traceStepRise;
    private double traceBuried;
    private double traceOverlap;
    private int traceWith = -1;
    private int traceRim = -1;
    private int traceBoxes;
    private int traceBlocks;
    private int traceColliders;
    private int traceTerrain;
    private int airTicks;
    private int troubleTicks;
    private int nudgeUntil = -1;
    private int solverBlockedTicks;
    private Vec3 pogoScaleTarget = new Vec3(1.0, 1.0, 1.0);
    private Vec3 lastPogoScaleTarget = new Vec3(1.0, 1.0, 1.0);
    private Vec3 currentPogoScale = new Vec3(1.0, 1.0, 1.0);
    private int pogoScaleTicksRemaining = 0;
    private int pogoScaleTicks = 0;
    private VoxelShape customShape = Shapes.block();
    private AABB shapeBounds;
    private AABB centredShapeBounds;
    private List<AABB> shapeBoxes;
    private AABB baseShapeBounds;
    private List<AABB> baseShapeBoxes;
    protected List<TrinketGrowth.Feature> featurePlan;
    private int featurePlanSeed;
    private final RandomSource growthRandom = RandomSource.create();
    private LivingBlockCollisionShapes collisionShapes;
    private LivingBlockCollisionShapes.Placement livePlacement;
    private Quaternionf livePlacementRotation;
    private Vec3 livePlacementPosition;
    private Quaternionf silhouetteRotation;
    private AABB silhouetteLocal;
    private double shapeCenterX;
    private double shapeCenterZ;
    private LivingBlockOrientation orientation;
    protected boolean rotationRestored;
    private int separationBackoff;
    private long carriedAtTick = Long.MIN_VALUE;
    private boolean shapeIsEntropic;
    private boolean pendingFlop;
    private boolean wasGrounded;
    private boolean tumbleArmed;
    private boolean tumbleAirborne;
    private double previousFallSpeed;
    private int squashRecoverTicks;

    public LivingBlock(final EntityType<? extends Mob>  type, final Level level) {
        super(type, level);
        this.setMovementData(this.movement.initData());
        this.noPhysics = false;
    }

    public VoxelShape getCustomShape() {
        return this.customShape;
    }
    public int getFeaturePlanSeed() { return this.entityData.get(DATA_FEATURE_SEED);}
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        this.assignShapeSeed();
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    public boolean isEntropicForm() {
        return this.getType().is(BeyondTags.ENTROPIC_FORM);
    }
    public int getGrowthStage() {
        return this.entityData.get(DATA_GROWTH);
    }

    private void assignShapeSeed() {
        if (this.entityData.get(DATA_FEATURE_SEED) == 0) {
            this.entityData.set(DATA_FEATURE_SEED, this.growthRandom.nextInt() | 1);
        }
        if (this.entityData.get(DATA_SHAPE_SEED) == 0) {
            this.entityData.set(DATA_SHAPE_SEED, this.growthRandom.nextInt() | 1);
        }
    }

    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        return LivingBlockShapeFactory.create(random, entropic);
    }

    protected void applyShape() {
        this.shapeIsEntropic = this.isEntropicForm();
        this.setShape(this.generateShape(
                RandomSource.create(this.entityData.get(DATA_SHAPE_SEED)), this.shapeIsEntropic));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    public void setShape(VoxelShape shape) {
        this.customShape = shape;
        this.baseShapeBounds = null;
        //this.featurePlan = null;
        this.cacheShapeGeometry();
        this.refreshDimensions();
    }

    private void cacheShapeGeometry() {
        VoxelShape shape = this.customShape != null ? this.customShape : Shapes.block();
        this.baseShapeBounds = shape.bounds();
        this.baseShapeBoxes = List.copyOf(shape.toAabbs());

//        List<AABB> boxes = FEATURE_GEOMETRY
//                ? LivingBlockFeatures.apply(this.baseShapeBoxes, this.featurePlan().features(),
//                        this.entityData.get(DATA_GROWTH))
//                : this.baseShapeBoxes;

        List<AABB> boxes = this.baseShapeBoxes;

        this.silhouetteLocal = null;
        this.livePlacement = null;
        this.shapeBoxes = boxes;
//        this.shapeBounds = boxes == this.baseShapeBoxes
//                ? this.baseShapeBounds
//                : LivingBlockFeatures.union(this.baseShapeBounds, LivingBlockFeatures.bounds(boxes));

        this.shapeBounds = this.baseShapeBounds;

        double centerY = Mth.lerp(0.5, this.baseShapeBounds.minY, this.baseShapeBounds.maxY);
        this.shapeCenterX = Mth.lerp(0.5, this.baseShapeBounds.minX, this.baseShapeBounds.maxX);
        this.shapeCenterZ = Mth.lerp(0.5, this.baseShapeBounds.minZ, this.baseShapeBounds.maxZ);
        this.centredShapeBounds = this.shapeBounds.move(-this.shapeCenterX, -centerY, -this.shapeCenterZ);
        this.collisionShapes = new LivingBlockCollisionShapes(
                this.shapeBoxes, this.shapeBounds, this.shapeCenterX, centerY, this.shapeCenterZ);
    }

    //private List<TrinketGrowth.Feature> featurePlan() {
    //    int seed = this.entityData.get(DATA_FEATURE_SEED);
    //    List<TrinketGrowth.Feature> cached = this.featurePlan;
    //    if (cached != null && this.featurePlanSeed == seed) {
    //        return cached;
    //    }
    //    List<TrinketGrowth.Feature> plan = TrinketGrowth.generate(this.baseShapeBoxes, seed);
    //    this.featurePlan = plan;
    //    this.featurePlanSeed = seed;
    //    return plan;
    //}

    public LivingBlockCollisionShapes.Placement getCollisionGeometry() {
        if (this.collisionShapes == null) {
            this.cacheShapeGeometry();
        }
        this.logShapeGeometry();
        if (!this.orientedHull()) {
            this.logAxisAlignedGeometry();
            return this.collisionShapes.world(this.restingFace(), this.position());
        }
        boolean settled = this.isSettledPose();
        if (settled) {
            return this.collisionShapes.world(this.getOrientation(), this.position());
        }

        Vec3 pos = this.position();
        if (this.livePlacement != null && pos.equals(this.livePlacementPosition)
                && this.livePlacementRotation != null && this.livePlacementRotation.equals(this.rotation)) {
            return this.livePlacement;
        }

        this.livePlacement = LivingBlockCollisionHandler.liveGeometry(this, this.rotation);
        this.livePlacementRotation = new Quaternionf(this.rotation);
        this.livePlacementPosition = pos;
        return this.livePlacement;
    }

    public LivingBlockCollisionShapes.Placement axisAlignedGeometry(final Vec3 at) {
        if (this.collisionShapes == null) {
            this.cacheShapeGeometry();
        }
        return this.collisionShapes.world(this.usesOrientedCollision()
                ? this.getOrientation() : this.restingFace(), at);
    }

    private void logShapeGeometry() {
        if (this.shapeGeometryLogged || this.level().isClientSide()) {
            return;
        }
        this.shapeGeometryLogged = true;
        if (shouldLog) LOGGER.debug("[livingblock] shapegeom id={} type={} features={} oriented={} lowstep={} base={} pieces={}",
                this.getId(), this.getType().toShortString(), FEATURE_GEOMETRY,
                this.usesOrientedCollision(), this.prefersLowStep(),
                this.baseShapeBoxes.size(), this.shapeBoxes.size());
    }

    private void logAxisAlignedGeometry() {
        if (this.axisAlignedLogged || this.level().isClientSide()) {
            return;
        }
        this.axisAlignedLogged = true;
        if (shouldLog) LOGGER.debug("[livingblock] aabbgeom id={} type={} oriented={} tilt={} pieces={}",
                this.getId(), this.getType().toShortString(), this.usesOrientedCollision(),
                String.format("%.2f", this.tiltDegrees()), this.shapeBoxes.size());
    }

    public AABB getShapeBounds() {
        if (this.shapeBounds == null) {
            this.cacheShapeGeometry();
        }
        return this.shapeBounds;
    }

    @Nullable
    public AABB rotatedSilhouette(final Quaternionf source) {
        if (source != this.rotation) {
            return this.buildSilhouette(source);
        }
        if (this.silhouetteLocal == null || this.silhouetteRotation == null
                || !this.silhouetteRotation.equals(source)) {
            AABB built = this.buildSilhouette(source);
            if (built == null) {
                return null;
            }
            this.silhouetteLocal = built;
            this.silhouetteRotation = new Quaternionf(source);
        }
        return this.silhouetteLocal;
    }

    @Nullable
    private AABB buildSilhouette(final Quaternionf source) {
        AABBBuilder builder = new AABBBuilder();
        LivingBlockCollisionHandler.includeRotatedOBBCorners(this, source, builder);
        return builder.isDefined() ? builder.build() : null;
    }

    public AABB getCentredShapeBounds() {
        if (this.centredShapeBounds == null) {
            this.cacheShapeGeometry();
        }
        return this.centredShapeBounds;
    }

    public LivingBlockOrientation getOrientation() {
        LivingBlockOrientation current = this.orientation;
        return current != null ? current : LivingBlockOrientation.IDENTITY;
    }

    public boolean isOrientationSettled() {
        return this.entityData.get(DATA_ORIENTATION_SETTLED);
    }

    private LivingBlockOrientation restingFace() {
        if (this.isOrientationSettled()) {
            return this.getOrientation();
        }
        return this.rotation == null
                ? LivingBlockOrientation.IDENTITY
                : LivingBlockOrientation.fromQuaternion(this.rotation);
    }

    private boolean isSettledPose() {
        return this.isOrientationSettled()
                && angleBetween(this.rotation, this.getOrientation().quaternion())
                        < SETTLED_POSE_DEGREES;
    }

    private void snapSettledPose() {
        if (!this.isOrientationSettled()
                || Math.abs(this.rollPending) >= LivingBlockRoll.RESIDUAL_EPSILON) {
            return;
        }
        LivingBlockOrientation lattice = this.getOrientation();
        if (angleBetween(this.tickStartRotation, this.rotation) < SETTLED_SNAP_DEGREES
                && angularDistance(this.rotation, lattice) < SETTLE_ANGLE_EPSILON) {
            this.rotation.set(lattice.quaternion());
        }
    }

    public List<AABB> getShapeBoxes() {
        if (this.shapeBoxes == null) {
            this.cacheShapeGeometry();
        }
        return this.shapeBoxes;
    }

    public AABB getBaseShapeBounds() {
        if (this.baseShapeBounds == null) {
            this.cacheShapeGeometry();
        }
        return this.baseShapeBounds;
    }

    public List<AABB> getBaseShapeBoxes() {
        if (this.baseShapeBoxes == null) {
            this.cacheShapeGeometry();
        }
        return this.baseShapeBoxes;
    }

    public boolean isBeingDraggedRapidly() {
        if (!this.isLeashed()) {
            return false;
        }
        Entity holder = this.getLeashHolder();
        if (holder == null) {
            return false;
        }
        double distance = this.distanceTo(holder);
        if (distance <= Leashable.LEASH_ELASTIC_DIST || distance > Leashable.LEASH_TOO_FAR_DIST) {
            return false;
        }
        return this.getDeltaMovement().horizontalDistanceSqr() > DRAG_TUMBLE_SPEED_SQR;
    }

    private MovementStrategy<?> desiredMovement() {
        if (this.climbPivotSequence) {
            return MV_ROLL_FLOAT;
        }
        AABB b = this.getBaseShapeBounds();
        double sizeX = b.getXsize();
        double sizeY = b.getYsize();
        double sizeZ = b.getZsize();

        double volume = sizeX * sizeY * sizeZ;
        double maxFootprint = Math.max(sizeX, sizeZ);

        boolean isTiny = volume <= 0.125;
        boolean isFlat = sizeY <= (maxFootprint * 0.5);

        if (this.isLeashed()) {
            Entity holder = this.getLeashHolder();
            if (holder != null) {
                if (!holder.onGround() && !this.onGround()) {
                    if (isTiny) {
                        return MV_DRIFT;
                    }
                }

                if (this.isBeingDraggedRapidly()) {
                    return MV_ROLL_FLOAT;
                }
            }
        }

        if (isFlat) {
            return MV_POGO;
        }
        if (this.usesOrientedCollision() && this.isNudging()) {
            return MV_NUDGE;
        }
        return MV_ROLL_FLOAT;
    }

    public boolean isNudging() {
        return this.nudgeUntil >= 0 && (this.tickCount < this.nudgeUntil || !this.onGround());
    }

    public void reportSolverBlocked(final boolean blocked) {
        if (this.climbPivotSequence) {
            this.solverBlockedTicks = 0;
            return;
        }
        if (!blocked) {
            this.solverBlockedTicks = 0;
            return;
        }
        this.solverBlockedTicks++;
        if (this.solverBlockedTicks >= SOLVER_BLOCKED_TO_NUDGE) {
            this.solverBlockedTicks = 0;
            this.reportBlocked();
        }
    }

    public boolean shouldLogSlide() {
        boolean fresh = this.tickCount - this.slideLogTick > 1;
        this.slideLogTick = this.tickCount;
        return fresh;
    }

    private boolean shouldLogRotClamp() {
        boolean fresh = this.tickCount - this.rotClampLogTick > 1;
        this.rotClampLogTick = this.tickCount;
        return fresh;
    }

    public void reportEntityZero(final boolean zeroed, final double want, final int colliders,
                                 final int drifting) {
        if (this.level().isClientSide()) {
            return;
        }
        if (!zeroed) {
            if (this.entityZeroActive) {
                this.emitEntityZero(this.entityZeroRun, 1, want, colliders, drifting);
                this.entityZeroActive = false;
                this.entityZeroQuietUntil = this.tickCount + ENTZERO_QUIET_TICKS;
            }
            this.entityZeroRun = 0;
            return;
        }
        this.entityZeroRun++;
        if (this.entityZeroActive) {
            return;
        }
        if (this.entityZeroRun == ENTZERO_LONG_RUN
                || this.entityZeroRun == ENTZERO_MIN_RUN && this.tickCount >= this.entityZeroQuietUntil) {
            this.entityZeroActive = true;
            this.emitEntityZero(this.entityZeroRun, 0, want, colliders, drifting);
        }
    }

    private void emitEntityZero(final int run, final int end, final double want, final int colliders,
                                final int drifting) {
        if (shouldLog) LOGGER.debug("[livingblock] entzero id={} run={} end={} want={} colliders={} drifting={} obb={} pos={}",
                this.getId(), run, end, String.format("%.4f", want), colliders, drifting,
                String.format("%.4f", LivingBlockCollisionHandler.worstEntityOverlap(this)),
                String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
    }

    public void reportBlocked() {
        if (this.level().isClientSide() || this.climbPivotSequence
                || !this.usesOrientedCollision()) {
            return;
        }
        if (!this.isNudging()) {
            if (shouldLog) LOGGER.debug("[livingblock] nudge id={} enter=1 tilt={} settled={} ground={} pos={}",
                    this.getId(), String.format("%.1f", this.tiltDegrees()),
                    this.isOrientationSettled(), this.onGround(),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
        if (!this.isNudging()) {
            this.nudgeStart = this.tickCount;
        }
        this.nudgeUntil = this.tickCount + NUDGE_HYSTERESIS_TICKS;
        this.resetMaxUpStep();
        this.resetClimbingDirection();
    }

    public void setMovement(final MovementStrategy<?> movement) {
        if (movement == null || movement == this.movement) {
            return;
        }
        this.movement = movement;
        if (!this.level().isClientSide()) {
            if (this.usesOrientedCollision()
                    && this.getMovementData() instanceof RollingMovement.Data leaving) {
                this.rollBlocked = leaving.failedMoveAttempts;
            }
            MovementData fresh = movement.initData();
            if (this.usesOrientedCollision() && fresh instanceof RollingMovement.Data entering) {
                entering.failedMoveAttempts = this.rollBlocked;
            }
            this.setMovementData(fresh);
            this.entityData.set(DATA_MOVEMENT_TYPE, movementTypeId());
        }
    }

    public void clearRollBlocked() {
        this.rollBlocked = 0;
    }

    public int getAirTicks() {
        return this.airTicks;
    }

    public boolean shouldLogVerticalBlock() {
        boolean fresh = this.tickCount - this.vblockLogTick >= VBLOCK_LOG_INTERVAL;
        if (fresh) {
            this.vblockLogTick = this.tickCount;
        }
        return fresh;
    }

    private int movementTypeId() {
        if (this.movement == MV_NUDGE) return 4;
        if (this.movement instanceof BouncingMovement) return 1;
        if (this.movement instanceof FluidFloatingMovement) return 3;
        if (this.movement instanceof FloatingMovement) return 2;
        return 0;
    }

    private static MovementStrategy<?> movementFromId(final int id) {
        return switch (id) {
            case 1 -> MV_POGO;
            case 2 -> MV_DRIFT;
            case 3 -> MV_ROLL_FLOAT;
            case 4 -> MV_NUDGE;
            default -> MV_ROLL_FLOAT;
        };
    }

    @Override
    public void onSyncedDataUpdated(final EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ROTATION.equals(key) && this.level().isClientSide()) {
            this.syncedRotation.set(this.entityData.get(DATA_ROTATION));
            this.hasSyncedRotation = true;
            this.rollPhase = 0.0;
            this.rollPending = 0.0;
        }
        if (DATA_MOVEMENT_TYPE.equals(key) && this.level().isClientSide()) {
            this.movement = movementFromId(this.entityData.get(DATA_MOVEMENT_TYPE));
        }
        if (this.level().isClientSide()
                && (DATA_CLIMB_PIVOT_WORLD.equals(key)
                        && this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE)
                        || DATA_CLIMB_PIVOT_ACTIVE.equals(key)
                                && this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE))) {
            Vector3f storedLocal = this.entityData.get(DATA_CLIMB_PIVOT_LOCAL);
            Vector3f storedWorld = this.entityData.get(DATA_CLIMB_PIVOT_WORLD);
            Vec3 local = new Vec3(storedLocal.x, storedLocal.y, storedLocal.z);
            Vec3 world = new Vec3(storedWorld.x, storedWorld.y, storedWorld.z);
            double stale = LivingBlockPivot.worldPoint(this.getShapeBoxes(), this.shapePivot(),
                    this.rotation, this.position(), local).distanceTo(world);
            if (shouldLog) LOGGER.debug("[livingblock] pivotsync id={} stale={} pos={}", this.getId(),
                    String.format("%.6f", stale),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        }
        if ((DATA_HULL_ORIENTED.equals(key) || DATA_ORIENTATION_SETTLED.equals(key))
                && this.level().isClientSide()) {
            this.setBoundingBox(this.makeBoundingBox());
        }
        if (DATA_SHAPE_SEED.equals(key)) {
            this.applyShape();
        }
        if (DATA_ORIENTATION.equals(key)) {
            this.orientation = LivingBlockOrientation.of(this.entityData.get(DATA_ORIENTATION));
            this.restoreClientRotation();
            this.setPos(this.position());
        }
        if (DATA_ORIENTATION_SETTLED.equals(key)) {
            this.restoreClientRotation();
            this.setPos(this.position());
        }
    }

    private void restoreClientRotation() {
        if (!this.level().isClientSide()) {
            return;
        }
        if (!this.rotationRestored) {
            this.rotationRestored = true;
            this.rotation.set(this.getOrientation().quaternion());
            this.lastRotation.set(this.rotation);
            this.drawnFrom.set(this.rotation);
            this.rollPhase = 0.0;
            this.rollPhaseAxis = LivingBlockRoll.AXIS_NONE;
            this.rollPending = 0.0;
            this.rollPendingAxis = LivingBlockRoll.AXIS_NONE;
        } else if (this.isOrientationSettled()) {
            this.pendingOrientationRestore = true;
        }
    }

    public void reissueMovementTarget(final Target target) {
        if (!this.level().isClientSide()) {
            this.movement.resetMovement(this);
            this.nudgeUntil = -1;
        }
        this.setMovementTarget(target);
    }

    public void setMovementTarget(final Target target) {
        if (!this.usesOrientedCollision()) {
            this.entityData.set(MOVEMENT_TARGET, target == null ? Target.NONE : target);
            return;
        }
        this.noteClimbOrder();
        Target next = target == null ? Target.NONE : target;
        Target previous = this.entityData.get(MOVEMENT_TARGET);
        if (!this.level().isClientSide() && !next.equals(previous)) {
            boolean wasClimbing = this.isClimbing();
            boolean wasNudging = this.isNudging();
            this.movement.resetMovement(this);
            this.nudgeUntil = -1;
            if (wasClimbing || wasNudging) {
                if (shouldLog) LOGGER.debug("[livingblock] retarget id={} climb={} nudge={} from={} to={}",
                        this.getId(), wasClimbing, wasNudging, previous.type(), next.type());
            }
        }
        this.riderHoldUntil = Long.MIN_VALUE;
        this.rollBlocked = 0;
        this.entityData.set(MOVEMENT_TARGET, next);
    }

    public void clearMovementTarget() {
        this.setMovementTarget(Target.NONE);
    }

    @Override
    protected void positionRider(final Entity passenger, final MoveFunction moveFunction) {
        if (this.isVehicle() && this.getPassengers().contains(passenger)) {
            Vec3 bedCenter = this.position();
            moveFunction.accept(passenger, bedCenter.x, bedCenter.y + 0.5625, bedCenter.z);
        }
    }

    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        return !this.isVehicle() && passenger instanceof Player;
    }

    @Override
    protected void defineSynchedData(final Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(MOVEMENT_TARGET, Target.NONE);
        entityData.define(MOVEMENT_DATA, MovementData.EMPTY);
        entityData.define(DATA_CLIMBING_DIRECTION, Direction.DOWN);
        entityData.define(DATA_MOVEMENT_TYPE, 3);
        entityData.define(DATA_SHAPE_SEED, 0);
        entityData.define(DATA_FEATURE_SEED, 0);
        entityData.define(DATA_GROWTH, 0);
        entityData.define(DATA_ORIENTATION, LivingBlockOrientation.IDENTITY.index());
        entityData.define(DATA_ORIENTATION_SETTLED, false);
        entityData.define(DATA_ROTATION, new Quaternionf());
        entityData.define(DATA_CLIMB_PIVOT_LOCAL, new Vector3f());
        entityData.define(DATA_CLIMB_PIVOT_WORLD, new Vector3f());
        entityData.define(DATA_CLIMB_PIVOT_ACTIVE, false);

        entityData.define(DATA_STEP_PHASE, false);
        entityData.define(DATA_HULL_ORIENTED, false);

    }

    public Direction getClimbingDirection() {
        return this.entityData.get(DATA_CLIMBING_DIRECTION);
    }

    public void setClimbingDirection(final Direction direction) {
        if (!this.level().isClientSide()) {
            if (direction != this.entityData.get(DATA_CLIMBING_DIRECTION)) {
                this.clearClimbPivot();
            }
            this.entityData.set(DATA_CLIMBING_DIRECTION, direction);
        }
    }

    public void resetClimbingDirection() {
        this.setClimbingDirection(Direction.DOWN);
    }

    public boolean isClimbing() {
        return this.getClimbingDirection().getAxis() != Axis.Y;
    }

    @Override
    public boolean causeFallDamage(final float fallDistance, final float multiplier,
                                   final DamageSource source) {
        if (this.usesOrientedCollision()) {
            return super.causeFallDamage(fallDistance, multiplier, source);
        }
        this.resetFallDistance();
        return false;
    }

    private double floorGap(final AABB hull) {
        AABB probe = new AABB(hull.minX, hull.minY - FLOOR_LIFT_MAX, hull.minZ,
                hull.maxX, hull.minY + FLOOR_LIFT_MAX, hull.maxZ).deflate(1.0E-4);
        double top = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(probe.minX, probe.minY, probe.minZ),
                BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ))) {
            for (AABB piece : this.level().getBlockState(pos)
                    .getCollisionShape(this.level(), pos).toAabbs()) {
                AABB world = piece.move(pos.getX(), pos.getY(), pos.getZ());
                if (world.intersects(probe)) {
                    top = Math.max(top, world.maxY);
                }
            }
        }
        return top == Double.NEGATIVE_INFINITY ? Double.NaN : hull.minY - top;
    }

    private void liftOutOfFloor() {
        if (this.usesOrientedCollision() || this.climbPivotSequence) {
            return;
        }
        double gap = this.floorGap(this.getBoundingBox());
        if (Double.isNaN(gap) || gap >= -1.0E-9 || gap < -FLOOR_LIFT_MAX) {
            return;
        }
        this.setPos(this.getX(), this.getY() - gap, this.getZ());
    }

    private void logLanding(final Direction direction) {
        if (this.level().isClientSide()) {
            return;
        }
        this.landingWatch = LANDING_WATCH_TICKS;
        this.landingTick = 0;
        this.landingDir = direction;
        this.landingSample("arc");
    }

    private void tickLandingWatch() {
        if (this.landingWatch <= 0 || this.level().isClientSide()) {
            return;
        }
        this.landingWatch--;
        this.landingTick++;
        boolean settled = this.onGround() && Math.abs(this.getDeltaMovement().y) < 1.0E-3;
        this.landingSample(settled ? "settled" : this.landingWatch == 0 ? "expired" : "fall");
        if (settled || this.landingWatch == 0) {
            this.landingWatch = 0;
            this.landingDir = null;
        }
    }

    private void landingSample(final String phase) {
        AABB hull = this.getBoundingBox();
        AABB below = new AABB(hull.minX, hull.minY - 1.6, hull.minZ,
                hull.maxX, hull.minY + 0.1, hull.maxZ).deflate(1.0E-4);
        StringBuilder support = new StringBuilder();
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(below.minX, below.minY, below.minZ),
                BlockPos.containing(below.maxX, below.maxY, below.maxZ))) {
            BlockState state = this.level().getBlockState(pos);
            for (AABB piece : state.getCollisionShape(this.level(), pos).toAabbs()) {
                AABB world = piece.move(pos.getX(), pos.getY(), pos.getZ());
                if (!world.intersects(below)) {
                    continue;
                }
                if (count++ > 0) {
                    support.append(' ');
                }
                support.append(String.format("%s@%.2f,%.2f,%.2f..%.2f,%.2f,%.2f",
                        state.getBlock().getDescriptionId(), world.minX, world.minY, world.minZ,
                        world.maxX, world.maxY, world.maxZ));
            }
        }
        AABB drop = hull.move(0.0, -0.05, 0.0);
        int engine = 0;
        for (VoxelShape ignored : this.level().getBlockCollisions(this, drop)) {
            engine++;
        }
        if (shouldLog) LOGGER.debug("[livingblock] landing id={} phase={} n={} dir={} hull={} feet={} ground={} vcol={} step={} vy={} climb={} pivot={} nograv={} free={} engine={} support={} boxes={}",
                this.getId(), phase, this.landingTick, this.landingDir,
                String.format("%.3f,%.3f,%.3f..%.3f,%.3f,%.3f", hull.minX, hull.minY, hull.minZ,
                        hull.maxX, hull.maxY, hull.maxZ),
                String.format("%.4f", hull.minY), this.onGround(), this.verticalCollision,
                String.format("%.2f", this.maxUpStep()),
                String.format("%.4f", this.getDeltaMovement().y),
                this.getClimbingDirection(), this.climbPivotSequence, this.isNoGravity(),
                this.level().noCollision(this, drop), engine,
                count == 0 ? "NONE" : support.toString(), count);
    }

    public boolean usesClimbPivot() {
        return !this.isEntropicForm();
    }

    public boolean usesOrientedCollision() {
        return this.isEntropicForm();
    }

    public boolean orientedHull() {
        return this.usesOrientedCollision() || this.entityData.get(DATA_HULL_ORIENTED);
    }

    private boolean wallArcRunning() {
        return this.climbPivotSequence && !this.climbPivotAligning && !this.descentSequence
                && !this.stepApproaching && !this.stepLifting && !this.stepHolding
                && !this.stepDropping && !this.entityData.get(DATA_STEP_PHASE);
    }

    private static final int CARRY_PUSH_GRACE = 5;

    private boolean holdingGround;

    private Vec3 frozenFrom = Vec3.ZERO;
    private int frozenTicks;

    private static final boolean TRACE_WRITERS = true;

    private String lastArcRefusal = "";
    private double penLast;
    private StringBuilder writerTrace;
    private int writerTraceTick = -1;

    @Override
    public void setPos(final double x, final double y, final double z) {
        if (TRACE_WRITERS && this.level() != null && !this.level().isClientSide()) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            if (dx * dx + dy * dy + dz * dz > 1.0E-12) {
                this.noteWriter(dx, dy, dz);
            }
        }
        super.setPos(x, y, z);
    }

    private void noteWriter(final double dx, final double dy, final double dz) {
        if (this.writerTrace == null) {
            this.writerTrace = new StringBuilder();
        }
        if (this.writerTraceTick != this.tickCount) {
            this.writerTraceTick = this.tickCount;
            this.writerTrace.setLength(0);
        }
        if (this.writerTrace.length() > 420) {
            return;
        }
        String who = StackWalker.getInstance().walk(frames -> frames.skip(2).limit(10)
                .map(StackWalker.StackFrame::getMethodName)
                .filter(name -> !name.equals("setPos") && !name.equals("noteWriter"))
                .findFirst().orElse("?"));
        this.writerTrace.append(who).append('(')
                .append(String.format("%.3f,%.3f,%.3f", dx, dy, dz)).append(") ");
    }

    private void tickWriterTrace() {
        if (!TRACE_WRITERS || this.level().isClientSide()) {
            return;
        }
        Target order = this.getMovementTarget();
        if (order == null || order == Target.NONE) {
            return;
        }
        RollingMovement.Data data = this.getMovementData() instanceof RollingMovement.Data d ? d : null;
        AABB hull = this.getBoundingBox();
        Vec3 vel = this.getDeltaMovement();
        Vec3 want = order.resolvePosition(this.level());
        double pen = this.penetrationNow();
        double dpen = pen - this.penLast;
        this.penLast = pen;
        if (shouldLog) LOGGER.debug("[livingblock] beadtrace id={} t={} writers=[{}] hull={} pos={} vel={} gap={} ground={} air={} settled={} oriented={} seq={} align={} phase={} climb={} step={} moveticks={} fails={} route={} pen={} dpen={} arcref={} tilt={} ori={} inwall={} spin={} moved={} dem={} carry={}",
                this.getId(), this.tickCount,
                this.writerTrace == null ? "" : this.writerTrace.toString().trim(),
                String.format("%.3fx%.3fx%.3f", hull.getXsize(), hull.getYsize(), hull.getZsize()),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()),
                String.format("%.4f,%.4f,%.4f", vel.x, vel.y, vel.z),
                want == null ? "none" : String.format("%.2f",
                        Math.sqrt(Mth.square(want.x - this.getX()) + Mth.square(want.z - this.getZ()))),
                this.onGround(), this.airTicks, this.isOrientationSettled(),
                this.orientedHull(),
                this.climbPivotSequence, this.climbPivotAligning,
                this.entityData.get(DATA_STEP_PHASE), this.getClimbingDirection(),
                data == null ? "-" : String.valueOf(data.movingTo),
                data == null ? -1 : data.moveTicks,
                data == null ? -1 : data.failedMoveAttempts,
                data == null ? "-" : data.lastRoute,
                String.format("%.4f", pen), String.format("%+.4f", dpen),
                this.lastArcRefusal.isEmpty() ? "-" : this.lastArcRefusal,
                String.format("%.1f", this.tiltDegrees()),
                this.getOrientation().index(), this.isInWall(),
                String.format("%.2f", this.traceSpin), String.format("%.4f", this.traceMoved),
                String.format("%.1f", this.rollAngle),
                String.format("%.1f", 90.0 * Math.sqrt(this.rollCarryX * this.rollCarryX
                        + this.rollCarryZ * this.rollCarryZ)));
        if (this.writerTrace != null) {
            this.writerTrace.setLength(0);
        }
    }

    private void tickFrozenWatch() {
        if (this.level().isClientSide()) {
            return;
        }
        Target target = this.getMovementTarget();
        if (target == null || target == Target.NONE
                || this.position().distanceToSqr(this.frozenFrom) > 1.0E-6) {
            this.frozenFrom = this.position();
            this.frozenTicks = 0;
            return;
        }
        if (++this.frozenTicks % 60 != 0) {
            return;
        }
        RollingMovement.Data data = this.getMovementData() instanceof RollingMovement.Data d ? d : null;
        AABB hull = this.getBoundingBox();
        Vec3 wanted = target.resolvePosition(this.level());
        double gap = wanted == null ? Double.NaN
                : Math.sqrt(new Vec3(wanted.x - this.getX(), 0.0, wanted.z - this.getZ()).lengthSqr());
        if (shouldLog) LOGGER.debug("[livingblock] frozen id={} ticks={} target={} mv={} moveticks={} gap={}/{} step={} fails={} streak={} route={} settled={} seq={} align={} phase={} ground={} air={} inwall={} tilt={} ori={} hull={} pos={}",
                this.getId(), this.frozenTicks, target.getClass().getSimpleName(),
                this.movement.getClass().getSimpleName(),
                data == null ? -1 : data.moveTicks,
                String.format("%.3f", gap), String.format("%.3f", target.distance()),
                data == null ? "-" : String.valueOf(data.movingTo),
                data == null ? -1 : data.failedMoveAttempts,
                data == null ? -1 : data.climbRefusalStreak,
                data == null ? "-" : data.lastRoute,
                this.isOrientationSettled(),
                this.climbPivotSequence, this.climbPivotAligning,
                this.entityData.get(DATA_STEP_PHASE), this.onGround(), this.airTicks,
                this.isInWall(), String.format("%.1f", this.tiltDegrees()),
                this.getOrientation().index(),
                String.format("%.3fx%.3fx%.3f", hull.getXsize(), hull.getYsize(), hull.getZsize()),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
    }

    private void refreshHullOrientation() {
        if (this.level().isClientSide() || this.usesOrientedCollision()) {
            return;
        }
        boolean want = this.wallArcRunning();
        if (want == this.entityData.get(DATA_HULL_ORIENTED)) {
            return;
        }
        AABB before = this.getBoundingBox();
        double penBefore = this.penetrationNow();
        this.entityData.set(DATA_HULL_ORIENTED, want);
        this.setBoundingBox(this.makeBoundingBox());
        AABB after = this.getBoundingBox();
        double penAfter = this.penetrationNow();
        if (shouldLog) LOGGER.debug("[livingblock] hulloriented id={} on={} tilt={} penbefore={} penafter={} before={} after={} pos={}",
                this.getId(), want, String.format("%.1f", this.tiltDegrees()),
                String.format("%.4f", penBefore), String.format("%.4f", penAfter),
                String.format("%.3fx%.3fx%.3f", before.getXsize(), before.getYsize(), before.getZsize()),
                String.format("%.3fx%.3fx%.3f", after.getXsize(), after.getYsize(), after.getZsize()),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
    }

    public boolean prefersLowStep() {
        return false;
    }

    public boolean isStepDriving() {
        return this.movement instanceof RollingMovement rolling && rolling.isStepDriving(this);
    }

    public boolean livingBlockInWay(final AABB area) {
        for (LivingBlock other : this.level().getEntitiesOfClass(LivingBlock.class, area,
                candidate -> candidate != this && candidate.isAlive() && this.canCollideWith(candidate))) {
            LivingBlockCollisionShapes.Placement placement =
                    LivingBlockCollisionShapes.preciseGeometry(other);
            if (placement == null) {
                return true;
            }
            for (AABB box : placement.boxes()) {
                if (box.intersects(area)) {
                    return true;
                }
            }
        }
        return false;
    }

    public LivingBlockStep.Verdict lowStepVerdict(final Direction direction) {
        if (!this.prefersLowStep() || !direction.getAxis().isHorizontal()) {
            return new LivingBlockStep.Verdict(false,
                    this.prefersLowStep() ? "nothorizontal" : "nolowstep",
                    Double.NaN, Double.NaN);
        }
        AABB hull = this.getBoundingBox();
        AABB probe = LivingBlockStep.probe(hull, direction);
        List<AABB> surfaces = new ArrayList<>();
        int beads = 0;
        double beadTop = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(probe.minX, probe.minY, probe.minZ),
                BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ))) {
            for (AABB piece : this.level().getBlockState(pos)
                    .getCollisionShape(this.level(), pos).toAabbs()) {
                surfaces.add(piece.move(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        LivingBlockCollisionHandler.BodyTerrain bodies =
                LivingBlockCollisionHandler.bodyTerrain(this, probe);
        for (AABB box : bodies.boxes()) {
            surfaces.add(box);
            beads++;
            beadTop = Math.max(beadTop, box.maxY);
        }
        LivingBlockStep.Verdict verdict = LivingBlockStep.ahead(surfaces, hull, direction);
        if (beads > 0 || bodies.refused() > 0) {
            this.climbWhy(direction, "stepbody", String.format(
                    "boxes=%d refused=%d worsttilt=%.2f bodytop=%.4f feet=%.4f verdict=%s rise=%.4f",
                    beads, bodies.refused(), bodies.worstTilt(), beadTop, hull.minY,
                    verdict.reason(), verdict.rise()));
        }
        return verdict;
    }

    public boolean lowStepAhead(final Direction direction) {
        return this.lowStepVerdict(direction).ok();
    }

    public void stepWhy(final Direction direction, final String verdict) {
        if (this.level().isClientSide()) {
            return;
        }
        if (verdict.equals(this.stepWhyLast) && this.tickCount - this.stepWhyTick < 20) {
            return;
        }
        this.stepWhyLast = verdict;
        this.stepWhyTick = this.tickCount;
        AABB hull = this.getBoundingBox();
        if (shouldLog) LOGGER.debug("[livingblock] step id={} dir={} {} tilt={} ground={} step={} hull={} pos={}",
                this.getId(), direction, verdict, String.format("%.1f", this.tiltDegrees()),
                this.onGround(), String.format("%.2f", this.maxUpStep()),
                String.format("%.3f..%.3f", hull.minY, hull.maxY),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
    }

    private ClimbPivotAdvance startPivotSequence(final LivingBlockPivot.WallPivot candidate,
                                                 final ClimbArc arc, final Direction direction,
                                                 final Vec3 shapePivot, final boolean touches) {
        if (touches) {
            this.playClimbFaceSound(candidate, direction);
        }
        this.descentSequence = true;
        this.climbPivotAligning = false;
        this.climbPivot = candidate;
        this.claimAnchor(candidate.world(), false);
        this.climbPivotDirection = direction;
        this.climbPivotStep = 0;
        this.climbPivotLimit = arc.steps();
        this.climbPivotDegreesRemaining = arc.degrees();
        this.climbPivotDegreesTotal = arc.degrees();
        this.climbPivotCompletes = arc.top();
        this.climbPivotSequence = true;
        this.climbPivotEdgeStartTick = this.tickCount;
        this.climbPivotLastAdvanceTick = this.tickCount - 1;
        this.climbPivotMaxAdvanceGap = 0;
        this.climbPivotRuntimeDrift = 0.0;
        this.climbPivotExpectedPosition = this.position();
        this.nudgeUntil = -1;
        this.solverBlockedTicks = 0;
        this.rollDeltaX = 0.0F;
        this.rollDeltaZ = 0.0F;
        this.rollCarryX = 0.0F;
        this.rollCarryZ = 0.0F;
        this.rollPending = 0.0;
        this.rollPendingAxis = LivingBlockRoll.AXIS_NONE;
        this.climbPivotVisualReleaseTick = -1;
        Vec3 local = candidate.local();
        this.entityData.set(DATA_CLIMB_PIVOT_LOCAL,
                new Vector3f((float)local.x, (float)local.y, (float)local.z));
        Vec3 world = candidate.world();
        this.entityData.set(DATA_CLIMB_PIVOT_WORLD,
                new Vector3f((float)world.x, (float)world.y, (float)world.z));
        this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, true);
        this.entityData.set(DATA_STEP_PHASE, false);
        this.climbFlow("descent", direction);
        if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=start dir={} edge={} steps={} angle={} top={} descent=1 pos={}",
                this.getId(), direction,
                String.format("%.3f,%.3f,%.3f", world.x, world.y, world.z),
                this.climbPivotLimit, String.format("%.2f", this.climbPivotDegreesTotal),
                this.climbPivotCompletes,
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return ClimbPivotAdvance.MOVED;
    }

    private static final double DESCENT_MIN_DROP = 0.25;
    private static final int DESCENT_DUMP_INTERVAL = 20;
    private static final int DESCENT_HEARTBEAT = 60;
    private static final int DESCENT_WALL_TICKS = 12;
    private static final double DESCENT_QUARTER_DEGREES = 90.0;

    private record DescentLook(LivingBlockPivot.DescentProbe probe, List<AABB> surfaces,
                               @Nullable Vec3 contact, int obbs) {
    }

    private List<AABB> descentSurfaces(final AABB around) {
        List<AABB> surfaces = new ArrayList<>();
        AABB sweep = around.inflate(1.2, 0.0, 1.2).expandTowards(0.0, -1.2, 0.0);
        for (BlockPos probe : BlockPos.betweenClosed(
                BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))) {
            for (AABB piece : this.level().getBlockState(probe)
                    .getCollisionShape(this.level(), probe).toAabbs()) {
                surfaces.add(piece.move(probe.getX(), probe.getY(), probe.getZ()));
            }
        }
        LivingBlockCollisionHandler.BodyTerrain bodies =
                LivingBlockCollisionHandler.bodyTerrain(this, sweep);
        surfaces.addAll(bodies.boxes());
        if ((!bodies.boxes().isEmpty() || bodies.refused() > 0)
                && this.beadWhyDue("descentground")) {
            this.beadWhy("descentground", String.format("boxes=%d refused=%d worsttilt=%.2f",
                    bodies.boxes().size(), bodies.refused(), bodies.worstTilt()));
        }
        return surfaces;
    }

    private LivingBlockPivot.PoseCheck descentPoseCheck() {
        return (pose, where) -> {
            LivingBlockCollisionHandler.ClimbPoseFailure failure =
                    LivingBlockCollisionHandler.climbPoseFailure(this, pose, where,
                            CLIMB_PIVOT_SLOP);
            return failure == null ? null : failure.kind() + ":"
                    + String.format("%.4f", failure.depth()) + ":" + failure.blocker();
        };
    }

    private @Nullable ClimbPivotAdvance alignForDescent(final LivingBlockPivot.WallPivot pivot,
                                                        final Direction direction,
                                                        final Vec3 shapePivot) {
        if (LivingBlockPivot.misalignment(this.rotation) <= LivingBlockPivot.LATTICE_TOLERANCE) {
            return null;
        }
        Quaternionf target = this.climbAlignmentTarget(pivot, shapePivot);
        if (target == null) {
            this.climbWhy(direction, "descentnoalign", String.format(
                    "misalign=%.3f", LivingBlockPivot.misalignment(this.rotation)));
            return null;
        }
        this.descentSequence = true;
        this.climbPivotSequence = true;
        this.climbPivotAligning = true;
        this.climbPivot = pivot;
        this.claimAnchor(pivot.world(), false);
        this.climbPivotDirection = direction;
        this.climbPivotAlignmentTarget.set(target);
        this.climbPivotStep = 0;
        this.climbPivotDegreesTotal = angleBetween(this.rotation, target);
        this.climbPivotDegreesRemaining = this.climbPivotDegreesTotal;
        this.climbPivotLimit = Math.max(1, (int)Math.ceil(
                (this.climbPivotDegreesTotal - 1.0E-6) / LivingBlockPivot.DEGREES));
        this.climbPivotCompletes = false;
        this.climbPivotEdgeStartTick = this.tickCount;
        this.climbPivotLastAdvanceTick = this.tickCount - 1;
        this.climbPivotMaxAdvanceGap = 0;
        this.climbPivotRuntimeDrift = 0.0;
        this.climbPivotExpectedPosition = this.position();
        this.nudgeUntil = -1;
        this.solverBlockedTicks = 0;
        this.rollCarryX = 0.0F;
        this.rollCarryZ = 0.0F;
        this.setDeltaMovement(Vec3.ZERO);
        this.pendingFlop = false;
        Vec3 local = pivot.local();
        this.entityData.set(DATA_CLIMB_PIVOT_LOCAL,
                new Vector3f((float)local.x, (float)local.y, (float)local.z));
        Vec3 world = pivot.world();
        this.entityData.set(DATA_CLIMB_PIVOT_WORLD,
                new Vector3f((float)world.x, (float)world.y, (float)world.z));
        this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, true);
        this.climbWhy(direction, "descentalign", String.format("deg=%.2f steps=%d",
                this.climbPivotDegreesTotal, this.climbPivotLimit));
        this.climbFlow("descentalign", direction);
        return this.advanceClimbAlignment(direction, shapePivot);
    }

    private @Nullable ClimbPivotAdvance continueDescent(final Direction direction,
                                                        final Vec3 shapePivot) {
        if (this.descentWall == null || this.descentWallDir != direction
                || this.tickCount - this.descentWallTick > DESCENT_WALL_TICKS) {
            if (this.descentSequence || this.descentWall != null) {
                this.climbWhy(direction, "descentnoheldwall", String.format(
                        "wall=%b helddir=%s age=%d limit=%d",
                        this.descentWall != null, this.descentWallDir,
                        this.descentWallTick == Integer.MIN_VALUE ? -1
                                : this.tickCount - this.descentWallTick, DESCENT_WALL_TICKS));
            }
            return null;
        }
        LivingBlockPivot.WallPivot next = LivingBlockPivot.wallPivot(this.getShapeBoxes(),
                this.getShapeBounds(), shapePivot, this.rotation, this.position(), direction,
                this.descentWall, CLIMB_PIVOT_CONTACT, true);
        if (next == null) {
            this.climbWhy(direction, "descentnowallpivot", String.format(
                    "plane=%.3f yband=%.3f..%.3f", this.descentWall.plane(),
                    this.descentWall.minY(), this.descentWall.maxY()));
            return null;
        }
        double ledgeGap = this.descentWallBody ? this.anchorGap(next.world()) : 0.0;
        if (ledgeGap > BODY_ANCHOR_HOLD) {
            if (this.beadWhyDue("descentanchorlost")) {
                this.beadWhy("descentanchorlost", String.format(
                        "dir=%s left=%.4f pen=%.5f point=%.3f,%.3f,%.3f", direction, ledgeGap,
                        this.penetrationNow(), next.world().x, next.world().y, next.world().z));
            }
            return null;
        }
        LivingBlockPivot.DescentPlan cont = LivingBlockPivot.descentPlan(this.getShapeBoxes(),
                shapePivot, this.rotation, this.position(), next, direction,
                this.descentSurfaces(this.getBoundingBox()), DESCENT_MIN_DROP,
                DESCENT_QUARTER_DEGREES, this.descentPoseCheck());
        if (cont.cleanSteps() == 0) {
            this.climbWhy(direction, "descentcontblocked", cont.stop());
            return null;
        }
        this.descentWallTick = this.tickCount;
        this.climbWhy(direction, "descentgo", String.format(
                "deg=%.2f steps=%d landed=%b", cont.degrees(), cont.cleanSteps(), cont.landed()));
        ClimbPivotAdvance installed = this.startPivotSequence(next,
                new ClimbArc(true, cont.cleanSteps(), cont.landed(), cont.degrees(), ""),
                direction, shapePivot, descentTouches(cont));
        return installed == ClimbPivotAdvance.MOVED ? this.advanceClimbPivot(direction) : installed;
    }

    private DescentLook lookDescent(final Direction direction) {
        AABB hull = this.getBoundingBox();
        LivingBlockCollisionShapes.Placement placement =
                LivingBlockCollisionShapes.preciseGeometry(this);
        Vec3 contact = null;
        for (OrientedBox box : placement.obbs()) {
            AABB world = box.getWorldAABB();
            if (contact == null || world.minY < contact.y) {
                contact = new Vec3(world.getCenter().x, world.minY, world.getCenter().z);
            }
        }
        List<AABB> surfaces = this.descentSurfaces(hull);
        if (contact == null) {
            return new DescentLook(LivingBlockPivot.DescentProbe.refused("noobb"), surfaces,
                    null, 0);
        }
        return new DescentLook(LivingBlockPivot.descentProbe(surfaces, hull, contact, direction,
                DESCENT_EDGE_REACH, DESCENT_MIN_DROP, this.getShapeBoxes(), this.getShapeBounds(),
                this.shapePivot(), this.rotation, this.position(), CLIMB_PIVOT_CONTACT),
                surfaces, contact, placement.obbs().size());
    }

    private LivingBlockPivot.DescentPlan descentPlan(final DescentLook look,
                                                     final Direction direction) {
        return this.descentPlan(look, direction, !this.lowStepDrop(look, direction));
    }

    private LivingBlockPivot.DescentPlan descentPlan(final DescentLook look,
                                                     final Direction direction,
                                                     final boolean stopOnLanding) {
        return LivingBlockPivot.descentPlan(this.getShapeBoxes(), this.shapePivot(), this.rotation,
                this.position(), look.probe().pivot(), direction, look.surfaces(), DESCENT_MIN_DROP,
                DESCENT_QUARTER_DEGREES, stopOnLanding, this.descentPoseCheck());
    }

    private static boolean descentTouches(final LivingBlockPivot.DescentPlan plan) {
        return plan.landed() || plan.stop().startsWith("touched");
    }

    private boolean approachLedge(final Direction direction,
                                  final LivingBlockPivot.DescentProbe probe) {
        double gap = probe.pivot() != null ? 0.0
                : Double.isFinite(probe.pivotDistance()) ? probe.pivotDistance()
                        : Double.isFinite(probe.beyondBody()) ? probe.beyondBody() : Double.NaN;
        if (!Double.isFinite(gap) || gap <= CLIMB_PIVOT_CONTACT || gap > DESCENT_EDGE_REACH) {
            this.descentApproachTicks = 0;
            return false;
        }
        if (this.position().distanceToSqr(this.descentApproachFrom) > 1.0E-8) {
            this.descentApproachTicks = 0;
        }
        this.descentApproachFrom = this.position();
        if (this.descentApproachTicks++ >= CLIMB_APPROACH_STUCK) {
            this.climbWhy(direction, "descentapproachstuck",
                    String.format("gap=%.5f ticks=%d", gap, this.descentApproachTicks));
            this.descentApproachTicks = 0;
            return false;
        }
        double step = Math.min(gap, CLIMB_APPROACH_SPEED);
        Vec3 velocity = this.getDeltaMovement();
        this.setDeltaMovement(direction.getStepX() * step, velocity.y, direction.getStepZ() * step);
        this.climbWhy(direction, "descentnear", String.format(
                "gap=%.5f step=%.5f ticks=%d", gap, step, this.descentApproachTicks));
        return true;
    }

    private static final double DESCENT_NOT_A_STEP = 0.05;

    private boolean climbingNotDescending(final Direction direction) {
        if (!this.prefersLowStep()) {
            return false;
        }
        LivingBlockStep.Verdict step = this.lowStepVerdict(direction);
        return step.ok() && step.rise() > DESCENT_NOT_A_STEP;
    }

    private boolean lowStepDrop(final DescentLook look, final Direction direction) {
        if (!this.prefersLowStep() || !look.probe().ok()) {
            return false;
        }
        double drop = look.probe().drop();
        return drop <= LOW_STEP_HEIGHT + LOW_STEP_SLACK
                || !Double.isFinite(drop) && this.lowStepAhead(direction);
    }

    private @Nullable LivingBlockPivot.WallPivot contactPivot() {
        Vec3 shapePivot = this.shapePivot();
        Vec3 bestLocal = null;
        Vec3 bestWorld = null;
        for (Vec3 local : LivingBlockPivot.cornerCandidates(this.getShapeBoxes())) {
            Vec3 world = LivingBlockPivot.worldPoint(this.getShapeBoxes(), shapePivot,
                    this.rotation, this.position(), local);
            if (bestWorld == null || world.y < bestWorld.y) {
                bestLocal = local;
                bestWorld = world;
            }
        }
        return bestWorld == null ? null : new LivingBlockPivot.WallPivot(bestLocal, bestWorld);
    }

    private boolean squareForDescent(final Direction direction, final DescentLook look) {
        if (!this.prefersLowStep() || this.climbPivotSequence
                || LivingBlockPivot.misalignment(this.rotation) <= LivingBlockPivot.LATTICE_TOLERANCE) {
            return false;
        }
        String reason = look.probe().reason();
        if (!"nocontact".equals(reason) && !"nocandidate".equals(reason)) {
            return false;
        }
        LivingBlockPivot.WallPivot seat = this.contactPivot();
        if (seat == null) {
            return false;
        }
        this.setClimbingDirection(direction);
        ClimbPivotAdvance aligning = this.alignForDescent(seat, direction, this.shapePivot());
        if (aligning == null || aligning == ClimbPivotAdvance.BLOCKED) {
            this.resetClimbingDirection();
            return false;
        }
        if (shouldLog) LOGGER.debug("[livingblock] square id={} dir={} why={} misalign={} tilt={} pos={}",
                this.getId(), direction, reason,
                String.format("%.3f", LivingBlockPivot.misalignment(this.rotation)),
                String.format("%.1f", this.tiltDegrees()),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return true;
    }

    public boolean canBeginDescent(final Direction direction) {
        if (this.level().isClientSide()) {
            return false;
        }
        if (!this.usesOrientedCollision()) {
            this.descentDump(direction, "aabbdrop", null, null);
            return false;
        }
        if (!this.usesClimbPivot() || this.isClimbing() || !this.onGround()
                || !direction.getAxis().isHorizontal()) {
            this.descentDump(direction, !this.usesClimbPivot() ? "nopivot"
                    : this.isClimbing() ? "alreadyclimbing"
                    : !this.onGround() ? "inair" : "dirnothorizontal", null, null);
            return false;
        }
        if (this.climbingNotDescending(direction)) {
            this.descentDump(direction, "stepahead", null, null);
            return false;
        }
        DescentLook look = this.lookDescent(direction);
        if (this.squareForDescent(direction, look)) {
            this.descentDump(direction, "square", look, null);
            return true;
        }
        LivingBlockPivot.DescentPlan plan = look.probe().ok()
                ? this.descentPlan(look, direction) : null;
        boolean ok = plan != null && plan.cleanSteps() > 0;
        if (!ok && !look.probe().ok() && this.approachLedge(direction, look.probe())) {
            this.descentDump(direction, "near", look, null);
            return false;
        }
        this.descentDump(direction, ok ? "ready"
                : look.probe().ok() ? "arc:" + plan.stop() : look.probe().reason(), look, plan);
        if (ok) {
            this.descentHeldLook = look;
            this.descentHeldDir = direction;
            this.descentHeldTick = this.tickCount;
        }
        return ok;
    }

    private void descentDump(final Direction direction, final String verdict,
                             final @Nullable DescentLook look,
                             final @Nullable LivingBlockPivot.DescentPlan plan) {
        if (this.level().isClientSide()) {
            return;
        }
        if (verdict.equals(this.descentDumpVerdict)
                && this.tickCount - this.descentDumpTick < DESCENT_DUMP_INTERVAL) {
            return;
        }
        this.descentDumpVerdict = verdict;
        this.descentDumpTick = this.tickCount;
        LivingBlockPivot.DescentProbe probe = look == null ? null : look.probe();
        AABB hull = this.getBoundingBox();
        AABB base = this.getBaseShapeBounds();
        Vec3 velocity = this.getDeltaMovement();
        BlockPos below = this.getOnPos();
        if (shouldLog) LOGGER.debug("[livingblock] descdump id={} t={} dir={} verdict={} form={} dirclimb={} pos={} prev={} vel={} ground={} drop={} nograv={} leash={} passengers={} riding={} inwater={} neighbours={} routes={} lastroute={} order={} pivotmoved={} syncactive={} visrelease={} tilt={} settled={} quat={} hull={} side={} obbs={} contact={} surfaces={} supportbelow={} why={} supporttop={} bodyfront={} plane={} marched={} beyond={} ceilingahead={} probedrop={} facebottom={} edge={} pivotlocal={} pivotworld={} candidates={} pivotdist={} rej={} landed={} steps={} deg={} stop={} pen={} offpivot={} floorgap={} wallgap={} jump={} misalign={} seq={} align={} step={}/{} left={} complete={} drift={} maxgap={} pennow={}",
                this.getId(), this.tickCount, direction, verdict,
                this.isEntropicForm() ? "entropic" : "block",
                this.getClimbingDirection(),
                String.format("%.6f,%.6f,%.6f", this.getX(), this.getY(), this.getZ()),
                String.format("%.6f,%.6f,%.6f", this.xo, this.yo, this.zo),
                String.format("%.6f,%.6f,%.6f", velocity.x, velocity.y, velocity.z),
                this.onGround(), String.format("%.3f", this.fallDistance), this.isNoGravity(),
                this.isLeashed(), this.getPassengers().size(), this.isPassenger(),
                this.isInWater(),
                this.level().getEntitiesOfClass(LivingBlock.class,
                        this.getBoundingBox().inflate(1.5), other -> other != this).size(),
                this.climbFlowRoutes, this.climbFlowLastRoute, this.climbFlowOrdered,
                this.climbPivotMovedThisTick,
                this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE), this.climbPivotVisualReleaseTick,
                String.format("%.3f", this.tiltDegrees()),
                this.entityData.get(DATA_ORIENTATION_SETTLED),
                String.format("%.4f,%.4f,%.4f,%.4f", this.rotation.x(), this.rotation.y(),
                        this.rotation.z(), this.rotation.w()),
                String.format("%.4f..%.4f", hull.minY, hull.maxY),
                String.format("%.3f,%.3f,%.3f", base.getXsize(), base.getYsize(), base.getZsize()),
                look == null ? -1 : look.obbs(),
                look == null || look.contact() == null ? "-"
                        : String.format("%.4f,%.4f,%.4f", look.contact().x, look.contact().y,
                                look.contact().z),
                look == null ? -1 : look.surfaces().size(),
                this.level().getBlockState(below).getBlock().getDescriptionId(),
                probe == null ? "-" : probe.reason(),
                probe == null ? "nan" : String.format("%.4f", probe.supportTop()),
                probe == null ? "nan" : String.format("%.4f", probe.bodyFront()),
                probe == null ? "nan" : String.format("%.4f", probe.plane()),
                probe == null ? -1 : probe.marched(),
                probe == null ? "nan" : String.format("%.4f", probe.beyondBody()),
                probe == null ? "nan" : String.format("%.4f", probe.aheadTop()),
                probe == null ? "nan" : String.format("%.4f", probe.drop()),
                probe == null ? "nan" : String.format("%.4f", probe.faceBottom()),
                probe == null || probe.edge() == null ? "-"
                        : String.format("%.4f,%.4f,%.4f", probe.edge().x, probe.edge().y,
                                probe.edge().z),
                probe == null || probe.pivot() == null ? "-"
                        : String.format("%.4f,%.4f,%.4f", probe.pivot().local().x,
                                probe.pivot().local().y, probe.pivot().local().z),
                probe == null || probe.pivot() == null ? "-"
                        : String.format("%.4f,%.4f,%.4f", probe.pivot().world().x,
                                probe.pivot().world().y, probe.pivot().world().z),
                probe == null ? -1 : probe.pivotCandidates(),
                probe == null ? "nan" : String.format("%.4f", probe.pivotDistance()),
                probe == null ? "-" : String.format("yband=%d across=%d outofreach=%d reach=%.4f",
                        probe.rejYBand(), probe.rejAcross(), probe.outOfReach(),
                        probe.pivotReach()),
                plan == null ? "-" : String.valueOf(plan.landed()),
                plan == null ? -1 : plan.cleanSteps(),
                plan == null ? "nan" : String.format("%.2f", plan.degrees()),
                plan == null ? "-" : plan.stop(),
                plan == null ? "nan" : String.format("%.6f", plan.worstPenetration()),
                plan == null ? "nan" : String.format("%.6f", plan.worstPivotToBody()),
                plan == null ? "nan" : String.format("%.6f", plan.worstGapToFloor()),
                plan == null ? "nan" : String.format("%.6f", plan.worstGapToWall()),
                plan == null ? "nan" : String.format("%.6f", plan.entryShift()),
                plan == null ? "nan" : String.format("%.3f", plan.endMisalignment()),
                this.climbPivotSequence, this.climbPivotAligning, this.climbPivotStep,
                this.climbPivotLimit, String.format("%.2f", this.climbPivotDegreesRemaining),
                this.climbPivotCompletes, String.format("%.6f", this.climbPivotRuntimeDrift),
                this.climbPivotMaxAdvanceGap, String.format("%.6f", this.penetrationNow()));
        if (plan != null) {
            for (LivingBlockPivot.DescentStep step : plan.steps()) {
                if (shouldLog) LOGGER.debug("[livingblock] descstep id={} t={} dir={} phase=plan step={} deg={} y={} offpivot={} floorgap={} wallgap={} pen={} held={} landed={} fail={}",
                        this.getId(), this.tickCount, direction, step.index(),
                        String.format("%.2f", step.degrees()), String.format("%.5f", step.bodyY()),
                        String.format("%.6f", step.pivotToBody()),
                        String.format("%.6f", step.gapToFloor()),
                        String.format("%.6f", step.gapToWall()),
                        String.format("%.6f", step.penetration()),
                        step.supported(), step.landed(), step.failure());
            }
        }
    }

    private void descentStepLog(final Direction direction, final LivingBlockPivot.WallPivot pivot) {
        if (this.level().isClientSide() || pivot == null) {
            return;
        }
        List<AABB> surfaces = new ArrayList<>();
        AABB sweep = this.getBoundingBox().inflate(1.2, 1.2, 1.2);
        for (BlockPos probe : BlockPos.betweenClosed(
                BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))) {
            for (AABB piece : this.level().getBlockState(probe)
                    .getCollisionShape(this.level(), probe).toAabbs()) {
                surfaces.add(piece.move(probe.getX(), probe.getY(), probe.getZ()));
            }
        }
        Vec3 shapePivot = this.shapePivot();
        if (shouldLog) LOGGER.debug("[livingblock] descstep id={} t={} dir={} phase=game step={}/{} deg={} y={} offpivot={} floorgap={} wallgap={} pen={} drift={} tickmove={} ground={} vel={}",
                this.getId(), this.tickCount, direction, this.climbPivotStep, this.climbPivotLimit,
                String.format("%.2f", this.climbPivotDegreesRemaining),
                String.format("%.5f", this.getY()),
                String.format("%.6f", LivingBlockPivot.pivotToBody(this.getShapeBoxes(), shapePivot,
                        this.rotation, this.position(), pivot)),
                String.format("%.6f", LivingBlockPivot.gapToFloor(this.getShapeBoxes(), shapePivot,
                        this.rotation, this.position(), surfaces)),
                String.format("%.6f", LivingBlockPivot.gapToWall(this.getShapeBoxes(), shapePivot,
                        this.rotation, this.position(), pivot.surface().plane(), direction)),
                String.format("%.6f", this.penetrationNow()),
                String.format("%.6f", this.climbPivotRuntimeDrift),
                String.format("%.6f", this.position().distanceTo(new Vec3(this.xo, this.yo, this.zo))),
                this.onGround(),
                String.format("%.5f", this.getDeltaMovement().length()));
    }

    private double wallTouch(final Direction direction, final LivingBlockPivot.WallContact wall) {
        return LivingBlockPivot.wallSeparation(this.getShapeBoxes(), this.shapePivot(),
                this.rotation, this.position(), wall.surface(), direction);
    }

    private @Nullable LivingBlockPivot.WallPivot stepEdgePivot(final Direction direction,
                                                                final Vec3 at) {
        LivingBlockPivot.WallContact wall = LivingBlockCollisionHandler.climbWallContact(
                this, direction, DESCENT_EDGE_REACH);
        if (wall == null) {
            return null;
        }
        LivingBlockPivot.WallPivot pivot = LivingBlockPivot.wallPivot(this.getShapeBoxes(),
                this.getShapeBounds(), this.shapePivot(), this.rotation, at, direction,
                wall.surface(), CLIMB_PIVOT_REACH, true);
        if (pivot == null) {
            return null;
        }
        double top = wall.surface().maxY();
        if (Math.abs(pivot.world().y - top) <= 1.0E-12) {
            return pivot;
        }
        return new LivingBlockPivot.WallPivot(pivot.local(),
                new Vec3(pivot.world().x, top, pivot.world().z), pivot.surface());
    }

    public boolean beginStepQuarter(final Direction direction) {
        if (this.level().isClientSide() || !this.prefersLowStep()
                || !direction.getAxis().isHorizontal()) {
            return false;
        }
        if (this.climbPivotSequence || this.isClimbing() || !this.onGround()) {
            this.stepWhy(direction, "quarter=refused seq=" + this.climbPivotSequence
                    + " climbing=" + this.isClimbing() + " ground=" + this.onGround());
            return false;
        }
        LivingBlockStep.Verdict step = this.lowStepVerdict(direction);
        if (!step.ok() || step.rise() <= DESCENT_NOT_A_STEP) {
            this.stepWhy(direction, String.format("quarter=refused step=%s rise=%.4f",
                    step.reason(), step.rise()));
            return false;
        }
        LivingBlockPivot.WallContact wall = LivingBlockCollisionHandler.climbWallContact(
                this, direction, DESCENT_EDGE_REACH);
        if (wall == null) {
            this.climbWhy(direction, "stepnoriser", "");
            return false;
        }
        double touch = this.wallTouch(direction, wall);
        if (!LivingBlockPivot.wallTouched(touch, wall.gap(), CLIMB_PIVOT_CONTACT, false)) {
            this.climbWhy(direction, "stepnotouch", String.format(
                    "touch=%.5f hullgap=%.5f contact=%.5f", touch, wall.gap(),
                    CLIMB_PIVOT_CONTACT));
            return false;
        }
        if (wall.gap() > CLIMB_PIVOT_CONTACT) {
            this.setClimbingDirection(direction);
            this.stepApproaching = true;
            this.stepTarget = direction.getStepX() * this.getX() + direction.getStepZ() * this.getZ()
                    + wall.gap();
            this.climbPivotSequence = true;
            this.climbPivotDirection = direction;
            this.setDeltaMovement(Vec3.ZERO);
            this.entityData.set(DATA_STEP_PHASE, true);
            if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=near dir={} gap={} pos={}",
                    this.getId(), direction, String.format("%.4f", wall.gap()),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            return true;
        }
        Vec3 lifted = this.position().add(0.0, step.rise(), 0.0);
        if (LivingBlockCollisionHandler.climbPoseFailure(this, this.rotation, lifted,
                CLIMB_PIVOT_SLOP) != null) {
            this.climbWhy(direction, "stepnoroom", String.format("rise=%.3f", step.rise()));
            return false;
        }
        Vec3 shapePivot = this.shapePivot();
        LivingBlockPivot.WallPivot pivot = this.stepEdgePivot(direction, lifted);
        if (pivot == null) {
            LivingBlockPivot.WallPivot seat = this.tiltDegrees() > CLIMB_ALIGN_MIN_TILT
                    ? this.contactPivot() : null;
            if (seat != null) {
                this.setClimbingDirection(direction);
                if (this.alignForDescent(seat, direction, this.shapePivot()) != null) {
                    this.climbWhy(direction, "stepsquare",
                            String.format("tilt=%.2f", this.tiltDegrees()));
                    return true;
                }
                this.resetClimbingDirection();
            }
            this.climbWhy(direction, "stepnocorner",
                    String.format("tilt=%.2f", this.tiltDegrees()));
            return false;
        }
        Vec3 world = pivot.world();
        LivingBlockPivot.DescentPlan arc = LivingBlockPivot.descentPlan(this.getShapeBoxes(),
                shapePivot, this.rotation, lifted, pivot, direction,
                this.descentSurfaces(this.getBoundingBox().move(0.0, step.rise(), 0.0)),
                DESCENT_MIN_DROP, DESCENT_QUARTER_DEGREES, false, this.descentPoseCheck());
        if (arc.cleanSteps() <= 0 || arc.degrees() < DESCENT_QUARTER_DEGREES - 1.0) {
            String fail = arc.steps().isEmpty() ? "nosteps"
                    : arc.steps().getLast().failure();
            this.climbWhy(direction, "steparcdirty", String.format(
                    "deg=%.1f steps=%d stop=%s fail=%s pen=%.5f pivot=%.3f,%.3f,%.3f rise=%.3f",
                    arc.degrees(), arc.cleanSteps(), arc.stop(), fail, arc.worstPenetration(),
                    world.x, world.y, world.z, step.rise()));
            return false;
        }
        this.setClimbingDirection(direction);
        this.stepLifting = true;
        this.stepTarget = this.getY() + step.rise();
        this.stepSeatedY = this.getY();
        this.climbPivotSequence = true;
        this.climbPivotDirection = direction;
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(DATA_STEP_PHASE, true);
        if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=rise dir={} rise={} deg={} pos={}",
                this.getId(), direction, String.format("%.3f", step.rise()),
                String.format("%.1f", arc.degrees()),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return true;
    }

    private ClimbPivotAdvance advanceStepApproach(final Direction direction, final Vec3 shapePivot) {
        double along = direction.getStepX() * this.getX() + direction.getStepZ() * this.getZ();
        double left = this.stepTarget - along;
        if (left > CLIMB_PIVOT_CONTACT) {
            double push = Math.min(left, STEP_APPROACH_RATE);
            Vec3 ahead = this.position().add(direction.getStepX() * push, 0.0,
                    direction.getStepZ() * push);
            if (LivingBlockCollisionHandler.climbPoseFailure(this, this.rotation, ahead,
                    CLIMB_PIVOT_SLOP) != null) {
                this.stepApproaching = false;
                this.climbWhy(direction, "stepnearblocked",
                        String.format("left=%.4f", left));
                this.clearClimbPivot();
                return ClimbPivotAdvance.BLOCKED;
            }
            this.setPos(ahead.x, ahead.y, ahead.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.climbPivotMovedThisTick = true;
            return ClimbPivotAdvance.MOVED;
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.stepApproaching = false;
        LivingBlockStep.Verdict step = this.lowStepVerdict(direction);
        if (!step.ok()) {
            this.climbWhy(direction, "steplostonapproach", step.reason());
            this.clearClimbPivot();
            return ClimbPivotAdvance.BLOCKED;
        }
        LivingBlockPivot.WallContact wall = LivingBlockCollisionHandler.climbWallContact(
                this, direction, DESCENT_EDGE_REACH);
        double touch = wall == null ? Double.POSITIVE_INFINITY : this.wallTouch(direction, wall);
        if (!LivingBlockPivot.wallTouched(touch, 0.0, CLIMB_PIVOT_CONTACT, true)) {
            this.climbWhy(direction, "stepnotouchatend", String.format(
                    "touch=%.5f contact=%.5f left=%.5f", touch, CLIMB_PIVOT_CONTACT, left));
            this.clearClimbPivot();
            return ClimbPivotAdvance.BLOCKED;
        }
        this.stepLifting = true;
        this.stepTarget = this.getY() + step.rise();
        this.stepSeatedY = this.getY();
        if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=rise dir={} rise={} pos={}",
                this.getId(), direction, String.format("%.3f", step.rise()),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return ClimbPivotAdvance.MOVED;
    }

    private ClimbPivotAdvance advanceStepLift(final Direction direction, final Vec3 shapePivot) {
        double left = this.stepTarget - this.getY();
        if (left > 1.0E-3) {
            Vec3 lifted = this.position().add(0.0, Math.min(left, STEP_PHASE_RATE), 0.0);
            if (LivingBlockCollisionHandler.climbPoseFailure(this, this.rotation, lifted,
                    CLIMB_PIVOT_SLOP) != null) {
                this.stepLifting = false;
                this.climbWhy(direction, "stepriseblocked",
                        String.format("left=%.4f", left));
                this.clearClimbPivot();
                return ClimbPivotAdvance.BLOCKED;
            }
            this.setPos(lifted.x, lifted.y, lifted.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.climbPivotMovedThisTick = true;
            return ClimbPivotAdvance.MOVED;
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.stepLifting = false;
        this.stepHolding = true;
        this.stepHoldToArc = true;
        this.stepHold = 0;
        if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=pause dir={} before=tip pos={}",
                this.getId(), direction,
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return ClimbPivotAdvance.MOVED;
    }

    private ClimbPivotAdvance advanceStepHold(final Direction direction, final Vec3 shapePivot) {
        this.setDeltaMovement(Vec3.ZERO);
        this.climbPivotMovedThisTick = true;
        if (++this.stepHold < QUARTER_HOLD_TICKS) {
            return ClimbPivotAdvance.MOVED;
        }
        this.stepHolding = false;
        this.stepHold = 0;
        if (!this.stepHoldToArc) {
            this.stepDropping = true;
            if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=fall dir={} pos={}",
                    this.getId(), direction,
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            return ClimbPivotAdvance.MOVED;
        }
        double provedTouch = Double.NaN;
        if (!Double.isNaN(this.stepSeatedY)) {
            LivingBlockPivot.WallContact riser = LivingBlockCollisionHandler.climbWallContact(
                    this, direction, DESCENT_EDGE_REACH);
            Vec3 proofPose = new Vec3(this.getX(), this.stepSeatedY, this.getZ());
            provedTouch = riser == null ? Double.NaN
                    : LivingBlockPivot.wallSeparation(this.getShapeBoxes(), shapePivot,
                            this.rotation, proofPose, riser.surface(), direction);
            if (!LivingBlockPivot.stepCornerHeld(this.getShapeBoxes(), shapePivot, this.rotation,
                    proofPose, riser, direction, CLIMB_PIVOT_CONTACT)) {
                this.climbWhy(direction, "stepnotouchattip", String.format(
                        "touch=%s seatedy=%.5f hullgap=%s contact=%.5f",
                        riser == null ? "-" : String.format("%.5f",
                                LivingBlockPivot.wallSeparation(this.getShapeBoxes(), shapePivot,
                                        this.rotation, proofPose, riser.surface(), direction)),
                        proofPose.y,
                        riser == null ? "-" : String.format("%.5f", riser.gap()),
                        CLIMB_PIVOT_CONTACT));
                this.clearClimbPivot();
                return ClimbPivotAdvance.BLOCKED;
            }
        }
        LivingBlockPivot.WallPivot seated = this.stepEdgePivot(direction, this.position());
        if (seated == null) {
            this.climbWhy(direction, "stepcornerlost", "");
            this.clearClimbPivot();
            return ClimbPivotAdvance.BLOCKED;
        }
        Vec3 world = seated.world();
        this.descentQuarterOnly = true;
        if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=tip dir={} touch={} contact={} corner={} pos={}",
                this.getId(), direction,
                Double.isNaN(provedTouch) ? "noriser" : String.format("%.5f", provedTouch),
                String.format("%.5f", CLIMB_PIVOT_CONTACT),
                String.format("%.3f,%.3f,%.3f", world.x, world.y, world.z),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return this.startPivotSequence(seated,
                new ClimbArc(true, CLIMB_PIVOT_STEPS, false, DESCENT_QUARTER_DEGREES, ""),
                direction, shapePivot, true);
    }

    private ClimbPivotAdvance advanceStepDrop(final Direction direction) {
        Vec3 lower = this.position().subtract(0.0, STEP_PHASE_RATE, 0.0);
        if (LivingBlockCollisionHandler.climbPoseFailure(this, this.rotation, lower,
                CLIMB_PIVOT_SLOP) == null) {
            this.setPos(lower.x, lower.y, lower.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.climbPivotMovedThisTick = true;
            return ClimbPivotAdvance.MOVED;
        }
        this.stepDropping = false;
        this.quarterReleaseTick = this.tickCount;
        this.descentQuarterOnly = false;
        this.climbPivotVisualReleaseTick = this.tickCount + 1;
        if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=seat dir={} pos={}",
                this.getId(), direction,
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        return ClimbPivotAdvance.COMPLETE;
    }

    public boolean canBeginClimbPivot(final Direction direction) {
        if (this.level().isClientSide() || !this.usesClimbPivot()
                || !direction.getAxis().isHorizontal()) {
            return false;
        }
        LivingBlockPivot.WallContact wall = LivingBlockCollisionHandler.climbWallContact(
                this, direction, CLIMB_PIVOT_REACH);
        if (wall == null) {
            this.logClimbCaptureMiss(direction, "wall", Double.NaN);
            return false;
        }
        Vec3 shapePivot = this.shapePivot();
        if (!LivingBlockPivot.wallCapturable(this.getShapeBoxes(), this.getShapeBounds(),
                shapePivot, this.rotation, this.position(), direction, wall, CLIMB_PIVOT_CONTACT,
                CLIMB_PIVOT_REACH)) {
            this.logClimbCaptureMiss(direction,
                    wall.gap() > CLIMB_PIVOT_CONTACT ? "edge" : "touch", wall.gap());
            return false;
        }
        return true;
    }

    public @Nullable Direction climbPivotDirection(final Direction preferred) {
        if (this.level().isClientSide() || !this.usesClimbPivot()
                || !preferred.getAxis().isHorizontal()) {
            return null;
        }
        Direction direction = LivingBlockCollisionHandler.climbWallDirection(
                this, preferred, CLIMB_PIVOT_REACH, this.isLeashed());
        if (direction == null) {
            this.logClimbCaptureMiss(preferred, "wall", Double.NaN);
            return null;
        }
        if (!this.canBeginClimbPivot(direction)) {
            return null;
        }
        if (direction != preferred) {
            if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=redirect wanted={} took={} leashed={} pos={}",
                    this.getId(), preferred, direction, this.isLeashed(),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        }
        return direction;
    }

    private void logClimbCaptureMiss(final Direction direction, final String reason, final double gap) {
        if (this.tickCount - this.climbPivotLogTick < ROT_LOG_INTERVAL) {
            return;
        }
        this.climbPivotLogTick = this.tickCount;
        boolean wallMiss = "wall".equals(reason);
        if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=capturemiss dir={} reason={} subcause={} candidates={} distance={} gap={} overlap={} ground={} leashed={} pos={}",
                this.getId(), direction, reason,
                wallMiss ? String.format("noterrain=%d yband=%d across=%d reach=%d nearestgap=%s",
                        LivingBlockPivot.rejNoTerrain, LivingBlockPivot.rejYBand,
                        LivingBlockPivot.rejAcross, LivingBlockPivot.rejReach,
                        Double.isNaN(LivingBlockPivot.rejNearestGap) ? "nan"
                                : String.format("%.5f", LivingBlockPivot.rejNearestGap)) : "-",
                LivingBlockPivot.lastWallCandidates,
                String.format("%.4f", LivingBlockPivot.lastWallDistance),
                Double.isFinite(gap) ? String.format("%.4f", gap) : "nan",
                Double.isNaN(LivingBlockPivot.lastWallOverlap) ? "nan"
                        : String.format("%.4f", LivingBlockPivot.lastWallOverlap),
                this.onGround(), this.isLeashed(),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
    }

    public void escalateBlockedClimb() {
        this.nudgeUntil = -1;
    }

    private double penetrationNow() {
        LivingBlockCollisionHandler.ClimbPoseFailure current =
                LivingBlockCollisionHandler.climbPoseFailure(this, this.rotation, this.position(), 0.0);
        return current == null ? 0.0 : current.depth();
    }

    public void releaseClimbPivot() {
        this.clearClimbPivot();
    }

    @Override
    public boolean isNoGravity() {
        return super.isNoGravity() || this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE)
                || this.entityData.get(DATA_STEP_PHASE) || this.climbPivotSequence;
    }

    public boolean justReleasedQuarter() {
        return this.quarterReleaseTick >= 0 && this.tickCount - this.quarterReleaseTick <= 1;
    }

    public boolean hasActiveClimbPivot() {
        return this.climbPivotSequence;
    }

    public Vec3 climbRenderOffset(final Quaternionf interpolated, final float partialTicks) {
        if (!this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE) || !this.usesClimbPivot()) {
            return Vec3.ZERO;
        }
        Vector3f stored = this.entityData.get(DATA_CLIMB_PIVOT_LOCAL);
        Vector3f storedWorld = this.entityData.get(DATA_CLIMB_PIVOT_WORLD);
        Vec3 local = new Vec3(stored.x, stored.y, stored.z);
        Vec3 world = new Vec3(storedWorld.x, storedWorld.y, storedWorld.z);
        Vec3 shapePivot = this.shapePivot();
        Vec3 linear = new Vec3(
                Mth.lerp((double)partialTicks, this.xo, this.getX()),
                Mth.lerp((double)partialTicks, this.yo, this.getY()),
                Mth.lerp((double)partialTicks, this.zo, this.getZ()));
        return LivingBlockPivot.renderOffset(this.getShapeBoxes(), shapePivot,
                interpolated, local, world, linear);
    }

    public enum ClimbPivotAdvance {
        MOVED,
        APPROACHING,
        ALIGNING,
        BLOCKED,
        RELEASED,
        COMPLETE;

        public boolean chargesMover() {
            return this == BLOCKED;
        }
    }

    private record ClimbArc(boolean clear, int steps, boolean top, double degrees, String refusal) {
    }

    private static final double BODY_ANCHOR_PROBE = 0.25;
    private static final double BODY_ANCHOR_TOLERANCE = 1.0E-6;
    private static final double BODY_ANCHOR_HOLD = LivingBlockPivot.CONTACT;

    private static final int BEAD_WHY_INTERVAL = 20;

    public boolean beadWhyDue(final String gate) {
        if (this.level().isClientSide()) {
            return false;
        }
        Integer last = this.beadWhyTicks.get(gate);
        return last == null || this.tickCount - last >= BEAD_WHY_INTERVAL;
    }

    public void beadWhy(final String gate, final String extra) {
        if (!this.beadWhyDue(gate)) {
            return;
        }
        this.beadWhyTicks.put(gate, this.tickCount);
        if (shouldLog) LOGGER.debug("[livingblock] bodyanchor id={} gate={} {} tilt={} settled={} ground={} seq={} pos={}",
                this.getId(), gate, extra, String.format("%.2f", this.tiltDegrees()),
                this.isOrientationSettled(), this.onGround(), this.climbPivotSequence,
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
    }

    private boolean anchorIsBody(final Vec3 point) {
        AABB probe = new AABB(point, point).inflate(BODY_ANCHOR_PROBE);
        return LivingBlockPivot.anchorHeld(
                LivingBlockCollisionHandler.bodyTerrain(this, probe).boxes(), point,
                BODY_ANCHOR_TOLERANCE);
    }

    private double anchorGap(final Vec3 point) {
        AABB probe = new AABB(point, point).inflate(BODY_ANCHOR_PROBE);
        List<AABB> around = new ArrayList<>(
                LivingBlockCollisionHandler.bodyTerrain(this, probe).boxes());
        for (VoxelShape shape : this.level().getBlockCollisions(this, probe)) {
            around.addAll(shape.toAabbs());
        }
        return LivingBlockPivot.anchorDistance(around, point);
    }

    private static final int ANCHOR_NONE = -1;
    private static final int ANCHOR_UNIDENTIFIED = -2;

    private void claimAnchor(final Vec3 world, final boolean bodySuspected) {
        this.climbPivotAnchorId = ANCHOR_NONE;
        this.climbPivotAnchorLocal = null;
        AABB probe = new AABB(world, world).inflate(BODY_ANCHOR_PROBE);
        LivingBlock owner = LivingBlockCollisionHandler.anchorOwner(this, probe, world,
                BODY_ANCHOR_TOLERANCE);
        if (owner != null) {
            this.climbPivotAnchorId = owner.getId();
            this.climbPivotAnchorLocal = LivingBlockPivot.localPoint(owner.getShapeBoxes(),
                    owner.shapePivot(), owner.getRotation(), owner.position(), world);
            this.beadWhy("anchorowner", String.format(
                    "owner=%d point=%.3f,%.3f,%.3f local=%.3f,%.3f,%.3f",
                    owner.getId(), world.x, world.y, world.z, this.climbPivotAnchorLocal.x,
                    this.climbPivotAnchorLocal.y, this.climbPivotAnchorLocal.z));
            return;
        }
        List<AABB> blocks = new ArrayList<>();
        for (VoxelShape shape : this.level().getBlockCollisions(this, probe)) {
            blocks.addAll(shape.toAabbs());
        }
        if (LivingBlockPivot.anchorHeld(blocks, world, BODY_ANCHOR_TOLERANCE)) {
            return;
        }
        this.climbPivotAnchorId = bodySuspected ? ANCHOR_UNIDENTIFIED : ANCHOR_NONE;
        if (bodySuspected) {
            this.beadWhy("anchornoid", String.format(
                    "point=%.3f,%.3f,%.3f", world.x, world.y, world.z));
        }
    }

    private @Nullable ClimbPivotAdvance holdAnchor(final Direction direction) {
        if (this.climbPivotAnchorId == ANCHOR_UNIDENTIFIED || this.climbPivotAnchorLocal == null) {
            double gap = this.anchorGap(this.climbPivot.world());
            if (gap <= BODY_ANCHOR_HOLD) {
                return null;
            }
            if (this.beadWhyDue("anchorlost")) {
                this.beadWhy("anchorlost", String.format(
                        "dir=%s step=%d/%d left=%.4f pen=%.5f point=%.3f,%.3f,%.3f",
                        direction, this.climbPivotStep, this.climbPivotLimit, gap,
                        this.penetrationNow(), this.climbPivot.world().x,
                        this.climbPivot.world().y, this.climbPivot.world().z));
            }
            this.clearClimbPivot();
            return ClimbPivotAdvance.RELEASED;
        }
        Vec3 held = this.climbPivot.world();
        if (!(this.level().getEntity(this.climbPivotAnchorId) instanceof LivingBlock owner)
                || !owner.isAlive()) {
            this.beadWhy("anchornoowner", String.format("owner=%d point=%.3f,%.3f,%.3f",
                    this.climbPivotAnchorId, held.x, held.y, held.z));
            this.clearClimbPivot();
            return ClimbPivotAdvance.RELEASED;
        }
        double moved = LivingBlockPivot.anchorDrift(owner.getShapeBoxes(), owner.shapePivot(),
                owner.getRotation(), owner.position(), this.climbPivotAnchorLocal, held);
        if (moved > BODY_ANCHOR_HOLD) {
            this.beadWhy("anchorjumped", String.format(
                    "owner=%d moved=%.4f limit=%.4f point=%.3f,%.3f,%.3f",
                    this.climbPivotAnchorId, moved, BODY_ANCHOR_HOLD, held.x, held.y, held.z));
            this.clearClimbPivot();
            return ClimbPivotAdvance.RELEASED;
        }
        if (moved > BODY_ANCHOR_TOLERANCE) {
            Vec3 now = LivingBlockPivot.worldPoint(owner.getShapeBoxes(), owner.shapePivot(),
                    owner.getRotation(), owner.position(), this.climbPivotAnchorLocal);
            this.climbPivot = new LivingBlockPivot.WallPivot(this.climbPivot.local(), now,
                    this.climbPivot.surface());
            this.entityData.set(DATA_CLIMB_PIVOT_WORLD,
                    new Vector3f((float)now.x, (float)now.y, (float)now.z));
            this.beadWhy("anchorfollow", String.format("owner=%d moved=%.5f point=%.3f,%.3f,%.3f",
                    this.climbPivotAnchorId, moved, now.x, now.y, now.z));
        }
        return null;
    }

    public void climbWhy(final Direction direction, final String gate, final String extra) {
        if (this.level().isClientSide()) {
            return;
        }
        boolean changed = !gate.equals(this.climbWhyLast);
        if (!changed && this.tickCount - this.climbWhyTick < 20) {
            return;
        }
        this.climbWhyLast = gate;
        this.climbWhyTick = this.tickCount;
        AABB hull = this.getBoundingBox();
        LivingBlockCollisionShapes.Placement placement =
                LivingBlockCollisionShapes.preciseGeometry(this);
        AABB obb = null;
        for (OrientedBox box : placement.obbs()) {
            AABB world = box.getWorldAABB();
            obb = obb == null ? world : obb.minmax(world);
        }
        double hullFace = obb == null ? Double.NaN : switch (direction) {
            case EAST -> hull.maxX;
            case WEST -> hull.minX;
            case SOUTH -> hull.maxZ;
            default -> hull.minZ;
        };
        double obbFace = obb == null ? Double.NaN : switch (direction) {
            case EAST -> obb.maxX;
            case WEST -> obb.minX;
            case SOUTH -> obb.maxZ;
            default -> obb.minZ;
        };
        if (shouldLog) LOGGER.debug("[livingblock] climbwhy id={} dir={} gate={} {} usespivot={} climbing={} seq={} align={} ground={} leash={} tilt={} pennow={} hullface={} obbface={} pieces={} vel={} pos={}",
                this.getId(), direction, gate, extra, this.usesClimbPivot(), this.isClimbing(),
                this.climbPivotSequence, this.climbPivotAligning, this.onGround(), this.isLeashed(),
                String.format("%.2f", this.tiltDegrees()),
                String.format("%.5f", this.penetrationNow()),
                String.format("%.5f", hullFace), String.format("%.5f", obbFace),
                placement.obbs().size(),
                String.format("%.4f", this.getDeltaMovement().horizontalDistance()),
                String.format("%.4f,%.4f,%.4f", this.getX(), this.getY(), this.getZ()));
    }

    public void descentWrite(final String who) {
        if (!this.descentSequence || this.level().isClientSide()) {
            return;
        }
        Vec3 now = this.position();
        double moved = now.distanceTo(this.descentWritePos);
        double turned = angleBetween(this.descentWriteRot, this.rotation);
        this.descentWritePos = now;
        this.descentWriteRot.set(this.rotation);
        if (moved <= 1.0E-9 && turned <= 1.0E-6) {
            return;
        }
        if (shouldLog) LOGGER.debug("[livingblock] descwrite id={} t={} who={} moved={} turned={} pos={} step={}/{} pivotmoved={} ground={}",
                this.getId(), this.tickCount, who, String.format("%.6f", moved),
                String.format("%.4f", turned),
                String.format("%.5f,%.5f,%.5f", now.x, now.y, now.z),
                this.climbPivotStep, this.climbPivotLimit, this.climbPivotMovedThisTick,
                this.onGround());
    }

    public void descentRoute(final String phase, final int moveTicks, final boolean on,
                             final boolean climbing, final Direction direction,
                             final int fails, final String mover) {
        if (this.level().isClientSide()) {
            return;
        }
        String verdict = phase + ":" + (on ? "1" : "0") + (climbing ? "e" : "-")
                + (this.onGround() ? "c" : "-");
        if (verdict.equals(this.descentRouteLast)
                && this.tickCount - this.descentRouteTick < DESCENT_DUMP_INTERVAL) {
            return;
        }
        this.descentRouteLast = verdict;
        this.descentRouteTick = this.tickCount;
        if (shouldLog) LOGGER.debug("[livingblock] descroute id={} t={} phase={} dir={} on={} moveTicks={} climbing={} ground={} fails={} mover={} nudge={} fluid={} seq={} pivotmoved={} vel={} pos={}",
                this.getId(), this.tickCount, phase, direction, on, moveTicks, climbing,
                this.onGround(), fails, mover, this.isNudging(), this.isInWater(),
                this.climbPivotSequence, this.climbPivotMovedThisTick,
                String.format("%.5f", this.getDeltaMovement().horizontalDistance()),
                String.format("%.4f,%.4f,%.4f", this.getX(), this.getY(), this.getZ()));
    }

    public void noteClimbRoute(final String route) {
        this.climbFlowRoutes++;
        this.climbFlowLastRoute = route;
    }

    public void noteClimbOrder() {
        this.climbFlowOrdered = true;
    }

    private void climbFlow(final String mark, final Direction direction) {
        if (this.level().isClientSide()) {
            return;
        }
        int since = this.climbFlowTick == Integer.MIN_VALUE ? -1 : this.tickCount - this.climbFlowTick;
        BlockPos below = this.getOnPos();
        if (shouldLog) LOGGER.debug("[livingblock] climbflow id={} mark={} dir={} sincelast={} routes={} lastroute={} order={} rose={} support={} ground={} pos={}",
                this.getId(), mark, direction, since, this.climbFlowRoutes,
                this.climbFlowLastRoute, this.climbFlowOrdered,
                this.climbFlowTick == Integer.MIN_VALUE ? "nan"
                        : String.format("%.3f", this.getY() - this.climbFlowY),
                this.level().getBlockState(below).getBlock().getDescriptionId(),
                this.onGround(),
                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        this.climbFlowTick = this.tickCount;
        this.climbFlowY = this.getY();
        this.climbFlowRoutes = 0;
        this.climbFlowLastRoute = "-";
        this.climbFlowOrdered = false;
    }

    public ClimbPivotAdvance advanceClimbPivot(final Direction direction) {
        ClimbPivotAdvance advance = this.advanceClimbPivotStep(direction);
        this.climbPivotStalled = advance == ClimbPivotAdvance.BLOCKED && this.climbPivotSequence;
        this.logPivotPhase(advance, direction);
        return advance;
    }

    private void logPivotPhase(final ClimbPivotAdvance advance, final Direction direction) {
        if (this.usesOrientedCollision() || this.level().isClientSide()) {
            return;
        }
        String phase = advance + "/" + direction + "/" + this.climbPivotSequence
                + "/" + (int) (this.climbPivotDegreesRemaining / PIVOT_LOG_DEGREE_BUCKET);
        if (phase.equals(this.pivotPhaseLast)) {
            return;
        }
        this.pivotPhaseLast = phase;
        if (shouldLog) LOGGER.debug("[livingblock] pivot id={} phase={} dir={} step={}/{} degrees={} moved={}",
                this.getId(), advance, direction, this.climbPivotStep, this.climbPivotLimit,
                String.format("%.2f", this.climbPivotDegreesRemaining), this.climbPivotMovedThisTick);
    }

    private ClimbPivotAdvance advanceClimbPivotStep(final Direction direction) {
        if (this.level().isClientSide() || !this.usesClimbPivot()
                || !direction.getAxis().isHorizontal()) {
            if (!this.level().isClientSide()) {
                this.climbWhy(direction, this.usesClimbPivot() ? "dirnothorizontal" : "nopivot", "");
            }
            return ClimbPivotAdvance.BLOCKED;
        }
        Vec3 shapePivot = this.shapePivot();
        if (this.climbPivotAnchorId != ANCHOR_NONE && this.climbPivot != null) {
            ClimbPivotAdvance released = this.holdAnchor(direction);
            if (released != null) {
                return released;
            }
        }
        if (this.stepApproaching && this.climbPivotDirection == direction) {
            return this.advanceStepApproach(direction, shapePivot);
        }
        if (this.stepLifting && this.climbPivotDirection == direction) {
            return this.advanceStepLift(direction, shapePivot);
        }
        if (this.stepHolding && this.climbPivotDirection == direction) {
            return this.advanceStepHold(direction, shapePivot);
        }
        if (this.stepDropping && this.climbPivotDirection == direction) {
            return this.advanceStepDrop(direction);
        }
        if (this.climbPivotAligning && this.climbPivot != null
                && this.climbPivotDirection == direction) {
            return this.advanceClimbAlignment(direction, shapePivot);
        }
        if (this.climbPivot == null || this.climbPivotDirection != direction) {
            this.setBoundingBox(this.makeBoundingBox());
            LivingBlockPivot.WallContact wall = LivingBlockCollisionHandler.climbWallContact(
                    this, direction, CLIMB_PIVOT_REACH);
            boolean bodyWall = LivingBlockCollisionHandler.lastWallFromBody();
            double faceLeft = wall == null ? Double.NaN
                    : wall.surface().maxY() - this.getBoundingBox().minY;
            double bodyRise = this.getBaseShapeBounds().getYsize();
            if (wall != null && faceLeft > LivingBlockPivot.SLOP
                    && faceLeft < bodyRise - LivingBlockPivot.SLOP) {
                LivingBlockPivot.WallPivot half = LivingBlockPivot.wallPivot(
                        this.getShapeBoxes(), this.getShapeBounds(), shapePivot, this.rotation,
                        this.position(), direction, wall.surface(), CLIMB_PIVOT_CONTACT);
                ClimbArc halfArc = half == null ? null : this.climbArc(half, direction, shapePivot);
                if (shouldLog) LOGGER.debug("[livingblock] halfquarter id={} dir={} left={} surface={} edge={} arc={} steps={} top={} deg={} why={}",
                        this.getId(), direction,
                        String.format("%.4f/%.3f", faceLeft, bodyRise),
                        String.format("plane=%.3f y=%.3f..%.3f across=%.3f..%.3f",
                                wall.surface().plane(), wall.surface().minY(), wall.surface().maxY(),
                                wall.surface().minAcross(), wall.surface().maxAcross()),
                        half == null ? "none" : String.format("%.3f,%.3f,%.3f",
                                half.world().x, half.world().y, half.world().z),
                        halfArc == null ? "none" : String.valueOf(halfArc.clear()),
                        halfArc == null ? -1 : halfArc.steps(),
                        halfArc == null ? "-" : String.valueOf(halfArc.top()),
                        halfArc == null ? "-" : String.format("%.2f", halfArc.degrees()),
                        halfArc == null ? "-" : halfArc.refusal());
                if (halfArc == null || !halfArc.clear()) {
                    this.climbWhy(direction, "partialrise", String.format("left=%.3f", faceLeft));
                    this.clearClimbPivot();
                    return ClimbPivotAdvance.RELEASED;
                }
                this.climbWhy(direction, "partialtake", String.format("left=%.3f top=%b",
                        faceLeft, halfArc.top()));
            }
            if (wall == null) {
                this.climbApproachTicks = 0;
                ClimbPivotAdvance continued = this.continueDescent(direction, shapePivot);
                if (continued != null) {
                    return continued;
                }
                if (!this.usesOrientedCollision()) {
                    this.climbWhy(direction, "aabbdrop", "descent=off");
                    return ClimbPivotAdvance.BLOCKED;
                }
                DescentLook look = this.descentHeldLook != null
                        && this.descentHeldDir == direction
                        && this.tickCount - this.descentHeldTick <= 4
                        ? this.descentHeldLook : this.lookDescent(direction);
                this.descentHeldLook = null;
                if (this.climbingNotDescending(direction)) {
                    this.climbWhy(direction, "stepahead", "");
                    return ClimbPivotAdvance.BLOCKED;
                }
                boolean quarter = this.lowStepDrop(look, direction);
                LivingBlockPivot.DescentPlan drop = look.probe().ok()
                        ? this.descentPlan(look, direction, !quarter) : null;
                if (drop != null && drop.cleanSteps() > 0) {
                    this.descentDump(direction, "install", look, drop);
                    this.climbWhy(direction, "descent", String.format(
                            "edge=%.3f,%.3f,%.3f deg=%.2f steps=%d",
                            look.probe().pivot().world().x, look.probe().pivot().world().y,
                            look.probe().pivot().world().z, drop.degrees(), drop.cleanSteps()));
                    this.descentWall = look.probe().pivot().surface();
                    this.descentWallBody = this.anchorIsBody(look.probe().pivot().world());
                    this.descentWallDir = direction;
                    this.descentWallTick = this.tickCount;
                    this.descentQuarterOnly = quarter;
                    ClimbPivotAdvance installed = this.startPivotSequence(look.probe().pivot(),
                            new ClimbArc(true, drop.cleanSteps(), !quarter && drop.landed(),
                                    drop.degrees(), ""),
                            direction, shapePivot, descentTouches(drop));
                    return installed == ClimbPivotAdvance.MOVED
                            ? this.advanceClimbPivot(direction) : installed;
                }
                this.descentDump(direction, look.probe().ok()
                        ? "arc:" + (drop == null ? "noplan" : drop.stop())
                        : look.probe().reason(), look, drop);
                this.climbWhy(direction, "nowall", "reach=" + CLIMB_PIVOT_REACH);
                return ClimbPivotAdvance.BLOCKED;
            }
            double touch = this.wallTouch(direction, wall);
            if (this.position().distanceToSqr(this.climbApproachFrom) > 1.0E-8) {
                this.climbApproachTicks = 0;
            }
            this.climbApproachFrom = this.position();
            boolean parked = this.climbApproachTicks >= CLIMB_APPROACH_STUCK;
            if (!LivingBlockPivot.wallTouched(touch, wall.gap(), CLIMB_PIVOT_CONTACT, parked)) {
                this.climbApproachTicks = 0;
                this.climbWhy(direction, "wallnotouch", String.format(
                        "touch=%.5f hullgap=%.5f contact=%.5f parked=%b", touch, wall.gap(),
                        CLIMB_PIVOT_CONTACT, parked));
                return ClimbPivotAdvance.BLOCKED;
            }
            double reach = parked ? Math.max(CLIMB_PIVOT_CONTACT, wall.gap() + DEPEN_EPSILON)
                    : CLIMB_PIVOT_CONTACT;
            if (parked) {
                this.climbWhy(direction, "solverparked", String.format(
                        "gap=%.5f reach=%.5f ticks=%d", wall.gap(), reach, this.climbApproachTicks));
                this.climbApproachTicks = 0;
            }
            if (!parked && wall.gap() > CLIMB_PIVOT_CONTACT) {
                this.climbApproachTicks++;
                this.climbWhy(direction, "nearing", String.format(
                        "gap=%.5f contact=%.5f plane=%.4f yband=%.4f..%.4f ticks=%d moved=%.5f",
                        wall.gap(), CLIMB_PIVOT_CONTACT, wall.surface().plane(),
                        wall.surface().minY(), wall.surface().maxY(), this.climbApproachTicks,
                        this.position().distanceTo(this.climbApproachFrom)));
                double approach = Math.min(wall.gap(), CLIMB_APPROACH_SPEED);
                Vec3 velocity = this.getDeltaMovement();
                this.setDeltaMovement(direction.getStepX() * approach, velocity.y,
                        direction.getStepZ() * approach);
                return ClimbPivotAdvance.APPROACHING;
            }
            this.climbApproachTicks = 0;
            LivingBlockPivot.WallPivot candidate = LivingBlockPivot.wallPivot(
                    this.getShapeBoxes(), this.getShapeBounds(), shapePivot, this.rotation,
                    this.position(), direction, wall.surface(), reach);
            ClimbArc arc = candidate == null ? null
                    : this.climbArc(candidate, direction, shapePivot);
            if (arc != null && arc.clear() && !arc.top()
                    && faceLeft <= LivingBlockPivot.SLOP) {
                arc = new ClimbArc(true, arc.steps(), true, arc.degrees(), arc.refusal());
            }
            boolean tiltResidue = this.tiltDegrees() > CLIMB_ALIGN_MIN_TILT;
            if (candidate != null && (arc == null || !arc.clear() && tiltResidue)) {
                Quaternionf target = this.climbAlignmentTarget(candidate, shapePivot);
                if (target == null) {
                    if (this.tickCount - this.climbPivotLogTick >= ROT_LOG_INTERVAL) {
                        this.climbPivotLogTick = this.tickCount;
                        if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=alignrefused dir={} tilt={} pennow={} candidates={} distance={} gap={} pos={}",
                                this.getId(), direction, String.format("%.1f", this.tiltDegrees()),
                                String.format("%.4f", this.penetrationNow()),
                                LivingBlockPivot.lastWallCandidates,
                                String.format("%.4f", LivingBlockPivot.lastWallDistance),
                                String.format("%.4f", wall.gap()),
                                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                    }
                    return ClimbPivotAdvance.BLOCKED;
                }
                this.climbPivotSequence = true;
                this.climbPivotAligning = true;
                this.climbPivot = candidate;
                this.claimAnchor(candidate.world(), bodyWall);
                this.climbPivotDirection = direction;
                this.climbPivotAlignmentTarget.set(target);
                this.climbPivotStep = 0;
                this.climbPivotDegreesTotal = angleBetween(this.rotation, target);
                this.climbPivotDegreesRemaining = this.climbPivotDegreesTotal;
                this.climbPivotLimit = Math.max(1, (int)Math.ceil(
                        (this.climbPivotDegreesTotal - 1.0E-6) / LivingBlockPivot.DEGREES));
                this.climbPivotCompletes = false;
                this.climbPivotEdgeStartTick = this.tickCount;
                this.climbPivotLastAdvanceTick = this.tickCount - 1;
                this.climbPivotMaxAdvanceGap = 0;
                this.climbPivotRuntimeDrift = 0.0;
                this.climbPivotExpectedPosition = this.position();
                this.nudgeUntil = -1;
                this.solverBlockedTicks = 0;
                this.rollCarryX = 0.0F;
                this.rollCarryZ = 0.0F;
                this.setDeltaMovement(Vec3.ZERO);
                this.pendingFlop = false;
                Vec3 local = candidate.local();
                this.entityData.set(DATA_CLIMB_PIVOT_LOCAL,
                        new Vector3f((float)local.x, (float)local.y, (float)local.z));
                Vec3 world = candidate.world();
                this.entityData.set(DATA_CLIMB_PIVOT_WORLD,
                        new Vector3f((float)world.x, (float)world.y, (float)world.z));
                this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, true);
                this.climbFlow("alignstart", direction);
                if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=alignstart dir={} edge={} steps={} angle={} pos={}",
                        this.getId(), direction,
                        String.format("%.3f,%.3f,%.3f", world.x, world.y, world.z),
                        this.climbPivotLimit, String.format("%.2f", this.climbPivotDegreesTotal),
                        String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                return this.advanceClimbAlignment(direction, shapePivot);
            }
            if (candidate == null || arc == null || !arc.clear()) {
                if (this.tickCount - this.climbPivotLogTick >= ROT_LOG_INTERVAL) {
                    this.climbPivotLogTick = this.tickCount;
                    AABB base = this.getBaseShapeBounds();
                    if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=refused dir={} tilt={} pennow={} reason={} base={} pieces={} pos={}",
                            this.getId(), direction, String.format("%.1f", this.tiltDegrees()),
                            String.format("%.4f", this.penetrationNow()),
                            candidate == null ? "edge:c=" + LivingBlockPivot.lastWallCandidates
                                    + ":d=" + String.format("%.4f", LivingBlockPivot.lastWallDistance)
                                    + ":gap=" + String.format("%.4f", wall.gap())
                                    : arc == null ? "alignment" : arc.refusal(),
                            String.format("%.3f,%.3f,%.3f", base.getXsize(), base.getYsize(),
                                    base.getZsize()), this.getShapeBoxes().size(),
                            String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                }
                return ClimbPivotAdvance.BLOCKED;
            }
            if (!arc.top()) {
                this.playClimbFaceSound(candidate, direction);
            }
            boolean continuing = this.climbPivotSequence && !this.climbPivotAligning;
            this.descentSequence = false;
            this.climbPivotAligning = false;
            this.climbPivot = candidate;
            this.claimAnchor(candidate.world(), bodyWall);
            this.climbPivotDirection = direction;
            this.climbPivotStep = 0;
            this.climbPivotLimit = arc.steps();
            this.climbPivotDegreesRemaining = arc.degrees();
            this.climbPivotDegreesTotal = arc.degrees();
            this.climbPivotCompletes = arc.top();
            this.climbPivotSequence = true;
            this.climbPivotEdgeStartTick = this.tickCount;
            if (!continuing) {
                this.climbPivotLastAdvanceTick = this.tickCount - 1;
                this.climbPivotMaxAdvanceGap = 0;
                this.climbPivotRuntimeDrift = 0.0;
                this.climbPivotExpectedPosition = this.position();
            }
            this.nudgeUntil = -1;
            this.solverBlockedTicks = 0;
            this.rollDeltaX = 0.0F;
            this.rollDeltaZ = 0.0F;
            this.rollCarryX = 0.0F;
            this.rollCarryZ = 0.0F;
            this.rollPending = 0.0;
            this.rollPendingAxis = LivingBlockRoll.AXIS_NONE;
            this.climbPivotVisualReleaseTick = -1;
            Vec3 local = candidate.local();
            this.entityData.set(DATA_CLIMB_PIVOT_LOCAL,
                    new Vector3f((float)local.x, (float)local.y, (float)local.z));
            Vec3 world = candidate.world();
            this.entityData.set(DATA_CLIMB_PIVOT_WORLD,
                    new Vector3f((float)world.x, (float)world.y, (float)world.z));
            this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, true);
            this.climbFlow("start", direction);
            if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=start dir={} edge={} face={} left={} steps={} angle={} top={} pos={}",
                    this.getId(), direction,
                    String.format("%.3f,%.3f,%.3f", candidate.world().x, candidate.world().y,
                            candidate.world().z),
                    String.format("%.3f..%.3f a=%.3f..%.3f p=%.3f", wall.surface().minY(),
                            wall.surface().maxY(), wall.surface().minAcross(),
                            wall.surface().maxAcross(), wall.surface().plane()),
                    String.format("%.3f", faceLeft),
                    this.climbPivotLimit, String.format("%.2f", this.climbPivotDegreesTotal),
                    this.climbPivotCompletes,
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        }

        this.climbPivotRuntimeDrift = Math.max(this.climbPivotRuntimeDrift,
                this.position().distanceTo(this.climbPivotExpectedPosition));
        this.climbPivotMaxAdvanceGap = Math.max(this.climbPivotMaxAdvanceGap,
                this.tickCount - this.climbPivotLastAdvanceTick);
        double rate = this.descentQuarterOnly ? LOW_STEP_DEGREES : LivingBlockPivot.DEGREES;
        double stepDegrees = Math.min(rate, this.climbPivotDegreesRemaining);
        LivingBlockPivot.WallTurn midpoint = LivingBlockPivot.wallTurn(
                this.getShapeBoxes(), shapePivot, this.rotation, this.position(), this.climbPivot,
                direction, stepDegrees * 0.5);
        LivingBlockPivot.WallTurn turn = LivingBlockPivot.wallTurn(
                this.getShapeBoxes(), shapePivot, this.rotation, this.position(), this.climbPivot,
                direction, stepDegrees);
        LivingBlockCollisionHandler.ClimbPoseFailure midpointFailure = midpoint == null ? null
                : LivingBlockCollisionHandler.climbPoseFailure(
                        this, midpoint.rotation(), midpoint.position(), CLIMB_PIVOT_SLOP);
        LivingBlockCollisionHandler.ClimbPoseFailure turnFailure = turn == null ? null
                : LivingBlockCollisionHandler.climbPoseFailure(
                        this, turn.rotation(), turn.position(), CLIMB_PIVOT_SLOP);
        if (midpoint == null || midpointFailure != null || turn == null || turnFailure != null) {
            if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=blocked dir={} step={} reason={} gap={} drift={} pos={}",
                    this.getId(), direction, this.climbPivotStep,
                    climbFailure(midpoint, midpointFailure, turn, turnFailure),
                    this.climbPivotMaxAdvanceGap, String.format("%.6f", this.climbPivotRuntimeDrift),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            this.logLanding(direction);
            return ClimbPivotAdvance.BLOCKED;
        }
        this.entityData.set(DATA_ORIENTATION_SETTLED, false);
        this.rotation.set(turn.rotation());
        this.setPos(turn.position());
        this.hasImpulse = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.setOnGround(false);
        this.pendingFlop = false;
        this.climbPivotMovedThisTick = true;
        this.climbPivotLastAdvanceTick = this.tickCount;
        this.climbPivotExpectedPosition = this.position();
        this.climbPivotDegreesRemaining = Math.max(0.0,
                this.climbPivotDegreesRemaining - stepDegrees);
        if (this.descentSequence) {
            this.descentWallTick = this.tickCount;
            this.descentWrite("arc");
            this.descentStepLog(direction, this.climbPivot);
        }
        if (++this.climbPivotStep >= this.climbPivotLimit
                || this.climbPivotDegreesRemaining <= 1.0E-4) {
            boolean complete = this.climbPivotCompletes || this.descentQuarterOnly;
            this.climbFlow(this.climbPivotCompletes ? "top" : "quarter", direction);
            if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event={} dir={} edge={} angle={} ticks={} gap={} drift={} pos={}",
                    this.getId(), this.climbPivotCompletes ? "top"
                            : this.descentQuarterOnly ? "stepquarter" : "quarter", direction,
                    String.format("%.3f,%.3f,%.3f", this.climbPivot.world().x,
                            this.climbPivot.world().y, this.climbPivot.world().z),
                    String.format("%.1f", this.climbPivotDegreesTotal),
                    this.tickCount - this.climbPivotEdgeStartTick + 1,
                    this.climbPivotMaxAdvanceGap, String.format("%.6f", this.climbPivotRuntimeDrift),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            this.logLanding(direction);
            if (complete && this.descentQuarterOnly) {
                this.climbPivot = null;
                this.climbPivotStep = 0;
                this.climbPivotLimit = 0;
                this.climbPivotDegreesRemaining = 0.0;
                this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, false);
                this.entityData.set(DATA_STEP_PHASE, true);
                this.climbPivotVisualReleaseTick = -1;
                this.stepHolding = true;
                this.stepHoldToArc = false;
                this.stepHold = 0;
                if (shouldLog) LOGGER.debug("[livingblock] stepquarter id={} phase=pause dir={} before=fall pos={}",
                        this.getId(), direction,
                        String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                return ClimbPivotAdvance.MOVED;
            }
            if (complete) {
                this.stepHold = 0;
                this.climbPivotVisualReleaseTick = this.tickCount + 1;
                if (this.descentQuarterOnly) {
                    this.quarterReleaseTick = this.tickCount;
                }
                this.descentQuarterOnly = false;
                return ClimbPivotAdvance.COMPLETE;
            }
            this.climbPivot = null;
            this.climbPivotStep = 0;
            this.climbPivotLimit = 0;
            this.climbPivotDegreesRemaining = 0.0;
            this.climbPivotDegreesTotal = 0.0;
            this.climbPivotCompletes = false;
        }
        return ClimbPivotAdvance.MOVED;
    }

    private @Nullable Quaternionf climbAlignmentTarget(final LivingBlockPivot.WallPivot pivot,
                                                        final Vec3 shapePivot) {
        Quaternionf best = null;
        double bestAngle = Double.POSITIVE_INFINITY;
        for (int index = 0; index < LivingBlockOrientation.COUNT; index++) {
            Quaternionf target = LivingBlockOrientation.of(index).quaternion();
            double totalAngle = angleBetween(this.rotation, target);
            if (totalAngle <= 1.0E-4 || totalAngle > 90.0 + 1.0E-4
                    || totalAngle >= bestAngle) {
                continue;
            }
            Quaternionf pose = new Quaternionf(this.rotation).normalize();
            boolean clear = true;
            int steps = 0;
            while (angleBetween(pose, target) > 1.0E-4 && steps++ < 16) {
                double angle = angleBetween(pose, target);
                double alpha = Math.min(1.0, LivingBlockPivot.DEGREES / angle);
                Quaternionf midpoint = new Quaternionf(pose)
                        .slerp(target, (float)(alpha * 0.5)).normalize();
                Quaternionf endpoint = new Quaternionf(pose)
                        .slerp(target, (float)alpha).normalize();
                Vec3 midpointPosition = LivingBlockPivot.positionForPivot(
                        this.getShapeBoxes(), shapePivot, midpoint, pivot.local(), pivot.world());
                Vec3 endpointPosition = LivingBlockPivot.positionForPivot(
                        this.getShapeBoxes(), shapePivot, endpoint, pivot.local(), pivot.world());
                if (LivingBlockCollisionHandler.climbPoseFailure(
                        this, midpoint, midpointPosition, CLIMB_PIVOT_SLOP) != null
                        || LivingBlockCollisionHandler.climbPoseFailure(
                                this, endpoint, endpointPosition, CLIMB_PIVOT_SLOP) != null) {
                    clear = false;
                    break;
                }
                pose = endpoint;
            }
            if (clear && angleBetween(pose, target) <= 1.0E-4) {
                best = new Quaternionf(target);
                bestAngle = totalAngle;
            }
        }
        return best;
    }

    private ClimbPivotAdvance advanceClimbAlignment(final Direction direction,
                                                     final Vec3 shapePivot) {
        this.climbPivotRuntimeDrift = Math.max(this.climbPivotRuntimeDrift,
                this.position().distanceTo(this.climbPivotExpectedPosition));
        this.climbPivotMaxAdvanceGap = Math.max(this.climbPivotMaxAdvanceGap,
                this.tickCount - this.climbPivotLastAdvanceTick);
        double angle = angleBetween(this.rotation, this.climbPivotAlignmentTarget);
        double alpha = Math.min(1.0, LivingBlockPivot.DEGREES / angle);
        Quaternionf midpoint = new Quaternionf(this.rotation)
                .slerp(this.climbPivotAlignmentTarget, (float)(alpha * 0.5)).normalize();
        Quaternionf endpoint = new Quaternionf(this.rotation)
                .slerp(this.climbPivotAlignmentTarget, (float)alpha).normalize();
        Vec3 midpointPosition = LivingBlockPivot.positionForPivot(
                this.getShapeBoxes(), shapePivot, midpoint,
                this.climbPivot.local(), this.climbPivot.world());
        Vec3 endpointPosition = LivingBlockPivot.positionForPivot(
                this.getShapeBoxes(), shapePivot, endpoint,
                this.climbPivot.local(), this.climbPivot.world());
        LivingBlockCollisionHandler.ClimbPoseFailure midpointFailure =
                LivingBlockCollisionHandler.climbPoseFailure(
                        this, midpoint, midpointPosition, CLIMB_PIVOT_SLOP);
        LivingBlockCollisionHandler.ClimbPoseFailure endpointFailure =
                LivingBlockCollisionHandler.climbPoseFailure(
                        this, endpoint, endpointPosition, CLIMB_PIVOT_SLOP);
        if (midpointFailure != null || endpointFailure != null) {
            LivingBlockCollisionHandler.ClimbPoseFailure failure = midpointFailure != null
                    ? midpointFailure : endpointFailure;
            if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=alignblocked dir={} step={} reason={}:{}:{} alpha={} ang={} mid={} end={} pennow={} pos={}",
                    this.getId(), direction, this.climbPivotStep, failure.kind(),
                    String.format("%.4f", failure.depth()), failure.blocker(),
                    String.format("%.4f", alpha), String.format("%.2f", angle),
                    midpointFailure == null ? "free" : String.format("%.4f", midpointFailure.depth()),
                    endpointFailure == null ? "free" : String.format("%.4f", endpointFailure.depth()),
                    String.format("%.4f", this.penetrationNow()),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            return ClimbPivotAdvance.BLOCKED;
        }
        this.entityData.set(DATA_ORIENTATION_SETTLED, false);
        this.rotation.set(endpoint);
        this.setPos(endpointPosition);
        this.setDeltaMovement(Vec3.ZERO);
        this.setOnGround(false);
        this.pendingFlop = false;
        this.climbPivotMovedThisTick = true;
        this.climbPivotLastAdvanceTick = this.tickCount;
        this.climbPivotExpectedPosition = this.position();
        this.climbPivotStep++;
        this.climbPivotDegreesRemaining = Math.max(0.0,
                this.climbPivotDegreesRemaining - Math.min(LivingBlockPivot.DEGREES, angle));
        if (angleBetween(this.rotation, this.climbPivotAlignmentTarget) <= 1.0E-4) {
            if (shouldLog) LOGGER.debug("[livingblock] climbpivot id={} event=aligned dir={} angle={} ticks={} gap={} drift={} pos={}",
                    this.getId(), direction, String.format("%.1f", this.climbPivotDegreesTotal),
                    this.tickCount - this.climbPivotEdgeStartTick + 1,
                    this.climbPivotMaxAdvanceGap, String.format("%.6f", this.climbPivotRuntimeDrift),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            this.climbPivot = null;
            this.climbPivotAligning = false;
            this.climbPivotStep = 0;
            this.climbPivotLimit = 0;
            this.climbPivotDegreesRemaining = 0.0;
            this.climbPivotDegreesTotal = 0.0;
            return ClimbPivotAdvance.MOVED;
        }
        return ClimbPivotAdvance.ALIGNING;
    }

    private @Nullable AABB surfaceAt(final double x, final double top, final double z) {
        BlockPos pos = BlockPos.containing(x, top - 0.05, z);
        AABB best = null;
        for (AABB piece : this.level().getBlockState(pos)
                .getCollisionShape(this.level(), pos).toAabbs()) {
            AABB world = piece.move(pos.getX(), pos.getY(), pos.getZ());
            if (Math.abs(world.maxY - top) > 0.06) {
                continue;
            }
            if (world.maxX < x - 0.06 || world.minX > x + 0.06
                    || world.maxZ < z - 0.06 || world.minZ > z + 0.06) {
                continue;
            }
            if (best == null || world.maxY > best.maxY) {
                best = world;
            }
        }
        return best;
    }

    private static final double DESCENT_MAX_DEGREES = 180.0;
    private static final double DESCENT_EDGE_REACH = 0.30;

    private ClimbArc climbArc(final LivingBlockPivot.WallPivot pivot,
                              final Direction direction, final Vec3 shapePivot) {
        Quaternionf pose = new Quaternionf(this.rotation);
        Vec3 position = this.position();
        double degrees = LivingBlockPivot.degreesToNextFace(pose, direction);
        if (!Double.isFinite(degrees)) {
            return null;
        }
        int steps = Math.max(1, (int)Math.ceil((degrees - 1.0E-6)
                / LivingBlockPivot.DEGREES));
        double remaining = degrees;
        for (int i = 1; i <= steps; i++) {
            double stepDegrees = Math.min(LivingBlockPivot.DEGREES, remaining);
            LivingBlockPivot.WallTurn midpoint = LivingBlockPivot.wallTurn(
                    this.getShapeBoxes(), shapePivot, pose, position, pivot, direction,
                    stepDegrees * 0.5);
            LivingBlockPivot.WallTurn turn = LivingBlockPivot.wallTurn(
                    this.getShapeBoxes(), shapePivot, pose, position, pivot, direction,
                    stepDegrees);
            LivingBlockCollisionHandler.ClimbPoseFailure midpointFailure = midpoint == null ? null
                    : LivingBlockCollisionHandler.climbPoseFailure(
                            this, midpoint.rotation(), midpoint.position(), CLIMB_PIVOT_SLOP);
            LivingBlockCollisionHandler.ClimbPoseFailure turnFailure = turn == null ? null
                    : LivingBlockCollisionHandler.climbPoseFailure(
                            this, turn.rotation(), turn.position(), CLIMB_PIVOT_SLOP);
            if (midpoint == null || midpointFailure != null || turn == null || turnFailure != null) {
                this.lastArcRefusal = climbFailure(midpoint, midpointFailure, turn, turnFailure);
                return new ClimbArc(false, steps, false, degrees, this.lastArcRefusal);
            }
            pose = turn.rotation();
            position = turn.position();
            remaining -= stepDegrees;
        }
        boolean top = LivingBlockPivot.topSupported(this.getShapeBoxes(), shapePivot,
                pose, position, pivot, direction, CLIMB_PIVOT_SLOP)
                && LivingBlockPivot.faceSettled(pose, CLIMB_TOP_SETTLE_DEGREES);
        return new ClimbArc(true, steps, top, degrees, "clear");
    }

    private static String climbFailure(final LivingBlockPivot.WallTurn midpoint,
                                       final LivingBlockCollisionHandler.ClimbPoseFailure midpointFailure,
                                       final LivingBlockPivot.WallTurn turn,
                                       final LivingBlockCollisionHandler.ClimbPoseFailure turnFailure) {
        if (midpoint == null) {
            return "midpoint";
        }
        if (midpointFailure != null) {
            return "mid-" + midpointFailure.kind() + ":"
                    + String.format("%.4f", midpointFailure.depth()) + ":"
                    + midpointFailure.blocker();
        }
        if (turn == null) {
            return "turn";
        }
        if (turnFailure != null) {
            return "turn-" + turnFailure.kind() + ":"
                    + String.format("%.4f", turnFailure.depth()) + ":"
                    + turnFailure.blocker();
        }
        return "unknown";
    }

    public Vec3 shapePivot() {
        AABB bounds = this.getBaseShapeBounds();
        return new Vec3((bounds.minX + bounds.maxX) * 0.5,
                (bounds.minY + bounds.maxY) * 0.5,
                (bounds.minZ + bounds.maxZ) * 0.5);
    }

    private void clearClimbPivot() {
        if (this.descentSequence) {
            this.descentEndTick = this.tickCount;
            this.descentEndDir = this.climbPivotDirection;
        }
        this.descentSequence = false;
        this.descentQuarterOnly = false;
        this.stepLifting = false;
        this.stepApproaching = false;
        this.stepTarget = 0.0;
        this.stepHold = 0;
        this.stepHolding = false;
        this.stepHoldToArc = false;
        this.stepDropping = false;
        this.stepSeatedY = Double.NaN;
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_STEP_PHASE, false);
        }
        this.descentWall = null;
        this.descentWallBody = false;
        this.descentWallTick = Integer.MIN_VALUE;
        this.climbPivot = null;
        this.climbPivotAnchorId = ANCHOR_NONE;
        this.climbPivotAnchorLocal = null;
        this.climbPivotStalled = false;
        this.climbPivotDirection = Direction.DOWN;
        this.climbPivotStep = 0;
        this.climbPivotLimit = 0;
        this.climbPivotDegreesRemaining = 0.0;
        this.climbPivotDegreesTotal = 0.0;
        this.climbPivotCompletes = false;
        this.climbPivotSequence = false;
        this.climbPivotAligning = false;
        this.climbPivotAlignmentTarget.identity();
        this.climbPivotLastAdvanceTick = -1;
        this.climbPivotMaxAdvanceGap = 0;
        this.climbPivotRuntimeDrift = 0.0;
        this.climbPivotExpectedPosition = Vec3.ZERO;
        if (!this.level().isClientSide() && this.climbPivotVisualReleaseTick <= this.tickCount) {
            this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, false);
        }
        this.rollDeltaX = 0.0F;
        this.rollDeltaZ = 0.0F;
        this.rollCarryX = 0.0F;
        this.rollCarryZ = 0.0F;
        this.rollPhase = 0.0;
        this.rollPhaseAxis = LivingBlockRoll.AXIS_NONE;
        this.rollPending = 0.0;
        this.rollPendingAxis = LivingBlockRoll.AXIS_NONE;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag input) {
        super.readAdditionalSaveData(input);
        if (input.contains("feature_seed")) {
            this.entityData.set(DATA_FEATURE_SEED, input.getInt("feature_seed"));
        }
        if (input.contains("growth_stage")) {
            this.entityData.set(DATA_GROWTH,
                    Mth.clamp(input.getInt("growth_stage"), 0, 100));
        }
        if (input.contains("shape_seed")) {
            this.entityData.set(DATA_SHAPE_SEED, input.getInt("shape_seed"));
        }
        if (input.contains("orientation")) {
            this.entityData.set(DATA_ORIENTATION, LivingBlockOrientation.of(input.getByte("orientation")).index());
        }
        if (input.contains("orientation_settled")) {
            this.entityData.set(DATA_ORIENTATION_SETTLED, input.getBoolean("orientation_settled"));
        }
        if (!this.rotationRestored) {
            this.rotationRestored = true;
            ListTag stored = input.getList("rotation", Tag.TAG_FLOAT);
            if (stored.size() == 4) {
                this.rotation.set(stored.getFloat(0), stored.getFloat(1), stored.getFloat(2), stored.getFloat(3));
            } else {
                this.rotation.set(this.getOrientation().quaternion());
            }
            if (!isUsableRotation(this.rotation)) {
                this.rotation.set(this.getOrientation().quaternion());
            }
            this.rotation.normalize();
            this.lastRotation.set(this.rotation);
            this.drawnFrom.set(this.rotation);
        }
    }

    protected static boolean isUsableRotation(final Quaternionfc value) {
        float lengthSqr = value.x() * value.x() + value.y() * value.y()
                + value.z() * value.z() + value.w() * value.w();
        return Float.isFinite(lengthSqr) && lengthSqr > 1.0E-6F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag output) {
        super.addAdditionalSaveData(output);
        output.putInt("shape_seed", this.entityData.get(DATA_SHAPE_SEED));
        output.putInt("feature_seed", this.entityData.get(DATA_FEATURE_SEED));
        output.putInt("growth_stage", this.entityData.get(DATA_GROWTH));
        output.putByte("orientation", this.entityData.get(DATA_ORIENTATION));
        output.putBoolean("orientation_settled", this.entityData.get(DATA_ORIENTATION_SETTLED));
        ListTag stored = new ListTag();
        stored.add(FloatTag.valueOf(this.rotation.x()));
        stored.add(FloatTag.valueOf(this.rotation.y()));
        stored.add(FloatTag.valueOf(this.rotation.z()));
        stored.add(FloatTag.valueOf(this.rotation.w()));
        output.put("rotation", stored);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState movingOn) {
        if (this.normalStepSounds()) {
            super.playStepSound(pos, movingOn);
        }
    }

    private void playFaceLandingSound(final BlockPos pos, final BlockState landedOn, final float contactSpan) {
        if (landedOn.liquid()) {
            return;
        }
        float volume = FLOP_VOLUME * (FLOP_MIN_CONTACT + (1.0F - FLOP_MIN_CONTACT) * contactSpan);
        float pitch = FLOP_PITCH + (1.0F - contactSpan) * FLOP_PITCH_SPREAD;
        this.playSound(BeyondSoundEvents.MEMOR_PLACE.get(), volume, pitch);
    }

    private void playClimbFaceSound(final LivingBlockPivot.WallPivot pivot,
                                    final Direction direction) {
        Vec3 point = LivingBlockPivot.wallSample(pivot.surface(), direction);
        BlockPos pos = BlockPos.containing(point);
        BlockState state = this.level().getBlockState(pos);
        this.playFaceLandingSound(pos, state, 1.0F);
        this.level().gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP, pos,
                net.minecraft.world.level.gameevent.GameEvent.Context.of(this, state));
        if (shouldLog) LOGGER.debug("[livingblock] climbstep id={} event=contact dir={} block={} pos={}",
                this.getId(), direction, state.getBlock(), pos);
    }

    @Override
    public void knockback(final double power, double xd, double zd) {
        if (!(power <= 0.0)) {
            Vec3 deltaMovement = this.getDeltaMovement();

            while (xd * xd + zd * zd < 1.0E-5F) {
                xd = (this.random.nextDouble() - this.random.nextDouble()) * 0.01;
                zd = (this.random.nextDouble() - this.random.nextDouble()) * 0.01;
            }

            Vec3 deltaVector = new Vec3(xd, 0.0, zd).normalize().scale(power);
            this.setDeltaMovement(
                    deltaMovement.x / 2.0 - deltaVector.x,
                    this.onGround() ? Math.min(0.4, deltaMovement.y / 2.0 + power) : deltaMovement.y,
                    deltaMovement.z / 2.0 - deltaVector.z
            );
        }
    }

    public static Quaternionf snapToNearestRightAngle(Quaternionfc rotation) {
        return LivingBlockOrientation.fromQuaternion(rotation).quaternion();
    }

    private static Quaternionf snapToNearestRightAngleLegacy(Quaternionfc rotation) {
        Vector3f localForward = Direction.NORTH.step().rotate(rotation);
        Vector3f localUp = Direction.UP.step().rotate(rotation);
        return new Quaternionf()
                .lookAlong(nearestStep(localForward.x, localForward.y, localForward.z, Direction.NORTH).step(),
                        nearestStep(localUp.x, localUp.y, localUp.z, Direction.UP).step())
                .conjugate();
    }

    private static Direction nearestStep(final float x, final float y, final float z,
                                         final Direction orElse) {
        float absX = Math.abs(x);
        float absY = Math.abs(y);
        float absZ = Math.abs(z);
        if (absX > absZ && absX > absY) {
            return x < 0.0F ? Direction.WEST : Direction.EAST;
        } else if (absZ > absX && absZ > absY) {
            return z < 0.0F ? Direction.NORTH : Direction.SOUTH;
        } else if (absY > absX && absY > absZ) {
            return y < 0.0F ? Direction.DOWN : Direction.UP;
        } else {
            return orElse;
        }
    }

    private Quaternionf snapNearest(final Quaternionfc source) {
        return this.usesOrientedCollision()
                ? snapToNearestRightAngle(source)
                : snapToNearestRightAngleLegacy(source);
    }

    @Override
    public void tick() {
        if (this.hurtTime > 0) {
            this.hurtTime--;
        }

        if (this.isDeadOrDying()) {
            this.tickDeath();
        }

        if (!this.level().isClientSide()) {
            if (this.descentSequence && this.climbPivot != null
                    && this.position().distanceToSqr(this.climbPivotExpectedPosition) > 1.0E-12) {
                if (shouldLog) LOGGER.debug("[livingblock] descdrift id={} t={} moved={} from={} to={} ground={} vel={} step={}/{} align={}",
                        this.getId(), this.tickCount,
                        String.format("%.6f",
                                this.position().distanceTo(this.climbPivotExpectedPosition)),
                        String.format("%.5f,%.5f,%.5f", this.climbPivotExpectedPosition.x,
                                this.climbPivotExpectedPosition.y,
                                this.climbPivotExpectedPosition.z),
                        String.format("%.5f,%.5f,%.5f", this.getX(), this.getY(), this.getZ()),
                        this.onGround(), String.format("%.5f", this.getDeltaMovement().length()),
                        this.climbPivotStep, this.climbPivotLimit, this.climbPivotAligning);
            }
            if (this.onGround() && this.tickCount - this.descentDumpTick >= DESCENT_HEARTBEAT) {
                this.descentDump(this.getDirection(), "noroute", null, null);
            }
        }
        this.descentWrite("tickstart");
        this.climbPivotMovedThisTick = false;
        boolean pivotSequence = this.climbPivotSequence;
        if (pivotSequence) {
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.refreshHullOrientation();
        this.vanillaOrderThisTick = !this.usesOrientedCollision();
        this.markTickStart();
        this.rollDeltaX += this.rollCarryX;
        this.rollDeltaZ += this.rollCarryZ;
        this.rollCarryX = 0.0F;
        this.rollCarryZ = 0.0F;
        this.rollAngle = 90.0F * (float) Math.sqrt(
                this.rollDeltaX * this.rollDeltaX + this.rollDeltaZ * this.rollDeltaZ);
        if (!pivotSequence) {
            this.applyGatedRotation();
        }
        if (!pivotSequence && this.rollSoundTime-- <= 0
                && !this.normalStepSounds() && !this.movement.flopsOnLanding()
                && !this.lastRotation.equals(this.rotation, ROLL_ROTATION_DELTA_EPSILON)) {
            Quaternionf groundAngle = this.snapNearest(this.rotation);
            Quaternionf lastToGround = new Quaternionf(this.lastRotation);
            lastToGround.conjugate();
            lastToGround.mul(groundAngle);
            double lastAngleToZero = Math.toDegrees(lastToGround.angle());
            double lastDistanceToZero = Math.min(lastAngleToZero, 360.0 - lastAngleToZero);
            Quaternionf currentToGround = new Quaternionf(this.rotation);
            currentToGround.conjugate();
            currentToGround.mul(groundAngle);
            double currentAngleToZero = Math.toDegrees(currentToGround.angle()) % 360.0;
            double currentDistanceToZero = Math.min(currentAngleToZero, 360.0 - currentAngleToZero);
            if (lastDistanceToZero > ROLL_SOUND_MIN_ANGLE && currentDistanceToZero <= ROLL_SOUND_MIN_ANGLE) {
                this.pendingFlop = true;
                this.rollSoundTime = ROLL_SOUND_MIN_TIME;
            }
        }

        this.lastRotation.set(this.rotation);
        this.drawnFrom.set(this.tickStartRotation);
        if (this.level().isClientSide() && this.hasSyncedRotation) {
            this.rotation.set(this.syncedRotation);
        }
        this.rollDeltaX = 0.0F;
        this.rollDeltaZ = 0.0F;
        if (this.usesOrientedCollision() && (!pivotSequence || this.climbPivotAligning)) {
            this.bleedRollResidual();
        }
        if (!this.level().isClientSide() && (!pivotSequence || this.climbPivotAligning)) {
            this.commitSettledOrientation();
        } else {
            this.pendingOrientationRestore = false;
        }
        this.snapSettledPose();
        this.rotationRestored = true;
        if (this.tickCount % ROT_LOG_INTERVAL == 0) {
            if (shouldLog) LOGGER.debug("[livingblock] rot side={} id={} q={} settled={} ground={} res={} axis={} phase={} fb={} rc={} pend={} rej={} ori={} data={} pos={}",
                    this.level().isClientSide() ? "C" : "S", this.getId(),
                    String.format("%.2f,%.2f,%.2f,%.2f",
                            this.rotation.w(), this.rotation.x(), this.rotation.y(), this.rotation.z()),
                    this.isOrientationSettled(), this.onGround(),
                    String.format("%.1f", Math.toDegrees(angularDistance(this.rotation,
                            LivingBlockOrientation.fromQuaternion(this.rotation)))),
                    this.rollPhaseAxis, String.format("%.1f", this.rollPhase), this.rollFallbacks,
                    this.rollRestores, String.format("%.1f", this.rollPending), this.orientationRejects,
                    this.getOrientation().index(), this.entityData.get(DATA_ORIENTATION),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
        this.descentWrite("beforevanilla");
        super.tick();
        this.descentWrite("movevanilla");
        this.groundOnBody();

        if (!this.level().isClientSide() && this.climbPivotSequence
                && this.movement instanceof RollingMovement rolling) {
            rolling.advanceClimbPivot(this);
        }
        this.refreshHullOrientation();
        this.tickFrozenWatch();

        this.tickLandingWatch();
        if (!this.level().isClientSide()) {
            this.assignShapeSeed();
        }

        if (this.shapeIsEntropic != this.isEntropicForm()) {
            this.applyShape();
        }

        boolean grounded = this.onGround();
        if (grounded && !this.wasGrounded && this.movement.flopsOnLanding()) {
            this.pendingFlop = true;
        }
        this.wasGrounded = grounded;

        if (this.pendingFlop && grounded) {
            this.pendingFlop = false;
            BlockPos flopPos = this.getOnPosLegacy();
            BlockState flopState = this.level().getBlockState(flopPos);
            this.playFaceLandingSound(flopPos, flopState,
                    LivingBlockCollisionHandler.groundContactSpan(this, this.snapNearest(this.rotation)));
            this.level().gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP, flopPos, net.minecraft.world.level.gameevent.GameEvent.Context.of(this, flopState));
        }

        if (!this.isDeadOrDying()) {
            if (!this.level().isClientSide()) {
                this.setMovement(this.desiredMovement());
                Entity holder = this.isLeashed() ? this.getLeashHolder() : null;
                if (holder != null) {
                    this.setMovementTarget(Target.followingEntity(holder, 2.0));
                } else if (this.isEntropicForm()) {
                    Player nearest = this.level().getNearestPlayer(this, AGGRO_RANGE);
                    if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
                        if (!(this.getMovementTarget() instanceof Target.EntityTarget following)
                                || !following.entityUUID().equals(nearest.getUUID())) {
                            this.setMovementTarget(Target.followingEntity(nearest, 1.0));
                        }
                    } else {
                        this.clearMovementTarget();
                    }
                }
            }

            this.releaseStuckSequence();

            Target target = this.getMovementTarget();
            Vec3 targetPos = target.resolvePosition(this.level());
            if (targetPos != null && !this.isHoldingForRider()) {
                boolean moved = this.movement.moveTowardsTarget(this, target, targetPos);
                if (!moved && target.clearWhenNear()) {
                    if (this.vanillaOrderThisTick) {
                        this.setMovementTarget(Target.NONE);
                    }
                    this.movement.resetMovement(this);
                }
            }
        }

        boolean mover = this.vanillaOrderThisTick && !this.level().isClientSide()
                && this.isEffectiveAi();
        Vec3 movePre = this.position();
        Vec3 moveDelta = this.getDeltaMovement();
        if (mover) {
            this.setBoundingBox(this.makeBoundingBox());
            this.liftOutOfFloor();
        }
        AABB moveHull = this.getBoundingBox();
        double floorGap = this.floorGap(moveHull);
        if (mover) {
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
        if (this.landingWatch > 0 && !this.level().isClientSide()) {
            if (shouldLog) LOGGER.debug("[livingblock] movetrace id={} ran={} ai={} vanilla={} hull={} gap={} pre={} delta={} post={} ground={} vcol={}",
                    this.getId(), mover, this.isEffectiveAi(), this.vanillaOrderThisTick,
                    String.format("%.4f|%.10f|%.4f", moveHull.minX, moveHull.minY, moveHull.minZ),
                    String.format("%.3e", floorGap),
                    String.format("%.4f|%.4f|%.4f", movePre.x, movePre.y, movePre.z),
                    String.format("%.4f|%.4f|%.4f", moveDelta.x, moveDelta.y, moveDelta.z),
                    String.format("%.4f,%.4f,%.4f", this.getX(), this.getY(), this.getZ()),
                    this.onGround(), this.verticalCollision);
        }

        AABB nudgeBounds = this.getBoundingBox();
        for (Entity entity : this.level().getEntities(this, AABB.ofSize(nudgeBounds.getCenter(), 0.05, 0.05, 0.05), t -> t instanceof LivingBlock)) {
            entity.push(this);
        }

        boolean isClimbing = this.isClimbing();
        BlockPos posBelow = this.getBlockPosBelowThatAffectsMyMovement();
        float blockFriction = this.onGround() ? this.level().getBlockState(posBelow).getBlock().getFriction() : 1.0F;
        float friction = blockFriction * 0.96F;
        float frictionY = isClimbing ? 0.57600003F : (this.vanillaOrderThisTick ? 0.98F : 1.0F);
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, frictionY, friction));

        if (this.vanillaOrderThisTick ? !isClimbing || this.onGround() : isClimbing && this.onGround()) {
            this.applyGravity();
        }

        if (this.isInWater()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        }

        if (this.climbPivotSequence) {
            this.setDeltaMovement(Vec3.ZERO);
        } else {
            this.applyTumbleDynamics();
        }

        if (this.descentSequence && !this.climbPivotMovedThisTick) {
            if (shouldLog) LOGGER.debug("[livingblock] descwrite id={} t={} who=snaprefusedbydescent moved=0.000000 turned=0.0000 pos={} step={}/{} pivotmoved=false ground={}",
                    this.getId(), this.tickCount,
                    String.format("%.5f,%.5f,%.5f", this.getX(), this.getY(), this.getZ()),
                    this.climbPivotStep, this.climbPivotLimit, this.onGround());
        }
        boolean lockedTilted = this.onGround()
                && this.tiltDegrees() > Math.toDegrees(UNSETTLE_ANGLE);
        double stillLimit = lockedTilted ? RIGHTING_SPEED_SQR : Mth.square(0.001);
        boolean quiet = !this.descentSequence && !this.climbPivotMovedThisTick
                && (this.onGround()
                        && this.getDeltaMovement().horizontalDistanceSqr() < stillLimit
                        || isClimbing && Math.abs(this.getDeltaMovement().y) < 0.001);
        int quietState = quiet ? 1 : 0;
        if (!this.level().isClientSide() && quietState != this.quietLoggedState
                && this.tickCount - this.quietLogTick >= QUIET_LOG_INTERVAL) {
            this.quietLoggedState = quietState;
            this.quietLogTick = this.tickCount;
            if (shouldLog) LOGGER.debug("[livingblock] quiet id={} open={} vel={} limit={} ground={} climb={} desc={} pivot={} tilt={} pos={}",
                    this.getId(), quiet,
                    String.format("%.5f", Math.sqrt(this.getDeltaMovement().horizontalDistanceSqr())),
                    String.format("%.5f", Math.sqrt(stillLimit)), this.onGround(), isClimbing,
                    this.descentSequence, this.climbPivotMovedThisTick,
                    String.format("%.1f", this.tiltDegrees()),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
        if (quiet) {
            Vec3 blockGridDeltaFull = this.blockPosition().getBottomCenter().subtract(this.position());
            Vec3 blockGridDelta = new Vec3(blockGridDeltaFull.x, 0, blockGridDeltaFull.z);
            double blockGridOffset = blockGridDelta.length();
            if (!this.usesOrientedCollision()) {
                if (!this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE)) {
                    double legacyAlpha = Mth.clamp(blockGridOffset * 64.0, 0.5, 1.0);
                    Quaternionf legacyBefore = new Quaternionf(this.rotation);
                    Quaternionf legacyTarget = this.snapNearest(this.rotation);
                    if (!legacyTarget.isFinite()) {
                        legacyTarget = snapToNearestRightAngle(this.rotation);
                    }
                    double legacySpan = angleBetween(this.rotation, legacyTarget);
                    if (legacySpan > MIN_SNAP_SPAN_DEGREES) {
                        double legacyStep = Math.min(legacyAlpha * legacySpan, LivingBlockPivot.DEGREES);
                        this.rotation.slerp(legacyTarget, (float) (legacyStep / legacySpan));
                    }
                    if (!this.level().isClientSide()) {
                        double moved = angleBetween(legacyBefore, this.rotation);
                        if (moved > 0.05) {
                            if (shouldLog) LOGGER.debug("[livingblock] legacysnap id={} alpha={} moved={} offset={} tilt={} ground={} pos={}",
                                    this.getId(), String.format("%.3f", legacyAlpha),
                                    String.format("%.2f", moved),
                                    String.format("%.5f", blockGridOffset),
                                    String.format("%.2f", this.tiltDegrees()), this.onGround(),
                                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                        }
                    }
                }
                if (blockGridOffset > GRID_RECENTRE_MIN && blockGridOffset <= GRID_RECENTRE_MAX
                        && this.isIdle() && !this.level().isClientSide()) {
                    boolean groundedBeforeRecentre = this.onGround();
                    double beforeRecentreX = this.getX();
                    double beforeRecentreZ = this.getZ();
                    this.move(MoverType.SELF, blockGridDelta);
                    this.setOnGround(groundedBeforeRecentre);
                    this.xo += this.getX() - beforeRecentreX;
                    this.zo += this.getZ() - beforeRecentreZ;
                }
            } else {
            this.finishRollTurn(QTURN_REST);
            if (!this.level().isClientSide()
                    && Math.abs(this.rollPending) < LivingBlockRoll.RESIDUAL_EPSILON
                    && angularDistance(this.rotation, LivingBlockOrientation.fromQuaternion(this.rotation))
                            > SETTLE_ANGLE_EPSILON) {
                this.rollFallbacks++;
                if (lockedTilted && !this.level().isClientSide()
                        && this.tickCount % ROT_LOG_INTERVAL == 0) {
                    if (shouldLog) LOGGER.debug("[livingblock] straighten id={} tilt={} vel={} ground={} pos={}",
                            this.getId(), String.format("%.1f", this.tiltDegrees()),
                            String.format("%.4f", Math.sqrt(
                                    this.getDeltaMovement().horizontalDistanceSqr())),
                            this.onGround(),
                            String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
                }
                double rotationAlpha = Mth.clamp(blockGridOffset * 64.0, 0.5, 1.0);
                double penBefore = this.penetrationNow();
                Quaternionf beforeSnap = new Quaternionf(this.rotation);
                Quaternionf preSnap = new Quaternionf(this.rotation);
                Quaternionf target = this.snapNearest(this.rotation);
                if (this.descentEndTick != Integer.MIN_VALUE
                        && this.tickCount - this.descentEndTick <= DESCENT_SETTLE_TICKS
                        && this.descentEndDir.getAxis().isHorizontal()) {
                    Quaternionf ahead = LivingBlockPivot.nextFaceOrientation(this.rotation,
                            this.descentEndDir);
                    if (ahead != null) {
                        target = ahead;
                    }
                }
                double span = angleBetween(this.rotation, target);
                this.rotation.slerp(target, (float) rotationAlpha);
                this.descentWrite("snaprotation");
                if (this.descentEndTick != Integer.MIN_VALUE
                        && this.tickCount - this.descentEndTick <= 10) {
                    if (shouldLog) LOGGER.debug("[livingblock] descrecoil id={} t={} sinceend={} turned={} tilt={} ground={} pos={}",
                            this.getId(), this.tickCount, this.tickCount - this.descentEndTick,
                            String.format("%.3f", angleBetween(preSnap, this.rotation)),
                            String.format("%.2f", this.tiltDegrees()), this.onGround(),
                            String.format("%.4f,%.4f,%.4f", this.getX(), this.getY(), this.getZ()));
                }
                if (this.penetrationNow() > penBefore + DEPEN_EPSILON
                        && !this.seatByTipping(beforeSnap, penBefore + DEPEN_EPSILON)) {
                    this.rotation.set(beforeSnap);
                    if (this.shouldLogRotClamp()) {
                        if (shouldLog) LOGGER.debug("[livingblock] pensource id={} source=snaprotation refused before={} alpha={} tilt={} pos={}",
                                this.getId(), String.format("%.5f", penBefore),
                                String.format("%.3f", rotationAlpha),
                                String.format("%.2f", this.tiltDegrees()),
                                String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                    }
                }
            }
            boolean recentreMin = blockGridOffset > GRID_RECENTRE_MIN;
            boolean recentreMax = blockGridOffset <= GRID_RECENTRE_MAX;
            boolean recentreIdle = this.isIdle();
            if (!this.level().isClientSide()) {
                String verdict = recentreMin + "/" + recentreMax + "/" + recentreIdle;
                if (!verdict.equals(this.recentreVerdict)) {
                    this.recentreVerdict = verdict;
                    if (shouldLog) LOGGER.debug("[livingblock] recentre id={} offset={} min={} max={} idle={} held={} pos={}",
                            this.getId(), String.format("%.5f", blockGridOffset), recentreMin,
                            recentreMax, recentreIdle, this.isHoldingForRider(),
                            String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                }
            }
            if (recentreMin && recentreMax && recentreIdle && !this.level().isClientSide()) {
                boolean groundedBeforeRecentre = this.onGround();
                double beforeRecentreX = this.getX();
                double beforeRecentreZ = this.getZ();
                this.move(MoverType.SELF, blockGridDelta);
                this.setOnGround(groundedBeforeRecentre);
                this.xo += this.getX() - beforeRecentreX;
                this.zo += this.getZ() - beforeRecentreZ;
            }
            }
            //setBoundingBox(rotateAABB());
        }

        Vector3f facing = new Vector3f();
        this.rotation.getEulerAnglesYXZ(facing);
        if (facing.isFinite()) {
            this.setRot(facing.y * (180.0F / (float)Math.PI), facing.x * (180.0F / (float)Math.PI));
        } else {
            this.setRot(0.0F, 0.0F);
            this.rotation.identity();
        }

        double spin = angleBetween(this.tickStartRotation, this.rotation);
        this.traceSpin = spin;
        this.traceMoved = this.position().distanceTo(new Vec3(this.xo, this.yo, this.zo));
        this.tickWriterTrace();
        if (spin > SPIN_LOG_DEGREES && this.tickCount % SPIN_LOG_INTERVAL == 0
                && !this.level().isClientSide()) {
            if (shouldLog) LOGGER.debug("[livingblock] spin id={} total={} roll={} snap={} ground={} settled={} moved={}",
                    this.getId(), String.format("%.1f", spin), String.format("%.1f", this.rollAngle),
                    String.format("%.1f", angleBetween(this.lastRotation, this.rotation)),
                    this.onGround(), this.isOrientationSettled(),
                    String.format("%.4f", this.position().distanceTo(new Vec3(this.xo, this.yo, this.zo))));
        }

        if (this.climbPivotAnchorId >= 0
                && this.level().getEntity(this.climbPivotAnchorId) instanceof LivingBlock owner) {
            owner.holdForRider(this.level().getGameTime());
        }
        LivingBlockCollisionHandler.carryRiders(this);

        Vec3 pogoScaleDiff = this.pogoScaleTarget.subtract(this.currentPogoScale);
        if (pogoScaleDiff.lengthSqr() > 1.0E-5F && this.pogoScaleTicks > 0) {
            this.currentPogoScale = MathHelpers.vecLerp((float) (1.0 - (double)this.pogoScaleTicksRemaining / this.pogoScaleTicks), this.lastPogoScaleTarget, this.pogoScaleTarget);
        }

        if (this.pogoScaleTicksRemaining > 0) {
            this.pogoScaleTicksRemaining--;
        }

        if (this.squashRecoverTicks > 0 && --this.squashRecoverTicks == 0) {
            this.setPogoScaleTarget(new Vec3(1.0, 1.0, 1.0), IMPACT_RECOVER_TICKS);
        }

        if (this.onGround()) {
            this.rollAirTicks = 0;
        } else {
            this.rollAirTicks++;
        }
        AABB bounds = this.getBaseShapeBounds();
        double sizeY = bounds.getYsize();
        if (sizeY > 1.0E-5F && !this.level().isClientSide()) {
            Vector3f localYaxis = Mth.Y_AXIS.rotate(this.lastRotation, new Vector3f());
            float tilt = Math.abs(localYaxis.y);
            double sizeX = bounds.getXsize();
            double sizeZ = bounds.getZsize();
            boolean rollAirborne = !this.onGround() && this.rollAirTicks > ROLL_AIR_GRACE;
            float airRoll = this.tumbleAirborne || this.isStepDriving() ? 1.0F
                    : this.isLeashed() || !this.usesOrientedCollision() ? AIR_DRAG_ROLL : 0.0F;
            float rotationSpeed = this.usesOrientedCollision()
                    ? (!isClimbing && rollAirborne ? airRoll : 1.0F)
                    : (!isClimbing && !this.onGround() ? (this.tumbleAirborne ? 1.0F : 0.5F) : 1.0F);
            double moveX = this.getX() - this.xo;
            double moveZ = this.getZ() - this.zo;
            if (!isClimbing && this.usesOrientedCollision()) {
                int axis = LivingBlockRoll.dominantAxis(moveX, moveZ, this.rollPhaseAxis);
                if (axis != this.rollPhaseAxis) {
                    this.rollAxisFlips++;
                    this.finishRollTurn(QTURN_AXIS);
                    this.rollPhaseAxis = axis;
                }
                if (axis == LivingBlockRoll.AXIS_X) {
                    moveX = 0.0;
                } else {
                    moveZ = 0.0;
                }
            }

            boolean oriented = this.usesOrientedCollision();
            if (!isClimbing) {
                if (sizeZ > 1.0E-5F) {
                    double sideLength = sizeZ < sizeY
                            ? Mth.lerp((double)tilt, Math.sqrt(sizeZ / sizeY) * sizeY, sizeY)
                            : Mth.lerp((double)tilt, sizeZ, Math.sqrt(sizeY / sizeZ) * sizeZ);
                    double step = moveZ * rotationSpeed
                            / quantizedSide(sideLength, !oriented);
                    this.rollDeltaX = this.rollDeltaX + (float)step;
                    if (this.rollPhaseAxis == 0) {
                        this.rollPhase += step * 90.0;
                    }
                }

                if (sizeX > 1.0E-5F) {
                    double sideLength = sizeX < sizeY
                            ? Mth.lerp((double)tilt, Math.sqrt(sizeX / sizeY) * sizeY, sizeY)
                            : Mth.lerp((double)tilt, sizeX, Math.sqrt(sizeY / sizeX) * sizeX);
                    double step = moveX * rotationSpeed
                            / quantizedSide(sideLength, !oriented);
                    this.rollDeltaZ = this.rollDeltaZ - (float)step;
                    if (this.rollPhaseAxis == 2) {
                        this.rollPhase -= step * 90.0;
                    }
                }
            }

            if (isClimbing && !this.climbPivotMovedThisTick) {
                Direction groundDirection = this.getClimbingDirection();
                boolean onXAxis = groundDirection.getAxis() == Direction.Axis.X;
                double size = onXAxis ? sizeZ : sizeX;
                double sideLength = size < sizeY
                        ? Mth.lerp((double)tilt, sizeY, Math.sqrt(size / sizeY) * sizeY)
                        : Mth.lerp((double)tilt, Math.sqrt(sizeY / size) * size, size);
                int sign = groundDirection.getAxisDirection().getStep();
                float roll = (float)((this.getY() - this.yo) * rotationSpeed / sideLength * sign);
                if (onXAxis) {
                    this.rollDeltaZ += this.usesOrientedCollision() ? -roll : roll;
                } else {
                    this.rollDeltaX += roll;
                }
            }
        }

        if (this.landingWatch > 0 && !this.level().isClientSide()) {
            if (shouldLog) LOGGER.debug("[livingblock] tickend id={} pos={} vy={} ground={}", this.getId(),
                    String.format("%.4f,%.4f,%.4f", this.getX(), this.getY(), this.getZ()),
                    String.format("%.4f", this.getDeltaMovement().y), this.onGround());
        }
        this.previousFallSpeed = this.getDeltaMovement().y;

        if (!this.level().isClientSide()) {
            if (!this.climbPivotSequence || this.climbPivotStalled) {
                this.easeOutOfOverlapTraced();
            }
            this.tickFeatureGrowth();
            this.closeNudge();
            this.emitTrace();
            this.publishRotation();
            if (this.climbPivot == null && this.climbPivotVisualReleaseTick >= 0
                    && this.tickCount >= this.climbPivotVisualReleaseTick) {
                this.entityData.set(DATA_CLIMB_PIVOT_ACTIVE, false);
                this.climbPivotVisualReleaseTick = -1;
            }
        }
    }

    private void publishRotation() {
        Quaternionf sent = this.entityData.get(DATA_ROTATION);
        if (angleBetween(sent, this.rotation) < ROTATION_SYNC_EPSILON) {
            return;
        }
        this.entityData.set(DATA_ROTATION, new Quaternionf(this.rotation));
    }

    private void closeNudge() {
        if (this.nudgeUntil < 0 || !this.onGround()) {
            return;
        }
        boolean early = false;
        if (this.tickCount < this.nudgeUntil) {
            if (this.tickCount < this.nudgeStart + NUDGE_MIN_TICKS || !this.rollingIsPossible()) {
                return;
            }
            early = true;
        }
        if (shouldLog) LOGGER.debug("[livingblock] nudge id={} exit=1 early={} held={} tilt={} settled={} obb={} pos={}",
                this.getId(), early, this.tickCount - this.nudgeStart,
                String.format("%.1f", this.tiltDegrees()), this.isOrientationSettled(),
                String.format("%.3f", this.traceOverlap),
                String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        this.nudgeUntil = -1;
    }

    private boolean rollingIsPossible() {
        Target target = this.getMovementTarget();
        Vec3 targetPos = target.resolvePosition(this.level());
        if (targetPos == null) {
            return false;
        }
        Vec3 delta = targetPos.subtract(this.position());
        if (delta.horizontalDistanceSqr() < Mth.square(target.distance())) {
            return false;
        }
        return RollingMovement.canRollTowards(this, Direction.getNearest(delta.x, 0.0, delta.z));
    }

    private void easeOutOfOverlapTraced() {
        this.easeOutOfOverlap();
        this.descentWrite("separation");
    }

    private void easeOutOfOverlap() {
        if (!this.usesOrientedCollision()) {
            return;
        }
        if (this.separationBackoff > 0) {
            this.separationBackoff--;
            return;
        }

        Vec3 push = LivingBlockCollisionHandler.separation(this);
        this.tracePush = push;
        if (push.lengthSqr() <= 1.0E-12) {
            this.traceApplied = Vec3.ZERO;
            double moved = this.distanceToSqr(this.xo, this.yo, this.zo);
            this.separationBackoff = moved > SEPARATION_STILL_EPSILON_SQR ? 0 : SEPARATION_IDLE_BACKOFF;
            return;
        }

        Vec3 before = this.position();
        double penBefore = this.level().isClientSide() ? 0.0 : this.penetrationNow();
        this.setPos(this.getX() + push.x, this.getY() + push.y, this.getZ() + push.z);
        if (!this.level().isClientSide()) {
            double penAfter = this.penetrationNow();
            if (penAfter > penBefore + DEPEN_EPSILON) {
                this.setPos(before.x - push.x, before.y - push.y, before.z - push.z);
                double penBack = this.penetrationNow();
                if (penBack < penBefore - DEPEN_EPSILON) {
                    this.traceApplied = this.position().subtract(before);
                    return;
                }
                this.setPos(before.x, before.y, before.z);
                if (shouldLog) LOGGER.debug("[livingblock] pensource id={} source=separation before={} after={} push={} undone=true climbing={} tilt={} side={} pos={}",
                        this.getId(), String.format("%.5f", penBefore),
                        String.format("%.5f", penAfter),
                        String.format("%.5f,%.5f,%.5f", push.x, push.y, push.z),
                        this.isClimbing(), String.format("%.2f", this.tiltDegrees()),
                        extent(this.getBoundingBox()),
                        String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
            }
        }
        this.traceApplied = this.position().subtract(before);
    }

    private void tickFeatureGrowth() {
        int stage = this.entityData.get(DATA_GROWTH);
        if (stage >= 100 || this.isDeadOrDying()) {
            return;
        }
        if (this.growthRandom.nextInt(100) != 0) {
            return;
        }
        this.entityData.set(DATA_GROWTH, stage + 1);
    }

    protected boolean normalStepSounds() {
        return this.movement.normalStepSounds();
    }

    private static double quantizedSide(final double sideLength, final boolean raw) {
        return raw ? sideLength : LivingBlockRoll.quantizedSide(sideLength);
    }

    private void finishRollTurn(final int cause) {
        if (this.rollPhaseAxis == LivingBlockRoll.AXIS_NONE) {
            return;
        }
        if (this.rollPendingAxis != this.rollPhaseAxis
                && Math.abs(this.rollPending) >= LivingBlockRoll.RESIDUAL_EPSILON) {
            return;
        }
        double phase = this.rollPhase;
        this.rollPendingAxis = this.rollPhaseAxis;
        double residual = LivingBlockRoll.residualToQuarter(phase);
        this.rollPending += residual;
        this.rollPhase = 0.0;
        if ((cause == QTURN_AXIS || Math.abs(phase) >= LivingBlockRoll.RESIDUAL_EPSILON)
                && this.tickCount - this.qturnLogTick >= QTURN_LOG_INTERVAL) {
            this.qturnLogTick = this.tickCount;
            if (shouldLog) LOGGER.debug("[livingblock] qturn side={} id={} cause={} phase={} res={} axis={} pend={} flips={} ground={} pos={}",
                    this.level().isClientSide() ? "C" : "S", this.getId(),
                    cause == QTURN_AXIS ? "axis" : "rest",
                    String.format("%.1f", phase), String.format("%.1f", residual),
                    this.rollPendingAxis, String.format("%.1f", this.rollPending),
                    this.rollAxisFlips, this.onGround(),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
    }

    private LivingBlockPivot.PoseCheck tipPoseCheck(final double allowed) {
        return (pose, where) -> {
            LivingBlockCollisionHandler.ClimbPoseFailure failure =
                    LivingBlockCollisionHandler.climbPoseFailure(this, pose, where, allowed);
            return failure == null ? null : failure.kind();
        };
    }

    private List<Vec3> pivotCandidates() {
        return LivingBlockPivot.cornerCandidates(this.getShapeBoxes());
    }

    private boolean seatByTipping(final Quaternionf previous, final double allowed) {
        Vec3 seated = LivingBlockPivot.seatByTipping(this.getShapeBoxes(), this.shapePivot(),
                previous, this.rotation, this.position(), this.tipPoseCheck(allowed));
        if (seated == null) {
            return false;
        }
        this.setPos(seated.x, seated.y, seated.z);
        return true;
    }

    private void bleedRollResidual() {
        if (this.rollPendingAxis == LivingBlockRoll.AXIS_NONE
                || Math.abs(this.rollPending) < LivingBlockRoll.RESIDUAL_EPSILON) {
            this.rollPending = 0.0;
            return;
        }
        if (!this.onGround() && !this.isClimbing()) {
            return;
        }
        double step = LivingBlockRoll.bleedStep(this.rollPending);
        AABB hull = this.getBoundingBox();
        double reach = Math.toRadians(Math.abs(step)) * 0.5
                * Math.sqrt(hull.getXsize() * hull.getXsize() + hull.getYsize() * hull.getYsize()
                        + hull.getZsize() * hull.getZsize());
        List<LivingBlock> neighbours = LivingBlockCollisionHandler.overlapNeighbours(this, reach);
        double overlapBefore = LivingBlockCollisionHandler.worstEntityOverlap(this, neighbours);
        boolean server = !this.level().isClientSide();
        double terrainBefore = server ? this.penetrationNow() : 0.0;
        this.rollPending -= step;
        LivingBlockRoll.applyResidual(this.rotation, this.rollPendingAxis, step);
        if (server) {
            double terrainAfter = this.penetrationNow();
            if (terrainAfter > terrainBefore + DEPEN_EPSILON) {
                Vec3 shapePivot = this.shapePivot();
                List<Vec3> locals = this.pivotCandidates();
                LivingBlockRoll.applyResidual(this.rotation, this.rollPendingAxis, -step);
                List<Vec3> worlds = new ArrayList<>(locals.size());
                for (Vec3 local : locals) {
                    worlds.add(LivingBlockPivot.worldPoint(this.getShapeBoxes(), shapePivot,
                            this.rotation, this.position(), local));
                }
                LivingBlockRoll.applyResidual(this.rotation, this.rollPendingAxis, step);
                for (int i = 0; i < locals.size(); i++) {
                    Vec3 tipped = LivingBlockPivot.positionForPivot(this.getShapeBoxes(),
                            shapePivot, this.rotation, locals.get(i), worlds.get(i));
                    if (LivingBlockCollisionHandler.climbPoseFailure(
                            this, this.rotation, tipped, terrainBefore + DEPEN_EPSILON) != null) {
                        continue;
                    }
                    this.setPos(tipped.x, tipped.y, tipped.z);
                    this.descentWrite("tip");
                    if (this.shouldLogRotClamp()) {
                        Vec3 world = worlds.get(i);
                        if (shouldLog) LOGGER.debug("[livingblock] rolltip id={} step={} support={} cand={} tilt={} pend={} pos={}",
                                this.getId(), String.format("%.2f", step),
                                String.format("%.3f,%.3f,%.3f", world.x, world.y, world.z), i,
                                String.format("%.2f", this.tiltDegrees()),
                                String.format("%.1f", this.rollPending),
                                String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
                    }
                    return;
                }
                LivingBlockRoll.applyResidual(this.rotation, this.rollPendingAxis, -step);
                this.rollPending += step;
                if (this.shouldLogRotClamp()) {
                    if (shouldLog) LOGGER.debug("[livingblock] rollterrain id={} step={} before={} after={} pend={} tilt={} pos={}",
                            this.getId(), String.format("%.2f", step),
                            String.format("%.5f", terrainBefore), String.format("%.5f", terrainAfter),
                            String.format("%.1f", this.rollPending),
                            String.format("%.2f", this.tiltDegrees()),
                            String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
                }
                return;
            }
        }
        double overlapAfter = LivingBlockCollisionHandler.worstEntityOverlap(this, neighbours);
        if (overlapAfter - overlapBefore > ROT_OVERLAP_DELTA) {
            if (shouldLog) LOGGER.debug("[livingblock] rotoverlap id={} side={} step={} before={} after={} pend={} pos={}",
                    this.getId(), this.level().isClientSide() ? "C" : "S",
                    String.format("%.2f", step), String.format("%.4f", overlapBefore),
                    String.format("%.4f", overlapAfter), String.format("%.1f", this.rollPending),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
        double gained = overlapAfter - overlapBefore;
        if (!this.level().isClientSide() && gained > ROT_OVERLAP_BUDGET) {
            double allowed = step * ROT_OVERLAP_BUDGET / gained;
            LivingBlockRoll.applyResidual(this.rotation, this.rollPendingAxis, allowed - step);
            this.rollPending += step - allowed;
            double clamped = LivingBlockCollisionHandler.worstEntityOverlap(this, neighbours);
            boolean reverted = clamped - overlapBefore > ROT_OVERLAP_BUDGET;
            if (reverted) {
                LivingBlockRoll.applyResidual(this.rotation, this.rollPendingAxis, -allowed);
                this.rollPending += allowed;
            }
            if (this.shouldLogRotClamp()) {
                if (shouldLog) LOGGER.debug("[livingblock] rotclamp id={} step={} allowed={} before={} after={} clamped={} reverted={} pend={} side={} pos={}",
                        this.getId(), String.format("%.2f", step), String.format("%.2f", allowed),
                        String.format("%.4f", overlapBefore), String.format("%.4f", overlapAfter),
                        String.format("%.4f", clamped), reverted,
                        String.format("%.1f", this.rollPending), extent(this.getBoundingBox()),
                        String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
            }
        }
    }

    private static String extent(final AABB box) {
        return String.format("%.2fx%.2fx%.2f", box.getXsize(), box.getYsize(), box.getZsize());
    }

    private static void applyMovementRotation(final float dx, final float dz, final Quaternionf dest) {
        LivingBlockRoll.applyRoll(dest, dx, dz);
    }

    public boolean spaceFree(final AABB area) {
        if (this.level().noCollision(this, area)) {
            return true;
        }
        if (!this.level().getEntityCollisions(this, area).isEmpty()) {
            return false;
        }
        if (LivingBlockCollisionHandler.terrainClearAt(this, area)) {
            this.terrainRefinements++;
            return true;
        }
        return false;
    }

    public boolean toppleArcClearTowards(final Direction direction) {
        if (this.level().isClientSide() || this.isClimbing() || !this.onGround()) {
            return true;
        }
        if (!this.usesOrientedCollision()) {
            if (!this.arcVetoSkipLogged) {
                this.arcVetoSkipLogged = true;
                if (shouldLog) LOGGER.debug("[livingblock] arcveto id={} type={} state=disabled reason=axisalignedhull dir={}",
                        this.getId(), this.getType().toShortString(), direction);
            }
            return true;
        }
        float dx = direction.getStepZ() != 0 ? Math.signum(direction.getStepZ()) : 0.0F;
        float dz = direction.getStepX() != 0 ? -Math.signum(direction.getStepX()) : 0.0F;
        if (dx == 0.0F && dz == 0.0F) {
            return true;
        }
        java.util.function.DoubleSupplier overlap =
                LivingBlockCollisionHandler.rollOverlapProbe(this, ARC_PROBE_REACH);
        if (overlap == null) {
            return true;
        }
        Quaternionf saved = new Quaternionf(this.rotation);
        double before = overlap.getAsDouble();
        boolean clear = true;
        for (int i = 0; i < ARC_SAMPLES.length; i++) {
            this.rotation.set(saved);
            LivingBlockRoll.applyRoll(this.rotation, dx * ARC_SAMPLES[i], dz * ARC_SAMPLES[i]);
            if (overlap.getAsDouble() - before > ARC_CLEAR_BUDGET) {
                clear = false;
                break;
            }
        }
        if (!clear && this.prefersLowStep()) {
            clear = this.toppleArcTips(saved, dx, dz);
        }
        this.rotation.set(saved);
        if (!clear) {
            this.arcRefusals++;
        }
        return clear;
    }

    private boolean toppleArcTips(final Quaternionf saved, final float dx, final float dz) {
        LivingBlockPivot.PoseCheck check = this.tipPoseCheck(CLIMB_PIVOT_SLOP);
        Quaternionf after = new Quaternionf();
        for (float sample : ARC_SAMPLES) {
            after.set(saved);
            LivingBlockRoll.applyRoll(after, dx * sample, dz * sample);
            if (LivingBlockPivot.seatByTipping(this.getShapeBoxes(), this.shapePivot(),
                    saved, after, this.position(), check) == null) {
                return false;
            }
        }
        return true;
    }

    private void applyGatedRotation() {
        this.applyGatedRotationInner();
        if (!this.level().isClientSide()) {
            this.depenetrateTerrainTraced();
        }
    }

    private void depenetrateTerrainTraced() {
        this.depenetrateTerrain();
        this.descentWrite("depenetration");
    }

    private void depenetrateTerrain() {
        if (this.climbPivotSequence || this.landingWatch > 0) {
            return;
        }
        LivingBlockCollisionShapes.Placement own = LivingBlockCollisionShapes.preciseGeometry(this);
        if (own == null) {
            return;
        }
        Vector3f axis = new Vector3f();
        Vector3f escape = new Vector3f();
        double worst = 0.0;
        for (OrientedBox mine : own.obbs()) {
            AABB box = mine.getWorldAABB();
            for (VoxelShape shape : this.level().getBlockCollisions(this, box)) {
                for (AABB block : shape.toAabbs()) {
                    double depth = OrientedBox.penetration(mine, new OrientedBox(block), axis);
                    if (depth > worst) {
                        worst = depth;
                        escape.set(axis);
                    }
                }
            }
        }
        if (worst <= DEPEN_EPSILON) {
            return;
        }
        if (this.lastEscape.lengthSquared() > 0.0F && escape.dot(this.lastEscape) < 0.0F) {
            escape.set(this.lastEscape);
        }
        this.lastEscape.set(escape);
        double push = Math.min(worst, DEPEN_MAX);
        this.setPos(this.getX() + escape.x() * push, this.getY() + escape.y() * push,
                this.getZ() + escape.z() * push);
    }

    private void applyGatedRotationInner() {
        float dx = this.rollDeltaX;
        float dz = this.rollDeltaZ;
        if (this.level().isClientSide() || this.isClimbing()
                || (Math.abs(dx) < ROLL_GATE_EPSILON && Math.abs(dz) < ROLL_GATE_EPSILON)) {
            boolean watched = !this.level().isClientSide() && this.isClimbing();
            double penBefore = watched ? this.penetrationNow() : 0.0;
            applyMovementRotation(dx, dz, this.rotation);
            if (watched) {
                double penAfter = this.penetrationNow();
                if (penAfter > penBefore + DEPEN_EPSILON) {
                    if (shouldLog) LOGGER.debug("[livingblock] pensource id={} source=rollnogate before={} after={} dx={} dz={} tilt={} pos={}",
                            this.getId(), String.format("%.5f", penBefore),
                            String.format("%.5f", penAfter), String.format("%.4f", dx),
                            String.format("%.4f", dz), String.format("%.2f", this.tiltDegrees()),
                            String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                }
            }
            return;
        }
        java.util.function.DoubleSupplier overlap =
                LivingBlockCollisionHandler.rollOverlapProbe(this, this.rollReach());
        boolean tell = !this.level().isClientSide();
        if (overlap == null) {
            if (tell) {
                if (shouldLog) LOGGER.debug("[livingblock] rollprobe id={} state=noprobe dx={} dz={} carry={},{} tilt={}",
                        this.getId(), String.format("%.4f", dx), String.format("%.4f", dz),
                        String.format("%.4f", this.rollCarryX), String.format("%.4f", this.rollCarryZ),
                        String.format("%.2f", this.tiltDegrees()));
            }
            applyMovementRotation(dx, dz, this.rotation);
            return;
        }
        if (tell) {
            if (shouldLog) LOGGER.debug("[livingblock] rollprobe id={} state=gated dx={} dz={} carry={},{} tilt={} ground={} settled={} {}",
                    this.getId(), String.format("%.4f", dx), String.format("%.4f", dz),
                    String.format("%.4f", this.rollCarryX), String.format("%.4f", this.rollCarryZ),
                    String.format("%.2f", this.tiltDegrees()), this.onGround(),
                    this.isOrientationSettled(),
                    LivingBlockCollisionHandler.rollOverlapReport(this, this.rollReach()));
        }
        Quaternionf beforeRoll = new Quaternionf(this.rotation);
        double[] gainBefore = tell
                ? LivingBlockCollisionHandler.rollOverlapPair(this, this.rollReach()) : null;
        double[] demand = null;
        if (tell) {
            Quaternionf probePose = new Quaternionf(this.rotation);
            LivingBlockRoll.applyRoll(this.rotation, dx, dz);
            demand = LivingBlockCollisionHandler.rollOverlapPair(this, this.rollReach());
            this.rotation.set(probePose);
        }
        LivingBlockRoll.GatedRoll gate = LivingBlockRoll.gateRoll(this.rotation, dx, dz,
                ROLL_GATE_BUDGET, ROLL_GATE_PASSES, overlap);
        if (gainBefore != null) {
            double[] after = LivingBlockCollisionHandler.rollOverlapPair(this, this.rollReach());
            if (shouldLog) LOGGER.debug("[livingblock] rollgain id={} scale={} terrainbefore={} terrainafter={} terraingain={} terraindemand={} bodybefore={} bodyafter={} bodygain={} bodydemand={} budget={} dx={} dz={}",
                    this.getId(), String.format("%.3f", gate.scale()),
                    String.format("%.5f", gainBefore[0]), String.format("%.5f", after[0]),
                    String.format("%+.5f", after[0] - gainBefore[0]),
                    String.format("%+.5f", demand[0] - gainBefore[0]),
                    String.format("%.5f", gainBefore[1]), String.format("%.5f", after[1]),
                    String.format("%+.5f", after[1] - gainBefore[1]),
                    String.format("%+.5f", demand[1] - gainBefore[1]),
                    String.format("%.5f", ROLL_GATE_BUDGET),
                    String.format("%.4f", dx), String.format("%.4f", dz));
        }
        if (gate.scale() >= 1.0) {
            if (tell) {
                if (shouldLog) LOGGER.debug("[livingblock] rollgate id={} state=full scale={} dx={} dz={}",
                        this.getId(), String.format("%.3f", gate.scale()),
                        String.format("%.4f", dx), String.format("%.4f", dz));
            }
            return;
        }
        if (this.prefersLowStep() && this.seatRollByTipping(beforeRoll, dx, dz, gate)) {
            return;
        }
        if (gate.scale() < ROLL_UNWIND_SCALE
                && angularDistance(this.rotation, LivingBlockOrientation.fromQuaternion(this.rotation))
                        > ROLL_UNWIND_TILT) {
            LivingBlockRoll.gateRoll(this.rotation, -this.rollDeltaX, -this.rollDeltaZ,
                    ROLL_GATE_BUDGET, ROLL_GATE_PASSES, overlap);
            this.rollCarryX = 0.0F;
            this.rollCarryZ = 0.0F;
            this.logRollGate(gate);
            return;
        }
        this.rollCarryX = (float) (dx * (1.0 - gate.scale()));
        this.rollCarryZ = (float) (dz * (1.0 - gate.scale()));
        this.logRollGate(gate);
    }

    private boolean seatRollByTipping(final Quaternionf beforeRoll, final float dx, final float dz,
                                      final LivingBlockRoll.GatedRoll gate) {
        Quaternionf clamped = new Quaternionf(this.rotation);
        Vec3 clampedPos = this.position();
        this.rotation.set(beforeRoll);
        double allowed = this.penetrationNow() + DEPEN_EPSILON;
        applyMovementRotation(dx, dz, this.rotation);
        boolean seated = this.seatByTipping(beforeRoll, allowed);
        if (seated) {
            this.rollCarryX = 0.0F;
            this.rollCarryZ = 0.0F;
        } else {
            this.rotation.set(clamped);
            this.setPos(clampedPos.x, clampedPos.y, clampedPos.z);
        }
        if (this.tickCount - this.tipLogTick >= ROT_LOG_INTERVAL || seated != this.tipLastSeated) {
            this.tipLogTick = this.tickCount;
            this.tipLastSeated = seated;
            if (shouldLog) LOGGER.debug("[livingblock] tip id={} seated={} scale={} asked={} tilt={} gap={} ground={} pos={}",
                    this.getId(), seated, String.format("%.3f", gate.scale()),
                    String.format("%.2f", this.rollAngle), String.format("%.2f", this.tiltDegrees()),
                    String.format("%.5f", allowed), this.onGround(),
                    String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
        }
        return seated;
    }

    private double rollReach() {
        AABB bounds = this.getBaseShapeBounds();
        double radius = 0.5 * Math.sqrt(bounds.getXsize() * bounds.getXsize()
                + bounds.getYsize() * bounds.getYsize() + bounds.getZsize() * bounds.getZsize());
        return Math.min(ROLL_GATE_MAX_REACH,
                4.0 * radius * Math.abs(Math.sin(Math.toRadians(this.rollAngle) * 0.5)));
    }

    private void logRollGate(final LivingBlockRoll.GatedRoll gate) {
        this.rollGateBites++;
        this.rollGateWorstRaw = Math.max(this.rollGateWorstRaw, gate.gainedRaw());
        this.rollGateWorstGain = Math.max(this.rollGateWorstGain, gate.gained());
        if (this.tickCount - this.rollGateLogTick > 1) {
            if (shouldLog) LOGGER.debug("[livingblock] rollgate id={} side=S req={} raw={} gain={} scale={} carry={} climbing={} ground={} pos={}",
                    this.getId(), String.format("%.2f", this.rollAngle),
                    String.format("%.4f", gate.gainedRaw()), String.format("%.4f", gate.gained()),
                    String.format("%.3f", gate.scale()),
                    String.format("%.2f", 90.0 * Math.sqrt(
                            this.rollCarryX * this.rollCarryX + this.rollCarryZ * this.rollCarryZ)),
                    this.isClimbing(), this.onGround(),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
        this.rollGateLogTick = this.tickCount;
        if (this.tickCount - this.rollGateSumTick >= ROLL_GATE_SUM_INTERVAL) {
            this.rollGateSumTick = this.tickCount;
            if (shouldLog) LOGGER.debug("[livingblock] rollgate id={} sum n={} arcref={} rawmax={} gainmax={}",
                    this.getId(), this.rollGateBites, this.arcRefusals + this.terrainRefinements,
                    String.format("%.4f", this.rollGateWorstRaw),
                    String.format("%.4f", this.rollGateWorstGain));
            this.rollGateWorstRaw = 0.0;
            this.rollGateWorstGain = 0.0;
        }
    }

    public void getRotation(final Quaternionf dest, final float partialTicks) {
        this.drawnFrom.slerp(this.rotation, partialTicks, dest);
    }

    @Override
    protected double getDefaultGravity() {
        return this.climbPivotSequence ? 0.0 : 0.05;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDeadOrDying();
    }

    @Override
    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        }
        AABB hull = this.getBoundingBox();
        double size = Math.min(WALL_PROBE_SIZE,
                Math.min(hull.getXsize(), Math.min(hull.getYsize(), hull.getZsize())) * 0.5);
        if (size <= 0.0) {
            return false;
        }
        AABB probe = AABB.ofSize(hull.getCenter(), size, size, size);
        return BlockPos.betweenClosedStream(probe).anyMatch(pos -> this.suffocatesAt(pos, probe));
    }

    private boolean suffocatesAt(final BlockPos pos, final AABB probe) {
        BlockState state = this.level().getBlockState(pos);
        return !state.isAir()
                && state.isSuffocating(this.level(), pos)
                && Shapes.joinIsNotEmpty(
                        state.getCollisionShape(this.level(), pos).move(pos.getX(), pos.getY(), pos.getZ()),
                        Shapes.create(probe), BooleanOp.AND);
    }

    @Override
    public boolean isPushable() {
        return !this.usesOrientedCollision() && !this.isDeadOrDying() && !this.holdingGround;
    }

    @Override
    public void push(Entity entity) {
        if (!this.usesOrientedCollision()) {
            this.pushAgainst(entity, false);
        }
    }

    @Override
    protected void doPush(final Entity entity) {
        this.pushAgainst(entity, true);
    }

    private void pushAgainst(final Entity other, final boolean ours) {
        if (this.heldRider(other.getId(), this.level().getGameTime(), CARRY_PUSH_GRACE)) {
            return;
        }
        this.holdingGround = other instanceof Player;
        try {
            if (ours) {
                super.doPush(other);
            } else {
                super.push(other);
            }
        } finally {
            this.holdingGround = false;
        }
    }

    @Override
    protected void pushEntities() {
        if (!this.usesOrientedCollision()) {
            super.pushEntities();
        }
    }

    @Override
    public float maxUpStep() {
        return this.maxUpStep;
    }

    public void setMaxUpStep(final float maxUpStep) {
        this.maxUpStep = maxUpStep;
    }

    public Vec3 adjustStepUpMovement(final Vec3 movement) {
        return this.movement.adjustStepUpMovement(this, movement);
    }

    @Override
    public void travel(final Vec3 input) {
        if (this.vanillaOrderThisTick) {
            this.calculateEntityAnimation(false);
            return;
        }
        super.travel(input);
    }

    public void resetMaxUpStep() {
        this.maxUpStep = this.usesOrientedCollision()
                ? (float)Math.min(0.6, this.getBaseShapeBounds().getYsize())
                : 0.6F;
    }

    private void applyTumbleDynamics() {
        if (!this.onGround()) {
            if (this.tumbleArmed) {
                this.tumbleAirborne = true;
            }
            return;
        }

        this.tumbleArmed = false;
        if (this.tumbleAirborne) {
            this.tumbleAirborne = false;
            this.applyImpactSquash(-this.previousFallSpeed);
        }

        if (!this.isBeingDraggedRapidly() || this.isClimbing()) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.horizontalDistance();
        if (speed < TUMBLE_MIN_SPEED) {
            return;
        }

        Vector3f travel = new Vector3f((float) (velocity.x / speed), 0.0F, (float) (velocity.z / speed));
        Vector3f probe = new Vector3f(travel).mul(TUMBLE_PIVOT_BIAS).sub(0.0F, 1.0F, 0.0F);
        Vector3f pivot = LivingBlockCollisionHandler.supportCorner(this, this.rotation, probe);

        double drop = -pivot.y();
        if (drop < 1.0E-4) {
            return;
        }

        double along = pivot.x() * travel.x() + pivot.z() * travel.z();
        double radius = Math.sqrt(along * along + drop * drop);
        double phase = Math.atan2(drop, -along);
        double sinPhase = Math.sin(phase);
        if (sinPhase < 1.0E-4) {
            return;
        }

        double launchSpeedSqr = this.getGravity() * radius * sinPhase * sinPhase * sinPhase * TUMBLE_LAUNCH_MARGIN;
        if (speed * speed <= launchSpeedSqr) {
            return;
        }

        double slope = Mth.clamp(-Math.cos(phase) / sinPhase, 0.0, TUMBLE_MAX_SLOPE);
        double lift = Math.min(speed * slope, TUMBLE_LIFT_CAP);
        if (lift > TUMBLE_MIN_LIFT && lift > velocity.y) {
            this.setDeltaMovement(velocity.x, lift, velocity.z);
            this.tumbleArmed = true;
        }
    }

    private void applyImpactSquash(final double impactSpeed) {
        double strength = Mth.clamp((impactSpeed - IMPACT_SQUASH_MIN_SPEED) / IMPACT_SQUASH_RANGE, 0.0, 1.0) * IMPACT_SQUASH_MAX;
        if (strength < 1.0E-3) {
            return;
        }
        this.setPogoScaleTarget(new Vec3(1.0 + strength, 1.0 - strength, 1.0 + strength), IMPACT_SQUASH_TICKS);
        this.squashRecoverTicks = IMPACT_RECOVER_TICKS;
    }

    @Override
    protected AABB makeBoundingBox() {
        if (this.shapeBoxes == null || this.rotation == null) {
            return super.makeBoundingBox();
        }
        if (!this.orientedHull()) {
            return this.boundingBoxFor(this.restingFace());
        }
        if (this.isSettledPose()) {
            return this.boundingBoxFor(this.orientation);
        }

        AABB local = this.rotatedSilhouette(this.rotation);
        if (local == null) {
            return this.boundingBoxFor(null);
        }

        Vec3 pos = this.position();
        return local.move(pos.x, pos.y - local.minY, pos.z);
    }

    private AABB boundingBoxFor(final @Nullable LivingBlockOrientation target) {
        AABB bounds = this.getShapeBounds();
        Vec3 position = this.position();
        if (target == null || target.isIdentity()) {
            return bounds.move(position.x - this.shapeCenterX, position.y - bounds.minY, position.z - this.shapeCenterZ);
        }
        AABB local = target.transform(this.getCentredShapeBounds());
        return local.move(position.x, position.y - local.minY, position.z);
    }

    private void commitSettledOrientation() {
        LivingBlockOrientation nearest = LivingBlockOrientation.fromQuaternion(this.rotation);
        double speedSqr = this.getDeltaMovement().horizontalDistanceSqr();
        double angle = angularDistance(this.rotation, nearest);
        boolean wasSettled = this.entityData.get(DATA_ORIENTATION_SETTLED);

        boolean settled = this.onGround() && (wasSettled
                ? speedSqr < UNSETTLE_SPEED_SQR && angle < UNSETTLE_ANGLE
                : speedSqr < SETTLE_SPEED_EPSILON_SQR && angle < SETTLE_ANGLE_EPSILON);

        if (settled && nearest.index() != this.entityData.get(DATA_ORIENTATION)) {
            AABB candidate = this.boundingBoxFor(nearest);
            boolean fits = this.level().noBlockCollision(this, candidate.deflate(SETTLE_FIT_EPSILON));
            if (fits || !this.usesOrientedCollision()) {
                if (!fits) {
                    if (shouldLog) LOGGER.debug("[livingblock] facemiss id={} index={} box={} pos={}",
                            this.getId(), nearest.index(), candidate,
                            String.format("%.3f,%.3f,%.3f", this.getX(), this.getY(), this.getZ()));
                }
                this.entityData.set(DATA_ORIENTATION, nearest.index());
            } else {
                settled = false;
                this.orientationRejects++;
                if (this.tickCount % ROT_LOG_INTERVAL == 0) {
                    if (shouldLog) LOGGER.debug("[livingblock] fit id={} index={} rejects={} sunk={} box={}",
                            this.getId(), nearest.index(), this.orientationRejects,
                            String.format("%.8f", candidate.minY - Math.floor(candidate.minY)), candidate);
                }
            }
        }

        if (settled != this.entityData.get(DATA_ORIENTATION_SETTLED)) {
            this.entityData.set(DATA_ORIENTATION_SETTLED, settled);
        }
    }

    private static double angleBetween(final Quaternionfc from, final Quaternionfc to) {
        double dot = Math.abs((double) from.x() * to.x() + (double) from.y() * to.y()
                + (double) from.z() * to.z() + (double) from.w() * to.w());
        return Math.toDegrees(2.0 * Math.acos(Math.min(1.0, dot)));
    }

    private static double angularDistance(final Quaternionfc from, final LivingBlockOrientation to) {
        double length = Math.sqrt((double) from.x() * from.x() + (double) from.y() * from.y()
                + (double) from.z() * from.z() + (double) from.w() * from.w());
        if (!(length > 1.0E-6)) {
            return Double.MAX_VALUE;
        }
        double dot = to.absDot(from) / length;
        if (Double.isNaN(dot)) {
            return Double.MAX_VALUE;
        }
        return 2.0 * Math.acos(Math.min(1.0, dot));
    }

    @Override
    public Vec3 getLeashOffset(float partialTicks) {
        return new Vec3(0.0, this.getBoundingBox().getYsize() / 2.0, 0.0);
    }

    public MovementData getMovementData() {
        return this.entityData.get(MOVEMENT_DATA);
    }

    public void setMovementData(final MovementData data) {
        this.entityData.set(MOVEMENT_DATA, data);
    }

    public Target getMovementTarget() {
        return this.entityData.get(MOVEMENT_TARGET);
    }

    public @Nullable Vec3 getTargetPosition() {
        return this.getMovementTarget().resolvePosition(this.level());
    }

    public boolean hasMovementTarget() {
        return this.getTargetPosition() != null;
    }

    public boolean isIdle() {
        return !this.hasMovementTarget();
    }

    boolean claimCarry(final long gameTime) {
        if (this.carriedAtTick == gameTime) {
            return false;
        }
        this.carriedAtTick = gameTime;
        return true;
    }

    void discountCarriedMotion(final Vec3 applied) {
        this.xo += applied.x;
        this.yo += applied.y;
        this.zo += applied.z;
        this.xOld += applied.x;
        this.yOld += applied.y;
        this.zOld += applied.z;
    }

    @Override
    public boolean canBeSeenByAnyone() {
        return this.isAlive();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    @Override
    public AABB getHitbox() {
        return this.getBoundingBox();
    }

    public void setPogoScaleTarget(final Vec3 scale, final int ticks) {
        double length = this.pogoScaleTarget.subtract(scale).lengthSqr();
        if (!(length < 1.0E-5F)) {
            this.lastPogoScaleTarget = this.currentPogoScale;
            this.pogoScaleTarget = scale;
            this.pogoScaleTicks = this.pogoScaleTicksRemaining = ticks;
        }
    }

    public Vector3fc getPogoScale(final float partialTicks) {
        if (this.pogoScaleTicksRemaining == 0) {
            return this.currentPogoScale.toVector3f();
        } else {
            double t = partialTicks * (1.0 / this.pogoScaleTicks);
            return this.currentPogoScale.add(this.pogoScaleTarget.subtract(this.lastPogoScaleTarget).multiply(t, t, t)).toVector3f();
        }
    }

    public Quaternionf getLastRotation() {
        return this.lastRotation;
    }

    public boolean wantsTrace() {
        return this.troubleTicks > 0;
    }

    public double tiltDegrees() {
        return Math.toDegrees(angularDistance(this.rotation,
                LivingBlockOrientation.fromQuaternion(this.rotation)));
    }

    void recordResolve(final Vec3 in, final Vec3 world, final Vec3 full, final double stepRise,
                       final int boxes, final int blocks, final int colliders) {
        this.traceIn = in;
        this.traceWorld = world;
        this.traceFull = full;
        this.traceStepRise = stepRise;
        this.traceBoxes = boxes;
        this.traceBlocks = blocks;
        this.traceColliders = colliders;
    }

    void recordEscape(final double buried, final Vec3 escape, final int terrain) {
        this.traceBuried = buried;
        this.traceEscape = escape;
        this.traceTerrain = terrain;
    }

    void recordOverlap(final double worst, final int with) {
        this.traceOverlap = worst;
        this.traceWith = with;
    }

    boolean recordRim(final int with) {
        boolean fresh = with != this.traceRim;
        this.traceRim = with;
        return fresh;
    }

    private static String vec(final Vec3 v) {
        return String.format("%.4f,%.4f,%.4f", v.x, v.y, v.z);
    }

    private void emitTrace() {
        if (this.level().isClientSide()) {
            return;
        }
        if (this.onGround() || this.climbPivotSequence) {
            this.airTicks = 0;
        } else {
            this.airTicks++;
        }
        double tilt = this.tiltDegrees();
        boolean trouble = this.traceBuried > TRACE_BURIED
                || this.traceOverlap > TRACE_OVERLAP
                || this.airTicks > TRACE_AIR_TICKS
                || (!this.isOrientationSettled() && tilt > TRACE_TILT_DEGREES
                        && this.distanceToSqr(this.xo, this.yo, this.zo) < TRACE_STILL_SQR);
        if (!trouble) {
            this.troubleTicks = 0;
            return;
        }
        this.troubleTicks++;
        if (this.troubleTicks > TRACE_DENSE_TICKS && this.troubleTicks % TRACE_SPARSE_INTERVAL != 0) {
            return;
        }
        if (shouldLog) LOGGER.debug("[livingblock] trace id={} n={} pos={} vel={} ground={} air={} settled={} tilt={} "
                        + "climb={} step={} buried={} escape={} terrain={} obb={} with={} "
                        + "push={} applied={} in={} world={} full={} rise={} boxes={} blocks={} colliders={}",
                this.getId(), this.troubleTicks, vec(this.position()), vec(this.getDeltaMovement()),
                this.onGround(), this.airTicks, this.isOrientationSettled(),
                String.format("%.1f", tilt), this.getClimbingDirection(),
                String.format("%.2f", this.maxUpStep()), String.format("%.4f", this.traceBuried),
                vec(this.traceEscape), this.traceTerrain, String.format("%.4f", this.traceOverlap),
                this.traceWith, vec(this.tracePush), vec(this.traceApplied), vec(this.traceIn),
                vec(this.traceWorld), vec(this.traceFull), String.format("%.4f", this.traceStepRise),
                this.traceBoxes, this.traceBlocks, this.traceColliders);
    }

    public Quaternionf getRotation() {
        return this.rotation;
    }

    private double riderMissBar = 1.0E-2;

    public boolean raiseRiderMissBar(final double miss) {
        if (!(miss > this.riderMissBar)) {
            return false;
        }
        this.riderMissBar = miss * 2.0;
        return true;
    }

    public Quaternionf getTickStartRotation() {
        return this.tickStartRotation;
    }

    public void markTickStart() {
        this.tickStartRotation.set(this.rotation);
    }

    public void holdRider(final int riderId, final long gameTime) {
        this.riderHold.put(riderId, gameTime);
    }

    public boolean heldRider(final int riderId, final long gameTime, final int ticks) {
        Long seen = this.riderHold.get(riderId);
        if (seen == null) {
            return false;
        }
        if (gameTime - seen > ticks) {
            this.riderHold.remove(riderId);
            return false;
        }
        return true;
    }

    public void releaseRider(final int riderId) {
        this.riderHold.remove(riderId);
    }

    private static final int RIDER_HOLD_TICKS = 20;

    public boolean bodyAheadAtStep(final Direction direction) {
        if (!direction.getAxis().isHorizontal()) {
            return false;
        }
        AABB hull = this.getBoundingBox();
        AABB ahead = hull.expandTowards(direction.getStepX() * STEP_LOOK_AHEAD, 0.0,
                direction.getStepZ() * STEP_LOOK_AHEAD);
        boolean found = this.livingBlockInWay(new AABB(ahead.minX,
                hull.minY + LOW_STEP_HEIGHT + LOW_STEP_SLACK, ahead.minZ,
                ahead.maxX, hull.minY + 1.0 + LOW_STEP_SLACK, ahead.maxZ));
        if (found) {
            this.climbWhy(direction, "stepcut", String.format(
                    "feet=%.4f band=%.4f..%.4f reach=%.2f", hull.minY,
                    hull.minY + LOW_STEP_HEIGHT + LOW_STEP_SLACK,
                    hull.minY + 1.0 + LOW_STEP_SLACK, STEP_LOOK_AHEAD));
        }
        return found;
    }

    private static final double STEP_LOOK_AHEAD = LivingBlockStep.REACH;

    private void releaseStuckSequence() {
        if (this.level().isClientSide() || this.onGround() || this.airTicks <= STUCK_AIR_TICKS) {
            return;
        }
        boolean held = this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE)
                || this.entityData.get(DATA_STEP_PHASE) || this.climbPivotSequence;
        boolean hanging = this.getDeltaMovement().lengthSqr() < STUCK_STILL_SQR;
        if (!held && !hanging) {
            return;
        }
        if (shouldLog) LOGGER.debug("[livingblock] stuck id={} air={} hanging={} pivot={} phase={} seq={} tilt={} pos={}",
                this.getId(), this.airTicks, hanging,
                this.entityData.get(DATA_CLIMB_PIVOT_ACTIVE),
                this.entityData.get(DATA_STEP_PHASE), this.climbPivotSequence,
                String.format("%.1f", this.tiltDegrees()),
                String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        this.clearClimbPivot();
        this.entityData.set(DATA_STEP_PHASE, false);
        this.stepApproaching = false;
        this.stepLifting = false;
        this.stepDropping = false;
        this.setNoGravity(false);
        this.setDeltaMovement(0.0, -STUCK_KICK, 0.0);
    }

    private static final int STUCK_AIR_TICKS = 40;
    private static final double STUCK_STILL_SQR = 1.0E-8;
    private static final double STUCK_KICK = 0.02;
    private static final double RIGHTING_SPEED_SQR = 4.0E-4;

    private void groundOnBody() {
        if (!this.usesOrientedCollision()) {
            return;
        }
        if (this.onGround() || this.getDeltaMovement().y > 0.0) {
            return;
        }
        int floor = LivingBlockCollisionHandler.floorBody(this);
        if (floor < 0) {
            return;
        }
        this.setOnGround(true);
        if (this.floorBodyLogged != floor && !this.level().isClientSide()) {
            this.floorBodyLogged = floor;
            if (shouldLog) LOGGER.debug("[livingblock] bodysupport id={} over={} feet={} air={} tilt={} side={}",
                    this.getId(), floor, String.format("%.4f", this.getBoundingBox().minY),
                    this.getAirTicks(), String.format("%.2f", this.tiltDegrees()),
                    extent(this.getBoundingBox()));
        }
    }

    public void holdForRider(final long gameTime) {
        this.riderHoldUntil = gameTime + RIDER_HOLD_TICKS;
        if (!this.riderHoldLogged && !this.level().isClientSide()) {
            this.riderHoldLogged = true;
            if (shouldLog) LOGGER.debug("[livingblock] wait id={} start until={} side={} pos={}", this.getId(),
                    this.riderHoldUntil, extent(this.getBoundingBox()),
                    String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
        }
    }

    public boolean isHoldingForRider() {
        if (!this.usesOrientedCollision()) {
            return false;
        }
        boolean holding = this.level().getGameTime() < this.riderHoldUntil;
        if (!holding && this.riderHoldLogged) {
            this.riderHoldLogged = false;
            if (!this.level().isClientSide()) {
                if (shouldLog) LOGGER.debug("[livingblock] wait id={} end side={} pos={}", this.getId(),
                        extent(this.getBoundingBox()),
                        String.format("%.2f,%.2f,%.2f", this.getX(), this.getY(), this.getZ()));
            }
        }
        return holding;
    }

    public float getRollAngle() {
        return this.rollAngle;
    }
}
