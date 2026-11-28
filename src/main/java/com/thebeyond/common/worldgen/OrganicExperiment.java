package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.block.BonfireBlock;
import com.thebeyond.common.block.OrganicBulbBlock;
import com.thebeyond.common.block.OrganicFilmBlock;
import com.thebeyond.common.block.OrganicTissueBlock;
import com.thebeyond.common.debug.OrganicWatch;
import com.thebeyond.common.registry.BeyondAttachments;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.util.VoronoiNoise;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/** The organic network's switches and block palette, shared by its painter, growth, terrain and zone line. */
public final class OrganicExperiment {
    public static final String STYLE = System.getProperty("beyond.sub.org", "");
    // the biomes whose cells grow an organism (any mod's biome ids, comma list), none unless configured
    static final Set<ResourceKey<Biome>> HOME = Set.copyOf(Arrays.stream(System.getProperty("beyond.sub.org.habitat", "")
            .split(",")).map(String::trim).filter(x -> !x.isEmpty())
            .map(x -> ResourceKey.create(Registries.BIOME, ResourceLocation.parse(x))).toList());
    public static final boolean ON = !STYLE.isEmpty() && !HOME.isEmpty();
    private static final double MESH_SHARE = Double.parseDouble(System.getProperty("beyond.sub.org.mesh", "0.5"));
    static final double HALF = 156;
    private static final double[] BAND_TOP = {50, 100, 150, 250, 400, 600, 900, 1300};
    private static final Block[] WOOL = {Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
            Blocks.CYAN_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.BLUE_WOOL, Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL};
    private static final Block[] GLASS = {Blocks.RED_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS, Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS, Blocks.MAGENTA_STAINED_GLASS};
    private static final Block[] CLAY = {Blocks.RED_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
            Blocks.LIME_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.BLUE_TERRACOTTA,
            Blocks.PURPLE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA};
    static final ResourceKey<Biome> VOID_BOG = ResourceKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("the_beyond", "void_bog"));
    static final byte LINE = 10;
    static final Block LINE_BLOCK = Blocks.COAL_BLOCK;
    static final boolean OWN_BLOCKS = !"vanilla".equals(System.getProperty("beyond.sub.org.blocks", "own"));
    private static final Block[] GLAZED = {Blocks.RED_GLAZED_TERRACOTTA, Blocks.ORANGE_GLAZED_TERRACOTTA,
            Blocks.YELLOW_GLAZED_TERRACOTTA, Blocks.LIME_GLAZED_TERRACOTTA, Blocks.CYAN_GLAZED_TERRACOTTA,
            Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.BLUE_GLAZED_TERRACOTTA, Blocks.PURPLE_GLAZED_TERRACOTTA,
            Blocks.MAGENTA_GLAZED_TERRACOTTA};

    private OrganicExperiment() {}

    /** A voxel's own block with its distance band, or with blocks=vanilla a vanilla palette (terracotta, glazed in rock). */
    static BlockState state(byte k, float d, boolean exposed) {
        int band = 0;
        while (band < BAND_TOP.length && d >= BAND_TOP[band]) band++;
        band = Math.min(band, CLAY.length - 1);
        if (k == LINE) return LINE_BLOCK.defaultBlockState();
        if (!OWN_BLOCKS) {
            if (k == OrganicNetwork.BULB) {
                Block b = d < 1 ? exposed ? Blocks.OBSIDIAN : Blocks.CRYING_OBSIDIAN
                        : exposed ? Blocks.RAW_GOLD_BLOCK : Blocks.GOLD_BLOCK;
                return b.defaultBlockState();
            }
            if (k == OrganicNetwork.FILM) return GLASS[band].defaultBlockState();
            if (k == OrganicNetwork.TUNNEL) return GLAZED[band].defaultBlockState();
            return CLAY[band].defaultBlockState();
        }
        if (k == OrganicNetwork.BULB) {
            Block b = d < 1 ? BeyondBlocks.ORG_NUCLEUS.get() : BeyondBlocks.ORG_BULB.get();
            return b.defaultBlockState().setValue(OrganicBulbBlock.EXPOSED, exposed);
        }
        Block b = k == OrganicNetwork.FILM ? BeyondBlocks.ORG_FILM.get() : k == OrganicNetwork.TUNNEL ? BeyondBlocks.ORG_ROOT.get()
                : BeyondBlocks.ORG_TUBE.get();
        return b.defaultBlockState().setValue(OrganicTissueBlock.BAND, band);
    }

    static boolean tissue(BlockState s) {
        Block b = s.getBlock();
        return b instanceof OrganicTissueBlock || b instanceof OrganicFilmBlock || b instanceof OrganicBulbBlock;
    }

    /** MIXED draws the style per cell from the seed, MESH_SHARE of the cells as slime mold. */
    static OrganicNetwork.Style style(SubSites.Cell c) {
        String s = STYLE.toUpperCase(Locale.ROOT);
        if (!s.equals("MIXED")) return OrganicNetwork.Style.valueOf(s);
        long h = SubSites.Network.key(c) * 0x9E3779B97F4A7C15L ^ BeyondEndChunkGenerator.seed;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53 < MESH_SHARE ? OrganicNetwork.Style.MESH : OrganicNetwork.Style.TREE;
    }
}
