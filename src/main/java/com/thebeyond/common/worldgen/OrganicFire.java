package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.thebeyond.common.worldgen.OrganicWorld.BURNING;
import static com.thebeyond.common.worldgen.OrganicWorld.QUART;
import static com.thebeyond.common.worldgen.OrganicWorld.RESTORE;
import static com.thebeyond.common.worldgen.OrganicWorld.TRACE;
import static com.thebeyond.common.worldgen.OrganicWorld.WHOLE;
import static com.thebeyond.common.worldgen.OrganicWorld.active;
import static com.thebeyond.common.worldgen.OrganicWorld.ensure;
import static com.thebeyond.common.worldgen.OrganicWorld.organism;
import static com.thebeyond.common.worldgen.OrganicWorld.ownerAt;
import static com.thebeyond.common.worldgen.OrganicWorld.refresh;
import static com.thebeyond.common.worldgen.OrganicWorld.remember;
import static com.thebeyond.common.worldgen.OrganicWorld.set;
import static com.thebeyond.common.worldgen.OrganicWorld.source;

/** A hand-lit bonfire's front clearing the light-shy network, and the bulbs its flame keeps open. */
public final class OrganicFire {
    private static final String BURN = System.getProperty("beyond.sub.org.clean.burn", "minecraft:magma_block");
    private static final int BURN_MIN = 6, BURN_SPAN = 9;
    private static final double GROUND_WOBBLE = 0.12;
    static final int LEAD = 6;

    private OrganicFire() {}

    static final class Cleaning {
        final OrganicLight.Bonfire fire;
        final long start;
        final List<OrganicClean.Step> steps;
        final List<OrganicClean.Exposure> exposures;
        final List<long[]> ids;
        // tick, QUART / BURNING / RESTORE, position (a quart for QUART), index in to for RESTORE
        final List<long[]> events;
        final List<BlockState> to;
        int next, nextExposure, nextEvent, removed, rock, skipped, quarts, burned, ground;
        long cell;
        int organ = WHOLE;

        Cleaning(OrganicLight.Bonfire fire, long start, OrganicClean.Plan plan, List<long[]> ids, List<long[]> events,
                 List<BlockState> to) {
            this.fire = fire;
            this.start = start;
            this.steps = plan.steps();
            this.exposures = new ArrayList<>(plan.exposures());
            this.exposures.sort(Comparator.comparingInt(OrganicClean.Exposure::tick));
            this.ids = ids;
            this.events = events;
            this.events.sort(Comparator.comparingLong(e -> e[0]));
            this.to = to;
        }
    }

    private record Gathered(long[] pos, byte[] kind, int[] owner, List<long[]> ids) {}

    /** A player lit this bonfire: over OrganicClean.TICKS its light clears the light-shy network in its clearing. */
    public static void lit(ServerLevel level, BlockPos fire) {
        if (!active(level)) return;
        OrganicLight.Bonfire b = OrganicLight.forFire(level, fire, source(level)).asLit();
        OrganicLevelState.of(level).returns.removeIf(r -> r.fire == fire.asLong());
        OrganicLight.handLit(b);
        OrganicLife.Data d = OrganicLife.Data.get(level);
        d.handLit.put(fire.asLong(), new int[]{b.fx(), b.fy(), b.fz(), b.footY(), 0});
        d.setDirty();
        start(level, b, level.getGameTime());
    }

    private static void start(ServerLevel level, OrganicLight.Bonfire b, long start) {
        Gathered g = gather(level, b, false);
        OrganicClean.Plan plan = OrganicClean.plan(b, g.pos(), g.kind(), g.owner(), level.getSeed());
        List<long[]> events = new ArrayList<>();
        List<BlockState> to = new ArrayList<>();
        front(level, b, events, to);
        Cleaning c = new Cleaning(b, start, plan, g.ids(), events, to);
        OrganicLevelState.of(level).cleanings.add(c);
        if (TRACE) TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG lit %d %d %d | foot %d | blocks %d | steps %d | bulbs %d"
                + " | max per tick %d | front events %d, last at tick %d", b.fx(), b.fy(), b.fz(), b.footY(), g.pos().length,
                plan.steps().size(), plan.exposures().size(), plan.maxPerTick(), events.size(),
                events.isEmpty() ? 0 : events.get(events.size() - 1)[0]));
    }

    static int frontTick(OrganicLight.Bonfire b, SimplexNoise n, double x, double y, double z) {
        double u = OrganicClean.FRONT * b.ratio(x, y, z) + GROUND_WOBBLE * n.getValue(x / 7, y / 7, z / 7);
        return (int) Math.round(OrganicClean.TICKS * Math.max(0, u));
    }

    /** The corruption retreats from the fire: each freed quart turns as the front passes, its ground burning a moment first. */
    private static void front(ServerLevel level, OrganicLight.Bonfire b, List<long[]> events, List<BlockState> to) {
        List<OrganicLight.Bonfire> lit = new ArrayList<>();
        for (OrganicLight.Bonfire o : OrganicLight.near(level, new ChunkPos(b.fx() >> 4, b.fz() >> 4), source(level))) {
            if (o.lit()) lit.add(o);
        }
        lit.add(b);
        double radius = OrganicLight.REACH + OrganicHalo.reach() + 2;
        Set<Long> free = OrganicRecession.releasable(level, b.fx() + 0.5, b.fz() + 0.5, radius, lit);
        if (free.isEmpty()) return;
        OrganicLife.Data d = OrganicLife.Data.get(level);
        long key = BlockPos.asLong(b.fx(), b.fy(), b.fz());
        Set<Long> all = new HashSet<>(free);
        for (long q : d.released.getOrDefault(key, new long[0])) all.add(q);
        d.released.put(key, all.stream().mapToLong(Long::longValue).toArray());
        d.setDirty();
        SimplexNoise n = new SimplexNoise(new XoroshiroRandomSource(level.getSeed() ^ 0x6275726EL));
        for (long q : free) {
            int t = frontTick(b, n, BlockPos.getX(q) * 4 + 2, BlockPos.getY(q) * 4 + 2, BlockPos.getZ(q) * 4 + 2);
            events.add(new long[]{Math.max(0, t - LEAD), QUART, q, -1});
        }
        Holder<Biome> plain = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
                net.minecraft.world.level.biome.Biomes.THE_END);
        // the biome each ground block will read once every freed quart has turned, as the surface swap read it
        BiomeManager.NoiseBiomeSource freed = (qx, qy, qz) -> free.contains(BlockPos.asLong(qx, qy, qz)) ? plain
                : level.getNoiseBiome(qx, qy, qz);
        BiomeManager after = new BiomeManager(freed, BiomeManager.obfuscateSeed(level.getSeed()));
        BlockState corrupted = OrganicSurface.corrupted();
        boolean burn = !"none".equals(BURN);
        // a trail reaches past the freed quarts by its own length
        int r = (int) Math.ceil(radius) + OrganicTrail.MAX;
        for (int cx = (b.fx() - r) >> 4; cx <= (b.fx() + r) >> 4; cx++) {
            for (int cz = (b.fz() - r) >> 4; cz <= (b.fz() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicSurface.Kept kept = ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
                if (kept == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < kept.pos.length; i++) {
                    int p = kept.pos[i];
                    BlockPos at = new BlockPos((cx << 4) + (p & 15), minY + (p >>> 8), (cz << 4) + (p >> 4 & 15));
                    BlockState cur = ch.getBlockState(at), had = kept.palette.get(kept.state[i]);
                    boolean plant = OrganicSurface.plant(had);
                    if (!(plant ? cur.isAir() : cur == corrupted)
                            || OrganicTrail.held(kept, i, at, x -> after.getBiome(x).is(OrganicBlight.BLIGHT))) continue;
                    int t = frontTick(b, n, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                    to.add(had);
                    if (plant) {
                        events.add(new long[]{t + BURN_MIN + BURN_SPAN, RESTORE, at.asLong(), to.size() - 1});
                        continue;
                    }
                    int span = burn ? BURN_MIN + (int) Math.floorMod(at.asLong() * 0x9E3779B97F4A7C15L >>> 40, (long) BURN_SPAN) : 0;
                    if (burn) events.add(new long[]{t, BURNING, at.asLong(), -1});
                    events.add(new long[]{t + span, RESTORE, at.asLong(), to.size() - 1});
                }
            }
        }
    }

    /** The light-shy network still standing in the bonfire's clearing (bulbs only with bulbsOnly), owners as plan ids. */
    private static Gathered gather(ServerLevel level, OrganicLight.Bonfire b, boolean bulbsOnly) {
        int r = (int) Math.ceil(OrganicLight.REACH) + 1;
        List<Long> pos = new ArrayList<>();
        List<Byte> kind = new ArrayList<>();
        List<Integer> owner = new ArrayList<>();
        List<long[]> ids = new ArrayList<>();
        Map<Long, Map<Integer, Integer>> idOf = new HashMap<>();
        for (int cx = (b.fx() - r) >> 4; cx <= (b.fx() + r) >> 4; cx++) {
            for (int cz = (b.fz() - r) >> 4; cz <= (b.fz() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (h == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < h.size(); i++) {
                    byte k = h.kind(i);
                    if (k < 0 || (bulbsOnly && k != OrganicNetwork.BULB)) continue;
                    long cell = h.cell(i);
                    String setup = h.setup(cell);
                    if (setup == null || !OrganicLight.shy(setup)) continue;
                    remember(h, cell);
                    int p = h.packed(i);
                    int x = (cx << 4) + (p & 15), y = minY + (p >>> 8), z = (cz << 4) + (p >> 4 & 15);
                    if (!b.reaches(x + 0.5, y + 0.5, z + 0.5)) continue;
                    if (!OrganicExperiment.tissue(ch.getBlockState(new BlockPos(x, y, z)))) continue;
                    pos.add(BlockPos.asLong(x, y, z));
                    kind.add(k);
                    int organ = h.organ(i);
                    owner.add(organ < 0 ? -1 : idOf.computeIfAbsent(cell, c -> new HashMap<>()).computeIfAbsent(organ, o -> {
                        ids.add(new long[]{cell, o});
                        return ids.size() - 1;
                    }));
                }
            }
        }
        long[] p = new long[pos.size()];
        byte[] k = new byte[pos.size()];
        int[] o = new int[pos.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = pos.get(i);
            k[i] = kind.get(i);
            o[i] = owner.get(i);
        }
        return new Gathered(p, k, o, ids);
    }

    /** The lit bonfire went out or was broken: the bulbs it kept exposed close, unless another lit bonfire still reaches them. */
    public static void out(ServerLevel level, BlockPos fire) {
        if (!active(level)) return;
        OrganicLife.Data d = OrganicLife.Data.get(level);
        OrganicLight.Bonfire b = OrganicLight.forFire(level, fire, source(level));
        OrganicLevelState.of(level).cleanings
                .removeIf(c -> BlockPos.asLong(c.fire.fx(), c.fire.fy(), c.fire.fz()) == fire.asLong());
        Set<Long> seen = new HashSet<>();
        List<long[]> pairs = new ArrayList<>(gather(level, b, true).ids());
        List<long[]> held = d.exposedBy.remove(fire.asLong());
        if (held != null) pairs.addAll(held);
        long now = level.getGameTime();
        int closed = 0;
        for (long[] pr : pairs) {
            int organ = (int) pr[1];
            if (!seen.add(pr[0] * 4099 + organ)) continue;
            OrganicLife.Organism org = organism(level, pr[0], d);
            if (org == null || !ensure(level, org, organ)) continue;
            if (stillLit(level, org, organ, fire)) continue;
            org.setBonfire(organ, false, now);
            refresh(level, org, organ, now);
            closed++;
        }
        d.setDirty();
        if (TRACE) TheBeyond.LOGGER.info("ORG bonfire out {} | bulbs closed {}", fire.toShortString(), closed);
        int[] a = d.handLit.get(fire.asLong());
        if (a != null) {
            a[4] = 1;
            OrganicReturn.giveBack(level, b, fire.asLong(), now + OrganicReturn.RETURN_PAUSE);
        }
    }

    static boolean stillBurning(ServerLevel level, OrganicLight.Bonfire o) {
        BlockPos f = new BlockPos(o.fx(), o.fy(), o.fz());
        if (!level.isLoaded(f)) return true;
        BlockState s = level.getBlockState(f);
        return !(s.getBlock() instanceof BonfireBlock) || s.getValue(BonfireBlock.LIT);
    }

    // another bonfire whose fire is lit right now reaches a block of this bulb
    private static boolean stillLit(ServerLevel level, OrganicLife.Organism org, int organ, BlockPos except) {
        OrganicLife.Organ g = org.organ(organ);
        if (g == null || g.size() == 0) return false;
        BlockPos at = BlockPos.of(g.pos[0]);
        for (OrganicLight.Bonfire b : OrganicLight.near(level, new ChunkPos(at), source(level))) {
            if (b.fx() == except.getX() && b.fy() == except.getY() && b.fz() == except.getZ()) continue;
            BlockPos f = new BlockPos(b.fx(), b.fy(), b.fz());
            BlockState s = level.isLoaded(f) ? level.getBlockState(f) : null;
            if (s != null && s.getBlock() instanceof BonfireBlock) {
                if (!s.getValue(BonfireBlock.LIT)) continue;
            } else if (!b.virtual() || !b.lit()) {
                continue;
            }
            for (long p : g.pos) {
                if (b.reaches(BlockPos.getX(p) + 0.5, BlockPos.getY(p) + 0.5, BlockPos.getZ(p) + 0.5)) return true;
            }
        }
        return false;
    }

    /** A live flame held to a bulb burns there for what was left of it. */
    public static boolean flame(ServerLevel level, BlockPos pos, ItemStack stack, Player player) {
        if (!active(level)) return false;
        long[] at = ownerAt(level, pos);
        if (at == null) return false;
        OrganicLife.Data d = OrganicLife.Data.get(level);
        OrganicLife.Organism org = organism(level, at[0], d);
        int organ = (int) at[1];
        if (org == null || !ensure(level, org, organ) || org.organ(organ).dead()) return false;
        long now = level.getGameTime(), left = Math.max(1, stack.getMaxDamage() - stack.getDamageValue());
        org.setFlame(organ, now + left, now);
        d.setDirty();
        OrganicLevelState.of(level).flames.add(new long[]{org.cell, organ});
        refresh(level, org, organ, now);
        if (!player.isCreative()) stack.shrink(1);
        if (TRACE) TheBeyond.LOGGER.info("ORG flame {} | cell {} organ {} | ticks {}", pos.toShortString(), org.cell, organ, left);
        return true;
    }

    static void resume(ServerLevel level, Set<Long> pending, long now) {
        OrganicLife.Data d = OrganicLife.Data.get(level);
        for (Iterator<Long> it = pending.iterator(); it.hasNext(); ) {
            long fire = it.next();
            int[] a = d.handLit.get(fire);
            if (a == null) {
                it.remove();
                continue;
            }
            if (!level.isLoaded(BlockPos.of(fire))) continue;
            it.remove();
            start(level, OrganicLight.forFire(level, BlockPos.of(fire), source(level)).asLit(), now - OrganicClean.TICKS);
        }
    }

    static boolean advance(ServerLevel level, Cleaning c, long now) {
        long t = now - c.start;
        while (c.next < c.steps.size() && c.steps.get(c.next).tick() <= t) step(level, c, c.steps.get(c.next++));
        OrganicLife.Data d = OrganicLife.Data.get(level);
        while (c.nextExposure < c.exposures.size() && c.exposures.get(c.nextExposure).tick() <= t) {
            long[] id = c.ids.get(c.exposures.get(c.nextExposure++).owner());
            OrganicLife.Organism org = organism(level, id[0], d);
            int organ = (int) id[1];
            if (org == null || !ensure(level, org, organ)) continue;
            org.setBonfire(organ, true, now);
            List<long[]> ex = d.exposedBy.computeIfAbsent(BlockPos.asLong(c.fire.fx(), c.fire.fy(), c.fire.fz()),
                    k -> new ArrayList<>());
            if (ex.stream().noneMatch(v -> v[0] == id[0] && v[1] == id[1])) ex.add(id.clone());
            d.setDirty();
            refresh(level, org, organ, now);
        }
        events(level, c, t);
        if (c.next < c.steps.size() || c.nextExposure < c.exposures.size() || c.nextEvent < c.events.size()
                || t < OrganicClean.TICKS) return false;
        if (c.fire == null) {
            if (TRACE) TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG recede done cell %d organ %d | ticks %d | quarts"
                    + " released %d | ground restored %d", c.cell, c.organ, t, c.quarts, c.ground));
            return true;
        }
        int[] a = d.handLit.get(BlockPos.asLong(c.fire.fx(), c.fire.fy(), c.fire.fz()));
        if (a != null) a[4] = 1;
        d.setDirty();
        if (TRACE) TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG clean done %d %d %d | ticks %d | removed %d rock %d"
                + " skipped %d | bulbs %d | quarts released %d | ground burned %d restored %d", c.fire.fx(), c.fire.fy(),
                c.fire.fz(), t, c.removed, c.rock, c.skipped, c.exposures.size(), c.quarts, c.burned, c.ground));
        return true;
    }

    private static void events(ServerLevel level, Cleaning c, long t) {
        Map<Long, Set<Long>> byChunk = new HashMap<>();
        BlockState corrupted = OrganicSurface.corrupted(), burn = null;
        while (c.nextEvent < c.events.size() && c.events.get(c.nextEvent)[0] <= t) {
            long[] e = c.events.get(c.nextEvent++);
            if (e[1] == QUART) {
                int qx = BlockPos.getX(e[2]), qz = BlockPos.getZ(e[2]);
                byChunk.computeIfAbsent(ChunkPos.asLong(qx >> 2, qz >> 2), k -> new HashSet<>()).add(e[2]);
                continue;
            }
            BlockPos p = BlockPos.of(e[2]);
            if (!level.isLoaded(p)) continue;
            if (burn == null) burn = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(BURN)).defaultBlockState();
            BlockState s = level.getBlockState(p);
            if (e[1] == BURNING && s == corrupted) {
                set(level, p, burn);
                c.burned++;
            } else if (e[1] == RESTORE && (s == corrupted || s == burn
                    || (s.isAir() && OrganicSurface.plant(c.to.get((int) e[3]))))) {
                set(level, p, c.to.get((int) e[3]));
                c.ground++;
            }
        }
        if (byChunk.isEmpty()) return;
        BeyondEndBiomeSource source = source(level);
        var sampler = level.getChunkSource().randomState().sampler();
        List<ChunkAccess> changed = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> en : byChunk.entrySet()) {
            int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk ch = level.getChunk(cx, cz);
            Set<Long> q = en.getValue();
            ch.fillBiomesFromNoise((qx, qy, qz, s) -> q.contains(BlockPos.asLong(qx, qy, qz))
                    ? source.getNoiseBiome(qx, qy, qz, s) : ch.getNoiseBiome(qx, qy, qz), sampler);
            ch.setUnsaved(true);
            changed.add(ch);
            c.quarts += q.size();
        }
        if (!changed.isEmpty()) OrganicBlight.resendLater(level, changed);
    }

    private static void step(ServerLevel level, Cleaning c, OrganicClean.Step s) {
        BlockPos p = BlockPos.of(s.pos());
        if (!level.isLoaded(p)) {
            c.skipped++;
            return;
        }
        LevelChunk ch = level.getChunkAt(p);
        OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
        int i = h == null ? -1 : h.find(OrganicTissue.pack(p.getX(), p.getY(), p.getZ(), ch.getMinBuildHeight()));
        if (!OrganicExperiment.tissue(level.getBlockState(p))) {
            c.skipped++;
            return;
        }
        BlockState rock = s.action() == OrganicLight.Action.ROCK && i >= 0 ? h.replaced(i) : null;
        BlockState was = level.getBlockState(p);
        set(level, p, rock != null ? rock : Blocks.AIR.defaultBlockState());
        if (rock != null) c.rock++;
        else c.removed++;
        if (i >= 0) {
            h.clear(i, was);
            ch.setUnsaved(true);
        }
    }
}
