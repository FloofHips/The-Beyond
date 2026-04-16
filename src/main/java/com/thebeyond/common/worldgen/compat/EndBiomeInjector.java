package com.thebeyond.common.worldgen.compat;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.thebeyond.TheBeyond;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.*;

/**
 * Injects Beyond biomes into the End dimension at server start.
 *
 * Supports two modes:
 * - MultiNoiseBiomeSource (Enderscape): Injects climate parameters directly
 * - TheEndBiomeSource (vanilla): Sets up holders for TheEndBiomeSourceMixin
 *
 * Called via ServerAboutToStartEvent, before levels are created.
 */
public class EndBiomeInjector {

    private static final ResourceKey<Biome> ATTRACTA_EXPANSE = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "attracta_expanse"));
    private static final ResourceKey<Biome> PEER_LANDS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "peer_lands"));
    private static final ResourceKey<Biome> THE_PATHS = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "the_paths"));
    private static final ResourceKey<Biome> TRUE_VOID = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, "true_void"));

    /** Holders for TheEndBiomeSourceMixin (vanilla End). Set at server start, null if not applicable. */
    public static volatile VanillaEndHolders vanillaEndHolders;

    public static class VanillaEndHolders {
        public final Holder<Biome> attractaExpanse;
        public final Holder<Biome> peerLands;
        public final Holder<Biome> trueVoid;
        public final Holder<Biome> thePaths;

        public VanillaEndHolders(Holder<Biome> attractaExpanse, Holder<Biome> peerLands,
                                  Holder<Biome> trueVoid, Holder<Biome> thePaths) {
            this.attractaExpanse = attractaExpanse;
            this.peerLands = peerLands;
            this.trueVoid = trueVoid;
            this.thePaths = thePaths;
        }
    }

    public static void injectBiomes(MinecraftServer server) {
        RegistryAccess registryAccess = server.registryAccess();

        Registry<LevelStem> dimensions = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
        LevelStem endStem = dimensions.get(LevelStem.END);
        if (endStem == null) {
            TheBeyond.LOGGER.warn("[TheBeyond] End dimension stem not found in registry, skipping biome injection");
            return;
        }

        BiomeSource biomeSource = endStem.generator().getBiomeSource();

        if (biomeSource instanceof MultiNoiseBiomeSource multiNoise) {
            injectIntoMultiNoise(multiNoise, registryAccess);
        } else if (biomeSource instanceof TheEndBiomeSource) {
            setupVanillaEnd(biomeSource, registryAccess);
        } else {
            TheBeyond.LOGGER.info("[TheBeyond] End biome source is {} (unhandled type), skipping injection",
                    biomeSource.getClass().getSimpleName());
        }
    }

    /** Inject into Enderscape's MultiNoiseBiomeSource using climate parameters. */
    private static void injectIntoMultiNoise(MultiNoiseBiomeSource multiNoise, RegistryAccess registryAccess) {
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        Climate.ParameterList<Holder<Biome>> paramList = multiNoise.parameters
                .map(direct -> direct, preset -> preset.value().parameters());

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> existing = paramList.values();
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> merged = new ArrayList<>(existing);
        int added = 0;

        // attracta_expanse: Deep highlands zone (positive temp avoids end_highlands[-1,0] domination)
        added += tryAdd(merged, biomeRegistry, ATTRACTA_EXPANSE, Climate.parameters(
                Climate.Parameter.span(0.0F, 0.3F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-0.55F, 0.55F),
                Climate.Parameter.span(0.50025F, 2.0F),
                Climate.Parameter.span(0.2F, 1.025F),
                Climate.Parameter.span(-0.05F, 1.0F),
                0
        ));

        // peer_lands: Shallow land zone (positive temp, shallow depth)
        added += tryAdd(merged, biomeRegistry, PEER_LANDS, Climate.parameters(
                Climate.Parameter.span(0.0F, 0.3F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-0.55F, 0.55F),
                Climate.Parameter.span(0.50025F, 2.0F),
                Climate.Parameter.span(-0.4F, 0.2F),
                Climate.Parameter.span(-0.05F, 1.0F),
                0
        ));

        // true_void: Small islands zone
        added += tryAdd(merged, biomeRegistry, TRUE_VOID, Climate.parameters(
                Climate.Parameter.span(0.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 2.0F),
                Climate.Parameter.span(-0.1F, 0.05F),
                Climate.Parameter.span(-1.0F, -0.05F),
                0
        ));

        // the_paths: Deep void
        added += tryAdd(merged, biomeRegistry, THE_PATHS, Climate.parameters(
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 1.0F),
                Climate.Parameter.span(-1.0F, 2.0F),
                Climate.Parameter.span(-1.5F, -0.4F),
                Climate.Parameter.span(-1.0F, 1.0F),
                0
        ));

        if (added == 0) return;

        Climate.ParameterList<Holder<Biome>> newParamList = new Climate.ParameterList<>(merged);
        multiNoise.parameters = Either.left(newParamList);

        Set<Holder<Biome>> allBiomes = merged.stream()
                .map(Pair::getSecond)
                .distinct()
                .collect(ImmutableSet.toImmutableSet());
        ((BiomeSource) multiNoise).possibleBiomes = Suppliers.memoize(() -> allBiomes);

        TheBeyond.LOGGER.info("[TheBeyond] Injected {} Beyond biomes into End MultiNoiseBiomeSource ({} total)",
                added, merged.size());
    }

    /** Set up holders for vanilla End (TheEndBiomeSourceMixin handles the actual biome replacement). */
    private static void setupVanillaEnd(BiomeSource biomeSource, RegistryAccess registryAccess) {
        Registry<Biome> biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

        Holder<Biome> attracta = biomeRegistry.getHolder(ATTRACTA_EXPANSE).orElse(null);
        Holder<Biome> peer = biomeRegistry.getHolder(PEER_LANDS).orElse(null);
        Holder<Biome> trueV = biomeRegistry.getHolder(TRUE_VOID).orElse(null);
        Holder<Biome> paths = biomeRegistry.getHolder(THE_PATHS).orElse(null);

        if (attracta == null && peer == null && trueV == null && paths == null) {
            TheBeyond.LOGGER.warn("[TheBeyond] No Beyond biomes found in registry, skipping vanilla End setup");
            return;
        }

        vanillaEndHolders = new VanillaEndHolders(attracta, peer, trueV, paths);

        // Update possibleBiomes cache for /locate support
        Set<Holder<Biome>> allBiomes = new HashSet<>(biomeSource.possibleBiomes());
        if (attracta != null) allBiomes.add(attracta);
        if (peer != null) allBiomes.add(peer);
        if (trueV != null) allBiomes.add(trueV);
        if (paths != null) allBiomes.add(paths);
        ImmutableSet<Holder<Biome>> finalBiomes = ImmutableSet.copyOf(allBiomes);
        biomeSource.possibleBiomes = Suppliers.memoize(() -> finalBiomes);

        TheBeyond.LOGGER.info("[TheBeyond] Set up vanilla End biome injection (TheEndBiomeSourceMixin active)");
    }

    private static int tryAdd(List<Pair<Climate.ParameterPoint, Holder<Biome>>> merged,
                               Registry<Biome> registry,
                               ResourceKey<Biome> key,
                               Climate.ParameterPoint params) {
        Optional<Holder.Reference<Biome>> holder = registry.getHolder(key);
        if (holder.isPresent()) {
            merged.add(Pair.of(params, holder.get()));
            return 1;
        }
        TheBeyond.LOGGER.warn("[TheBeyond] Biome {} not in registry, skipping injection", key.location());
        return 0;
    }
}
