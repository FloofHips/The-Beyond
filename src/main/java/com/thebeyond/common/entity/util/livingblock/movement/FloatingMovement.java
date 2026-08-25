package com.thebeyond.common.entity.util.livingblock.movement;

import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class FloatingMovement implements MovementStrategy<FloatingMovement.Data> {
    private static final double SPEED = 0.06;

    public FloatingMovement.Data initData() {
        return new FloatingMovement.Data();
    }

    @Override
    public boolean moveTowardsTarget(LivingBlock entity, final Target target, final Vec3 targetPos) {
        Vec3 pos = entity.position();
        Vec3 delta = targetPos.add(0.0, 0.5, 0.0).subtract(pos);
        FloatingMovement.Data data = this.getData(entity);
        boolean stuck = data.wantedHorizontalSpeed > 0.001 && entity.getDeltaMovement().horizontalDistance() < 0.001;
        if (delta.horizontalDistanceSqr() > Mth.square(target.distance())) {
            Vec3 dir = delta.normalize();
            double speed = Math.min(delta.length(), 0.06);

            entity.addDeltaMovement(dir.scale(speed).add(0.0, entity.getGravity(), 0.0));
            data.wantedHorizontalSpeed = entity.getDeltaMovement().horizontalDistance();
            if (stuck) {
                entity.addDeltaMovement(new Vec3(0.0, 0.06, 0.0));
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void resetMovement(final LivingBlock entity) {
        MovementStrategy.resetVelocity(entity, false);
    }

    public static class Data implements MovementData {

        public double wantedHorizontalSpeed;

        public Data(final double wantedHorizontalSpeed) {
            this.wantedHorizontalSpeed = wantedHorizontalSpeed;
        }

        public Data() {
            this(0.0);
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(this.wantedHorizontalSpeed);
        }

        public static Data read(FriendlyByteBuf buf) {
            return new Data(buf.readDouble());
        }
    }
}
