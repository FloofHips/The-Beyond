package com.thebeyond.common.entity.util.livingblock;

import com.thebeyond.common.registry.BeyondEntityDataSerializers;
import com.thebeyond.common.entity.util.livingblock.movement.*;
import com.thebeyond.util.MathHelpers;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
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

    private final Quaternionf lastRotation = new Quaternionf();
    private final Quaternionf rotation = new Quaternionf();

    private float rollDeltaX;
    private float rollDeltaZ;
    private int rollSoundTime;

    private static final int ROLL_SOUND_MIN_TIME = 4;
    private static final double ROLL_SOUND_MIN_ANGLE = 10.0;
    private static final float ROLL_ROTATION_DELTA_EPSILON = 1.0E-5F;

    public Vec3 boundingBoxOffset = Vec3.ZERO;
    private MovementStrategy<?> movement = new RollingMovement();
    private float maxUpStep = 0.6F;
    private Vec3 pogoScaleTarget = new Vec3(1.0, 1.0, 1.0);
    private Vec3 lastPogoScaleTarget = new Vec3(1.0, 1.0, 1.0);
    private Vec3 currentPogoScale = new Vec3(1.0, 1.0, 1.0);
    private int pogoScaleTicksRemaining = 0;
    private int pogoScaleTicks = 0;
    private VoxelShape customShape = Shapes.block();

    public LivingBlock(final EntityType<? extends Mob>  type, final Level level) {
        super(type, level);
        this.setMovementData(this.movement.initData());
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    public void setShape(VoxelShape shape) {
        this.customShape = shape;
        this.refreshDimensions();
    }

    private static @Nullable BlockPos findFreeSpawnInColumn(final Level level, final BlockPos base, final Set<BlockPos> used) {
        MutableBlockPos pos = base.mutable();

        for (int y = 0; y < 5; y++) {
            if (!used.contains(pos) && level.noCollision(AABB.encapsulatingFullBlocks(pos, pos).deflate(1.0E-7))) {
                return pos.immutable();
            }

            pos.move(Direction.UP);
        }

        pos.set(base).move(Direction.DOWN);

        for (int y = 0; y < 3; y++) {
            if (!used.contains(pos) && level.noCollision(AABB.encapsulatingFullBlocks(pos, pos).deflate(1.0E-7))) {
                return pos.immutable();
            }

            pos.move(Direction.DOWN);
        }

        return null;
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
        this.setPinned(input.getBoolean("pinned"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag output) {
        output.putBoolean("pinned", this.isPinned());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void playStepSound(final BlockPos pos, final BlockState movingOn) {
        super.playStepSound(pos, movingOn);
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
        if (this.rollSoundTime-- <= 0 && !this.normalStepSounds() && !this.lastRotation.equals(this.rotation, 1.0E-5F)) {
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
            if (lastDistanceToZero > 10.0 && currentDistanceToZero <= 10.0) {
                BlockPos effectPos = this.getOnPosLegacy();
                BlockState effectState = this.level().getBlockState(effectPos);
                //this.walkingStepSound(effectPos, effectState);
                this.level().gameEvent(GameEvent.STEP, effectPos, Context.of(this, effectState));
                this.rollSoundTime = 4;
            }
        }

        this.lastRotation.set(this.rotation);
        this.rollDeltaX = 0.0F;
        this.rollDeltaZ = 0.0F;
        super.tick();

        if (!this.isDeadOrDying() && !this.isPinned()) {
            Target target = this.getMovementTarget();
            Vec3 targetPos = target.resolvePosition(this.level());
            if (targetPos != null) {
                boolean moved = this.movement.moveTowardsTarget(this, target, targetPos);
                if (!moved && target.clearWhenNear()) {
                    this.movement.resetMovement(this);
                }
            }
        }

        //this.move(MoverType.SELF, this.getDeltaMovement());
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

        if (this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() < Mth.square(0.001) || isClimbing && Math.abs(this.getDeltaMovement().y) < 0.001) {
            Vec3 blockGridDeltaFull = this.blockPosition().getBottomCenter().subtract(this.position());
            Vec3 blockGridDelta = new Vec3(blockGridDeltaFull.x, 0, blockGridDeltaFull.z);
            double blockGridOffset = blockGridDelta.length();
            double rotationAlpha = Mth.clamp(blockGridOffset * 64.0, 0.5, 1.0);
            this.rotation.slerp(snapToNearestRightAngle(this.rotation), (float)rotationAlpha);
            if (blockGridOffset > 1.0E-6 && blockGridOffset <= 0.125 && this.isIdle() && !this.level().isClientSide()) {
                this.move(MoverType.SELF, blockGridDelta);
            }
        }

        Vector3f facing = new Vector3f();
        this.rotation.getEulerAnglesYXZ(facing);
        if (facing.isFinite()) {
            this.setRot(facing.y * (180.0F / (float)Math.PI), facing.x * (180.0F / (float)Math.PI));
        } else {
            this.setRot(0.0F, 0.0F);
            this.rotation.identity();
        }

        if (this.movement instanceof BouncingMovement) {
            Vec3 pogoScaleDiff = this.pogoScaleTarget.subtract(this.currentPogoScale);
            if (pogoScaleDiff.lengthSqr() > 1.0E-5F) {
                this.currentPogoScale = MathHelpers.vecLerp((float) (1.0 - (double)this.pogoScaleTicksRemaining / this.pogoScaleTicks), this.lastPogoScaleTarget, this.pogoScaleTarget);
            }

            if (this.pogoScaleTicksRemaining > 0) {
                this.pogoScaleTicksRemaining--;
            }
        }

        AABB bounds = this.getBoundingBox();
        double sizeY = bounds.getYsize();
        if (sizeY > 1.0E-5F) {
            Vector3f localYaxis = Mth.Y_AXIS.rotate(this.lastRotation, new Vector3f());
            float tilt = Math.abs(localYaxis.y);
            double sizeX = bounds.getXsize();
            double sizeZ = bounds.getZsize();
            float rotationSpeed = !isClimbing && !this.onGround() ? 0.5F : 1.0F;
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
                this.rollDeltaZ = this.rollDeltaZ + (float)((this.getX() - this.xo) * rotationSpeed / sideLength);
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
    public boolean isPushable() {
        return !this.isDeadOrDying();
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


    @Override
    protected AABB makeBoundingBox() {
        Vec3 position = this.getPosition(0);

        VoxelShape shape = Block.box((double)6.0F, (double)0.0F, (double)6.0F, (double)10.0F, (double)10.0F, (double)10.0F);

        AABB bounds = shape.bounds();
        this.boundingBoxOffset = bounds.getCenter().reverse();

        return bounds.move(position.subtract(bounds.getBottomCenter()));
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
