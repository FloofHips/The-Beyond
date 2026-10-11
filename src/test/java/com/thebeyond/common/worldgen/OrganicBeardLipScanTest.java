package com.thebeyond.common.worldgen;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

/**
 * Gate for the organic beard lip: it rises the island to hug the structure base and cover the exposed
 * platform slab, but must never bury a thin piece or exceed {@code FLOAT_HMARGIN}. Pure helpers, no noise.
 */
class OrganicBeardLipScanTest {

    private static final int R = BeyondEndChunkGenerator.BEARD_LAT_REACH;
    private static final int COVER = BeyondEndChunkGenerator.PLATFORM_COVER;
    private static final int DEPTH = BeyondEndChunkGenerator.FOUNDATION_DEPTH;

    @Test
    void withinGuard_noFloatByConstruction() {
        Assertions.assertTrue(R <= BeyondEndChunkGenerator.FLOAT_HMARGIN,
                "BEARD_LAT_REACH must stay within FLOAT_HMARGIN — the lip's added solid is inside the carve guard (no float)");
    }

    @Test
    void risesToPlatformTop_coversSlabSide() {
        int anchorColLo = 120, buildingTop = 180;           // colHi: tall building far above the apron
        int platformTop = anchorColLo + (COVER - 1);        // slab's exposed top face
        int natTop = anchorColLo - 1;                       // island just under the apron
        int near = BeyondEndChunkGenerator.the_beyond$lipTop(natTop, anchorColLo, buildingTop, 1);
        int mid  = BeyondEndChunkGenerator.the_beyond$lipTop(natTop, anchorColLo, buildingTop, 3);
        int edge = BeyondEndChunkGenerator.the_beyond$lipTop(natTop, anchorColLo, buildingTop, R);
        Assertions.assertTrue(near >= platformTop - 1,
                "adjacent to the base the lip reaches ~the platform TOP (covers the exposed slab side); got " + near + " target " + platformTop);
        Assertions.assertTrue(near <= platformTop, "the lip never exceeds the platform top; got " + near);
        Assertions.assertTrue(near >= mid && mid >= edge,
                "the lip is FEATHERED — monotonically tapering with dxz; near=" + near + " mid=" + mid + " edge=" + edge);
        Assertions.assertEquals(natTop, edge, "at BEARD_LAT_REACH the lip feathers down to the island top; got " + edge);
    }

    @Test
    void clampedAtColHi_neverAboveTheStructure() {
        // thin piece (colHi only 1 above colLo): cover target clamps to colHi, never rises above it
        int anchorColLo = 120, thinColHi = 121, natTop = 119;
        int lt = BeyondEndChunkGenerator.the_beyond$lipTop(natTop, anchorColLo, thinColHi, 1);
        Assertions.assertTrue(lt <= thinColHi, "the lip clamps at the occupied column's own top (colHi), never above; got " + lt);
    }

    @Test
    void neverBuriesTheBuilding() {
        // cover target stays far below a tall building's top, even adjacent to the base
        int anchorColLo = 120, buildingTop = 180;
        int platformTop = anchorColLo + (COVER - 1);
        int lt = BeyondEndChunkGenerator.the_beyond$lipTop(anchorColLo + 2, anchorColLo, buildingTop, 1);
        Assertions.assertTrue(lt <= platformTop, "the lip never exceeds the platform apron top; got " + lt);
        Assertions.assertTrue(lt < buildingTop, "the lip is far below the building top (the visible structure is never buried)");
    }

    @Test
    void overVoid_zeroLip_theNoFloatCase() {
        int lt = BeyondEndChunkGenerator.the_beyond$lipTop(Integer.MIN_VALUE, 120, 180, 1);
        Assertions.assertEquals(Integer.MIN_VALUE, lt, "a void/cantilever fringe (no island) must get NO lip");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$isLipFill(110, Integer.MIN_VALUE, lt),
                "no cell is lip-fill when there is no island (no floating endstone)");
    }

    @Test
    void overSliver_zeroLip() {
        int anchorColLo = 120;
        IntPredicate sliver = y -> y == anchorColLo - 1 || y == anchorColLo - 2;   // 2 solid blocks then void
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(anchorColLo, DEPTH, sliver);
        Assertions.assertEquals(Integer.MIN_VALUE, natTop, "a sub-MIN_RUN sliver is not ground (no lip anchor)");
        Assertions.assertEquals(Integer.MIN_VALUE, BeyondEndChunkGenerator.the_beyond$lipTop(natTop, anchorColLo, 180, 1),
                "a sliver fringe gets ZERO lip (no weld onto a 1-2-block sliver)");
    }

    @Test
    void addOnly_fillRangeIsAboveTheIsland() {
        int natTop = 100, lipTop = 104;
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$isLipFill(natTop, natTop, lipTop), "never fills AT the island top (no void pillar)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$isLipFill(natTop - 1, natTop, lipTop), "never fills below the island top");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$isLipFill(natTop + 1, natTop, lipTop), "fills just above the island");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$isLipFill(lipTop, natTop, lipTop), "fills up to the lip top (inclusive)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$isLipFill(lipTop + 1, natTop, lipTop), "never fills above the lip top");
    }
}
