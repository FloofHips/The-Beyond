package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.api.worldgen.SanctionedWrite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Drops feature parts the {@link com.thebeyond.api.worldgen.FeatureGuard} severed — any recorded block whose own
 *  {@code canSurvive} now fails (or an unsupported falling fruit), cascading up. Generic, no per-mod block list. */
public final class FloatingFeatureGuard {
    private static final ThreadLocal<List<Long>> RECORDED = ThreadLocal.withInitial(ArrayList::new);
    private static final AtomicBoolean loggedFirstSweep = new AtomicBoolean(false);

    private FloatingFeatureGuard() {}

    /** Record a placed decoration block (from the setBlock mixin, only while the feature-guard is armed). */
    public static void record(long packedPos) { RECORDED.get().add(packedPos); }

    public static void reset() { RECORDED.get().clear(); }

    /** After decoration: drop any recorded block that can no longer survive where it stands, cascading upward. */
    public static void sweep(WorldGenLevel level) {
        List<Long> rec = RECORDED.get();
        if (rec.isEmpty()) return;
        Set<Long> live = new HashSet<>(rec);
        ArrayDeque<Long> queue = new ArrayDeque<>(rec);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int removed = 0;
        while (!queue.isEmpty()) {
            long p = queue.poll();
            if (!live.contains(p)) continue;
            int px = BlockPos.getX(p), py = BlockPos.getY(p), pz = BlockPos.getZ(p);
            BlockState st = level.getBlockState(mp.set(px, py, pz));
            if (st.isAir()) { live.remove(p); continue; }
            boolean drop;
            if (st.getBlock() instanceof FallingBlock) {
                drop = level.getBlockState(mp.set(px, py - 1, pz)).isAir();  // a fruit whose stem was removed
            } else {
                drop = !st.canSurvive(level, mp.set(px, py, pz));            // its own support-rule now fails
            }
            if (drop) {
                SanctionedWrite.enter();
                try { level.setBlock(mp.set(px, py, pz), Blocks.AIR.defaultBlockState(), 2); }
                finally { SanctionedWrite.exit(); }
                live.remove(p);
                removed++;
                // Removing this may unsupport a recorded neighbour (esp. the one above) — recheck them.
                for (long np : new long[]{
                        BlockPos.asLong(px, py + 1, pz), BlockPos.asLong(px, py - 1, pz),
                        BlockPos.asLong(px + 1, py, pz), BlockPos.asLong(px - 1, py, pz),
                        BlockPos.asLong(px, py, pz + 1), BlockPos.asLong(px, py, pz - 1)}) {
                    if (live.contains(np)) queue.add(np);
                }
            }
        }
        if (removed > 0 && loggedFirstSweep.compareAndSet(false, true)) {
            TheBeyond.LOGGER.info("[FloatingFeature] removed {} base-less feature block(s) near a carve structure", removed);
        }
        rec.clear();
    }
}
