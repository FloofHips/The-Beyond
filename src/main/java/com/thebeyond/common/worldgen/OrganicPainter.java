package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.block.OrganicBulbBlock;
import com.thebeyond.common.debug.OrganicWatch;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.worldgen.OrganicGrowth.Atrophy;
import com.thebeyond.common.worldgen.OrganicGrowth.Grown;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;

import static com.thebeyond.common.worldgen.OrganicExperiment.LINE;
import static com.thebeyond.common.worldgen.OrganicExperiment.OWN_BLOCKS;
import static com.thebeyond.common.worldgen.OrganicExperiment.state;
import static com.thebeyond.common.worldgen.OrganicExperiment.tissue;

/** Paints the organic network into a generating chunk, with the corruption halo around its light-shy tissue. */
public final class OrganicPainter {
    private static final Map<Byte, String> KIND_NAME = Map.of(OrganicNetwork.TUBE, "tube", OrganicNetwork.BULB, "bulb",
            OrganicNetwork.FILM, "film", OrganicNetwork.CROSS, "cross", OrganicNetwork.AERIAL, "aerial", OrganicNetwork.PENDANT,
            "pendant", OrganicNetwork.TUNNEL, "tunnel");

    private OrganicPainter() {}

    public static void paintChunk(WorldGenLevel level, ChunkAccess chunk, BeyondEndBiomeSource source, ChunkGenerator gen,
                                  StructureManager structures) {
        ChunkPos cp = chunk.getPos();
        try {
            int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
            OrganicGrowth.Where w = OrganicGrowth.Where.of(level, chunk, source, gen);
            long start = System.nanoTime(), haloNs = 0;
            double cx = cp.getMinBlockX() + 8, cz = cp.getMinBlockZ() + 8;
            boolean light = !OrganicLight.SHY.isEmpty(), halo = light && OrganicLight.HALO > 0;
            double reach = halo ? OrganicHalo.reach() : 0;
            List<OrganicLight.Bonfire> bonfires = light || OrganicLight.EDGE_LIT ? OrganicLight.near(level, cp, source)
                    : List.of();
            markLit(chunk, cp, bonfires);
            List<OrganicLight.Bonfire> zones = OrganicLight.litReaching(bonfires, cp, 0);
            List<OrganicLight.Bonfire> haloZones = halo ? OrganicLight.litReaching(bonfires, cp, reach + 1) : zones;
            List<SubSites.Cell> cells = OrganicGrowth.cellsCovering(w, cx, cz, reach);
            OrganicBlight.Mask mask = halo ? new OrganicBlight.Mask(chunk.getSectionsCount()) : null;
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            boolean refs = !chunk.getAllReferences().isEmpty();
            long key = ChunkPos.asLong(cp.x, cp.z);
            int placed = 0, purified = 0, lined = 0, pieces = 0;
            Map<String, Integer> over = new TreeMap<>(), cleared = new TreeMap<>();
            List<Grown> grown = new ArrayList<>();
            List<int[]> line = new ArrayList<>(), drips = new ArrayList<>();
            Set<Long> wrote = new HashSet<>();
            long waited = System.nanoTime();
            for (SubSites.Cell c : cells) OrganicGrowth.full(w, c);
            waited = System.nanoTime() - waited;
            if (waited >= 1_000_000) {
                TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG wait chunk %d %d | %d ms", cp.x, cp.z, waited / 1_000_000));
            }
            OrganicTissue.Held tissue = new OrganicTissue.Held();
            for (SubSites.Cell c : cells) {
                Grown gr = OrganicGrowth.full(w, c);
                grown.add(gr);
                long cellKey = SubSites.Network.key(c);
                boolean shy = OrganicLight.shy(gr.setup());
                // an organism that died before this chunk generated is born withered here, and so is a dead bulb's part
                Set<Integer> dead = OrganicWorld.DEAD.get(cellKey);
                if (dead != null && dead.contains(OrganicWorld.WHOLE)) continue;
                Atrophy atrophy = dead != null && !dead.isEmpty() && OrganicGrowth.atrophies(gr.setup())
                        ? OrganicGrowth.atrophied(w, c, dead) : null;
                if (shy && halo) {
                    long t = System.nanoTime();
                    stampHalo(mask, gr, cp, chunk.getMinSection(), haloZones, dead, atrophy, level.getSeed());
                    haloNs += System.nanoTime() - t;
                }
                int[] mine = gr.byChunk().get(key);
                if (mine == null) continue;
                float[] table = gr.organs().clone();
                for (int o = 0; o < table.length; o += OrganicNetwork.ORGAN) {
                    table[o] += gr.x0();
                    table[o + 1] += gr.y0();
                    table[o + 2] += gr.z0();
                }
                int[] cores = cores(gr, table);
                tissue.organism(cellKey, gr.setup(), table);
                int plane = gr.ny() * gr.nz();
                boolean shielded = shy && !zones.isEmpty() && OrganicLife.bornShielded(gr.setup(), gr.organs(), dead);
                for (int s : mine) {
                    byte k = gr.kinds()[s];
                    if (k == LINE) {
                        line.add(new int[]{grown.size() - 1, s});
                        continue;
                    }
                    if (dead != null && dead.contains(gr.owner()[s])) continue;
                    int i = gr.vox()[s];
                    if (atrophy != null && atrophy.lost(i)) continue;
                    int x = gr.x0() + i / plane, y = gr.y0() + (i / gr.nz()) % gr.ny(), z = gr.z0() + i % gr.nz();
                    at.set(x, y, z);
                    BlockState old = chunk.getBlockState(at);
                    boolean air = old.isAir();
                    boolean piece = false;
                    if (!air && (k != OrganicNetwork.TUNNEL || old.hasBlockEntity() || (OWN_BLOCKS && tissue(old))
                            || (piece = refs && structures.getStructureWithPieceAt(at, h -> true).isValid()))) {
                        if (piece) pieces++;
                        if (OrganicWatch.ACTIVE) OrganicWatch.skipped(at, old);
                        continue;
                    }
                    byte kind = air && k == OrganicNetwork.TUNNEL ? OrganicNetwork.TUBE : k;
                    boolean exposed = false;
                    if (shy && !zones.isEmpty() && OrganicLight.inAny(zones, x + 0.5, y + 0.5, z + 0.5)) {
                        OrganicLight.Action a = OrganicLight.action(kind);
                        if (a == OrganicLight.Action.REMOVE || a == OrganicLight.Action.ROCK) {
                            cleared.merge(KIND_NAME.getOrDefault(kind, "kind" + kind), 1, Integer::sum);
                            continue;
                        }
                        if (a == OrganicLight.Action.BREAKABLE && kind == OrganicNetwork.BULB && !(shielded && nucleus(gr, s))) {
                            exposed = true;
                            purified++;
                        }
                    }
                    BlockState b = state(kind, gr.dnet()[s], exposed);
                    int own = gr.owner()[s];
                    if (b.getBlock() instanceof OrganicBulbBlock && own >= 0 && own < cores.length && cores[own] == s) {
                        b = b.setValue(OrganicBulbBlock.CORE, true);
                    }
                    if (!air) over.merge(old.getBlock().getDescriptionId(), 1, Integer::sum);
                    chunk.setBlockState(at, b, false);
                    if (OrganicWatch.ACTIVE) OrganicWatch.painted(at, b.getBlock());
                    byte drip = halo && OrganicHalo.STREAKS && OrganicHalo.flow() ? drip(chunk, x, y, z, minY) : 0;
                    tissue.add(OrganicTissue.pack(x, y, z, minY), kind, cellKey, gr.owner()[s], air ? null : old,
                            gr.thick()[s], drip);
                    if (drip != 0) drips.add(new int[]{x, y, z, drip});
                    wrote.add(at.asLong());
                    placed++;
                }
            }
            if (tissue.size() > 0) {
                chunk.setData(BeyondAttachments.ORG_TISSUE, tissue);
                chunk.setUnsaved(true);
            }
            Set<Integer> held = new LinkedHashSet<>();
            for (int[] e : line) {
                Grown gr = grown.get(e[0]);
                int i = gr.vox()[e[1]], plane = gr.ny() * gr.nz();
                at.set(gr.x0() + i / plane, gr.y0() + (i / gr.nz()) % gr.ny(), gr.z0() + i % gr.nz());
                if (wrote.contains(at.asLong())) continue;
                if (!OrganicZoneLine.drawn(gr, cells.get(e[0]), e[1], w)) continue;
                held.add((at.getY() - minY) << 8 | (at.getZ() & 15) << 4 | (at.getX() & 15));
            }
            lined = held.size();
            if (lined > 0) {
                if (mask == null) mask = new OrganicBlight.Mask(chunk.getSectionsCount());
                mask.line = held.stream().mapToInt(Integer::intValue).toArray();
            }
            int blight = 0, clearing = 0;
            if (mask != null) {
                for (int[] d : drips) markDrip(mask, cp, chunk.getMinSection(), d);
                int[] n = clearZones(mask, cp, chunk.getMinSection(), zones);
                blight = n[0];
                clearing = n[1];
                if (!mask.empty()) {
                    chunk.setData(BeyondAttachments.ORG_BLIGHT, mask);
                    chunk.setUnsaved(true);
                }
            }
            if (placed > 0 || lined > 0 || !cleared.isEmpty() || blight > 0 || pieces > 0) {
                TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG chunk %d %d | cells %d placed %d line %d | over %s | zones"
                        + " %d cleared %s purified %d | blight %d clearing %d | paint %.2f ms halo %.2f ms | pieces %d",
                        cp.x, cp.z,
                        cells.size(), placed, lined, over, zones.size(), cleared, purified, blight, clearing,
                        (System.nanoTime() - start - waited) / 1e6, haloNs / 1e6, pieces));
            }
            if (OrganicWatch.ACTIVE) OrganicWatch.flush();
        } catch (Throwable ex) {
            TheBeyond.LOGGER.error("ORG_FAILURE chunk {} {}", cp.x, cp.z, ex);
        }
    }

    /** How far an exposed rock face runs down from this tissue, a wall beside it or a cliff under it (0 under two blocks). */
    private static byte drip(ChunkAccess chunk, int x, int y, int z, int minY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int best = 0, side = 0;
        for (int s = 0; s < 4; s++) {
            int dx = s == 0 ? 1 : s == 1 ? -1 : 0, dz = s == 2 ? 1 : s == 3 ? -1 : 0;
            if ((x + dx) >> 4 != x >> 4 || (z + dz) >> 4 != z >> 4) continue;
            int wall = 0, cliff = 0;
            while (wall < OrganicHalo.STREAK_MAX && y - wall - 1 >= minY && chunk.getBlockState(p.set(x, y - wall - 1, z)).isAir()
                    && OrganicSurface.ground(chunk.getBlockState(p.set(x + dx, y - wall - 1, z + dz)))) wall++;
            while (cliff < OrganicHalo.STREAK_MAX && y - cliff - 1 >= minY
                    && OrganicSurface.ground(chunk.getBlockState(p.set(x, y - cliff - 1, z)))
                    && chunk.getBlockState(p.set(x + dx, y - cliff - 1, z + dz)).isAir()) cliff++;
            int k = Math.max(wall, cliff);
            if (k > best) {
                best = k;
                side = s;
            }
        }
        return best >= 2 ? OrganicHalo.drip(best, side) : 0;
    }

    private static void markDrip(OrganicBlight.Mask m, ChunkPos cp, int minSection, int[] d) {
        int len = OrganicHalo.dripLength(d[3]);
        int[][] cols = {{d[0], d[2]}, {d[0] + OrganicHalo.dripDx(d[3]), d[2] + OrganicHalo.dripDz(d[3])}};
        for (int[] c : cols) {
            if (c[0] >> 4 != cp.x || c[1] >> 4 != cp.z) continue;
            for (int qy = Math.floorDiv(d[1] - len, 4); qy <= Math.floorDiv(d[1] - 1, 4); qy++) {
                int sec = (qy >> 2) - minSection;
                if (sec >= 0 && sec < m.blight.length) m.blight[sec] |= 1L << OrganicBlight.Mask.bit(c[0] >> 2, qy, c[1] >> 2);
            }
        }
    }

    /** Each organ's bulb voxel nearest its centre, ties by position: the block OrganicLife's Organ sorts first and calls core. */
    private static int[] cores(Grown gr, float[] table) {
        int n = table.length / OrganicNetwork.ORGAN, plane = gr.ny() * gr.nz();
        int[] best = new int[n];
        double[] dist = new double[n];
        long[] at = new long[n];
        Arrays.fill(best, -1);
        for (int s = 0; s < gr.vox().length; s++) {
            int o = gr.owner()[s];
            if (o < 0 || o >= n || gr.kinds()[s] != OrganicNetwork.BULB) continue;
            int i = gr.vox()[s];
            int x = gr.x0() + i / plane, y = gr.y0() + (i / gr.nz()) % gr.ny(), z = gr.z0() + i % gr.nz();
            int b = o * OrganicNetwork.ORGAN;
            double dx = x + 0.5 - table[b], dy = y + 0.5 - table[b + 1], dz = z + 0.5 - table[b + 2];
            double d = dx * dx + dy * dy + dz * dz;
            long p = BlockPos.asLong(x, y, z);
            if (best[o] < 0 || d < dist[o] || (d == dist[o] && p < at[o])) {
                best[o] = s;
                dist[o] = d;
                at[o] = p;
            }
        }
        return best;
    }

    private static boolean nucleus(Grown gr, int s) {
        int o = gr.owner()[s];
        return o >= 0 && gr.organs()[o * OrganicNetwork.ORGAN + 4] == 0;
    }

    /** Sets the fire of each bonfire in this chunk to its decision and logs it once, from the chunk that holds the fire. */
    private static void markLit(ChunkAccess chunk, ChunkPos cp, List<OrganicLight.Bonfire> all) {
        for (OrganicLight.Bonfire b : all) {
            if (b.virtual() || b.fx() >> 4 != cp.x || b.fz() >> 4 != cp.z) continue;
            BlockPos p = new BlockPos(b.fx(), b.fy(), b.fz());
            BlockState s = chunk.getBlockState(p);
            if (!(s.getBlock() instanceof BonfireBlock)) {
                TheBeyond.LOGGER.warn("{} | fire block missing, found {}", OrganicLight.describe(b), s);
                continue;
            }
            if (OrganicLight.EDGE_LIT && s.getValue(BonfireBlock.LIT) != b.lit()) {
                s = s.setValue(BonfireBlock.LIT, b.lit());
                chunk.setBlockState(p, s, false);
            }
            TheBeyond.LOGGER.info("{} | placed {}", OrganicLight.describe(b), s.getValue(BonfireBlock.LIT));
        }
    }

    /** Marks this chunk's quarts whose centre lies within the halo of a voxel the light has not cleaned. */
    private static void stampHalo(OrganicBlight.Mask m, Grown gr, ChunkPos cp, int minSection,
                                  List<OrganicLight.Bonfire> zones, Set<Integer> dead, Atrophy atrophy, long seed) {
        if (!OrganicWorld.RECEDE) {
            dead = null;
            atrophy = null;
        }
        if (OrganicHalo.flow()) {
            stampFlowHalo(m, gr, cp, minSection, zones, dead, atrophy, seed);
            return;
        }
        double d = OrganicLight.HALO, d2 = d * d;
        int plane = gr.ny() * gr.nz();
        int qx0 = cp.x << 2, qz0 = cp.z << 2, qy0 = minSection << 2, qy1 = qy0 + (m.blight.length << 2) - 1;
        double bx0 = cp.getMinBlockX() - d, bx1 = cp.getMaxBlockX() + 1 + d, bz0 = cp.getMinBlockZ() - d;
        double bz1 = cp.getMaxBlockZ() + 1 + d;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int[] list = gr.byChunk().get(ChunkPos.asLong(cp.x + dx, cp.z + dz));
                if (list == null) continue;
                for (int s : list) {
                    if (dead != null && dead.contains(gr.owner()[s])) continue;
                    int i = gr.vox()[s];
                    if (atrophy != null && atrophy.lost(i)) continue;
                    double px = gr.x0() + i / plane + 0.5, py = gr.y0() + (i / gr.nz()) % gr.ny() + 0.5;
                    double pz = gr.z0() + i % gr.nz() + 0.5;
                    if (px < bx0 || px > bx1 || pz < bz0 || pz > bz1) continue;
                    if (!zones.isEmpty() && OrganicLight.inAny(zones, px, py, pz)) continue;
                    int xa = Math.max(qx0, Mth.floor((px - d) / 4)), xb = Math.min(qx0 + 3, Mth.floor((px + d) / 4));
                    int ya = Math.max(qy0, Mth.floor((py - d) / 4)), yb = Math.min(qy1, Mth.floor((py + d) / 4));
                    int za = Math.max(qz0, Mth.floor((pz - d) / 4)), zb = Math.min(qz0 + 3, Mth.floor((pz + d) / 4));
                    for (int qx = xa; qx <= xb; qx++) {
                        double ex = qx * 4 + 2 - px;
                        for (int qy = ya; qy <= yb; qy++) {
                            double ey = qy * 4 + 2 - py;
                            for (int qz = za; qz <= zb; qz++) {
                                double ez = qz * 4 + 2 - pz;
                                if (ex * ex + ey * ey + ez * ez > d2) continue;
                                m.blight[(qy >> 2) - minSection] |= 1L << OrganicBlight.Mask.bit(qx, qy, qz);
                            }
                        }
                    }
                }
            }
        }
    }

    /** As stampHalo, with each voxel's halo set by its width and the edge by noise, the rule the running world reads back. */
    private static void stampFlowHalo(OrganicBlight.Mask m, Grown gr, ChunkPos cp, int minSection,
                                      List<OrganicLight.Bonfire> zones, Set<Integer> dead, Atrophy atrophy, long seed) {
        double reach = OrganicHalo.reach();
        int plane = gr.ny() * gr.nz();
        int qx0 = cp.x << 2, qz0 = cp.z << 2, qy0 = minSection << 2, qy1 = qy0 + (m.blight.length << 2) - 1;
        double bx0 = cp.getMinBlockX() - reach, bx1 = cp.getMaxBlockX() + 1 + reach, bz0 = cp.getMinBlockZ() - reach;
        double bz1 = cp.getMaxBlockZ() + 1 + reach;
        OrganicHalo.Field f = new OrganicHalo.Field(qx0, qy0, qz0, qx0 + 3, qy1, qz0 + 3, false);
        double fed = OrganicHalo.CORE > 0 ? OrganicLife.bornFed(gr.organs(), dead) : 0;
        boolean any = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int[] list = gr.byChunk().get(ChunkPos.asLong(cp.x + dx, cp.z + dz));
                if (list == null) continue;
                for (int s : list) {
                    if (gr.kinds()[s] == LINE || dead != null && dead.contains(gr.owner()[s])) continue;
                    if (zones.isEmpty() && atrophy == null && gr.hidden().get(s)) continue;
                    int i = gr.vox()[s];
                    if (atrophy != null && atrophy.lost(i)) continue;
                    double px = gr.x0() + i / plane + 0.5, py = gr.y0() + (i / gr.nz()) % gr.ny() + 0.5;
                    double pz = gr.z0() + i % gr.nz() + 0.5;
                    if (px < bx0 || px > bx1 || pz < bz0 || pz > bz1) continue;
                    if (!zones.isEmpty() && OrganicLight.inAny(zones, px, py, pz)) continue;
                    f.add(px, py, pz, OrganicHalo.radius(gr.thick()[s], nucleus(gr, s) ? fed : 0), 0);
                    any = true;
                }
            }
        }
        if (!any) return;
        for (int qx = qx0; qx < qx0 + 4; qx++) {
            for (int qy = qy0; qy <= qy1; qy++) {
                for (int qz = qz0; qz < qz0 + 4; qz++) {
                    if (f.held(seed, qx, qy, qz)) m.blight[(qy >> 2) - minSection] |= 1L << OrganicBlight.Mask.bit(qx, qy, qz);
                }
            }
        }
    }

    /** Quarts inside a clearing keep the generated biome, or take the released one when configured. Returns both counts. */
    private static int[] clearZones(OrganicBlight.Mask m, ChunkPos cp, int minSection, List<OrganicLight.Bonfire> zones) {
        boolean keep = OrganicLight.RELEASED.equals("genuine");
        int blight = 0, clearing = 0;
        for (int s = 0; s < m.blight.length; s++) {
            long bits = m.blight[s];
            while (bits != 0) {
                int bit = Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
                int qx = (cp.x << 2) + (bit & 3), qz = (cp.z << 2) + (bit >> 2 & 3), qy = ((s + minSection) << 2) + (bit >> 4);
                if (!zones.isEmpty() && OrganicLight.inAny(zones, qx * 4 + 2, qy * 4 + 2, qz * 4 + 2)) {
                    m.blight[s] &= ~(1L << bit);
                    if (!keep) {
                        m.clearing[s] |= 1L << bit;
                        clearing++;
                    }
                } else {
                    blight++;
                }
            }
        }
        return new int[]{blight, clearing};
    }
}
