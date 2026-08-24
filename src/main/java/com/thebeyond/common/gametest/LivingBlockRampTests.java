package com.thebeyond.common.gametest;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.entity.BeadEntity;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.entity.util.livingblock.LivingBlockCollisionHandler;
import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;

@GameTestHolder(TheBeyond.MODID)
@PrefixGameTestTemplate(false)
public final class LivingBlockRampTests {

    private static final int FLOOR = 1;
    private static final int FLOOR_SPAN = 9;
    private static final int SPAWN_XZ = 4;
    private static final int SETTLE_TICKS = 3;
    private static final int GROWTH_STEPS = 400;
    private static final double PUSH = 0.1;
    private static final int PUSH_TICKS = 40;
    private static final double MIN_ADVANCE = 1.0;
    private static final double MIN_RISE = 0.25;
    private static final double TILT = 45.0;
    private static final double GRAVITY = 0.08;
    private static final double MIN_FIXTURE_TILT_DEG = 15.0;
    private static final double MIN_FIXTURE_HULL_HEIGHT = 0.7;
    private static final double CLIMB_START_INSET = 0.35;
    private static final int CLIMB_DROP_TICKS = 14;
    private static final double STILL_EPSILON = 1.0E-4;
    private static final double DESCEND_START_LIFT = 0.01;
    private static final double JITTER_START_OFFSET = 0.6;
    private static final int MAX_VERTICAL_REVERSALS = 4;
    private static final double PROBE_START_FRACTION = 0.25;
    private static final int PROBE_DROP_TICKS = 12;
    private static final int PROBE_MAX_TICKS = 20;
    private static final double PROBE_STEP_HEIGHT = 0.6;
    private static final double PROBE_CONTACT_INFLATE = 0.2;

    private LivingBlockRampTests() {
    }

    private static void floor(final GameTestHelper helper) {
        for (int x = 0; x < FLOOR_SPAN; x++) {
            for (int z = 0; z < FLOOR_SPAN; z++) {
                helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
            }
        }
    }

    private static void growToCeiling(final LivingBlock bead) {
        if (bead instanceof BeadEntity grown) {
            for (int i = 0; i < GROWTH_STEPS; i++) {
                grown.grow();
            }
        }
        bead.refreshDimensions();
    }


    private static boolean tiltedEnough(final GameTestHelper helper, final LivingBlock bead) {
        AABB hull = bead.getBoundingBox();
        if (bead.tiltDegrees() > MIN_FIXTURE_TILT_DEG && hull.getYsize() > MIN_FIXTURE_HULL_HEIGHT) {
            return true;
        }
        helper.fail(String.format(Locale.ROOT,
                "invalid setup: tilt %.1f deg, hull %.4f tall, settled=%s",
                bead.tiltDegrees(), hull.getYsize(), bead.isOrientationSettled()));
        return false;
    }

    @GameTest(template = "ramp", timeoutTicks = 200)
    public static void playerClimbsTiltedFace(final GameTestHelper helper) {
        floor(helper);
        LivingBlock bead = helper.spawn(BeyondEntityTypes.BEAD.get(),
                new BlockPos(SPAWN_XZ, FLOOR + 1, SPAWN_XZ));
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> growToCeiling(bead))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    bead.getRotation().identity().rotateZ((float) Math.toRadians(TILT));
                    bead.refreshDimensions();
                })
                .thenExecute(() -> {
                    if (!tiltedEnough(helper, bead)) {
                        return;
                    }
                    AABB hull = bead.getBoundingBox();

                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setPos(hull.maxX - CLIMB_START_INSET, hull.maxY + 0.5, hull.getCenter().z);
                    player.setDeltaMovement(Vec3.ZERO);
                    for (int drop = 0; drop < CLIMB_DROP_TICKS; drop++) {
                        player.move(MoverType.SELF, new Vec3(0.0, -GRAVITY, 0.0));
                    }
                    double landed = player.getY();

                    double beforeY = player.getY();
                    double beforeX = player.getX();
                    for (int i = 0; i < PUSH_TICKS; i++) {
                        player.move(MoverType.SELF, new Vec3(-PUSH, -GRAVITY, 0.0));
                    }
                    double advanced = beforeX - player.getX();
                    double rose = player.getY() - beforeY;
                    double needRise = MIN_RISE * hull.getYsize();
                    boolean stalled = Math.abs(rose) < STILL_EPSILON && advanced < MIN_ADVANCE;
                    if (advanced < MIN_ADVANCE || stalled) {
                        helper.fail(String.format(Locale.ROOT,
                                "moved %.5f and rose %.5f against a face at %.0f deg, minimum %.2f and %.2f"
                                        + " | landed=%.4f ground=%s horiz=%s step=%.2f feet=%.4f body=%.4f..%.4f",
                                advanced, rose, TILT, MIN_ADVANCE, needRise, landed,
                                player.onGround(), player.horizontalCollision, player.maxUpStep(),
                                player.getY(), hull.minY, hull.maxY));
                        return;
                    }
                    helper.succeed();
                }).thenSucceed();
    }

    @GameTest(template = "ramp", timeoutTicks = 200)
    public static void playerDescendsTiltedFace(final GameTestHelper helper) {
        floor(helper);
        LivingBlock bead = helper.spawn(BeyondEntityTypes.BEAD.get(),
                new BlockPos(SPAWN_XZ, FLOOR + 1, SPAWN_XZ));
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> growToCeiling(bead))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    bead.getRotation().identity().rotateZ((float) Math.toRadians(TILT));
                    bead.refreshDimensions();
                })
                .thenExecute(() -> {
                    if (!tiltedEnough(helper, bead)) {
                        return;
                    }
                    AABB hull = bead.getBoundingBox();

                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setPos(hull.getCenter().x, hull.maxY + DESCEND_START_LIFT, hull.getCenter().z);
                    player.setDeltaMovement(Vec3.ZERO);

                    double beforeY = player.getY();
                    double beforeX = player.getX();
                    for (int i = 0; i < PUSH_TICKS; i++) {
                        player.move(MoverType.SELF, new Vec3(PUSH, -GRAVITY, 0.0));
                    }
                    double advanced = player.getX() - beforeX;
                    double dropped = beforeY - player.getY();

                    if (advanced < MIN_ADVANCE) {
                        helper.fail(String.format(Locale.ROOT,
                                "moved only %.5f going down the face, and dropped %.5f", advanced, dropped));
                        return;
                    }
                    helper.succeed();
                }).thenSucceed();
    }

    @GameTest(template = "ramp", timeoutTicks = 200)
    public static void climbDoesNotJitter(final GameTestHelper helper) {
        floor(helper);
        LivingBlock bead = helper.spawn(BeyondEntityTypes.BEAD.get(),
                new BlockPos(SPAWN_XZ, FLOOR + 1, SPAWN_XZ));
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> growToCeiling(bead))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    bead.getRotation().identity().rotateZ((float) Math.toRadians(TILT));
                    bead.refreshDimensions();
                })
                .thenExecute(() -> {
                    if (!tiltedEnough(helper, bead)) {
                        return;
                    }
                    AABB hull = bead.getBoundingBox();

                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setPos(hull.minX - JITTER_START_OFFSET, hull.minY, hull.getCenter().z);
                    player.setDeltaMovement(Vec3.ZERO);

                    int reversals = 0;
                    double last = player.getY();
                    int sign = 0;
                    for (int i = 0; i < PUSH_TICKS; i++) {
                        player.move(MoverType.SELF, new Vec3(PUSH, -GRAVITY, 0.0));
                        double delta = player.getY() - last;
                        last = player.getY();
                        int now = delta > STILL_EPSILON ? 1 : delta < -STILL_EPSILON ? -1 : 0;
                        if (now != 0 && sign != 0 && now != sign) {
                            reversals++;
                        }
                        if (now != 0) {
                            sign = now;
                        }
                    }
                    if (reversals > MAX_VERTICAL_REVERSALS) {
                        helper.fail(String.format(Locale.ROOT,
                                "%d vertical reversals in %d ticks climbing the face", reversals, PUSH_TICKS));
                        return;
                    }
                    helper.succeed();
                }).thenSucceed();
    }

    @GameTest(template = "ramp", timeoutTicks = 200, required = false)
    public static void probeStepGates(final GameTestHelper helper) {
        floor(helper);
        LivingBlock bead = helper.spawn(BeyondEntityTypes.BEAD.get(),
                new BlockPos(SPAWN_XZ, FLOOR + 1, SPAWN_XZ));
        helper.startSequence()
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> growToCeiling(bead))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    bead.getRotation().identity().rotateZ((float) Math.toRadians(TILT));
                    bead.refreshDimensions();
                })
                .thenExecute(() -> {
                    AABB hull = bead.getBoundingBox();
                    Player p = helper.makeMockPlayer(GameType.SURVIVAL);
                    p.setPos(hull.minX + hull.getXsize() * PROBE_START_FRACTION, hull.maxY + 0.5,
                            hull.getCenter().z);
                    p.setDeltaMovement(Vec3.ZERO);
                    for (int drop = 0; drop < PROBE_DROP_TICKS; drop++) {
                        p.move(MoverType.SELF, new Vec3(0.0, -GRAVITY, 0.0));
                    }
                    int ticks = 0;
                    while (ticks < PROBE_MAX_TICKS && !p.horizontalCollision) {
                        p.move(MoverType.SELF, new Vec3(PUSH, -GRAVITY, 0.0));
                        ticks++;
                    }
                    double touchedAt = p.getX();
                    boolean riseFree = p.level().noCollision(p,
                            p.getBoundingBox().move(0.0, PROBE_STEP_HEIGHT, 0.0));
                    int pieces = p.level().getEntityCollisions(p,
                            p.getBoundingBox().inflate(PROBE_CONTACT_INFLATE)).size();

                    Vec3 slide = LivingBlockCollisionHandler.slideOnBody(
                            p, new Vec3(PUSH, 0.0, 0.0), Vec3.ZERO);
                    boolean destinationFree = slide != null
                            && p.level().noCollision(p, p.getBoundingBox().move(slide));

                    helper.fail(String.format(Locale.ROOT,
                            "PROBE ticks=%d riseFree=%s pieces=%d touchedAt=%.4f slide=%s destinationFree=%s"
                                    + " hull=%.4f..%.4f tilt=%.1f ground=%s horiz=%s step=%.2f",
                            ticks, riseFree, pieces, touchedAt,
                            slide == null ? "NULL" : String.format(Locale.ROOT, "%.4f,%.4f,%.4f",
                                    slide.x, slide.y, slide.z),
                            destinationFree, hull.minY, hull.maxY, bead.tiltDegrees(),
                            p.onGround(), p.horizontalCollision, p.maxUpStep()));
                })
                .thenSucceed();
    }
}
