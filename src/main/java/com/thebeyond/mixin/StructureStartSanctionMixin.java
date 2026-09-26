package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.api.worldgen.ForeignStructureWrite;
import com.thebeyond.api.worldgen.SanctionedWrite;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

/** Sanctions Beyond's own structure writes so the carve veto ({@link IslandCarveProtectionMixin}) lets them
 *  overwrite their own footprint; foreign structures stay unsanctioned so their template AIR can't carve islands. */
@Mixin(StructureStart.class)
public abstract class StructureStartSanctionMixin {

    @WrapMethod(method = "placeInChunk")
    private void the_beyond$sanctionOwnStructureWrites(
            WorldGenLevel level, StructureManager structureManager, ChunkGenerator chunkGenerator,
            RandomSource random, BoundingBox boundingBox, ChunkPos chunkPos,
            Operation<Void> original) {
        // Gate on the chunkgen instance, not a session flag, so this never fires in another mod's End.
        boolean beyondGen = chunkGenerator instanceof BeyondEndChunkGenerator;
        // Sanctioned across the whole End: the auroracite floor protection also runs in fallback Ends.
        ResourceLocation key = null;
        if (beyondGen || level.getLevel().dimension() == Level.END) {
            try {
                key = level.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE)
                        .getKey(((StructureStart) (Object) this).getStructure());
            } catch (Throwable ignored) {
                key = null;
            }
        }
        boolean own = key != null && "the_beyond".equals(key.getNamespace());
        // A seated landmark whose pancake a foreign carve later empties would float in the cavity, so it is skipped.
        if (beyondGen && own && the_beyond$isPancakeSeatedLandmark(key)
                && ((BeyondEndChunkGenerator) chunkGenerator)
                        .the_beyond$landmarkInForeignCavity(structureManager, (StructureStart) (Object) this, chunkPos)) {
            return;   // draws nothing this chunk
        }
        if (own) SanctionedWrite.enter();
        // Mark the structure-placement phase so the feature guard (which only vetoes feature writes) lets
        // this structure build inside its own bbox.
        if (beyondGen) FeatureGuard.enterStructure();
        boolean foreign = beyondGen && !own;
        ForeignStructureWrite.Scope displaced = null;
        if (foreign) {
            ForeignStructureWrite.enter();
            displaced = ForeignStructureWrite.openScope(key != null ? key.toString() : "<unknown>");
        }
        try {
            original.call(level, structureManager, chunkGenerator, random, boundingBox, chunkPos);
            // Inside the scope: outside it the feature guard vetoes the swap on every column the carve cleared.
            if (foreign) the_beyond$coverGroundDirt(level, boundingBox, key);
        } finally {
            if (foreign) {
                the_beyond$logCarveLedger();
                ForeignStructureWrite.closeScope(displaced);
                ForeignStructureWrite.exit();
            }
            if (beyondGen) FeatureGuard.exitStructure();
            if (own) SanctionedWrite.exit();
        }
    }

    /** Keyed per counter, so a first slice touching only terrain cannot hide whether the self-overwrite path fired. */
    private static void the_beyond$logCarveLedger() {
        ForeignStructureWrite.Scope scope = ForeignStructureWrite.currentScope();
        if (scope == null) return;
        if (scope.selfOverwrite > 0
                && BeyondGenDiagnostics.loggedCarveLedger.add(scope.structureId + ":self")) {
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[IslandCarveProtection] {}: self-overwrite allowed {} (first slice)",
                    scope.structureId, scope.selfOverwrite);
        }
        if (scope.featureVeto > 0
                && BeyondGenDiagnostics.loggedCarveLedger.add(scope.structureId + ":feature")) {
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[FeatureGuard] {}: {} of its OWN solid blocks refused while it was placing itself"
                    + " (first slice); a nonzero count here is the structure losing its own geometry",
                    scope.structureId, scope.featureVeto);
        }
        if (scope.terrainVeto > 0
                && BeyondGenDiagnostics.loggedCarveLedger.add(scope.structureId + ":veto")) {
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[IslandCarveProtection] {}: terrain veto {} (first slice)",
                    scope.structureId, scope.terrainVeto);
        }
    }

    private void the_beyond$coverGroundDirt(WorldGenLevel level, BoundingBox writeBox, ResourceLocation key) {
        StructureStart self = (StructureStart) (Object) this;
        StructureIntegrationProfile profile;
        try {
            profile = BeyondForeignStructureProfiles.resolve(self.getStructure(), key);
        } catch (Throwable ignored) {
            return;
        }
        if (profile == null || !profile.coverGroundDirt()) return;
        if (BeyondForeignStructureProfiles.isDirtCoverSuppressed(key)) return;   // authored garden/grass kept
        // Clamp to the intersection of this chunk's write region and the structure's own footprint.
        BoundingBox sb = self.getBoundingBox();
        int x0 = Math.max(writeBox.minX(), sb.minX()), x1 = Math.min(writeBox.maxX(), sb.maxX());
        int z0 = Math.max(writeBox.minZ(), sb.minZ()), z1 = Math.min(writeBox.maxZ(), sb.maxZ());
        int y0 = Math.max(writeBox.minY(), sb.minY()), y1 = Math.min(writeBox.maxY(), sb.maxY());
        if (x0 > x1 || z0 > z1 || y0 > y1) return;
        BlockState end = Blocks.END_STONE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int swapped = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = y0; y <= y1; y++) {
                    m.set(x, y, z);
                    if (the_beyond$isOverworldGround(level.getBlockState(m))) {
                        level.setBlock(m, end, 2);   // sync to clients, skip neighbour shape updates
                        swapped++;
                    }
                }
            }
        }
        if (key != null && swapped > 0
                && BeyondGenDiagnostics.loggedDirtCover.add(key.toString())) {
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[Beyond] dirt-cover {} swapped {} ground block(s) -> end_stone (first chunk slice)", key, swapped);
        }
    }

    /** True for landmarks rerouted onto a far-field pancake top (aberrant_remains / arch / bonfire).
     *  Central structures anchor to the floor/void instead, so they're excluded. */
    private static boolean the_beyond$isPancakeSeatedLandmark(ResourceLocation key) {
        if (key == null) return false;
        String p = key.getPath();
        return "aberrant_remains".equals(p) || "arch".equals(p) || "bonfire".equals(p);
    }

    private static boolean the_beyond$isOverworldGround(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.GRASS_BLOCK)
                || s.is(Blocks.PODZOL) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MUD) || s.is(Blocks.DIRT_PATH)
                || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(Blocks.FARMLAND);
    }
}
