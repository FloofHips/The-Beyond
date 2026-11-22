package com.thebeyond.common.worldgen;

import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.internal.worldgen.BeyondTerrainStateInternal;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/** Gate for the land-rate void draw: the biome's share of void cells, and every other void cell left as it was. */
class LandRateVoidBiomeTest {

    private static final long NOISE_SEED = 42L;
    private static final int DIM_MIN_Y = -64, DIM_MAX_Y = 320;
    private static final int SAMPLE_Y = DIM_MIN_Y + 200;
    private static final int STEP = 3000, LIMIT = 183000;
    private static final HolderOwner<Biome> OWNER = new HolderOwner<>() {};

    private final List<Holder<Biome>> land = List.of(biome("land_a"), biome("land_b"), biome("land_c"));
    private final Holder<Biome> rare = biome("rare_void");
    private final Holder<Biome> second = biome("second_void");
    private final List<Holder<Biome>> voids = List.of(rare, second, biome("third_void"));

    private HashSimplexNoise sS, sBS;
    private PerlinSimplexNoise sH, sV, sC;
    private BeyondTerrainParams sP;
    private long sSeed;
    private int sMin, sMax;

    @BeforeEach
    void setUp() {
        sS = BeyondEndChunkGenerator.simplexNoise; sBS = BeyondEndChunkGenerator.biomeSimplexNoise;
        sH = BeyondEndChunkGenerator.globalHOffsetNoise; sV = BeyondEndChunkGenerator.globalVOffsetNoise;
        sC = BeyondEndChunkGenerator.globalCOffsetNoise; sP = BeyondEndChunkGenerator.activeTerrainParams;
        sSeed = BeyondEndChunkGenerator.seed;
        sMin = BeyondTerrainState.getDimMinY(); sMax = BeyondTerrainState.getDimMaxY();
        BeyondEndChunkGenerator.simplexNoise = new HashSimplexNoise(RandomSource.create(NOISE_SEED));
        BeyondEndChunkGenerator.globalHOffsetNoise = new PerlinSimplexNoise(RandomSource.create(NOISE_SEED + 1), List.of(1));
        BeyondEndChunkGenerator.globalVOffsetNoise = new PerlinSimplexNoise(RandomSource.create(NOISE_SEED + 2), List.of(1));
        BeyondEndChunkGenerator.globalCOffsetNoise = new PerlinSimplexNoise(RandomSource.create(NOISE_SEED + 3), List.of(1));
        BeyondEndChunkGenerator.biomeSimplexNoise = new HashSimplexNoise(RandomSource.create(NOISE_SEED + 4));
        BeyondEndChunkGenerator.seed = NOISE_SEED;
        BeyondTerrainStateInternal.setDimBounds(DIM_MIN_Y, DIM_MAX_Y);
    }

    @AfterEach
    void tearDown() {
        BeyondEndChunkGenerator.simplexNoise = sS; BeyondEndChunkGenerator.biomeSimplexNoise = sBS;
        BeyondEndChunkGenerator.globalHOffsetNoise = sH; BeyondEndChunkGenerator.globalVOffsetNoise = sV;
        BeyondEndChunkGenerator.globalCOffsetNoise = sC; BeyondEndChunkGenerator.activeTerrainParams = sP;
        BeyondEndChunkGenerator.seed = sSeed;
        BeyondTerrainStateInternal.setDimBounds(sMin, sMax);
    }

    @Test
    void landRateBiomeTakesOneVoidCellInLandBiomesPlusOne() {
        BeyondEndBiomeSource plain = source(List.of());
        BeyondEndBiomeSource ruled = source(List.of(rare));
        int samples = 0, rareBefore = 0, rareAfter = 0;
        for (int x = STEP; x < LIMIT; x += STEP) {
            for (int z = STEP; z < LIMIT; z += STEP) {
                if (!isVoid(x, z)) continue;
                Holder<Biome> before = at(plain, x, z);
                Holder<Biome> after = at(ruled, x, z);
                samples++;
                if (before == rare) rareBefore++;
                if (after == rare) {
                    rareAfter++;
                    continue;
                }
                Assertions.assertTrue(voids.contains(after), "void cell at " + x + " " + z + " got a land biome");
                if (before != rare) Assertions.assertSame(before, after, "cell at " + x + " " + z + " changed without being the rare biome's");
            }
        }
        Assertions.assertTrue(samples >= 1500, "liveness: only " + samples + " void samples");
        Assertions.assertEquals(1.0 / 3, rareBefore / (double) samples, 0.04, "liveness: plain pool share of " + samples);
        // three land biomes, so the land-rate biome takes one void cell in four
        Assertions.assertEquals(1.0 / 4, rareAfter / (double) samples, 0.04, "land-rate share of " + samples);
    }

    @Test
    void twoLandRateBiomesTakeOneVoidCellEachInLandBiomesPlusTwo() {
        BeyondEndBiomeSource ruled = source(List.of(rare, second));
        int samples = 0, first = 0, other = 0;
        for (int x = STEP; x < LIMIT; x += STEP) {
            for (int z = STEP; z < LIMIT; z += STEP) {
                if (!isVoid(x, z)) continue;
                Holder<Biome> biome = at(ruled, x, z);
                samples++;
                if (biome == rare) first++;
                if (biome == second) other++;
            }
        }
        Assertions.assertTrue(samples >= 1500, "liveness: only " + samples + " void samples");
        Assertions.assertEquals(1.0 / 5, first / (double) samples, 0.04, "first land-rate share of " + samples);
        Assertions.assertEquals(1.0 / 5, other / (double) samples, 0.04, "second land-rate share of " + samples);
    }

    @Test
    void disabledLandRateBiomeGetsNoCell() {
        BeyondEndBiomeSource ruled = source(List.of(rare));
        ruled.disableBiomes(List.of(rare.unwrapKey().orElseThrow()));
        int samples = 0;
        for (int x = STEP; x < LIMIT; x += STEP) {
            for (int z = STEP; z < LIMIT; z += STEP) {
                if (!isVoid(x, z)) continue;
                samples++;
                Assertions.assertNotSame(rare, at(ruled, x, z), "disabled biome at " + x + " " + z);
            }
        }
        Assertions.assertTrue(samples >= 1500, "liveness: only " + samples + " void samples");
    }

    private static boolean isVoid(int x, int z) {
        return BeyondEndChunkGenerator.getTerrainDensity(x, SAMPLE_Y, z) < 0.01f;
    }

    private static Holder<Biome> at(BeyondEndBiomeSource source, int x, int z) {
        return source.getNoiseBiome(x >> 2, SAMPLE_Y >> 2, z >> 2, null);
    }

    private BeyondEndBiomeSource source(List<Holder<Biome>> landRate) {
        return new BeyondEndBiomeSource(land, List.of(), Optional.empty(), voids, List.of(), landRate,
                biome("center"), biome("bottom"), BeyondTerrainParams.DEFAULTS);
    }

    private static Holder<Biome> biome(String name) {
        return Holder.Reference.createStandAlone(OWNER, ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("test", name)));
    }
}
