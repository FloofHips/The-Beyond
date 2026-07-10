package com.thebeyond.mixin;

import com.thebeyond.api.compat.PancakeScan;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrain;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
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
public abstract class JigsawStructureMixin {

    @Shadow @Final private Holder<StructureTemplatePool> startPool;
    @Shadow @Final private Optional<ResourceLocation> startJigsawName;
    @Shadow @Final private int maxDepth;
    @Shadow @Final private boolean useExpansionHack;
    @Shadow @Final private int maxDistanceFromCenter;
    @Shadow @Final private List<PoolAliasBinding> poolAliases;
    @Shadow @Final private DimensionPadding dimensionPadding;
    @Shadow @Final private LiquidSettings liquidSettings;
    @Shadow @Final private Optional<Heightmap.Types> projectStartToHeightmap;

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
            int centerX = chunkPos.getMinBlockX();
            int centerZ = chunkPos.getMinBlockZ();
            int dimMaxY = context.heightAccessor().getMaxBuildHeight() - 1;

            List<Integer> tops = the_beyond$pancakeTops(centerX, centerZ, dimMinY, dimMaxY);
            if (tops.isEmpty()) {
                cir.setReturnValue(Optional.empty());
                return;
            }

            long mix = ChunkPos.asLong(chunkPos.x, chunkPos.z) ^ context.seed();
            int chosenY = tops.get(new Random(mix).nextInt(tops.size()));
            BlockPos pos = new BlockPos(centerX, chosenY, centerZ);
            cir.setReturnValue(the_beyond$addPiecesAt(context, pos));
            return;
        }
    }

    private Optional<Structure.GenerationStub> the_beyond$addPiecesAt(
            Structure.GenerationContext context, BlockPos pos) {
        return JigsawPlacement.addPieces(
                context, this.startPool, this.startJigsawName, this.maxDepth,
                pos, this.useExpansionHack,
                Optional.empty(),
                this.maxDistanceFromCenter,
                PoolAliasLookup.create(this.poolAliases, pos, context.seed()),
                this.dimensionPadding,
                this.liquidSettings
        );
    }

    /** Auto-host a foreign jigsaw structure on Beyond's far-field: Class A (projects to a heightmap) is opted
     *  into carve + foundation, Class F (void floater) is left alone, Class B is re-anchored onto a pancake. */
    private void the_beyond$autoAnchorForeign(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!BeyondTerrainState.isActive()) return;
        // isActive() is global (not per-dimension); this instanceof is what confines the branches below to Beyond's End.
        if (!(context.chunkGenerator() instanceof BeyondEndChunkGenerator beg)) return;
        if (this.projectStartToHeightmap.isPresent()) {
            // ENCAPSULATE is exempt (it buries on purpose); other Class A adapters get carve + foundation since
            // vanilla leaves no carve and stacked islands would fill their interior with stone.
            Structure selfA = (Structure) (Object) this;
            if (selfA.terrainAdaptation() != TerrainAdjustment.ENCAPSULATE
                    && the_beyond$belongsToEndBiome(selfA, beg)) {
                ResourceLocation keyA = the_beyond$structureKey(context, selfA);
                if (keyA != null && BeyondForeignStructureProfiles.get(keyA) == null) {
                    BeyondForeignStructureProfiles.markAutoSeatedProjected(keyA);
                    ChunkPos cpA = context.chunkPos();
                    int centerX = cpA.getMinBlockX(), centerZ = cpA.getMinBlockZ();
                    boolean farField = (double) centerX * centerX + (double) centerZ * centerZ >= 650.0 * 650.0;
                    if (farField) {
                        beg.computeNoisesIfNotPresent(context.randomState());
                        int[] layer = PancakeScan.pickEndBiomeLayerInChunk(
                                beg, cpA.x, cpA.z, context.heightAccessor(), context.randomState(),
                                selfA.biomes(), PancakeScan.LAYER_MIN_HEADROOM);
                        if (layer != null) {
                            BeyondForeignStructureProfiles.markLayerDistributed(keyA, cpA.toLong());
                            the_beyond$logAuto(selfA, keyA, "A", "LAYER_DISTRIBUTED requestedY=" + layer[1] + " (surgical distributed carve)");
                            the_beyond$logDistributedY(keyA, cpA, layer[1]);
                            cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(layer[0], layer[1], layer[2])));
                            return;
                        }
                    }
                    the_beyond$logAuto(selfA, keyA, "A", "AUTO_SEAT_PROJECTED (no layer; topmost) adapt=" + selfA.terrainAdaptation());
                }
            }
            return;   // placement stays vanilla self-projected to the surface
        }

        ChunkPos chunkPos = context.chunkPos();
        int centerX = chunkPos.getMinBlockX();
        int centerZ = chunkPos.getMinBlockZ();
        // Inside radius 650: central island + inner void, no valid pancake terrain to re-anchor onto.
        if ((double) centerX * centerX + (double) centerZ * centerZ < 650.0 * 650.0) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        Structure self = (Structure) (Object) this;
        ResourceLocation key = the_beyond$structureKey(context, self);
        // Class F check must precede Class B: neither terraforms nor targets the surface step → deliberate void floater.
        if (self.terrainAdaptation() == TerrainAdjustment.NONE
                && self.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) {
            the_beyond$logAuto(self, key, "F", "FLOAT_PRESERVE");
            return;
        }

        beg.computeNoisesIfNotPresent(context.randomState());
        int[] spot = PancakeScan.pickEndBiomeSpotInChunk(
                beg, chunkPos.x, chunkPos.z, context.heightAccessor(), context.randomState(), self.biomes());
        if (spot == null) {
            the_beyond$logAuto(self, key, "B", "REJECT_no_pancake");
            cir.setReturnValue(Optional.empty());              // no fitting pancake in this cell → no structure
            return;
        }
        the_beyond$logAuto(self, key, "B", "REANCHOR_SEATED y=" + spot[1]);
        BeyondForeignStructureProfiles.markAutoReanchored(key);
        cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(spot[0], spot[1], spot[2])));
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

    private static void the_beyond$logAuto(Structure self, @org.jetbrains.annotations.Nullable ResourceLocation key, String cls, String decision) {
        String id = key == null ? "?" : key.toString();
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
