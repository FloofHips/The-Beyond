package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.thebeyond.common.worldgen.OrganicWorld.CORRUPT;
import static com.thebeyond.common.worldgen.OrganicWorld.DIRS;
import static com.thebeyond.common.worldgen.OrganicWorld.QUART;
import static com.thebeyond.common.worldgen.OrganicWorld.RECEDE;
import static com.thebeyond.common.worldgen.OrganicWorld.TISSUE;
import static com.thebeyond.common.worldgen.OrganicWorld.TRACE;
import static com.thebeyond.common.worldgen.OrganicWorld.dead;
import static com.thebeyond.common.worldgen.OrganicWorld.set;
import static com.thebeyond.common.worldgen.OrganicWorld.source;

/** What a fire cleared grows back once it is out, its front walking in reverse after the bulbs' pause. */
final class OrganicReturn {
    // once the fire is out the corruption and the cleared network come back after the bulbs' pause, the front reversed
    static final int RETURN_PAUSE = 200, RETURN_STRETCH = 2;

    private OrganicReturn() {}

    static final class Return {
        final long fire, start;
        // tick, QUART / TISSUE / CORRUPT, position (a quart for QUART), kept index * 2 + plant for CORRUPT
        final List<long[]> events;
        int next, quarts, tissue, ground;

        Return(long fire, long start, List<long[]> events) {
            this.fire = fire;
            this.start = start;
            this.events = events;
            this.events.sort(Comparator.comparingLong(e -> e[0]));
        }
    }

    /** The clearing goes back to the organism: network, quarts, ground and plants regrow from its edge in toward the fire. */
    static void giveBack(ServerLevel level, OrganicLight.Bonfire b, long fire, long start) {
        OrganicLife.Data d = OrganicLife.Data.get(level);
        List<OrganicLight.Bonfire> others = new ArrayList<>();
        for (OrganicLight.Bonfire o : OrganicLight.near(level, new ChunkPos(b.fx() >> 4, b.fz() >> 4), source(level))) {
            if (o.lit() && BlockPos.asLong(o.fx(), o.fy(), o.fz()) != fire && OrganicFire.stillBurning(level, o)) others.add(o);
        }
        SimplexNoise n = new SimplexNoise(new XoroshiroRandomSource(level.getSeed() ^ 0x6275726EL));
        List<long[]> ev = new ArrayList<>();
        int r = (int) Math.ceil(OrganicLight.REACH) + 1;
        for (int cx = (b.fx() - r) >> 4; cx <= (b.fx() + r) >> 4; cx++) {
            for (int cz = (b.fz() - r) >> 4; cz <= (b.fz() + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                if (h == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < h.size(); i++) {
                    if (!h.cleared(i) || dead(h.cell(i), h.organ(i))) continue;
                    int p = h.packed(i);
                    int x = (cx << 4) + (p & 15), y = minY + (p >>> 8), z = (cz << 4) + (p >> 4 & 15);
                    if (OrganicLight.inAny(others, x + 0.5, y + 0.5, z + 0.5)) continue;
                    long when = OrganicFire.frontTick(b, n, x + 0.5, y + 0.5, z + 0.5);
                    ev.add(new long[]{when, TISSUE, BlockPos.asLong(x, y, z), 0});
                }
            }
        }
        Set<Long> back = new HashSet<>();
        long[] released = d.released.getOrDefault(fire, new long[0]);
        OrganicHalo.Field again = RECEDE ? regrowing(level, released, others) : null;
        for (long q : released) {
            double ex = BlockPos.getX(q) * 4 + 2, ey = BlockPos.getY(q) * 4 + 2, ez = BlockPos.getZ(q) * 4 + 2;
            if (OrganicLight.inAny(others, ex, ey, ez)) continue;
            if (again != null && !again.held(level.getSeed(), BlockPos.getX(q), BlockPos.getY(q), BlockPos.getZ(q))) continue;
            back.add(q);
            ev.add(new long[]{OrganicFire.frontTick(b, n, ex, ey, ez), QUART, q, 0});
        }
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> blight = biomes.getHolderOrThrow(OrganicBlight.BLIGHT);
        BiomeManager.NoiseBiomeSource backed = (qx, qy, qz) -> back.contains(BlockPos.asLong(qx, qy, qz)) ? blight
                : level.getNoiseBiome(qx, qy, qz);
        BiomeManager before = new BiomeManager(backed, BiomeManager.obfuscateSeed(level.getSeed()));
        int g = (int) Math.ceil(OrganicLight.REACH + OrganicHalo.reach() + 2) + OrganicTrail.MAX;
        for (int cx = (b.fx() - g) >> 4; cx <= (b.fx() + g) >> 4; cx++) {
            for (int cz = (b.fz() - g) >> 4; cz <= (b.fz() + g) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                OrganicSurface.Kept kept = ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
                if (kept == null) continue;
                int minY = ch.getMinBuildHeight();
                for (int i = 0; i < kept.pos.length; i++) {
                    int p = kept.pos[i];
                    BlockPos at = new BlockPos((cx << 4) + (p & 15), minY + (p >>> 8), (cz << 4) + (p >> 4 & 15));
                    BlockState had = kept.palette.get(kept.state[i]);
                    if (ch.getBlockState(at) != had
                            || !OrganicTrail.held(kept, i, at, x -> before.getBiome(x).is(OrganicBlight.BLIGHT))) continue;
                    boolean plant = OrganicSurface.plant(had);
                    long when = OrganicFire.frontTick(b, n, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                    ev.add(new long[]{when, CORRUPT, at.asLong(), i * 2L + (plant ? 1 : 0)});
                }
            }
        }
        long top = 0;
        for (long[] e : ev) top = Math.max(top, e[0]);
        for (long[] e : ev) {
            long t = RETURN_STRETCH * (top - e[0]);
            e[0] = e[1] == QUART ? Math.max(0, t - OrganicFire.LEAD)
                    : e[1] == CORRUPT && (e[3] & 1) == 1 ? Math.max(0, t - 1) : t;
        }
        OrganicLevelState.of(level).returns.add(new Return(fire, start, ev));
        if (TRACE) TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG return %d %d %d | in %d ticks | tissue %d | quarts %d"
                + " | ground and plants %d | last at tick %d", b.fx(), b.fy(), b.fz(), start - level.getGameTime(),
                ev.stream().filter(e -> e[1] == TISSUE).count(), back.size(), ev.stream().filter(e -> e[1] == CORRUPT).count(),
                RETURN_STRETCH * top));
    }

    /** Halo of the tissue that stands once the return is done: live or cleared, of a live part, out of the burning fires. */
    private static OrganicHalo.Field regrowing(ServerLevel level, long[] quarts, List<OrganicLight.Bonfire> others) {
        if (quarts.length == 0) return null;
        int qx0 = Integer.MAX_VALUE, qy0 = Integer.MAX_VALUE, qz0 = Integer.MAX_VALUE;
        int qx1 = Integer.MIN_VALUE, qy1 = Integer.MIN_VALUE, qz1 = Integer.MIN_VALUE;
        for (long q : quarts) {
            qx0 = Math.min(qx0, BlockPos.getX(q));
            qy0 = Math.min(qy0, BlockPos.getY(q));
            qz0 = Math.min(qz0, BlockPos.getZ(q));
            qx1 = Math.max(qx1, BlockPos.getX(q));
            qy1 = Math.max(qy1, BlockPos.getY(q));
            qz1 = Math.max(qz1, BlockPos.getZ(q));
        }
        return OrganicRecession.field(level, qx0, qy0, qz0, qx1, qy1, qz1, (int) Math.ceil(OrganicHalo.reach()) + 1,
                (h, i) -> (!h.gone(i) || h.cleared(i)) && !dead(h.cell(i), h.organ(i)), others);
    }

    static boolean advance(ServerLevel level, Return r, long now) {
        if (now < r.start) return false;
        long t = now - r.start;
        Map<Long, Set<Long>> byChunk = new HashMap<>();
        BlockState corrupted = OrganicSurface.corrupted();
        while (r.next < r.events.size() && r.events.get(r.next)[0] <= t) {
            long[] e = r.events.get(r.next++);
            if (e[1] == QUART) {
                byChunk.computeIfAbsent(ChunkPos.asLong(BlockPos.getX(e[2]) >> 2, BlockPos.getZ(e[2]) >> 2), k -> new HashSet<>())
                        .add(e[2]);
                continue;
            }
            BlockPos p = BlockPos.of(e[2]);
            if (!level.isLoaded(p)) continue;
            LevelChunk ch = level.getChunkAt(p);
            BlockState cur = level.getBlockState(p);
            if (e[1] == TISSUE) {
                OrganicTissue.Held h = ch.getExistingDataOrNull(BeyondAttachments.ORG_TISSUE);
                int i = h == null ? -1 : h.find(OrganicTissue.pack(p.getX(), p.getY(), p.getZ(), ch.getMinBuildHeight()));
                if (i < 0 || !h.cleared(i)) continue;
                if (OrganicDeath.atrophied(level, h, i, p.getX(), p.getY(), p.getZ())) {
                    h.remove(i);
                    ch.setUnsaved(true);
                    continue;
                }
                BlockState rock = h.replaced(i);
                if (!cur.isAir() && cur != rock || touchesBlockEntity(level, p)) continue;
                BlockState grown = h.regrow(i);
                ch.setUnsaved(true);
                if (grown == null) continue;
                set(level, p, grown);
                r.tissue++;
            } else {
                OrganicSurface.Kept kept = ch.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
                int i = (int) (e[3] >> 1);
                if (kept == null || i >= kept.pos.length || cur != kept.palette.get(kept.state[i])) continue;
                set(level, p, (e[3] & 1) == 1 ? Blocks.AIR.defaultBlockState() : corrupted);
                r.ground++;
            }
        }
        if (!byChunk.isEmpty()) {
            Holder<Biome> blight = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(OrganicBlight.BLIGHT);
            var sampler = level.getChunkSource().randomState().sampler();
            List<ChunkAccess> changed = new ArrayList<>();
            for (Map.Entry<Long, Set<Long>> en : byChunk.entrySet()) {
                int cx = ChunkPos.getX(en.getKey()), cz = ChunkPos.getZ(en.getKey());
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                Set<Long> q = en.getValue();
                ch.fillBiomesFromNoise((qx, qy, qz, s) -> q.contains(BlockPos.asLong(qx, qy, qz)) ? blight
                        : ch.getNoiseBiome(qx, qy, qz), sampler);
                ch.setUnsaved(true);
                changed.add(ch);
                r.quarts += q.size();
            }
            if (!changed.isEmpty()) OrganicBlight.resendLater(level, changed);
        }
        if (r.next < r.events.size()) return false;
        OrganicLife.Data d = OrganicLife.Data.get(level);
        d.handLit.remove(r.fire);
        d.released.remove(r.fire);
        d.exposedBy.remove(r.fire);
        d.setDirty();
        OrganicLight.dropHandLit(r.fire);
        if (TRACE) TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG return done %d %d %d | ticks %d | regrown %d | quarts %d"
                + " | ground and plants %d", BlockPos.getX(r.fire), BlockPos.getY(r.fire), BlockPos.getZ(r.fire), t, r.tissue,
                r.quarts, r.ground));
        return true;
    }

    /** Tissue never grows back against a block entity, so a chest still opens and a machine keeps its side free. */
    static boolean touchesBlockEntity(ServerLevel level, BlockPos p) {
        for (Direction d : DIRS) {
            if (level.getBlockState(p.relative(d)).hasBlockEntity()) return true;
        }
        return false;
    }
}
