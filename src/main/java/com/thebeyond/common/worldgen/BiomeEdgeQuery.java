package com.thebeyond.common.worldgen;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Distance from a block to the nearest block of another biome, by the game's own biome lookup (one instance per thread). */
public final class BiomeEdgeQuery {

    public enum Shape { HORIZONTAL, SPHERE }

    public enum Density { READ, VOID, LAND }

    public record Edge(double distance, BlockPos pos, Holder<Biome> biome) {}

    private static final int MAX_RADIUS = 256;
    private static final Map<Integer, int[]> OFFSETS = new ConcurrentHashMap<>();

    private final BeyondEndBiomeSource source;
    private final Density density;
    private final int minY;
    private final int maxY;
    private final int minQuartY;
    private final int maxQuartY;
    private final BiomeManager zoom;
    private final BiomeManager locator;
    private final Long2ObjectOpenHashMap<Quart> quarts = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] xs = new int[8];
    private final int[] ys = new int[8];
    private final int[] zs = new int[8];
    private int readX;
    private int readY;
    private int readZ;
    private int densityReads;

    public BiomeEdgeQuery(BeyondEndBiomeSource source, BiomeManager biomeManager, LevelHeightAccessor height, Density density) {
        this.source = source;
        this.density = density;
        this.minY = height.getMinBuildHeight();
        this.maxY = height.getMaxBuildHeight();
        this.minQuartY = QuartPos.fromBlock(minY);
        this.maxQuartY = minQuartY + QuartPos.fromBlock(height.getHeight()) - 1;
        this.zoom = biomeManager.withDifferentSource((x, y, z) -> biomeOf(quart(x, y, z)));
        this.locator = biomeManager.withDifferentSource((x, y, z) -> {
            readX = x;
            readY = y;
            readZ = z;
            return null;
        });
    }

    public Holder<Biome> biome(BlockPos pos) {
        return zoom.getBiome(pos);
    }

    public Edge nearestOutside(BlockPos pos, Predicate<Holder<Biome>> inside, Shape shape, int maxRadius) {
        if (maxRadius > MAX_RADIUS) throw new IllegalArgumentException("maxRadius above " + MAX_RADIUS + ": " + maxRadius);
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        boolean flat = shape == Shape.HORIZONTAL;
        if (maxRadius < 0 || flat && (py < minY || py >= maxY)) return null;
        int cx = px >> 2, cy = py >> 2, cz = pz >> 2;
        int ox = px & 3, oy = py & 3, oz = pz & 3;
        // blocks in one layer can only read two quart layers: (y - 2) >> 2 and the one above it
        int baseY = flat ? ((py - 2) >> 2) - cy : 0;
        long best = (long) maxRadius * maxRadius + 1;
        int bx = 0, by = 0, bz = 0;
        Quart hit = null;
        int[] offsets = offsets(flat, maxRadius);
        for (int i = 0; i < offsets.length; i += 2) {
            if (offsets[i + 1] >= best) break;
            int packed = offsets[i];
            int kx = (packed & 1023) - 512;
            int ky = ((packed >> 10) & 1023) - 512 + baseY;
            int kz = ((packed >> 20) & 1023) - 512;
            long gx = gap(kx, ox), gy = flat ? 0 : gap(ky, oy), gz = gap(kz, oz);
            if (gx * gx + gy * gy + gz * gz >= best) continue;
            int qx = cx + kx, qy = cy + ky, qz = cz + kz;
            Quart quart = quart(qx, qy, qz);
            if (!outside(quart, inside)) continue;
            nearestFirst(4 * qx - 2, px, xs);
            nearestFirst(4 * qz - 2, pz, zs);
            int ny = 1;
            if (flat) {
                ys[0] = py;
            } else {
                nearestFirst(4 * qy - 2, py, ys);
                ny = 8;
            }
            for (int iz = 0; iz < 8; iz++) {
                long dz = zs[iz] - pz;
                long dz2 = dz * dz;
                if (dz2 >= best) break;
                for (int iy = 0; iy < ny; iy++) {
                    long dy = ys[iy] - py;
                    long dzy2 = dz2 + dy * dy;
                    if (dzy2 >= best) break;
                    if (ys[iy] < minY || ys[iy] >= maxY) continue;
                    for (int ix = 0; ix < 8; ix++) {
                        long dx = xs[ix] - px;
                        long d2 = dzy2 + dx * dx;
                        if (d2 >= best) break;
                        if (reads(xs[ix], ys[iy], zs[iz], qx, qy, qz)) {
                            best = d2;
                            bx = xs[ix];
                            by = ys[iy];
                            bz = zs[iz];
                            hit = quart;
                            break;
                        }
                    }
                }
            }
        }
        return hit == null ? null : new Edge(Math.sqrt(best), new BlockPos(bx, by, bz), biomeOf(hit));
    }

    public int quartsRead() {
        return quarts.size();
    }

    public int densityReads() {
        return densityReads;
    }

    private boolean reads(int x, int y, int z, int qx, int qy, int qz) {
        locator.getBiome(cursor.set(x, y, z));
        return readX == qx && readY == qy && readZ == qz;
    }

    private Quart quart(int qx, int qy, int qz) {
        int y = Mth.clamp(qy, minQuartY, maxQuartY);
        long key = BlockPos.asLong(qx, y, qz);
        Quart quart = quarts.get(key);
        if (quart == null) {
            int bx = QuartPos.toBlock(qx), by = QuartPos.toBlock(y), bz = QuartPos.toBlock(qz);
            Holder<Biome> fixed = source.densityFreeBiome(bx, by, bz);
            quart = fixed != null ? new Quart(bx, by, bz, fixed, fixed)
                    : new Quart(bx, by, bz, source.sideBiome(bx, by, bz, true), source.sideBiome(bx, by, bz, false));
            quarts.put(key, quart);
        }
        return quart;
    }

    private Holder<Biome> biomeOf(Quart quart) {
        if (quart.onVoid == quart.onLand) return quart.onVoid;
        return switch (density) {
            case VOID -> quart.onVoid;
            case LAND -> quart.onLand;
            case READ -> isVoid(quart) ? quart.onVoid : quart.onLand;
        };
    }

    private boolean outside(Quart quart, Predicate<Holder<Biome>> inside) {
        if (quart.onVoid == quart.onLand || density == Density.VOID) return !inside.test(quart.onVoid);
        if (density == Density.LAND) return !inside.test(quart.onLand);
        boolean voidIn = inside.test(quart.onVoid);
        if (voidIn == inside.test(quart.onLand)) return !voidIn;
        return isVoid(quart) != voidIn;
    }

    private boolean isVoid(Quart quart) {
        if (quart.side == 0) {
            densityReads++;
            quart.side = source.isVoid(quart.x, quart.y, quart.z) ? (byte) 1 : (byte) 2;
        }
        return quart.side == 1;
    }

    private static long gap(int k, int o) {
        return Math.max(0, Math.max(4 * k - 2 - o, o - 5 - 4 * k));
    }

    private static void nearestFirst(int lo, int p, int[] out) {
        int hi = lo + 7;
        int c = Mth.clamp(p, lo, hi);
        int down = c - 1, up = c + 1, n = 0;
        out[n++] = c;
        while (n < 8) {
            if (up > hi || down >= lo && p - down <= up - p) out[n++] = down--;
            else out[n++] = up++;
        }
    }

    private static int[] offsets(boolean flat, int radius) {
        return OFFSETS.computeIfAbsent(flat ? -1 - radius : radius, k -> buildOffsets(flat, radius));
    }

    private static int[] buildOffsets(boolean flat, int radius) {
        int reach = (radius + 5) / 4 + 1;
        long limit = (long) radius * radius;
        LongArrayList keyed = new LongArrayList();
        for (int kx = -reach; kx <= reach; kx++) {
            for (int kz = -reach; kz <= reach; kz++) {
                long xz = least(kx) + least(kz);
                if (xz > limit) continue;
                for (int ky = flat ? 0 : -reach; ky <= (flat ? 1 : reach); ky++) {
                    long key = xz + (flat ? 0 : least(ky));
                    if (key > limit) continue;
                    keyed.add(key << 32 | (kx + 512) | (ky + 512) << 10 | (kz + 512) << 20);
                }
            }
        }
        long[] sorted = keyed.toLongArray();
        Arrays.sort(sorted);
        int[] out = new int[2 * sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            out[2 * i] = (int) sorted[i];
            out[2 * i + 1] = (int) (sorted[i] >>> 32);
        }
        return out;
    }

    private static long least(int k) {
        long g = Math.max(0, 4 * Math.abs(k) - 5);
        return g * g;
    }

    private static final class Quart {
        final int x;
        final int y;
        final int z;
        final Holder<Biome> onVoid;
        final Holder<Biome> onLand;
        byte side;

        Quart(int x, int y, int z, Holder<Biome> onVoid, Holder<Biome> onLand) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.onVoid = onVoid;
            this.onLand = onLand;
        }
    }
}
