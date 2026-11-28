package com.thebeyond.common.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

import java.util.Set;

/** Lets an environment scan jump over steps whose predicates read only air, since they answer as on the step before. */
public final class ScanSkip {
    public static final int[] NONE = new int[0];
    private static final Set<String> FIXED_READS = Set.of("minecraft:matching_blocks", "minecraft:matching_block_tag",
            "minecraft:matching_fluids", "minecraft:solid", "minecraft:replaceable", "minecraft:has_sturdy_face",
            "minecraft:inside_world_bounds", "minecraft:true", "minecraft:not", "minecraft:any_of", "minecraft:all_of");

    private ScanSkip() {}

    /** The offsets the predicates read, as x, y, z triples, or NONE when one of them reads anything else. */
    public static int[] reads(HolderLookup.Provider registries, BlockPredicate... predicates) {
        IntArrayList out = new IntArrayList();
        try {
            var ops = registries.createSerializationContext(JsonOps.INSTANCE);
            for (BlockPredicate predicate : predicates) {
                JsonElement json = BlockPredicate.CODEC.encodeStart(ops, predicate).result().orElse(null);
                if (json == null || !collect(json, out)) return NONE;
            }
        } catch (RuntimeException e) {
            return NONE;
        }
        IntArrayList unique = new IntArrayList();
        for (int i = 0; i < out.size(); i += 3) {
            boolean seen = false;
            for (int j = 0; j < unique.size() && !seen; j += 3) {
                seen = unique.getInt(j) == out.getInt(i) && unique.getInt(j + 1) == out.getInt(i + 1)
                        && unique.getInt(j + 2) == out.getInt(i + 2);
            }
            if (!seen) unique.addElements(unique.size(), new int[]{out.getInt(i), out.getInt(i + 1), out.getInt(i + 2)});
        }
        return unique.isEmpty() ? NONE : unique.toIntArray();
    }

    private static boolean collect(JsonElement json, IntArrayList out) {
        if (!json.isJsonObject()) return false;
        JsonObject o = json.getAsJsonObject();
        if (!o.has("type") || !FIXED_READS.contains(o.get("type").getAsString())) return false;
        String type = o.get("type").getAsString();
        if (type.equals("minecraft:not")) return o.has("predicate") && collect(o.get("predicate"), out);
        if (type.equals("minecraft:any_of") || type.equals("minecraft:all_of")) {
            if (!o.has("predicates") || !o.get("predicates").isJsonArray()) return false;
            for (JsonElement child : o.getAsJsonArray("predicates")) {
                if (!collect(child, out)) return false;
            }
            return true;
        }
        if (type.equals("minecraft:true")) return true;
        int x = 0, y = 0, z = 0;
        if (o.has("offset")) {
            JsonArray a = o.getAsJsonArray("offset");
            if (a.size() != 3) return false;
            x = a.get(0).getAsInt();
            y = a.get(1).getAsInt();
            z = a.get(2).getAsInt();
        }
        out.addElements(out.size(), new int[]{x, y, z});
        return true;
    }

    /** Further steps whose reads stay in sections of plain air, capped by left and the scan's heights. Moves pos past them. */
    public static int skip(WorldGenLevel level, BlockPos.MutableBlockPos pos, int dy, int[] reads, int minY, int maxY, int left) {
        int k = Math.min(left, dy > 0 ? maxY - pos.getY() : pos.getY() - minY);
        for (int i = 0; i < reads.length && k > 0; i += 3) {
            int x = pos.getX() + reads[i], y = pos.getY() + reads[i + 1], z = pos.getZ() + reads[i + 2];
            if (!level.hasChunk(x >> 4, z >> 4)) return 0;
            ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
            int s = chunk.getSectionIndex(y);
            if (s < 0 || s >= chunk.getSectionsCount()) return 0;
            LevelChunkSection section = chunk.getSection(s);
            if (!section.hasOnlyAir() || section.maybeHas(state -> !state.is(Blocks.AIR))) return 0;
            k = Math.min(k, dy > 0 ? 15 - (y & 15) : y & 15);
        }
        if (k <= 0) return 0;
        pos.move(0, dy * k, 0);
        return k;
    }
}
