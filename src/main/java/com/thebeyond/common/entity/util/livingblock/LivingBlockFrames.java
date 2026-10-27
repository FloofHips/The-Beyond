package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/** Moving frames a living block can live in (Sable sub-levels), with the transforms between a frame and the open world. */
public final class LivingBlockFrames {
    public interface Hooks {
        /** The sub-level whose plot holds this position, or null for the open world. */
        @Nullable Object frameAt(Level level, Vec3 pos);

        @Nullable Object standingOn(Entity entity);

        boolean alive(Object frame);

        Vec3 toWorld(Object frame, Vec3 pos);

        Vec3 toFrame(Object frame, Vec3 pos);

        Vec3 dirToWorld(Object frame, Vec3 dir);

        Vec3 dirToFrame(Object frame, Vec3 dir);

        Quaternionf orientation(Object frame);

        /** Velocity of the frame point at framePos in blocks per tick, in world axes. */
        Vec3 pointVelocity(Level level, Object frame, Vec3 framePos);

        /** Bounds of the frame's blocks in its own coordinates, null when it holds none. */
        @Nullable AABB blockBounds(Object frame);
    }

    /** Terrain that moves outside the level's blocks (Create contraptions), in world space. */
    public interface ExtraTerrain {
        void collect(Level level, AABB box, java.util.List<OrientedBox> out);
    }

    @Nullable
    public static volatile Hooks hooks;

    @Nullable
    public static volatile ExtraTerrain extraTerrain;

    public static java.util.List<OrientedBox> extraTerrain(Level level, AABB box) {
        ExtraTerrain terrain = extraTerrain;
        if (terrain == null) {
            return java.util.List.of();
        }
        java.util.List<OrientedBox> out = new java.util.ArrayList<>();
        terrain.collect(level, box, out);
        return out;
    }

    public static Vec3 toWorld(Level level, Vec3 pos) {
        Hooks h = hooks;
        Object frame = h == null ? null : h.frameAt(level, pos);
        return frame == null ? pos : h.toWorld(frame, pos);
    }

    public static boolean noBlockCollision(Entity entity, AABB box) {
        return entity.level().noBlockCollision(entity, box) && !hitsExtraTerrain(entity.level(), box);
    }

    public static boolean noCollision(Entity entity, AABB box) {
        return entity.level().noCollision(entity, box) && !hitsExtraTerrain(entity.level(), box);
    }

    private static boolean hitsExtraTerrain(Level level, AABB box) {
        if (extraTerrain == null) {
            return false;
        }
        OrientedBox probe = null;
        org.joml.Vector3f axis = null;
        for (OrientedBox part : extraTerrain(level, box)) {
            if (!part.getWorldAABB().intersects(box)) {
                continue;
            }
            if (part.axisAligned()) {
                return true;
            }
            if (probe == null) {
                probe = new OrientedBox(box);
                axis = new org.joml.Vector3f();
            }
            if (OrientedBox.penetration(probe, part, axis) > 0.0) {
                return true;
            }
        }
        return false;
    }

    /** The extra terrain boxes squared with the world axes, the only ones the wall climb can pivot on. */
    public static void addAlignedExtraTerrain(Level level, AABB box, java.util.List<AABB> into) {
        for (OrientedBox part : extraTerrain(level, box)) {
            if (part.axisAligned()) {
                into.add(part.getWorldAABB());
            }
        }
    }

    private LivingBlockFrames() {
    }

    public static @Nullable Object frameOf(Entity entity) {
        Hooks h = hooks;
        return h == null ? null : h.frameAt(entity.level(), entity.position());
    }

    /** A position from any frame expressed in the frame the given entity lives in. */
    public static Vec3 inFrameOf(Entity entity, Vec3 pos) {
        Hooks h = hooks;
        if (h == null) {
            return pos;
        }
        Object from = h.frameAt(entity.level(), pos);
        Object to = h.frameAt(entity.level(), entity.position());
        if (from == to) {
            return pos;
        }
        Vec3 world = from != null ? h.toWorld(from, pos) : pos;
        return to != null ? h.toFrame(to, world) : world;
    }

    public static Vec3 worldPosition(Entity entity) {
        Hooks h = hooks;
        if (h == null) {
            return entity.position();
        }
        Object frame = h.frameAt(entity.level(), entity.position());
        return frame != null ? h.toWorld(frame, entity.position()) : entity.position();
    }

    /** A world-axes direction expressed in the axes of the frame the entity lives in. */
    public static Vec3 dirInFrameOf(Entity entity, Vec3 dir) {
        Hooks h = hooks;
        Object frame = h == null ? null : h.frameAt(entity.level(), entity.position());
        return frame == null ? dir : h.dirToFrame(frame, dir);
    }

    /** Another entity's collision boxes carried from its frame into the asker's, null when both share a frame. */
    public static @Nullable java.util.List<OrientedBox> carriedBoxes(@Nullable Entity asker, Entity other,
                                                                   java.util.List<OrientedBox> boxes) {
        Hooks h = hooks;
        if (h == null) {
            return null;
        }
        Object from = h.frameAt(other.level(), other.position());
        Object to = asker == null ? null : h.frameAt(asker.level(), asker.position());
        if (from == to) {
            return null;
        }
        Quaternionf turn = new Quaternionf();
        if (from != null) {
            turn.set(h.orientation(from));
        }
        if (to != null) {
            turn.premul(h.orientation(to).conjugate());
        }
        java.util.List<OrientedBox> out = new java.util.ArrayList<>(boxes.size());
        for (OrientedBox box : boxes) {
            Vec3 center = new Vec3(box.centerX(), box.centerY(), box.centerZ());
            Vec3 world = from != null ? h.toWorld(from, center) : center;
            out.add(box.carried(to != null ? h.toFrame(to, world) : world, turn));
        }
        return out;
    }

    public static double tiltDegrees(Quaternionf orientation) {
        org.joml.Vector3f up = new org.joml.Vector3f(0.0F, 1.0F, 0.0F).rotate(orientation);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, up.y()))));
    }
}
