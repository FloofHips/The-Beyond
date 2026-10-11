package com.thebeyond.common.entity.util.livingblock;

import org.joml.Quaternionf;

import java.util.function.DoubleSupplier;

public final class LivingBlockRoll {

    public record GatedRoll(double scale, double gainedRaw, double gained) {
    }

    public static final int AXIS_NONE = -1;
    public static final int AXIS_X = 0;
    public static final int AXIS_Z = 2;

    public static final double AXIS_EPSILON = 1.0E-9;
    public static final double RESIDUAL_EPSILON = 1.0E-4;
    public static final double SETTLE_RATE_DEGREES = 12.0;

    private LivingBlockRoll() {
    }

    public static double quantizedSide(final double sideLength) {
        return sideLength <= 1.0E-5
                ? sideLength
                : 1.0 / Math.max(1.0, (double) Math.round(1.0 / sideLength));
    }

    public static int dominantAxis(final double moveX, final double moveZ, final int current) {
        double absX = Math.abs(moveX);
        double absZ = Math.abs(moveZ);
        if (absX < AXIS_EPSILON && absZ < AXIS_EPSILON) {
            return current;
        }
        return absX >= absZ ? AXIS_Z : AXIS_X;
    }

    public static double residualToQuarter(final double phaseDegrees) {
        return 90.0 * Math.round(phaseDegrees / 90.0) - phaseDegrees;
    }

    public static void applyResidual(final Quaternionf dest, final int axis, final double residualDegrees) {
        if (axis == AXIS_NONE || Math.abs(residualDegrees) < RESIDUAL_EPSILON) {
            return;
        }
        float radians = (float) Math.toRadians(residualDegrees);
        if (axis == AXIS_X) {
            dest.rotateLocalX(radians);
        } else {
            dest.rotateLocalZ(radians);
        }
    }

    public static double bleedStep(final double pending) {
        if (Math.abs(pending) <= SETTLE_RATE_DEGREES) {
            return pending;
        }
        return pending < 0.0 ? -SETTLE_RATE_DEGREES : SETTLE_RATE_DEGREES;
    }

    public static void applyRoll(final Quaternionf dest, final float dx, final float dz) {
        if (Math.abs(dx) > 1.0E-5F) {
            dest.rotateLocalX(dx * (float) (Math.PI / 2));
        }
        if (Math.abs(dz) > 1.0E-5F) {
            dest.rotateLocalZ(dz * (float) (Math.PI / 2));
        }
    }

    public static GatedRoll gateRoll(final Quaternionf dest, final float dx, final float dz,
                                     final double budget, final int passes,
                                     final DoubleSupplier overlap) {
        double before = overlap.getAsDouble();
        Quaternionf saved = new Quaternionf(dest);
        applyRoll(dest, dx, dz);
        double raw = overlap.getAsDouble() - before;
        double gained = raw;
        double scale = 1.0;
        double safeScale = 0.0;
        double safeGained = 0.0;
        if (gained <= budget) {
            safeScale = scale;
            safeGained = gained;
        }
        for (int pass = 0; pass < passes && gained > budget; pass++) {
            double next = scale * budget / gained;
            if (!(next < scale)) {
                break;
            }
            scale = next;
            dest.set(saved);
            applyRoll(dest, (float) (dx * scale), (float) (dz * scale));
            gained = overlap.getAsDouble() - before;
            if (gained <= budget && scale > safeScale) {
                safeScale = scale;
                safeGained = gained;
            }
        }
        if (gained > budget) {
            dest.set(saved);
            if (safeScale > 0.0) {
                applyRoll(dest, (float) (dx * safeScale), (float) (dz * safeScale));
            }
            return new GatedRoll(safeScale, raw, safeGained);
        }
        return new GatedRoll(scale, raw, gained);
    }
}
