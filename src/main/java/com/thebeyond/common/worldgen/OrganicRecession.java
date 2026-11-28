package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondAttachments;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.thebeyond.common.worldgen.OrganicWorld.DEAD;
import static com.thebeyond.common.worldgen.OrganicWorld.QUART;
import static com.thebeyond.common.worldgen.OrganicWorld.RESTORE;
import static com.thebeyond.common.worldgen.OrganicWorld.TABLES;
import static com.thebeyond.common.worldgen.OrganicWorld.TRACE;
import static com.thebeyond.common.worldgen.OrganicWorld.WITHER;
import static com.thebeyond.common.worldgen.OrganicWorld.box;
import static com.thebeyond.common.worldgen.OrganicWorld.dead;
import static com.thebeyond.common.worldgen.OrganicWorld.set;
import static com.thebeyond.common.worldgen.OrganicWorld.source;

/** The corruption a dead part held recedes, in the running world and in chunks that load after the death. */
final class OrganicRecession {
    private static final int RECEDE_DELAY = 10;
    private static final OrganicClean.Plan NO_PLAN = new OrganicClean.Plan(List.of(), List.of(), 0);

    private OrganicRecession() {}

    /** Frees the corruption around x, z that a lit clearing holds or no tissue holds any more, ground too: quarts and blocks. */
    static int[] release(ServerLevel level, double x, double z, double radius) {
        List<OrganicLight.Bonfire> lit = new ArrayList<>();
        for (OrganicLight.Bonfire b : OrganicLight.near(level, new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4), source(level))) {
            if (b.lit()) lit.add(b);
        }
        Set<Long> free = releasable(level, x, z, radius, lit);
        BeyondEndBiomeSource source = source(level);
        var sampler = level.getChunkSource().randomState().sampler();
        Map<Long, Set<Long>> byChunk = new HashMap<>();
        for (long q : free) {
            byChunk.computeIfAbsent(ChunkPos.asLong(BlockPos.getX(q) >> 2, BlockPos.getZ(q) >> 2), k -> new HashSet<>()).add(q);
        }
        List<ChunkAccess> changed = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> en : byChunk.entrySet()) {
            LevelChunk ch = level.getChunk(ChunkPos.getX(en.getKey()), ChunkPos.getZ(en.getKey()));
            Set<Long> q = en.getValue();
            ch.fillBiomesFromNoise((qx, qy, qz, s) -> q.contains(BlockPos.asLong(qx, qy, qz))
                    ? source.getNoiseBiome(qx, qy, qz, s) : ch.getNoiseBiome(qx, qy, qz), sampler);
            ch.setUnsaved(true);
            changed.add(ch);
        }
        if (!changed.isEmpty()) OrganicBlight.resendLater(level, changed);
        int ground = 0;
        for (ChunkAccess c : changed) ground += restoreGround(level, (LevelChunk) c);
        return new int[]{free.size(), ground};
    }

    /** Corruption quarts around x, z in a lit clearing or no longer within any light-shy tissue's halo, packed. */
    static Set<Long> releasable(ServerLevel level, double x, double z, double radius, List<OrganicLight.Bonfire> lit) {
        Set<Long> out = new HashSet<>();
        int r = (int) Math.ceil(radius);
        int cx0 = (Mth.floor(x) - r) >> 4, cx1 = (Mth.floor(x) + r) >> 4, cz0 = (Mth.floor(z) - r) >> 4;
        int cz1 = (Mth.floor(z) + r) >> 4, qy0 = level.getMinSection() << 2, qy1 = (level.getMaxSection() << 2) - 1;
        OrganicHalo.Field held = field(level, cx0 << 2, qy0, cz0 << 2, (cx1 << 2) + 3, qy1, (cz1 << 2) + 3,
                (int) Math.ceil(OrganicHalo.reach()) + 1, (h, i) -> !h.gone(i) && !dead(h.cell(i), h.organ(i)), lit);
        long seed = level.getSeed();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                for (int qx = cx << 2; qx < (cx << 2) + 4; qx++) {
                    for (int qz = cz << 2; qz < (cz << 2) + 4; qz++) {
                        double ex = qx * 4 + 2, ez = qz * 4 + 2;
                        if ((ex - x) * (ex - x) + (ez - z) * (ez - z) > (radius + 3) * (radius + 3)) continue;
                        for (int qy = ch.getMinSection() << 2; qy < ch.getMaxSection() << 2; qy++) {
                            if (!ch.getNoiseBiome(qx, qy, qz).is(OrganicBlight.BLIGHT)) continue;
                            if (OrganicLight.inAny(lit, ex, qy * 4 + 2, ez) || !held.held(seed, qx, qy, qz)) {
                                out.add(BlockPos.asLong(qx, qy, qz));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    @FunctionalInterface
    interface EntryTest {
        boolean test(OrganicTissue.Held h, int i);
    }

    /** Halo of the light-shy tissue passing keep, read from the loaded chunks pad blocks around the box, out of lit clearings. */
    static OrganicHalo.Field field(ServerLevel level, int qx0, int qy0, int qz0, int qx1, int qy1, int qz1, int pad,
                                           EntryTest keep, List<OrganicLight.Bonfire> lit) {
        return field(level, qx0, qy0, qz0, qx1, qy1, qz1, pad, keep, lit, null);
    }

    static OrganicHalo.Field field(ServerLevel level, int qx0, int qy0, int qz0, int qx1, int qy1, int qz1, int pad,
                                           EntryTest keep, List<OrganicLight.Bonfire> lit, LongSet except) {
        OrganicHalo.Field f = new OrganicHalo.Field(qx0, qy0, qz0, qx1, qy1, qz1, false);
        Map<Long, Double> fed = new HashMap<>();
        for (int cx = (qx0 * 4 - pad) >> 4; cx <= (qx1 * 4 + 3 + pad) >> 4; cx++) {
            for (int cz = (qz0 * 4 - pad) >> 4; cz <= (qz1 * 4 + 3 + pad) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicTissue.Held held = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (held == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < held.size(); i++) {
                    if (!keep.test(held, i)) continue;
                    String setup = held.setup(held.cell(i));
                    if (setup == null || !OrganicLight.shy(setup)) continue;
                    int p = held.packed(i);
                    double px = (cx << 4) + (p & 15) + 0.5, py = minY + (p >>> 8) + 0.5, pz = (cz << 4) + (p >> 4 & 15) + 0.5;
                    if (OrganicLight.inAny(lit, px, py, pz)) continue;
                    if (except != null && except.contains(BlockPos.asLong((cx << 4) + (p & 15), minY + (p >>> 8),
                            (cz << 4) + (p >> 4 & 15)))) continue;
                    f.add(px, py, pz, OrganicHalo.radius(held.thick(i), core(level, held, i, fed)), 0);
                    int drip = held.drip(i);
                    if (drip != 0) f.drip(Mth.floor(px), Mth.floor(py), Mth.floor(pz), drip, 0);
                }
            }
        }
        return f;
    }

    /** What only the dying part held recedes behind its wither, each quart after its nearest dying block, plants last. */
    static void recede(ServerLevel level, OrganicLife.Organism org, int organ, long[] pos, double[] rad, int[] streak,
                       int[] tick, long now, LongSet except) {
        if (pos.length == 0 || !org.shy) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, last = 0;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int i = 0; i < pos.length; i++) {
            x0 = Math.min(x0, BlockPos.getX(pos[i]));
            y0 = Math.min(y0, BlockPos.getY(pos[i]));
            z0 = Math.min(z0, BlockPos.getZ(pos[i]));
            x1 = Math.max(x1, BlockPos.getX(pos[i]));
            y1 = Math.max(y1, BlockPos.getY(pos[i]));
            z1 = Math.max(z1, BlockPos.getZ(pos[i]));
            last = Math.max(last, tick[i]);
        }
        int r = (int) Math.ceil(OrganicHalo.reach()) + 1;
        int qx0 = Math.floorDiv(x0 - r, 4), qx1 = Math.floorDiv(x1 + r, 4), qz0 = Math.floorDiv(z0 - r, 4);
        int qz1 = Math.floorDiv(z1 + r, 4);
        int qy0 = Math.max(level.getMinSection() << 2, Math.floorDiv(y0 - r - OrganicHalo.below(), 4));
        int qy1 = Math.min((level.getMaxSection() << 2) - 1, Math.floorDiv(y1 + r, 4));
        OrganicHalo.Field dying = new OrganicHalo.Field(qx0, qy0, qz0, qx1, qy1, qz1, true);
        for (int i = 0; i < pos.length; i++) {
            double x = BlockPos.getX(pos[i]) + 0.5, y = BlockPos.getY(pos[i]) + 0.5, z = BlockPos.getZ(pos[i]) + 0.5;
            dying.add(x, y, z, rad[i], tick[i]);
            if (streak != null && streak[i] != 0) {
                dying.drip(BlockPos.getX(pos[i]), BlockPos.getY(pos[i]), BlockPos.getZ(pos[i]), streak[i], tick[i]);
            }
        }
        OrganicHalo.Field live = field(level, qx0, qy0, qz0, qx1, qy1, qz1, r,
                (h, i) -> !h.gone(i) && !dead(h.cell(i), h.organ(i)), List.of(), except);
        long seed = level.getSeed();
        Map<Long, Integer> free = new HashMap<>();
        for (int cx = qx0 >> 2; cx <= qx1 >> 2; cx++) {
            for (int cz = qz0 >> 2; cz <= qz1 >> 2; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                for (int qx = Math.max(qx0, cx << 2); qx <= Math.min(qx1, (cx << 2) + 3); qx++) {
                    for (int qz = Math.max(qz0, cz << 2); qz <= Math.min(qz1, (cz << 2) + 3); qz++) {
                        for (int qy = qy0; qy <= qy1; qy++) {
                            if (!dying.held(seed, qx, qy, qz) || live.held(seed, qx, qy, qz)) continue;
                            if (!ch.getNoiseBiome(qx, qy, qz).is(OrganicBlight.BLIGHT)) continue;
                            int t = dying.tick(qx, qy, qz);
                            free.put(BlockPos.asLong(qx, qy, qz), t == Integer.MAX_VALUE ? last : t);
                        }
                    }
                }
            }
        }
        if (free.isEmpty()) return;
        List<long[]> events = new ArrayList<>();
        List<BlockState> to = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : free.entrySet()) {
            events.add(new long[]{e.getValue() + RECEDE_DELAY, QUART, e.getKey(), -1});
        }
        Holder<Biome> plain = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(
                net.minecraft.world.level.biome.Biomes.THE_END);
        BiomeManager.NoiseBiomeSource freed = (qx, qy, qz) -> free.containsKey(BlockPos.asLong(qx, qy, qz)) ? plain
                : level.getNoiseBiome(qx, qy, qz);
        BiomeManager after = new BiomeManager(freed, BiomeManager.obfuscateSeed(seed));
        BlockState corrupted = OrganicSurface.corrupted();
        int ground = 0;
        // one chunk more on each side for the trails that started in the freed quarts
        for (int cx = (qx0 >> 2) - 1; cx <= (qx1 >> 2) + 1; cx++) {
            for (int cz = (qz0 >> 2) - 1; cz <= (qz1 >> 2) + 1; cz++) {
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
                    int t = Integer.MAX_VALUE;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                Integer q = free.get(BlockPos.asLong((at.getX() >> 2) + dx, (at.getY() >> 2) + dy,
                                        (at.getZ() >> 2) + dz));
                                if (q != null) t = Math.min(t, q);
                            }
                        }
                    }
                    to.add(had);
                    events.add(new long[]{(t == Integer.MAX_VALUE ? last : t) + RECEDE_DELAY + (plant ? 3 : 1), RESTORE,
                            at.asLong(), to.size() - 1});
                    ground++;
                }
            }
        }
        OrganicFire.Cleaning c = new OrganicFire.Cleaning(null, now, NO_PLAN, List.of(), events, to);
        c.cell = org.cell;
        c.organ = organ;
        OrganicLevelState.of(level).cleanings.add(c);
        if (TRACE) TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG recede cell %d organ %d | quarts %d | ground and plants"
                + " %d | last at tick %d", org.cell, organ, free.size(), ground, last + RECEDE_DELAY + 3));
    }

    /** How fed the nucleus owning this entry is (0 for a sub's tissue), which widens its halo into the dense core. */
    private static double core(ServerLevel level, OrganicTissue.Held h, int i, Map<Long, Double> fed) {
        long cell = h.cell(i);
        int o = h.organ(i);
        float[] t = h.table(cell);
        if (OrganicHalo.CORE <= 0 || t == null || o < 0 || (o + 1) * OrganicNetwork.ORGAN > t.length
                || t[o * OrganicNetwork.ORGAN + 4] != 0) return 0;
        return fed.computeIfAbsent(cell, c -> {
            OrganicLife.Organism org = OrganicLife.Data.get(level).organisms.get(c);
            return org != null ? org.fed() : OrganicLife.bornFed(t, DEAD.get(c));
        });
    }

    /** A dead sub fed its nucleus no more: the dense core shrinks by its share of the flow and that ring recedes. */
    static void recedeCore(ServerLevel level, OrganicLife.Organism org, int organ, long now) {
        double before = org.fed(organ), after = org.fed();
        float[] t = TABLES.get(org.cell);
        boolean fed = false;
        for (int o = 0; o < org.size(); o++) fed |= org.nucleus(o) && !org.dead(o);
        if (!fed || !org.shy || OrganicHalo.CORE <= 0 || !OrganicHalo.flow() || before <= after || t == null) return;
        int[] box = box(t);
        List<long[]> found = new ArrayList<>();
        for (int cx = box[0]; cx <= box[1]; cx++) {
            for (int cz = box[2]; cz <= box[3]; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (h == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < h.size(); i++) {
                    int o = h.organ(i);
                    if (h.gone(i) || h.cell(i) != org.cell || o < 0 || !org.nucleus(o) || org.dead(o)) continue;
                    int p = h.packed(i);
                    found.add(new long[]{BlockPos.asLong((cx << 4) + (p & 15), minY + (p >>> 8), (cz << 4) + (p >> 4 & 15)),
                            h.thick(i)});
                }
            }
        }
        long[] pos = new long[found.size()];
        double[] rad = new double[pos.length];
        int[] tick = new int[pos.length];
        for (int i = 0; i < pos.length; i++) {
            pos[i] = found.get(i)[0];
            rad[i] = OrganicHalo.radius((int) found.get(i)[1], before);
            tick[i] = WITHER;
        }
        if (TRACE) {
            TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG core cell %d organ %d | fed %.3f -> %.3f | nucleus blocks %d",
                    org.cell, organ, before, after, pos.length));
        }
        recede(level, org, organ, pos, rad, null, tick, now, null);
    }

    static boolean blighted(LevelChunk ch) {
        for (LevelChunkSection s : ch.getSections()) {
            if (s.getBiomes().maybeHas(h -> h.is(OrganicBlight.BLIGHT))) return true;
        }
        return false;
    }

    /** Gives back at once, in a chunk that loaded after a death, the corruption a dead part held and no live tissue holds. */
    static void releaseLoaded(ServerLevel level, LevelChunk ch) {
        ChunkPos cp = ch.getPos();
        int qx0 = cp.x << 2, qz0 = cp.z << 2, qy0 = ch.getMinSection() << 2, qy1 = (ch.getMaxSection() << 2) - 1;
        int pad = (int) Math.ceil(OrganicHalo.reach()) + 1;
        OrganicHalo.Field gone = field(level, qx0, qy0, qz0, qx0 + 3, qy1, qz0 + 3, pad, (h, i) -> dead(h.cell(i), h.organ(i)),
                List.of());
        OrganicHalo.Field live = field(level, qx0, qy0, qz0, qx0 + 3, qy1, qz0 + 3, pad,
                (h, i) -> !h.gone(i) && !dead(h.cell(i), h.organ(i)), List.of());
        long seed = level.getSeed();
        Set<Long> q = new HashSet<>();
        for (int qx = qx0; qx < qx0 + 4; qx++) {
            for (int qz = qz0; qz < qz0 + 4; qz++) {
                for (int qy = qy0; qy <= qy1; qy++) {
                    if (!gone.held(seed, qx, qy, qz) || live.held(seed, qx, qy, qz)) continue;
                    if (ch.getNoiseBiome(qx, qy, qz).is(OrganicBlight.BLIGHT)) q.add(BlockPos.asLong(qx, qy, qz));
                }
            }
        }
        if (q.isEmpty()) return;
        BeyondEndBiomeSource source = source(level);
        var sampler = level.getChunkSource().randomState().sampler();
        ch.fillBiomesFromNoise((qx, qy, qz, s) -> q.contains(BlockPos.asLong(qx, qy, qz))
                ? source.getNoiseBiome(qx, qy, qz, s) : ch.getNoiseBiome(qx, qy, qz), sampler);
        ch.setUnsaved(true);
        OrganicBlight.resendLater(level, List.of(ch));
        int ground = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) ground += restoreGround(level, level.getChunk(cp.x + dx, cp.z + dz));
        }
        if (TRACE) TheBeyond.LOGGER.info("ORG release on load chunk {} {} | quarts {} | ground {}", cp.x, cp.z, q.size(), ground);
    }

    // swapped ground whose block no longer reads the corruption takes back the block it had
    private static int restoreGround(ServerLevel level, LevelChunk ch) {
        OrganicSurface.Kept kept = ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
        if (kept == null) return 0;
        int n = 0, minY = ch.getMinBuildHeight();
        ChunkPos cp = ch.getPos();
        BlockState corrupted = OrganicSurface.corrupted();
        for (int i = 0; i < kept.pos.length; i++) {
            int p = kept.pos[i];
            BlockPos at = new BlockPos(cp.getMinBlockX() + (p & 15), minY + (p >>> 8), cp.getMinBlockZ() + (p >> 4 & 15));
            BlockState cur = level.getBlockState(at), had = kept.palette.get(kept.state[i]);
            if (!(OrganicSurface.plant(had) ? cur.isAir() : cur == corrupted)
                    || OrganicTrail.held(kept, i, at, x -> level.getBiome(x).is(OrganicBlight.BLIGHT))) {
                continue;
            }
            set(level, at, had);
            n++;
        }
        return n;
    }
}
