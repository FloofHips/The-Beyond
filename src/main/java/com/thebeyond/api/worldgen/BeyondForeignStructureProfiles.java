package com.thebeyond.api.worldgen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Maps a foreign structure to its {@link StructureIntegrationProfile}. LEAVE-ALONE by default — hosting
 *  requires explicit registration during {@code BeyondCommonSetupEvent}. */
@ApiStatus.Experimental
public final class BeyondForeignStructureProfiles {

    private static final Map<ResourceLocation, StructureIntegrationProfile> PROFILES = new ConcurrentHashMap<>();

    private static final Set<ResourceLocation> AUTO_REANCHORED = ConcurrentHashMap.newKeySet();

    /** Foreign structures another addon's bespoke compat owns; the automatic policy must not double-handle them. */
    private static final Set<ResourceLocation> SUPPRESS_AUTO = ConcurrentHashMap.newKeySet();

    private static final Set<ResourceLocation> SUPPRESS_DIRT_COVER = ConcurrentHashMap.newKeySet();

    /** Crashed ships: interior-only carve — core columns clear, no foundation/base-beard platform; read by the carver as {@code carveOnly}. */
    private static final Set<ResourceLocation> EMBEDDED = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> BASE_PEDESTAL = ConcurrentHashMap.newKeySet();

    /** Unlike {@link #AUTO_REANCHORED}, Beyond never moves these, so rejection stays off. */
    private static final Set<ResourceLocation> AUTO_PROJECTED = ConcurrentHashMap.newKeySet();

    /** Keyed by {@code "structureId@startChunkLong"}. */
    private static final Set<String> LAYER_DISTRIBUTED = ConcurrentHashMap.newKeySet();

    private static final Set<String> SESSION_STARTS = ConcurrentHashMap.newKeySet();

    private static volatile boolean warming;

    /** Genuine void floaters instead opt in via explicit FLOATING registration — never auto-classified. */
    private static final StructureIntegrationProfile AUTO_SEATED =
            StructureIntegrationProfile.builder(StructureIntegrationProfile.Anchor.SEATED).coverGroundDirt(true).build();

    private static final StructureIntegrationProfile AUTO_SEATED_PROJECTED =
            StructureIntegrationProfile.builder(StructureIntegrationProfile.Anchor.SEATED)
                    .coverGroundDirt(true).rejectUnfit(false).build();

    private static final StructureIntegrationProfile AUTO_FLOAT_CARVED =
            StructureIntegrationProfile.builder(StructureIntegrationProfile.Anchor.FLOATING)
                    .rejectUnfit(false).build();
    private static final Set<ResourceLocation> AUTO_FLOATERS = ConcurrentHashMap.newKeySet();

    public static boolean isAutoFloatCarved(@Nullable ResourceLocation structureId) {
        return structureId != null && AUTO_FLOATERS.contains(structureId);
    }

    public static void markAutoFloatCarved(@Nullable ResourceLocation structureId) {
        if (structureId == null) return;
        if (AUTO_FLOATERS.add(structureId)) noteAutoHost("floaters", structureId);
        EMBEDDED.add(structureId);
    }

    public static void warming(boolean on) {
        warming = on;
    }

    private static void noteAutoHost(String set, ResourceLocation structureId) {
        if (!warming) {
            com.thebeyond.TheBeyond.LOGGER.info("[Beyond] auto-host {} += {} outside the start-up warm-up", set, structureId);
        }
    }

    public static void markSessionStart(@Nullable ResourceLocation structureId, long startChunkLong) {
        if (structureId != null) SESSION_STARTS.add(structureId + "@" + startChunkLong);
    }

    public static boolean isSessionStart(@Nullable ResourceLocation structureId, long startChunkLong) {
        return structureId != null && SESSION_STARTS.contains(structureId + "@" + startChunkLong);
    }

    private BeyondForeignStructureProfiles() {}

    public static void register(ResourceLocation structureId, StructureIntegrationProfile profile) {
        if (structureId == null || profile == null) return;
        PROFILES.put(structureId, profile);
    }

    @Nullable
    public static StructureIntegrationProfile get(@Nullable ResourceLocation structureId) {
        return structureId == null ? null : PROFILES.get(structureId);
    }

    public static void markAutoReanchored(@Nullable ResourceLocation structureId) {
        if (structureId != null && AUTO_REANCHORED.add(structureId)) noteAutoHost("reanchored", structureId);
    }

    public static void markAutoSeatedProjected(@Nullable ResourceLocation structureId) {
        if (structureId != null && AUTO_PROJECTED.add(structureId)) noteAutoHost("projected", structureId);
    }

    public static void markLayerDistributed(@Nullable ResourceLocation structureId, long startChunkLong) {
        if (structureId != null) LAYER_DISTRIBUTED.add(structureId + "@" + startChunkLong);
    }

    public static boolean isLayerDistributed(@Nullable ResourceLocation structureId, long startChunkLong) {
        return structureId != null && LAYER_DISTRIBUTED.contains(structureId + "@" + startChunkLong);
    }

    /** Always gets the organic dt3 skirt clearing, regardless of {@link #isLayerDistributed}. */
    public static boolean isAutoSeatedProjected(@Nullable ResourceLocation structureId) {
        return structureId != null && AUTO_PROJECTED.contains(structureId);
    }

    /** Call on server stop to prevent cross-world leakage. */
    public static void clearLayerDistributed() {
        LAYER_DISTRIBUTED.clear();
        SESSION_STARTS.clear();
    }

    public static void suppressAuto(@Nullable ResourceLocation structureId) {
        if (structureId != null) SUPPRESS_AUTO.add(structureId);
    }

    public static void suppressDirtCover(@Nullable ResourceLocation structureId) {
        if (structureId != null) SUPPRESS_DIRT_COVER.add(structureId);
    }

    public static boolean isDirtCoverSuppressed(@Nullable ResourceLocation structureId) {
        return structureId != null && SUPPRESS_DIRT_COVER.contains(structureId);
    }

    /** A hull the island is meant to close on. Its own volume is still cleared, nothing around it is. */
    private static final Set<ResourceLocation> HUG_TERRAIN = ConcurrentHashMap.newKeySet();

    public static void markHugTerrain(@Nullable ResourceLocation structureId) {
        if (structureId != null) HUG_TERRAIN.add(structureId);
    }

    public static boolean isHugTerrain(@Nullable ResourceLocation structureId) {
        return structureId != null && HUG_TERRAIN.contains(structureId);
    }

    public static void markEmbedded(@Nullable ResourceLocation structureId) {
        if (structureId != null) EMBEDDED.add(structureId);
    }

    public static boolean isEmbedded(@Nullable ResourceLocation structureId) {
        return structureId != null && EMBEDDED.contains(structureId);
    }

    /** A marker, not a profile field: register() would switch off the auto-host path that gives the structure its carve class. */
    public static void markBasePedestal(@Nullable ResourceLocation structureId) {
        if (structureId != null) BASE_PEDESTAL.add(structureId);
    }

    public static boolean isBasePedestal(@Nullable ResourceLocation structureId) {
        return structureId != null && BASE_PEDESTAL.contains(structureId);
    }

    public static boolean isBasePedestal(@Nullable ResourceLocation structureId,
            @Nullable net.minecraft.core.Registry<Structure> registry, @Nullable Structure structure) {
        if (isBasePedestal(structureId)) return true;
        if (registry == null || structure == null) return false;
        try {
            return registry.wrapAsHolder(structure).is(com.thebeyond.common.registry.BeyondTags.BASE_PEDESTAL);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean pedestalByTagOnly(@Nullable ResourceLocation structureId,
            @Nullable net.minecraft.core.Registry<Structure> registry, @Nullable Structure structure,
            @Nullable StructureIntegrationProfile profile) {
        if (isBasePedestal(structureId) || (profile != null && profile.basePedestal())) return false;
        return isBasePedestal(structureId, registry, structure);
    }

    /** Precedence: explicit registration, then auto-reanchored/auto-projected; everything else is null. */
    @Nullable
    public static StructureIntegrationProfile resolve(@Nullable Structure structure, @Nullable ResourceLocation id) {
        if (id == null) return null;
        StructureIntegrationProfile explicit = PROFILES.get(id);
        if (explicit != null) return explicit;
        String ns = id.getNamespace();
        if ("the_beyond".equals(ns) || "minecraft".equals(ns)) return null;
        if (SUPPRESS_AUTO.contains(id)) return null;
        if (AUTO_REANCHORED.contains(id)) return AUTO_SEATED;
        if (AUTO_PROJECTED.contains(id)) return AUTO_SEATED_PROJECTED;
        if (AUTO_FLOATERS.contains(id)) return AUTO_FLOAT_CARVED;
        // No structure-shape heuristic here: it could only ever mis-host a self-integrating ruin.
        return null;
    }

    public static boolean isEmpty() { return PROFILES.isEmpty(); }
}
