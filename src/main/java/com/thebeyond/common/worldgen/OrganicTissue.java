package com.thebeyond.common.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the painter left in a chunk, each network block's kind and bulb, and the block a root replaced. */
public final class OrganicTissue {

    private OrganicTissue() {}

    public static int pack(int x, int y, int z, int minY) {
        return (y - minY) << 8 | (z & 15) << 4 | (x & 15);
    }

    public static final byte GONE = -1;
    static final byte CLEARED = -2;

    /** Per block: position (y << 8 | z << 4 | x from the chunk's lowest block), kind, owner slot and replaced block. */
    public static final class Held implements INBTSerializable<CompoundTag> {
        int count;
        int[] pos = new int[0], owner = new int[0], rock = new int[0], was = new int[0];
        byte[] kind = new byte[0], thick = new byte[0], drip = new byte[0];
        // owner slots: the organism's cell key and the bulb's index in it
        long[] cells = new long[0];
        int[] organs = new int[0];
        final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
        private final Map<Long, Map<Integer, Integer>> slots = new HashMap<>();
        // per organism painted here: its setup and its organ table (OrganicNetwork.ORGAN floats per organ)
        private final Map<Long, String> setups = new HashMap<>();
        private final Map<Long, float[]> tables = new HashMap<>();
        private Map<Integer, Integer> at;

        public Held() {}

        /** Adds a painted block, owned by bulb organ of the organism at cell (organ -1 for none), replaced null for air. */
        public void add(int packed, byte k, long cell, int organ, BlockState replaced) {
            add(packed, k, cell, organ, replaced, (byte) -1);
        }

        public void add(int packed, byte k, long cell, int organ, BlockState replaced, byte wide) {
            add(packed, k, cell, organ, replaced, wide, (byte) 0);
        }

        public void add(int packed, byte k, long cell, int organ, BlockState replaced, byte wide, byte dripCode) {
            if (count == pos.length) {
                int size = Math.max(64, count * 2);
                pos = Arrays.copyOf(pos, size);
                owner = Arrays.copyOf(owner, size);
                rock = Arrays.copyOf(rock, size);
                kind = Arrays.copyOf(kind, size);
                was = Arrays.copyOf(was, size);
                thick = Arrays.copyOf(thick, size);
                drip = Arrays.copyOf(drip, size);
            }
            pos[count] = packed;
            kind[count] = k;
            owner[count] = slot(cell, organ);
            rock[count] = replaced == null ? -1 : index(replaced);
            was[count] = -1;
            thick[count] = wide;
            drip[count] = dripCode;
            if (at != null) at.put(packed, count);
            count++;
        }

        /** Keeps what any chunk of the organism needs to rebuild it: its setup and organ table. */
        public void organism(long cell, String setup, float[] organTable) {
            setups.putIfAbsent(cell, setup);
            tables.putIfAbsent(cell, organTable.clone());
        }

        public String setup(long cell) {
            return setups.get(cell);
        }

        public float[] table(long cell) {
            float[] t = tables.get(cell);
            return t == null ? null : t.clone();
        }

        /** The entry at this packed position, or -1. */
        public int find(int packed) {
            if (at == null) {
                at = new HashMap<>(count * 2);
                for (int i = 0; i < count; i++) at.put(pos[i], i);
            }
            Integer i = at.get(packed);
            return i == null ? -1 : i;
        }

        public void remove(int i) {
            kind[i] = GONE;
        }

        /** The light took this block: it stays gone until it grows back as the state it had. */
        public void clear(int i, BlockState tissue) {
            if (kind[i] < 0) return;
            kind[i] = (byte) (CLEARED - kind[i]);
            was[i] = index(tissue);
        }

        public boolean cleared(int i) {
            return kind[i] <= CLEARED;
        }

        public boolean gone(int i) {
            return kind[i] < 0;
        }

        /** Gives a cleared entry its kind again and returns the state it had (null if none was kept). */
        public BlockState regrow(int i) {
            if (!cleared(i)) return null;
            BlockState s = was[i] < 0 ? null : palette.get(was[i]);
            kind[i] = (byte) (CLEARED - kind[i]);
            was[i] = -1;
            return s;
        }

        private int index(BlockState s) {
            return paletteIndex.computeIfAbsent(s, k -> {
                palette.add(k);
                return palette.size() - 1;
            });
        }

        private int slot(long cell, int organ) {
            Map<Integer, Integer> m = slots.computeIfAbsent(cell, c -> new HashMap<>());
            return m.computeIfAbsent(organ, o -> {
                cells = Arrays.copyOf(cells, cells.length + 1);
                organs = Arrays.copyOf(organs, organs.length + 1);
                cells[cells.length - 1] = cell;
                organs[organs.length - 1] = organ;
                return cells.length - 1;
            });
        }

        public int size() {
            return count;
        }

        public int packed(int i) {
            return pos[i];
        }

        public byte kind(int i) {
            return kind[i];
        }

        public long cell(int i) {
            return owner[i] < 0 ? 0 : cells[owner[i]];
        }

        public int organ(int i) {
            return owner[i] < 0 ? -1 : organs[owner[i]];
        }

        public int thick(int i) {
            return thick[i];
        }

        public int drip(int i) {
            return drip[i];
        }

        /** The block the root replaced, or null when the network was painted into air. */
        public BlockState replaced(int i) {
            return rock[i] < 0 ? null : palette.get(rock[i]);
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putIntArray("pos", Arrays.copyOf(pos, count));
            tag.putByteArray("kind", Arrays.copyOf(kind, count));
            tag.putIntArray("owner", Arrays.copyOf(owner, count));
            tag.putIntArray("rock", Arrays.copyOf(rock, count));
            tag.putIntArray("was", Arrays.copyOf(was, count));
            tag.putByteArray("thick", Arrays.copyOf(thick, count));
            tag.putByteArray("drip", Arrays.copyOf(drip, count));
            tag.putLongArray("cells", cells.clone());
            tag.putIntArray("organs", organs.clone());
            ListTag list = new ListTag();
            for (BlockState s : palette) list.add(NbtUtils.writeBlockState(s));
            tag.put("palette", list);
            ListTag orgs = new ListTag();
            for (Map.Entry<Long, float[]> e : tables.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putLong("cell", e.getKey());
                t.putString("setup", setups.getOrDefault(e.getKey(), ""));
                float[] f = e.getValue();
                int[] bits = new int[f.length];
                for (int i = 0; i < f.length; i++) bits[i] = Float.floatToRawIntBits(f[i]);
                t.putIntArray("organs", bits);
                orgs.add(t);
            }
            tag.put("organisms", orgs);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            pos = tag.getIntArray("pos").clone();
            kind = tag.getByteArray("kind").clone();
            owner = tag.getIntArray("owner").clone();
            rock = tag.getIntArray("rock").clone();
            int[] w = tag.getIntArray("was");
            was = w.length == pos.length ? w.clone() : new int[pos.length];
            if (w.length != pos.length) Arrays.fill(was, -1);
            byte[] wd = tag.getByteArray("thick");
            thick = wd.length == pos.length ? wd.clone() : new byte[pos.length];
            if (wd.length != pos.length) Arrays.fill(thick, (byte) -1);
            byte[] dr = tag.getByteArray("drip");
            drip = dr.length == pos.length ? dr.clone() : new byte[pos.length];
            cells = tag.getLongArray("cells").clone();
            organs = tag.getIntArray("organs").clone();
            count = pos.length;
            palette.clear();
            paletteIndex.clear();
            for (Tag t : tag.getList("palette", Tag.TAG_COMPOUND)) {
                BlockState s = NbtUtils.readBlockState(provider.lookupOrThrow(Registries.BLOCK), (CompoundTag) t);
                paletteIndex.putIfAbsent(s, palette.size());
                palette.add(s);
            }
            slots.clear();
            for (int i = 0; i < cells.length; i++) slots.computeIfAbsent(cells[i], c -> new HashMap<>()).put(organs[i], i);
            setups.clear();
            tables.clear();
            at = null;
            for (Tag t : tag.getList("organisms", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                int[] bits = c.getIntArray("organs");
                float[] f = new float[bits.length];
                for (int i = 0; i < bits.length; i++) f[i] = Float.intBitsToFloat(bits[i]);
                setups.put(c.getLong("cell"), c.getString("setup"));
                tables.put(c.getLong("cell"), f);
            }
        }
    }
}
