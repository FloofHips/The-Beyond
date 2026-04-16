package com.thebeyond.common.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thebeyond.util.WorldSeedHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class BeyondEndChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<BeyondEndChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((p_255585_) -> {
        return p_255585_.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter((p_255584_) -> {
            return p_255584_.biomeSource;
        }), NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter((p_224278_) -> {
            return p_224278_.settings;
        })).apply(p_255585_, p_255585_.stable(BeyondEndChunkGenerator::new));
    });

    private final Holder<NoiseGeneratorSettings> settings;
    public static SimplexNoise simplexNoise;
    public static PerlinSimplexNoise globalHOffsetNoise;
    public static PerlinSimplexNoise globalVOffsetNoise;
    public static PerlinSimplexNoise globalCOffsetNoise;
    private double islandRadius = 75.0;
    private double buffer = 700.0;
    private static final double worldHeight = 192;

    private static final int NUM_OCTAVES = 4;
    private static final double LACUNARITY = 2.0;
    private static final double PERSISTENCE = 0.5;
    private static final int TERRAIN_Y_OFFSET = 32;

    /**
     * Ping-pong wrap: bounces input between min and max like a triangle wave.
     * Unlike hard modulo (which has a seam), adjacent inputs always map to
     * adjacent outputs — no discontinuity at the wrap boundary.
     */
    private static int pingPongWrap(int input, int min, int max) {
        int range = max - min;
        int wrap = range * 2;

        int x = (input - min) % wrap;
        if (x < 0) x += wrap;

        if (x > range) {
            x = wrap - x;
        }

        return x + min;
    }

    public BeyondEndChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
    }

    public void computeNoisesIfNotPresent(RandomState randomState) {
        if (simplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {
            WorldSeedHolder holder = (WorldSeedHolder) (Object) randomState;
            long worldSeed = holder.the_Beyond$getWorldSeed();
            computeNoisesIfNotPresent(worldSeed);
        }
    }

    public void computeNoisesIfNotPresent(long worldSeed) {
        if (simplexNoise == null || globalHOffsetNoise == null || globalVOffsetNoise == null || globalCOffsetNoise == null) {
            RandomSource random1 = RandomSource.create(worldSeed);
            RandomSource random2 = RandomSource.create(worldSeed+1);
            RandomSource random3 = RandomSource.create(worldSeed+2);
            RandomSource random4 = RandomSource.create(worldSeed+3);

            simplexNoise = new SimplexNoise(random1);
            globalHOffsetNoise = new PerlinSimplexNoise(random2, Collections.singletonList(1));
            globalVOffsetNoise = new PerlinSimplexNoise(random3, Collections.singletonList(1));
            globalCOffsetNoise = new PerlinSimplexNoise(random4, Collections.singletonList(1));
        }
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager structureTemplateManager) {
        computeNoisesIfNotPresent(structureState.getLevelSeed());
        super.createStructures(registryAccess, structureState, structureManager, chunk, structureTemplateManager);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        computeNoisesIfNotPresent(randomState);
        return super.createBiomes(randomState, blender, structureManager, chunk);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType, LevelHeightAccessor level, RandomState randomState) {
        computeNoisesIfNotPresent(randomState);
        float distanceFromOrigin = (float) Math.sqrt((double) x * x + (double) z * z);

        for (int y = 132; y >= level.getMinBuildHeight(); y--) {
            if (isSolidTerrain(x, y, z, distanceFromOrigin)) {
                return y;
            }
        }

        return 0;
    }
    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        computeNoisesIfNotPresent(random);
        return Math.max(this.getBaseHeight(x, z, type, level, random), level.getMinBuildHeight());
    }

    @Override
    public int getMinY() {
        return 10;
    }

    public static double getHorizontalBaseScale(int globalX, int globalZ) {
        return globalNoiseOffset(0.005, 0.015, globalX * 0.000001, globalZ * 0.000001, globalHOffsetNoise);
    }

    public static double getVerticalBaseScale(int globalX, int globalZ) {
        return globalNoiseOffset(0.005, 0.015, globalX * 0.00001, globalZ * 0.00001, globalVOffsetNoise);
    }

    public static double getCycleHeight(int globalX, int globalZ) {
        return globalNoiseOffset(10, 100, globalX * 0.0001, globalZ * 0.0001, globalCOffsetNoise);
    }

    public static double getThreshold(int globalX, int globalZ, float distanceFromOrigin) {
        double baseThreshold = globalNoiseOffset(0.01, 0.6, globalX * 0.0002, globalZ * 0.0002, globalCOffsetNoise);

        float innerTaperEnd = 100f;
        float outerTaperStart = 700f;
        float outerTaperEnd = 750f;

        if (distanceFromOrigin > innerTaperEnd && distanceFromOrigin < outerTaperStart) {
            return 1.0;
        }
        else if (distanceFromOrigin >= outerTaperStart && distanceFromOrigin <= outerTaperEnd) {
            float progress = (distanceFromOrigin - outerTaperStart) / (outerTaperEnd - outerTaperStart);
            double taperValue = 0.59 * (1.0 - progress);

            return baseThreshold + taperValue;
        }
        else {
            return baseThreshold;
        }
    }

    /**
     * Convenience entry point for callers that don't have the per-column noise values
     * pre-resolved (one-shot probes like {@link #isSolidTerrain}, heightmap queries, and
     * {@link #addDebugScreenInfo}). Resolves horizontal/vertical scales, cycle height and
     * the ping-pong wrapped coordinates, then delegates to the hot-path 6-arg overload.
     *
     * <p>Hot-path callers ({@link #generateEndTerrain}) MUST use the 6-arg form directly
     * and hoist the column-invariant values out of their y-loop — this wrapper re-resolves
     * 5 PerlinSimplex lookups on every call, which the y-loop cannot afford.</p>
     */
    public static double getTerrainDensity(int globalX, int globalY, int globalZ) {
        double horizontalBaseScale = getHorizontalBaseScale(globalX, globalZ);
        double verticalBaseScale = getVerticalBaseScale(globalX, globalZ);
        double cycleHeight = getCycleHeight(globalX, globalZ);

        // Ping-pong wrap the block coordinates before scaling. Keeps noise inputs
        // small enough that SimplexNoise's & 0xFF permutation table doesn't cause
        // precision loss, while avoiding the hard seam of modulo wrapping.
        // Range 0–65536 is large enough that the bounce point is never visible in gameplay.
        int wrappedX = pingPongWrap(globalX, 0, 65536);
        int wrappedZ = pingPongWrap(globalZ, 0, 65536);

        return getTerrainDensity(globalY, horizontalBaseScale, verticalBaseScale, cycleHeight, wrappedX, wrappedZ);
    }

    /**
     * Hot-path terrain density sampler. All column-invariant values are passed in by the
     * caller, which MUST hoist them out of any y-iteration loop. Used by
     * {@link #generateEndTerrain} where a single column is sampled for 159 y-levels — the
     * old 3-arg form resolved 3 Perlin scales + 2 pingPongWrap calls per call, costing
     * 160× more than necessary inside the chunk-gen hot path.
     *
     * @param globalY              absolute y coordinate being sampled
     * @param horizontalBaseScale  pre-resolved {@link #getHorizontalBaseScale} for this column
     * @param verticalBaseScale    pre-resolved {@link #getVerticalBaseScale} for this column
     * @param cycleHeight          pre-resolved {@link #getCycleHeight} for this column
     * @param wrappedX             pre-computed {@link #pingPongWrap} of globalX
     * @param wrappedZ             pre-computed {@link #pingPongWrap} of globalZ
     */
    public static double getTerrainDensity(
            int globalY,
            double horizontalBaseScale,
            double verticalBaseScale,
            double cycleHeight,
            int wrappedX,
            int wrappedZ) {
        int shiftedY = globalY + TERRAIN_Y_OFFSET;

        double noiseValue = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxAmplitude = 0.0;

        for (int octave = 0; octave < NUM_OCTAVES; octave++) {
            double hScale = horizontalBaseScale * frequency;
            double vScale = verticalBaseScale * frequency;

            double sampleX = wrappedX * hScale;
            double sampleY = shiftedY * vScale;
            double sampleZ = wrappedZ * hScale;

            double octaveNoise = simplexNoise.getValue(sampleX, sampleY, sampleZ);
            noiseValue += octaveNoise * amplitude;
            maxAmplitude += amplitude;

            amplitude *= PERSISTENCE;
            frequency *= LACUNARITY;
        }

        noiseValue /= maxAmplitude;
        double densityModifier = cyclicDensity(shiftedY, cycleHeight);
        noiseValue *= densityModifier;

        return edgeGradient(shiftedY, worldHeight, noiseValue);
    }

    public static boolean isSolidTerrain(int globalX, int globalY, int globalZ, float distanceFromOrigin) {
        double threshold = getThreshold(globalX, globalZ, distanceFromOrigin);
        double density = getTerrainDensity(globalX, globalY, globalZ);

        return density > threshold;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        computeNoisesIfNotPresent(randomState);
        return CompletableFuture.supplyAsync(() -> {
            ChunkPos chunkPos = chunk.getPos();
            int startX = chunkPos.getMinBlockX();
            int startZ = chunkPos.getMinBlockZ();

            // Snapshot the valid structure starts ONCE per chunk. Previously the inner
            // adaptation call re-fetched chunk.getAllStarts() and re-checked isValid() on
            // every (x, y, z) triple — 40 704 calls per chunk for a pair of trivial lookups
            // that never change during fillFromNoise. The snapshot also lets the hot path
            // iterate a plain List (no Map.values() iterator churn).
            List<StructureStart> validStarts = null;
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (start.isValid()) {
                    if (validStarts == null) validStarts = new ArrayList<>(4);
                    validStarts.add(start);
                }
            }
            if (validStarts == null) validStarts = Collections.emptyList();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int globalX = startX + x;
                    int globalZ = startZ + z;

                    // Auroracite floor is placed by AuroraciteLayerFeature (biome modifier on
                    // #minecraft:is_end at raw_generation). Generating it here too caused the
                    // chunk-gen pass and the feature pass to OR two independent simplex noises,
                    // bumping coverage from ~50% to ~75% and producing a near-solid floor when
                    // Beyond's chunk gen actually runs (i.e. when BetterX/Wover does not replace it).

                    float distanceFromOrigin = (float) Math.sqrt((double) globalX * globalX + (double) globalZ * globalZ);

                    if (distanceFromOrigin <= islandRadius + 50) {
                        generateMainIsland(chunk, globalX, globalZ, distanceFromOrigin, islandRadius);
                    }

                    else if (distanceFromOrigin >= 650) {
                      generateEndTerrain(chunk, globalX, globalZ, distanceFromOrigin, validStarts);
                    }
                }
            }

            return chunk;
        });
    }

    private void generateMainIsland(ChunkAccess chunk, int globalX, int globalZ, double distance, double islandRadius) {
        islandRadius += 50;
        int height = 40;
        float threshold = 1;

        for (int y = 0; y <= 40; y++) {
            double noiseValue = simplexNoise.getValue(globalX * 0.03, y * 0.1, globalZ * 0.03);
            double noiseOctave = simplexNoise.getValue(globalX * 0.1, y * 0.1, globalZ * 0.1);

            double finalNoise = noiseValue + noiseOctave * 0.2f;

            if (y > 37) {
                threshold = 1 - ((y - 37) / 3f);
            }
            else {
                threshold = y / 35f;
            }

            if ((double) globalX * globalX + (double) y * y + (double) globalZ * globalZ <= islandRadius * islandRadius * (0.5 + 0.5 * finalNoise) * threshold) {
                chunk.setBlockState(new BlockPos(globalX, y + 20 , globalZ), Blocks.END_STONE.defaultBlockState(), false);
            }
        }
    }

    private void generateFarlands(ChunkAccess chunk, int globalX, int globalZ) {

    }

    private void generateEndTerrain(ChunkAccess chunk, int globalX, int globalZ, float distanceFromOrigin, List<StructureStart> validStarts) {
        // Column-invariant values: resolve ONCE per (globalX, globalZ), reuse for all 159
        // y-levels. Previously these five Perlin lookups + two wrap calls ran inside the
        // y-loop via getTerrainDensity(int, int, int), paying 160× the necessary cost per
        // column (~40 k extra PerlinSimplexNoise evaluations per chunk in far-terrain).
        // baseThreshold is the same: getThreshold depends only on (globalX, globalZ,
        // distanceFromOrigin), all of which are fixed for this call.
        double horizontalBaseScale = getHorizontalBaseScale(globalX, globalZ);
        double verticalBaseScale = getVerticalBaseScale(globalX, globalZ);
        double cycleHeight = getCycleHeight(globalX, globalZ);
        int wrappedX = pingPongWrap(globalX, 0, 65536);
        int wrappedZ = pingPongWrap(globalZ, 0, 65536);
        double baseThreshold = getThreshold(globalX, globalZ, distanceFromOrigin);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int y = 1; y < 160; y++) {
            double structureAdaptation = calculateStructureAdaptation(validStarts, globalX, y, globalZ);

            double density = getTerrainDensity(y, horizontalBaseScale, verticalBaseScale, cycleHeight, wrappedX, wrappedZ);

            // Beardification: pull threshold down and push density up near structure bounding
            // boxes so the noise terrain extends to support them. Without this, structures
            // spawn floating with isolated patches of end_stone underneath where the un-
            // adapted noise happened to be solid. Reda referred to this as "beardification".
            double adaptedThreshold = baseThreshold - structureAdaptation * 0.15;
            double adaptedDensity = density + structureAdaptation * 0.1;

            if (adaptedDensity > adaptedThreshold) {
                chunk.setBlockState(mutable.set(globalX, y, globalZ), Blocks.END_STONE.defaultBlockState(), false);
            }
        }
    }

    private static double cyclicDensity(int y, double cycleHeight) {
        double normalizedY = (y % cycleHeight) / cycleHeight;
        if (normalizedY < 0.8) {
            return Math.sin((normalizedY / 0.8) * (Math.PI / 2));
        } else {
            return 1 - (normalizedY - 0.8) / 0.2;
        }
    }

    private static double edgeGradient(double y, double worldHeight, double noiseValue) {
        double gradientBottom = 1.0;
        double gradientTop = 1.0;

        if (y <= 64) {
            gradientBottom = (y - 32) / 32.0;
            if (y < 32) {
                gradientBottom = 0.0;
            }
        }

        if (y >= (worldHeight - 64)) {
            gradientTop = (worldHeight - y) / 64.0;
        }

        return gradientBottom * gradientTop * noiseValue;
    }

    private static double globalNoiseOffset(double min, double max, double x, double z, PerlinSimplexNoise noise) {
        double noiseValue = noise.getValue(x, z, false);
        return min + (max - min) * ((noiseValue + 1) / 2);
    }

    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        int globalX = pos.getX();
        int globalZ = pos.getZ();

        computeNoisesIfNotPresent(random);

        float distanceFromOrigin = (float) Math.sqrt((double) globalX * globalX + (double) globalZ * globalZ);
        double horizontalBaseScale = getHorizontalBaseScale(pos.getX(), pos.getZ());
        double verticalBaseScale = getVerticalBaseScale(pos.getX(), pos.getZ());
        double threshold = getThreshold(pos.getX(), pos.getZ(), distanceFromOrigin);
        double cycleHeight = getCycleHeight(pos.getX(), pos.getZ());
        double terrainNoise = getTerrainDensity(pos.getX(), pos.getY(), pos.getZ());
        double simplex = simplexNoise.getValue(pos.getX(), pos.getY(), pos.getZ());


        info.add("TerrainNoise T: " + decimalformat.format(terrainNoise) +
                " HS: " + decimalformat.format(horizontalBaseScale) +
                " VS: " + decimalformat.format(verticalBaseScale) +
                " Threshold: " + decimalformat.format(threshold) +
                " CH: " + decimalformat.format(cycleHeight));
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        computeNoisesIfNotPresent(randomState);
        return super.createState(structureSetLookup, randomState, seed);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        computeNoisesIfNotPresent(random);
        return super.getBaseColumn(x, z, height, random);
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
        computeNoisesIfNotPresent(random);
        super.buildSurface(level, structureManager, random, chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        // Auroracite floor placement is owned by AuroraciteLayerFeature (NeoForge biome modifier
        // on #the_beyond:has_auroracite_layer at raw_generation).
        // Protection from Stellarity's frozen biome ice overwrites is handled by
        // AuroraciteLayerProtectionMixin (vetoes ice/snow setBlock at minY/minY+1 in the End).

        // --- Exit-portal column exclusivity sweep ---------------------------------
        //
        // EndDragonFight.spawnExitPortal calls
        //   level.getHeightmapPos(MOTION_BLOCKING_NO_LEAVES, END_PODIUM_LOCATION)
        // with END_PODIUM_LOCATION == (0, 64, 0), then places the obsidian podium
        // at the returned Y. So the dragon-portal spawn depends entirely on the
        // MOTION_BLOCKING_NO_LEAVES heightmap at column (0,0) in chunk (0,0).
        //
        // Beyond-só (beyond_terrain pack, dim bounds 0..256): foreign End decorators
        // don't exist, so column (0,0)'s heightmap top is Beyond's END_STONE dome at
        // y≈59 (placed by generateMainIsland). Portal spawns at y=60 on Beyond's
        // island. Works correctly.
        //
        // Beyond+Enderscape combo (beyond_enderscape_bounds sidecar, dim bounds
        // -64..320): Beyond's chunk generator is still running and still places the
        // central island dome, but Enderscape (and other End mods keyed off Beyond's
        // biome tags on outer biomes which include vanilla End biomes) decorates
        // features at column (0,0) high-y — celestial islands, clutter, whatever
        // lands there. Those blocks push MOTION_BLOCKING_NO_LEAVES at (0,0) up to
        // ~build limit, so EndDragonFight.spawnExitPortal computes a y near 319 and
        // the obsidian podium plus return portal spawn at the top of the world.
        // Reported by Reda 2026-04-14.
        //
        // The fix: in the one chunk that owns the portal column, after vanilla/
        // foreign decoration has finished, surgically clear column (0,0) from just
        // above Beyond's dome (y=60) up to the dim top. ProtoChunk.setBlockState
        // updates the live heightmaps (POST_FEATURES status includes
        // MOTION_BLOCKING_NO_LEAVES), so the heightmap re-primes down to Beyond's
        // END_STONE at y≈59 exactly where EndDragonFight will query it.
        //
        // Scoped:
        //   * Chunk (0,0) only — the exact chunk that owns the portal column.
        //   * Column (0,0) only (1-block footprint) — Beyond's own
        //     jump_platform_island structure (absolute y=70..100, max_distance=1)
        //     spreads platforms across the central chunks but doesn't sit on the
        //     dead-center column; leaving every other column untouched guarantees
        //     Beyond's decorative platforms, bridges, bonfires, etc. survive.
        //   * Gated on BeyondTerrainState.isActive() — soup mode (player disabled
        //     beyond_terrain) leaves the foreign owner's column alone because in
        //     soup mode Beyond has no island dome to anchor the portal to.
        if (BeyondTerrainState.isActive()) {
            ChunkPos cp = chunk.getPos();
            if (cp.x == 0 && cp.z == 0) {
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                int top = chunk.getMaxBuildHeight() - 1;
                int floor = 60; // first y above Beyond's central island dome top at (0,0)
                net.minecraft.world.level.block.state.BlockState air = Blocks.AIR.defaultBlockState();
                for (int y = top; y >= floor; y--) {
                    mpos.set(0, y, 0);
                    if (!chunk.getBlockState(mpos).isAir()) {
                        chunk.setBlockState(mpos, air, false);
                    }
                }
            }
        }
    }

    /**
     * Sums the beardification adaptation contribution from every pre-filtered valid
     * structure start that overlaps the point {@code (x, y, z)}. The list is snapshotted
     * once per chunk in {@link #fillFromNoise} so the per-call overhead here is just an
     * array-backed for-loop over a short list (typically 0–4 entries).
     *
     * <p>Pre-2026-04-14 this iterated {@code chunk.getAllStarts().values()} with a
     * per-call {@code isValid()} check, and its caller threaded the result through a
     * {@code HashMap<Long, Double>} "cache" keyed by {@code (x, y, z)}. Because
     * {@code generateEndTerrain} visits each {@code (x, y, z)} exactly once per chunk,
     * every cache lookup was a guaranteed miss — 40 704 useless map ops and a stale
     * HashMap growing to the same size, per chunk. Removed.</p>
     */
    private double calculateStructureAdaptation(List<StructureStart> validStarts, int x, int y, int z) {
        if (validStarts.isEmpty()) return 0.0;

        double adaptation = 0.0;
        // Indexed loop over an ArrayList avoids Iterator allocation on the hot path.
        for (int i = 0, n = validStarts.size(); i < n; i++) {
            adaptation += calculateStructureInfluence(validStarts.get(i), x, y, z);
        }
        return adaptation;
    }

    private double calculateStructureInfluence(StructureStart start, int x, int y, int z) {
        BoundingBox bounds = start.getBoundingBox();

        int dx = Math.max(Math.max(bounds.minX() - x, x - bounds.maxX()), 0);
        int dy = Math.max(Math.max(bounds.minY() - y, y - bounds.maxY()), 0);
        int dz = Math.max(Math.max(bounds.minZ() - z, z - bounds.maxZ()), 0);

        int distance = dx + dy + dz;

        int influenceRadius = Math.max(bounds.getXSpan(), bounds.getZSpan()) * 2;

        if (distance < influenceRadius) {
            double normalized = 1.0 - ((double)distance / influenceRadius);
            return normalized * normalized * 0.3;
        }

        return 0.0;
    }
}