package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.common.registry.BeyondTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceBlockConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pedestal, knee and island rock read from each structure and the End generator, the tags only force them. */
public final class StructureReadings {
    private static volatile Set<Block> rock;
    static int unread;

    private StructureReadings() {}

    public static boolean pedestal(Structure s, @Nullable ResourceLocation key, @Nullable Registry<Structure> reg) {
        Holder<Structure> h = reg == null ? null : reg.wrapAsHolder(s);
        StructureIntegrationProfile p = BeyondForeignStructureProfiles.get(key);
        boolean floats = BeyondForeignStructureProfiles.isEmbedded(key) || BeyondForeignStructureProfiles.isAutoFloatCarved(key)
                || (p != null && p.anchor() == StructureIntegrationProfile.Anchor.FLOATING);
        return pedestal(s.terrainAdaptation(), s.step(), floats, tagged(h, BeyondTags.BASE_PEDESTAL),
                tagged(h, BeyondTags.NO_BASE_PEDESTAL));
    }

    static boolean pedestal(TerrainAdjustment adapt, GenerationStep.Decoration step, boolean floats, boolean forced,
            boolean barred) {
        if (barred) return false;
        return forced || (adapt == TerrainAdjustment.BEARD_THIN && step == GenerationStep.Decoration.SURFACE_STRUCTURES
                && !floats);
    }

    public static boolean knee(Holder<Structure> h) {
        return knee(h.value().terrainAdaptation(), tagged(h, BeyondTags.KNEE), tagged(h, BeyondTags.NO_KNEE));
    }

    static boolean knee(TerrainAdjustment adapt, boolean forced, boolean barred) {
        return !barred && (forced || adapt == TerrainAdjustment.BEARD_BOX);
    }

    public static boolean rock(BlockState s) {
        if (s.is(BeyondTags.NO_FOUNDATION_ROCK)) return false;
        return s.is(BeyondTags.FOUNDATION_ROCK) || rockSet().contains(s.getBlock());
    }

    private static boolean tagged(@Nullable Holder<Structure> h, TagKey<Structure> tag) {
        try {
            return h != null && h.is(tag);
        } catch (IllegalStateException unbound) {
            return false;
        }
    }

    public static void reset() {
        rock = null;
    }

    private static Set<Block> rockSet() {
        Set<Block> r = rock;
        if (r != null) return r;
        synchronized (StructureReadings.class) {
            if (rock == null) rock = readRock();
            return rock;
        }
    }

    /** Read at first use, after Isleweaver and the biome APIs have merged their surface rules into the End generator. */
    private static Set<Block> readRock() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel end = server == null ? null : server.getLevel(Level.END);
        if (end == null || !(end.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen)) return Set.of();
        Set<Block> out = new HashSet<>();
        unread = 0;
        try {
            islandRock(gen.generatorSettings().value(), gen.getBiomeSource().possibleBiomes(), out);
        } catch (Throwable t) {
            com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] island rock not fully read from the End generator, {} blocks so far:"
                    + " {}", out.size(), t.toString());
        }
        if (unread > 0) {
            com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] island rock: {} fields of the End generator's rules could not be read,"
                    + " so the set may miss blocks", unread);
        }
        List<String> ids = new ArrayList<>();
        for (Block b : out) ids.add(BuiltInRegistries.BLOCK.getKey(b).toString());
        Collections.sort(ids);
        com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] island rock read from the End generator: {} blocks {}", ids.size(), ids);
        return Set.copyOf(out);
    }

    static void islandRock(NoiseGeneratorSettings settings, Set<Holder<Biome>> biomes, Set<Block> out) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        add(settings.defaultBlock(), out);
        collect(settings.surfaceRule(), false, out, seen, 0);
        for (Holder<Biome> b : biomes) {
            for (HolderSet<PlacedFeature> step : b.value().getGenerationSettings().features()) {
                for (Holder<PlacedFeature> f : step) {
                    Object cfg = f.value().feature().value().config();
                    if (cfg instanceof OreConfiguration || cfg instanceof ReplaceBlockConfiguration) {
                        collect(cfg, true, out, seen, 0);
                    }
                }
            }
        }
    }

    /** Walks any mod's rule or ore config by reflection, since a rule a mod wraps at runtime does not show in its codec. */
    @SuppressWarnings("unchecked")
    static void collect(@Nullable Object o, boolean ore, Set<Block> out, Set<Object> seen, int depth) {
        if (o == null || depth > 64 || !seen.add(o)) return;
        if (o instanceof BlockState s) { add(s, out); return; }
        if (ore && o instanceof Block b) { add(b.defaultBlockState(), out); return; }
        if (ore && o instanceof TagKey<?> t) {
            if (t.registry().equals(Registries.BLOCK)) {
                BuiltInRegistries.BLOCK.getTag((TagKey<Block>) t)
                        .ifPresent(hs -> hs.forEach(h -> add(h.value().defaultBlockState(), out)));
            }
            return;
        }
        if (o instanceof Collection<?> c) { for (Object x : c) collect(x, ore, out, seen, depth + 1); return; }
        if (o instanceof Map<?, ?> m) { for (Object x : m.values()) collect(x, ore, out, seen, depth + 1); return; }
        if (o instanceof Optional<?> opt) { opt.ifPresent(x -> collect(x, ore, out, seen, depth + 1)); return; }
        if (o instanceof Object[] arr) { for (Object x : arr) collect(x, ore, out, seen, depth + 1); return; }
        Class<?> cl = o.getClass();
        if (cl.getName().startsWith("java.") || o instanceof Enum<?> || o instanceof Block || o instanceof Biome
                || o instanceof Registry<?> || o instanceof ChunkGenerator || o instanceof Level
                || o instanceof MinecraftServer) return;
        for (Class<?> c = cl; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    collect(f.get(o), ore, out, seen, depth + 1);
                } catch (Throwable denied) {
                    unread++;
                }
            }
        }
    }

    private static void add(BlockState s, Set<Block> out) {
        if (!s.isAir() && !s.hasBlockEntity() && s.getFluidState().isEmpty()
                && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) out.add(s.getBlock());
    }
}
