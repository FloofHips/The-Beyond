package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

public final class LivingBlockCollisionShapes {

    private static final double PROBE_STEP_LIMIT = 0.6;
    private static final double STEP_FLOOR_EPSILON = 1.0E-7;
    private static final double QUERY_SLOP = 1.0E-7;

    public record Placement(List<AABB> boxes, List<VoxelShape> shapes, List<OrientedBox> obbs) {
    }

    private record Memo(int index, double x, double y, double z, Placement placement) {
    }

    private final List<AABB> centredBoxes;
    private final AABB centredBounds;
    private final List<AABB>[] localByOrientation;

    private Memo memo;

    public LivingBlockCollisionShapes(final List<AABB> boxes, final AABB bounds) {
        this(boxes, bounds,
                Mth.lerp(0.5, bounds.minX, bounds.maxX),
                Mth.lerp(0.5, bounds.minY, bounds.maxY),
                Mth.lerp(0.5, bounds.minZ, bounds.maxZ));
    }

    @SuppressWarnings("unchecked")
    public LivingBlockCollisionShapes(final List<AABB> boxes, final AABB bounds,
                                      final double centerX, final double centerY, final double centerZ) {
        List<AABB> centred = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            centred.add(box.move(-centerX, -centerY, -centerZ));
        }
        this.centredBoxes = List.copyOf(centred);
        this.centredBounds = bounds.move(-centerX, -centerY, -centerZ);
        this.localByOrientation = new List[LivingBlockOrientation.COUNT];
    }

    public Placement world(final LivingBlockOrientation orientation, final Vec3 position) {
        int index = orientation.index();
        Memo cached = this.memo;
        if (cached != null && cached.index == index
                && cached.x == position.x && cached.y == position.y && cached.z == position.z) {
            return cached.placement;
        }

        double lift = position.y - orientation.transform(this.centredBounds).minY;
        List<AABB> local = this.local(index, orientation);
        int size = local.size();
        List<AABB> boxes = new ArrayList<>(size);
        List<VoxelShape> shapes = new ArrayList<>(size);
        List<OrientedBox> obbs = new ArrayList<>(size);
        for (AABB box : local) {
            AABB moved = box.move(position.x, lift, position.z);
            VoxelShape shape = Shapes.create(moved);
            if (!shape.isEmpty()) {
                boxes.add(moved);
                shapes.add(shape);
                obbs.add(new OrientedBox(moved));
            }
        }

        Placement placement = new Placement(List.copyOf(boxes), List.copyOf(shapes), List.copyOf(obbs));
        this.memo = new Memo(index, position.x, position.y, position.z, placement);
        return placement;
    }

    private List<AABB> local(final int index, final LivingBlockOrientation orientation) {
        List<AABB> cached = this.localByOrientation[index];
        if (cached != null) {
            return cached;
        }
        List<AABB> rotated = new ArrayList<>(this.centredBoxes.size());
        for (AABB box : this.centredBoxes) {
            rotated.add(orientation.transform(box));
        }
        List<AABB> result = List.copyOf(rotated);
        this.localByOrientation[index] = result;
        return result;
    }

    @Nullable
    public static List<AABB> queryEnvelope(@Nullable final Entity asker, final AABB query) {
        if (!(asker instanceof LivingBlock block)) {
            return null;
        }
        Placement placement = preciseGeometry(block);
        if (placement == null) {
            return null;
        }

        AABB hull = block.getBoundingBox();
        double growMinX = hull.minX - query.minX;
        double growMinY = hull.minY - query.minY;
        double growMinZ = hull.minZ - query.minZ;
        double growMaxX = query.maxX - hull.maxX;
        double growMaxY = query.maxY - hull.maxY;
        double growMaxZ = query.maxZ - hull.maxZ;

        List<AABB> boxes = placement.boxes();
        List<AABB> envelope = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            envelope.add(new AABB(
                    box.minX - growMinX, box.minY - growMinY, box.minZ - growMinZ,
                    box.maxX + growMaxX, box.maxY + growMaxY, box.maxZ + growMaxZ));
        }
        return envelope;
    }

    public static double bodyTop(final Placement placement) {
        double top = Double.NEGATIVE_INFINITY;
        for (AABB box : placement.boxes()) {
            top = Math.max(top, box.maxY);
        }
        return top;
    }

    public static boolean blocksStep(@Nullable final Entity asker, final double bodyTop, final AABB box) {
        if (!(asker instanceof LivingBlock block)) {
            return true;
        }
        double feet = block.getBoundingBox().minY;
        if (box.maxY <= feet + STEP_FLOOR_EPSILON) {
            return true;
        }
        double ceiling = feet + Math.min(block.maxUpStep(), PROBE_STEP_LIMIT);
        return box.maxY > ceiling || bodyTop > ceiling;
    }

    public static boolean reaches(@Nullable final List<AABB> envelope, final AABB box) {
        if (envelope == null) {
            return true;
        }
        for (AABB candidate : envelope) {
            if (candidate.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    public static List<VoxelShape> entityCollisions(final Level level,
                                                    @Nullable final Entity entity, final AABB collisionBox) {
        if (collisionBox.getSize() < QUERY_SLOP) {
            return List.of();
        }

        Predicate<Entity> predicate = entity == null
                ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        AABB probe = collisionBox.inflate(QUERY_SLOP);
        List<Entity> list = level.getEntities(entity, probe, predicate);
        if (list.isEmpty()) {
            return List.of();
        }

        if (entity instanceof LivingBlock asker && !asker.usesOrientedCollision()) {
            List<VoxelShape> plain = new ArrayList<>(list.size());
            for (Entity other : list) {
                if (LivingBlockCollisionHandler.carrying(entity, other)) {
                    continue;
                }
                Placement precise = preciseGeometry(other);
                for (AABB piece : precise == null ? List.of(other.getBoundingBox()) : precise.boxes()) {
                    plain.add(Shapes.create(piece));
                }
            }
            return List.copyOf(plain);
        }

        List<AABB> askerBoxes = null;
        boolean askerResolved = false;
        AABB stepFootprint = entity == null ? null : stepFootprint(entity, collisionBox);

        List<VoxelShape> built = new ArrayList<>(list.size());
        for (Entity other : list) {
            if (LivingBlockCollisionHandler.carrying(entity, other)) {
                continue;
            }
            Placement precise = preciseGeometry(other);
            if (precise == null) {
                built.add(Shapes.create(other.getBoundingBox()));
                continue;
            }
            if (!askerResolved) {
                askerResolved = true;
                askerBoxes = queryEnvelope(entity, collisionBox);
            }
            List<AABB> boxes = precise.boxes();
            List<VoxelShape> shapes = precise.shapes();
            double bodyTop = bodyTop(precise);
            for (int i = 0; i < boxes.size(); i++) {
                AABB box = boxes.get(i);
                if (box.intersects(probe) && reaches(askerBoxes, box)
                        && blocksStep(entity, bodyTop, box)) {
                    VoxelShape shape = shapes.get(i);
                    if (stepFootprint != null && shape instanceof OrientedBoxShape oriented) {
                        shape = oriented.forStepFootprint(stepFootprint);
                    }
                    built.add(shape);
                }
            }
        }
        return List.copyOf(built);
    }

    public static AABB stepFootprint(final Entity entity, final AABB query) {
        AABB current = entity.getBoundingBox();
        double positiveX = Math.max(0.0, query.maxX - current.maxX);
        double negativeX = Math.max(0.0, current.minX - query.minX);
        double positiveZ = Math.max(0.0, query.maxZ - current.maxZ);
        double negativeZ = Math.max(0.0, current.minZ - query.minZ);
        double dx = positiveX > negativeX ? positiveX : negativeX > positiveX ? -negativeX : 0.0;
        double dz = positiveZ > negativeZ ? positiveZ : negativeZ > positiveZ ? -negativeZ : 0.0;
        return current.move(dx, 0.0, dz);
    }

    @Nullable
    public static Placement preciseGeometry(final Entity entity) {
        if (!(entity instanceof LivingBlock block)) {
            return null;
        }
        Placement placement = block.getCollisionGeometry();
        return placement == null || placement.boxes().isEmpty() ? null : placement;
    }
}
