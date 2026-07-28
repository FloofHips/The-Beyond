package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.FloatingFeatureGuard;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

//temporarily removed from the mixins.json!

/** Arms {@link FeatureGuard} with the exact volume the island carve cleared around nearby carve-type
 *  structures, so decorations can't pierce them or float in the carved margin. Cleared in a finally. */
@Mixin(ChunkGenerator.class)
public abstract class FeatureGuardMixin {

    @WrapMethod(method = "applyBiomeDecoration")
    private void the_beyond$guardFeatures(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structureManager, Operation<Void> original) {
        FeatureGuard.clearVolume();
        try {
            if (level.getLevel().dimension() == Level.END
                    && ((Object) this) instanceof BeyondEndChunkGenerator gen) {
                // Chunks outside a large structure's ±8 reference window are skipped by vanilla placement,
                // leaving gaps (e.g. a station's far tower) — build those here from the pre-seeded cache.
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
                    }
                } catch (Throwable t) {
                    FeatureGuard.clearVolume();   // fail-soft: no guard, vanilla decoration wins
                }
            }
            original.call(level, chunk, structureManager);
            // Drop base-less parts of grounded features the guard severed above. Fail-soft.
            try { FloatingFeatureGuard.sweep(level); } catch (Throwable ignored) { }
        } finally {
            FeatureGuard.clearVolume();
            FloatingFeatureGuard.reset();
        }
    }
}
