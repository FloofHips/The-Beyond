package com.thebeyond.common.entity.util.livingblock.movement;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class RollingMovement implements MovementStrategy<RollingMovement.Data> {
    private static final int MOVE_ATTEMPTS_UNTIL_NOT_INTERESTED = 10;
    private static final int TICKS_BEFORE_MOVING_AFTER_FAIL = 6;
    private static final int FORGET_MOVE_TARGET_CHESSBOARD_DISTANCE = 1;

    public RollingMovement.Data initData() {
        return new RollingMovement.Data();
    }

    @Override
    public boolean moveTowardsTarget(LivingBlock entity, final Target target, final Vec3 targetPos) {
        if (this.checkIsMovementDisabled(entity)) {
            return true;
        } else {
            RollingMovement.Data data = this.getData(entity);
            if (data.moveTicks == 0) {
                Direction continueInDirection = null;
                if (entity.isClimbing()) {
                    if (!entity.horizontalCollision) {
                        entity.resetClimbingDirection();
                    } else {
                        continueInDirection = entity.getClimbingDirection();
                    }
                }

                boolean ableToMove;
                if (continueInDirection != null) {
                    ableToMove = this.tryFindNextMoveStep(entity, continueInDirection);
                } else {
                    Vec3 pos = entity.position();
                    Vec3 delta = targetPos.subtract(pos);
                    double minDistanceSq = Mth.square(target.distance());
                    if (delta.horizontalDistanceSqr() < minDistanceSq) {
                        return false;
                    }

                    BlockPos targetBlockPos = BlockPos.containing(targetPos);
                    Vec3 roundedDelta = targetBlockPos.getBottomCenter().subtract(pos);
                    if (roundedDelta.horizontalDistanceSqr() < minDistanceSq) {
                        return false;
                    }

                    Direction direction = Direction.getNearest(delta.x, delta.y, delta.z);
                    ableToMove = this.tryFindNextMoveStep(entity, direction);
                }

                if (!ableToMove) {
                    if (data.failedMoveAttempts >= 10) {
                        return false;
                    }

                    data.moveTicks = 6;
                    entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
                    return true;
                }

                data.moveTicks = entity.getRandom().nextIntBetweenInclusive(5, 9);
            }

            if (data.movingTo != null && !entity.level().isClientSide()) {
                double scale = 1.0 / Math.pow(data.moveTicks, 1.5);
                data.movingTo.applyMovement(entity, scale);
            }

            data.moveTicks--;
            return true;
        }
    }

    @Override
    public boolean normalStepSounds() {
        return false;
    }

    @Override
    public void resetMovement(final LivingBlock entity) {
        boolean wasClimbing = entity.isClimbing();
        RollingMovement.Data data = this.getData(entity);
        data.movingTo = null;
        data.moveTicks = 0;
        data.failedMoveAttempts = 0;
        entity.resetMaxUpStep();
        entity.resetClimbingDirection();
        if (wasClimbing || entity.onGround()) {
            MovementStrategy.resetVelocity(entity, wasClimbing);
        }
    }

    @Override
    public Vec3 adjustStepUpMovement(final LivingBlock entity, final Vec3 movement) {
        RollingMovement.Data data = this.getData(entity);
        if (data.movingTo != null && data.movingTo.type == RollingMovement.MoveType.STEP_UP) {
            Direction stepUpDirection = data.movingTo.direction;
            double amount = stepUpDirection.getAxis().choose(movement.x, movement.y, movement.z);
            return Mth.sign(amount) != stepUpDirection.getAxisDirection().getStep() ? Vec3.ZERO : new Vec3(stepUpDirection.getStepX() * Math.abs(amount), stepUpDirection.getStepY() * Math.abs(amount), stepUpDirection.getStepZ() * Math.abs(amount));
        } else {
            return Vec3.ZERO;
        }
    }

    private boolean tryFindNextMoveStep(final LivingBlock entity, final Direction direction) {
        Vec3 minimumMove = new Vec3(direction.getStepX() * 0.1, direction.getStepY() * 0.1, direction.getStepZ() * 0.1);
        AABB nextPosBounds = entity.getBoundingBox().expandTowards(minimumMove).deflate(1.0E-6);
        BlockPos blockPos = entity.onGround() ? BlockPos.containing(entity.getBoundingBox().getCenter()) : entity.blockPosition();
        BlockPos nextBlockPos = blockPos.relative(direction);
        boolean isClimbing = entity.isClimbing();

        if (!isClimbing && entity.level().noCollision(entity, nextPosBounds)) {
            this.setMoveStep(entity, RollingMovement.MoveType.STEP_UP, nextBlockPos, direction);
            return true;
        } else {
            RollingMovement.Data data = this.getData(entity);
            if (!this.isAnyEntityStandingOn(entity)) {
                AABB onTopBounds = nextPosBounds.move(0.0, 1.0, 0.0);

                boolean blockCollision = !entity.level().noBlockCollision(entity, onTopBounds);
                boolean entityCollision = !entity.level().getEntityCollisions(entity, onTopBounds).isEmpty();

                if (!blockCollision && entity.onGround()) {
                    this.setMoveStep(entity, RollingMovement.MoveType.STEP_UP, nextBlockPos.above(), direction);
                    return true;
                }

                if (entityCollision && !isClimbing) {
                    data.failedMoveAttempts++;
                } else {
                    AABB directlyAboveBounds = entity.getBoundingBox().deflate(1.0E-6).move(0.0, 2.0E-6, 0.0);
                    if (entity.level().noBlockCollision(entity, directlyAboveBounds)) {
                        this.setMoveStep(entity, RollingMovement.MoveType.CLIMB, nextBlockPos.above(), direction);
                        return true;
                    }
                    data.failedMoveAttempts = 10;
                }
            } else {
                if (isClimbing) {
                    this.setMoveStep(entity, RollingMovement.MoveType.IDLE_CLING, nextBlockPos, direction);
                    return true;
                }
                entity.resetClimbingDirection();
            }

            data.movingTo = null;
            entity.resetMaxUpStep();
            return false;
        }
    }

    private void setMoveStep(final LivingBlock entity, final RollingMovement.MoveType moveType, final BlockPos movingToBlock, final Direction direction) {
        this.setMoveStep(entity, moveType, movingToBlock.getBottomCenter(), direction);
    }

    private void setMoveStep(final LivingBlock entity, final RollingMovement.MoveType moveType, final Vec3 movingToPos, final Direction direction) {
        RollingMovement.Data data = this.getData(entity);
        data.movingTo = new RollingMovement.MoveStep(moveType, movingToPos, direction);
        data.movingTo.setOn(entity);
        data.failedMoveAttempts = 0;
    }

    private boolean checkIsMovementDisabled(final LivingBlock entity) {
        RollingMovement.Data data = this.getData(entity);
        if (!entity.onGround() && !entity.isClimbing()) {
            if (data.movingTo != null) {
                Vec3 movement = entity.getDeltaMovement();
                if (movement.y <= 0.0 && movement.y >= -entity.getGravity() * 2.0) {
                    entity.setDeltaMovement(0.0, movement.y, 0.0);
                    data.moveTicks = 0;
                }
            }

            data.movingTo = null;
            data.failedMoveAttempts = 0;
            return true;
        } else if (data.movingTo == null && data.moveTicks > 0) {
            data.moveTicks--;
            return true;
        } else {
            if (data.movingTo != null && entity.blockPosition().distManhattan(BlockPos.containing(data.movingTo.position)) > 1) {
                data.moveTicks = 6;
                data.movingTo = null;
            }

            return false;
        }
    }

    private boolean isAnyEntityStandingOn(final LivingBlock entity) {
        AABB aabb = entity.getBoundingBox();
        AABB onTop = new AABB(aabb.minX, aabb.maxY, aabb.minZ, aabb.maxX, aabb.maxY + 1.0, aabb.maxZ).deflate(1.0E-6);
        return !entity.level()
                .getEntities(
                        EntityTypeTest.forClass(Entity.class),
                        onTop,
                        other -> other != entity && other.onGround() && (!(other instanceof LivingBlock) || entity.canCollideWith(other))
                ).isEmpty();
    }

    public static class Data implements MovementData {
        public @Nullable RollingMovement.MoveStep movingTo;
        public int moveTicks;
        public int failedMoveAttempts;

        public Data(@Nullable final RollingMovement. MoveStep movingTo, final int moveTicks, final int failedMoveAttempts) {
            this.movingTo = movingTo;
            this.moveTicks = moveTicks;
            this.failedMoveAttempts = failedMoveAttempts;
        }

        public Data() {
            this(null, 0, 0);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(this.movingTo != null);
            if (this.movingTo != null) {
                this.movingTo.write(buf);
            }
            buf.writeInt(this.moveTicks);
            buf.writeInt(this.failedMoveAttempts);
        }

        public static Data read(FriendlyByteBuf buf) {
            RollingMovement.MoveStep movingTo = buf.readBoolean() ? RollingMovement.MoveStep.read(buf) : null;
            int moveTicks = buf.readInt();
            int failedMoveAttempts = buf.readInt();
            return new Data(movingTo, moveTicks, failedMoveAttempts);
        }
    }

    public record MoveStep(RollingMovement.MoveType type, Vec3 position, Direction direction) {

        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(this.type);
            buf.writeDouble(this.position.x);
            buf.writeDouble(this.position.y);
            buf.writeDouble(this.position.z);
            buf.writeEnum(this.direction);
        }

        public static RollingMovement.MoveStep read(FriendlyByteBuf buf) {
            RollingMovement.MoveType type = buf.readEnum(RollingMovement.MoveType.class);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            Direction dir = buf.readEnum(Direction.class);
            return new RollingMovement.MoveStep(type, new Vec3(x, y, z), dir);
        }

        public void setOn(final LivingBlock entity) {
            if (this.type.isAttachedVertically()) {
                entity.setClimbingDirection(this.direction);
                entity.setMaxUpStep(0.0F);
                entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
            } else {
                entity.resetClimbingDirection();
                if (this.type == RollingMovement.MoveType.STEP_UP) {
                    entity.setMaxUpStep(1.0F);
                } else {
                    entity.resetMaxUpStep();
                }
            }
        }

        public void applyMovement(final LivingBlock entity, final double scale) {
            Vec3 predictedPos = entity.position().add(entity.getDeltaMovement());
            Vec3 delta = this.position.subtract(predictedPos);
            if (this.type.isAttachedVertically()) {
                AABB aabb = entity.getBoundingBox().deflate(1.0E-6).move(this.direction.getStepX() * 2.0E-6, this.direction.getStepY() * 2.0E-6, this.direction.getStepZ() * 2.0E-6);
                if (!entity.level().noCollision(entity, aabb)) {
                    Vec3 stickingForce = new Vec3(this.direction.getStepX() * 0.1, this.direction.getStepY() * 0.1, this.direction.getStepZ() * 0.1);
                    if (this.type == RollingMovement.MoveType.CLIMB)
                        entity.addDeltaMovement(stickingForce.add(0.0, delta.y * scale, 0.0));
                    else entity.addDeltaMovement(stickingForce);


                    return;
                }
            }

            entity.addDeltaMovement(new Vec3(delta.x * scale, 0.0, delta.z * scale));
        }
    }

    private static enum MoveType {
        DEFAULT,
        STEP_UP,
        CLIMB,
        IDLE_CLING;

        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(this);
        }

        public static MoveType read(FriendlyByteBuf buf) {
            return buf.readEnum(MoveType.class);
        }

        public boolean isAttachedVertically() {
            return this == CLIMB || this == IDLE_CLING;
        }
    }
}
