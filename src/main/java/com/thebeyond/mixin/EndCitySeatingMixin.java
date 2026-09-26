package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Re-seats End Cities on a real pancake top, since vanilla's four-corner minimum plants them on a low shelf. */
@Mixin(EndCityStructure.class)
public abstract class EndCitySeatingMixin {

    private static final double FAR_FIELD_SQ = 650.0 * 650.0;

    @WrapMethod(method = "findGenerationPoint")
    private Optional<Structure.GenerationStub> the_beyond$seatOnPancakeTop(
            Structure.GenerationContext context,
            Operation<Optional<Structure.GenerationStub>> original) {

        if (!BeyondTerrainState.isActive()) return original.call(context);
        if (!(context.chunkGenerator() instanceof BeyondEndChunkGenerator beg)) return original.call(context);
        ChunkPos cp = context.chunkPos();
        double cx = cp.getMinBlockX(), cz = cp.getMinBlockZ();
        // Inside the far-field edge there is no pancake terrain to re-anchor onto.
        if (cx * cx + cz * cz < FAR_FIELD_SQ) return original.call(context);

        Structure self = (Structure) (Object) this;
        try {
            // Drawn before the scan so the rotation stays a function of the structure seed alone.
            Rotation rotation = Rotation.getRandom(context.random());
            beg.computeNoisesIfNotPresent(context.randomState());
            // Checks the whole footprint (radius 10, 50% coverage), four probes pass a narrow tip over the void.
            int[] spot = the_beyond$bestSeat(beg, cp, context, self, Integer.MAX_VALUE);
            if (spot == null) return original.call(context);

            // The shared picker buries the base course, but the End City must sit on the island.
            BlockPos pos = new BlockPos(spot[0], spot[1] + PancakeScan.LAYER_BURY_DEPTH, spot[2]);
            if (pos.getY() < 60) return Optional.empty();   // vanilla's own floor gate

            final int ceiling = context.heightAccessor().getMaxBuildHeight() - 1;
            List<StructurePiece> pieces = the_beyond$build(context, pos, rotation);
            int need = the_beyond$topOf(pieces) - pos.getY();

            // A ~134-block stack would be sliced at the ceiling, so the same city moves down instead of being rebuilt.
            int moved = 0;
            if (pos.getY() + need > ceiling) {
                int[] layer = the_beyond$bestSeat(beg, cp, context, self, ceiling - need);
                if (layer == null || layer[1] < 60) return the_beyond$logSkip(cp, pos, need, ceiling);
                moved = layer[1] + PancakeScan.LAYER_BURY_DEPTH - pos.getY();
                for (StructurePiece p : pieces) p.move(layer[0] - pos.getX(), moved, layer[2] - pos.getZ());
                pos = new BlockPos(layer[0], layer[1] + PancakeScan.LAYER_BURY_DEPTH, layer[2]);
            }

            if (BeyondGenDiagnostics.loggedEndCitySeat.add(cp.toLong())) {
                // top != y + need would mean the piece list was rebuilt rather than moved.
                com.thebeyond.TheBeyond.LOGGER.info(
                        "[Beyond] end_city seated y={} top={} need={} ceiling={} moved={} at {}",
                        pos.getY(), the_beyond$topOf(pieces), need, ceiling, moved, pos);
            }
            final List<StructurePiece> placed = pieces;
            return Optional.of(new Structure.GenerationStub(pos, builder -> placed.forEach(builder::addPiece)));
        } catch (Throwable t) {
            if (!BeyondGenDiagnostics.loggedEndCitySeatError) {
                BeyondGenDiagnostics.loggedEndCitySeatError = true;
                com.thebeyond.TheBeyond.LOGGER.warn(
                        "[Beyond] end_city re-seat failed, vanilla placement kept: {}", t.toString());
            }
            return original.call(context);
        }
    }

    private static int[] the_beyond$bestSeat(BeyondEndChunkGenerator beg, ChunkPos cp,
            Structure.GenerationContext context, Structure self, int maxSeatY) {
        double[][] tiers = PancakeScan.FOOTPRINT_COVERAGE_TIERS;
        for (int t = 0; t < tiers.length; t++) {
            int[] spot = PancakeScan.pickEndBiomeLayerInChunk(
                    beg, cp.x, cp.z, context.heightAccessor(), context.randomState(), self.biomes(),
                    PancakeScan.LAYER_MIN_HEADROOM, (int) tiers[t][0],
                    PancakeScan.FOOTPRINT_SUPPORT_STRIDE, tiers[t][1], maxSeatY, (int) tiers[t][2]);
            if (spot != null) {
                if (t > 0 && BeyondGenDiagnostics.loggedEndCityTier.add(cp.toLong())) {
                    com.thebeyond.TheBeyond.LOGGER.info(
                            "[Beyond] end_city seated at tier {} (radius {}, {}% coverage) at {}",
                            t, (int) tiers[t][0], Math.round(tiers[t][1] * 100), cp);
                }
                return spot;
            }
        }
        return null;
    }

    private static List<StructurePiece> the_beyond$build(
            Structure.GenerationContext context, BlockPos pos, Rotation rotation) {
        List<StructurePiece> pieces = new ArrayList<>();
        EndCityPieces.startHouseTower(context.structureTemplateManager(), pos, rotation, pieces, context.random());
        return pieces;
    }

    private static int the_beyond$topOf(List<StructurePiece> pieces) {
        int top = Integer.MIN_VALUE;
        for (StructurePiece p : pieces) {
            int m = p.getBoundingBox().maxY();
            if (m > top) top = m;
        }
        return top;
    }

    private static Optional<Structure.GenerationStub> the_beyond$logSkip(
            ChunkPos cp, BlockPos pos, int need, int ceiling) {
        if (BeyondGenDiagnostics.loggedEndCitySeat.add(cp.toLong())) {
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[Beyond] end_city skipped at {}: needs {} blocks of headroom, ceiling {}", pos, need, ceiling);
        }
        return Optional.empty();
    }
}
