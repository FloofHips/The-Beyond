package com.thebeyond.common.worldgen.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.mixin.NoiseGeneratorSettingsAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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

import java.util.ArrayList;
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
     * Merges surface rules into the active End noise settings.
     *
     * <p>When {@code beyondActive} is true, Beyond's terrain owns the End. In that case,
     * the method also scans foreign noise settings entries (from installed mods) for
     * surface rules that should be merged. This lets mods' biome-specific surface blocks
     * work under Beyond's chunk generator without requiring manual compat.</p>
     *
     * @param server       the Minecraft server
     * @param beyondActive true if Beyond's chunk gen is the active End provider
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
        boolean externalTerrain = endStem.generator().getBiomeSource() instanceof MultiNoiseBiomeSource;

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

        // Collect rules: Beyond first (highest priority), then foreign mods, then existing
        List<SurfaceRules.RuleSource> allRules = new ArrayList<>();
        allRules.add(beyondRule);

        if (beyondActive) {
            List<SurfaceRules.RuleSource> foreignRules = collectForeignSurfaceRules(registryAccess);
            allRules.addAll(foreignRules);
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
     * Scans the noise settings registry for entries from foreign mods that might contain
     * End-related surface rules. Extracts and returns them for merging.
     *
     * <p>Checks two sources:</p>
     * <ol>
     *   <li>{@code minecraft:end} — vanilla's End noise settings, which mods may override
     *       via datapack to add their own surface rules (e.g. Stellarity)</li>
     *   <li>Any noise settings entry whose namespace matches a known End mod — some mods
     *       define their own settings key (e.g. {@code modid:the_end})</li>
     * </ol>
     */
    private static List<SurfaceRules.RuleSource> collectForeignSurfaceRules(RegistryAccess registryAccess) {
        Registry<NoiseGeneratorSettings> noiseRegistry = registryAccess.registryOrThrow(Registries.NOISE_SETTINGS);
        List<SurfaceRules.RuleSource> foreignRules = new ArrayList<>();

        // Check minecraft:end — mods that override vanilla End settings via datapack
        // inject their surface rules here.
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

        // Scan all noise settings for entries from foreign namespaces that might be
        // End-related. Heuristic: key path contains "end" and namespace is not ours.
        for (Map.Entry<ResourceKey<NoiseGeneratorSettings>, NoiseGeneratorSettings> entry :
                noiseRegistry.entrySet()) {
            ResourceKey<NoiseGeneratorSettings> key = entry.getKey();
            String ns = key.location().getNamespace();
            String path = key.location().getPath();

            // Skip vanilla and Beyond's own settings (already handled)
            if (ns.equals("minecraft") || ns.equals(TheBeyond.MODID)) continue;

            // Heuristic: only consider settings whose path suggests End dimension
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
