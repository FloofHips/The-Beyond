package com.thebeyond.common.worldgen.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.mixin.NoiseGeneratorSettingsAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges Beyond's surface rules into the End's active noise settings.
 *
 * Conditional thresholds:
 * - External terrain (Enderscape): wider noise range [-0.3, 0.3] for plate_block
 *   to compensate for different terrain characteristics
 * - Beyond/vanilla terrain: uses JSON-defined thresholds (default [-0.2, 0.2])
 */
public class SurfaceRuleMerger {

    private static final ResourceKey<NoiseGeneratorSettings> BEYOND_END_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_end"));

    /**
     * Merges surface rules into the active End noise settings. When {@code beyondActive}
     * is true, also scans foreign noise settings for biome-specific surface rules so mod
     * biomes keep their surface blocks under Beyond's chunk generator.
     */
    public static void mergeSurfaceRules(MinecraftServer server, boolean beyondActive) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            TheBeyond.LOGGER.warn("[TheBeyond] End dimension stem not found, skipping surface rule merge");
            return;
        }

        ChunkGenerator chunkGenerator = endStem.generator();
        if (!(chunkGenerator instanceof NoiseBasedChunkGenerator noiseGen)) {
            TheBeyond.LOGGER.info("[TheBeyond] End generator is not NoiseBasedChunkGenerator, skipping surface rule merge");
            return;
        }

        NoiseGeneratorSettings activeSettings = noiseGen.generatorSettings().value();
        BiomeSource endBiomeSource = endStem.generator().getBiomeSource();
        boolean externalTerrain = endBiomeSource instanceof MultiNoiseBiomeSource;

        SurfaceRules.RuleSource beyondRule;
        if (externalTerrain) {
            beyondRule = buildExternalTerrainRules();
            TheBeyond.LOGGER.info("[TheBeyond] Using wider surface rule thresholds for external terrain (Enderscape)");
        } else {
            Registry<NoiseGeneratorSettings> noiseRegistry = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
            NoiseGeneratorSettings beyondEnd = noiseRegistry.get(BEYOND_END_SETTINGS);
            if (beyondEnd == null) {
                TheBeyond.LOGGER.warn("[TheBeyond] Beyond End noise settings not found, skipping surface rule merge");
                return;
            }
            beyondRule = ((NoiseGeneratorSettingsAccessor) (Object) beyondEnd).the_beyond$getSurfaceRule();
            TheBeyond.LOGGER.info("[TheBeyond] Using default surface rule thresholds for Beyond/vanilla terrain");
        }

        // sequence() short-circuits on first non-null, so biome-guarded rules must precede
        // terminators. Order: beyond → Wover per-biome → foreign whole-settings → active rule.
        List<SurfaceRules.RuleSource> allRules = new ArrayList<>();
        allRules.add(beyondRule);

        if (beyondActive) {
            allRules.addAll(collectWoverBiomeSurfaceRules(registryAccess, endBiomeSource));
            allRules.addAll(collectForeignNoiseSettingsRules(registryAccess));
        }

        allRules.add(activeSettings.surfaceRule());

        SurfaceRules.RuleSource mergedRule = SurfaceRules.sequence(allRules.toArray(new SurfaceRules.RuleSource[0]));
        ((NoiseGeneratorSettingsAccessor) (Object) activeSettings).the_beyond$setSurfaceRule(mergedRule);
        TheBeyond.LOGGER.info("[TheBeyond] Merged surface rules into End generator settings");
    }

    /** Backward-compatible overload for existing call sites (soup mode). */
    public static void mergeSurfaceRules(MinecraftServer server) {
        mergeSurfaceRules(server, false);
    }

    /**
     * Collects surface rules from foreign {@link NoiseGeneratorSettings}:
     * {@code minecraft:end} (where datapack overrides like Stellarity inject their
     * rules) and any non-Beyond namespace whose key path contains {@code "end"}.
     * These are whole-settings blobs that may contain terminators, so the caller
     * must place them AFTER biome-guarded rules in the sequence.
     */
    private static List<SurfaceRules.RuleSource> collectForeignNoiseSettingsRules(
            RegistryAccess registryAccess) {
        Registry<NoiseGeneratorSettings> noiseRegistry = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
        List<SurfaceRules.RuleSource> foreignRules = new ArrayList<>();

        // minecraft:end — datapack overrides (e.g. Stellarity) inject rules here.
        ResourceKey<NoiseGeneratorSettings> vanillaEnd = ResourceKey.create(Registries.NOISE_SETTINGS,
                ResourceLocation.withDefaultNamespace("end"));
        NoiseGeneratorSettings vanillaEndSettings = noiseRegistry.get(vanillaEnd);
        if (vanillaEndSettings != null) {
            SurfaceRules.RuleSource rule = ((NoiseGeneratorSettingsAccessor) (Object) vanillaEndSettings)
                    .the_beyond$getSurfaceRule();
            if (rule != null) {
                foreignRules.add(rule);
                TheBeyond.LOGGER.info("[TheBeyond] Extracted surface rules from minecraft:end noise settings (may contain foreign mod overrides)");
            }
        }

        // Heuristic scan: foreign-namespace noise settings whose path contains "end".
        for (Map.Entry<ResourceKey<NoiseGeneratorSettings>, NoiseGeneratorSettings> entry :
                noiseRegistry.entrySet()) {
            ResourceKey<NoiseGeneratorSettings> key = entry.getKey();
            String ns = key.location().getNamespace();
            String path = key.location().getPath();

            if (ns.equals("minecraft") || ns.equals(TheBeyond.MODID)) continue;
            if (!path.contains("end")) continue;

            SurfaceRules.RuleSource rule = ((NoiseGeneratorSettingsAccessor) (Object) entry.getValue())
                    .the_beyond$getSurfaceRule();
            if (rule != null) {
                foreignRules.add(rule);
                TheBeyond.LOGGER.info("[TheBeyond] Extracted surface rules from {} noise settings", key.location());
            }
        }

        return foreignRules;
    }

    /**
     * Collects per-biome rules from Wover's {@code SURFACE_RULES_REGISTRY} and
     * wraps each with {@code ifTrue(isBiome(key), ...)}. Mirrors Wover's own
     * {@code SurfaceRuleUtil.getRulesForBiome}; reflective because Wover is not
     * a compile-time dependency. Silent empty return when Wover is absent.
     */
    private static List<SurfaceRules.RuleSource> collectWoverBiomeSurfaceRules(
            RegistryAccess registryAccess, BiomeSource biomeSource) {
        WoverRegistryAccess access = WoverRegistryAccess.INSTANCE;
        if (access == null) return List.of();

        @SuppressWarnings("unchecked")
        ResourceKey<Registry<Object>> key = (ResourceKey<Registry<Object>>) access.registryKey;
        Registry<Object> registry = registryAccess.registry(key).orElse(null);
        if (registry == null) return List.of();

        // Bucket AssignedSurfaceRule entries by biomeID for O(1) per-biome lookup.
        Map<ResourceLocation, List<Entry>> byBiome = new HashMap<>();
        try {
            for (Object assigned : registry) {
                if (assigned == null) continue;
                ResourceLocation id = (ResourceLocation) access.biomeIdField.get(assigned);
                if (id == null) continue;
                SurfaceRules.RuleSource rule = (SurfaceRules.RuleSource) access.ruleSourceField.get(assigned);
                if (rule == null) continue;
                int priority = access.priorityField.getInt(assigned);
                byBiome.computeIfAbsent(id, k -> new ArrayList<>()).add(new Entry(rule, priority));
            }
        } catch (IllegalAccessException e) {
            TheBeyond.LOGGER.warn("[TheBeyond] Failed to read Wover AssignedSurfaceRule fields; per-biome rules skipped", e);
            return List.of();
        }
        if (byBiome.isEmpty()) return List.of();

        List<SurfaceRules.RuleSource> out = new ArrayList<>();
        int ruleCount = 0;
        for (Holder<Biome> holder : biomeSource.possibleBiomes()) {
            ResourceKey<Biome> biomeKey = holder.unwrapKey().orElse(null);
            if (biomeKey == null) continue;
            List<Entry> matches = byBiome.get(biomeKey.location());
            if (matches == null || matches.isEmpty()) continue;
            // High-priority first — matches Wover's SequenceRuleSource ordering, so
            // priority overrides continue to win under this collection.
            matches.sort((a, b) -> b.priority - a.priority);
            SurfaceRules.RuleSource[] perBiome = new SurfaceRules.RuleSource[matches.size()];
            for (int i = 0; i < matches.size(); i++) perBiome[i] = matches.get(i).rule;
            // Public sequence(...) factory — SequenceRuleSource ctor is package-private.
            out.add(SurfaceRules.ifTrue(
                    SurfaceRules.isBiome(biomeKey),
                    SurfaceRules.sequence(perBiome)));
            ruleCount += perBiome.length;
        }
        if (ruleCount > 0) {
            TheBeyond.LOGGER.info(
                    "[TheBeyond] Collected {} per-biome surface rules from Wover SURFACE_RULES_REGISTRY across {} biomes",
                    ruleCount, out.size());
        }
        return out;
    }

    /** Simple tuple for the biomeID → rules bucket in {@link #collectWoverBiomeSurfaceRules}. */
    private record Entry(SurfaceRules.RuleSource rule, int priority) {}

    /** Reflective handle to Wover's SurfaceRuleRegistry / AssignedSurfaceRule,
     *  resolved once at class init. INSTANCE is null when Wover is absent. */
    private static final class WoverRegistryAccess {
        static final WoverRegistryAccess INSTANCE = tryCreate();

        final ResourceKey<? extends Registry<?>> registryKey;
        final Field biomeIdField;
        final Field ruleSourceField;
        final Field priorityField;

        private WoverRegistryAccess(ResourceKey<? extends Registry<?>> registryKey,
                                     Field biomeIdField, Field ruleSourceField, Field priorityField) {
            this.registryKey = registryKey;
            this.biomeIdField = biomeIdField;
            this.ruleSourceField = ruleSourceField;
            this.priorityField = priorityField;
        }

        private static WoverRegistryAccess tryCreate() {
            try {
                Class<?> registryApi = Class.forName("org.betterx.wover.surface.api.SurfaceRuleRegistry");
                ResourceKey<? extends Registry<?>> key =
                        (ResourceKey<? extends Registry<?>>) registryApi.getField("SURFACE_RULES_REGISTRY").get(null);
                Class<?> ruleClass = Class.forName("org.betterx.wover.surface.api.AssignedSurfaceRule");
                Field biomeIdField = ruleClass.getField("biomeID");
                Field ruleSourceField = ruleClass.getField("ruleSource");
                Field priorityField = ruleClass.getField("priority");
                TheBeyond.LOGGER.info("[TheBeyond] Wover surface rule reflection bound — per-biome collection enabled");
                return new WoverRegistryAccess(key, biomeIdField, ruleSourceField, priorityField);
            } catch (ClassNotFoundException e) {
                TheBeyond.LOGGER.debug("[TheBeyond] Wover not present on classpath; per-biome surface rule collection disabled");
                return null;
            } catch (Throwable t) {
                // Widened from ReflectiveOperationException so NoClassDefFoundError / LinkageError
                // from a broken Wover degrades to "skip" instead of breaking server start.
                TheBeyond.LOGGER.warn("[TheBeyond] Failed to bind Wover surface rule reflection; per-biome collection disabled", t);
                return null;
            }
        }
    }

    /**
     * Builds attracta_expanse surface rules with wider thresholds for Enderscape terrain.
     * plate_block: [-0.3, 0.3] (vs JSON default [-0.2, 0.2])
     * plated_end_stone: [-0.5, 0.5] (vs JSON default [-0.3, 0.3])
     */
    private static SurfaceRules.RuleSource buildExternalTerrainRules() {
        BlockState plateBlock = BeyondBlocks.PLATE_BLOCK.get().defaultBlockState();
        BlockState platedEndStone = BeyondBlocks.PLATED_END_STONE.get().defaultBlockState();

        ResourceKey<net.minecraft.world.level.biome.Biome> attractaExpanse = ResourceKey.create(
                Registries.BIOME, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "attracta_expanse"));

        return SurfaceRules.ifTrue(
                SurfaceRules.isBiome(attractaExpanse),
                SurfaceRules.sequence(
                        // Floor surface (top block)
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR,
                                SurfaceRules.sequence(
                                        // plate_block: wider range for Enderscape
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.3, 0.3),
                                                SurfaceRules.state(plateBlock)),
                                        // plated_end_stone: transition zone
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.5, 0.5),
                                                SurfaceRules.state(platedEndStone))
                                )),
                        // Subsurface (one block below floor)
                        SurfaceRules.ifTrue(SurfaceRules.stoneDepthCheck(1, false, CaveSurface.FLOOR),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, -0.3, 0.3),
                                        SurfaceRules.state(platedEndStone)))
                ));
    }
}
