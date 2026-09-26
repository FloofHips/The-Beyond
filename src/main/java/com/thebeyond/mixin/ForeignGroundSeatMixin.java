package com.thebeyond.mixin;

import com.mojang.datafixers.util.Either;
import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import com.thebeyond.common.worldgen.ForeignFit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Structure.class)
public abstract class ForeignGroundSeatMixin {

    private static final double FAR_FIELD_SQ = 650.0 * 650.0;

    @Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void the_beyond$seatDeclaredGround(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Structure self = (Structure) (Object) this;
        if (self instanceof JigsawStructure || self instanceof EndCityStructure) return;
        // NONE is the author saying the terrain is not its business, so it keeps whatever height it chose.
        if (self.terrainAdaptation() == TerrainAdjustment.NONE) return;
        if (!BeyondTerrainState.isActive()) return;
        if (!(context.chunkGenerator() instanceof BeyondEndChunkGenerator beg)) return;
        Optional<Structure.GenerationStub> stub = cir.getReturnValue();
        if (stub == null || stub.isEmpty()) return;
        ChunkPos cp = context.chunkPos();
        double cx = cp.getMinBlockX(), cz = cp.getMinBlockZ();
        if (cx * cx + cz * cz < FAR_FIELD_SQ) return;

        ResourceLocation key = the_beyond$key(context, self);
        StructureIntegrationProfile declared = key == null ? null : BeyondForeignStructureProfiles.resolve(self, key);
        var registry = the_beyond$registry(context);
        boolean held = BeyondForeignStructureProfiles.isBasePedestal(key, registry, self)
                || (declared != null && declared.basePedestal());
        boolean mayGiveUp = held && BeyondForeignStructureProfiles.pedestalByTagOnly(key, registry, self, declared);
        try {
            // Already grounded by a host, so no second seat. A pedestal only settles onto the island under its floor.
            if (key != null && BeyondForeignStructureProfiles.isLayerDistributed(key, cp.toLong())) {
                if (held) {
                    beg.computeNoisesIfNotPresent(context.randomState());
                    Structure.GenerationStub seated = the_beyond$onGround(context, beg, self, key, stub.get(), mayGiveUp);
                    cir.setReturnValue(Optional.ofNullable(seated));
                    the_beyond$log(key, self, cp, seated == null ? "REJECT_island_too_small" : "HELD (on its host's layer)");
                    return;
                }
                the_beyond$log(key, self, cp, "SKIP (already seated by its host)");
                return;
            }
            beg.computeNoisesIfNotPresent(context.randomState());
            int[] spot = PancakeScan.pickEndBiomeSpotInChunk(
                    beg, cp.x, cp.z, context.heightAccessor(), context.randomState(), self.biomes());
            if (spot == null) {
                the_beyond$log(key, self, cp, "REJECT_no_pancake");
                cir.setReturnValue(Optional.empty());
                return;
            }
            BlockPos from = stub.get().position();
            int seatY = PancakeScan.seatOnOwnColumn(beg, from.getX(), from.getZ(), spot[1], 24,
                    context.heightAccessor(), context.randomState());
            int dy = seatY - from.getY();
            if (dy == 0) {
                if (key != null) BeyondForeignStructureProfiles.markAutoSeatedProjected(key);
                if (held) {
                    Structure.GenerationStub seated = the_beyond$onGround(context, beg, self, key, stub.get(), mayGiveUp);
                    cir.setReturnValue(Optional.ofNullable(seated));
                    if (seated == null) the_beyond$log(key, self, cp, "REJECT_island_too_small");
                }
                return;
            }
            StructurePiecesBuilder pieces = stub.get().getPiecesBuilder();
            if (pieces.isEmpty()) return;
            // The rejection gate never sees a stub this seat hands back, so a declared SEATED profile is tested here.
            StructureIntegrationProfile profile = key == null ? null : BeyondForeignStructureProfiles.get(key);
            if (profile != null && profile.rejectUnfit() && profile.anchor() == StructureIntegrationProfile.Anchor.SEATED
                    && ForeignFit.seatedUnfit(from.getX(), from.getZ(), seatY, pieces,
                            context.heightAccessor().getMinBuildHeight(), profile)) {
                the_beyond$log(key, self, cp, "REJECT_unfit y=" + seatY);
                cir.setReturnValue(Optional.empty());
                return;
            }
            pieces.offsetPiecesVertically(dy);
            if (key != null) BeyondForeignStructureProfiles.markAutoSeatedProjected(key);
            Structure.GenerationStub seated = new Structure.GenerationStub(from.offset(0, dy, 0), Either.right(pieces));
            Structure.GenerationStub chosen = held ? the_beyond$onGround(context, beg, self, key, seated, mayGiveUp) : seated;
            the_beyond$log(key, self, cp, chosen == null ? "REJECT_island_too_small" : "SEAT_ON_PANCAKE dy=" + dy + " y=" + seatY);
            cir.setReturnValue(Optional.ofNullable(chosen));
        } catch (Throwable t) {
            if (BeyondGenDiagnostics.loggedMaskKeys.add("ground-seat-fail")) {
                com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] generic ground seat skipped: {}", t.toString());
            }
        }
    }

    private static final int HOLD_DEPTH = 16, HOLD_GRIP = 8;
    private static final double HOLD_NEED = 0.5;
    private static final int OTHER_LAYER_TRIES = 8;

    @org.jetbrains.annotations.Nullable
    private static Structure.GenerationStub the_beyond$onGround(Structure.GenerationContext context,
            BeyondEndChunkGenerator beg, Structure self, @org.jetbrains.annotations.Nullable ResourceLocation key,
            Structure.GenerationStub stub, boolean mayGiveUp) {
        StructurePiecesBuilder pieces = stub.getPiecesBuilder();
        // Assembled once: a lazy stub would run its jigsaw again for every caller.
        Structure.GenerationStub assembled = new Structure.GenerationStub(stub.position(), Either.right(pieces));
        if (pieces.isEmpty()) return assembled;
        if (ForeignFit.sinks(pieces.build().pieces(), context.structureTemplateManager())) {
            the_beyond$logSeat(context, key, "its pieces reach under its start's floor, so it sinks into the island and gets no pedestal");
            return assembled;
        }
        StructurePiece start = pieces.build().pieces().get(0);
        StructureTemplateManager tm = context.structureTemplateManager();
        ForeignFit.Course floor = ForeignFit.lowestCourse(start, tm);
        String what;
        if (floor == null) {
            what = "its template is unknown, so it stays where it was placed";
        } else {
            what = the_beyond$settle(pieces, floor, start.getBoundingBox().minY(), floor.y());
            double held = mayGiveUp ? the_beyond$floorHeld(start, tm) : 1.0;
            if (held < ForeignFit.FIT_NEED) {
                int[] tried = new int[1];
                String other = the_beyond$otherLayer(context, beg, self, pieces, start, tm, tried);
                if (other == null) {
                    the_beyond$logSeat(context, key, "gives up its chunk: the island holds " + Math.round(held * 100)
                            + "% of its ground floor on its layer" + (tried[0] == 0 ? " and there is no other layer here"
                            : " and too little on the " + tried[0] + " other layer(s) tried here"));
                    return null;
                }
                what = other;
            }
        }
        the_beyond$logSeat(context, key, what);
        return assembled;
    }

    private static String the_beyond$settle(StructurePiecesBuilder pieces, ForeignFit.Course floor, int boxFloor, int sampledTop) {
        ForeignFit.Fit fit = ForeignFit.groundFit(floor, boxFloor, sampledTop);
        if (fit.seat() == Integer.MIN_VALUE) {
            return "under a quarter of its ground floor at " + floor.y() + " has island near it, so it stays there";
        }
        boolean aside = fit.dx() != 0 || fit.dz() != 0;
        if (!aside && fit.seat() == boxFloor) {
            return "the island under the ground floor seats its box floor at " + fit.seat() + ", where it already is";
        }
        if (the_beyond$tryMove(pieces, fit.dx(), fit.seat() - boxFloor, fit.dz())) {
            return "the island under the ground floor seats its box floor at " + fit.seat() + (aside ? " " + fit.dx() + ","
                    + fit.dz() + " sideways, where it holds " + Math.round(fit.held() * 100) + "% of the floor," : ",")
                    + " floor moved from " + floor.y();
        }
        if (aside) {
            int seat = ForeignFit.groundSeat(floor, boxFloor, sampledTop);
            if (seat != Integer.MIN_VALUE && the_beyond$tryMove(pieces, 0, seat - boxFloor, 0)) {
                return "the island does not hold its base " + fit.dx() + "," + fit.dz() + " sideways, so it seats its box floor"
                        + " at " + seat + " in place, floor moved from " + floor.y();
            }
        }
        return "the island under the ground floor seats its box floor at " + fit.seat() + ", floor kept at " + floor.y()
                + " because the island no longer holds it there";
    }

    private static boolean the_beyond$tryMove(StructurePiecesBuilder pieces, int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return true;
        ForeignFit.movePieces(pieces, dx, dy, dz);
        if (ForeignFit.baseHeldShare(pieces.build().pieces(), HOLD_DEPTH, HOLD_GRIP) >= HOLD_NEED) return true;
        ForeignFit.movePieces(pieces, -dx, -dy, -dz);
        return false;
    }

    private static double the_beyond$floorHeld(StructurePiece start, StructureTemplateManager tm) {
        ForeignFit.Course c = ForeignFit.lowestCourse(start, tm);
        return c == null ? 1.0 : ForeignFit.floorHeld(c);
    }

    @org.jetbrains.annotations.Nullable
    private static String the_beyond$otherLayer(Structure.GenerationContext context, BeyondEndChunkGenerator beg,
            Structure self, StructurePiecesBuilder pieces, StructurePiece start, StructureTemplateManager tm, int[] tried) {
        ChunkPos cp = context.chunkPos();
        // The piece moves its own box, so where it started is kept by value.
        BoundingBox b0 = start.getBoundingBox();
        int hx = b0.minX(), hy = b0.minY(), hz = b0.minZ();
        int[] tops = new int[OTHER_LAYER_TRIES];
        int tries = 0;
        for (int[] layer : PancakeScan.orderedEndBiomeLayersInChunk(beg, cp.x, cp.z, context.heightAccessor(),
                context.randomState(), self.biomes(), PancakeScan.LAYER_MIN_HEADROOM)) {
            if (tries >= OTHER_LAYER_TRIES) break;
            ForeignFit.Course floor = ForeignFit.lowestCourse(start, tm);
            if (floor == null) return null;
            int top = layer[1] + PancakeScan.LAYER_BURY_DEPTH;
            // The scan samples one layer at several columns, and the fit already read the one within reach of each top.
            if (Math.abs(top - hy) <= ForeignFit.GROUND_REACH) continue;
            boolean read = false;
            for (int k = 0; k < tries; k++) read |= Math.abs(tops[k] - top) <= ForeignFit.GROUND_REACH;
            if (read) continue;
            tops[tries++] = top;
            tried[0] = tries;
            String what = the_beyond$settle(pieces, floor, start.getBoundingBox().minY(), top);
            if (start.getBoundingBox().minY() != hy && the_beyond$floorHeld(start, tm) >= ForeignFit.FIT_NEED) {
                return what + " on the layer at " + top + " (try " + tries + ")";
            }
            BoundingBox now = start.getBoundingBox();
            ForeignFit.movePieces(pieces, hx - now.minX(), hy - now.minY(), hz - now.minZ());
        }
        return null;
    }

    private static void the_beyond$logSeat(Structure.GenerationContext context,
            @org.jetbrains.annotations.Nullable ResourceLocation key, String what) {
        ChunkPos cp = context.chunkPos();
        if (BeyondGenDiagnostics.loggedMaskKeys.add("ground-seat@" + key + "@" + cp.toLong())
                && BeyondGenDiagnostics.loggedMaskKeys.size() <= 4000) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] held seat {} at chunk [{},{}]: {}", key, cp.x, cp.z, what);
        }
    }

    @org.jetbrains.annotations.Nullable
    private static net.minecraft.core.Registry<Structure> the_beyond$registry(Structure.GenerationContext context) {
        try {
            return context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        } catch (Throwable t) {
            return null;
        }
    }

    @org.jetbrains.annotations.Nullable
    private static ResourceLocation the_beyond$key(Structure.GenerationContext context, Structure self) {
        try {
            return context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void the_beyond$log(@org.jetbrains.annotations.Nullable ResourceLocation key,
            Structure self, ChunkPos cp, String decision) {
        String id = key == null ? "?" : key.toString();
        BeyondGenDiagnostics.countDecision(id, "G", decision, cp.toLong());
        if (BeyondGenDiagnostics.loggedAutoProfile.add("generic:" + id)) {
            com.thebeyond.TheBeyond.LOGGER.debug(
                    "[Beyond] auto-structure {} class=G adapt={} type={} -> {}",
                    id, self.terrainAdaptation(), self.getClass().getSimpleName(), decision);
        }
    }
}
