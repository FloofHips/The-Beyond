package com.thebeyond.common.entity.util.livingblock.movement;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public interface Target {
    int ANY_FACE = -1;

    Target NONE = new Target() {
        @Override
        public @Nullable Vec3 resolvePosition(final Level level) {
            return null;
        }

        @Override
        public double distance() {
            return 0.0;
        }

        @Override
        public Target.Type type() {
            return Target.Type.NONE;
        }
    };

    @Nullable Vec3 resolvePosition(Level level);
    double distance();
    default int orientation() { return ANY_FACE; }
    default boolean clearWhenNear() { return true; }
    Target.Type type();

    static Target near(final Vec3 position, final double distance) {
        return new Target.PositionTarget(position, distance, ANY_FACE);
    }

    static Target facing(final Vec3 position, final double distance, final int orientation) {
        return new Target.PositionTarget(position, distance, orientation);
    }

    static Target followingEntity(final @Nullable Entity entity, final double distance) {
        return entity == null ? NONE : new Target.EntityTarget(entity.getUUID(), distance, false);
    }

    record EntityTarget(UUID entityUUID, double distance, boolean clearWhenNear) implements Target {
        @Override
        public @Nullable Vec3 resolvePosition(final Level level) {
            if (level instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(this.entityUUID);
                return entity != null ? entity.position() : null;
            }
            return null;
        }

        @Override
        public Target.Type type() {
            return Target.Type.ENTITY;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUUID(this.entityUUID);
            buf.writeDouble(this.distance);
            buf.writeBoolean(this.clearWhenNear);
        }

        public static EntityTarget read(FriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            double distance = buf.readDouble();
            boolean clearWhenNear = buf.readBoolean();
            return new EntityTarget(uuid, distance, clearWhenNear);
        }
    }

    record PositionTarget(Vec3 position, double distance, int orientation) implements Target {
        @Override
        public Vec3 resolvePosition(final Level level) {
            return this.position;
        }

        @Override
        public Target.Type type() {
            return Target.Type.POSITION;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeDouble(this.position.x);
            buf.writeDouble(this.position.y);
            buf.writeDouble(this.position.z);
            buf.writeDouble(this.distance);
            buf.writeInt(this.orientation);
        }

        public static PositionTarget read(FriendlyByteBuf buf) {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double distance = buf.readDouble();
            return new PositionTarget(new Vec3(x, y, z), distance, buf.readInt());
        }
    }

    enum Type {
        NONE,
        POSITION,
        ENTITY;
    }
}
