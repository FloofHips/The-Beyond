package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Classifies every foreign type at start-up: the host keeps classes in memory only, and a restart would lose their carve. */
public final class AutoHostWarmUp {

    enum Kind { NONE, PROJECTED, FLOATER, REANCHORED, GENERIC }

    private AutoHostWarmUp() {}

    public static void run(MinecraftServer server) {
        if (!BeyondTerrainState.isActive()) return;
        BeyondEndChunkGenerator.the_beyond$templateManager = server.getStructureManager();
        LevelStem end = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.END);
        if (end == null) return;
        Set<Holder<Biome>> endBiomes = end.generator().getBiomeSource().possibleBiomes();
        Registry<Structure> reg = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int[] n = new int[Kind.values().length];
        for (var e : reg.entrySet()) {
            ResourceLocation key = e.getKey().location();
            Kind k = classify(e.getValue(), key, endBiomes);
            n[k.ordinal()]++;
            switch (k) {
                case PROJECTED, GENERIC -> BeyondForeignStructureProfiles.markAutoSeatedProjected(key);
                case FLOATER -> BeyondForeignStructureProfiles.markAutoFloatCarved(key);
                case REANCHORED -> BeyondForeignStructureProfiles.markAutoReanchored(key);
                default -> { }
            }
        }
        com.thebeyond.TheBeyond.LOGGER.info("[Beyond] restart warm-up: {} projected, {} generic, {} floaters, {} reanchored,"
                + " template manager bound", n[Kind.PROJECTED.ordinal()], n[Kind.GENERIC.ordinal()],
                n[Kind.FLOATER.ordinal()], n[Kind.REANCHORED.ordinal()]);
    }

    public static void reset() {
        BeyondEndChunkGenerator.the_beyond$templateManager = null;
    }

    /** The attempt path's classes (JigsawStructureMixin's auto-anchor and ForeignGroundSeatMixin) read off the definition. */
    static Kind classify(Structure s, ResourceLocation key, Set<Holder<Biome>> endBiomes) {
        String ns = key.getNamespace();
        if ("the_beyond".equals(ns) || "minecraft".equals(ns) || !inEnd(s, endBiomes)) return Kind.NONE;
        StructureIntegrationProfile prof = BeyondForeignStructureProfiles.get(key);
        boolean declaredFloat = prof != null && prof.anchor() == StructureIntegrationProfile.Anchor.FLOATING;
        if (s instanceof JigsawStructure) {
            if (!(s instanceof AutoHostShape h) || !h.the_beyond$foreignPool()) return Kind.NONE;
            if (h.the_beyond$projects()) {
                if (h.the_beyond$ownHeight() || declaredFloat) return prof == null ? Kind.FLOATER : Kind.NONE;
                if (s.terrainAdaptation() == TerrainAdjustment.ENCAPSULATE) return Kind.NONE;
                return prof == null ? Kind.PROJECTED : Kind.NONE;
            }
            if (declaredFloat) return Kind.NONE;
            if (s.terrainAdaptation() == TerrainAdjustment.NONE && s.step() != GenerationStep.Decoration.SURFACE_STRUCTURES) return Kind.NONE;
            return Kind.REANCHORED;
        }
        if (s instanceof EndCityStructure || s.terrainAdaptation() == TerrainAdjustment.NONE || overridesPlacement(s)) return Kind.NONE;
        return Kind.GENERIC;
    }

    /** Recomputes the layer mark a start from an earlier session lost: a far-field class A start was always seated on a layer. */
    public static boolean layerDistributed(@Nullable ResourceLocation key, StructureStart start, Set<Holder<Biome>> endBiomes) {
        long cp = start.getChunkPos().toLong();
        if (BeyondForeignStructureProfiles.isLayerDistributed(key, cp)) return true;
        if (key == null || BeyondForeignStructureProfiles.isSessionStart(key, cp)) return false;
        ChunkPos c = start.getChunkPos();
        boolean far = (double) c.getMinBlockX() * c.getMinBlockX() + (double) c.getMinBlockZ() * c.getMinBlockZ() >= 650.0 * 650.0;
        if (!far || !seatsOnLayer(start.getStructure(), key, endBiomes)) return false;
        if (BeyondGenDiagnostics.loggedRestartLayer.size() < 2000 && BeyondGenDiagnostics.loggedRestartLayer.add(key + "@" + cp)) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] restart layer {} start@[{},{}]: seated on a layer in an earlier session", key, c.x, c.z);
        }
        return true;
    }

    private static boolean seatsOnLayer(Structure s, ResourceLocation key, Set<Holder<Biome>> endBiomes) {
        if (!(s instanceof JigsawStructure) || !(s instanceof AutoHostShape h) || !h.the_beyond$foreignPool() || !h.the_beyond$projects()) {
            return false;
        }
        if (!inEnd(s, endBiomes)) return false;
        StructureIntegrationProfile prof = BeyondForeignStructureProfiles.get(key);
        if (h.the_beyond$ownHeight() || (prof != null && prof.anchor() == StructureIntegrationProfile.Anchor.FLOATING)) return false;
        return s.terrainAdaptation() != TerrainAdjustment.ENCAPSULATE;
    }

    private static boolean inEnd(Structure s, Set<Holder<Biome>> endBiomes) {
        for (Holder<Biome> b : s.biomes()) if (endBiomes.contains(b)) return true;
        return false;
    }

    /** ForeignGroundSeatMixin hooks Structure's own placement, which a subclass that overrides it never reaches. */
    private static boolean overridesPlacement(Structure s) {
        try {
            return s.getClass().getMethod("findValidGenerationPoint", Structure.GenerationContext.class).getDeclaringClass() != Structure.class;
        } catch (Throwable t) {
            return true;
        }
    }
}
