package com.thebeyond.common.worldgen.features.compat;

import com.mojang.serialization.Codec;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Per-pancake port of BetterEnd's {@code CrystalMountainPiece.postProcess}. Height bonus
 *  is biome-span-relative (highest Crystal-Mountains pancake in the column = full +36
 *  bonus, lowest = 0); cluster count is reduced, centers are spaced, tips capped to the
 *  next solid block above so pillars don't pierce overhead pancakes. */
public class AuroraCrystalClusterFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Biome> CRYSTAL_MOUNTAINS_TAG = TagKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("betterend", "has_structure/mountain"));
    private static final int BIOME_CELL = 4;
    private static final ResourceLocation AURORA_CRYSTAL_ID =
            ResourceLocation.fromNamespaceAndPath("betterend", "aurora_crystal");
    private static final int MIN_CLUSTER_SPACING_SQ = 25; // 5 blocks min between cluster centers
    private static final int CEILING_MARGIN = 3;          // never tip closer than 3 to pancake above

    private static volatile BlockState auroraCrystalState;

    public AuroraCrystalClusterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    private static BlockState getAuroraCrystal() {
        BlockState cached = auroraCrystalState;
        if (cached != null) return cached;
        Block block = BuiltInRegistries.BLOCK.get(AURORA_CRYSTAL_ID);
        if (block == BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("air"))) {
            return null;
        }
        cached = block.defaultBlockState();
        auroraCrystalState = cached;
        return cached;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        BlockState crystal = getAuroraCrystal();
        if (crystal == null) return false;

        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        BlockState below = level.getBlockState(origin.below());
        if (below.isAir() || !below.isSolid()) return false;

        // relativeY = origin's fraction inside the biome's Y span at this column.
        int[] biomeRange = findCrystalMountainsRangeAtColumn(level, origin);
        float relativeY;
        if (biomeRange == null || biomeRange[0] == biomeRange[1]) {
            relativeY = 1f;
        } else {
            relativeY = Mth.clamp(
                    (origin.getY() - biomeRange[0]) / (float) (biomeRange[1] - biomeRange[0]),
                    0f, 1f);
        }
        float heightBonus = relativeY * 36f;
        int bigCount = 1 + Math.round(relativeY * 2f);
        int smallCount = 1 + Math.round(relativeY * 3f);

        int dimMaxY = BeyondTerrainState.isActive()
                ? BeyondTerrainState.getDimMaxY() : level.getMaxBuildHeight();
        int ceilingAbove = findNextSolidAbove(level, origin.getX(), origin.getY(), origin.getZ(), dimMaxY);
        int maxAllowedTipY = ceilingAbove - CEILING_MARGIN;

        int chunkOriginX = (origin.getX() >> 4) << 4;
        int chunkOriginZ = (origin.getZ() >> 4) << 4;
        List<BlockPos> placedCenters = new ArrayList<>(bigCount + smallCount);

        for (int i = 0; i < bigCount; i++) {
            int radius = 2 + random.nextInt(2);
            float fill = random.nextFloat();
            BlockPos pos = sampleSpacedPosition(chunkOriginX, chunkOriginZ, origin.getY(),
                    radius, level, random, placedCenters);
            if (pos == null) continue;
            placedCenters.add(pos);
            int crystalHeight = computeCrystalHeight(radius, random, heightBonus, pos.getY(), maxAllowedTipY);
            if (crystalHeight < 2) continue;
            placeCrystal(level, pos, radius, crystalHeight, fill, random, crystal);
        }

        for (int i = 0; i < smallCount; i++) {
            int radius = 1 + random.nextInt(2);
            float fill = random.nextBoolean() ? 0f : 1f;
            BlockPos pos = sampleSpacedPosition(chunkOriginX, chunkOriginZ, origin.getY(),
                    radius, level, random, placedCenters);
            if (pos == null) continue;
            placedCenters.add(pos);
            int crystalHeight = computeCrystalHeight(radius, random, heightBonus, pos.getY(), maxAllowedTipY);
            if (crystalHeight < 2) continue;
            placeCrystal(level, pos, radius, crystalHeight, fill, random, crystal);
        }

        return !placedCenters.isEmpty();
    }

    private static int computeCrystalHeight(int radius, RandomSource random, float heightBonus,
                                             int posY, int maxAllowedTipY) {
        int wanted = Mth.floor(radius * (1.5f + random.nextFloat() * 1.5f) + heightBonus);
        int maxAllowed = maxAllowedTipY - posY;
        return Math.min(wanted, maxAllowed);
    }

    /** Chunk-local (x, z) at least {@link #MIN_CLUSTER_SPACING_SQ} from every placed center,
     *  with a solid block below. 6 attempts; {@code null} if none satisfy. */
    private static BlockPos sampleSpacedPosition(int chunkOriginX, int chunkOriginZ, int posY,
                                                  int radius, WorldGenLevel level,
                                                  RandomSource random, List<BlockPos> placedCenters) {
        for (int attempt = 0; attempt < 6; attempt++) {
            int localX = radius + random.nextInt(16 - 2 * radius);
            int localZ = radius + random.nextInt(16 - 2 * radius);
            BlockPos pos = new BlockPos(chunkOriginX + localX, posY, chunkOriginZ + localZ);
            boolean tooClose = false;
            for (BlockPos other : placedCenters) {
                if (pos.distSqr(other) < MIN_CLUSTER_SPACING_SQ) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;
            BlockState localBelow = level.getBlockState(pos.below());
            if (localBelow.isAir() || !localBelow.isSolid()) continue;
            return pos;
        }
        return null;
    }

    /** Tilted diamond pillar buried 3-7 blocks into the surface (port of BetterEnd's
     *  {@code CrystalMountainPiece.crystal}). */
    private void placeCrystal(WorldGenLevel level, BlockPos pos, int radius, int height,
                              float fill, RandomSource random, BlockState crystal) {
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int max = Mth.floor(fill * radius + radius + 0.5F);
        int absoluteTipY = height + pos.getY();
        int coefX = random.nextInt(3) - 1;
        int coefZ = random.nextInt(3) - 1;
        for (int x = -radius; x <= radius; x++) {
            int worldX = x + pos.getX();
            int ax = Math.abs(x);
            for (int z = -radius; z <= radius; z++) {
                int worldZ = z + pos.getZ();
                int az = Math.abs(z);
                if (ax + az < max) {
                    int minY = pos.getY() - (3 + random.nextInt(5));
                    int h = coefX * x + coefZ * z + absoluteTipY;
                    for (int y = minY; y < h; y++) {
                        mut.set(worldX, y, worldZ);
                        level.setBlock(mut, crystal, 2);
                    }
                }
            }
        }
    }

    /** Returns the lowest Y > {@code yStart} that contains a non-air block; {@code yMax} if none. */
    private static int findNextSolidAbove(WorldGenLevel level, int x, int yStart, int z, int yMax) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = yStart + 1; y < yMax; y++) {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir()) return y;
        }
        return yMax;
    }

    private static int[] findCrystalMountainsRangeAtColumn(WorldGenLevel level, BlockPos origin) {
        int dimMinY = BeyondTerrainState.isActive()
                ? BeyondTerrainState.getDimMinY() : level.getMinBuildHeight();
        int dimMaxY = BeyondTerrainState.isActive()
                ? BeyondTerrainState.getDimMaxY() : level.getMaxBuildHeight();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(origin.getX(), 0, origin.getZ());
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int y = dimMinY; y < dimMaxY; y += BIOME_CELL) {
            probe.setY(y);
            Holder<Biome> biome = level.getBiome(probe);
            if (biome.is(CRYSTAL_MOUNTAINS_TAG)) {
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        return minY == Integer.MAX_VALUE ? null : new int[]{minY, maxY};
    }
}
