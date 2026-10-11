package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.mixin.CountOnEveryLayerPlacementAccessor;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Gellid a structure veto kept out of a lake, written once the lake's chunk and its eight neighbours are complete. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class GellidLakeFill {
    private static final byte FILL = 0, UNDO = 1, DUG = 2;
    // Fewer fluid blocks outside the clearance than the smallest lake holds read as a notch by the wall.
    private static final int MIN_FLUID_LEFT = 20;
    private static final int CHUNKS_PER_TICK = 4;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    // The farthest a tagged feature reaches from its spot: a patch's spread plus the block under its try.
    private static final int REACH = 6;
    private static final TagKey<PlacedFeature> LAKE_DECORATION = TagKey.create(Registries.PLACED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "gellid_lake_decoration"));

    private static final LongLinkedOpenHashSet CHECK = new LongLinkedOpenHashSet(); // main thread only

    private GellidLakeFill() {}

    /** Fluid the veto kept out, a write near a piece to take back, or dug air over held fluid. */
    private record Cell(long pos, byte kind, BlockState expect, @Nullable BlockState put) {}

    public static final class Lake {
        private final BlockPos base;
        private final boolean[] shape;
        private final boolean notch;
        private boolean erased;
        private final List<Cell> cells = new ArrayList<>();

        public Lake(BlockPos base, boolean[] shape) {
            this.base = base;
            this.shape = shape;
            int fluid = 0, near = 0;
            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    for (int y = 1; y < 4; y++) {
                        if (!shape[(x * 16 + z) * 8 + y]) continue;
                        fluid++;
                        if (FeatureGuard.insidePieceClearance(base.getX() + x, base.getY() + y, base.getZ() + z)) near++;
                    }
                }
            }
            this.notch = near > 0 && fluid - near < MIN_FLUID_LEFT;
        }

        public void record(BlockPos pos, BlockState state, BlockState found, boolean written) {
            boolean near = FeatureGuard.insidePieceClearance(pos.getX(), pos.getY(), pos.getZ());
            if (written) {
                if (notch || near) cells.add(new Cell(pos.asLong(), UNDO, state, found));
                else if (state.isAir()) cells.add(new Cell(pos.asLong(), DUG, state, null));
            } else if (state.isAir()) {
                erased = true; // the guard blocked the whole lake, so it holds no fluid
            } else if (!notch && !near && state.is(BeyondBlocks.GELLID_VOID.get())) {
                cells.add(new Cell(pos.asLong(), FILL, found, null));
            }
        }
    }

    /** A chunk's held lake cells, in write order since undoing runs newest first. */
    public static final class Held implements INBTSerializable<CompoundTag> {
        private final List<Cell> cells = new ArrayList<>();

        synchronized void add(List<Cell> more) { cells.addAll(more); }

        synchronized List<Cell> cells() { return new ArrayList<>(cells); }

        @Override
        public synchronized CompoundTag serializeNBT(HolderLookup.Provider provider) {
            int n = cells.size();
            long[] pos = new long[n];
            byte[] kind = new byte[n];
            int[] expect = new int[n], put = new int[n];
            Map<BlockState, Integer> ids = new IdentityHashMap<>();
            ListTag palette = new ListTag();
            for (int i = 0; i < n; i++) {
                Cell c = cells.get(i);
                pos[i] = c.pos;
                kind[i] = c.kind;
                expect[i] = id(ids, palette, c.expect);
                put[i] = c.put == null ? -1 : id(ids, palette, c.put);
            }
            CompoundTag tag = new CompoundTag();
            tag.put("palette", palette);
            tag.putLongArray("pos", pos);
            tag.putByteArray("kind", kind);
            tag.putIntArray("expect", expect);
            tag.putIntArray("put", put);
            return tag;
        }

        private static int id(Map<BlockState, Integer> ids, ListTag palette, BlockState state) {
            return ids.computeIfAbsent(state, s -> {
                palette.add(NbtUtils.writeBlockState(s));
                return palette.size() - 1;
            });
        }

        @Override
        public synchronized void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            cells.clear();
            HolderGetter<Block> blocks = provider.lookupOrThrow(Registries.BLOCK);
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] states = new BlockState[palette.size()];
            for (int i = 0; i < states.length; i++) states[i] = NbtUtils.readBlockState(blocks, palette.getCompound(i));
            long[] pos = tag.getLongArray("pos");
            byte[] kind = tag.getByteArray("kind");
            int[] expect = tag.getIntArray("expect"), put = tag.getIntArray("put");
            if (kind.length != pos.length || expect.length != pos.length || put.length != pos.length) return;
            for (int i = 0; i < pos.length; i++) {
                cells.add(new Cell(pos[i], kind[i], states[expect[i]], put[i] < 0 ? null : states[put[i]]));
            }
        }
    }

    /** Files a lake's cells with the chunks they fall in, saved with each chunk until it settles. */
    public static void hold(WorldGenLevel level, Lake lake, boolean placed) {
        if (TheBeyond.LOGGER.isDebugEnabled()) log(lake, placed);
        if (lake.cells.isEmpty()) return;
        LongSet fluidColumns = new LongOpenHashSet();
        if (!lake.erased) {
            for (Cell c : lake.cells) if (c.kind == FILL) fluidColumns.add(column(c.pos));
        }
        Long2ObjectMap<List<Cell>> byChunk = new Long2ObjectOpenHashMap<>();
        for (Cell c : lake.cells) {
            if (c.kind != UNDO && !fluidColumns.contains(column(c.pos))) continue;
            byChunk.computeIfAbsent(ChunkPos.asLong(BlockPos.getX(c.pos) >> 4, BlockPos.getZ(c.pos) >> 4),
                    k -> new ArrayList<>()).add(c);
        }
        for (Long2ObjectMap.Entry<List<Cell>> e : byChunk.long2ObjectEntrySet()) {
            ChunkAccess chunk = level.getChunk(ChunkPos.getX(e.getLongKey()), ChunkPos.getZ(e.getLongKey()));
            chunk.getData(BeyondAttachments.HELD_LAKE).add(e.getValue());
            chunk.setUnsaved(true);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.END) return;
        ChunkPos cp = chunk.getPos();
        // The chunk that completes a neighbourhood may be any of its nine, and a neighbour may have been given cells late.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk k = dx == 0 && dz == 0 ? chunk : level.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz);
                if (k != null && k.hasData(BeyondAttachments.HELD_LAKE)) CHECK.add(ChunkPos.asLong(cp.x + dx, cp.z + dz));
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.END) {
            CHECK.remove(chunk.getPos().toLong());
        }
    }

    // With the nine chunks complete, every feature that can reach the cells written here has already run.
    @SubscribeEvent
    public static void settleReady(ServerTickEvent.Post event) {
        if (CHECK.isEmpty()) return;
        ServerLevel end = event.getServer().getLevel(Level.END);
        if (end == null) {
            CHECK.clear();
            return;
        }
        int settled = 0;
        for (LongIterator it = CHECK.iterator(); it.hasNext() && settled < CHUNKS_PER_TICK; ) {
            long k = it.nextLong();
            it.remove();
            int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
            if (completeAround(end, cx, cz) && settleChunk(end, cx, cz)) settled++;
        }
    }

    /** Settles a complete chunk's held cells, false when it holds none. */
    public static boolean settleChunk(ServerLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        Held held = chunk == null ? null : chunk.getExistingDataOrNull(BeyondAttachments.HELD_LAKE);
        if (held == null) return false;
        chunk.removeData(BeyondAttachments.HELD_LAKE);
        try {
            settle(level, held.cells(), cx, cz);
        } catch (RuntimeException e) {
            TheBeyond.LOGGER.warn("[LakeFill] chunk [{},{}] left as it was", cx, cz, e);
        }
        return true;
    }

    @SubscribeEvent
    public static void reset(ServerStoppedEvent event) {
        CHECK.clear();
    }

    private static boolean completeAround(ServerLevel level, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
            }
        }
        return true;
    }

    private static void settle(ServerLevel level, List<Cell> cells, int cx, int cz) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        List<BoundingBox> pieces = pieceBoxes(level, cx, cz);
        int undone = 0, changed = 0, spill = 0, cleared = 0;
        // Newest first, so a cell two lakes wrote goes back to what the first one found.
        for (int i = cells.size() - 1; i >= 0; i--) {
            Cell c = cells.get(i);
            if (c.kind != UNDO) continue;
            BlockState now = level.getBlockState(mp.set(c.pos));
            // Inside a piece only the lake's own write goes back, since a template may have put its air there later.
            boolean in = inside(pieces, c.pos);
            boolean same = now == c.expect || !in && now.isAir() && c.expect.isAir();
            boolean grew = !in && !now.isAir() && now.getFluidState().isEmpty();
            if (now != c.put && (same || grew)) {
                level.setBlock(mp, c.put, FLAGS);
                undone++;
            }
        }
        Long2ObjectMap<BlockState> fill = new Long2ObjectOpenHashMap<>();
        for (Cell c : cells) {
            if (c.kind != FILL) continue;
            BlockState now = level.getBlockState(mp.set(c.pos));
            // Ground a later feature left on the dry floor, such as zymote or ore, takes the fluid like the stone.
            if ((now == c.expect || ground(now) && ground(c.expect)) && !inside(pieces, c.pos)) fill.put(c.pos, now);
            else changed++;
        }
        boolean again = !fill.isEmpty();
        while (again) {
            again = false;
            for (LongIterator it = fill.keySet().iterator(); it.hasNext(); ) {
                long p = it.nextLong();
                int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
                if (open(level, fill, mp, x + 1, y, z) || open(level, fill, mp, x - 1, y, z)
                        || open(level, fill, mp, x, y, z + 1) || open(level, fill, mp, x, y, z - 1)
                        || open(level, fill, mp, x, y - 1, z)) {
                    it.remove();
                    spill++;
                    again = true;
                }
            }
        }
        // The chances the dry lake already gave are read before the fluid goes in.
        Decoration decoration = fill.isEmpty() ? null : Decoration.before(level, cx, cz, fill.keySet());
        BlockState fluid = BeyondBlocks.GELLID_VOID.get().defaultBlockState();
        Long2IntOpenHashMap fluidTop = new Long2IntOpenHashMap();
        fluidTop.defaultReturnValue(Integer.MIN_VALUE);
        for (long p : fill.keySet()) {
            level.setBlock(mp.set(p), fluid, FLAGS);
            fluidTop.put(column(p), Math.max(fluidTop.get(column(p)), BlockPos.getY(p)));
        }
        // What grew on the dry floor stands in the dug air over the new fluid, where a wet lake grows nothing.
        LongArrayFIFOQueue loose = new LongArrayFIFOQueue();
        for (Cell c : cells) {
            if (c.kind != DUG || BlockPos.getY(c.pos) <= fluidTop.get(column(c.pos))) continue;
            BlockState now = level.getBlockState(mp.set(c.pos));
            if (now.isAir() || !now.getFluidState().isEmpty() || inside(pieces, c.pos)) continue;
            level.setBlock(mp, Blocks.CAVE_AIR.defaultBlockState(), FLAGS);
            cleared++;
            enqueueAround(loose, c.pos);
        }
        for (Long2IntOpenHashMap.Entry e : fluidTop.long2IntEntrySet()) {
            loose.enqueue(BlockPos.asLong(BlockPos.getX(e.getLongKey()), e.getIntValue() + 1, BlockPos.getZ(e.getLongKey())));
        }
        int dropped = dropUnsupported(level, loose, pieces);
        int grown = decoration == null ? 0 : decoration.place(level, fill.keySet());
        TheBeyond.LOGGER.debug("[LakeFill] chunk [{},{}] filled {} undone {} changed {} spill {} cleared {} dropped {} grown {}",
                cx, cz, fill.size(), undone, changed, spill, cleared, dropped, grown);
    }

    /** Solid, dry ground a feature may replace, as a lake takes for its floor. */
    private static boolean ground(BlockState s) {
        return s.isSolid() && s.getFluidState().isEmpty() && !s.is(BlockTags.FEATURES_CANNOT_REPLACE);
    }

    /** True when fluid beside this cell would run out into it. */
    private static boolean open(ServerLevel level, Long2ObjectMap<BlockState> fill, BlockPos.MutableBlockPos mp,
            int x, int y, int z) {
        if (fill.containsKey(BlockPos.asLong(x, y, z))) return false;
        BlockState s = level.getBlockState(mp.set(x, y, z));
        return !s.isSolid() && s.getFluidState().isEmpty();
    }

    private static void enqueueAround(LongArrayFIFOQueue queue, long p) {
        queue.enqueue(BlockPos.offset(p, 0, 1, 0));
        queue.enqueue(BlockPos.offset(p, 1, 0, 0));
        queue.enqueue(BlockPos.offset(p, -1, 0, 0));
        queue.enqueue(BlockPos.offset(p, 0, 0, 1));
        queue.enqueue(BlockPos.offset(p, 0, 0, -1));
    }

    /** Drops what the new fluid or a cleared cell left without support, cascading up and sideways. */
    private static int dropUnsupported(ServerLevel level, LongArrayFIFOQueue queue, List<BoundingBox> pieces) {
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int dropped = 0;
        for (int steps = 0; !queue.isEmpty() && steps < 4096; steps++) {
            long p = queue.dequeueLong();
            BlockState s = level.getBlockState(mp.set(p));
            if (s.isAir() || !s.getFluidState().isEmpty() || inside(pieces, p)) continue;
            boolean drop = s.getBlock() instanceof FallingBlock
                    ? level.getBlockState(mp.set(p).move(0, -1, 0)).isAir()
                    : !s.canSurvive(level, mp.set(p));
            if (!drop) continue;
            level.setBlock(mp.set(p), Blocks.AIR.defaultBlockState(), FLAGS);
            dropped++;
            enqueueAround(queue, p);
        }
        return dropped;
    }

    private static List<BoundingBox> pieceBoxes(ServerLevel level, int cx, int cz) {
        List<BoundingBox> boxes = new ArrayList<>();
        Set<StructureStart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos cp = new ChunkPos(cx + dx, cz + dz);
                for (StructureStart start : level.structureManager().startsForStructure(cp, s -> true)) {
                    if (!seen.add(start)) continue;
                    for (StructurePiece piece : start.getPieces()) boxes.add(piece.getBoundingBox());
                }
            }
        }
        return boxes;
    }

    private static boolean inside(List<BoundingBox> pieces, long pos) {
        int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
        for (int i = 0, n = pieces.size(); i < n; i++) {
            if (pieces.get(i).isInside(x, y, z)) return true;
        }
        return false;
    }

    private static long column(long pos) {
        return BlockPos.asLong(BlockPos.getX(pos), 0, BlockPos.getZ(pos));
    }

    /** Gives the tagged features their chances on the refilled cells once, as a lake wet from the start had them. */
    private static final class Decoration {
        private final List<PlacedFeature> features;
        private final long[] columns;
        private final int yLo, yHi;
        private final long seed;
        // Per feature and column, whether each layer's spot passed the feature's filters while the lake was dry.
        private final List<Long2ObjectMap<boolean[]>> dry;

        private Decoration(List<PlacedFeature> features, long[] columns, int yLo, int yHi, long seed) {
            this.features = features;
            this.columns = columns;
            this.yLo = yLo;
            this.yHi = yHi;
            this.seed = seed;
            this.dry = new ArrayList<>(features.size());
        }

        @Nullable
        static Decoration before(ServerLevel level, int cx, int cz, LongSet refilled) {
            Optional<HolderSet.Named<PlacedFeature>> tag =
                    level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getTag(LAKE_DECORATION);
            if (tag.isEmpty()) return null;
            List<PlacedFeature> features = new ArrayList<>();
            for (Holder<PlacedFeature> h : tag.get()) {
                List<PlacementModifier> mods = h.value().placement();
                if (!mods.isEmpty() && mods.get(0) instanceof CountOnEveryLayerPlacementAccessor) features.add(h.value());
            }
            if (features.isEmpty()) return null;
            // In the order the lake's biome decorates, since one feature's blocks open spots for the next.
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            List<PlacedFeature> order = new ArrayList<>();
            Holder<Biome> biome = level.getBiome(BlockPos.of(refilled.iterator().nextLong()));
            for (HolderSet<PlacedFeature> step : generator.getBiomeGenerationSettings(biome).features()) {
                for (Holder<PlacedFeature> h : step) order.add(h.value());
            }
            features.sort(Comparator.comparingInt(f -> order.contains(f) ? order.indexOf(f) : Integer.MAX_VALUE));
            // Spots near the fill, kept inside the complete nine chunks so nothing writes into a chunk still generating.
            int minX = (cx - 1) << 4, maxX = ((cx + 2) << 4) - 1, minZ = (cz - 1) << 4, maxZ = ((cz + 2) << 4) - 1;
            LongRBTreeSet cols = new LongRBTreeSet();
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (long p : refilled) {
                int px = BlockPos.getX(p), pz = BlockPos.getZ(p);
                lo = Math.min(lo, BlockPos.getY(p));
                hi = Math.max(hi, BlockPos.getY(p));
                for (int x = Math.max(minX, px - REACH); x <= Math.min(maxX, px + REACH); x++) {
                    for (int z = Math.max(minZ, pz - REACH); z <= Math.min(maxZ, pz + REACH); z++) {
                        cols.add(BlockPos.asLong(x, 0, z));
                    }
                }
            }
            Decoration d = new Decoration(features, cols.toLongArray(), lo - 3, hi + 4,
                    level.getSeed() ^ ChunkPos.asLong(cx, cz) * 0x9E3779B97F4A7C15L);
            for (int f = 0; f < features.size(); f++) {
                PlacementContext ctx = new PlacementContext(level, generator, Optional.of(features.get(f)));
                Long2ObjectMap<boolean[]> perColumn = new Long2ObjectOpenHashMap<>();
                for (long col : d.columns) {
                    int x = BlockPos.getX(col), z = BlockPos.getZ(col);
                    int[] spots = d.surfaces(level, x, z);
                    boolean[] ok = new boolean[spots.length];
                    for (int l = 0; l < spots.length; l++) {
                        ok[l] = filtered(features.get(f), ctx, d.spotSeed(f, x, z, l), new BlockPos(x, spots[l], z)) != null;
                    }
                    perColumn.put(col, ok);
                }
                d.dry.add(perColumn);
            }
            return d;
        }

        int place(ServerLevel level, LongSet refilled) {
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            int grown = 0;
            for (int f = 0; f < features.size(); f++) {
                PlacedFeature feature = features.get(f);
                PlacementContext ctx = new PlacementContext(level, generator, Optional.of(feature));
                ConfiguredFeature<?, ?> configured = feature.feature().value();
                CountOnEveryLayerPlacementAccessor count = (CountOnEveryLayerPlacementAccessor) feature.placement().get(0);
                // Spots are read before anything grows, as the layer count reads them, so a new block never opens its own.
                int[][] spots = new int[columns.length][];
                for (int c = 0; c < columns.length; c++) {
                    spots[c] = surfaces(level, BlockPos.getX(columns[c]), BlockPos.getZ(columns[c]));
                }
                for (int c = 0; c < columns.length; c++) {
                    int x = BlockPos.getX(columns[c]), z = BlockPos.getZ(columns[c]);
                    boolean[] wasOk = dry.get(f).get(columns[c]);
                    for (int l = 0; l < spots[c].length; l++) {
                        RandomSource random = RandomSource.create(spotSeed(f, x, z, l) ^ 0x5DEECE66DL);
                        int picks = 0;
                        // The layer count draws its columns one at a time out of 256, so a spot is drawn this many times.
                        for (int i = count.the_beyond$count().sample(random); i > 0; i--) if (random.nextInt(256) == 0) picks++;
                        if (picks == 0) continue;
                        BlockPos at = filtered(feature, ctx, spotSeed(f, x, z, l), new BlockPos(x, spots[c][l], z));
                        if (at == null) continue;
                        boolean already = wasOk != null && l < wasOk.length && wasOk[l];
                        for (int k = 0; k < picks; k++) {
                            if (!already) {
                                if (configured.place(level, generator, random, at)) grown++;
                            } else if (configured.config() instanceof RandomPatchConfiguration patch) {
                                grown += patchOnFill(level, generator, random, at, patch, refilled);
                            }
                        }
                    }
                }
            }
            return grown;
        }

        /** Tops of solid blocks under open cells inside the window, as the Beyond's layer count finds them. */
        private int[] surfaces(ServerLevel level, int x, int z) {
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(x, yHi + 1, z);
            int[] out = new int[8];
            int n = 0;
            boolean above = solid(level.getBlockState(mp));
            for (int y = yHi; y >= yLo; y--) {
                boolean s = solid(level.getBlockState(mp.setY(y)));
                if (!above && s) {
                    if (n == out.length) out = java.util.Arrays.copyOf(out, n * 2);
                    out[n++] = y + 1;
                }
                above = s;
            }
            return java.util.Arrays.copyOf(out, n);
        }

        private static boolean solid(BlockState s) {
            return !s.isAir() && s.getFluidState().isEmpty();
        }

        private long spotSeed(int feature, int x, int z, int layer) {
            return seed ^ (feature + 1) * 0xC2B2AE3D27D4EB4FL ^ BlockPos.asLong(x, layer, z) * 0x165667B19E3779F9L;
        }

        /** Where the feature's modifiers after the layer count put this spot, or null, the same draws in either pass. */
        @Nullable
        private static BlockPos filtered(PlacedFeature feature, PlacementContext ctx, long seed, BlockPos pos) {
            RandomSource random = RandomSource.create(seed);
            Stream<BlockPos> stream = Stream.of(pos);
            List<PlacementModifier> mods = feature.placement();
            for (int i = 1; i < mods.size(); i++) {
                PlacementModifier m = mods.get(i);
                stream = stream.flatMap(p -> m.getPositions(ctx, random, p));
            }
            return stream.findFirst().map(BlockPos::immutable).orElse(null);
        }

        /** A patch's tries that land on the refilled fluid, the only ones the dry lake made fail. */
        private static int patchOnFill(ServerLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                RandomPatchConfiguration patch, LongSet refilled) {
            int xz = patch.xzSpread() + 1, y = patch.ySpread() + 1, grown = 0;
            BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
            for (int t = 0; t < patch.tries(); t++) {
                mp.setWithOffset(origin, random.nextInt(xz) - random.nextInt(xz), random.nextInt(y) - random.nextInt(y),
                        random.nextInt(xz) - random.nextInt(xz));
                if (refilled.contains(BlockPos.asLong(mp.getX(), mp.getY() - 1, mp.getZ()))
                        && patch.feature().value().place(level, generator, random, mp)) grown++;
            }
            return grown;
        }
    }

    private static void log(Lake lake, boolean placed) {
        boolean[] fill = new boolean[lake.shape.length], undo = new boolean[lake.shape.length];
        for (Cell c : lake.cells) {
            int x = BlockPos.getX(c.pos) - lake.base.getX(), y = BlockPos.getY(c.pos) - lake.base.getY();
            int z = BlockPos.getZ(c.pos) - lake.base.getZ();
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= 8 || !lake.shape[(x * 16 + z) * 8 + y]) continue;
            if (c.kind == FILL && !lake.erased) fill[(x * 16 + z) * 8 + y] = true;
            if (c.kind == UNDO) undo[(x * 16 + z) * 8 + y] = true;
        }
        TheBeyond.LOGGER.debug("[LakeTest] base={},{},{} placed={} notch={} shape={} fillmask={} undomask={}",
                lake.base.getX(), lake.base.getY(), lake.base.getZ(), placed, lake.notch ? 1 : 0,
                hex(lake.shape), hex(fill), hex(undo));
    }

    private static String hex(boolean[] bits) {
        StringBuilder sb = new StringBuilder(bits.length / 4);
        for (int i = 0; i < bits.length; i += 4) {
            int n = (bits[i] ? 8 : 0) | (bits[i + 1] ? 4 : 0) | (bits[i + 2] ? 2 : 0) | (bits[i + 3] ? 1 : 0);
            sb.append(Character.forDigit(n, 16));
        }
        return sb.toString();
    }
}
