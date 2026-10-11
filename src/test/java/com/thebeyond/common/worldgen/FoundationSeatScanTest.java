package com.thebeyond.common.worldgen;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

/**
 * Gate for the SEATED-structure foundation refill: the body clear strips the ground under {@code
 * gLo}, so the fill must restore it without pouring a pillar past a thin island into the void.
 */
class FoundationSeatScanTest {

    private static final int GLO = 100;
    private static final int DEPTH = BeyondEndChunkGenerator.FOUNDATION_DEPTH;   // production bound (8)

    private static boolean fillsAt(int y, IntPredicate naturalSolid) {
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, naturalSolid);
        return BeyondEndChunkGenerator.the_beyond$isFoundationFill(y, GLO, natTop);
    }

    @Test
    void thickIsland_refillsBase_noGap_noStrayFill() {
        IntPredicate island = y -> y <= GLO;
        Assertions.assertTrue(fillsAt(GLO, island),
                "thick island: carved base gLo must be REFILLED (else the base floats a block — the gap bug)");
        Assertions.assertFalse(fillsAt(GLO + 1, island), "must not fill above the base");
        Assertions.assertFalse(fillsAt(GLO - 1, island),
                "natural island block just below the base needs no foundation fill");
        // explicit regression pin: a gLo-start scan would yield natTop==gLo and leave gLo unfilled.
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, island);
        Assertions.assertEquals(GLO - 1, natTop,
                "natTop must be the first natural-solid STRICTLY below the carved body (gLo-1), not gLo");
    }

    @Test
    void thinPancake_refillsBase_noVoidPillar() {
        IntPredicate pancake = y -> y >= GLO - 3 && y <= GLO;
        Assertions.assertTrue(fillsAt(GLO, pancake), "thin pancake: carved base gLo must be refilled");
        // the original pillar bug: a fixed-depth fill poured gLo-4, gLo-5 into the void below the island.
        for (int y = GLO - 4; y >= GLO - DEPTH; y--) {
            Assertions.assertFalse(fillsAt(y, pancake),
                    "no foundation fill below the island bottom (void pillar) at y=" + y);
        }
    }

    @Test
    void overhang_bridgesGapToLeg_notBelowIsland() {
        IntPredicate overhang = y -> y <= GLO - 3;
        Assertions.assertTrue(fillsAt(GLO, overhang), "overhang: base refilled");
        Assertions.assertTrue(fillsAt(GLO - 1, overhang), "overhang: gap bridged");
        Assertions.assertTrue(fillsAt(GLO - 2, overhang), "overhang: gap bridged down to the island top");
        Assertions.assertFalse(fillsAt(GLO - 3, overhang), "the island top itself is natural — no fill");
        Assertions.assertFalse(fillsAt(GLO - 4, overhang), "never fill below the island (no pillar)");
    }

    @Test
    void deepVoid_fillsNothing() {
        IntPredicate empty = y -> false;
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, empty);
        Assertions.assertEquals(Integer.MIN_VALUE, natTop, "no natural terrain in band → no anchor");
        for (int y = GLO - DEPTH; y <= GLO + 2; y++) {
            Assertions.assertFalse(fillsAt(y, empty), "deep void: fill nothing (no stilt) at y=" + y);
        }
    }

    @Test
    void respectsFoundationDepthBound() {
        // deeper than the depth bound → out of reach → no fill.
        IntPredicate deepIsland = y -> y <= GLO - DEPTH;
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, deepIsland);
        Assertions.assertEquals(Integer.MIN_VALUE, natTop,
                "island deeper than FOUNDATION_DEPTH below the base is out of range → no fill");
    }

    // foundationNatTop must not leap a void gap onto a stray sliver or separate lower island: the bridge is
    // bounded to a real surface lip (FOUNDATION_MAX_LIP air) with a substantial run (FOUNDATION_MIN_RUN) on top.

    @Test
    void disconnectedSliver_belowGapBudget_doesNotGlue() {
        IntPredicate sliver = y -> y == GLO - 7;   // gap 6 > MAX_LIP
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, sliver);
        Assertions.assertEquals(Integer.MIN_VALUE, natTop,
                "a sliver separated from the base by a void gap must NOT anchor a fill (the glued-column bug)");
        for (int y = GLO; y > GLO - DEPTH; y--)
            Assertions.assertFalse(fillsAt(y, sliver), "no END_STONE column poured onto a disconnected sliver at y=" + y);
    }

    @Test
    void separateLowerIsland_beyondLipBudget_doesNotGlue() {
        IntPredicate lower = y -> y <= GLO - 6;   // gap 5 > MAX_LIP
        Assertions.assertEquals(Integer.MIN_VALUE,
                BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, lower),
                "the base must not glue a tall column onto a DIFFERENT lower island it doesn't rest on");
    }

    @Test
    void sameIslandRecess_withinLipBudget_stillSeats() {
        IntPredicate recess = y -> y <= GLO - 3;   // 3-block air lip, within MAX_LIP
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, recess);
        Assertions.assertEquals(GLO - 3, natTop,
                "a real same-island recess within the lip budget (≤MAX_LIP air, ≥MIN_RUN solid) STILL seats");
        Assertions.assertTrue(fillsAt(GLO, recess) && fillsAt(GLO - 2, recess),
                "the ≤3 lip is bridged up to the base");
    }

    @Test
    void thinSliverAtBase_belowMinRun_doesNotGlue() {
        IntPredicate thinTop = y -> y == GLO - 1;
        Assertions.assertEquals(Integer.MIN_VALUE,
                BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, thinTop),
                "a 1-block sliver at the base (run < MIN_RUN) is not ground → no fill");
        // contiguousRockDown sanity: 1 < FOUNDATION_MIN_RUN(3).
        Assertions.assertEquals(1, BeyondEndChunkGenerator.the_beyond$contiguousRockDown(
                GLO - 1, BeyondEndChunkGenerator.FOUNDATION_MIN_RUN, thinTop));
    }

    // A distributed end_city gets no per-column footing anywhere, so its cantilevers never weld to
    // islands they pass over; a seated-but-not-distributed tower still needs footing under its legs.

    @Test
    void footingActiveAt_truthTable() {
        // footingActiveAt(seated, distributed, inBaseFootprint) = seated && !distributed; inBaseFootprint is
        // ignored — a distributed base rests on its own start_platform foot instead.
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$footingActiveAt(false, false, false),
                "a pure FLOATING structure has no foundation footing");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$footingActiveAt(true, false, false),
                "a SEATED-but-NOT-distributed tower keeps its footing everywhere (inBaseFootprint ignored, legs need it)");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$footingActiveAt(true, false, true),
                "…still keyed off seated alone for a non-distributed tower (inBaseFootprint ignored)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$footingActiveAt(true, true, true),
                "a DISTRIBUTED instance gets NO footing even in its base footprint (base on its own start_platform foot)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$footingActiveAt(true, true, false),
                "a DISTRIBUTED instance OUTSIDE the base footprint also gets no footing (no weld)");
    }

    @Test
    void distributed_noFootingAnywhere() {
        // Island is in reach and isFoundationFill would place the bridge — proves footingActiveAt (not
        // isFoundationFill) is what suppresses the slab for a distributed structure.
        IntPredicate islandBelow = y -> y <= GLO - 2;
        int natTop = BeyondEndChunkGenerator.the_beyond$foundationNatTop(GLO, DEPTH, islandBelow);
        Assertions.assertEquals(GLO - 2, natTop, "precondition: the island is within reach");
        Assertions.assertTrue(BeyondEndChunkGenerator.the_beyond$isFoundationFill(GLO, GLO, natTop),
                "precondition: isFoundationFill WOULD place the fill if the gate allowed it");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$footingActiveAt(true, true, true),
                "distributed base column: footing OFF (no placed slab; base on its own start_platform foot)");
        Assertions.assertFalse(BeyondEndChunkGenerator.the_beyond$footingActiveAt(true, true, false),
                "distributed cantilever column: footing OFF (no END_STONE bridge under house/walkway/ship)");
    }

    @Test
    void fromBoxes_distributedSuppressesFootingEverywhere() {
        final int R = BeyondEndChunkGenerator.BEARD_LAT_REACH;
        var boxes = java.util.List.of(
                new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 100, 0, 8, 140, 8));
        BeyondEndChunkGenerator.CarveMask mask =
                BeyondEndChunkGenerator.CarveMask.fromBoxes(boxes, true, 0, true, true);
        Assertions.assertTrue(mask.distributed, "fromBoxes(distributed=true) builds a distributed mask");
        // inBaseFootprint is kept for the future rounded-base option; it still classifies base vs cantilever columns.
        Assertions.assertTrue(mask.inBaseFootprint(4, 4), "a column inside the start_platform footprint is base");
        Assertions.assertTrue(mask.inBaseFootprint(8 + R, 4), "…still base out to the lip's lateral reach");
        Assertions.assertFalse(mask.inBaseFootprint(8 + R + 1, 4), "a column past base+reach is a cantilever, not base");
        Assertions.assertFalse(mask.inBaseFootprint(4, 8 + R + 1), "…in Z too");
        Assertions.assertFalse(
                BeyondEndChunkGenerator.the_beyond$footingActiveAt(mask.seated, mask.distributed, mask.inBaseFootprint(4, 4)),
                "distributed mask: base column footing OFF (slab dropped)");
        Assertions.assertFalse(
                BeyondEndChunkGenerator.the_beyond$footingActiveAt(mask.seated, mask.distributed, mask.inBaseFootprint(40, 40)),
                "distributed mask: far cantilever column footing OFF");
    }
}
