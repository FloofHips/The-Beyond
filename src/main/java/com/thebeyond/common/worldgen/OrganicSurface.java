package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Inside the corruption the ground's top block turns into its corrupted form at chunk load, keeping the original. */
public final class OrganicSurface {
    public enum Mode { OFF, QUART, BLOCK }

    public static final Mode MODE = Mode.valueOf(System.getProperty("beyond.sub.org.surface", "block").toUpperCase(Locale.ROOT));
    private static final boolean DUMP = Boolean.getBoolean("beyond.sub.org.surface.dump");
    private static final Set<String> FACES = Set.of(System.getProperty("beyond.sub.org.surface.faces", "floor,ceiling,wall")
            .toLowerCase(Locale.ROOT).split(","));
    private static final boolean FLOOR = FACES.contains("floor"), CEILING = FACES.contains("ceiling"), WALL = FACES.contains("wall");
    private static final int DEPTH = Math.clamp(Integer.getInteger("beyond.sub.org.surface.depth", 1), 1, 16);
    private static final boolean CHECK = Boolean.getBoolean("beyond.sub.org.surface.check");
    private static final ResourceLocation CORRUPTED = ResourceLocation.parse(System.getProperty("beyond.sub.org.surface.block",
            "minecraft:sculk"));
    private static BufferedWriter dump;

    /** Originals of the swapped blocks: y << 8 | z << 4 | x from the chunk's lowest block, each with a palette index. */
    public static final class Kept implements INBTSerializable<CompoundTag> {
        int[] pos = new int[0], state = new int[0];
        long[] origin = new long[0];
        boolean trails;
        final List<BlockState> palette = new ArrayList<>();

        public Kept() {}

        int index(BlockState s) {
            int i = palette.indexOf(s);
            if (i >= 0) return i;
            palette.add(s);
            return palette.size() - 1;
        }

        long origin(int i) {
            return i < origin.length ? origin[i] : OrganicTrail.NONE;
        }

        void add(int packed, BlockState had, long from) {
            int n = pos.length;
            pos = Arrays.copyOf(pos, n + 1);
            state = Arrays.copyOf(state, n + 1);
            long[] o = Arrays.copyOf(origin, n + 1);
            Arrays.fill(o, origin.length, n, OrganicTrail.NONE);
            o[n] = from;
            origin = o;
            pos[n] = packed;
            state[n] = index(had);
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("pos", pos);
            tag.putIntArray("state", state);
            if (origin.length > 0) tag.putLongArray("origin", origin);
            if (trails) tag.putBoolean("trails", true);
            ListTag list = new ListTag();
            for (BlockState s : palette) list.add(NbtUtils.writeBlockState(s));
            tag.put("palette", list);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            pos = tag.getIntArray("pos");
            state = tag.getIntArray("state");
            origin = tag.getLongArray("origin");
            trails = tag.getBoolean("trails");
            palette.clear();
            for (Tag t : tag.getList("palette", Tag.TAG_COMPOUND)) {
                palette.add(NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK), (CompoundTag) t));
            }
        }
    }

    private OrganicSurface() {}

    static BlockState corrupted() {
        return BuiltInRegistries.BLOCK.get(CORRUPTED).defaultBlockState();
    }

    // Every solid block the world grew counts, from surface rules or features of any mod, the network and the fire aside.
    static boolean ground(BlockState s) {
        return s.canOcclude() && natural(s);
    }

    /** Plants (blocks without collision) hide under the corruption and come back with the ground. */
    static boolean plant(BlockState s) {
        return !s.isAir() && !s.canOcclude() && s.getFluidState().isEmpty() && natural(s)
                && s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static boolean natural(BlockState s) {
        Block b = s.getBlock();
        return !s.hasBlockEntity() && !(b instanceof BonfireBlock) && b != OrganicExperiment.LINE_BLOCK
                && !OrganicExperiment.tissue(s) && !BuiltInRegistries.BLOCK.getKey(b).equals(CORRUPTED);
    }

    /** Swaps this chunk's exposed ground that the corruption covers, read per quart or per block as a surface rule reads it. */
    static void apply(ServerLevel level, LevelChunk chunk) {
        if (MODE == Mode.OFF || !OrganicExperiment.ON || OrganicLight.SHY.isEmpty() || OrganicLight.HALO <= 0
                || level.dimension() != Level.END) return;
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> blight = biomes.getHolder(OrganicBlight.BLIGHT).orElse(null);
        Holder<Biome> other = biomes.getHolder(Biomes.THE_END).orElse(null);
        if (blight == null || other == null) return;
        long t0 = System.nanoTime();
        ChunkPos cp = chunk.getPos();
        ChunkAccess[] near = new ChunkAccess[9];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkHolder h = dx == 0 && dz == 0 ? null
                        : level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(cp.x + dx, cp.z + dz));
                near[(dx + 1) * 3 + dz + 1] = dx == 0 && dz == 0 ? chunk : h == null ? null : h.getLatestChunk();
            }
        }
        int sections = chunk.getSectionsCount();
        boolean[] hot = hot(near, sections, true);
        if (hot == null) return;
        BiomeManager zoom = new BiomeManager((qx, qy, qz) -> quart(near, cp, qx, qy, qz, blight, other),
                BiomeManager.obfuscateSeed(level.getSeed()));
        BlockState corrupted = BuiltInRegistries.BLOCK.get(CORRUPTED).defaultBlockState();
        boolean refs = !chunk.getAllReferences().isEmpty();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        int exposed = 0, byQuart = 0, byBlock = 0, guarded = 0;
        List<int[]> swaps = new ArrayList<>(), plants = new ArrayList<>();
        Kept kept = chunk.getExistingDataOrNull(BeyondAttachments.ORG_SURFACE);
        if (kept == null) kept = new Kept();
        StringBuilder sb = DUMP ? new StringBuilder() : null;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int yLo = Integer.MAX_VALUE, yHi = Integer.MIN_VALUE;
        for (int s = 0; s < sections; s++) {
            if (!hot[s]) continue;
            yLo = Math.min(yLo, minY + (s << 4));
            yHi = Math.max(yHi, minY + (s << 4) + 16);
        }
        Crust crust = new Crust(near, cp, yLo, yHi, minY, maxY, corrupted);
        long tc = 0;
        if (CHECK) {
            tc = System.nanoTime();
            boolean same = sameCrust(near, cp, sections, hot, crust, yLo, yHi, minY, maxY, corrupted);
            tc = System.nanoTime() - tc;
            TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG surface check chunk %d %d | equal %b | first ms %.3f", cp.x,
                    cp.z, same, tc / 1e6));
        }
        for (int s = 0; s < sections; s++) {
            if (!hot[s]) continue;
            for (int y = minY + (s << 4); y < minY + (s << 4) + 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        at.set(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                        BlockState here = chunk.getBlockState(at);
                        if (plant(here)) {
                            boolean in = MODE == Mode.QUART ? chunk.getNoiseBiome(QuartPos.fromBlock(at.getX()),
                                    QuartPos.fromBlock(y), QuartPos.fromBlock(at.getZ())).is(OrganicBlight.BLIGHT)
                                    : zoom.getBiome(at).is(OrganicBlight.BLIGHT);
                            if (in && !(refs && inStructure(level, at))) {
                                plants.add(new int[]{(y - minY) << 8 | z << 4 | x, kept.index(here)});
                            }
                            continue;
                        }
                        if (!crust.ground(at.getX(), y, at.getZ())) continue;
                        int layer = crust.layer(at.getX(), y, at.getZ());
                        if (layer == 0) continue;
                        int faces = crust.faces(at.getX(), y, at.getZ());
                        exposed++;
                        boolean q = chunk.getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(y),
                                QuartPos.fromBlock(at.getZ())).is(OrganicBlight.BLIGHT);
                        boolean b = zoom.getBiome(at).is(OrganicBlight.BLIGHT);
                        if (q) byQuart++;
                        if (b) byBlock++;
                        if (sb != null && (q || b)) {
                            sb.append("G ").append(at.getX()).append(' ').append(y).append(' ').append(at.getZ())
                                    .append(q ? " 1" : " 0").append(b ? " 1 " : " 0 ").append(faces);
                            if (DEPTH > 1) sb.append(' ').append(layer);
                            sb.append('\n');
                        }
                        if (!(MODE == Mode.QUART ? q : b)) continue;
                        if (refs && inStructure(level, at)) {
                            guarded++;
                            if (sb != null) sb.append("K ").append(at.getX()).append(' ').append(y).append(' ').append(at.getZ())
                                    .append('\n');
                            continue;
                        }
                        BlockState old = chunk.getBlockState(at);
                        swaps.add(new int[]{(y - minY) << 8 | z << 4 | x, kept.index(old)});
                        if (sb != null) sb.append("O ").append(at.getX()).append(' ').append(y).append(' ').append(at.getZ())
                                .append(' ').append(BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath()).append('\n');
                    }
                }
            }
        }
        // Every decision reads the ground as generated, so a block turned here never exposes the one behind it.
        for (int[] w : swaps) {
            at.set(cp.getMinBlockX() + (w[0] & 15), minY + (w[0] >>> 8), cp.getMinBlockZ() + (w[0] >> 4 & 15));
            chunk.setBlockState(at, corrupted, false);
        }
        for (int[] w : plants) {
            at.set(cp.getMinBlockX() + (w[0] & 15), minY + (w[0] >>> 8), cp.getMinBlockZ() + (w[0] >> 4 & 15));
            chunk.setBlockState(at, Blocks.AIR.defaultBlockState(), false);
        }
        int swapped = swaps.size();
        swaps.addAll(plants);
        if (!swaps.isEmpty()) {
            int n = kept.pos.length;
            kept.pos = Arrays.copyOf(kept.pos, n + swaps.size());
            kept.state = Arrays.copyOf(kept.state, n + swaps.size());
            for (int i = 0; i < swaps.size(); i++) {
                kept.pos[n + i] = swaps.get(i)[0];
                kept.state[n + i] = swaps.get(i)[1];
            }
            if (kept.origin.length > 0) {
                long[] o = Arrays.copyOf(kept.origin, kept.pos.length);
                Arrays.fill(o, kept.origin.length, kept.pos.length, OrganicTrail.NONE);
                kept.origin = o;
            }
            chunk.setData(BeyondAttachments.ORG_SURFACE, kept);
            chunk.setUnsaved(true);
            OrganicTrail.due(level, chunk);
        }
        if (sb != null && !sb.isEmpty()) write(sb);
        TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG surface chunk %d %d | mode %s | exposed %d | blight by quart %d"
                + " by block %d | swapped %d plants %d guarded %d | kept %d states %d | ms %.3f | depth %d", cp.x, cp.z, MODE,
                exposed, byQuart, byBlock, swapped, plants.size(), guarded, kept.pos.length, kept.palette.size(),
                (System.nanoTime() - t0 - tc) / 1e6, DEPTH));
    }

    static boolean inStructure(ServerLevel level, BlockPos at) {
        return level.structureManager().getStructureWithPieceAt(at, h -> true).isValid();
    }

    /** The ground as generated plus a DEPTH margin, each block's layer being its steps from the nearest open face that counts. */
    private static final class Crust {
        private final int x0, y0, z0, nx, ny, nz;
        // 0 open, 1 ground, 2 the corrupted block or the zone line (closed, never swapped)
        private final byte[] kind;
        private final byte[] layer;
        private int[] front = new int[256], next = new int[256];
        private int nf, nn;

        // A neighbour's corrupted blocks and the zone line still count as ground, so the order chunks load in changes nothing.
        Crust(ChunkAccess[] near, ChunkPos cp, int yLo, int yHi, int minY, int maxY, BlockState corrupted) {
            x0 = cp.getMinBlockX() - DEPTH;
            y0 = yLo - DEPTH;
            z0 = cp.getMinBlockZ() - DEPTH;
            nx = nz = 16 + 2 * DEPTH;
            ny = yHi - yLo + 2 * DEPTH;
            kind = new byte[nx * ny * nz];
            Kinds kinds = new Kinds(corrupted);
            int ya = Math.max(y0, minY), yb = Math.min(y0 + ny, maxY);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkAccess c = near[(dx + 1) * 3 + dz + 1];
                    int bx = (cp.x + dx) << 4, bz = (cp.z + dz) << 4;
                    int xa = Math.max(x0, bx), xb = Math.min(x0 + nx, bx + 16);
                    int za = Math.max(z0, bz), zb = Math.min(z0 + nz, bz + 16);
                    if (c == null || xa >= xb || za >= zb) continue;
                    for (int sy = ya; sy < yb; sy = (sy & ~15) + 16) {
                        LevelChunkSection sec = c.getSection(c.getSectionIndex(sy));
                        // only air, or no closed state in the palette: nothing in this section is closed
                        if (sec.hasOnlyAir() || !sec.maybeHas(kinds::closed)) continue;
                        int ey = Math.min(yb, (sy & ~15) + 16);
                        for (int gx = xa; gx < xb; gx++) {
                            for (int gz = za; gz < zb; gz++) {
                                for (int gy = sy; gy < ey; gy++) {
                                    kind[((gx - x0) * ny + gy - y0) * nz + gz - z0] = kinds.of(sec.getBlockState(gx & 15, gy & 15,
                                            gz & 15));
                                }
                            }
                        }
                    }
                }
            }
            layer = new byte[kind.length];
            for (int i = 0; i < layer.length; i++) if (kind[i] != 0) layer[i] = Byte.MAX_VALUE;
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    for (int z = 0; z < nz; z++) {
                        int i = (x * ny + y) * nz + z;
                        if (layer[i] != Byte.MAX_VALUE) continue;
                        if (FLOOR && at(x, y + 1, z, 0) || CEILING && at(x, y - 1, z, 0) || WALL && (at(x + 1, y, z, 0)
                                || at(x - 1, y, z, 0) || at(x, y, z + 1, 0) || at(x, y, z - 1, 0))) {
                            layer[i] = 1;
                            if (nf == front.length) front = Arrays.copyOf(front, nf * 2);
                            front[nf++] = i;
                        }
                    }
                }
            }
            // Layer k grows only from layer k - 1: the blocks a pass over the whole volume would find, read from the frontier.
            for (int k = 2; k <= DEPTH && nf > 0; k++) {
                nn = 0;
                for (int f = 0; f < nf; f++) {
                    int i = front[f], x = i / (ny * nz), y = i / nz % ny, z = i % nz;
                    if (FLOOR) mark(x, y - 1, z, k);
                    if (CEILING) mark(x, y + 1, z, k);
                    if (WALL) {
                        mark(x + 1, y, z, k);
                        mark(x - 1, y, z, k);
                        mark(x, y, z + 1, k);
                        mark(x, y, z - 1, k);
                    }
                }
                int[] t = front;
                front = next;
                next = t;
                nf = nn;
            }
        }

        private void mark(int x, int y, int z, int k) {
            if (x < 0 || y < 0 || z < 0 || x >= nx || y >= ny || z >= nz) return;
            int i = (x * ny + y) * nz + z;
            if (layer[i] != Byte.MAX_VALUE) return;
            layer[i] = (byte) k;
            if (nn == next.length) next = Arrays.copyOf(next, nn * 2);
            next[nn++] = i;
        }

        private boolean at(int x, int y, int z, int k) {
            return x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz && layer[(x * ny + y) * nz + z] == k;
        }

        /** 1..DEPTH for a block inside the corrupted crust, 0 otherwise. */
        int layer(int gx, int gy, int gz) {
            int v = layer[((gx - x0) * ny + gy - y0) * nz + gz - z0];
            return v == Byte.MAX_VALUE ? 0 : v;
        }

        boolean ground(int gx, int gy, int gz) {
            return kind[((gx - x0) * ny + gy - y0) * nz + gz - z0] == 1;
        }

        // Open faces as bits (1 floor, 2 ceiling, 4 wall), only the ones the setting turns on.
        int faces(int gx, int gy, int gz) {
            int x = gx - x0, y = gy - y0, z = gz - z0, f = 0;
            if (FLOOR && open(x, y + 1, z)) f |= 1;
            if (CEILING && open(x, y - 1, z)) f |= 2;
            if (WALL && (open(x + 1, y, z) || open(x - 1, y, z) || open(x, y, z + 1) || open(x, y, z - 1))) f |= 4;
            return f;
        }

        private boolean open(int x, int y, int z) {
            return kind[(x * ny + y) * nz + z] == 0;
        }
    }

    // Each state is classified once per chunk: the ground test reads a tag and the block's name.
    private static final class Kinds {
        private final BlockState corrupted;
        private final Map<BlockState, Byte> seen = new IdentityHashMap<>();
        private BlockState last;
        private byte lastKind;

        Kinds(BlockState corrupted) {
            this.corrupted = corrupted;
        }

        byte of(BlockState s) {
            if (s == last) return lastKind;
            Byte k = seen.get(s);
            if (k == null) {
                k = (byte) (ground(s) ? 1 : s == corrupted || s.is(OrganicExperiment.LINE_BLOCK) ? 2 : 0);
                seen.put(s, k);
            }
            last = s;
            lastKind = k;
            return k;
        }

        boolean closed(BlockState s) {
            return of(s) != 0;
        }
    }

    /** The first reading beside the fast one, with the hot sections, every layer and every kind compared. */
    private static boolean sameCrust(ChunkAccess[] near, ChunkPos cp, int sections, boolean[] hot, Crust fast, int yLo, int yHi,
                                     int minY, int maxY, BlockState corrupted) {
        if (!Arrays.equals(hot, hot(near, sections, false))) return false;
        FirstCrust old = new FirstCrust(near, cp, yLo, yHi, minY, maxY, corrupted);
        for (int i = 0; i < old.layer.length; i++) {
            if (old.layer[i] != fast.layer[i] || old.closed[i] != (fast.kind[i] != 0) || old.ground[i] != (fast.kind[i] == 1)) {
                return false;
            }
        }
        return true;
    }

    /** The crust as first written, block by block with every pass over the whole volume, kept to check the fast one. */
    private static final class FirstCrust {
        private final int x0, y0, z0, nx, ny, nz;
        private final boolean[] closed, ground;
        private final byte[] layer;

        // A neighbour's corrupted blocks and the zone line still count as ground, so the order chunks load in changes nothing.
        FirstCrust(ChunkAccess[] near, ChunkPos cp, int yLo, int yHi, int minY, int maxY, BlockState corrupted) {
            x0 = cp.getMinBlockX() - DEPTH;
            y0 = yLo - DEPTH;
            z0 = cp.getMinBlockZ() - DEPTH;
            nx = nz = 16 + 2 * DEPTH;
            ny = yHi - yLo + 2 * DEPTH;
            closed = new boolean[nx * ny * nz];
            ground = new boolean[closed.length];
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = 0; x < nx; x++) {
                for (int z = 0; z < nz; z++) {
                    ChunkAccess c = near[(((x0 + x) >> 4) - cp.x + 1) * 3 + ((z0 + z) >> 4) - cp.z + 1];
                    if (c == null) continue;
                    for (int y = Math.max(0, minY - y0); y < Math.min(ny, maxY - y0); y++) {
                        BlockState s = c.getBlockState(p.set(x0 + x, y0 + y, z0 + z));
                        int i = (x * ny + y) * nz + z;
                        ground[i] = ground(s);
                        closed[i] = ground[i] || s == corrupted || s.is(OrganicExperiment.LINE_BLOCK);
                    }
                }
            }
            layer = new byte[closed.length];
            for (int i = 0; i < layer.length; i++) if (closed[i]) layer[i] = Byte.MAX_VALUE;
            for (int k = 1; k <= DEPTH; k++) {
                for (int x = 0; x < nx; x++) {
                    for (int y = 0; y < ny; y++) {
                        for (int z = 0; z < nz; z++) {
                            int i = (x * ny + y) * nz + z, j = k - 1;
                            if (layer[i] != Byte.MAX_VALUE) continue;
                            if (FLOOR && at(x, y + 1, z, j) || CEILING && at(x, y - 1, z, j) || WALL && (at(x + 1, y, z, j)
                                    || at(x - 1, y, z, j) || at(x, y, z + 1, j) || at(x, y, z - 1, j))) layer[i] = (byte) k;
                        }
                    }
                }
            }
        }

        private boolean at(int x, int y, int z, int k) {
            return x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz && layer[(x * ny + y) * nz + z] == k;
        }
    }

    // A neighbour that has not loaded yet still holds its corruption in the attachment, so every load reads the same map.
    private static boolean pending(ChunkAccess c, boolean self, int s, int bit) {
        OrganicBlight.Mask m = self ? null : c.getExistingDataOrNull(BeyondAttachments.ORG_BLIGHT);
        return m != null && s >= 0 && s < m.blight.length && (bit < 0 ? m.blight[s] != 0 : (m.blight[s] >>> bit & 1) != 0);
    }

    // Sections that hold blight, here or in a neighbour, and the ones beside them: null when there is none.
    private static boolean[] hot(ChunkAccess[] near, int sections, boolean byPalette) {
        boolean[] hot = new boolean[sections];
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            if (near[i] == null || MODE == Mode.QUART && i != 4) continue;
            for (int s = 0; s < sections; s++) {
                if (!sectionBlight(near[i], i == 4, s, byPalette)) continue;
                for (int t = Math.max(0, s - 1); t <= Math.min(sections - 1, s + 1); t++) hot[t] = true;
                any = true;
            }
        }
        return any ? hot : null;
    }

    private static boolean sectionBlight(ChunkAccess c, boolean self, int s, boolean byPalette) {
        if (pending(c, self, s, -1)) return true;
        LevelChunkSection sec = c.getSection(s);
        // a palette without the blight holds no quart of it
        if (byPalette && !sec.getBiomes().maybeHas(h -> h.is(OrganicBlight.BLIGHT))) return false;
        for (int i = 0; i < 64; i++) {
            if (sec.getNoiseBiome(i & 3, i >> 4, i >> 2 & 3).is(OrganicBlight.BLIGHT)) return true;
        }
        return false;
    }

    private static Holder<Biome> quart(ChunkAccess[] near, ChunkPos cp, int qx, int qy, int qz, Holder<Biome> blight,
                                       Holder<Biome> other) {
        int dx = (qx >> 2) - cp.x, dz = (qz >> 2) - cp.z;
        ChunkAccess c = Math.abs(dx) > 1 || Math.abs(dz) > 1 ? null : near[(dx + 1) * 3 + dz + 1];
        if (c == null) return other;
        int lo = QuartPos.fromBlock(c.getMinBuildHeight()), y = Mth.clamp(qy, lo, lo + QuartPos.fromBlock(c.getHeight()) - 1);
        int s = c.getSectionIndex(QuartPos.toBlock(y));
        if (pending(c, dx == 0 && dz == 0, s, OrganicBlight.Mask.bit(qx, y, qz))) return blight;
        Holder<Biome> h = c.getNoiseBiome(qx, y, qz);
        return h.is(OrganicBlight.BLIGHT) ? blight : other;
    }

    private static synchronized void write(StringBuilder sb) {
        try {
            if (dump == null) {
                dump = Files.newBufferedWriter(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("org_surface.txt"),
                        StandardCharsets.UTF_8);
            }
            dump.write(sb.toString());
            dump.flush();
        } catch (IOException e) {
            TheBeyond.LOGGER.warn("ORG surface dump failed: {}", e.toString());
        }
    }
}
