package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** From the corruption's edge a trail runs down an exposed face and on downhill over the ground, past the halo. */
final class OrganicTrail {
    static final boolean ON = !"off".equals(System.getProperty("beyond.sub.org.trail", "on"));
    // one chunk around the origin's own is all a pass reads, so a trail plus its widened head must fit in 16 blocks
    static final int MAX = Math.clamp(Integer.getInteger("beyond.sub.org.trail.max", 15), 1, 15);
    static final long NONE = Long.MIN_VALUE;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int FACE = 0, FLOOR = 1;

    private OrganicTrail() {}

    /** Whether a kept entry is still under the corruption: its own block, or for a trail the halo block it started from. */
    static boolean held(OrganicSurface.Kept kept, int i, BlockPos at, Predicate<BlockPos> blockBlight) {
        long o = kept.origin(i);
        return blockBlight.test(o == NONE ? at : BlockPos.of(o));
    }

    static void due(ServerLevel level, LevelChunk ch) {
        if (!ON || !OrganicExperiment.ON) return;
        OrganicSurface.Kept kept = ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
        if (kept != null && !kept.trails) OrganicLevelState.of(level).trailsDue.add(ch.getPos().toLong());
    }

    /** Runs the pass of each due chunk whose neighbours are all loaded, so every block it reads is final. */
    static void advance(ServerLevel level) {
        Set<Long> due = OrganicLevelState.of(level).trailsDue;
        if (due.isEmpty()) return;
        int done = 0;
        for (Iterator<Long> it = due.iterator(); it.hasNext() && done < 32; ) {
            long key = it.next();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
            if (c == null) {
                it.remove();
                continue;
            }
            boolean ready = true;
            for (int dx = -1; dx <= 1 && ready; dx++) {
                for (int dz = -1; dz <= 1 && ready; dz++) ready = level.getChunkSource().getChunkNow(cx + dx, cz + dz) != null;
            }
            if (!ready) continue;
            it.remove();
            pass(level, c);
            done++;
        }
    }

    static int pass(ServerLevel level, LevelChunk c) {
        OrganicSurface.Kept kept = c.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
        if (kept == null || kept.trails) return 0;
        kept.trails = true;
        c.setUnsaved(true);
        BlockState corrupted = OrganicSurface.corrupted();
        Halo halo = new Halo(level);
        ChunkPos cp = c.getPos();
        int minY = c.getMinBuildHeight();
        // the lowest halo block of each column and side that starts a trail
        Map<String, long[]> starts = new TreeMap<>();
        for (int i = 0; i < kept.pos.length; i++) {
            if (kept.origin(i) != NONE || OrganicSurface.plant(kept.palette.get(kept.state[i]))) continue;
            int p = kept.pos[i];
            BlockPos at = new BlockPos(cp.getMinBlockX() + (p & 15), minY + (p >>> 8), cp.getMinBlockZ() + (p >> 4 & 15));
            if (level.getBlockState(at) != corrupted) continue;
            BlockPos b = at.below();
            if (!ground(level, b) || halo.is(b)) continue;
            for (int s = 0; s < SIDES.length; s++) {
                if (!open(level, at.offset(SIDES[s][0], 0, SIDES[s][1])) || !open(level, b.offset(SIDES[s][0], 0, SIDES[s][1]))) continue;
                String k = String.format(Locale.ROOT, "%d %d %d", at.getX(), at.getZ(), s);
                long[] old = starts.get(k);
                if (old == null || at.getY() < BlockPos.getY(old[0])) starts.put(k, new long[]{at.asLong(), s});
            }
        }
        int trails = 0, painted = 0;
        Map<Long, List<long[]>> byChunk = new HashMap<>();
        for (long[] st : starts.values()) {
            BlockPos origin = BlockPos.of(st[0]);
            List<BlockPos> paint = walk(level, origin.below(), SIDES[(int) st[1]], key(origin, (int) st[1]));
            long from = origin.asLong();
            boolean any = false;
            for (BlockPos q : paint) {
                BlockState s = level.getBlockState(q);
                if (s == corrupted || !OrganicSurface.ground(s) || !ground(level, q)) continue;
                if (OrganicSurface.inStructure(level, q)) continue;
                byChunk.computeIfAbsent(ChunkPos.asLong(q.getX() >> 4, q.getZ() >> 4), x -> new ArrayList<>())
                        .add(new long[]{q.asLong(), from});
                any = true;
            }
            if (any) trails++;
        }
        for (Map.Entry<Long, List<long[]>> e : byChunk.entrySet()) {
            LevelChunk ch = level.getChunk(ChunkPos.getX(e.getKey()), ChunkPos.getZ(e.getKey()));
            OrganicSurface.Kept k = ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
            if (k == null) {
                k = new OrganicSurface.Kept();
                // a chunk with no halo of its own only holds trails, so it needs no pass of its own
                k.trails = true;
            }
            int cmin = ch.getMinBuildHeight();
            Set<Long> seen = new HashSet<>();
            for (long[] w : e.getValue()) {
                if (!seen.add(w[0])) continue;
                BlockPos q = BlockPos.of(w[0]);
                BlockState had = ch.getBlockState(q);
                if (had == corrupted) continue;
                k.add((q.getY() - cmin) << 8 | (q.getZ() & 15) << 4 | (q.getX() & 15), had, w[1]);
                ch.setBlockState(q, corrupted, false);
                painted++;
            }
            ch.setData(BeyondAttachments.ORG_SURFACE, k);
            ch.setUnsaved(true);
        }
        if (OrganicWorld.TRACE && (trails > 0 || !starts.isEmpty())) {
            TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG trail chunk %d %d | starts %d trails %d blocks %d", cp.x, cp.z,
                    starts.size(), trails, painted));
        }
        return painted;
    }

    /** Down the face while it stays exposed, then downhill on the ground: drop, then step, then flat (at most three ahead). */
    static List<BlockPos> walk(ServerLevel level, BlockPos f0, int[] a, long key) {
        List<Object[]> steps = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        BlockPos f = f0;
        int mode = FACE, flat = 0;
        int[] d = a;
        while (steps.size() < MAX && seen.add(f)) {
            steps.add(new Object[]{f, mode, d});
            if (mode == FACE) {
                BlockPos b = f.below();
                if (ground(level, b) && open(level, b.offset(d[0], 0, d[1]))) {
                    f = b;
                    continue;
                }
                BlockPos g = f.offset(d[0], -1, d[1]);
                if (ground(level, g) && open(level, g.above()) && !seen.contains(g)) {
                    f = g;
                    mode = FLOOR;
                    flat = 0;
                    continue;
                }
                break;
            }
            int[][] order = SIDES.clone();
            final int[] dir = d;
            final BlockPos here = f;
            Arrays.sort(order, (u, v) -> {
                int c = Integer.compare(-(u[0] * dir[0] + u[1] * dir[1]), -(v[0] * dir[0] + v[1] * dir[1]));
                return c != 0 ? c : Double.compare(random(key, here, u), random(key, here, v));
            });
            Object[] pick = null;
            int best = Integer.MAX_VALUE;
            for (int[] dd : order) {
                if (dd[0] == -d[0] && dd[1] == -d[1]) continue;
                BlockPos h = f.offset(dd[0], 0, dd[1]);
                Object[] op = null;
                if (open(level, h) && open(level, h.below())) {
                    BlockPos b = f.below();
                    if (ground(level, b) && open(level, b.offset(dd[0], 0, dd[1]))) op = new Object[]{0, b, FACE, dd};
                } else if (open(level, h) && ground(level, h.below())) {
                    op = new Object[]{1, h.below(), FLOOR, dd};
                } else if (ground(level, h) && open(level, h.above()) && dd[0] == d[0] && dd[1] == d[1] && flat < 3) {
                    op = new Object[]{2, h, FLOOR, dd};
                }
                if (op == null || seen.contains((BlockPos) op[1])) continue;
                if ((int) op[0] < best) {
                    best = (int) op[0];
                    pick = op;
                }
            }
            if (pick == null) break;
            f = (BlockPos) pick[1];
            mode = (int) pick[2];
            d = (int[]) pick[3];
            flat = best == 2 ? flat + 1 : 0;
        }
        Set<BlockPos> paint = new LinkedHashSet<>();
        for (Object[] s : steps) paint.add((BlockPos) s[0]);
        // a wide head (three, then two, then two) that narrows to one, and a small pool where it stops on the ground
        for (int i = 0; i < Math.min(3, steps.size()); i++) {
            if ((int) steps.get(i)[1] != FACE) continue;
            BlockPos p = (BlockPos) steps.get(i)[0];
            int[] dd = (int[]) steps.get(i)[2];
            int[][] perp = {{-dd[1], dd[0]}, {dd[1], -dd[0]}};
            int[][] sides = i == 0 ? perp : new int[][]{perp[random(key, p, dd) < 0.5 ? 0 : 1]};
            for (int[] q : sides) {
                BlockPos r = p.offset(q[0], 0, q[1]);
                if (ground(level, r) && open(level, r.offset(dd[0], 0, dd[1]))) paint.add(r);
            }
        }
        if (!steps.isEmpty() && (int) steps.get(steps.size() - 1)[1] == FLOOR) {
            BlockPos p = (BlockPos) steps.get(steps.size() - 1)[0];
            int[][] order = SIDES.clone();
            Arrays.sort(order, (u, v) -> Double.compare(random(key ^ 0x706F6361L, p, u), random(key ^ 0x706F6361L, p, v)));
            for (int j = 0; j < 2; j++) {
                BlockPos r = p.offset(order[j][0], 0, order[j][1]);
                if (ground(level, r) && open(level, r.above())) paint.add(r);
            }
        }
        return new ArrayList<>(paint);
    }

    // air, and blocks that leave the face behind them exposed (plants, slabs, panes, fluids), but never the network's own
    static boolean open(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isAir() || (!OrganicExperiment.tissue(s) && !s.canOcclude());
    }

    static boolean ground(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !s.isAir() && s.canOcclude() && !OrganicExperiment.tissue(s) && !s.is(Blocks.BEDROCK);
    }

    private static long key(BlockPos origin, int side) {
        return origin.asLong() * 31 + side;
    }

    private static double random(long key, BlockPos p, int[] d) {
        long h = key ^ p.asLong() * 0x9E3779B97F4A7C15L ^ (d[0] + 3L) * 0xC2B2AE3D27D4EB4FL ^ (d[1] + 7L) * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return ((h ^ (h >>> 33)) >>> 11) * 0x1.0p-53;
    }

    /** The halo's own swaps around the pass, read once per chunk: a trail block never counts as halo for another start. */
    private static final class Halo {
        private final ServerLevel level;
        private final Map<Long, Set<Long>> byChunk = new HashMap<>();

        Halo(ServerLevel level) {
            this.level = level;
        }

        boolean is(BlockPos p) {
            long ck = ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            Set<Long> s = byChunk.get(ck);
            if (s == null) {
                s = new HashSet<>();
                LevelChunk ch = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                OrganicSurface.Kept k = ch == null ? null : ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
                if (k != null) {
                    int minY = ch.getMinBuildHeight();
                    for (int i = 0; i < k.pos.length; i++) {
                        if (k.origin(i) != NONE) continue;
                        int q = k.pos[i];
                        s.add(BlockPos.asLong((ch.getPos().x << 4) + (q & 15), minY + (q >>> 8), (ch.getPos().z << 4) + (q >> 4 & 15)));
                    }
                }
                byChunk.put(ck, s);
            }
            return s.contains(p.asLong()) && level.getBlockState(p) == OrganicSurface.corrupted();
        }
    }
}
