package com.thebeyond.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boundary tests for {@link FovStealth} — the flattened frontal-cone geometry that gates
 * whether a deafened mob can notice the player. Pure math, no Minecraft bootstrap.
 */
class FovStealthTest {

    private static double[] point(double phiDeg, double psiDeg, double r) {
        double phi = Math.toRadians(phiDeg);
        double psi = Math.toRadians(psiDeg);
        double rh = r * Math.cos(psi);
        return new double[]{rh * Math.sin(phi), r * Math.sin(psi), rh * Math.cos(phi)};
    }

    private static boolean inCone(double phiDeg, double psiDeg, float yaw) {
        double[] p = point(phiDeg, psiDeg, 10.0);
        return FovStealth.inFovCone(0, 0, 0, p[0], p[1], p[2], yaw, 0.0f);
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "45",
            "74",
    })
    void insideHorizontal(double phi) {
        assertTrue(inCone(phi, 0, 0f), () -> "φ=" + phi + " should be in cone");
        assertTrue(inCone(-phi, 0, 0f), () -> "φ=-" + phi + " should be in cone");
    }

    @ParameterizedTest
    @CsvSource({
            "76",
            "90",
            "135",
            "180",
    })
    void outsideHorizontal(double phi) {
        assertFalse(inCone(phi, 0, 0f), () -> "φ=" + phi + " should be outside cone");
        assertFalse(inCone(-phi, 0, 0f), () -> "φ=-" + phi + " should be outside cone");
    }

    @Test
    void insideVerticalUp() {
        assertTrue(inCone(0, 49, 0f), "ψ=+49 should be in (below the +50 up limit)");
    }

    @Test
    void outsideVerticalUp() {
        assertFalse(inCone(0, 51, 0f), "ψ=+51 should be out (above the +50 up limit)");
    }

    @Test
    void insideVerticalDown() {
        assertTrue(inCone(0, -69, 0f), "ψ=-69 should be in (above the -70 down limit)");
    }

    @Test
    void outsideVerticalDown() {
        assertFalse(inCone(0, -71, 0f), "ψ=-71 should be out (below the -70 down limit)");
    }

    @Test
    void verticalAsymmetry() {
        assertTrue(inCone(0, -60, 0f), "ψ=-60 down should be in");
        assertFalse(inCone(0, 60, 0f), "ψ=+60 up should be out");
    }

    // Exact-edge classification hinges on sub-ULP float behaviour and isn't robustly assertable;
    // pin the boundary location to within 0.1° instead.
    @Test
    void horizontalBoundaryNear75() {
        assertTrue(inCone(74.9, 0, 0f), "74.9° horizontal is just inside");
        assertFalse(inCone(75.1, 0, 0f), "75.1° horizontal is just outside");
    }

    @Test
    void verticalUpBoundaryNear50() {
        assertTrue(inCone(0, 49.9, 0f), "+49.9° up is just inside");
        assertFalse(inCone(0, 50.1, 0f), "+50.1° up is just outside");
    }

    @Test
    void verticalDownBoundaryNear70() {
        assertTrue(inCone(0, -69.9, 0f), "-69.9° down is just inside");
        assertFalse(inCone(0, -70.1, 0f), "-70.1° down is just outside");
    }

    @Test
    void yawRotatesCone() {
        // Body yaw 90° → forward = (-sin90, cos90) = (-1, 0) = -X.
        assertTrue(FovStealth.inFovCone(0, 0, 0, -10, 0, 0, 90f, 0.0f), "target at -X is in front when yaw=90");
        assertFalse(FovStealth.inFovCone(0, 0, 0, 10, 0, 0, 90f, 0.0f), "target at +X is behind when yaw=90");
    }

    @Test
    void overlapCountsAsSeen() {
        assertTrue(FovStealth.inFovCone(5, 64, 5, 5, 64, 5, 137f, 0.0f), "exact overlap → seen");
    }

    @Test
    void straightUpBeyondLimitIsOut() {
        // Directly overhead: normalized dir.y = 1 > sin(50°) → rejected before the XZ normalize.
        assertFalse(FovStealth.inFovCone(0, 0, 0, 0, 10, 0, 0f, 0.0f), "straight up is out");
    }

    @Test
    void vec3OverloadAgrees() {
        double[] p = point(30, 10, 8.0);
        assertEquals(
                FovStealth.inFovCone(0, 0, 0, p[0], p[1], p[2], 0f, 0.0f),
                FovStealth.inFovCone(new net.minecraft.world.phys.Vec3(0, 0, 0),
                        new net.minecraft.world.phys.Vec3(p[0], p[1], p[2]), 0f, 0.0f),
                "Vec3 overload must match the primitive overload");
    }

    // MC pitch: positive looks DOWN. At pitch θ, world elevation ψ reads as ψ+θ relative to the gaze.
    private static boolean inConeP(double phiDeg, double psiDeg, float yaw, float pitch) {
        double[] p = point(phiDeg, psiDeg, 10.0);
        return FovStealth.inFovCone(0, 0, 0, p[0], p[1], p[2], yaw, pitch);
    }

    @Test
    void gazeUpLiftsTheCone() {
        assertFalse(inConeP(0, 60, 0f, 0f), "level gaze: +60° up is outside (+50 limit)");
        assertTrue(inConeP(0, 60, 0f, -30f), "gazing 30° up: +60° world is only +30° relative — inside");
        assertFalse(inConeP(0, -55, 0f, -30f), "gazing 30° up: −55° world is −85° relative — below the −70 limit");
    }

    @Test
    void gazeDownDropsTheCone() {
        assertTrue(inConeP(0, -60, 0f, 60f), "gazing 60° down: −60° world is dead ahead");
        assertFalse(inConeP(0, 0, 0f, 60f), "gazing 60° down: the horizon is +60° relative — outside (+50 limit)");
    }

    @Test
    void straightUpGazeSeesOverhead() {
        // Degenerate right-vector fallback: gazing straight up, a target directly overhead is dead ahead.
        assertTrue(FovStealth.inFovCone(0, 0, 0, 0, 10, 0, 0f, -90f), "vertical gaze: overhead target is ahead");
    }

    @Test
    void pitchZeroMatchesLevelModel() {
        // At pitch 0 the gaze-frame math must degenerate to the plain level-oriented model.
        for (double[] c : new double[][]{{30, 20}, {-60, -50}, {74, 0}, {0, 49}, {120, 10}}) {
            boolean gazeFrame = inConeP(c[0], c[1], 0f, 0.0f);
            double[] p = point(c[0], c[1], 10.0);
            double ny = p[1] / 10.0;
            boolean level = !(ny > Math.sin(Math.toRadians(50)) || ny < -Math.sin(Math.toRadians(70)))
                    && Math.cos(Math.toRadians(c[0])) >= Math.cos(Math.toRadians(75));
            assertEquals(level, gazeFrame, () -> "pitch-0 equivalence broken at φ=" + c[0] + " ψ=" + c[1]);
        }
    }
}
