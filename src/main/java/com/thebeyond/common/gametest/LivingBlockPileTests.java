package com.thebeyond.common.gametest;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.BeadEntity;
import com.thebeyond.common.entity.util.livingblock.movement.Target;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

@GameTestHolder(TheBeyond.MODID)
@PrefixGameTestTemplate(false)
public final class LivingBlockPileTests {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final int SPAN = 16;
    private static final int FLOOR = 1;
    private static final int STAND = FLOOR + 1;
    private static final int WALL_X = 12;
    private static final int WALL_Z = 13;
    private static final int WALL_HEIGHT = 4;
    private static final int AIM_Z = 8;

    private static final int[] GROW_X = {1, 3, 5, 7};
    private static final int[] GROW_Z = {1, 4, 7, 10};
    private static final int BEADS = 12;

    private static final int GROWTH_STEPS = 400;
    private static final int SETTLE_TICKS = 10;
    private static final int ORDER_TICKS = 600;
    private static final double RADIUS_TIGHT = 1.0E-6;
    private static final double RADIUS_WIDE = 0.1;
    private static final double HULL_FULL = 0.99;

    private LivingBlockPileTests() {
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisTight0(final GameTestHelper helper) {
        run(helper, 0, false, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisTight1(final GameTestHelper helper) {
        run(helper, 1, false, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisTight2(final GameTestHelper helper) {
        run(helper, 2, false, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisTight3(final GameTestHelper helper) {
        run(helper, 3, false, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisTight4(final GameTestHelper helper) {
        run(helper, 4, false, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisTight5(final GameTestHelper helper) {
        run(helper, 5, false, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisWide0(final GameTestHelper helper) {
        run(helper, 0, false, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisWide1(final GameTestHelper helper) {
        run(helper, 1, false, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisWide2(final GameTestHelper helper) {
        run(helper, 2, false, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisWide3(final GameTestHelper helper) {
        run(helper, 3, false, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisWide4(final GameTestHelper helper) {
        run(helper, 4, false, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileAxisWide5(final GameTestHelper helper) {
        run(helper, 5, false, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerTight0(final GameTestHelper helper) {
        run(helper, 0, true, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerTight1(final GameTestHelper helper) {
        run(helper, 1, true, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerTight2(final GameTestHelper helper) {
        run(helper, 2, true, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerTight3(final GameTestHelper helper) {
        run(helper, 3, true, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerTight4(final GameTestHelper helper) {
        run(helper, 4, true, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerTight5(final GameTestHelper helper) {
        run(helper, 5, true, RADIUS_TIGHT);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerWide0(final GameTestHelper helper) {
        run(helper, 0, true, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerWide1(final GameTestHelper helper) {
        run(helper, 1, true, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerWide2(final GameTestHelper helper) {
        run(helper, 2, true, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerWide3(final GameTestHelper helper) {
        run(helper, 3, true, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerWide4(final GameTestHelper helper) {
        run(helper, 4, true, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 1400)
    public static void pileCornerWide5(final GameTestHelper helper) {
        run(helper, 5, true, RADIUS_WIDE);
    }

    @GameTest(template = "pile", timeoutTicks = 900)
    public static void orderOverhead(final GameTestHelper helper) {
        floor(helper);
        final List<BeadEntity> beads = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BeadEntity bead = helper.spawn(BeyondEntityTypes.BEAD.get(),
                    new BlockPos(2 + 3 * i, STAND, 3));
            bead.getRandom().setSeed(900L + i);
            beads.add(bead);
        }
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> grow(beads))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    for (BeadEntity bead : beads) {
                        BlockPos above = bead.blockPosition().above(6).east();
                        bead.setMovementTarget(Target.near(above.getCenter(), RADIUS_TIGHT));
                    }
                })
                .thenExecute(() -> overhead(helper, beads, "t0"))
                .thenIdle(200)
                .thenExecute(() -> overhead(helper, beads, "t200"))
                .thenIdle(400)
                .thenExecute(() -> overhead(helper, beads, "t600"))
                .thenSucceed();
    }

    private static void overhead(final GameTestHelper helper, final List<BeadEntity> beads,
                                 final String phase) {
        Vec3 origin = originOf(helper);
        for (BeadEntity bead : beads) {
            Vec3 spot = bead.position();
            LOGGER.debug(String.format(Locale.ROOT,
                    "[pileshape] overhead phase=%s id=%d pos=%.3f,%.3f,%.3f held=%b ground=%b climb=%b",
                    phase, bead.getId(), spot.x - origin.x, spot.y - origin.y, spot.z - origin.z,
                    bead.hasMovementTarget(), bead.onGround(), bead.isClimbing()));
        }
    }

    private static void floor(final GameTestHelper helper) {
        for (int x = 0; x < SPAN; x++) {
            for (int z = 0; z < SPAN; z++) {
                helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
            }
        }
    }

    private static void arena(final GameTestHelper helper, final boolean corner) {
        floor(helper);
        for (int y = STAND; y < STAND + WALL_HEIGHT; y++) {
            for (int z = 0; z < SPAN; z++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
            if (corner) {
                for (int x = 0; x < SPAN; x++) {
                    helper.setBlock(new BlockPos(x, y, WALL_Z), Blocks.STONE);
                }
            }
        }
    }

    private static void grow(final List<BeadEntity> beads) {
        for (BeadEntity bead : beads) {
            for (int i = 0; i < GROWTH_STEPS; i++) {
                bead.grow();
            }
            bead.refreshDimensions();
        }
    }

    private static void run(final GameTestHelper helper, final int seed, final boolean corner,
                            final double radius) {
        arena(helper, corner);
        final String mode = (corner ? "corner" : "axis")
                + (radius > RADIUS_TIGHT ? "-wide" : "-tight");
        final List<BeadEntity> beads = new ArrayList<>();
        int index = 0;
        outer:
        for (int gx : GROW_X) {
            for (int gz : GROW_Z) {
                if (index >= BEADS) {
                    break outer;
                }
                BeadEntity bead = helper.spawn(BeyondEntityTypes.BEAD.get(),
                        new BlockPos(gx, STAND, gz));
                bead.getRandom().setSeed(seed * 1000L + index);
                beads.add(bead);
                index++;
            }
        }

        final List<BlockPos> start = startCells(seed, corner);

        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> grow(beads))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> place(helper, beads, start, seed, mode))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> order(helper, beads, seed, mode, corner, radius))
                .thenIdle(ORDER_TICKS / 2)
                .thenExecute(() -> report(helper, beads, seed, mode, "half"))
                .thenIdle(ORDER_TICKS / 2)
                .thenExecute(() -> report(helper, beads, seed, mode, "final"))
                .thenSucceed();
    }

    private static List<BlockPos> startCells(final int seed, final boolean corner) {
        List<BlockPos> pool = new ArrayList<>();
        for (int x = 3; x <= 9; x++) {
            for (int z = corner ? 4 : 4; z <= 11; z++) {
                pool.add(new BlockPos(x, STAND, z));
            }
        }
        Collections.shuffle(pool, new Random(seed * 7919L + (corner ? 1 : 0)));
        return pool.subList(0, BEADS);
    }

    private static void place(final GameTestHelper helper, final List<BeadEntity> beads,
                              final List<BlockPos> start, final int seed, final String mode) {
        for (int i = 0; i < beads.size(); i++) {
            Vec3 spot = helper.absolutePos(start.get(i)).getBottomCenter();
            BeadEntity bead = beads.get(i);
            bead.moveTo(spot.x, spot.y, spot.z, bead.getYRot(), bead.getXRot());
            bead.setDeltaMovement(Vec3.ZERO);
        }
        int full = 0;
        for (BeadEntity bead : beads) {
            AABB hull = bead.getBoundingBox();
            if (hull.getXsize() >= HULL_FULL && hull.getYsize() >= HULL_FULL
                    && hull.getZsize() >= HULL_FULL) {
                full++;
            }
        }
        StringBuilder cells = new StringBuilder();
        for (BlockPos p : start) {
            if (cells.length() > 0) {
                cells.append(';');
            }
            cells.append(p.getX()).append(',').append(p.getZ());
        }
        LOGGER.debug(String.format(Locale.ROOT,
                "[pileshape] setup seed=%d mode=%s beads=%d fullhull=%d start=%s",
                seed, mode, beads.size(), full, cells));
    }

    private static void order(final GameTestHelper helper, final List<BeadEntity> beads,
                              final int seed, final String mode, final boolean corner,
                              final double radius) {
        BlockPos at = corner ? new BlockPos(WALL_X, STAND, WALL_Z) : new BlockPos(WALL_X, STAND, AIM_Z);
        Vec3 aim = helper.absolutePos(at).getCenter();
        for (BeadEntity bead : beads) {
            bead.setMovementTarget(Target.near(aim, radius));
        }
        Vec3 origin = originOf(helper);
        LOGGER.debug(String.format(Locale.ROOT,
                "[pileshape] order seed=%d mode=%s beads=%d aim=%.1f,%.1f,%.1f radius=%.7f",
                seed, mode, beads.size(), aim.x - origin.x, aim.y - origin.y,
                aim.z - origin.z, radius));
    }

    private static void report(final GameTestHelper helper, final List<BeadEntity> beads,
                               final int seed, final String mode, final String phase) {
        Set<Long> occupied = new HashSet<>();
        for (BeadEntity bead : beads) {
            BlockPos cell = cellOf(helper, bead);
            occupied.add(key(cell.getX(), cell.getY(), cell.getZ()));
        }

        int top = STAND;
        Set<Long> columns = new HashSet<>();
        StringBuilder cells = new StringBuilder();
        StringBuilder treads = new StringBuilder();

        for (BeadEntity bead : beads) {
            BlockPos cell = cellOf(helper, bead);
            int cx = cell.getX();
            int cy = cell.getY();
            int cz = cell.getZ();
            top = Math.max(top, cy);
            columns.add(key(cx, 0, cz));
            if (cells.length() > 0) {
                cells.append(';');
            }
            cells.append(cx).append(',').append(cy).append(',').append(cz);

            int[] rest = support(helper, occupied, cx, cy, cz);
            if (treads.length() > 0) {
                treads.append(';');
            }
            treads.append(rest[0]).append(',').append(rest[1]);

            LOGGER.debug(String.format(Locale.ROOT,
                    "[pileshape] bead seed=%d mode=%s phase=%s id=%d cell=%d,%d,%d"
                            + " tread=%d,%d ground=%b climb=%b hull=%.3f",
                    seed, mode, phase, bead.getId(), cx, cy, cz, rest[0], rest[1],
                    bead.onGround(), bead.isClimbing(), bead.getBoundingBox().getYsize()));
        }

        LOGGER.debug(String.format(Locale.ROOT,
                "[pileshape] summary seed=%d mode=%s phase=%s beads=%d height=%d columns=%d"
                        + " cells=%s treads=%s",
                seed, mode, phase, beads.size(), top - STAND + 1, columns.size(), cells, treads));
    }

    private static int[] support(final GameTestHelper helper, final Set<Long> occupied,
                                 final int cx, final int cy, final int cz) {
        if (cy <= STAND) {
            return new int[]{0, 0};
        }
        int[] best = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!rests(helper, occupied, cx + dx, cy - 1, cz + dz)) {
                    continue;
                }
                if (best == null || Math.abs(dx) + Math.abs(dz) < Math.abs(best[0]) + Math.abs(best[1])) {
                    best = new int[]{-dx, -dz};
                }
            }
        }
        return best == null ? new int[]{9, 9} : best;
    }

    private static boolean rests(final GameTestHelper helper, final Set<Long> occupied,
                                 final int cx, final int cy, final int cz) {
        if (occupied.contains(key(cx, cy, cz))) {
            return true;
        }
        Vec3 origin = originOf(helper);
        BlockPos world = BlockPos.containing(origin.x + cx + 0.5, origin.y + cy + 0.5,
                origin.z + cz + 0.5);
        return !helper.getLevel().getBlockState(world).isAir();
    }

    private static Vec3 originOf(final GameTestHelper helper) {
        return Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO));
    }

    private static BlockPos cellOf(final GameTestHelper helper, final BeadEntity bead) {
        Vec3 origin = originOf(helper);
        Vec3 spot = bead.position();
        return BlockPos.containing(spot.x - origin.x, spot.y - origin.y + 1.0E-4, spot.z - origin.z);
    }

    private static long key(final int x, final int y, final int z) {
        return ((long) (x + 64) << 40) | ((long) (y + 64) << 20) | (z + 64);
    }
}
