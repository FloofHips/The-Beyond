package com.thebeyond.mixin;

import com.mojang.datafixers.util.Either;
import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrain;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import com.thebeyond.common.worldgen.BeyondStructureArbiter;
import com.thebeyond.common.worldgen.ForeignFit;
import com.thebeyond.common.worldgen.StructureShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProviderType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Reroutes {@code the_beyond:*} jigsaw Y: fountain → dim floor, others → random pancake top.
 *  Soup mode cancels all except fountain to keep them off foreign terrain. */
@Mixin(JigsawStructure.class)
public abstract class JigsawStructureMixin implements com.thebeyond.common.worldgen.AutoHostShape {

    @Shadow @Final private Holder<StructureTemplatePool> startPool;
    @Shadow @Final private Optional<ResourceLocation> startJigsawName;
    @Shadow @Final private int maxDepth;
    @Shadow @Final private boolean useExpansionHack;
    @Shadow @Final private int maxDistanceFromCenter;
    @Shadow @Final private List<PoolAliasBinding> poolAliases;
    @Shadow @Final private DimensionPadding dimensionPadding;
    @Shadow @Final private LiquidSettings liquidSettings;
    @Shadow @Final private Optional<Heightmap.Types> projectStartToHeightmap;
    @Shadow @Final private net.minecraft.world.level.levelgen.heightproviders.HeightProvider startHeight;

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void the_beyond$rerouteBeyondStructures(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {

        ResourceKey<StructureTemplatePool> poolKey = this.startPool.unwrapKey().orElse(null);
        if (poolKey == null) return;
        ResourceLocation poolLoc = poolKey.location();
        if (!"the_beyond".equals(poolLoc.getNamespace())) {
            the_beyond$autoAnchorForeign(context, cir);
            return;
        }

        boolean beyondActive = BeyondTerrainState.isActive();
        int dimMinY = context.heightAccessor().getMinBuildHeight();
        String path = poolLoc.getPath();
        ChunkPos chunkPos = context.chunkPos();

        // Fountain anchors to the dim floor even outside beyondActive (soup mode included).
        if ("fountain/fountain".equals(path)) {
            BlockPos pos = new BlockPos(chunkPos.getMinBlockX(), dimMinY + 2, chunkPos.getMinBlockZ());
            cir.setReturnValue(the_beyond$addPiecesAt(context, pos));
            return;
        }

        if (!beyondActive) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        // streamPancakeTops needs noise primed; structure-start/locate can run before any generator does so — NPE otherwise.
        if (context.chunkGenerator() instanceof BeyondEndChunkGenerator beg)
            beg.computeNoisesIfNotPresent(context.randomState());

        // jump_platform_island is a separate pool with no branch here — falls through to vanilla placement.
        if ("misc/jump_platform".equals(path)) {
            int centerX = chunkPos.getMinBlockX();
            int centerZ = chunkPos.getMinBlockZ();
            int dimMaxY = context.heightAccessor().getMaxBuildHeight() - 1;

            List<Integer> floatYs = the_beyond$floatingPlatformYs(centerX, centerZ, dimMinY, dimMaxY);
            if (floatYs.isEmpty()) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            long mix = ChunkPos.asLong(chunkPos.x, chunkPos.z) ^ context.seed();
            int chosenY = floatYs.get(new Random(mix).nextInt(floatYs.size()));
            cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(centerX, chosenY, centerZ)));
            return;
        }

        if (path.startsWith("bridge/")) {
            int centerX = chunkPos.getMinBlockX();
            int centerZ = chunkPos.getMinBlockZ();
            int dimMaxY = context.heightAccessor().getMaxBuildHeight() - 1;

            int highest = Integer.MIN_VALUE;
            int[][] offsets = {{0,0},{96,0},{-96,0},{0,96},{0,-96},{64,64},{-64,-64},{64,-64},{-64,64}};
            for (int[] o : offsets) {
                List<Integer> ts = the_beyond$pancakeTops(centerX + o[0], centerZ + o[1], dimMinY, dimMaxY);
                if (!ts.isEmpty() && ts.get(0) > highest) highest = ts.get(0);
            }
            if (highest == Integer.MIN_VALUE) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            int chosenY = highest + 40;
            if (chosenY + 30 > dimMaxY) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(centerX, chosenY, centerZ)));
            return;
        }

        if (path.startsWith("bonfire/") || path.startsWith("aberrant_remains/") || "misc/arch".equals(path)) {
            try {
                cir.setReturnValue(the_beyond$placeLandmark(context, dimMinY));
            } catch (Throwable t) {
                the_beyond$failSoft(poolLoc, "landmark seat", t);
                cir.setReturnValue(Optional.empty());
            }
            return;
        }
    }

    private Optional<Structure.GenerationStub> the_beyond$addPiecesAt(
            Structure.GenerationContext context, BlockPos pos) {
        return the_beyond$addPiecesAt(context, pos, pos);
    }

    private Optional<Structure.GenerationStub> the_beyond$addPiecesAt(
            Structure.GenerationContext context, BlockPos pos, BlockPos aliasPos) {
        return JigsawPlacement.addPieces(
                context, this.startPool, this.startJigsawName, this.maxDepth,
                pos, this.useExpansionHack,
                Optional.empty(),
                this.maxDistanceFromCenter,
                PoolAliasLookup.create(this.poolAliases, aliasPos, context.seed()),
                this.dimensionPadding,
                this.liquidSettings
        );
    }

    /** A jigsaw draw with its own random, so draw 0 is vanilla's and the same draw at another height gives the same layout. */
    private Optional<Structure.GenerationStub> the_beyond$drawAt(Structure.GenerationContext context, BlockPos pos,
            BlockPos aliasPos, int draw) {
        net.minecraft.world.level.levelgen.WorldgenRandom r = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        r.setLargeFeatureSeed(context.seed() + draw * 0x9E3779B97F4A7C15L, context.chunkPos().x, context.chunkPos().z);
        Structure.GenerationContext own = new Structure.GenerationContext(context.registryAccess(), context.chunkGenerator(),
                context.biomeSource(), context.randomState(), context.structureTemplateManager(), r, context.seed(),
                context.chunkPos(), context.heightAccessor(), context.validBiome());
        return the_beyond$addPiecesAt(own, pos, aliasPos);
    }

    private Optional<Structure.GenerationStub> the_beyond$heldDraw(Structure.GenerationContext context,
            @org.jetbrains.annotations.Nullable ResourceLocation key, BlockPos pos, BlockPos aliasPos, int draw) {
        return the_beyond$drawAt(context, pos, aliasPos, draw)
                .map(s -> the_beyond$withoutFoundationRock(context, key, the_beyond$assembled(s)));
    }

    private static Structure.GenerationStub the_beyond$withoutFoundationRock(Structure.GenerationContext context,
            @org.jetbrains.annotations.Nullable ResourceLocation key, Structure.GenerationStub assembled) {
        StructurePiecesBuilder builder = assembled.getPiecesBuilder();
        if (builder.isEmpty()) return assembled;
        List<net.minecraft.world.level.levelgen.structure.StructurePiece> pieces = builder.build().pieces();
        if (pieces.size() < 2) return assembled;
        BoundingBox start = pieces.get(0).getBoundingBox();
        StructurePiecesBuilder kept = new StructurePiecesBuilder();
        int dropped = 0;
        for (int i = 0; i < pieces.size(); i++) {
            var p = pieces.get(i);
            if (i > 0 && ForeignFit.isFoundationRock(p, start, context.structureTemplateManager())) { dropped++; continue; }
            kept.addPiece(p);
        }
        if (dropped == 0) return assembled;
        if (BeyondGenDiagnostics.loggedMaskKeys.add("rock-platform@" + key)) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] {} leaves out {} piece(s) of island rock under its start:"
                    + " the island's own ground stands in for them", key, dropped);
        }
        return new Structure.GenerationStub(assembled.position(), Either.right(kept));
    }

    /** Moves the start onto the island under its whole ground floor, not one sample column. */
    private Structure.GenerationStub the_beyond$onGround(Structure.GenerationContext context,
            @org.jetbrains.annotations.Nullable ResourceLocation key, BlockPos at, BlockPos aliasPos, int draw,
            Structure.GenerationStub held, boolean sideways) {
        BoundingBox start = the_beyond$startBox(held);
        if (start == null) return held;
        ForeignFit.Course floor = ForeignFit.lowestCourse(held.getPiecesBuilder().build().pieces().get(0),
                context.structureTemplateManager());
        if (floor == null) return held;
        int sampled = at.getY() + PancakeScan.LAYER_BURY_DEPTH;
        ForeignFit.Fit fit = sideways ? ForeignFit.groundFit(floor, start.minY(), sampled)
                : new ForeignFit.Fit(0, 0, ForeignFit.groundSeat(floor, start.minY(), sampled), 0.0, 0.0);
        int seat = fit.seat();
        int lift = seat == Integer.MIN_VALUE ? 0 : seat - start.minY();
        if (lift == 0 && fit.dx() == 0 && fit.dz() == 0) return held;
        Optional<Structure.GenerationStub> again = the_beyond$heldDraw(context, key,
                new BlockPos(at.getX() + fit.dx(), at.getY() + lift, at.getZ() + fit.dz()), aliasPos, draw);
        Structure.GenerationStub chosen = held;
        String why = "the moved seat assembled nothing";
        if (again.isPresent()) {
            // Same column, so vanilla keeps judging the biome on the ground: the air over an island reads as void.
            Structure.GenerationStub a = new Structure.GenerationStub(held.position(), Either.right(again.get().getPiecesBuilder()));
            BoundingBox s2 = the_beyond$startBox(a);
            if (s2 == null || s2.minY() != start.minY() + lift) {
                why = "the moved draw landed its floor at " + (s2 == null ? "nothing" : s2.minY() - start.minY() + floor.y());
            } else if (s2.minX() != start.minX() + fit.dx() || s2.minZ() != start.minZ() + fit.dz()) {
                why = "the moved draw landed " + (s2.minX() - start.minX()) + "," + (s2.minZ() - start.minZ()) + " sideways";
            } else if (the_beyond$heldShare(a) < BASE_SUPPORT_NEED) why = "the island no longer holds it there";
            else chosen = a;
        }
        ChunkPos cp = context.chunkPos();
        boolean aside = fit.dx() != 0 || fit.dz() != 0;
        if (chosen == held && aside) {
            // A refused sideways move still leaves the plain seat.
            if (BeyondGenDiagnostics.loggedMaskKeys.add("ground-seat-aside@" + key + "@" + cp.toLong())
                    && BeyondGenDiagnostics.loggedMaskKeys.size() <= 4000) {
                com.thebeyond.TheBeyond.LOGGER.info("[Beyond] held seat {} at chunk [{},{}]: not moved {},{} sideways because {},"
                        + " seated in place", key, cp.x, cp.z, fit.dx(), fit.dz(), why);
            }
            return the_beyond$onGround(context, key, at, aliasPos, draw, held, false);
        }
        if (BeyondGenDiagnostics.loggedMaskKeys.add("ground-seat@" + key + "@" + cp.toLong())
                && BeyondGenDiagnostics.loggedMaskKeys.size() <= 4000) {
            String moved = !aside ? "," : " " + fit.dx() + "," + fit.dz() + " sideways, where it holds "
                    + Math.round(fit.held() * 100) + "% of the floor,";
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] held seat {} at chunk [{},{}]: the island under the ground floor"
                    + " seats its box floor at {}{} floor {}", key, cp.x, cp.z, seat, moved,
                    chosen != held ? "moved from " + floor.y() : "kept at " + floor.y() + " because " + why);
        }
        return chosen;
    }

    private static double the_beyond$floorHeld(Structure.GenerationContext context, Structure.GenerationStub assembled) {
        StructurePiecesBuilder builder = assembled.getPiecesBuilder();
        if (builder.isEmpty()) return 0.0;
        ForeignFit.Course floor = ForeignFit.lowestCourse(builder.build().pieces().get(0), context.structureTemplateManager());
        return floor == null ? 1.0 : ForeignFit.floorHeld(floor);
    }

    private Structure.GenerationStub the_beyond$seatHeld(Structure.GenerationContext context, Structure self,
            @org.jetbrains.annotations.Nullable ResourceLocation key, BlockPos at, int draw, Structure.GenerationStub stub,
            boolean mayGiveUp, double[] share, ResourceLocation[] rival) {
        Structure.GenerationStub grounded = the_beyond$onGround(context, key, at, at, draw, stub, true);
        rival[0] = the_beyond$yieldsTo(context, self, key, grounded);
        if (rival[0] != null && !the_beyond$sameColumns(grounded, stub)) {
            Structure.GenerationStub inPlace = the_beyond$onGround(context, key, at, at, draw, stub, false);
            if (the_beyond$yieldsTo(context, self, key, inPlace) == null) {
                ChunkPos cp = context.chunkPos();
                if (BeyondGenDiagnostics.loggedMaskKeys.add("held-in-place@" + key + "@" + cp.toLong())
                        && BeyondGenDiagnostics.loggedMaskKeys.size() <= 4000) {
                    com.thebeyond.TheBeyond.LOGGER.info("[Beyond] held seat {} at chunk [{},{}]: seated in place, {} would"
                            + " have dropped it moved sideways", key, cp.x, cp.z, rival[0]);
                }
                grounded = inPlace;
                rival[0] = null;
            }
        }
        share[0] = the_beyond$heldShare(grounded);
        share[1] = mayGiveUp ? the_beyond$floorHeld(context, grounded) : 1.0;
        return grounded;
    }

    private static void the_beyond$logSinks(@org.jetbrains.annotations.Nullable ResourceLocation key, ChunkPos cp,
            Structure.GenerationStub assembled, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager tm) {
        if (BeyondGenDiagnostics.loggedSinks.size() <= 4000 && BeyondGenDiagnostics.loggedSinks.add(key + "@" + cp.toLong())) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] held seat {} at chunk [{},{}]: its pieces reach {} under its start's"
                    + " floor, so it sinks into the island and gets no pedestal", key, cp.x, cp.z,
                    ForeignFit.sinkDepth(assembled.getPiecesBuilder().build().pieces(), tm));
        }
    }

    private static boolean the_beyond$sameColumns(Structure.GenerationStub a, Structure.GenerationStub b) {
        BoundingBox x = the_beyond$startBox(a), y = the_beyond$startBox(b);
        return x != null && y != null && x.minX() == y.minX() && x.minZ() == y.minZ();
    }

    private static String the_beyond$sideways(Structure.GenerationStub before, Structure.GenerationStub after) {
        BoundingBox a = the_beyond$startBox(before), b = the_beyond$startBox(after);
        if (a == null || b == null || (a.minX() == b.minX() && a.minZ() == b.minZ())) return "";
        return ", moved " + (b.minX() - a.minX()) + "," + (b.minZ() - a.minZ()) + " sideways";
    }

    private static final int BASE_SUPPORT_DEPTH = 16;
    private static final int BASE_SUPPORT_GRIP = 8;
    private static final double BASE_SUPPORT_NEED = 0.5;
    private static final int MAX_LAYER_TRIES = 8;
    private static final int CEILING_SLACK = 16;
    private static final int ROOF_REDRAWS = 4;
    private static final double START_BURIED_MAX = 0.25;
    private static final int LANDMARK_HEADROOM = 4;
    private static final int LANDMARK_SLOPE = 3;
    private static final int LANDMARK_DROP = 2;

    /** Auto-hosts a foreign jigsaw: class A (projected) on a layer, a floater at its height, class B (absolute) on a pancake. */
    private void the_beyond$autoAnchorForeign(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!BeyondTerrainState.isActive()) return;
        // isActive() is global (not per-dimension); this instanceof is what confines the branches below to Beyond's End.
        if (!(context.chunkGenerator() instanceof BeyondEndChunkGenerator beg)) return;
        Structure self = (Structure) (Object) this;
        ChunkPos cp = context.chunkPos();
        ResourceLocation key = the_beyond$structureKey(context, self);
        boolean farField = (double) cp.getMinBlockX() * cp.getMinBlockX()
                + (double) cp.getMinBlockZ() * cp.getMinBlockZ() >= 650.0 * 650.0;
        if (this.projectStartToHeightmap.isPresent()) {
            if (!the_beyond$belongsToEndBiome(self, beg)) return;
            var prof = key == null ? null : BeyondForeignStructureProfiles.get(key);
            boolean declaredFloat = prof != null
                    && prof.anchor() == com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor.FLOATING;
            if (the_beyond$isDeliberateFloater(self) || declaredFloat) {
                // Kept at its own height but still carved: a hull crossing a pancake would otherwise be swallowed by it.
                if (key != null && prof == null) BeyondForeignStructureProfiles.markAutoFloatCarved(key);
                try {
                    cir.setReturnValue(the_beyond$placeFloater(context, self, key, prof, declaredFloat));
                } catch (Throwable t) {
                    the_beyond$failSoft(key, "floater", t);
                }
                return;
            }
            // ENCAPSULATE is exempt: it buries on purpose.
            if (self.terrainAdaptation() == TerrainAdjustment.ENCAPSULATE || key == null) return;
            if (prof == null) BeyondForeignStructureProfiles.markAutoSeatedProjected(key);
            if (!farField) {
                the_beyond$logAuto(self, key, cp, "A", "AUTO_SEAT_PROJECTED (near field) adapt=" + self.terrainAdaptation());
                return;
            }
            try {
                cir.setReturnValue(the_beyond$seatOnLayer(context, beg, self, key));
            } catch (Throwable t) {
                the_beyond$failSoft(key, "layer seat", t);
            }
            return;
        }

        // Inside radius 650: central island + inner void, no valid pancake terrain to re-anchor onto.
        if (!farField) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        // A declared floater keeps its own height here too, where its surface step alone would seat it on an island.
        var declared = key == null ? null : BeyondForeignStructureProfiles.get(key);
        if (declared != null && declared.anchor() == com.thebeyond.api.worldgen.StructureIntegrationProfile.Anchor.FLOATING
                && the_beyond$belongsToEndBiome(self, beg)) {
            try {
                cir.setReturnValue(the_beyond$placeFloater(context, self, key, declared, true));
            } catch (Throwable t) {
                the_beyond$failSoft(key, "floater", t);
            }
            return;
        }
        // Class F check must precede Class B: neither terraforms nor targets the surface step → deliberate void floater.
        if (self.terrainAdaptation() == TerrainAdjustment.NONE
                && self.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
            the_beyond$logAuto(self, key, cp, "F", "FLOAT_PRESERVE");
            return;
        }

        beg.computeNoisesIfNotPresent(context.randomState());
        int[] spot = PancakeScan.pickEndBiomeSpotInChunk(
                beg, cp.x, cp.z, context.heightAccessor(), context.randomState(), self.biomes());
        if (spot == null) {
            the_beyond$logAuto(self, key, cp, "B", "REJECT_no_pancake");
            cir.setReturnValue(Optional.empty());              // no fitting pancake in this cell → no structure
            return;
        }
        the_beyond$logAuto(self, key, cp, "B", "REANCHOR_SEATED y=" + spot[1]);
        BeyondForeignStructureProfiles.markAutoReanchored(key);
        cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(spot[0], spot[1], spot[2])));
    }

    /** Far-field class A seat on a layer of its own biome, else the pancake spot, else none (vanilla's projection hangs it). */
    private Optional<Structure.GenerationStub> the_beyond$seatOnLayer(Structure.GenerationContext context,
            BeyondEndChunkGenerator beg, Structure self, ResourceLocation key) {
        ChunkPos cp = context.chunkPos();
        LevelHeightAccessor lh = context.heightAccessor();
        beg.computeNoisesIfNotPresent(context.randomState());
        var prof = BeyondForeignStructureProfiles.resolve(self, key);
        var registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        boolean held = BeyondForeignStructureProfiles.isBasePedestal(key, registry, self) || (prof != null && prof.basePedestal());
        boolean mayGiveUp = held && BeyondForeignStructureProfiles.pedestalByTagOnly(key, registry, self, prof);
        List<int[]> layers = PancakeScan.orderedEndBiomeLayersInChunk(beg, cp.x, cp.z, lh, context.randomState(),
                self.biomes(), PancakeScan.LAYER_MIN_HEADROOM);
        int tries = 0, failed = 0, lost = 0;
        ResourceLocation lostTo = null;
        int[] failedY = new int[MAX_LAYER_TRIES];
        double best = -1, bestFloor = -1;
        int roof = lh.getMaxBuildHeight() - this.dimensionPadding.top();
        // A held seat that lost its tops to the roof, returned as is when no other layer holds the structure.
        Optional<Structure.GenerationStub> roofed = Optional.empty();
        BlockPos roofedLayer = null;
        int roofedY = 0;
        for (int[] layer : layers) {
            if (tries >= (held ? MAX_LAYER_TRIES : 1)) break;
            // The same shelf drawn from a neighbouring sample rarely holds what it failed twice, so other layers get the tries.
            if (held && the_beyond$failedNear(failedY, failed, layer[1]) >= 2) continue;
            tries++;
            BlockPos at = new BlockPos(layer[0], layer[1], layer[2]);
            Optional<Structure.GenerationStub> stub = the_beyond$drawAt(context, at, at, held ? tries - 1 : 0)
                    .map(JigsawStructureMixin::the_beyond$assembled);
            boolean sinks = held && stub.isPresent()
                    && ForeignFit.sinks(stub.get().getPiecesBuilder().build().pieces(), context.structureTemplateManager());
            if (sinks) {
                the_beyond$logSinks(key, cp, stub.get(), context.structureTemplateManager());
                stub = Optional.of(the_beyond$unburied(context, self, key, layer, stub.get(), tries - 1));
                if (tries > 1 && !the_beyond$validBiome(context, stub.get())) { failedY[failed++] = layer[1]; continue; }
            } else if (held) {
                stub = stub.map(s -> the_beyond$withoutFoundationRock(context, key, s));
                if (stub.isEmpty()) { failedY[failed++] = layer[1]; continue; }
                // A retry lands elsewhere, and vanilla drops a start whose biome it rejects once this returns.
                if (tries > 1 && !the_beyond$validBiome(context, stub.get())) { failedY[failed++] = layer[1]; continue; }
                double[] share = new double[2];
                ResourceLocation[] rival = new ResourceLocation[1];
                Structure.GenerationStub grounded = the_beyond$seatHeld(context, self, key, at, tries - 1, stub.get(),
                        mayGiveUp, share, rival);
                best = Math.max(best, share[0]);
                bestFloor = Math.max(bestFloor, share[1]);
                if (share[0] < BASE_SUPPORT_NEED || share[1] < ForeignFit.FIT_NEED) { failedY[failed++] = layer[1]; continue; }
                if (rival[0] != null) { lost++; lostTo = rival[0]; failedY[failed++] = layer[1]; continue; }
                int seatY = layer[1] + the_beyond$floorShift(stub.get(), grounded);
                String aside = the_beyond$sideways(stub.get(), grounded);
                stub = Optional.of(grounded);
                if (the_beyond$reachesRoof(stub.get(), seatY, roof)) {
                    if (roofed.isEmpty()) { roofed = stub; roofedLayer = at; roofedY = seatY; }
                    failedY[failed++] = layer[1];
                    continue;
                }
                the_beyond$logHeld(key, cp, "seated y=" + seatY + aside + " on try " + tries + " of " + layers.size()
                        + " layers, island holds " + Math.round(share[0] * 100) + "% of the base"
                        + (mayGiveUp ? " and " + Math.round(share[1] * 100) + "% of the ground floor" : "") + ", top "
                        + the_beyond$top(stub.get()) + " under roof " + roof + the_beyond$lostNote(lost, lostTo));
            } else if (stub.isPresent()) {
                stub = Optional.of(the_beyond$unburied(context, self, key, layer, the_beyond$assembled(stub.get()), 0));
            }
            BeyondForeignStructureProfiles.markLayerDistributed(key, cp.toLong());
            the_beyond$logAuto(self, key, cp, "A", (tries == 1 ? "LAYER_DISTRIBUTED" : "LAYER_HELD_ON_RETRY")
                    + " requestedY=" + layer[1] + " try=" + tries + " of " + layers.size());
            the_beyond$logDistributedY(key, cp, layer[1]);
            return stub;
        }
        if (roofed.isPresent()) {
            // No other layer fits, so redraw on the same seat and keep the first layout that fits under the roof.
            int redrawn = 0;
            for (int r = 1; r <= ROOF_REDRAWS && redrawn == 0; r++) {
                int draw = MAX_LAYER_TRIES + r;
                // Drawn on the layer so the biome is judged on the ground, a redraw may put its centre over the void.
                Optional<Structure.GenerationStub> again = the_beyond$heldDraw(context, key, roofedLayer, roofedLayer, draw);
                if (again.isEmpty() || !the_beyond$validBiome(context, again.get())) continue;
                double[] share = new double[2];
                ResourceLocation[] rival = new ResourceLocation[1];
                Structure.GenerationStub a = the_beyond$seatHeld(context, self, key, roofedLayer, draw, again.get(),
                        mayGiveUp, share, rival);
                if (share[0] < BASE_SUPPORT_NEED || share[1] < ForeignFit.FIT_NEED) continue;
                if (the_beyond$reachesRoof(a, roofedLayer.getY() + the_beyond$floorShift(again.get(), a), roof)) continue;
                if (rival[0] != null) { lost++; lostTo = rival[0]; continue; }
                roofed = Optional.of(a);
                roofedY = roofedLayer.getY() + the_beyond$floorShift(again.get(), a);
                redrawn = r;
            }
            BeyondForeignStructureProfiles.markLayerDistributed(key, cp.toLong());
            the_beyond$logAuto(self, key, cp, "A", (redrawn > 0 ? "LAYER_REDRAWN_UNDER_ROOF" : "LAYER_KEPT_AT_ROOF")
                    + " requestedY=" + roofedY + " tries=" + tries);
            the_beyond$logHeld(key, cp, (redrawn > 0
                    ? "redrawn at y=" + roofedY + " on draw " + redrawn + ", top " + the_beyond$top(roofed.get()) + " under roof " + roof
                    : "kept at y=" + roofedY + ": every layer that holds it reaches the roof " + roof + ", and so did "
                            + ROOF_REDRAWS + " redraws") + the_beyond$lostNote(lost, lostTo));
            the_beyond$logDistributedY(key, cp, roofedY);
            return roofed;
        }
        int[] spot = PancakeScan.pickEndBiomeSpotInChunk(beg, cp.x, cp.z, lh, context.randomState(), self.biomes());
        if (spot != null && (layers.isEmpty() || held)) {
            int seatY = PancakeScan.seatOnOwnColumn(beg, spot[0], spot[2], spot[1], 24, lh, context.randomState());
            BlockPos at = new BlockPos(spot[0], seatY, spot[2]);
            int draw = MAX_LAYER_TRIES + ROOF_REDRAWS + 1;
            Optional<Structure.GenerationStub> stub = held ? the_beyond$heldDraw(context, key, at, at, draw)
                    : the_beyond$drawAt(context, at, at, 0);
            if (held && !stub.isEmpty()) {
                double[] share = new double[2];
                ResourceLocation[] rival = new ResourceLocation[1];
                Structure.GenerationStub grounded = the_beyond$seatHeld(context, self, key, at, draw, stub.get(),
                        mayGiveUp, share, rival);
                best = Math.max(best, share[0]);
                bestFloor = Math.max(bestFloor, share[1]);
                if (share[0] < BASE_SUPPORT_NEED || share[1] < ForeignFit.FIT_NEED) stub = Optional.empty();
                else {
                    String aside = the_beyond$sideways(stub.get(), grounded);
                    stub = Optional.of(grounded);
                    the_beyond$logHeld(key, cp, "seated on the pancake spot y=" + seatY + aside + " after " + tries
                            + " layer tries, island holds " + Math.round(share[0] * 100) + "% of the base");
                }
            }
            if (stub.isPresent() || !held) {
                // Re-anchored like the layer seat, so its footing and lip are not suppressed as a topmost projection.
                BeyondForeignStructureProfiles.markLayerDistributed(key, cp.toLong());
                the_beyond$logAuto(self, key, cp, "A", "SEAT_ON_PANCAKE y=" + seatY);
                return stub;
            }
        }
        if (tries > 0) {
            boolean small = mayGiveUp && best >= BASE_SUPPORT_NEED && bestFloor < ForeignFit.FIT_NEED;
            the_beyond$logAuto(self, key, cp, "A", (small ? "REJECT_island_too_small" : "REJECT_base_unsupported")
                    + " tries=" + tries + " best=" + Math.round(best * 100) + "%");
            if (held) the_beyond$logHeld(key, cp, "rejected after " + tries + " of " + layers.size()
                    + " layers, best hold " + Math.round(best * 100) + "%"
                    + (mayGiveUp ? ", best ground floor " + Math.round(bestFloor * 100) + "%" : ""));
        } else {
            the_beyond$logAuto(self, key, cp, "A", "REJECT_no_pancake adapt=" + self.terrainAdaptation());
        }
        return Optional.empty();
    }

    /** Assembled once and kept, as calling getPiecesBuilder again would build a different structure from the one measured. */
    private static Structure.GenerationStub the_beyond$assembled(Structure.GenerationStub stub) {
        return new Structure.GenerationStub(stub.position(), Either.right(stub.getPiecesBuilder()));
    }

    private static int the_beyond$floorShift(Structure.GenerationStub before, Structure.GenerationStub after) {
        BoundingBox a = the_beyond$startBox(before), b = the_beyond$startBox(after);
        return a == null || b == null ? 0 : b.minY() - a.minY();
    }

    private static double the_beyond$heldShare(Structure.GenerationStub assembled) {
        return ForeignFit.baseHeldShare(assembled.getPiecesBuilder().build().pieces(), BASE_SUPPORT_DEPTH, BASE_SUPPORT_GRIP);
    }

    @org.jetbrains.annotations.Nullable
    private static BoundingBox the_beyond$startBox(Structure.GenerationStub assembled) {
        StructurePiecesBuilder builder = assembled.getPiecesBuilder();
        if (builder.isEmpty() || ForeignFit.featureOnly(builder)) return null;
        return builder.build().pieces().get(0).getBoundingBox();
    }

    private static int the_beyond$top(Structure.GenerationStub assembled) {
        StructurePiecesBuilder builder = assembled.getPiecesBuilder();
        return builder.isEmpty() ? Integer.MIN_VALUE : builder.getBoundingBox().maxY();
    }

    private static boolean the_beyond$validBiome(Structure.GenerationContext context, Structure.GenerationStub stub) {
        BlockPos p = stub.position();
        return context.validBiome().test(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(p.getX()),
                QuartPos.fromBlock(p.getY()), QuartPos.fromBlock(p.getZ()), context.randomState().sampler()));
    }

    /** A start buried by the one-column layer seat is assembled again on the median surface of its own footprint. */
    private Structure.GenerationStub the_beyond$unburied(Structure.GenerationContext context, Structure self,
            @org.jetbrains.annotations.Nullable ResourceLocation key, int[] layer, Structure.GenerationStub first, int draw) {
        BoundingBox start = the_beyond$startBox(first);
        if (start == null) return first;
        double buried = ForeignFit.startBuriedShare(start);
        if (buried <= START_BURIED_MAX) return first;
        int surface = ForeignFit.footprintSurface(start);
        int raised = surface == Integer.MIN_VALUE ? Integer.MIN_VALUE : surface - PancakeScan.LAYER_BURY_DEPTH;
        Structure.GenerationStub chosen = first;
        String why = raised > layer[1] ? "the raised seat assembled nothing" : "no surface above the seat";
        if (raised > layer[1]) {
            Optional<Structure.GenerationStub> again = the_beyond$drawAt(context, new BlockPos(layer[0], raised, layer[2]),
                    new BlockPos(layer[0], layer[1], layer[2]), draw);
            if (again.isPresent()) {
                // Same column, so vanilla keeps judging the biome on the ground: the air over an island reads as void.
                Structure.GenerationStub a = new Structure.GenerationStub(first.position(),
                        Either.right(again.get().getPiecesBuilder()));
                BoundingBox s2 = the_beyond$startBox(a);
                double still = s2 == null ? 1.0 : ForeignFit.startBuriedShare(s2);
                if (still <= START_BURIED_MAX) chosen = a;
                else why = "the raised seat is still " + Math.round(still * 100) + "% covered";
            }
        }
        ChunkPos cp = context.chunkPos();
        the_beyond$logAuto(self, key, cp, "A", (chosen != first ? "START_RAISED" : "START_BURIED_KEPT") + " y=" + layer[1]);
        if (BeyondGenDiagnostics.loggedMaskKeys.add("buried-start@" + key + "@" + cp.toLong())
                && BeyondGenDiagnostics.loggedMaskKeys.size() <= 4000) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] buried start {} at chunk [{},{}]: {}% under the island at y={},"
                    + " footprint surface {}, {}", key, cp.x, cp.z, Math.round(buried * 100), layer[1], surface,
                    chosen != first ? "raised to y=" + raised : "kept: " + why);
        }
        return chosen;
    }

    /** The jigsaw drops pieces past the roof, so a clamped top in the last courses under it shows pieces went missing. */
    private boolean the_beyond$reachesRoof(Structure.GenerationStub assembled, int seatY, int roof) {
        if (seatY + this.maxDistanceFromCenter + 1 <= roof) return false;
        return the_beyond$top(assembled) >= roof - CEILING_SLACK;
    }

    @org.jetbrains.annotations.Nullable
    private static ResourceLocation the_beyond$yieldsTo(Structure.GenerationContext context, Structure self,
            @org.jetbrains.annotations.Nullable ResourceLocation key, Structure.GenerationStub assembled) {
        StructurePiecesBuilder builder = assembled.getPiecesBuilder();
        if (builder.isEmpty()) return null;
        return BeyondStructureArbiter.wouldYieldTo(self, key, builder.build().pieces(),
                context.registryAccess().registryOrThrow(Registries.STRUCTURE));
    }

    private static String the_beyond$lostNote(int lost, @org.jetbrains.annotations.Nullable ResourceLocation lostTo) {
        return lost == 0 ? "" : ", " + lost + " layout(s) passed over because " + lostTo + " would have dropped them";
    }

    private static int the_beyond$failedNear(int[] failedY, int failed, int y) {
        int n = 0;
        for (int i = 0; i < failed; i++) if (Math.abs(failedY[i] - y) <= 2) n++;
        return n;
    }

    private static void the_beyond$logHeld(@org.jetbrains.annotations.Nullable ResourceLocation key, ChunkPos cp, String what) {
        if (BeyondGenDiagnostics.loggedMaskKeys.add("held-seat@" + key + "@" + cp.toLong())
                && BeyondGenDiagnostics.loggedMaskKeys.size() <= 4000) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] held seat {} at chunk [{},{}]: {}", key, cp.x, cp.z, what);
        }
    }

    private Optional<Structure.GenerationStub> the_beyond$placeFloater(Structure.GenerationContext context, Structure self,
            @org.jetbrains.annotations.Nullable ResourceLocation key,
            @org.jetbrains.annotations.Nullable com.thebeyond.api.worldgen.StructureIntegrationProfile prof, boolean anchorBiome) {
        ChunkPos cp = context.chunkPos();
        LevelHeightAccessor lh = context.heightAccessor();
        // Exactly JigsawStructure.findGenerationPoint: the start height is drawn first, then the projected assembly.
        int offset = this.startHeight.sample(context.random(), new WorldGenerationContext(context.chunkGenerator(), lh));
        BlockPos from = new BlockPos(cp.getMinBlockX(), offset, cp.getMinBlockZ());
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(
                context, this.startPool, this.startJigsawName, this.maxDepth, from, this.useExpansionHack,
                this.projectStartToHeightmap, this.maxDistanceFromCenter,
                PoolAliasLookup.create(this.poolAliases, from, context.seed()), this.dimensionPadding, this.liquidSettings);
        if (stub.isEmpty()) return stub;
        StructurePiecesBuilder pieces = stub.get().getPiecesBuilder();
        BoundingBox bb = pieces.getBoundingBox();
        // A feature piece's one-block box says nothing of what the feature builds, so its profile's envelope stands in.
        int rise = ForeignFit.featureOnly(pieces) && prof != null ? prof.floatEnvelope() : bb.maxY() - bb.minY();
        int over = bb.minY() + rise - (lh.getMaxBuildHeight() - 1);
        int dy = over > 0 ? -over : 0;
        if (bb.minY() + dy < lh.getMinBuildHeight()) {
            the_beyond$logAuto(self, key, cp, "F", "REJECT_taller_than_world");
            return Optional.empty();
        }
        if (dy != 0) pieces.offsetPiecesVertically(dy);
        BlockPos at = stub.get().position().offset(0, dy, 0);
        if (anchorBiome) at = the_beyond$biomeAnchor(context, at);
        the_beyond$logAuto(self, key, cp, "F", "FLOAT_CARVED floor=" + (bb.minY() + dy) + " lowered=" + (-dy) + " biomeAt=" + at.getY());
        return Optional.of(new Structure.GenerationStub(at, Either.right(pieces)));
    }

    /** Ground below a hovering stub whose biome fails in the air, where a 2D End would have read it. */
    private static BlockPos the_beyond$biomeAnchor(Structure.GenerationContext context, BlockPos hover) {
        var sampler = context.randomState().sampler();
        var source = context.biomeSource();
        if (context.validBiome().test(source.getNoiseBiome(QuartPos.fromBlock(hover.getX()),
                QuartPos.fromBlock(hover.getY()), QuartPos.fromBlock(hover.getZ()), sampler))) return hover;
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        BeyondEndChunkGenerator.initColumnScratch(hover.getX(), hover.getZ(),
                (float) Math.sqrt((double) hover.getX() * hover.getX() + (double) hover.getZ() * hover.getZ()), scr);
        for (int y = Math.min(hover.getY(), context.heightAccessor().getMaxBuildHeight() - 1);
                y > context.heightAccessor().getMinBuildHeight(); y--) {
            if (!BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) continue;
            BlockPos ground = new BlockPos(hover.getX(), y, hover.getZ());
            return context.validBiome().test(source.getNoiseBiome(QuartPos.fromBlock(ground.getX()),
                    QuartPos.fromBlock(y), QuartPos.fromBlock(ground.getZ()), sampler)) ? ground : hover;
        }
        return hover;
    }

    private Optional<Structure.GenerationStub> the_beyond$placeLandmark(Structure.GenerationContext context, int dimMinY) {
        Structure self = (Structure) (Object) this;
        ChunkPos cp = context.chunkPos();
        ResourceLocation key = the_beyond$structureKey(context, self);
        int x = cp.getMinBlockX(), z = cp.getMinBlockZ();
        int need = StructureShape.startExtent(this.startPool, context.structureTemplateManager())[1] + LANDMARK_HEADROOM;
        List<Integer> tops = new ArrayList<>();
        for (int t : the_beyond$pancakeTops(x, z, dimMinY, context.heightAccessor().getMaxBuildHeight() - 1)) {
            if (the_beyond$clearAbove(x, z, t, need)) tops.add(t);
        }
        if (tops.isEmpty()) {
            the_beyond$logAuto(self, key, cp, "L", "REJECT_no_headroom");
            return Optional.empty();
        }
        java.util.Collections.shuffle(tops, new Random(ChunkPos.asLong(cp.x, cp.z) ^ context.seed()));
        String unfit = null;
        for (int chosenY : tops) {
            Optional<Structure.GenerationStub> stub = the_beyond$addPiecesAt(context, new BlockPos(x, chosenY, z));
            if (stub.isEmpty()) continue;
            StructurePiecesBuilder pieces = stub.get().getPiecesBuilder();
            unfit = the_beyond$landmarkUnfit(pieces.getBoundingBox());
            if (unfit == null) {
                the_beyond$logAuto(self, key, cp, "L", "PLACED y=" + chosenY + " of " + tops.size());
                return Optional.of(new Structure.GenerationStub(stub.get().position(), Either.right(pieces)));
            }
        }
        the_beyond$logAuto(self, key, cp, "L", "REJECT_" + (unfit == null ? "no_pieces" : unfit));
        return Optional.empty();
    }

    private static boolean the_beyond$clearAbove(int x, int z, int from, int need) {
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scr);
        for (int y = from; y < from + need; y++) {
            if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) return false;
        }
        return true;
    }

    @org.jetbrains.annotations.Nullable
    private static String the_beyond$landmarkUnfit(BoundingBox bb) {
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int total = 0, ground = 0, roofed = 0;
        for (int x = bb.minX(); x <= bb.maxX(); x += 2) {
            for (int z = bb.minZ(); z <= bb.maxZ(); z += 2) {
                total++;
                BeyondEndChunkGenerator.initColumnScratch(x, z, (float) Math.sqrt((double) x * x + (double) z * z), scr);
                for (int y = bb.minY(); y >= bb.minY() - LANDMARK_DROP; y--) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) { ground++; break; }
                }
                for (int y = bb.minY() + LANDMARK_SLOPE; y <= bb.maxY() + LANDMARK_HEADROOM; y++) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) { roofed++; break; }
                }
            }
        }
        if (2 * ground < total) return "no_ground";
        if (8 * roofed > total) return "roofed";
        return null;
    }

    @Override
    public boolean the_beyond$foreignPool() {
        ResourceKey<StructureTemplatePool> poolKey = this.startPool.unwrapKey().orElse(null);
        return poolKey != null && !"the_beyond".equals(poolKey.location().getNamespace());
    }

    @Override
    public boolean the_beyond$projects() {
        return this.projectStartToHeightmap.isPresent();
    }

    @Override
    public boolean the_beyond$ownHeight() {
        return the_beyond$isDeliberateFloater((Structure) (Object) this);
    }

    /** Declared hanging: no terrain adaptation and a start height drawn from a range, not the projected surface. */
    private boolean the_beyond$isDeliberateFloater(Structure self) {
        return self.terrainAdaptation() == TerrainAdjustment.NONE
                && this.startHeight.getType() != HeightProviderType.CONSTANT;
    }

    private static void the_beyond$failSoft(@org.jetbrains.annotations.Nullable ResourceLocation key, String what, Throwable t) {
        if (BeyondGenDiagnostics.loggedMaskKeys.add("auto-anchor-fail:" + what)) {
            com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] {} for {} failed, placement left to vanilla or skipped: {}", what, key, t.toString());
        }
    }

    /** Guards against a mixed structure set running a non-End member here before vanilla's set-level filter rejects it. */
    private static boolean the_beyond$belongsToEndBiome(Structure self, BeyondEndChunkGenerator beg) {
        var possible = beg.getBiomeSource().possibleBiomes();
        for (var biome : self.biomes()) {
            if (possible.contains(biome)) return true;
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private static ResourceLocation the_beyond$structureKey(Structure.GenerationContext context, Structure self) {
        try {
            return context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(self);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void the_beyond$logAuto(Structure self, @org.jetbrains.annotations.Nullable ResourceLocation key, ChunkPos cp,
            String cls, String decision) {
        String id = key == null ? "?" : key.toString();
        BeyondGenDiagnostics.countDecision(id, cls, decision, cp.toLong());
        if (BeyondGenDiagnostics.loggedAutoProfile.add(id)) {
            com.thebeyond.TheBeyond.LOGGER.debug(
                    "[Beyond] auto-structure {} class={} adapt={} step={} -> {}",
                    id, cls, self.terrainAdaptation(), self.step(), decision);
        }
    }

    private static void the_beyond$logDistributedY(ResourceLocation key, ChunkPos cp, int requestedY) {
        String id = key == null ? "?" : key.toString();
        if (BeyondGenDiagnostics.loggedDistributedY.add(id + "@" + cp.x + "," + cp.z)
                && BeyondGenDiagnostics.loggedDistributedY.size() <= 40) {
            com.thebeyond.TheBeyond.LOGGER.debug(
                    "[Beyond] layer-distribute {} chunk=[{},{}] requestedY={}", id, cp.x, cp.z, requestedY);
        }
    }

    private static List<Integer> the_beyond$floatingPlatformYs(int x, int z, int minY, int maxY) {
        List<Integer> tops = the_beyond$pancakeTops(x, z, minY, maxY);
        if (tops.isEmpty()) return tops;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < tops.size(); i++) {
            int currTop = tops.get(i);
            int upperLimit = (i > 0) ? tops.get(i - 1) : Math.min(maxY, currTop + 30);
            int gap = upperLimit - currTop;
            if (gap >= 8) result.add(currTop + gap / 2);
        }
        return result;
    }

    private static List<Integer> the_beyond$pancakeTops(int x, int z, int minY, int maxY) {
        if ((double) x * x + (double) z * z < 650.0 * 650.0) return new ArrayList<>();
        List<Integer> tops = new ArrayList<>();
        try {
            BeyondTerrain.streamPancakeTops(x, z, minY, maxY).forEach(tops::add);
        } catch (Throwable t) {
            return new ArrayList<>();   // unprimed/edge state → empty (caller falls back), never crash
        }
        return tops;
    }
}
