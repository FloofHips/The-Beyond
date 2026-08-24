package com.thebeyond.common.entity.util.livingblock.movement;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionHandler;
import com.thebeyond.common.entity.util.livingblock.LivingBlockPivot;
import com.thebeyond.common.entity.util.livingblock.LivingBlockStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.List;

public class RollingMovement implements MovementStrategy<RollingMovement.Data> {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int STEP_LOG_INTERVAL = 20;
    private static final int CLIMB_LOG_INTERVAL = 5;
    private static final double BOUNDARY_EPSILON = 1.0E-3;
    private static final double OBB_INFLATION_SLACK = 0.21;
    private static final double CLIMB_RISE_MIN = 0.075;
    private static final double CLIMB_RISE_MAX = 0.20;
    private static final int BLOCKED_UNTIL_NUDGE = 2;
    private static final double STEP_UP_EPSILON = 1.0E-6;
    private static final int RISE_SAMPLES = 8;
    private static final double CLIMB_CONTACT = 0.02;
    private static final double CLIMB_HEADROOM = 0.05;
    private static final double CLIMB_TICKS_PER_QUARTER = 8.0;
    private static final double STEP_SMOOTH = 0.125;
    private static final double CLIMB_REACH = 0.10;
    private static final double CLIMB_HOLD = 0.01;
    private static final double STEP_PUSH_LIMIT = 0.2;
    private static final double CLIMB_STALL_RISE = 0.01;
    private static final int CLIMB_STALL_TICKS = 20;
    private static final int QUARTER_SETTLE_TICKS = 5;
    private static final double BODY_IN_WAY_SLOP = 1.0E-3;
    private static final double SETTLE_SPEED = 0.05;
    private static final double SETTLE_REACH = 1.0;
    private static final int SETTLE_STUCK_TICKS = 20;
    private static final double SETTLE_PROGRESS = 1.0E-3;
    private static final int SETTLE_LOG_INTERVAL = 10;
    private static final double SETTLE_SLOP = 1.0E-6;
    private static final int SETTLE_PROBES = 5;
    private static final double SETTLE_MIN_STEP = 2.0E-3;
    private static final int SETTLE_WAIT_TICKS = 200;
    private static final double SETTLE_SUPPORT = 0.1;

    private static double requiredRise(final LivingBlock entity, final AABB nextPosBounds, final double limit) {
        for (int i = 1; i <= RISE_SAMPLES; i++) {
            double rise = limit * i / RISE_SAMPLES;
            if (entity.level().noCollision(entity, nextPosBounds.move(0.0, rise, 0.0))) {
                return rise;
            }
        }
        return entity.spaceFree(nextPosBounds.move(0.0, limit, 0.0))
                ? limit
                : Double.POSITIVE_INFINITY;
    }

    private static boolean climbStalled(final LivingBlock entity, final RollingMovement.Data data) {
        if (entity.getY() > data.climbLastY + CLIMB_STALL_RISE) {
            data.climbLastY = entity.getY();
            data.climbStallTicks = 0;
            return false;
        }
        if (++data.climbStallTicks < CLIMB_STALL_TICKS) {
            return false;
        }
        data.climbStallTicks = 0;
        data.climbLastY = Double.NEGATIVE_INFINITY;
        LOGGER.debug("[livingblock] climbstall id={} y={} dir={}",
                entity.getId(), String.format("%.4f", entity.getY()), entity.getClimbingDirection());
        return true;
    }

    private static boolean wallGone(final LivingBlock entity) {
        Direction direction = entity.getClimbingDirection();
        if (direction.getAxis() == Direction.Axis.Y) {
            return true;
        }
        AABB probe = entity.getBoundingBox().deflate(1.0E-6)
                .move(direction.getStepX() * CLIMB_REACH, 0.0, direction.getStepZ() * CLIMB_REACH);
        return entity.level().noCollision(entity, probe);
    }

    private static double climbRate(final LivingBlock entity) {
        return entity.getBaseShapeBounds().getYsize() / CLIMB_TICKS_PER_QUARTER + entity.getGravity();
    }

    private static AABB climbEntryProbe(final LivingBlock entity) {
        AABB hull = entity.getBoundingBox();
        return new AABB(hull.minX, hull.maxY, hull.minZ, hull.maxX, hull.maxY + CLIMB_HEADROOM, hull.maxZ)
                .deflate(CLIMB_CONTACT, 0.0, CLIMB_CONTACT);
    }

    private static AABB tiltProbe(final AABB bounds) {
        double shrink = Math.min(OBB_INFLATION_SLACK,
                Math.min(bounds.getXsize(), bounds.getZsize()) * 0.25);
        return bounds.deflate(shrink, 0.0, shrink);
    }

    static boolean lowStepDrive(final RollingMovement.Data data) {
        return data.movingTo != null && data.movingTo.type() == RollingMovement.MoveType.STEP_UP
                && data.movingTo.rise() > 0.0F
                && data.movingTo.rise() <= LivingBlock.LOW_STEP_HEIGHT;
    }

    public boolean isStepDriving(final LivingBlock entity) {
        RollingMovement.Data data = this.getData(entity);
        return data.moveTicks > 0 && lowStepDrive(data);
    }

    public RollingMovement.Data initData() {
        return new RollingMovement.Data();
    }

    @Override
    public boolean moveTowardsTarget(LivingBlock entity, final Target target, final Vec3 targetPos) {
        if (entity.hasActiveClimbPivot()) {
            return true;
        }
        if (this.checkIsMovementDisabled(entity)) {
            return true;
        } else {
            RollingMovement.Data data = this.getData(entity);
            if (data.moveTicks != 0) {
                entity.descentRoute("cadence", data.moveTicks, DESCENT_ENABLED,
                        entity.isClimbing(), entity.getDirection(), data.failedMoveAttempts,
                        this.getClass().getSimpleName());
            }
            if (data.moveTicks == 0) {
                Direction continueInDirection = null;
                if (entity.isClimbing()) {
                    if (!entity.hasActiveClimbPivot() && wallGone(entity)) {
                        entity.resetClimbingDirection();
                    } else {
                        continueInDirection = entity.getClimbingDirection();
                    }
                }

                boolean ableToMove;
                Direction attempted;
                if (continueInDirection != null) {
                    attempted = continueInDirection;
                    ableToMove = this.tryFindNextMoveStep(entity, continueInDirection);
                } else {
                    Vec3 pos = entity.position();
                    Vec3 delta = targetPos.subtract(pos);
                    double minDistance = target.distance();
                    double minDistanceSq = Mth.square(minDistance);
                    boolean layered = target.type() == Target.Type.POSITION;
                    double settle = LivingBlock.settleRadius(target);
                    if (delta.horizontalDistanceSqr() < Mth.square(settle)
                            && (!layered || Math.abs(delta.y) <= minDistance)) {
                        return false;
                    }

                    BlockPos targetBlockPos = BlockPos.containing(targetPos);
                    Vec3 roundedDelta = targetBlockPos.getBottomCenter().subtract(pos);
                    if (roundedDelta.horizontalDistanceSqr() < minDistanceSq
                            && (!layered || Math.abs(delta.y) <= minDistance)) {
                        return this.settleApproach(entity, data, delta, settle);
                    }

                    attempted = Direction.getNearest(delta.x, 0.0, delta.z);
                    ableToMove = this.tryFindNextMoveStep(entity, attempted);
                    if (!ableToMove && attempted.getAxis().isHorizontal()) {
                        Direction nearer = attempted.getClockWise();
                        Direction farther = attempted.getCounterClockWise();
                        if (nearer.getStepX() * delta.x + nearer.getStepZ() * delta.z
                                < farther.getStepX() * delta.x + farther.getStepZ() * delta.z) {
                            Direction swap = nearer;
                            nearer = farther;
                            farther = swap;
                        }
                        for (Direction detour : List.of(nearer, farther)) {
                            if (this.tryFindNextMoveStep(entity, detour)) {
                                LOGGER.debug("[livingblock] detour id={} blocked={} took={} tries={} dist={}",
                                        entity.getId(), attempted, detour, data.failedMoveAttempts,
                                        String.format("%.2f", Math.sqrt(delta.horizontalDistanceSqr())));
                                attempted = detour;
                                ableToMove = true;
                                break;
                            }
                        }
                    }
                }

                if (!ableToMove) {
                    if (data.failedMoveAttempts >= BLOCKED_UNTIL_NUDGE && entity.onGround()
                            && !entity.isClimbing()) {
                        entity.reportBlocked();
                    }
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

            data.moveTicks = Math.max(0, data.moveTicks - 1);
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
        data.climbLastY = Double.NEGATIVE_INFINITY;
        data.climbStallTicks = 0;
        data.settleBest = Double.MAX_VALUE;
        data.settleStallTicks = 0;
        data.settleWaitTicks = 0;
        entity.clearRollBlocked();
        entity.resetMaxUpStep();
        entity.resetClimbingDirection();
        if (wasClimbing || entity.onGround()) {
            MovementStrategy.resetVelocity(entity, wasClimbing);
        }
    }

    public void advanceClimbPivot(final LivingBlock entity) {
        if (!entity.hasActiveClimbPivot() || !entity.isClimbing()) {
            return;
        }
        this.finishClimbPivot(entity, entity.advanceClimbPivot(entity.getClimbingDirection()));
    }

    private static void finishClimbPivot(final LivingBlock entity,
                                         final LivingBlock.ClimbPivotAdvance advance) {
        if (advance != LivingBlock.ClimbPivotAdvance.BLOCKED
                && advance != LivingBlock.ClimbPivotAdvance.RELEASED
                && advance != LivingBlock.ClimbPivotAdvance.COMPLETE) {
            return;
        }
        RollingMovement.Data data = (RollingMovement.Data)entity.getMovementData();
        data.movingTo = null;
        data.moveTicks = 0;
        if (advance.chargesMover()) {
            data.failedMoveAttempts++;
            Direction refused = entity.getClimbingDirection();
            boolean repeat = climbRefusalHolds(refused, entity.tickCount, entity.position(),
                    data.climbRefusedDirection, data.climbRefusedTick, data.climbRefusedPos);
            data.climbRefusalStreak = repeat ? data.climbRefusalStreak + 1 : 1;
            data.climbRefusedDirection = refused;
            data.climbRefusedTick = entity.tickCount;
            data.climbRefusedPos = entity.position();
            if (data.climbRefusalStreak >= CLIMB_REFUSAL_STREAK) {
                LOGGER.debug("[livingblock] climbrefusal id={} dir={} pos={}",
                        entity.getId(), refused,
                        String.format("%.3f,%.3f,%.3f", entity.getX(), entity.getY(), entity.getZ()));
                data.climbRefusalStreak = 0;
                entity.escalateBlockedClimb();
            }
            Vec3 velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, Math.min(velocity.y, 0.0), 0.0);
        } else if (advance == LivingBlock.ClimbPivotAdvance.RELEASED) {
            Vec3 released = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, Math.min(released.y, 0.0), 0.0);
        } else {
            data.failedMoveAttempts = 0;
            if (entity.justReleasedQuarter()) {
                data.moveTicks = QUARTER_SETTLE_TICKS;
            }
        }
        entity.resetMaxUpStep();
        entity.resetClimbingDirection();
    }

    @Override
    public Vec3 adjustStepUpMovement(final LivingBlock entity, final Vec3 movement) {
        RollingMovement.Data data = this.getData(entity);
        if (data.movingTo == null || data.movingTo.type() != RollingMovement.MoveType.STEP_UP) {
            return Vec3.ZERO;
        }
        Direction stepUpDirection = data.movingTo.direction();
        double amount = stepUpDirection.getAxis().choose(movement.x, movement.y, movement.z);
        return Mth.sign(amount) != stepUpDirection.getAxisDirection().getStep()
                ? Vec3.ZERO
                : new Vec3(stepUpDirection.getStepX() * Math.abs(amount), 0.0,
                        stepUpDirection.getStepZ() * Math.abs(amount));
    }

    public static boolean canRollTowards(final LivingBlock entity, final Direction direction) {
        if (entity.isClimbing() || !entity.onGround()) {
            return false;
        }
        Vec3 minimumMove = new Vec3(direction.getStepX() * 0.1, direction.getStepY() * 0.1, direction.getStepZ() * 0.1);
        AABB nextPosBounds = entity.getBoundingBox().expandTowards(minimumMove).deflate(1.0E-6);
        if (entity.spaceFree(nextPosBounds)) {
            return true;
        }
        return !Double.isInfinite(
                requiredRise(entity, nextPosBounds, entity.getBaseShapeBounds().getYsize()));
    }

    private boolean tryFindNextMoveStep(final LivingBlock entity, final Direction direction) {
        Vec3 minimumMove = new Vec3(direction.getStepX() * 0.1, direction.getStepY() * 0.1, direction.getStepZ() * 0.1);
        AABB nextPosBounds = entity.getBoundingBox().expandTowards(minimumMove).deflate(1.0E-6);

        Vec3 centre = entity.getBoundingBox().getCenter();
        BlockPos blockPos = BlockPos.containing(centre.x, entity.getY() + BOUNDARY_EPSILON, centre.z);
        if (entity.tickCount % 40 == 0 && !entity.level().isClientSide()
                && blockPos.getY() != BlockPos.containing(centre).getY()) {
            entity.climbWhy(direction, "blockrefdiff", "feet=" + blockPos.getY()
                    + " centre=" + BlockPos.containing(centre).getY());
        }
        BlockPos nextBlockPos = blockPos.relative(direction);
        boolean isClimbing = entity.isClimbing();

        entity.descentRoute(DESCENT_ENABLED && !isClimbing && entity.onGround()
                        ? "ask" : "skip", this.getData(entity).moveTicks, DESCENT_ENABLED,
                isClimbing, direction, this.getData(entity).failedMoveAttempts,
                this.getClass().getSimpleName());
        if (DESCENT_ENABLED && !isClimbing && entity.onGround() && entity.canBeginDescent(direction)) {
            logRoute(entity, this.getData(entity), "descent", 0.0);
            this.setMoveStep(entity, RollingMovement.MoveType.CLIMB, nextBlockPos, direction, 0.0F);
            return true;
        }
        boolean free = entity.spaceFree(nextPosBounds)
                && !entity.livingBlockInWay(nextPosBounds.deflate(BODY_IN_WAY_SLOP));
        boolean arcClear = free && entity.toppleArcClearTowards(direction);
        if (free && arcClear) {
            if (isClimbing) {
                logRoute(entity, this.getData(entity), "left", 0.0);
                entity.resetClimbingDirection();
            }
            if (entity.tickCount % STEP_LOG_INTERVAL == 0) {
                List<LivingBlock> hidden = entity.level().getEntitiesOfClass(LivingBlock.class, nextPosBounds,
                        other -> other != entity && other.isAlive());
                if (!hidden.isEmpty()) {
                    LOGGER.debug("[livingblock] step id={} hidden={} maxUpStep={} feet={}",
                            entity.getId(), hidden.size(), entity.maxUpStep(),
                            String.format("%.2f", entity.getBoundingBox().minY));
                }
            }
            this.setMoveStep(entity, RollingMovement.MoveType.STEP_UP, nextBlockPos, direction, 0.0F);
            return true;
        } else {
            RollingMovement.Data data = this.getData(entity);
            if (!this.isAnyEntityStandingOn(entity)) {
                AABB onTopBounds = nextPosBounds.move(0.0, 1.0, 0.0);
                boolean blockCollision = !entity.level().noBlockCollision(entity, onTopBounds);
                boolean entityCollision = !entity.level().getEntityCollisions(entity, onTopBounds).isEmpty();

                if (entityCollision) {
                    entity.climbWhy(direction, "entityhit", "above=" + blockCollision);
                }
                double lift = LivingBlock.LOW_STEP_HEIGHT + LivingBlock.LOW_STEP_SLACK;
                LivingBlockStep.Verdict step = entity.lowStepVerdict(direction);
                boolean bodyInWay = step.ok()
                        && entity.livingBlockInWay(nextPosBounds.move(0.0, lift, 0.0)
                                .deflate(BODY_IN_WAY_SLOP));
                double rise = step.ok() && !bodyInWay
                        ? requiredRise(entity, tiltProbe(nextPosBounds), lift) : Double.NaN;
                boolean lowStep = !entityCollision && !isClimbing && entity.onGround()
                        && step.ok() && !bodyInWay && !Double.isInfinite(rise);
                entity.stepWhy(direction, String.format(
                        "free=%b arc=%b step=%s rise=%.4f body=%b above=%b ent=%b picks=%s",
                        free, arcClear, step.reason(), rise, bodyInWay, blockCollision,
                        entityCollision, lowStep ? "lowstep" : "next"));
                if (lowStep) {
                    if (entity.beginStepQuarter(direction)) {
                        logRoute(entity, data, "stepquarter", LivingBlock.LOW_STEP_HEIGHT);
                        this.setMoveStep(entity, RollingMovement.MoveType.CLIMB, nextBlockPos,
                                direction, 0.0F);
                        return true;
                    }
                    logRoute(entity, data, "lowstep", LivingBlock.LOW_STEP_HEIGHT);
                    this.setMoveStep(entity, RollingMovement.MoveType.STEP_UP, nextBlockPos, direction,
                            LivingBlock.LOW_STEP_HEIGHT);
                    return true;
                }
                Direction climbDirection = !entityCollision && entity.usesClimbPivot()
                        ? entity.climbPivotDirection(direction) : null;
                if (climbDirection != null && refusedFromHere(entity, data, climbDirection)) {
                    entity.climbWhy(direction, "refusedlately", "");
                    climbDirection = null;
                }
                boolean wallCapturable = climbDirection != null;
                if (!wallCapturable && !entityCollision) {
                    entity.climbWhy(direction, "nowallcatch",
                            "blockabove=" + blockCollision + " ground=" + entity.onGround());
                }
                if (LivingBlockPivot.preferWallClimb(entity.usesClimbPivot(), isClimbing,
                        !entityCollision, wallCapturable)) {
                    logRoute(entity, data, "capture", 0.0);
                    this.setMoveStep(entity, RollingMovement.MoveType.CLIMB,
                            nextBlockPos.above(), climbDirection, 0.0F);
                    return true;
                }

                if (!blockCollision && !entityCollision && entity.onGround()) {
                    logRoute(entity, data, "step", 1.0);
                    this.setMoveStep(entity, RollingMovement.MoveType.STEP_UP, nextBlockPos.above(), direction, 1.0F);
                    return true;
                }

                if (entityCollision && !isClimbing) {
                    data.failedMoveAttempts++;
                } else {
                    AABB directlyAboveBounds = entity.getBoundingBox()
                            .deflate(OBB_INFLATION_SLACK, 1.0E-6, OBB_INFLATION_SLACK)
                            .move(0.0, 2.0E-6, 0.0);
                    if (isClimbing && entity.lowStepAhead(direction)) {
                        logRoute(entity, data, "stepnotwall", 0.0);
                        entity.resetClimbingDirection();
                        data.movingTo = null;
                        entity.resetMaxUpStep();
                        return false;
                    }
                    if (entity.level().noBlockCollision(entity, directlyAboveBounds)) {
                        logRoute(entity, data, "climb", 0.0);
                        entity.climbWhy(direction, "climbstuck",
                                "target=" + nextBlockPos.above().getY());
                        this.setMoveStep(entity, RollingMovement.MoveType.CLIMB, nextBlockPos.above(), direction, 0.0F);
                        return true;
                    }
                    logRoute(entity, data, "stuck", 0.0);
                    entity.climbWhy(direction, "ceilingtaken", "");
                    data.failedMoveAttempts = 10;
                }
            } else {
                if (isClimbing) {
                    this.setMoveStep(entity, RollingMovement.MoveType.IDLE_CLING, nextBlockPos, direction, 0.0F);
                    return true;
                }
                entity.resetClimbingDirection();
            }

            data.movingTo = null;
            entity.resetMaxUpStep();
            return false;
        }
    }

    private static final boolean DESCENT_ENABLED = true;
    private static final int CLIMB_REFUSAL_TICKS = 20;
    private static final int CLIMB_REFUSAL_STREAK = 2;
    private static final double CLIMB_REFUSAL_MOVED = 0.05;

    private static boolean refusedFromHere(final LivingBlock entity, final RollingMovement.Data data,
                                           final Direction direction) {
        return climbRefusalHolds(direction, entity.tickCount, entity.position(),
                data.climbRefusedDirection, data.climbRefusedTick, data.climbRefusedPos);
    }

    public static boolean climbRefusalHolds(final Direction direction, final int tick, final Vec3 pos,
                                            final @Nullable Direction refusedDirection,
                                            final int refusedTick, final Vec3 refusedPos) {
        if (refusedDirection != direction || tick - refusedTick > CLIMB_REFUSAL_TICKS) {
            return false;
        }
        return pos.distanceToSqr(refusedPos) <= CLIMB_REFUSAL_MOVED * CLIMB_REFUSAL_MOVED;
    }

    private void setMoveStep(final LivingBlock entity, final RollingMovement.MoveType moveType, final BlockPos movingToBlock, final Direction direction, final float rise) {
        this.setMoveStep(entity, moveType, movingToBlock.getBottomCenter(), direction, rise);
    }

    private void setMoveStep(final LivingBlock entity, final RollingMovement.MoveType moveType, final Vec3 movingToPos, final Direction direction, final float rise) {
        RollingMovement.Data data = this.getData(entity);
        data.movingTo = new RollingMovement.MoveStep(moveType, movingToPos, direction, rise);
        data.movingTo.setOn(entity);
        data.failedMoveAttempts = 0;
    }

    private static void logRoute(final LivingBlock entity, final RollingMovement.Data data, final String route, final double rise) {
        entity.noteClimbRoute(route);
        if (route.equals(data.lastRoute)) {
            return;
        }
        data.lastRoute = route;
        LOGGER.debug("[livingblock] route id={} type={} bodyheight={} rise={} step={}",
                entity.getId(), route,
                String.format("%.4f", entity.getBaseShapeBounds().getYsize()),
                Double.isInfinite(rise) ? "inf" : String.format("%.4f", rise),
                String.format("%.2f", entity.maxUpStep()));
    }

    private static boolean blockedByMover(final LivingBlock entity, final AABB destination) {
        for (LivingBlock other : entity.level().getEntitiesOfClass(LivingBlock.class,
                destination.inflate(SETTLE_SLOP), candidate -> candidate != entity
                        && candidate.isAlive() && !candidate.isAnchored())) {
            return true;
        }
        return false;
    }

    private static boolean supported(final LivingBlock entity, final AABB destination) {
        AABB foot = new AABB(destination.minX, destination.minY - SETTLE_SUPPORT, destination.minZ,
                destination.maxX, destination.minY, destination.maxZ);
        return !entity.level().noBlockCollision(entity, foot) || entity.livingBlockInWay(foot);
    }

    private boolean settleApproach(final LivingBlock entity, final RollingMovement.Data data,
                                   final Vec3 delta, final double radius) {
        if (entity.level().isClientSide() || !entity.onGround() || entity.isClimbing()
                || entity.hasActiveClimbPivot()) {
            return false;
        }
        double distance = Math.sqrt(delta.horizontalDistanceSqr());
        if (distance > SETTLE_REACH) {
            data.settleBest = Double.MAX_VALUE;
            data.settleStallTicks = 0;
            return false;
        }
        Vec3 heading = new Vec3(delta.x, 0.0, delta.z).normalize();
        Vec3 full = heading.scale(Math.min(distance, SETTLE_SPEED));
        AABB destination = entity.getBoundingBox().move(full).deflate(1.0E-6);
        Vec3 drive = Vec3.ZERO;
        boolean clear = false;
        LivingBlockCollisionHandler.ClimbPoseFailure hit = null;
        boolean held = false;
        double reach = full.horizontalDistance();
        for (int probe = 0; probe < SETTLE_PROBES && reach >= SETTLE_MIN_STEP; probe++, reach *= 0.5) {
            Vec3 candidate = heading.scale(reach);
            hit = LivingBlockCollisionHandler.climbPoseFailure(entity, entity.getRotation(),
                    entity.position().add(candidate), SETTLE_SLOP);
            held = supported(entity, entity.getBoundingBox().move(candidate).deflate(1.0E-6));
            if (hit == null && held) {
                drive = candidate;
                clear = true;
                break;
            }
        }
        String why = clear ? "free"
                : hit != null
                        ? hit.kind() + ":" + String.format("%.4f", hit.depth()) + ":" + hit.blocker()
                        : "nosupport";

        if (clear && distance < data.settleBest - SETTLE_PROGRESS) {
            data.settleBest = distance;
            data.settleStallTicks = 0;
            data.settleWaitTicks = 0;
        } else if (!clear && blockedByMover(entity, destination)
                && ++data.settleWaitTicks < SETTLE_WAIT_TICKS) {
            data.settleStallTicks = 0;
        } else if (++data.settleStallTicks >= SETTLE_STUCK_TICKS) {
            LOGGER.debug("[livingblock] settle id={} phase=giveup why={} dist={} radius={} tilt={} pos={}",
                    entity.getId(), why, String.format("%.4f", distance),
                    String.format("%.3f", radius), String.format("%.1f", entity.tiltDegrees()),
                    String.format("%.3f,%.3f,%.3f", entity.getX(), entity.getY(), entity.getZ()));
            data.settleBest = Double.MAX_VALUE;
            data.settleStallTicks = 0;
            data.settleWaitTicks = 0;
            return false;
        }
        if (clear) {
            entity.setDeltaMovement(drive.x, entity.getDeltaMovement().y, drive.z);
        }
        if (entity.tickCount % SETTLE_LOG_INTERVAL == 0) {
            LOGGER.debug("[livingblock] settle id={} phase={} why={} dist={} step={} radius={} wait={} tilt={}",
                    entity.getId(), clear ? "moving" : "blocked", why,
                    String.format("%.4f", distance),
                    String.format("%.4f", drive.horizontalDistance()),
                    String.format("%.3f", radius), data.settleStallTicks + data.settleWaitTicks,
                    String.format("%.1f", entity.tiltDegrees()));
        }
        return true;
    }

    private static int horizontalDistanceTo(final LivingBlock entity, final Vec3 target) {
        BlockPos here = entity.blockPosition();
        BlockPos there = BlockPos.containing(target);
        return Math.abs(here.getX() - there.getX()) + Math.abs(here.getZ() - there.getZ());
    }

    private boolean checkIsMovementDisabled(final LivingBlock entity) {
        RollingMovement.Data data = this.getData(entity);
        if (!entity.onGround() && !entity.isClimbing()) {
            if (data.moveTicks > 0 && lowStepDrive(data)) {
                return false;
            }
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
            if (data.movingTo != null && horizontalDistanceTo(entity, data.movingTo.position) > 1) {
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
        public String lastRoute = "";
        public double climbLastY = Double.NEGATIVE_INFINITY;
        public int climbStallTicks;
        public @Nullable Direction climbRefusedDirection;
        public int climbRefusedTick = Integer.MIN_VALUE;
        public Vec3 climbRefusedPos = Vec3.ZERO;
        public int climbRefusalStreak;
        public double settleBest = Double.MAX_VALUE;
        public int settleStallTicks;
        public int settleWaitTicks;

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

    public record MoveStep(RollingMovement.MoveType type, Vec3 position, Direction direction, float rise) {

        public void write(FriendlyByteBuf buf) {
            buf.writeEnum(this.type);
            buf.writeDouble(this.position.x);
            buf.writeDouble(this.position.y);
            buf.writeDouble(this.position.z);
            buf.writeEnum(this.direction);
            buf.writeFloat(this.rise);
        }

        public static RollingMovement.MoveStep read(FriendlyByteBuf buf) {
            RollingMovement.MoveType type = buf.readEnum(RollingMovement.MoveType.class);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            Direction dir = buf.readEnum(Direction.class);
            float rise = buf.readFloat();
            return new RollingMovement.MoveStep(type, new Vec3(x, y, z), dir, rise);
        }

        public void setOn(final LivingBlock entity) {
            if (this.type.isAttachedVertically()) {
                entity.setClimbingDirection(this.direction);
                entity.setMaxUpStep(0.0F);
                entity.setDeltaMovement(0.0, entity.getDeltaMovement().y, 0.0);
            } else {
                entity.resetClimbingDirection();
                if (this.type == RollingMovement.MoveType.STEP_UP) {
                    entity.setMaxUpStep(this.rise > 0.0F && this.rise <= LivingBlock.LOW_STEP_HEIGHT
                            ? this.rise + LivingBlock.LOW_STEP_SLACK
                            : entity.bodyAheadAtStep(this.direction)
                                    ? LivingBlock.LOW_STEP_HEIGHT + LivingBlock.LOW_STEP_SLACK
                                    : 1.0F);
                } else {
                    entity.resetMaxUpStep();
                }
            }
        }

        public void applyMovement(final LivingBlock entity, final double scale) {
            if (this.type == RollingMovement.MoveType.CLIMB && entity.usesClimbPivot()) {
                if (!entity.hasActiveClimbPivot()) {
                    RollingMovement.finishClimbPivot(entity,
                            entity.advanceClimbPivot(this.direction));
                } else if (!entity.isClimbing()
                        && climbStalled(entity, (RollingMovement.Data)entity.getMovementData())) {
                    entity.releaseClimbPivot();
                    RollingMovement.finishClimbPivot(entity,
                            LivingBlock.ClimbPivotAdvance.BLOCKED);
                }
                return;
            }
            Vec3 predictedPos = entity.position().add(entity.getDeltaMovement());
            Vec3 delta = this.position.subtract(predictedPos);
            if (this.type.isAttachedVertically()) {
                AABB aabb = entity.getBoundingBox().deflate(1.0E-6).move(this.direction.getStepX() * CLIMB_REACH, this.direction.getStepY() * CLIMB_REACH, this.direction.getStepZ() * CLIMB_REACH);
                boolean touching = !entity.level().noCollision(entity, aabb);
                if (entity.tickCount % CLIMB_LOG_INTERVAL == 0 && !entity.level().isClientSide()) {
                    LOGGER.debug("[livingblock] climbdrive id={} type={} dir={} touching={} targetY={} deltaY={} scale={} velY={}",
                            entity.getId(), this.type, this.direction, touching,
                            String.format("%.3f", this.position.y), String.format("%.3f", delta.y),
                            String.format("%.3f", scale), String.format("%.4f", entity.getDeltaMovement().y));
                }
                if (touching) {
                    Vec3 stickingForce = new Vec3(this.direction.getStepX() * 0.1, this.direction.getStepY() * 0.1, this.direction.getStepZ() * 0.1);
                    if (this.type == RollingMovement.MoveType.CLIMB) {
                        double rise = Mth.clamp(delta.y * scale, CLIMB_RISE_MIN, CLIMB_RISE_MAX);
                        entity.addDeltaMovement(stickingForce.add(0.0, rise, 0.0));
                    } else {
                        entity.addDeltaMovement(stickingForce);
                    }

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
