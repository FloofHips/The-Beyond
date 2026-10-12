package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.api.worldgen.SanctionedWrite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.concurrent.atomic.AtomicBoolean;

/** Drops recorded feature blocks the guard left unsupported, whose canSurvive now fails, cascading up. */
public final class FloatingFeatureGuard {
    private static final ThreadLocal<LongArrayList> RECORDED = ThreadLocal.withInitial(LongArrayList::new);
    private static final AtomicBoolean loggedFirstSweep = new AtomicBoolean(false);

    private FloatingFeatureGuard() {}

    /** Record a placed decoration block (from the setBlock mixin, only while the feature-guard is armed). */
    public static void record(long packedPos) { RECORDED.get().add(packedPos); }

    public static void reset() { RECORDED.get().clear(); }

    /** After decoration: drop any recorded block that can no longer survive where it stands, cascading upward. */
    public static void sweep(WorldGenLevel level) {
        LongArrayList rec = RECORDED.get();
        if (rec.isEmpty()) return;
        LongOpenHashSet live = new LongOpenHashSet(rec);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue(rec.size());
        for (int i = 0, n = rec.size(); i < n; i++) queue.enqueue(rec.getLong(i));
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int removed = 0;
        while (!queue.isEmpty()) {
            long p = queue.dequeueLong();
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
                // removing this may unsupport a recorded neighbour, so they are checked again
                requeue(live, queue, BlockPos.asLong(px, py + 1, pz));
                requeue(live, queue, BlockPos.asLong(px, py - 1, pz));
                requeue(live, queue, BlockPos.asLong(px + 1, py, pz));
                requeue(live, queue, BlockPos.asLong(px - 1, py, pz));
                requeue(live, queue, BlockPos.asLong(px, py, pz + 1));
                requeue(live, queue, BlockPos.asLong(px, py, pz - 1));
            }
        }
        if (FeatureGuard.debugLogs && removed > 0 && loggedFirstSweep.compareAndSet(false, true)) {
            TheBeyond.LOGGER.debug("[FloatingFeature] removed {} base-less feature block(s) near a carve structure", removed);
        }
        rec.clear();
    }

    private static void requeue(LongOpenHashSet live, LongArrayFIFOQueue queue, long p) {
        if (live.contains(p)) queue.enqueue(p);
    }
}
