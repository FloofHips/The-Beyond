package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class LivingBlockCollisionHandler {

    public static Vec3 worldToLocal(Vec3 worldPos, LivingBlock entity, float partialTicks) {
        Vec3 entityPos = entity.getPosition(partialTicks);
        AABB box = entity.getBoundingBox();
        double sizeX = box.getXsize();
        double sizeY = box.getYsize();
        double sizeZ = box.getZsize();

        Direction climbDir = entity.getClimbingDirection();
        Direction.Axis axis = climbDir.getAxis();
        int step = climbDir.getAxisDirection().getStep();

        Quaternionf rot = new Quaternionf();
        entity.getRotation(rot, partialTicks);

        AABBBuilder builder = new AABBBuilder();
        includeRotatedOBBCorners(entity, rot, builder);
        double edgeOffset = builder.edge(climbDir);

        double translateY = 0.0;
        double translateX = 0.0;
        double translateZ = 0.0;

        if (axis == Direction.Axis.Y) {
            if (climbDir == Direction.DOWN) {
                translateY = -edgeOffset;
            } else {
                translateY = sizeY - edgeOffset;
            }
        } else {
            translateY = sizeY / 2.0;
            if (axis == Direction.Axis.X) {
                translateX = (sizeX / 2.0) * step - edgeOffset;
            } else {
                translateZ = (sizeZ / 2.0) * step - edgeOffset;
            }
        }

        double dx = worldPos.x - (entityPos.x + translateX);
        double dy = worldPos.y - (entityPos.y + translateY);
        double dz = worldPos.z - (entityPos.z + translateZ);

        rot.conjugate(); 

        Vector3f vec = new Vector3f((float) dx, (float) dy, (float) dz);
        vec.rotate(rot);

        return new Vec3(vec.x(), vec.y(), vec.z());
    }

    public static boolean intersectsRay(Vec3 startWorld, Vec3 endWorld, LivingBlock entity, float partialTicks) {
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return entity.getBoundingBox().contains(startWorld);
        }

        Vec3 localStart = worldToLocal(startWorld, entity, partialTicks);
        Vec3 localEnd = worldToLocal(endWorld, entity, partialTicks);

        AABB bounds = entity.getShapeBounds();

        Vec3 shapeCenter = new Vec3(
                (bounds.minX + bounds.maxX) / 2.0,
                (bounds.minY + bounds.maxY) / 2.0,
                (bounds.minZ + bounds.maxZ) / 2.0
        );

        Vec3 shiftedStart = localStart.add(shapeCenter);
        Vec3 shiftedEnd = localEnd.add(shapeCenter);

        for (AABB box : entity.getShapeBoxes()) {
            if (box.clip(shiftedStart, shiftedEnd).isPresent()) {
                return true;
            }
        }

        return false;
    }

    private static final float CONTACT_TOLERANCE = 0.01F;

    private static void cornerAt(Vector3f dest, AABB box, int index, double cx, double cy, double cz) {
        dest.set(
                (float) (((index & 1) == 0 ? box.minX : box.maxX) - cx),
                (float) (((index & 2) == 0 ? box.minY : box.maxY) - cy),
                (float) (((index & 4) == 0 ? box.minZ : box.maxZ) - cz)
        );
    }

    public static float groundContactSpan(LivingBlock entity, Quaternionf rotation) {
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return 1.0F;
        }

        AABB bounds = entity.getShapeBounds();
        double cx = (bounds.minX + bounds.maxX) / 2.0;
        double cy = (bounds.minY + bounds.maxY) / 2.0;
        double cz = (bounds.minZ + bounds.maxZ) / 2.0;

        Vector3f corner = new Vector3f();
        float lowest = Float.POSITIVE_INFINITY;
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (AABB box : entity.getShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                lowest = Math.min(lowest, corner.y());
                minX = Math.min(minX, corner.x());
                maxX = Math.max(maxX, corner.x());
                minZ = Math.min(minZ, corner.z());
                maxZ = Math.max(maxZ, corner.z());
            }
        }

        float spanX = maxX - minX;
        float spanZ = maxZ - minZ;
        if (spanX < 1.0E-4F || spanZ < 1.0E-4F) {
            return 0.0F;
        }

        float plane = lowest + CONTACT_TOLERANCE;
        float contactMinX = Float.POSITIVE_INFINITY;
        float contactMaxX = Float.NEGATIVE_INFINITY;
        float contactMinZ = Float.POSITIVE_INFINITY;
        float contactMaxZ = Float.NEGATIVE_INFINITY;

        for (AABB box : entity.getShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                if (corner.y() <= plane) {
                    contactMinX = Math.min(contactMinX, corner.x());
                    contactMaxX = Math.max(contactMaxX, corner.x());
                    contactMinZ = Math.min(contactMinZ, corner.z());
                    contactMaxZ = Math.max(contactMaxZ, corner.z());
                }
            }
        }

        if (contactMaxX < contactMinX) {
            return 0.0F;
        }

        return Mth.clamp(((contactMaxX - contactMinX) / spanX) * ((contactMaxZ - contactMinZ) / spanZ), 0.0F, 1.0F);
    }

    public static Vector3f supportCorner(LivingBlock entity, Quaternionf rotation, Vector3f direction) {
        Vector3f best = new Vector3f();
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return best;
        }

        AABB bounds = entity.getShapeBounds();
        double cx = (bounds.minX + bounds.maxX) / 2.0;
        double cy = (bounds.minY + bounds.maxY) / 2.0;
        double cz = (bounds.minZ + bounds.maxZ) / 2.0;

        float bestDot = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();

        for (AABB box : entity.getShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                float dot = corner.dot(direction);
                if (dot > bestDot) {
                    bestDot = dot;
                    best.set(corner);
                }
            }
        }

        return best;
    }

    public static void includeRotatedOBBCorners(LivingBlock entity, Quaternionf rotation, AABBBuilder builder) {
        VoxelShape shape = entity.getCustomShape();
        if (shape == null || shape.isEmpty()) {
            return;
        }

        AABB bounds = entity.getShapeBounds();
        double cx = (bounds.minX + bounds.maxX) / 2.0;
        double cy = (bounds.minY + bounds.maxY) / 2.0;
        double cz = (bounds.minZ + bounds.maxZ) / 2.0;

        Vector3f corner = new Vector3f();
        for (AABB box : entity.getShapeBoxes()) {
            for (int i = 0; i < 8; i++) {
                cornerAt(corner, box, i, cx, cy, cz);
                corner.rotate(rotation);
                builder.include(corner);
            }
        }
    }
}