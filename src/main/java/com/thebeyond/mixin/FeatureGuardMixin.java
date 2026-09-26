package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import com.thebeyond.common.worldgen.BeyondStructureArbiter;
import net.minecraft.core.registries.Registries;
import com.thebeyond.common.worldgen.BeyondStructureCarver;
import com.thebeyond.common.worldgen.FloatingFeatureGuard;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

/** Arms {@link FeatureGuard} with the exact volume the island carve cleared around nearby carve-type
 *  structures, so decorations can't pierce them or float in the carved margin. Cleared in a finally. */
@Mixin(ChunkGenerator.class)
public abstract class FeatureGuardMixin {

    @WrapMethod(method = "applyBiomeDecoration")
    private void the_beyond$guardFeatures(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structureManager, Operation<Void> original) {
        FeatureGuard.clearVolume();
        BeyondStructureCarver.TerrainSnapshot debrisSnapshot = null;
        List<BeyondEndChunkGenerator.CarveMask> debrisMasks = null;
        BeyondEndChunkGenerator debrisGen = null;
        try {
            if (level.getLevel().dimension() == Level.END
                    && ((Object) this) instanceof BeyondEndChunkGenerator gen) {
                // Before every write in this stage, so the debris sweep judges the island alone without our blocks.
                try {
                    List<BeyondEndChunkGenerator.CarveMask> pre =
                            gen.the_beyond$collectCarveMasks(structureManager, chunk.getPos());
                    if (!pre.isEmpty()) {
                        BeyondStructureArbiter.arbitrateBeforePlacement(chunk, structureManager,
                                level.registryAccess().registryOrThrow(Registries.STRUCTURE),
                                gen.the_beyond$knownStarts());
                        debrisGen = gen;
                        debrisMasks = pre;
                        // The knees go in first, so the sweeps below count them as island, and after the arbiter's vetoes.
                        com.thebeyond.common.worldgen.BunkerKnee.fill(level, chunk, gen.the_beyond$collectCarveMasks(structureManager, chunk.getPos()));
                        debrisSnapshot = gen.the_beyond$snapshotCarveTerrain(chunk, pre);
                        the_beyond$sweepDebris(gen, level, chunk, pre, debrisSnapshot);
                    }
                } catch (Throwable ignored) { }
                // Vanilla skips chunks outside a large structure's reference window (a station's far tower), so build them here.
                try { gen.the_beyond$placeUnreferencedCarveStructures(level, structureManager, chunk); }
                catch (Throwable ignored) { /* fail-soft: a missed far build, never a crash */ }
                try {
                    ChunkPos cp = chunk.getPos();
                    // Guard the whole erodable band, not just the hard core: fringe erosion is stochastic,
                    // so a feature on a column the noise later cuts would be left as a floating stub.
                    List<BeyondEndChunkGenerator.CarveMask> masks = gen.the_beyond$collectCarveMasks(structureManager, cp);
                    if (!masks.isEmpty()) {
                        // Matches the carve's own Y-band; outside it edgeGradient forces pure air anyway.
                        final int loY = BeyondTerrainState.getDimMinY() + 33;
                        final int hiY = BeyondTerrainState.getDimMaxY() - 32;
                        // Cheap geometric prefilter (guardOutsideDist) then veto only where the carve actually
                        // removed terrain, so an intact separate island near the station keeps its decorations.
                        FeatureGuard.setVolume((x, y, z) -> y >= loY && y < hiY
                                && BeyondEndChunkGenerator.the_beyond$guardOutsideDist(masks, x, y, z)
                                        <= BeyondEndChunkGenerator.CARVE_ERODE_REACH
                                && BeyondEndChunkGenerator.the_beyond$carveRemovedAirAt(masks, x, y, z));
                        // Structure volume: bar the gellid-void pool from intruding any carve structure's footprint.
                        FeatureGuard.setStructureVolume((x, y, z) ->
                                BeyondEndChunkGenerator.the_beyond$insideAnyStructureFootprint(masks, x, y, z));
                        // A cleared feature may cross the margin but never overwrite a piece, as vanilla settles it.
                        FeatureGuard.setPieceVolume((x, y, z) ->
                                BeyondEndChunkGenerator.the_beyond$insideAnyPieceBox(masks, x, y, z));
                    }
                } catch (Throwable t) {
                    FeatureGuard.clearVolume();   // fail-soft: no guard, vanilla decoration wins
                }
            }
            original.call(level, chunk, structureManager);
            // After placement template air severs rock too, and this runs before the sweep that cleans it.
            if (debrisSnapshot != null) {
                the_beyond$sweepDebris(debrisGen, level, chunk, debrisMasks, debrisSnapshot);
            }
            // Drop base-less parts of grounded features the guard severed above. Fail-soft.
            try { FloatingFeatureGuard.sweep(level); } catch (Throwable ignored) { }
        } finally {
            FeatureGuard.clearVolume();
            FloatingFeatureGuard.reset();
        }
    }

    @Unique
    private static void the_beyond$sweepDebris(BeyondEndChunkGenerator gen, WorldGenLevel level, ChunkAccess chunk,
            List<BeyondEndChunkGenerator.CarveMask> masks, BeyondStructureCarver.TerrainSnapshot snap) {
        try {
            int dropped = gen.the_beyond$dropCarveDebris(level, chunk, masks, snap);
            if (dropped <= 0) return;
            int total = BeyondGenDiagnostics.debrisDropped.addAndGet(dropped);
            if (!BeyondGenDiagnostics.loggedDebris) {
                BeyondGenDiagnostics.loggedDebris = true;
                com.thebeyond.TheBeyond.LOGGER.info(
                        "[Beyond] carve debris: first sweep dropped {} severed cells at chunk {} (running total {})",
                        dropped, chunk.getPos(), total);
            }
            if (total > 0 && total % 4000 < dropped) {
                com.thebeyond.TheBeyond.LOGGER.info("[Beyond] carve sweep totals: dropped={} warts={} pits={} attached={} knee={}",
                        total, BeyondGenDiagnostics.grainWarts.get(), BeyondGenDiagnostics.grainPits.get(),
                        BeyondGenDiagnostics.debrisAttached.get(), BeyondGenDiagnostics.kneeLaid.get());
            }
        } catch (Throwable ignored) { }
    }
}
