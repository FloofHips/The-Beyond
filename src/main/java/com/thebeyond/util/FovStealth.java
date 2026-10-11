package com.thebeyond.util;

import net.minecraft.world.phys.Vec3;

/** Whether a target sits in a mob's frontal cone, flattened and asymmetric, turning with the head (no raycast). */
public final class FovStealth {
    private FovStealth() {}

    public static final double H_HALF_DEG = 75.0;
    public static final double V_UP_DEG = 50.0;
    public static final double V_DOWN_DEG = 70.0;

    static final double COS_H_HALF = Math.cos(Math.toRadians(H_HALF_DEG));
    static final double SIN_V_UP = Math.sin(Math.toRadians(V_UP_DEG));
    static final double SIN_V_DOWN = -Math.sin(Math.toRadians(V_DOWN_DEG));

    /** Overlap guard: below this eye-to-eye distance the target is effectively on top of the mob → seen. */
    private static final double MIN_DIST = 1.0e-6;

    /** @param pitchDeg Minecraft convention: positive looks DOWN. */
    public static boolean inFovCone(double mobEyeX, double mobEyeY, double mobEyeZ,
                                    double targetEyeX, double targetEyeY, double targetEyeZ,
                                    float yawDeg, float pitchDeg) {
        double dx = targetEyeX - mobEyeX;
        double dy = targetEyeY - mobEyeY;
        double dz = targetEyeZ - mobEyeZ;

        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < MIN_DIST) return true;  // overlapping, noticed whatever the facing

        // head frame without roll: forward is the view vector, right is F x up (yaw only when vertical), up is R x F
        double yr = Math.toRadians(yawDeg);
        double pr = Math.toRadians(pitchDeg);
        double cp = Math.cos(pr);
        double fx = -Math.sin(yr) * cp;
        double fy = -Math.sin(pr);
        double fz = Math.cos(yr) * cp;

        double rx = -fz, rz = fx;                 // F × worldUp = (−fz, 0, fx)
        double rLen = Math.sqrt(rx * rx + rz * rz);
        if (rLen < MIN_DIST) {  // gazing straight up or down, right from yaw alone
            rx = -Math.cos(yr);
            rz = -Math.sin(yr);
        } else {
            rx /= rLen;
            rz /= rLen;
        }
        double ux = -rz * fy;                     // U = R × F (R has y = 0)
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;

        // Vertical first (cheap): normalized head-up component vs the asymmetric limits.
        double du = dx * ux + dy * uy + dz * uz;
        double nu = du / dist;
        if (nu > SIN_V_UP || nu < SIN_V_DOWN) return false;

        // Horizontal: remove the head-up component, normalize, compare against the gaze forward.
        double hx = dx - du * ux;
        double hy = dy - du * uy;
        double hz = dz - du * uz;
        double hLen = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hLen < MIN_DIST) return true;  // on the gaze's vertical axis, treated as seen
        double dot = (hx * fx + hy * fy + hz * fz) / hLen;
        return dot >= COS_H_HALF;
    }

    /** Vec3 overload taking both eye positions and the head rotation. */
    public static boolean inFovCone(Vec3 mobEye, Vec3 targetEye, float yawDeg, float pitchDeg) {
        return inFovCone(mobEye.x, mobEye.y, mobEye.z, targetEye.x, targetEye.y, targetEye.z, yawDeg, pitchDeg);
    }

    /** Head-frame basis without roll, the debug view of the test above. */
    public record Basis(Vec3 fwd, Vec3 right, Vec3 up) {}

    public static Basis basis(float yawDeg, float pitchDeg) {
        double yr = Math.toRadians(yawDeg);
        double pr = Math.toRadians(pitchDeg);
        double cp = Math.cos(pr);
        Vec3 fwd = new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
        double rx = -fwd.z, rz = fwd.x;
        double rLen = Math.sqrt(rx * rx + rz * rz);
        Vec3 right = rLen < MIN_DIST
                ? new Vec3(-Math.cos(yr), 0.0, -Math.sin(yr))
                : new Vec3(rx / rLen, 0.0, rz / rLen);
        Vec3 up = right.cross(fwd);
        return new Basis(fwd, right, up);
    }
}
