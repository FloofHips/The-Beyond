package com.thebeyond.compat.sable;

import com.thebeyond.common.entity.util.livingblock.LivingBlockFrames;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;

/** Sable sub-levels as living block frames. Loaded only after {@code isLoaded("sable")}. */
final class SableLivingBlockFrames implements LivingBlockFrames.Hooks {
    @Override
    public @Nullable Object frameAt(Level level, Vec3 pos) {
        return Sable.HELPER.getContaining(level, pos);
    }

    @Override
    public @Nullable Object standingOn(Entity entity) {
        return Sable.HELPER.getTrackingSubLevel(entity);
    }

    @Override
    public boolean alive(Object frame) {
        return !((SubLevel) frame).isRemoved();
    }

    @Override
    public Vec3 toWorld(Object frame, Vec3 pos) {
        return ((SubLevel) frame).logicalPose().transformPosition(pos);
    }

    @Override
    public Vec3 toFrame(Object frame, Vec3 pos) {
        return ((SubLevel) frame).logicalPose().transformPositionInverse(pos);
    }

    @Override
    public Vec3 dirToWorld(Object frame, Vec3 dir) {
        return ((SubLevel) frame).logicalPose().transformNormal(dir);
    }

    @Override
    public Vec3 dirToFrame(Object frame, Vec3 dir) {
        return ((SubLevel) frame).logicalPose().transformNormalInverse(dir);
    }

    @Override
    public Quaternionf orientation(Object frame) {
        Quaterniondc q = ((SubLevel) frame).logicalPose().orientation();
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }

    @Override
    public Vec3 pointVelocity(Level level, Object frame, Vec3 framePos) {
        // Sable answers in blocks per second
        return Sable.HELPER.getVelocity(level, (SubLevel) frame, framePos).scale(0.05);
    }

    @Override
    public @Nullable AABB blockBounds(Object frame) {
        BoundingBox3ic b = ((SubLevel) frame).getPlot().getBoundingBox();
        if (b == null || b.maxX() < b.minX()) {
            return null;
        }
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }
}
