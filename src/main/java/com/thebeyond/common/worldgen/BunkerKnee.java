package com.thebeyond.common.worldgen;

import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/** Rounds the knee where a buried room's envelope meets the island, laying what a closing fills only because of the envelope. */
public final class BunkerKnee {

    /** Twice the radius is the 16 blocks a decorating chunk may read on each side, so neighbours agree. */
    static final int RADIUS = 8;
    private static final int R2 = RADIUS * RADIUS;
    private static final int LAT = BeyondStructureCarver.ENVELOPE_LAT;
    private static final int DOWN = BeyondStructureCarver.ENVELOPE_DOWN;
    private static final int REACH = LAT + RADIUS;
    private static final int PAD = 2 * RADIUS;
    private static final int INF = 1 << 28;

    private BunkerKnee() {}

    public static int fill(WorldGenLevel level, ChunkAccess chunk, List<BeyondEndChunkGenerator.CarveMask> masks) {
        ChunkPos cp = chunk.getPos();
        try {
            int n = fillOrThrow(level, chunk, masks);
            if (n > 0) {
                BeyondGenDiagnostics.kneeLaid.addAndGet(n);
                if (BeyondGenDiagnostics.loggedKnees.size() < 2000 && BeyondGenDiagnostics.loggedKnees.add(cp.toLong())) {
                    com.thebeyond.TheBeyond.LOGGER.info("[Beyond] knee at chunk [{},{}]: {} blocks laid", cp.x, cp.z, n);
                }
            }
            return n;
        } catch (Throwable t) {
            if (!BeyondGenDiagnostics.loggedKneeError) {
                BeyondGenDiagnostics.loggedKneeError = true;
                com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] knee at chunk [{},{}] failed, left as it was: {}", cp.x, cp.z, t.toString());
            }
            return 0;
        }
    }

    private static int fillOrThrow(WorldGenLevel level, ChunkAccess chunk, List<BeyondEndChunkGenerator.CarveMask> masks) {
        ChunkPos cp = chunk.getPos();
        final int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
        final int gx0 = bx - PAD, gz0 = bz - PAD, nx = 16 + 2 * PAD, nz = 16 + 2 * PAD;
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<int[]> knees = kneeColumns(masks, reg, gx0 - LAT, gz0 - LAT, gx0 + nx - 1 + LAT, gz0 + nz - 1 + LAT);
        if (knees.isEmpty()) return 0;
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        for (int[] k : knees) {
            yMin = Math.min(yMin, k[2] - DOWN - RADIUS);
            yMax = Math.max(yMax, k[3]);
        }
        final int gy0 = Math.max(level.getMinBuildHeight(), yMin - PAD);
        final int gy1 = Math.min(level.getMaxBuildHeight() - 1, yMax + PAD);
        final int ny = gy1 - gy0 + 1;
        if (ny <= 0) return 0;
        final int n = nx * ny * nz;

        // Per knee column, the heights a knee may fill in this chunk and the heights its envelope wraps in the window.
        boolean[] domain = new boolean[256 * ny], zone = new boolean[nx * nz * ny];
        for (int[] k : knees) {
            int x = k[0], z = k[1];
            int fLo = Math.max(gy0, k[2] - DOWN - RADIUS), fHi = Math.min(gy1, k[4]);
            for (int cz = Math.max(bz, z - REACH); cz <= Math.min(bz + 15, z + REACH); cz++) {
                for (int cx = Math.max(bx, x - REACH); cx <= Math.min(bx + 15, x + REACH); cx++) {
                    int c = (cx - bx) + (cz - bz) * 16;
                    for (int y = fLo; y <= fHi; y++) domain[c * ny + y - gy0] = true;
                }
            }
            int zLo = Math.max(gy0, k[2] - DOWN), zHi = Math.min(gy1, k[3]);
            for (int wz = Math.max(gz0, z - LAT); wz <= Math.min(gz0 + nz - 1, z + LAT); wz++) {
                for (int wx = Math.max(gx0, x - LAT); wx <= Math.min(gx0 + nx - 1, x + LAT); wx++) {
                    int c = (wx - gx0) + (wz - gz0) * nx;
                    for (int y = zLo; y <= zHi; y++) zone[c * ny + y - gy0] = true;
                }
            }
        }

        boolean[] open = new boolean[n];
        for (BeyondEndChunkGenerator.CarveMask m : masks) markStructure(m, open, gx0, gy0, gz0, nx, ny, nz);

        // Rock is full non-tree blocks outside the structure, and island density splits the envelope's rock off.
        boolean[] full = new boolean[n], island = new boolean[n];
        boolean anyAdded = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BeyondEndChunkGenerator.ColumnScratch s = new BeyondEndChunkGenerator.ColumnScratch();
        for (int z = 0; z < nz; z++) {
            for (int x = 0; x < nx; x++) {
                int wc = x + z * nx, wx = gx0 + x, wz = gz0 + z;
                boolean ready = false;
                for (int y = 0; y < ny; y++) {
                    int i = (y * nz + z) * nx + x;
                    if (open[i]) continue;
                    BlockState st = level.getBlockState(p.set(wx, gy0 + y, wz));
                    if (st.isAir() || st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || !st.isCollisionShapeFullBlock(level, p)) continue;
                    full[i] = true;
                    island[i] = true;
                    if (!zone[wc * ny + y]) continue;
                    if (!ready) {
                        BeyondEndChunkGenerator.initColumnScratch(wx, wz, (float) Math.sqrt((double) wx * wx + (double) wz * wz), s);
                        ready = true;
                    }
                    if (!BeyondEndChunkGenerator.isSolidTerrainScratch(gy0 + y, s)) {
                        island[i] = false;
                        anyAdded = true;
                    }
                }
            }
        }
        if (!anyAdded) return 0;
        boolean[] knee = knee(full, island, nx, ny, nz, R2);

        int laid = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int c = x + z * 16, gx = x + PAD, gz = z + PAD;
                for (int y = ny - 1; y >= 0; y--) {
                    int i = (y * nz + gz) * nx + gx;
                    if (!domain[c * ny + y] || !knee[i] || open[i] || !roofed(full, gx, y, gz, nx, ny, nz)) continue;
                    int wx = bx + x, wy = gy0 + y, wz = bz + z;
                    if (!level.getBlockState(p.set(wx, wy, wz)).isAir()) continue;
                    chunk.setBlockState(p, rockFor(level, full, open, gx, y, gz, nx, ny, nz, wx, wy, wz), false);
                    laid++;
                }
            }
        }
        return laid;
    }

    static boolean[] knee(boolean[] full, boolean[] island, int nx, int ny, int nz, int r2) {
        boolean[] withAdded = closing(full, nx, ny, nz, r2), alone = closing(island, nx, ny, nz, r2);
        boolean[] out = new boolean[full.length];
        for (int i = 0; i < out.length; i++) out[i] = withAdded[i] && !alone[i] && !full[i];
        return out;
    }

    private static void markStructure(BeyondEndChunkGenerator.CarveMask m, boolean[] open, int gx0, int gy0, int gz0,
            int nx, int ny, int nz) {
        if (m.oX > gx0 + nx - 1 || m.oX + m.w - 1 < gx0 || m.oZ > gz0 + nz - 1 || m.oZ + m.d - 1 < gz0) return;
        if (m.pieceBoxes != null) {
            for (BoundingBox b : m.pieceBoxes) {
                for (int y = Math.max(b.minY(), gy0); y <= Math.min(b.maxY(), gy0 + ny - 1); y++) {
                    for (int z = Math.max(b.minZ(), gz0); z <= Math.min(b.maxZ(), gz0 + nz - 1); z++) {
                        for (int x = Math.max(b.minX(), gx0); x <= Math.min(b.maxX(), gx0 + nx - 1); x++) {
                            open[((y - gy0) * nz + z - gz0) * nx + x - gx0] = true;
                        }
                    }
                }
            }
        }
        for (int z = Math.max(m.oZ, gz0); z <= Math.min(m.oZ + m.d - 1, gz0 + nz - 1); z++) {
            for (int x = Math.max(m.oX, gx0); x <= Math.min(m.oX + m.w - 1, gx0 + nx - 1); x++) {
                int bit = (x - m.oX) + (z - m.oZ) * m.w;
                boolean occ = (m.occ[bit >> 6] & (1L << (bit & 63))) != 0;
                for (int y = gy0; y < gy0 + ny; y++) {
                    if ((occ && y >= m.colLo(bit) && y <= m.colHi(bit)) || (m.pieceBoxes == null && m.insideBoxAt(x, y, z))) {
                        open[((y - gy0) * nz + z - gz0) * nx + x - gx0] = true;
                    }
                }
            }
        }
    }

    /** Envelope columns in the rectangle as {x, z, lowest block, top, highest fill}, for structures tagged the_beyond:knee. */
    private static List<int[]> kneeColumns(List<BeyondEndChunkGenerator.CarveMask> masks, Registry<Structure> reg,
            int x0, int z0, int x1, int z1) {
        List<int[]> out = new ArrayList<>();
        for (BeyondEndChunkGenerator.CarveMask m : masks) {
            if (m.baseBox == null || m.floating || m.carveOnly || m.basePedestal || !m.distributed || !tagged(m, reg)) continue;
            if (m.oX > x1 || m.oX + m.w - 1 < x0 || m.oZ > z1 || m.oZ + m.d - 1 < z0) continue;
            for (int j = Math.max(0, z0 - m.oZ); j <= Math.min(m.d - 1, z1 - m.oZ); j++) {
                for (int i = Math.max(0, x0 - m.oX); i <= Math.min(m.w - 1, x1 - m.oX); i++) {
                    int bit = i + j * m.w;
                    if ((m.occ[bit >> 6] & (1L << (bit & 63))) == 0) continue;
                    int x = m.oX + i, z = m.oZ + j;
                    boolean base = m.inBaseFootprint(x, z);
                    int floor = m.floorFor(x, z, base);
                    int cLo = m.colLo(bit), cHi = m.colHi(bit);
                    if (cLo >= floor) continue;
                    int cap = BeyondStructureCarver.the_beyond$roofCapTop(m, x, z, base, cHi);
                    int top = cap != Integer.MIN_VALUE ? cap : Math.min(cHi, floor - 1);
                    if (top >= cLo) out.add(new int[]{x, z, cLo, top, Math.min(top, floor - 1)});
                }
            }
        }
        return out;
    }

    private static boolean tagged(BeyondEndChunkGenerator.CarveMask m, Registry<Structure> reg) {
        if (m.structureKey == null) return false;
        return reg.getHolder(ResourceKey.create(Registries.STRUCTURE, m.structureKey)).map(h -> h.is(BeyondTags.KNEE)).orElse(false);
    }

    private static boolean roofed(boolean[] full, int x, int y, int z, int nx, int ny, int nz) {
        for (int up = 1; up <= RADIUS && y + up < ny; up++) if (full[((y + up) * nz + z) * nx + x]) return true;
        return false;
    }

    private static BlockState rockFor(WorldGenLevel level, boolean[] full, boolean[] open, int gx, int gy, int gz,
            int nx, int ny, int nz, int wx, int wy, int wz) {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int first = -1;
        for (int up = 1; up <= RADIUS && gy + up < ny; up++) {
            int i = ((gy + up) * nz + gz) * nx + gx;
            if (!full[i]) {
                if (open[i]) break;
                continue;
            }
            if (first < 0) first = up;
            if (gy + up + 1 < ny && full[i + nz * nx]) return level.getBlockState(q.set(wx, wy + up, wz));
        }
        if (first >= 0) return level.getBlockState(q.set(wx, wy + first, wz));
        int i = (gy * nz + gz) * nx + gx;
        int[][] side = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : side) {
            int x = gx + d[0], z = gz + d[1];
            if (x < 0 || x >= nx || z < 0 || z >= nz) continue;
            if (full[i + d[0] + d[1] * nx]) return level.getBlockState(q.set(wx + d[0], wy, wz + d[1]));
        }
        return Blocks.END_STONE.defaultBlockState();
    }

    static boolean[] closing(boolean[] solid, int nx, int ny, int nz, int r2) {
        int[] f = new int[solid.length];
        for (int i = 0; i < f.length; i++) f[i] = solid[i] ? 0 : INF;
        edt(f, nx, ny, nz);
        for (int i = 0; i < f.length; i++) f[i] = f[i] <= r2 ? INF : 0;
        edt(f, nx, ny, nz);
        boolean[] closed = new boolean[solid.length];
        for (int i = 0; i < f.length; i++) closed[i] = f[i] > r2;
        return closed;
    }

    static void edt(int[] f, int nx, int ny, int nz) {
        int m = Math.max(nx, Math.max(ny, nz));
        int[] line = new int[m], v = new int[m];
        double[] br = new double[m + 1];
        for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) pass(f, (y * nz + z) * nx, 1, nx, line, v, br);
        for (int y = 0; y < ny; y++) for (int x = 0; x < nx; x++) pass(f, y * nz * nx + x, nx, nz, line, v, br);
        for (int z = 0; z < nz; z++) for (int x = 0; x < nx; x++) pass(f, z * nx + x, nx * nz, ny, line, v, br);
    }

    /** Lower envelope of parabolas (Felzenszwalb and Huttenlocher) along one line of the grid. */
    private static void pass(int[] f, int off, int stride, int n, int[] line, int[] v, double[] br) {
        for (int i = 0; i < n; i++) line[i] = f[off + i * stride];
        int k = 0;
        v[0] = 0;
        br[0] = Double.NEGATIVE_INFINITY;
        br[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < n; q++) {
            double s = cross(line, v[k], q);
            while (s <= br[k]) {
                k--;
                s = cross(line, v[k], q);
            }
            k++;
            v[k] = q;
            br[k] = s;
            br[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (br[k + 1] < q) k++;
            long d = (long) (q - v[k]) * (q - v[k]) + line[v[k]];
            f[off + q * stride] = (int) Math.min(d, INF);
        }
    }

    private static double cross(int[] line, int p, int q) {
        return ((line[q] + (double) q * q) - (line[p] + (double) p * p)) / (2.0 * (q - p));
    }
}
