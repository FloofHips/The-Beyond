package com.thebeyond.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Reusable primitive for breadth-first signal propagation through a block
 * network, capped by a per-call step budget and (optionally) a per-tick
 * throttle.
 *
 * <p><b>Relationship to the existing
 * {@link com.thebeyond.util.IMagneticReceiver} pattern</b>: the Polar
 * Antennae network already uses a callback-style chain where each block
 * receiving a signal decides what to do in its own {@code receiveSignal}
 * implementation and schedules the next tick. That pattern works well for
 * magnetic semantics (stochastic branching, per-block cooldown/stability)
 * and should be <b>left alone</b>. {@code SignalPropagator} is meant for
 * <i>new</i> cases that are purely topological (walk the network, emit a
 * side effect at each visited position) where per-receiver behaviour is
 * not needed — treating it as a replacement for {@code IMagneticReceiver}
 * would strip the magnetic-specific branching logic.
 *
 * <p><b>Intended new consumers</b>:
 * <ul>
 *   <li>Bismuth freeze — when ice contacts a Bismuth pool, spread the
 *       freeze across the {@code #the_beyond:molten_bismuth} tag. Budget
 *       prevents an O(pool-size) tick spike. Purely topological; no
 *       per-block decision.</li>
 *   <li>Migration Storm influence radius — radial scan (not BFS) for the
 *       Lanterns to pick up the storm's northward heading. See
 *       {@link #radial} for that variant.</li>
 *   <li>Perka Stalk extension — walks segments in a cardinal direction,
 *       re-branching on missed hits. Uses {@link #propagate} but the
 *       visitor carries the probabilistic branching.</li>
 *   <li>Legacy Grove excavation tiles — scan a radial neighbourhood for
 *       remaining loot, used by the "you dug too shallow" hint.</li>
 *   <li>Endermen corruption crosstalk in The Beyond (future) — same
 *       BFS semantics over entity-tag rather than block-tag; wrap the
 *       entity case as a separate helper when needed, reusing the step
 *       budget concept here.</li>
 * </ul>
 *
 * <p><b>Design notes</b>:
 * <ul>
 *   <li><b>Synchronous budget</b>: a single {@link #propagate} call walks
 *       up to {@code maxSteps} neighbours and returns. For multi-tick
 *       spread, the caller re-invokes from the frontier persisted
 *       elsewhere (e.g. {@link com.thebeyond.common.activation.BeyondActivation}).
 *       We deliberately don't take ownership of scheduling — the caller
 *       knows best when to resume (block tick, event, etc.).</li>
 *   <li><b>No mutation</b>: this class visits, it doesn't modify. The
 *       visitor callback is free to place/replace blocks, emit particles,
 *       mark activation state, or no-op. Separating walk from mutation
 *       keeps tests simple (pass a recording visitor, assert the walk
 *       order).</li>
 *   <li><b>26-neighbourhood vs 6</b>: use 6 (face-adjacent) unless the
 *       caller explicitly wants diagonals. Polar Antennae are placed on a
 *       regular lattice, so 6 is natural; Bismuth pools are flat, so 6 is
 *       natural. Diagonal BFS can be added if a real case shows up —
 *       don't preemptively complicate the API.</li>
 * </ul>
 *
 * <p><b>Not wired yet</b>: this is pure scaffolding per
 * {@code IMPLEMENTATION_PLAN.md §2.2}. Adding it costs zero — no existing
 * code calls {@code propagate} or {@code radial}. The moment
 * {@code PolarAntennaBlock} (or similar) wants chain behaviour, the
 * primitive is ready.
 *
 * <p><b>Testability</b>: because this is a pure function over a
 * {@link Level} read, tests can use a mock level or a fixture with a fixed
 * set of block positions. No Minecraft server required — unit test in
 * {@code src/test/java/com/thebeyond/common/util/SignalPropagatorTest.java}
 * whenever the primitive gets its first real use.
 */
public final class SignalPropagator {

    private SignalPropagator() {}

    /** 6-neighbourhood offsets. */
    private static final int[][] FACE_OFFSETS = {
            { 1, 0, 0}, {-1, 0, 0},
            { 0, 1, 0}, { 0,-1, 0},
            { 0, 0, 1}, { 0, 0,-1}
    };

    /**
     * Walks a signal BFS through blocks whose {@link BlockState} matches
     * {@code conductorTag}, starting from {@code origin}.
     *
     * @param level         the world, used to read block states. Reads only —
     *                      the visitor does any writing.
     * @param origin        seed position. {@code origin} itself is passed to
     *                      the visitor as step 0 regardless of whether it
     *                      matches the tag (callers often want to invoke
     *                      side-effects at the source).
     * @param conductorTag  the block tag that forms the network. Only blocks
     *                      matching this tag are traversed further.
     * @param maxSteps      hard cap on positions visited. Prevents runaway
     *                      spread on large pools.
     * @param visitor       called for each visited position, in BFS order,
     *                      with the {@code stepDistance} from the origin
     *                      (useful for particle falloff, sound pitch, etc.).
     *                      Safe to mutate the world from the visitor; visited
     *                      set is computed before the call so self-feeding
     *                      replacements don't cause infinite loops.
     * @return the number of positions visited (including the origin).
     */
    public static int propagate(Level level, BlockPos origin, TagKey<net.minecraft.world.level.block.Block> conductorTag,
                                 int maxSteps, BiConsumer<BlockPos, Integer> visitor) {
        if (maxSteps <= 0) return 0;

        Set<Long> visited = new HashSet<>();
        Deque<PosAndStep> frontier = new ArrayDeque<>();

        visited.add(origin.asLong());
        frontier.add(new PosAndStep(origin.immutable(), 0));

        int visitedCount = 0;
        while (!frontier.isEmpty() && visitedCount < maxSteps) {
            PosAndStep current = frontier.pollFirst();
            visitor.accept(current.pos, current.step);
            visitedCount++;

            // Expand neighbours only if the current node conducts. The origin
            // is always visited (step 0) but we gate its NEIGHBOURS on tag
            // membership so a non-conducting origin still triggers its own
            // side effects without contaminating the walk.
            BlockState currentState = level.getBlockState(current.pos);
            if (current.step > 0 && !currentState.is(conductorTag)) continue;
            if (current.step == 0 && !currentState.is(conductorTag)) {
                // Origin is a non-conductor: check neighbours anyway, a common
                // pattern is "player-activated trigger block propagates through
                // adjacent conductors".
            }

            for (int[] off : FACE_OFFSETS) {
                BlockPos n = current.pos.offset(off[0], off[1], off[2]);
                long key = n.asLong();
                if (!visited.add(key)) continue;
                BlockState neighbourState = level.getBlockState(n);
                if (!neighbourState.is(conductorTag)) continue;
                frontier.addLast(new PosAndStep(n.immutable(), current.step + 1));
            }
        }
        return visitedCount;
    }

    /**
     * Radial variant: walks every block within {@code radius} Chebyshev
     * distance of {@code origin}, regardless of tag. Useful for non-network
     * signals (Enadrake scream, Migration Storm influence radius).
     *
     * @param level         the world.
     * @param origin        centre of the radial scan.
     * @param radius        Chebyshev distance. A {@code radius} of 0 visits
     *                      only the origin; a radius of 1 visits 27 cells.
     * @param visitor       called for each cell in the radial shell order.
     *                      Receives the position and the Chebyshev distance
     *                      from the origin.
     */
    public static void radial(Level level, BlockPos origin, int radius,
                               BiConsumer<BlockPos, Integer> visitor) {
        if (radius < 0) return;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        // shell-only at distance r: skip cells closer than r
                        int cheby = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                        if (cheby != r) continue;
                        mut.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        visitor.accept(mut.immutable(), r);
                    }
                }
            }
        }
    }

    /**
     * Convenience alias for the common Polar-chain use case — propagates
     * across blocks in {@link BlockTags#AIR} placeholder. This signature is
     * intentionally kept as a TODO: when {@code BeyondTags} adds
     * {@code POLAR_ANTENNA}, swap the default here.
     */
    private record PosAndStep(BlockPos pos, int step) {}
}
