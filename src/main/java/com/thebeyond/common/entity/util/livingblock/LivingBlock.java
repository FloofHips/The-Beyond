package com.thebeyond.common.entity.util.livingblock;

import com.thebeyond.common.registry.BeyondEntityDataSerializers;
import com.thebeyond.common.registry.BeyondTags;
import com.thebeyond.common.entity.util.livingblock.movement.*;
import com.thebeyond.common.entity.util.livingblock.LivingBlockShapeFactory;
import com.thebeyond.util.MathHelpers;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import javax.annotation.Nullable;

import static net.minecraft.core.Direction.*;

public class LivingBlock extends Mob {

    protected static final EntityDataAccessor<MovementData> MOVEMENT_DATA = SynchedEntityData.defineId(LivingBlock.class, BeyondEntityDataSerializers.MOVEMENT_DATA.get());
    protected static final EntityDataAccessor<Target> MOVEMENT_TARGET = SynchedEntityData.defineId(LivingBlock.class, BeyondEntityDataSerializers.TARGET.get());
    private static final EntityDataAccessor<Direction> DATA_CLIMBING_DIRECTION = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.DIRECTION);
    private static final EntityDataAccessor<Boolean> DATA_PINNED = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SHAPE_SEED = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DATA_MOVEMENT_TYPE = SynchedEntityData.defineId(LivingBlock.class, EntityDataSerializers.INT);

    private static final MovementStrategy<?> MV_ROLL = new RollingMovement();
    private static final MovementStrategy<?> MV_POGO = new BouncingMovement();
    private static final MovementStrategy<?> MV_DRIFT = new FloatingMovement();
    private static final MovementStrategy<?> MV_ROLL_FLOAT = new FluidFloatingMovement();

    private static final double AGGRO_RANGE = 24.0;

    private final Quaternionf lastRotation = new Quaternionf();
    private final Quaternionf rotation = new Quaternionf();

    private float rollDeltaX;
    private float rollDeltaZ;
    private int rollSoundTime;

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

    private MovementStrategy<?> movement = MV_ROLL_FLOAT;
    private float maxUpStep = 0.6F;
    private Vec3 pogoScaleTarget = new Vec3(1.0, 1.0, 1.0);
    private Vec3 lastPogoScaleTarget = new Vec3(1.0, 1.0, 1.0);
    private Vec3 currentPogoScale = new Vec3(1.0, 1.0, 1.0);
    private int pogoScaleTicksRemaining = 0;
    private int pogoScaleTicks = 0;
    private VoxelShape customShape = Shapes.block();
    private AABB shapeBounds;
    private List<AABB> shapeBoxes;
    private double shapeCenterX;
    private double shapeCenterZ;
    private boolean shapeIsEntropic;
    private boolean pendingFlop;
    private boolean wasGrounded;
    private boolean tumbleArmed;
    private boolean tumbleAirborne;
    private double previousFallSpeed;
    private int squashRecoverTicks;
    private CollisionInteraction collision = CollisionInteraction.ENTITY;

    public CollisionInteraction getCollision() {
        return this.collision;
    }

    public void setCollision(CollisionInteraction collision) {
        this.collision = collision != null ? collision : CollisionInteraction.ENTITY;
    }

    public LivingBlock(final EntityType<? extends Mob>  type, final Level level) {
        super(type, level);
        this.setMovementData(this.movement.initData());
        this.noPhysics = false;
    }

    public VoxelShape getCustomShape() {
        return this.customShape;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        this.assignShapeSeed();
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    public boolean isEntropicForm() {
        return this.getType().is(BeyondTags.ENTROPIC_FORM);
    }

    private void assignShapeSeed() {
        if (this.entityData.get(DATA_SHAPE_SEED) == 0) {
            this.entityData.set(DATA_SHAPE_SEED, this.random.nextInt() | 1);
        }
    }

    protected VoxelShape generateShape(final RandomSource random, final boolean entropic) {
        return LivingBlockShapeFactory.create(random, entropic);
    }

    private void applyShape() {
        this.shapeIsEntropic = this.isEntropicForm();
        this.setShape(this.generateShape(
                RandomSource.create(this.entityData.get(DATA_SHAPE_SEED)), this.shapeIsEntropic));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    public void setShape(VoxelShape shape) {
        this.customShape = shape;
        this.cacheShapeGeometry();
        this.refreshDimensions();
    }

    private void cacheShapeGeometry() {
        VoxelShape shape = this.customShape != null ? this.customShape : Shapes.block();
        this.shapeBounds = shape.bounds();
        this.shapeBoxes = List.copyOf(shape.toAabbs());
        this.shapeCenterX = Mth.lerp(0.5, this.shapeBounds.minX, this.shapeBounds.maxX);
        this.shapeCenterZ = Mth.lerp(0.5, this.shapeBounds.minZ, this.shapeBounds.maxZ);
    }

    public AABB getShapeBounds() {
        if (this.shapeBounds == null) {
            this.cacheShapeGeometry();
        }
        return this.shapeBounds;
    }

    public List<AABB> getShapeBoxes() {
        if (this.shapeBoxes == null) {
            this.cacheShapeGeometry();
        }
        return this.shapeBoxes;
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
        AABB b = this.getShapeBounds();
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
        return MV_ROLL_FLOAT; 
    }

    public void setMovement(final MovementStrategy<?> movement) {
        if (movement == null || movement == this.movement) {
            return;
        }
        this.movement = movement;
        if (!this.level().isClientSide()) {
            this.setMovementData(movement.initData());
            this.entityData.set(DATA_MOVEMENT_TYPE, movementTypeId());
        }
    }

    private int movementTypeId() {
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
            default -> MV_ROLL;
        };
    }

    @Override
    public void onSyncedDataUpdated(final EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MOVEMENT_TYPE.equals(key) && this.level().isClientSide()) {
            this.movement = movementFromId(this.entityData.get(DATA_MOVEMENT_TYPE));
        }
        if (DATA_SHAPE_SEED.equals(key)) {
            this.applyShape();
        }
    }

    public void setMovementTarget(final Target target) {
        this.entityData.set(MOVEMENT_TARGET, target == null ? Target.NONE : target);
    }

    public void clearMovementTarget() {
        this.setMovementTarget(Target.NONE);
    }

    @Override
    protected void positionRider(final Entity passenger, final MoveFunction moveFunction) {
        if (this.isVehicle() && this.getPassengers().contains(passenger)) {
            double seatHeight = 0.5625;
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
        entityData.define(DATA_PINNED, false);
        entityData.define(MOVEMENT_TARGET, Target.NONE);
        entityData.define(MOVEMENT_DATA, MovementData.EMPTY);
        entityData.define(DATA_CLIMBING_DIRECTION, Direction.DOWN);
        entityData.define(DATA_MOVEMENT_TYPE, 3);
        entityData.define(DATA_SHAPE_SEED, 0);
        
    }

    public boolean isPinned() {
        return this.entityData.get(DATA_PINNED);
    }

    public void setPinned(final boolean pinned) {
        this.entityData.set(DATA_PINNED, pinned);
    }

    public Direction getClimbingDirection() {
        return this.entityData.get(DATA_CLIMBING_DIRECTION);
    }

    public void setClimbingDirection(final Direction direction) {
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_CLIMBING_DIRECTION, direction);
        }
    }

    public void resetClimbingDirection() {
        this.setClimbingDirection(Direction.DOWN);
    }

    public boolean isClimbing() {
        return this.getClimbingDirection().getAxis() != Axis.Y;
    }

    public MovementStrategy<?> getMovement() {
        return this.movement;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag input) {
        super.readAdditionalSaveData(input);
        if (input.contains("shape_seed")) {
            this.entityData.set(DATA_SHAPE_SEED, input.getInt("shape_seed"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag output) {
        super.addAdditionalSaveData(output);
        output.putInt("shape_seed", this.entityData.get(DATA_SHAPE_SEED));
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
        Vector3f localForward = Direction.NORTH.step().rotate(rotation);
        Vector3f localUp = Direction.UP.step().rotate(rotation);
        return new Quaternionf()
                .lookAlong(getNearest(localForward.x,localForward.y,localForward.z, Direction.NORTH).step(), getNearest(localUp.x,localUp.y,localUp.z, Direction.UP).step())
                .conjugate();
    }

    public static @Nullable Direction getNearest(float x, float y, float z, @Nullable Direction orElse) {
        float absX = Math.abs(x);
        float absY = Math.abs(y);
        float absZ = Math.abs(z);
        if (absX > absZ && absX > absY) {
            return x < 0.0F ? WEST : EAST;
        } else if (absZ > absX && absZ > absY) {
            return z < 0.0F ? NORTH : SOUTH;
        } else if (absY > absX && absY > absZ) {
            return y < 0.0F ? DOWN : UP;
        } else {
            return orElse;
        }
    }

    @Override
    public void tick() {
        if (this.hurtTime > 0) {
            this.hurtTime--;
        }

        if (this.isDeadOrDying()) {
            this.tickDeath();
        }

        applyMovementRotation(this.rollDeltaX, this.rollDeltaZ, this.rotation);
        if (this.rollSoundTime-- <= 0 && !this.normalStepSounds() && !this.movement.flopsOnLanding()
                && !this.lastRotation.equals(this.rotation, ROLL_ROTATION_DELTA_EPSILON)) {
            Quaternionf groundAngle = snapToNearestRightAngle(this.rotation);
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
        this.rollDeltaX = 0.0F;
        this.rollDeltaZ = 0.0F;
        super.tick();

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
                    LivingBlockCollisionHandler.groundContactSpan(this, snapToNearestRightAngle(this.rotation)));
            this.level().gameEvent(GameEvent.STEP, flopPos, Context.of(this, flopState));
        }

        if (!this.isDeadOrDying() && !this.isPinned()) {
            if (!this.level().isClientSide()) {
                this.setMovement(this.desiredMovement());
                Entity holder = this.isLeashed() ? this.getLeashHolder() : null;
                if (holder != null) {
                    this.setMovementTarget(Target.followingEntity(holder, 2.0));
                } else if (AGGRO_RANGE > 0.0) {
                    Player nearest = this.level().getNearestPlayer(this, AGGRO_RANGE);
                    if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
                        this.setMovementTarget(Target.followingEntity(nearest, 1.0));
                    } else {
                        this.clearMovementTarget();
                    }
                }
            }

            Target target = this.getMovementTarget();
            Vec3 targetPos = target.resolvePosition(this.level());
            if (targetPos != null) {
                boolean moved = this.movement.moveTowardsTarget(this, target, targetPos);
                if (!moved && target.clearWhenNear()) {
                    this.movement.resetMovement(this);
                }
            }
        }

        if (this.collision.canBeNudged()) {
            AABB bounds = this.getBoundingBox();
            for (Entity entity : this.level().getEntities(this, AABB.ofSize(bounds.getCenter(), 0.05, 0.05, 0.05), t -> t instanceof LivingBlock livingBlock && livingBlock.collision.canBeNudged())) {
                entity.push(this);
            }
        }

        boolean isClimbing = this.isClimbing();
        BlockPos posBelow = this.getBlockPosBelowThatAffectsMyMovement();
        float blockFriction = this.onGround() ? this.level().getBlockState(posBelow).getBlock().getFriction() : 1.0F;
        float friction = blockFriction * 0.96F;
        float frictionY = isClimbing ? 0.57600003F : 0.98F;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, frictionY, friction));
        if (!isClimbing || this.onGround()) {
            this.applyGravity();
        }

        if (this.isInWater()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        }

        this.applyTumbleDynamics();

        if (this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() < Mth.square(0.001) || isClimbing && Math.abs(this.getDeltaMovement().y) < 0.001) {
            Vec3 blockGridDeltaFull = this.blockPosition().getBottomCenter().subtract(this.position());
            Vec3 blockGridDelta = new Vec3(blockGridDeltaFull.x, 0, blockGridDeltaFull.z);
            double blockGridOffset = blockGridDelta.length();
            double rotationAlpha = Mth.clamp(blockGridOffset * 64.0, 0.5, 1.0);
            this.rotation.slerp(snapToNearestRightAngle(this.rotation), (float)rotationAlpha);
            if (blockGridOffset > 1.0E-6 && blockGridOffset <= 0.125 && this.isIdle() && !this.level().isClientSide()) {
                this.move(MoverType.SELF, blockGridDelta);
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

        AABB bounds = this.getBoundingBox();
        double sizeY = bounds.getYsize();
        if (sizeY > 1.0E-5F) {
            Vector3f localYaxis = Mth.Y_AXIS.rotate(this.lastRotation, new Vector3f());
            float tilt = Math.abs(localYaxis.y);
            double sizeX = bounds.getXsize();
            double sizeZ = bounds.getZsize();
            float rotationSpeed = !isClimbing && !this.onGround() ? (this.tumbleAirborne ? 1.0F : 0.5F) : 1.0F;
            if (sizeZ > 1.0E-5F) {
                double sideLength = sizeZ < sizeY
                        ? Mth.lerp((double)tilt, Math.sqrt(sizeZ / sizeY) * sizeY, sizeY)
                        : Mth.lerp((double)tilt, sizeZ, Math.sqrt(sizeY / sizeZ) * sizeZ);
                this.rollDeltaX = this.rollDeltaX + (float)((this.getZ() - this.zo) * rotationSpeed / sideLength);
            }

            if (sizeX > 1.0E-5F) {
                double sideLength = sizeX < sizeY
                        ? Mth.lerp((double)tilt, Math.sqrt(sizeX / sizeY) * sizeY, sizeY)
                        : Mth.lerp((double)tilt, sizeX, Math.sqrt(sizeY / sizeX) * sizeX);
                this.rollDeltaZ = this.rollDeltaZ - (float)((this.getX() - this.xo) * rotationSpeed / sideLength);
            }

            if (isClimbing) {
                Direction groundDirection = this.getClimbingDirection();
                boolean onXAxis = groundDirection.getAxis() == Axis.X;
                double size = onXAxis ? sizeZ : sizeX;
                double sideLength = size < sizeY
                        ? Mth.lerp((double)tilt, sizeY, Math.sqrt(size / sizeY) * sizeY)
                        : Mth.lerp((double)tilt, Math.sqrt(sizeY / size) * size, size);
                int sign = groundDirection.getAxisDirection().getStep();
                float roll = (float)((this.getY() - this.yo) * rotationSpeed / sideLength * sign);
                if (onXAxis) {
                    this.rollDeltaZ += roll;
                } else {
                    this.rollDeltaX += roll;
                }
            }
        }

        this.previousFallSpeed = this.getDeltaMovement().y;
    }

    protected boolean normalStepSounds() {
        return this.movement.normalStepSounds();
    }

    private static void applyMovementRotation(final float dx, final float dz, final Quaternionf dest) {
        if (Math.abs(dx) > 1.0E-5F) {
            dest.rotateLocalX(dx * (float) (Math.PI / 2));
        }

        if (Math.abs(dz) > 1.0E-5F) {
            dest.rotateLocalZ(dz * (float) (Math.PI / 2));
        }
    }

    public void getRotation(final Quaternionf dest, final float partialTicks) {
        this.lastRotation.slerp(this.rotation, partialTicks, dest);
        applyMovementRotation(this.rollDeltaX * partialTicks, this.rollDeltaZ * partialTicks, dest);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDeadOrDying();
    }

    @Override
    public boolean isPushable() {
        return !this.isDeadOrDying() && this.collision.isPushable();
    }

    @Override
    public float maxUpStep() {
        return this.maxUpStep;
    }

    public void setMaxUpStep(final float maxUpStep) {
        this.maxUpStep = maxUpStep;
    }

    public void resetMaxUpStep() {
        this.maxUpStep = 0.6F;
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
        AABB bounds = this.getShapeBounds();
        Vec3 position = this.position();
        return bounds.move(position.x - this.shapeCenterX, position.y - bounds.minY, position.z - this.shapeCenterZ);
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
}