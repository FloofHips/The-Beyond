package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.worldgen.OrganicGrowth.Atrophy;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import static com.thebeyond.common.worldgen.OrganicWorld.DEAD;
import static com.thebeyond.common.worldgen.OrganicWorld.DIRS;
import static com.thebeyond.common.worldgen.OrganicWorld.RECEDE;
import static com.thebeyond.common.worldgen.OrganicWorld.TABLES;
import static com.thebeyond.common.worldgen.OrganicWorld.TRACE;
import static com.thebeyond.common.worldgen.OrganicWorld.WHOLE;
import static com.thebeyond.common.worldgen.OrganicWorld.WITHER;
import static com.thebeyond.common.worldgen.OrganicWorld.active;
import static com.thebeyond.common.worldgen.OrganicWorld.applying;
import static com.thebeyond.common.worldgen.OrganicWorld.box;
import static com.thebeyond.common.worldgen.OrganicWorld.dead;
import static com.thebeyond.common.worldgen.OrganicWorld.ensure;
import static com.thebeyond.common.worldgen.OrganicWorld.organism;
import static com.thebeyond.common.worldgen.OrganicWorld.ownerAt;
import static com.thebeyond.common.worldgen.OrganicWorld.refresh;
import static com.thebeyond.common.worldgen.OrganicWorld.set;

/** A bulb's wounds and death, the wither of a dead part, and the atrophy of the roots that fed it. */
public final class OrganicDeath {
    static final int ATROPHIED = -2;
    private static final int ATROPHY_TICKS = 3 * WITHER;

    private OrganicDeath() {}

    static final class Withering {
        final long start, cell;
        final int organ;
        final long[] pos;
        final int[] tick;
        int next;

        Withering(long start, long cell, int organ, long[] pos, int[] tick) {
            this.start = start;
            this.cell = cell;
            this.organ = organ;
            this.pos = pos;
            this.tick = tick;
        }
    }

    /** A bulb block left the world by any means but the organism's own edits (OrganicWorld.set): a hit on its organ. */
    public static void bulbRemoved(ServerLevel level, BlockPos pos) {
        if (applying || !active(level)) return;
        long[] at = ownerAt(level, pos);
        if (at == null) {
            if (TRACE) TheBeyond.LOGGER.info("ORG hit skipped {} | not in the tissue record", pos.toShortString());
            return;
        }
        OrganicLife.Data d = OrganicLife.Data.get(level);
        OrganicLife.Organism org = organism(level, at[0], d);
        int organ = (int) at[1];
        if (org == null || !ensure(level, org, organ)) {
            if (TRACE) TheBeyond.LOGGER.info("ORG hit skipped {} | cell {} organ {} | organism {} | table {}", pos.toShortString(),
                    at[0], organ, org != null, TABLES.containsKey(at[0]));
            return;
        }
        long now = level.getGameTime();
        OrganicLife.Hit hit = org.hit(organ, pos.asLong(), now, true);
        d.setDirty();
        if (!hit.taken()) {
            OrganicLife.Organ g = org.organ(organ);
            if (TRACE) TheBeyond.LOGGER.info("ORG hit refused {} | cell {} organ {} | blocks {} alive {} dead {} | has block {}",
                    pos.toShortString(), at[0], organ, g.size(), g.alive(), g.dead(), g.indexOf(pos.asLong()) >= 0);
            return;
        }
        if (TRACE) TheBeyond.LOGGER.info("ORG hit {} | cell {} organ {} | material {} | organ died {} | organism died {} | {}",
                pos.toShortString(), at[0], organ, hit.material(), hit.organDied(), hit.organismDied(), org.describe());
        if (hit.organismDied()) nucleusDied(level, org, organ, now);
        else if (hit.organDied()) {
            wither(level, org, organ, now);
            for (int o = 0; o < org.size(); o++) {
                if (org.nucleus(o)) refresh(level, org, o, now);
            }
        }
    }

    /** The nucleus died: SUBS_FIRST or a lone nucleus withers whole, SPLIT only its own part, CASCADE every sub not grafted. */
    private static void nucleusDied(ServerLevel level, OrganicLife.Organism org, int nucleus, long now) {
        if (!org.alive() || org.death == OrganicLife.Death.SUBS_FIRST) {
            wither(level, org, WHOLE, now);
            return;
        }
        if (org.death == OrganicLife.Death.SPLIT) {
            wither(level, org, nucleus, now);
            if (TRACE) TheBeyond.LOGGER.info("ORG nucleus died cell {} | rule split | {}", org.cell, org.describe());
            return;
        }
        Set<Integer> grafted = grafted(level, org);
        List<Integer> doomed = new ArrayList<>();
        for (int o = 0; o < org.size(); o++) {
            if (!org.nucleus(o) && !org.dead(o) && !grafted.contains(o)) doomed.add(o);
        }
        for (int o : doomed) org.kill(o);
        OrganicLife.Data.get(level).setDirty();
        if (!org.alive()) {
            wither(level, org, WHOLE, now);
            return;
        }
        Set<Integer> dead = DEAD.computeIfAbsent(org.cell, c -> ConcurrentHashMap.newKeySet());
        dead.add(nucleus);
        dead.addAll(doomed);
        wither(level, org, nucleus, now);
        for (int o : doomed) wither(level, org, o, now);
        if (TRACE) TheBeyond.LOGGER.info("ORG nucleus died cell {} | rule cascade | grafted subs {} | withered subs {}", org.cell,
                grafted, doomed);
    }

    /** Subs whose live tissue touches the live tissue of another organism in the loaded chunks, where roots fuse. */
    private static Set<Integer> grafted(ServerLevel level, OrganicLife.Organism org) {
        Set<Integer> out = new HashSet<>();
        float[] t = TABLES.get(org.cell);
        if (t == null) return out;
        int[] box = box(t);
        int cx0 = box[0], cx1 = box[1], cz0 = box[2], cz1 = box[3];
        Map<Long, Integer> mine = new HashMap<>();
        int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE;
        int bx1 = Integer.MIN_VALUE, by1 = Integer.MIN_VALUE, bz1 = Integer.MIN_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    if (!level.hasChunk(cx, cz)) continue;
                    LevelChunk ch = level.getChunk(cx, cz);
                    OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                    if (h == null) continue;
                    int minY = ch.getMinBuildHeight();
                    for (int i = 0; i < h.size(); i++) {
                        if (h.gone(i) || dead(h.cell(i), h.organ(i)) || (pass == 0) != (h.cell(i) == org.cell)) continue;
                        int p = h.packed(i), x = (cx << 4) + (p & 15), y = minY + (p >>> 8), z = (cz << 4) + (p >> 4 & 15);
                        if (pass == 0) {
                            mine.put(BlockPos.asLong(x, y, z), h.organ(i));
                            bx0 = Math.min(bx0, x);
                            by0 = Math.min(by0, y);
                            bz0 = Math.min(bz0, z);
                            bx1 = Math.max(bx1, x);
                            by1 = Math.max(by1, y);
                            bz1 = Math.max(bz1, z);
                            continue;
                        }
                        if (x < bx0 - 1 || x > bx1 + 1 || y < by0 - 1 || y > by1 + 1 || z < bz0 - 1 || z > bz1 + 1) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    Integer o = mine.get(BlockPos.asLong(x + dx, y + dy, z + dz));
                                    if (o != null && o >= 0 && !org.nucleus(o)) out.add(o);
                                }
                            }
                        }
                    }
                }
            }
            if (mine.isEmpty()) break;
        }
        return out;
    }

    /** The dead organ's part (or the whole organism for WHOLE) withers in the loaded chunks, from the bulb outward. */
    private static void wither(ServerLevel level, OrganicLife.Organism org, int organ, long now) {
        DEAD.computeIfAbsent(org.cell, c -> ConcurrentHashMap.newKeySet()).add(organ);
        float[] t = TABLES.get(org.cell);
        if (t == null) return;
        int n = t.length / OrganicNetwork.ORGAN;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            x0 = Math.min(x0, t[i * OrganicNetwork.ORGAN]);
            x1 = Math.max(x1, t[i * OrganicNetwork.ORGAN]);
            z0 = Math.min(z0, t[i * OrganicNetwork.ORGAN + 2]);
            z1 = Math.max(z1, t[i * OrganicNetwork.ORGAN + 2]);
        }
        int b = Math.max(0, organ) * OrganicNetwork.ORGAN;
        double cx = organ >= 0 ? t[b] : (x0 + x1) / 2, cy = organ >= 0 ? t[b + 1] : t[1], cz = organ >= 0 ? t[b + 2] : (z0 + z1) / 2;
        int[] box = box(t);
        List<long[]> found = new ArrayList<>();
        for (int x = box[0]; x <= box[1]; x++) {
            for (int z = box[2]; z <= box[3]; z++) {
                if (!level.hasChunk(x, z)) continue;
                LevelChunk ch = level.getChunk(x, z);
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (h == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < h.size(); i++) {
                    if (h.gone(i) || h.cell(i) != org.cell || (organ >= 0 && h.organ(i) != organ)) continue;
                    int p = h.packed(i);
                    found.add(new long[]{BlockPos.asLong((x << 4) + (p & 15), minY + (p >>> 8), (z << 4) + (p >> 4 & 15)),
                            h.thick(i), h.organ(i), h.drip(i)});
                }
            }
        }
        double[] dist = new double[found.size()];
        double top = 1;
        for (int i = 0; i < dist.length; i++) {
            long p = found.get(i)[0];
            double dx = BlockPos.getX(p) + 0.5 - cx, dy = BlockPos.getY(p) + 0.5 - cy, dz = BlockPos.getZ(p) + 0.5 - cz;
            dist[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
            top = Math.max(top, dist[i]);
        }
        long[] pos = new long[dist.length];
        int[] tick = new int[dist.length];
        double[] rad = new double[dist.length];
        int[] streak = new int[dist.length];
        Integer[] order = new Integer[dist.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble(i -> dist[i]));
        for (int i = 0; i < order.length; i++) {
            long[] f = found.get(order[i]);
            pos[i] = f[0];
            rad[i] = OrganicHalo.radius((int) f[1], f[2] >= 0 && org.nucleus((int) f[2]) ? 1 : 0);
            streak[i] = (int) f[3];
            tick[i] = (int) Math.round(WITHER * dist[order[i]] / top);
        }
        OrganicLevelState.of(level).witherings.add(new Withering(now, org.cell, organ, pos, tick));
        if (RECEDE) OrganicRecession.recede(level, org, organ, pos, rad, streak, tick, now, null);
        if (RECEDE && organ >= 0 && !org.nucleus(organ)) OrganicRecession.recedeCore(level, org, organ, now);
        if (organ >= 0 && OrganicGrowth.atrophies(org.setup)) {
            OrganicLevelState.of(level).atrophyDue.add(org.cell);
        }
        if (TRACE) TheBeyond.LOGGER.info("ORG wither cell {} organ {} | blocks {} in loaded chunks", org.cell, organ, pos.length);
    }

    static boolean advance(ServerLevel level, Withering w, long now) {
        long t = now - w.start;
        while (w.next < w.pos.length && w.tick[w.next] <= t) {
            BlockPos p = BlockPos.of(w.pos[w.next++]);
            if (!level.isLoaded(p)) continue;
            LevelChunk ch = level.getChunkAt(p);
            OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
            int i = h == null ? -1 : h.find(OrganicTissue.pack(p.getX(), p.getY(), p.getZ(), ch.getMinBuildHeight()));
            if (!OrganicExperiment.tissue(level.getBlockState(p))) {
                if (i >= 0 && !h.gone(i)) {
                    h.remove(i);
                    ch.setUnsaved(true);
                }
                continue;
            }
            BlockState rock = i >= 0 && h.kind(i) == OrganicNetwork.TUNNEL ? h.replaced(i) : null;
            set(level, p, rock != null ? rock : Blocks.AIR.defaultBlockState());
            if (i >= 0) {
                h.remove(i);
                ch.setUnsaved(true);
            }
        }
        if (w.next < w.pos.length) return false;
        if (!RECEDE && w.organ == WHOLE && w.cell != 0) {
            float[] t2 = TABLES.get(w.cell);
            if (t2 != null) {
                int[] r = OrganicRecession.release(level, t2[0], t2[2], 160);
                if (TRACE) TheBeyond.LOGGER.info("ORG organism {} withered | quarts released {} | ground restored {}", w.cell, r[0], r[1]);
            }
        }
        return true;
    }

    /** Whether the organism's atrophy drops this live block, asking for the atrophy to be grown when it is not yet. */
    static boolean atrophied(ServerLevel level, OrganicTissue.Held h, int i, int x, int y, int z) {
        long cell = h.cell(i);
        Set<Integer> dead = DEAD.get(cell);
        String setup = h.setup(cell);
        if (dead == null || dead.contains(WHOLE) || setup == null || !OrganicGrowth.atrophies(setup)) return false;
        if (!OrganicGrowth.atrophyFor(cell, dead)) {
            OrganicLevelState.of(level).atrophyDue.add(cell);
            return false;
        }
        return OrganicGrowth.lostNow(cell, dead, x, y, z);
    }

    /** Grows the atrophy of a living organism's dead organs off the server thread, then withers what it lost. */
    static void startAtrophy(ServerLevel level, long cell) {
        OrganicLife.Organism org = OrganicLife.Data.get(level).organisms.get(cell);
        Set<Integer> dead = DEAD.get(cell);
        float[] t = TABLES.get(cell);
        if (org == null || !org.alive() || dead == null || dead.isEmpty() || dead.contains(WHOLE) || t == null) return;
        int[] box = box(t);
        for (int cx = box[0]; cx <= box[1]; cx++) {
            for (int cz = box[2]; cz <= box[3]; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                OrganicGrowth.atrophyLater(level, level.getChunk(cx, cz), cell, t[0], t[2], Set.copyOf(dead),
                        a -> applyAtrophy(level, org, a));
                return;
            }
        }
    }


    /** Withers what the thinner roots lost, far end first back to the live network, and the corruption only it held recedes. */
    private static void applyAtrophy(ServerLevel level, OrganicLife.Organism org, Atrophy a) {
        float[] t = TABLES.get(org.cell);
        if (!org.alive() || t == null || a.gone().length == 0 || !a.dead().equals(DEAD.getOrDefault(org.cell, Set.of()))) return;
        int[] box = box(t);
        LongOpenHashSet keep = new LongOpenHashSet();
        Long2IntOpenHashMap lost = new Long2IntOpenHashMap(), owner = new Long2IntOpenHashMap(), drip = new Long2IntOpenHashMap();
        for (int cx = box[0]; cx <= box[1]; cx++) {
            for (int cz = box[2]; cz <= box[3]; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (h == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < h.size(); i++) {
                    if (h.gone(i) || h.cell(i) != org.cell || dead(org.cell, h.organ(i))) continue;
                    int p = h.packed(i), x = (cx << 4) + (p & 15), y = minY + (p >>> 8), z = (cz << 4) + (p >> 4 & 15);
                    if (a.lost(x, y, z)) {
                        lost.put(BlockPos.asLong(x, y, z), h.thick(i));
                        owner.put(BlockPos.asLong(x, y, z), h.organ(i));
                        drip.put(BlockPos.asLong(x, y, z), h.drip(i));
                    } else {
                        keep.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
        if (lost.isEmpty()) return;
        Long2IntOpenHashMap depth = new Long2IntOpenHashMap();
        depth.defaultReturnValue(-1);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (long p : lost.keySet()) {
            for (Direction d : DIRS) {
                if (!keep.contains(BlockPos.offset(p, d))) continue;
                depth.put(p, 0);
                queue.enqueue(p);
                break;
            }
        }
        int deepest = 0;
        while (!queue.isEmpty()) {
            long p = queue.dequeueLong();
            int dp = depth.get(p);
            for (Direction d : DIRS) {
                long n = BlockPos.offset(p, d);
                if (!lost.containsKey(n) || depth.get(n) >= 0) continue;
                depth.put(n, dp + 1);
                deepest = Math.max(deepest, dp + 1);
                queue.enqueue(n);
            }
        }
        long[] all = lost.keySet().toLongArray();
        int far = deepest + 1;
        int[] when = new int[all.length];
        Integer[] order = new Integer[all.length];
        for (int i = 0; i < all.length; i++) {
            int d = depth.get(all[i]);
            when[i] = (int) Math.round(ATROPHY_TICKS * (double) (d < 0 ? 0 : far - d) / far);
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparingInt(i -> when[i]));
        long[] pos = new long[all.length];
        int[] tick = new int[all.length];
        double[] rad = new double[all.length];
        int[] streak = new int[all.length];
        double fed = org.fed();
        for (int i = 0; i < order.length; i++) {
            pos[i] = all[order[i]];
            tick[i] = when[order[i]];
            rad[i] = OrganicHalo.radius(lost.get(pos[i]), org.nucleus(owner.get(pos[i])) ? fed : 0);
            streak[i] = drip.get(pos[i]);
        }
        long now = level.getGameTime();
        OrganicLevelState.of(level).witherings.add(new Withering(now, org.cell, ATROPHIED, pos, tick));
        if (RECEDE) OrganicRecession.recede(level, org, ATROPHIED, pos, rad, streak, tick, now, new LongOpenHashSet(pos));
        if (TRACE) {
            TheBeyond.LOGGER.info("ORG atrophy cell {} | dead organs {} | blocks {} in loaded chunks, {} layers, kept {}",
                    org.cell, a.dead(), pos.length, far, keep.size());
        }
    }
}
