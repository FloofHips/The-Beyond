package com.thebeyond.common.worldgen;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganicLightTest {
    private static final OrganicLight.Bonfire TALL = new OrganicLight.Bonfire(1, 100, 170, -50, 157, true, true, 3, 0.6,
            false);

    @Test
    void clearingStaysBetweenTheJitterBounds() {
        BeyondEndChunkGenerator.seed = 1234;
        Random r = new Random(7);
        int inside = 0, outside = 0;
        for (int i = 0; i < 20000; i++) {
            double x = 100.5 + (r.nextDouble() - 0.5) * 40, y = 140 + r.nextDouble() * 60, z = -49.5 + (r.nextDouble() - 0.5) * 40;
            double cy = Math.max(157.5, Math.min(170.5, y));
            double d = Math.sqrt((x - 100.5) * (x - 100.5) + (y - cy) * (y - cy) + (z + 49.5) * (z + 49.5));
            boolean in = TALL.reaches(x, y, z);
            if (d <= OrganicLight.RADIUS - OrganicLight.JITTER - 1e-9) {
                assertTrue(in, "inside the smallest radius at " + d);
                inside++;
            }
            if (d > OrganicLight.RADIUS + OrganicLight.JITTER + 1e-9) {
                assertFalse(in, "outside the largest radius at " + d);
                outside++;
            }
        }
        assertTrue(inside > 100 && outside > 100, "both bounds exercised: " + inside + " " + outside);
    }

    @Test
    void clearingFollowsThePillarNotOnlyTheFire() {
        BeyondEndChunkGenerator.seed = 1234;
        assertTrue(TALL.reaches(100.5 + OrganicLight.RADIUS - OrganicLight.JITTER - 0.5, 158.5, -49.5), "beside the foot");
        assertFalse(TALL.reaches(100.5, 157.5 - OrganicLight.RADIUS - OrganicLight.JITTER - 0.5, -49.5), "below the foot");
    }

    @Test
    void defaultRulesCleanTubesKeepRockAndLeaveBulbsBreakable() {
        assertEquals(OrganicLight.Action.REMOVE, OrganicLight.action(OrganicNetwork.TUBE));
        assertEquals(OrganicLight.Action.REMOVE, OrganicLight.action(OrganicNetwork.FILM));
        assertEquals(OrganicLight.Action.REMOVE, OrganicLight.action(OrganicNetwork.PENDANT));
        assertEquals(OrganicLight.Action.ROCK, OrganicLight.action(OrganicNetwork.TUNNEL));
        assertEquals(OrganicLight.Action.BREAKABLE, OrganicLight.action(OrganicNetwork.BULB));
    }
}
