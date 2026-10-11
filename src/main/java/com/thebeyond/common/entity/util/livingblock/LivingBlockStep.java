package com.thebeyond.common.entity.util.livingblock;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class LivingBlockStep {

    public static final double HEIGHT = 0.5;
    public static final double SLACK = 0.05;
    public static final double FLOOR = 2.0E-3;
    public static final double REACH = 0.30;

    private LivingBlockStep() {
    }

    public record Verdict(boolean ok, String reason, double rise, double top) {

        static Verdict no(final String reason) {
            return new Verdict(false, reason, Double.NaN, Double.NaN);
        }
    }

    public static AABB probe(final AABB hull, final Direction direction) {
        int stepX = direction.getStepX();
        int stepZ = direction.getStepZ();
        double faceX = stepX > 0 ? hull.maxX : hull.minX;
        double faceZ = stepZ > 0 ? hull.maxZ : hull.minZ;
        return new AABB(
                stepX != 0 ? faceX : hull.minX, hull.minY + FLOOR,
                stepZ != 0 ? faceZ : hull.minZ,
                stepX != 0 ? faceX + stepX * REACH : hull.maxX, hull.minY + HEIGHT + SLACK,
                stepZ != 0 ? faceZ + stepZ * REACH : hull.maxZ);
    }

    public static Verdict ahead(final List<AABB> surfaces, final AABB hull,
                                final Direction direction) {
        if (!direction.getAxis().isHorizontal()) {
            return Verdict.no("nothorizontal");
        }
        AABB probe = probe(hull, direction);
        double top = Double.NEGATIVE_INFINITY;
        for (AABB world : surfaces) {
            if (!world.intersects(probe)) {
                continue;
            }
            if (world.maxY > probe.maxY) {
                return Verdict.no("toohigh");
            }
            top = Math.max(top, world.maxY);
        }
        if (top == Double.NEGATIVE_INFINITY) {
            return Verdict.no("nostep");
        }
        return new Verdict(true, "ok", top - hull.minY, top);
    }

    public static boolean lowDrop(final double drop) {
        return drop <= HEIGHT + SLACK;
    }
}
