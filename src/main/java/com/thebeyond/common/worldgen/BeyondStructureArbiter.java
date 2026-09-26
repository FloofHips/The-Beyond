package com.thebeyond.common.worldgen;

import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Drops the smaller of two crossing structures, since two mods' structure sets never see each other. */
public final class BeyondStructureArbiter {

    private BeyondStructureArbiter() {}

    private static final Set<StructureStart> VETOED = ConcurrentHashMap.newKeySet();

    private static final int PIECE_CROSS_DEPTH = 2;

    /** Big enough that anything inside it is a mistake, and kept because vanilla only remembers starts within 8 chunks. */
    private static final int GIANT_SPAN = 48;
    private static final int GIANT_CAP = 512;
    private static final Set<StructureStart> GIANTS = ConcurrentHashMap.newKeySet();

    /** Recent starts, so small structures from different chunks still meet. The oldest leave first: generation is local. */
    private static final int RECENT_CAP = 3072;
    private static final Set<StructureStart> RECENT = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ConcurrentLinkedQueue<StructureStart> RECENT_ORDER =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private static void the_beyond$remember(StructureStart start) {
        if (!RECENT.add(start)) return;
        RECENT_ORDER.add(start);
        while (RECENT.size() > RECENT_CAP) {
            StructureStart oldest = RECENT_ORDER.poll();
            if (oldest == null) break;
            RECENT.remove(oldest);
        }
    }

    public static boolean isVetoed(StructureStart start) {
        return start != null && VETOED.contains(start);
    }

    /** Vanilla's structures carry the progression, so they never yield. Ours yield like anyone else's. */
    private static boolean the_beyond$immune(ResourceLocation key) {
        return key == null || "minecraft".equals(key.getNamespace());
    }

    private static long the_beyond$volume(BoundingBox b) {
        return (long) b.getXSpan() * b.getYSpan() * b.getZSpan();
    }

    private static boolean the_beyond$yieldsTo(BoundingBox self, ResourceLocation selfKey,
            BoundingBox rival, ResourceLocation rivalKey) {
        long a = the_beyond$volume(self), b = the_beyond$volume(rival);
        if (a != b) return a < b;
        return selfKey.compareTo(rivalKey) > 0;
    }

    public static void reset() {
        VETOED.clear();
        GIANTS.clear();
        RECENT.clear();
        RECENT_ORDER.clear();
    }


    /** Needs two blocks of overlap on every axis, so touching faces do not count. */
    private static boolean the_beyond$piecesCross(StructureStart a, StructureStart b) {
        return the_beyond$piecesCross(a.getPieces(), b);
    }

    private static boolean the_beyond$piecesCross(List<StructurePiece> a, StructureStart b) {
        for (StructurePiece pa : a) {
            BoundingBox ba = pa.getBoundingBox();
            if (!ba.intersects(b.getBoundingBox())) continue;
            for (StructurePiece pb : b.getPieces()) {
                BoundingBox bb = pb.getBoundingBox();
                int dx = Math.min(ba.maxX(), bb.maxX()) - Math.max(ba.minX(), bb.minX()) + 1;
                int dy = Math.min(ba.maxY(), bb.maxY()) - Math.max(ba.minY(), bb.minY()) + 1;
                int dz = Math.min(ba.maxZ(), bb.maxZ()) - Math.max(ba.minZ(), bb.minZ()) + 1;
                if (dx >= PIECE_CROSS_DEPTH && dy >= PIECE_CROSS_DEPTH && dz >= PIECE_CROSS_DEPTH) return true;
            }
        }
        return false;
    }

    /** The known start a layout would lose to, so a seat can try another layout first. */
    @org.jetbrains.annotations.Nullable
    public static ResourceLocation wouldYieldTo(Structure structure, @org.jetbrains.annotations.Nullable ResourceLocation key,
            List<StructurePiece> pieces, Registry<Structure> structures) {
        if (key == null || pieces.isEmpty() || the_beyond$immune(key)) return null;
        BoundingBox box = BoundingBox.encapsulatingBoxes(pieces.stream().map(StructurePiece::getBoundingBox)::iterator)
                .map(structure::adjustBoundingBox).orElse(null);
        if (box == null) return null;
        for (Set<StructureStart> known : List.of(GIANTS, RECENT)) {
            for (StructureStart rival : known) {
                if (rival == null || !rival.isValid() || VETOED.contains(rival) || rival.getStructure() == structure) continue;
                BoundingBox rivalBox = rival.getBoundingBox();
                if (!box.intersects(rivalBox) || !the_beyond$piecesCross(pieces, rival)) continue;
                ResourceLocation rivalKey = structures.getKey(rival.getStructure());
                if (rivalKey != null && the_beyond$yieldsTo(box, key, rivalBox, rivalKey)) return rivalKey;
            }
        }
        return null;
    }

    public static int arbitrate(ChunkAccess chunk, StructureManager manager, Registry<Structure> structures,
            Collection<StructureStart> known) {
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return 0;
        return the_beyond$run(chunk, manager, structures, known, new ArrayList<>(starts.entrySet()));
    }

    /** Second look once the neighbourhood is known, for a start made before its rival. */
    public static int arbitrateBeforePlacement(ChunkAccess chunk, StructureManager manager,
            Registry<Structure> structures, Collection<StructureStart> known) {
        List<Map.Entry<Structure, StructureStart>> here = new ArrayList<>();
        for (StructureStart st : manager.startsForStructure(chunk.getPos(), s -> true)) {
            if (st != null && st.isValid()) here.add(Map.entry(st.getStructure(), st));
        }
        if (here.isEmpty()) return 0;
        return the_beyond$run(chunk, null, structures, known, here);
    }

    private static int the_beyond$run(ChunkAccess chunk, StructureManager manager, Registry<Structure> structures,
            Collection<StructureStart> known, List<Map.Entry<Structure, StructureStart>> fresh) {
        List<StructureStart> rivals = new ArrayList<>(known);
        rivals.addAll(GIANTS);
        rivals.addAll(RECENT);
        for (Map.Entry<Structure, StructureStart> e : fresh) {
            StructureStart st = e.getValue();
            if (st == null || !st.isValid()) continue;
            rivals.add(st);
            the_beyond$remember(st);
            BoundingBox b = st.getBoundingBox();
            if (GIANTS.size() < GIANT_CAP && Math.max(b.getXSpan(), b.getZSpan()) >= GIANT_SPAN) GIANTS.add(st);
        }
        int dropped = 0;
        for (Map.Entry<Structure, StructureStart> e : fresh) {
            StructureStart self = e.getValue();
            if (self == null || !self.isValid() || VETOED.contains(self)) continue;
            ResourceLocation selfKey = structures.getKey(e.getKey());
            BoundingBox selfBox = self.getBoundingBox();
            for (StructureStart rival : rivals) {
                if (rival == null || rival == self || !rival.isValid() || VETOED.contains(rival)) continue;
                if (rival.getStructure() == e.getKey()) continue;   // same structure: its own spacing owns it
                BoundingBox rivalBox = rival.getBoundingBox();
                if (!selfBox.intersects(rivalBox)) continue;
                if (!the_beyond$piecesCross(self, rival)) continue;
                ResourceLocation rivalKey = structures.getKey(rival.getStructure());
                if (selfKey == null || rivalKey == null) {   // a start this registry cannot name is not judged
                    if (BeyondGenDiagnostics.loggedArbitration.add("unnamed")) {
                        com.thebeyond.TheBeyond.LOGGER.warn(
                                "[Beyond] structure overlap not judged: {} {} crosses a start this world's registry cannot name, {}",
                                selfKey, selfBox, rivalBox);
                    }
                    continue;
                }
                boolean selfYields = the_beyond$yieldsTo(selfBox, selfKey, rivalBox, rivalKey);
                StructureStart loser = selfYields ? self : rival;
                ResourceLocation loserKey = selfYields ? selfKey : rivalKey;
                ResourceLocation winnerKey = selfYields ? rivalKey : selfKey;
                if (the_beyond$immune(loserKey)) continue;
                VETOED.add(loser);
                dropped++;
                if (loser == self && manager != null) {
                    manager.setStartForStructure(SectionPos.bottomOf(chunk), e.getKey(),
                            StructureStart.INVALID_START, chunk);
                }
                if (BeyondGenDiagnostics.loggedArbitration.add(loserKey + "|" + winnerKey)) {
                    com.thebeyond.TheBeyond.LOGGER.info(
                            "[Beyond] structure overlap: {} {} dropped, it was born inside {} {}",
                            loserKey, (selfYields ? selfBox : rivalBox), winnerKey, (selfYields ? rivalBox : selfBox));
                }
                if (loser == self) break;
            }
        }
        return dropped;
    }
}
