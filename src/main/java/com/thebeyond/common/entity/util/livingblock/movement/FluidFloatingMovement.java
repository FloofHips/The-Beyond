package com.thebeyond.common.entity.util.livingblock.movement;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class FluidFloatingMovement extends RollingMovement {

    @Override
    public boolean moveTowardsTarget(LivingBlock entity, final Target target, final Vec3 targetPos) {
        if (!entity.isInLiquid()) {
            return super.moveTowardsTarget(entity, target, targetPos);
        } else {
            moveInFluid(entity, target, targetPos);
            return true;
        }
    }

    public static void moveInFluid(final LivingBlock entity, final Target target, final Vec3 targetPos) {
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(movement.x, movement.y + 0.1, movement.z);
        Vec3 position = entity.position();
        Vec3 deltaFull = targetPos.subtract(position);
        Vec3 delta = new Vec3(deltaFull.x, 0, deltaFull.z);
        if (delta.lengthSqr() > Mth.square(target.distance())) {
            Vec3 directionFull = delta.normalize();
            Vec3 direction = new Vec3(directionFull.x, 0, directionFull.z);
            Vec3 lookAhead = position.add(direction.add(0.0, -0.1, 0.0));
            BlockPos lookaheadBlockPos = BlockPos.containing(lookAhead);
            Level level = entity.level();
            BlockState lookaheadBlockState = level.getBlockState(lookaheadBlockPos);
            BlockState aboveBlockState = level.getBlockState(lookaheadBlockPos.above());
            if (lookaheadBlockState.isFaceSturdy(level, lookaheadBlockPos, Direction.UP)
                    && aboveBlockState.getCollisionShape(level, lookaheadBlockPos.above()).isEmpty()) {
                entity.addDeltaMovement(new Vec3(direction.x * 0.1, 0.25, direction.z * 0.1));
            } else {
                entity.addDeltaMovement(new Vec3(direction.x * 0.16, 0.0, direction.z * 0.16));
            }
        }
    }
}
