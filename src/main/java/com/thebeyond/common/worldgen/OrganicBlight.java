package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.debug.OrganicWatch;
import com.thebeyond.common.registry.BeyondAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** The corruption biome around the light-shy network and its zone line blocks, held in the chunk and written once it is full. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class OrganicBlight {
    public static final ResourceKey<Biome> BLIGHT = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("the_beyond", "org_blight"));
    // each chunk sent makes clients remesh every section of its 3x3 columns, so changes go out at most every SYNC ticks
    static final int SYNC = Math.max(1, Integer.getInteger("beyond.sub.org.biomeSync", 10));

    /** The server keeps the new biomes at once, players get them with the next batch. */
    static void resendLater(ServerLevel level, List<? extends ChunkAccess> changed) {
        OrganicLevelState s = OrganicLevelState.of(level);
        for (ChunkAccess c : changed) s.biomeDirty.add(c.getPos().toLong());
        s.biomeCalls++;
    }

    static void flush(ServerLevel level, OrganicLevelState s, long now) {
        if (s.biomeDirty.isEmpty() || now < s.biomeSent + SYNC) return;
        List<ChunkAccess> out = new ArrayList<>();
        for (long k : s.biomeDirty) {
            int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
            if (level.hasChunk(cx, cz)) out.add(level.getChunk(cx, cz));
        }
        s.biomeDirty.clear();
        s.biomeSent = now;
        if (out.isEmpty()) return;
        level.getChunkSource().chunkMap.resendBiomesForChunks(out);
        s.biomeSends++;
        s.biomeChunks += out.size();
    }

    /** A bit per quart (y << 4 | z << 2 | x) in one long per section, zone line blocks as y << 8 | z << 4 | x from the bottom. */
    public static final class Mask implements INBTSerializable<CompoundTag> {
        long[] blight = new long[0], clearing = new long[0];
        int[] line = new int[0];

        public Mask() {}

        Mask(int sections) {
            blight = new long[sections];
            clearing = new long[sections];
        }

        static int bit(int qx, int qy, int qz) {
            return (qy & 3) << 4 | (qz & 3) << 2 | (qx & 3);
        }

        boolean empty() {
            return line.length == 0 && count(blight) + count(clearing) == 0;
        }

        int count(long[] m) {
            int n = 0;
            for (long v : m) n += Long.bitCount(v);
            return n;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putLongArray("blight", blight);
            tag.putLongArray("clearing", clearing);
            tag.putIntArray("line", line);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            blight = tag.getLongArray("blight");
            clearing = tag.getLongArray("clearing");
            line = tag.getIntArray("line");
        }
    }

    private OrganicBlight() {}

    // Every chunk that decorates near this one has run, so no feature reads the biome or the line, and none writes over it.
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getChunk() instanceof LevelChunk chunk)
                || !(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Mask m = chunk.getExistingDataOrNull(BeyondAttachments.ORG_BLIGHT);
        if (m != null) apply(chunk, level, m);
        OrganicSurface.apply(level, chunk);
    }

    private static void apply(LevelChunk chunk, ServerLevel level, Mask m) {
        chunk.removeData(BeyondAttachments.ORG_BLIGHT);
        ChunkPos cp = chunk.getPos();
        int placed = 0, air = 0, partial = 0, structure = 0, foreign = 0;
        Map<String, Integer> replaced = new TreeMap<>(), kept = new TreeMap<>();
        boolean refs = !chunk.getAllReferences().isEmpty();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int p : m.line) {
            at.set(cp.getMinBlockX() + (p & 15), chunk.getMinBuildHeight() + (p >>> 8), cp.getMinBlockZ() + (p >> 4 & 15));
            BlockState old = chunk.getBlockState(at);
            if (old.isAir()) {
                air++;
                continue;
            }
            if (!old.isCollisionShapeFullBlock(chunk, at)) {
                partial++;
                continue;
            }
            if (!OrganicSurface.ground(old)) {
                foreign++;
                kept.merge(BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath(), 1, Integer::sum);
                continue;
            }
            if (refs && OrganicSurface.inStructure(level, at)) {
                structure++;
                continue;
            }
            chunk.setBlockState(at, OrganicExperiment.LINE_BLOCK.defaultBlockState(), false);
            placed++;
            if (OrganicWatch.ACTIVE) replaced.merge(BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath(), 1, Integer::sum);
        }
        if (m.count(m.blight) + m.count(m.clearing) > 0) writeBiomes(chunk, level, m);
        chunk.setUnsaved(true);
        ChunkStatus least = null;
        int absent = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                ChunkHolder h = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(cp.x + dx, cp.z + dz));
                ChunkStatus st = h == null ? null : h.getPersistedStatus();
                if (st == null) absent++;
                else if (least == null || !least.isOrBefore(st)) least = st;
            }
        }
        TheBeyond.LOGGER.info(String.format(Locale.ROOT, "ORG blight chunk %d %d | quarts %d | clearing %d | neighbours least %s"
                + " absent %d | line %d placed %d skipped air %d partial %d | guard structure %d foreign %d %s%s", cp.x, cp.z,
                m.count(m.blight), m.count(m.clearing), least, absent, m.line.length, placed, air, partial, structure, foreign,
                kept, OrganicWatch.ACTIVE ? " | replaced " + replaced : ""));
    }

    private static void writeBiomes(LevelChunk chunk, ServerLevel level, Mask m) {
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> blight = biomes.getHolder(BLIGHT).orElse(null);
        Holder<Biome> clearing = OrganicLight.RELEASED.equals("genuine") ? null : biomes.getHolder(ResourceKey.create(
                Registries.BIOME, ResourceLocation.parse(OrganicLight.RELEASED))).orElse(null);
        ChunkPos cp = chunk.getPos();
        if (blight == null) {
            TheBeyond.LOGGER.error("ORG_FAILURE blight biome missing, chunk {} {} left genuine", cp.x, cp.z);
            return;
        }
        int minSection = chunk.getMinSection();
        chunk.fillBiomesFromNoise((x, y, z, sampler) -> {
            int s = (y >> 2) - minSection;
            if (s >= 0 && s < m.blight.length) {
                int bit = Mask.bit(x, y, z);
                if ((m.blight[s] >>> bit & 1) != 0) return blight;
                if (clearing != null && (m.clearing[s] >>> bit & 1) != 0) return clearing;
            }
            return chunk.getNoiseBiome(x, y, z);
        }, level.getChunkSource().randomState().sampler());
    }
}
