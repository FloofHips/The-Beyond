package com.thebeyond.common.worldgen.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Discovers End biomes from installed mods via tags ({@code #minecraft:is_end},
 * {@code #c:is_end_biome}, {@code #wover:is_end/*}) and injects them into
 * {@link BeyondEndBiomeSource}'s tainted End pool at server start.
 *
 * <p>Deduplicates against all existing pools. Called from {@code ServerWorldEvents}.</p>
 */
public class EndBiomeDiscovery {

    /** Vanilla End biome tag — most mods add their End biomes here. */
    private static final TagKey<Biome> IS_END = TagKey.create(Registries.BIOME,
            ResourceLocation.withDefaultNamespace("is_end"));

    /** Common convention tag (NeoForge/Fabric ecosystem). */
    private static final TagKey<Biome> C_IS_END_BIOME = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("c", "is_end_biome"));

    /** Wover/BetterEnd land biome tags (optional, non-fatal if absent). */
    private static final List<TagKey<Biome>> WOVER_END_TAGS = List.of(
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("wover", "is_end/land")),
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("wover", "is_end/highland")),
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("wover", "is_end/midland")),
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("wover", "is_end/barrens")),
            TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("wover", "is_end/high_or_midland"))
    );

    /**
     * Scans biome tags for End biomes from installed mods and injects them into
     * Beyond's tainted End biome pool.
     *
     * @param server the Minecraft server instance (available at ServerAboutToStartEvent)
     */
    public static void discoverAndInject(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            TheBeyond.LOGGER.warn("[TheBeyond] End dimension stem not found, skipping biome auto-discovery");
            return;
        }

        BiomeSource biomeSource = endStem.generator().getBiomeSource();
        if (!(biomeSource instanceof BeyondEndBiomeSource beyondSource)) {
            TheBeyond.LOGGER.debug("[TheBeyond] End biome source is not BeyondEndBiomeSource, skipping auto-discovery");
            return;
        }

        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        // Collect all candidate biomes from tags, deduplicating by ResourceKey.
        Map<ResourceKey<Biome>, Holder<Biome>> candidates = new LinkedHashMap<>();
        collectFromTag(biomeRegistry, IS_END, candidates);
        collectFromTag(biomeRegistry, C_IS_END_BIOME, candidates);
        for (TagKey<Biome> woverTag : WOVER_END_TAGS) {
            collectFromTag(biomeRegistry, woverTag, candidates);
        }

        if (candidates.isEmpty()) {
            TheBeyond.LOGGER.info("[TheBeyond] No End biomes found via tags — nothing to auto-discover");
            return;
        }

        // Inject into the tainted pool. BeyondEndBiomeSource.injectBiomesIntoTaintedPool
        // handles deduplication against all existing pools (tainted, void, center, bottom).
        int injected = beyondSource.injectBiomesIntoTaintedPool(candidates.values());

        if (injected > 0) {
            // Log which namespaces contributed biomes
            Set<String> namespaces = candidates.values().stream()
                    .flatMap(h -> h.unwrapKey().stream())
                    .map(k -> k.location().getNamespace())
                    .filter(ns -> !ns.equals("minecraft") && !ns.equals(TheBeyond.MODID))
                    .collect(Collectors.toCollection(TreeSet::new));

            TheBeyond.LOGGER.info("[TheBeyond] Auto-discovered {} End biome(s) from tags and injected into tainted pool. " +
                    "Contributing mod namespaces: {}", injected, namespaces);
        } else {
            TheBeyond.LOGGER.info("[TheBeyond] All tagged End biomes already present in biome pools — nothing new to inject");
        }
    }

    private static void collectFromTag(Registry<Biome> registry, TagKey<Biome> tag,
                                        Map<ResourceKey<Biome>, Holder<Biome>> out) {
        Iterable<Holder<Biome>> entries = registry.getTagOrEmpty(tag);
        for (Holder<Biome> holder : entries) {
            holder.unwrapKey().ifPresent(key -> out.putIfAbsent(key, holder));
        }
    }
}
