package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker + hook interface for blocks that self-mutate after a delay
 * ("transient" in the sense of "has a scheduled phase change", not in the
 * JVM-field sense).
 *
 * <p><b>Candidate consumers</b> (none migrated yet — see
 * {@code IMPLEMENTATION_PLAN.md §2.5}):
 * <ul>
 *   <li>{@code EctoplasmBlock} — fades and disappears after N ticks. Already
 *       has this behaviour inlined; refactor to consume this interface for
 *       consistency.</li>
 *   <li>Future {@code MoltenBismuthBlock} — on air exposure, transitions to
 *       {@code BrittleMetalBlock} after N ticks. On adjacent ice, transitions
 *       to {@code CrystallineBismuthBlock} instead.</li>
 *   <li>Future {@code WitheringGrowthBlock} — if Life Itself's trail is ever
 *       implemented as persistent growth that later wilts.</li>
 *   <li>Future {@code CorruptedBlock} — decay stages in The Beyond.</li>
 * </ul>
 *
 * <p><b>Intentionally sparse</b>: two methods and no default implementation
 * body. The scheduling helper ({@code DelayedPhaseTicker} mentioned in the
 * plan) is NOT added yet because it would require choosing between
 * {@code ServerLevel#scheduleTick}, a block-entity ticker, and a custom
 * attachment-based scheduler — each has trade-offs. That decision belongs
 * to the first real migration (probably the Bismuth sprint), at which point
 * the chosen implementation becomes the blessed path and can be documented
 * here.
 *
 * <p><b>Contract</b>:
 * <ul>
 *   <li>{@link #getLifetimeTicks} must be deterministic given the same
 *       state. Random variation should go into the block via
 *       {@code randomTick}, not here.</li>
 *   <li>{@link #getNext} returns the state to replace with. Returning
 *       {@code null} means "remove the block" (set to
 *       {@code Blocks.AIR.defaultBlockState()} at the call site).</li>
 *   <li>Implementations must not have side effects in {@link #getNext}
 *       other than returning the new state — callers may invoke this
 *       speculatively (e.g. to show a preview or run a gametest assertion).</li>
 * </ul>
 */
public interface TransientBlock {

    /**
     * @return the number of server ticks before this block self-mutates.
     * Return {@code Integer.MAX_VALUE} to mean "never on a schedule" — some
     * blocks transition on environmental triggers (ice adjacency) rather
     * than time, but still want to share the interface for uniformity.
     */
    int getLifetimeTicks(BlockState state);

    /**
     * Compute the replacement state.
     *
     * @param current the current state (may or may not still be the block
     *                placed, since randomTick variants exist).
     * @param level   the world, so implementations can check environment
     *                (adjacent ice, exposure to sky, etc.).
     * @param pos     the position. Read-only use — do not mutate from here.
     * @return the state to replace with, or {@code null} for "remove".
     */
    BlockState getNext(BlockState current, ServerLevel level, BlockPos pos);
}
