package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.block.OrganicBulbBlock;
import com.thebeyond.common.block.OrganicTissueBlock;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.registry.BeyondBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The organic network in a running world: its events, the order of each tick, the state its parts share and healing. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class OrganicWorld {
    static final boolean TRACE = Boolean.getBoolean("beyond.sub.org.trace");
    public static final int WITHER = Math.max(1, Integer.getInteger("beyond.sub.org.wither.ticks", 100));
    private static final int HEAL_EVERY = 20;
    private static final int SHY_LIGHT = 8;
    static final int WHOLE = -1;
    static final Map<Long, Set<Integer>> DEAD = new ConcurrentHashMap<>();
    static final Direction[] DIRS = Direction.values();
    static boolean applying;
    static final Map<Long, float[]> TABLES = new HashMap<>();
    private static final Map<Long, String> SETUPS = new HashMap<>();
    static final int QUART = 0, BURNING = 1, RESTORE = 2, TISSUE = 3, CORRUPT = 4;
    /** The corruption recedes just behind a dying part's wither, and a chunk that loads after the death gives it back. */
    static final boolean RECEDE = !"false".equals(System.getProperty("beyond.sub.org.recede", "true"));
    private OrganicWorld() {}

    @SubscribeEvent
    public static void onServerStarting(ServerAboutToStartEvent e) {
        reset();
        OrganicPresets.reload();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        reset();
    }

    private static void reset() {
        DEAD.clear();
        OrganicLevelState.forget();
        TABLES.clear();
        SETUPS.clear();
        OrganicGrowth.forget();
        SubSitesExperiment.forget();
        OrganicLight.clearHandLit();
        applying = false;
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !active(level)) return;
        OrganicLife.Data d = OrganicLife.Data.get(level);
        OrganicLevelState state = OrganicLevelState.of(level);
        for (Map.Entry<Long, int[]> en : d.handLit.entrySet()) {
            int[] a = en.getValue();
            OrganicLight.handLit(OrganicLight.litAt(a[0], a[1], a[2], a[3]));
            // a clearing cut short by a stop finishes once its chunk is loaded again
            if (a[4] == 0) state.pending.add(en.getKey());
            else state.returning.add(en.getKey());
        }
        for (OrganicLife.Organism o : d.organisms.values()) markDead(o);
        TheBeyond.LOGGER.info("ORG world {} | organisms {} | hand-lit bonfires {} | dead cells {}", level.dimension().location(),
                d.organisms.size(), d.handLit.size(), DEAD.size());
    }

    static boolean active(ServerLevel level) {
        return OrganicExperiment.ON && level.dimension() == Level.END
                && level.getChunkSource().getGenerator().getBiomeSource() instanceof BeyondEndBiomeSource;
    }

    static BeyondEndBiomeSource source(ServerLevel level) {
        return (BeyondEndBiomeSource) level.getChunkSource().getGenerator().getBiomeSource();
    }

    static void remember(OrganicTissue.Held h, long cell) {
        if (!TABLES.containsKey(cell)) {
            float[] t = h.table(cell);
            if (t != null) TABLES.put(cell, t);
        }
        String s = h.setup(cell);
        if (s != null) SETUPS.putIfAbsent(cell, s);
    }

    /** The organism of this cell, kept in the saved data from its first touch, its organs filled in as their chunks load. */
    static OrganicLife.Organism organism(ServerLevel level, long cell, OrganicLife.Data d) {
        OrganicLife.Organism o = d.get(cell);
        if (o != null) return o;
        float[] t = TABLES.get(cell);
        String setup = null;
        if (t == null) return null;
        int n = t.length / OrganicNetwork.ORGAN;
        boolean[] nucleus = new boolean[n];
        double[] flow = new double[n];
        for (int i = 0; i < n; i++) {
            nucleus[i] = t[i * OrganicNetwork.ORGAN + 4] == 0;
            flow[i] = t[i * OrganicNetwork.ORGAN + 6];
        }
        setup = SETUPS.get(cell);
        o = new OrganicLife.Organism(cell, setup == null ? "" : setup, new OrganicLife.Organ[n], nucleus, flow);
        d.put(o);
        return o;
    }

    /** Builds the organ from its painted bulb blocks once every chunk under its sphere is loaded. */
    static boolean ensure(ServerLevel level, OrganicLife.Organism org, int organ) {
        if (org.seen(organ)) return true;
        float[] t = TABLES.get(org.cell);
        if (t == null) return false;
        int b = organ * OrganicNetwork.ORGAN;
        double cx = t[b], cy = t[b + 1], cz = t[b + 2], r = t[b + 3] + 1;
        List<Long> blocks = new ArrayList<>();
        boolean exposed = false;
        for (int x = (int) Math.floor(cx - r) >> 4; x <= (int) Math.floor(cx + r) >> 4; x++) {
            for (int z = (int) Math.floor(cz - r) >> 4; z <= (int) Math.floor(cz + r) >> 4; z++) {
                if (!level.hasChunk(x, z)) return false;
                LevelChunk ch = level.getChunk(x, z);
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (h == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < h.size(); i++) {
                    if (h.kind(i) != OrganicNetwork.BULB || h.cell(i) != org.cell || h.organ(i) != organ) continue;
                    int p = h.packed(i);
                    BlockPos at = new BlockPos((x << 4) + (p & 15), minY + (p >>> 8), (z << 4) + (p >> 4 & 15));
                    blocks.add(at.asLong());
                    BlockState s = ch.getBlockState(at);
                    if (s.getBlock() instanceof OrganicBulbBlock && s.getValue(OrganicBulbBlock.EXPOSED)) exposed = true;
                }
            }
        }
        long[] p = new long[blocks.size()];
        for (int i = 0; i < p.length; i++) p[i] = blocks.get(i);
        OrganicLife.Organ g = new OrganicLife.Organ(org.nucleus(organ), cx, cy, cz, p, t[b + 6]);
        org.fill(organ, g);
        // the painter marks the grown bulb nearest the centre, and when that one went unpainted the next nearest is marked here
        if (g.core >= 0) {
            BlockPos c = BlockPos.of(g.pos[g.core]);
            BlockState s = level.getBlockState(c);
            if (s.getBlock() instanceof OrganicBulbBlock && !s.getValue(OrganicBulbBlock.CORE)) {
                level.setBlock(c, s.setValue(OrganicBulbBlock.CORE, true), Block.UPDATE_CLIENTS);
            }
        }
        // painted open: it sits in the clearing of a bonfire lit since the world generated it
        if (exposed) org.setBonfire(organ, true, level.getGameTime());
        return true;
    }

    /** Sets each live block of the bulb to show whether light exposes it now. */
    static void refresh(ServerLevel level, OrganicLife.Organism org, int organ, long now) {
        OrganicLife.Organ g = org.organ(organ);
        if (g == null) return;
        boolean open = org.open(organ, now);
        for (int i = 0; i < g.size(); i++) {
            if (g.state[i] == OrganicLife.BROKEN) continue;
            BlockPos p = BlockPos.of(g.pos[i]);
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof OrganicBulbBlock && s.getValue(OrganicBulbBlock.EXPOSED) != open) {
                set(level, p, s.setValue(OrganicBulbBlock.EXPOSED, open));
            }
        }
    }

    static void set(ServerLevel level, BlockPos p, BlockState s) {
        applying = true;
        try {
            level.setBlock(p, s, Block.UPDATE_ALL);
        } finally {
            applying = false;
        }
    }

    /** Where a bulb block sits in its organism, or null when the painter did not record it. */
    static long[] ownerAt(ServerLevel level, BlockPos pos) {
        LevelChunk ch = level.getChunkAt(pos);
        OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
        if (h == null) return null;
        int i = h.find(OrganicTissue.pack(pos.getX(), pos.getY(), pos.getZ(), ch.getMinBuildHeight()));
        if (i < 0 || h.organ(i) < 0) return null;
        remember(h, h.cell(i));
        return new long[]{h.cell(i), h.organ(i)};
    }

    /** Closed bulbs, and a nucleus whose subs live, cannot be broken, also in creative. A broken root grows back. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !active(level)) return;
        if (e.getState().getBlock() instanceof OrganicTissueBlock) {
            OrganicRegrow.broken(level, e.getPos(), e.getState());
            return;
        }
        if (!(e.getState().getBlock() instanceof OrganicBulbBlock)) return;
        long[] at = ownerAt(level, e.getPos());
        if (at == null) return;
        OrganicLife.Data d = OrganicLife.Data.get(level);
        OrganicLife.Organism org = organism(level, at[0], d);
        int organ = (int) at[1];
        boolean ok = org != null && ensure(level, org, organ) ? org.breakable(organ, level.getGameTime())
                : e.getState().getValue(OrganicBulbBlock.EXPOSED);
        if (!ok) {
            e.setCanceled(true);
            if (TRACE) TheBeyond.LOGGER.info("ORG break refused {} | cell {} organ {}", e.getPos().toShortString(), at[0], organ);
        }
    }

    private static void markDead(OrganicLife.Organism o) {
        if (!o.alive()) {
            DEAD.computeIfAbsent(o.cell, c -> ConcurrentHashMap.newKeySet()).add(WHOLE);
            return;
        }
        for (int i = 0; i < o.size(); i++) {
            if (o.dead(i)) DEAD.computeIfAbsent(o.cell, c -> ConcurrentHashMap.newKeySet()).add(i);
        }
    }

    static int[] box(float[] t) {
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < t.length; i += OrganicNetwork.ORGAN) {
            x0 = Math.min(x0, t[i]);
            x1 = Math.max(x1, t[i]);
            z0 = Math.min(z0, t[i + 2]);
            z1 = Math.max(z1, t[i + 2]);
        }
        // a part reaches past the organ centres by at most the cell's own margin
        int m = 48;
        return new int[]{((int) Math.floor(x0) - m) >> 4, ((int) Math.floor(x1) + m) >> 4, ((int) Math.floor(z0) - m) >> 4,
                ((int) Math.floor(z1) + m) >> 4};
    }

    static boolean dead(long cell, int organ) {
        Set<Integer> s = DEAD.get(cell);
        return s != null && (s.contains(WHOLE) || (organ >= 0 && s.contains(organ)));
    }

    /** Dead or atrophied tissue in a chunk that loads now withers at once, as it did in the loaded ones. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getChunk() instanceof LevelChunk ch) || !(ch.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.END) return;
        OrganicTrail.due(level, ch);
        if (DEAD.isEmpty()) return;
        if (RECEDE && OrganicRecession.blighted(ch)) {
            OrganicLevelState.of(level).releasing.add(ch.getPos().toLong());
        }
        OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
        if (h == null) return;
        List<Long> pos = new ArrayList<>();
        int minY = ch.getMinBuildHeight();
        ChunkPos cp = ch.getPos();
        for (int i = 0; i < h.size(); i++) {
            if (h.gone(i)) continue;
            int p = h.packed(i), x = cp.getMinBlockX() + (p & 15), y = minY + (p >>> 8), z = cp.getMinBlockZ() + (p >> 4 & 15);
            if (!dead(h.cell(i), h.organ(i)) && !OrganicDeath.atrophied(level, h, i, x, y, z)) continue;
            pos.add(BlockPos.asLong(x, y, z));
        }
        if (pos.isEmpty()) return;
        long[] p = new long[pos.size()];
        for (int i = 0; i < p.length; i++) p[i] = pos.get(i);
        OrganicLevelState.of(level).witherings
                .add(new OrganicDeath.Withering(level.getGameTime(), 0, WHOLE, p, new int[p.length]));
        if (TRACE) TheBeyond.LOGGER.info("ORG wither on load chunk {} {} | blocks {}", cp.x, cp.z, p.length);
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        long t0 = TRACE ? System.nanoTime() : 0;
        long now = level.getGameTime();
        OrganicLevelState state = OrganicLevelState.of(level);
        boolean work = TRACE && state.working();
        long t = t0;
        for (Iterator<OrganicFire.Cleaning> it = state.cleanings.iterator(); it.hasNext(); ) {
            if (OrganicFire.advance(level, it.next(), now)) it.remove();
        }
        if (TRACE) t = state.lap(0, t);
        if (!state.witherings.isEmpty()) {
            List<OrganicDeath.Withering> done = new ArrayList<>();
            for (OrganicDeath.Withering w : List.copyOf(state.witherings)) {
                if (OrganicDeath.advance(level, w, now)) done.add(w);
            }
            state.witherings.removeAll(done);
        }
        if (TRACE) t = state.lap(1, t);
        if (!state.pending.isEmpty()) OrganicFire.resume(level, state.pending, now);
        if (TRACE) t = state.lap(2, t);
        for (Iterator<OrganicReturn.Return> it = state.returns.iterator(); it.hasNext(); ) {
            if (OrganicReturn.advance(level, it.next(), now)) it.remove();
        }
        if (TRACE) t = state.lap(3, t);
        Set<Long> returning = state.returning;
        if (!returning.isEmpty()) {
            for (Iterator<Long> it = returning.iterator(); it.hasNext(); ) {
                long fire = it.next();
                BlockPos f = BlockPos.of(fire);
                if (!level.isLoaded(f)) continue;
                it.remove();
                BlockState s = level.getBlockState(f);
                if (s.getBlock() instanceof BonfireBlock && s.getValue(BonfireBlock.LIT)) continue;
                OrganicReturn.giveBack(level, OrganicLight.forFire(level, f, source(level)), fire, now);
            }
        }
        if (TRACE) t = state.lap(4, t);
        if (now % HEAL_EVERY == 0 && active(level)) heal(level, now);
        if (now % HEAL_EVERY == 0) OrganicTrail.advance(level);
        if (TRACE) t = state.lap(5, t);
        Set<Long> releasing = state.releasing;
        if (!releasing.isEmpty() && now % HEAL_EVERY == 0) {
            List<Long> ready = new ArrayList<>();
            for (Iterator<Long> it = releasing.iterator(); it.hasNext(); ) {
                long k = it.next();
                int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
                if (!level.hasChunk(cx, cz)) {
                    it.remove();
                    continue;
                }
                boolean all = true;
                for (int dx = -1; dx <= 1 && all; dx++) {
                    for (int dz = -1; dz <= 1 && all; dz++) all = level.hasChunk(cx + dx, cz + dz);
                }
                if (!all) continue;
                it.remove();
                ready.add(k);
            }
            for (long k : ready) OrganicRecession.releaseLoaded(level, level.getChunk(ChunkPos.getX(k), ChunkPos.getZ(k)));
        }
        if (TRACE) t = state.lap(6, t);
        if (!state.atrophyDue.isEmpty()) {
            List<Long> due = List.copyOf(state.atrophyDue);
            state.atrophyDue.clear();
            for (long cell : due) OrganicDeath.startAtrophy(level, cell);
        }
        if (TRACE) t = state.lap(7, t);
        OrganicBlight.flush(level, state, now);
        if (TRACE) {
            state.lap(8, t);
            state.timed(level, System.nanoTime() - t0, work);
        }
    }

    /** Light-shy bulbs stop healing in strong block light, the others heal in the dark after the pause. */
    private static void heal(ServerLevel level, long now) {
        OrganicRegrow.advance(level, now);
        OrganicLife.Data d = OrganicLife.Data.get(level);
        for (OrganicLife.Organism org : d.organisms.values()) {
            if (!org.alive()) continue;
            boolean due = false, loaded = true;
            for (int o = 0; o < org.size() && loaded; o++) {
                OrganicLife.Organ g = org.organ(o);
                if (g == null || g.dead() || g.full()) continue;
                due = true;
                if (!level.isLoaded(BlockPos.of(g.pos[0]))) loaded = false;
                else if (org.shy) org.setLight(o, bright(level, g), now);
            }
            if (!due || !loaded) continue;
            List<OrganicLife.Growth> grown = org.advance(now, p -> {
                BlockPos b = BlockPos.of(p);
                return level.isLoaded(b) && level.getBlockState(b).isAir() && !OrganicReturn.touchesBlockEntity(level, b);
            });
            for (OrganicLife.Growth gw : grown) {
                Block block = org.nucleus(gw.organ()) ? BeyondBlocks.ORG_NUCLEUS.get() : BeyondBlocks.ORG_BULB.get();
                set(level, BlockPos.of(gw.pos()), block.defaultBlockState().setValue(OrganicBulbBlock.REGROWN, true)
                        .setValue(OrganicBulbBlock.EXPOSED, org.open(gw.organ(), now))
                        .setValue(OrganicBulbBlock.CORE, org.core(gw.organ(), gw.pos())));
            }
            if (!grown.isEmpty()) {
                d.setDirty();
                if (TRACE) TheBeyond.LOGGER.info("ORG heal cell {} | blocks {} | {}", org.cell, grown.size(), org.describe());
            }
        }
        for (Iterator<long[]> it = OrganicLevelState.of(level).flames.iterator(); it.hasNext(); ) {
            long[] f = it.next();
            OrganicLife.Organism org = d.get(f[0]);
            if (org == null || org.exposed((int) f[1], now)) continue;
            refresh(level, org, (int) f[1], now);
            it.remove();
        }
    }

    static boolean lit(ServerLevel level, BlockPos p) {
        return level.getBrightness(LightLayer.BLOCK, p) >= SHY_LIGHT;
    }

    // strongest block light in the air touching a live block of the bulb
    private static boolean bright(ServerLevel level, OrganicLife.Organ g) {
        for (int i = 0; i < g.size(); i++) {
            if (g.state[i] == OrganicLife.BROKEN) continue;
            BlockPos p = BlockPos.of(g.pos[i]);
            for (Direction dir : DIRS) {
                BlockPos n = p.relative(dir);
                if (level.getBlockState(n).isAir() && level.getBrightness(LightLayer.BLOCK, n) >= SHY_LIGHT) return true;
            }
        }
        return false;
    }

}
