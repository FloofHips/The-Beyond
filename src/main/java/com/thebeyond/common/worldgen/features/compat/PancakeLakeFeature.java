package com.thebeyond.common.worldgen.features.compat;

import com.mojang.serialization.Codec;
import com.thebeyond.TheBeyond;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Carves a simplex-perturbed ellipsoid lake sized to the host pancake (probed at runtime)
 *  so it never exceeds the island. Leaves rare endstone mounds inside and a shore ring. */
public class PancakeLakeFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState END_STONE = Blocks.END_STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Vertical scan limit when probing pancake bounds. */
    private static final int MAX_VERTICAL_PROBE = 80;
    /** Horizontal scan limit (per direction) when probing pancake horizontal extent. */
    private static final int MAX_HORIZONTAL_PROBE = 64;
    /** Minimum pancake dimensions to bother carving a lake. */
    private static final int MIN_THICKNESS = 3;
    private static final int MIN_HORIZONTAL_RADIUS = 4;
    /** Lake sizing relative to pancake: depth uses fraction of thickness, radius of horizontal extent. */
    private static final float DEPTH_FRACTION = 0.5f;
    private static final float RADIUS_FRACTION = 0.75f;
    /** Carving caps. */
    private static final float MAX_DEPTH = 12f;
    private static final float MAX_RADIUS = 40f;
    /** Edge noise amplitude as fraction of radius (e.g. 0.2 = ±20% radius wobble). */
    private static final float EDGE_NOISE_AMPLITUDE = 0.2f;
    private static final boolean LOG_PLACEMENT = false;

    public PancakeLakeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource random = ctx.random();

        int x = origin.getX();
        int z = origin.getZ();
        int y = origin.getY();

        if (isInsideExistingLake(level, x, y, z)) {
            return false;
        }

        int surfaceY = findPancakeTop(level, x, y, z);
        if (surfaceY == Integer.MIN_VALUE) {
            if (LOG_PLACEMENT) TheBeyond.LOGGER.info("[PancakeLake] bail at ({},{},{}): no surface", x, y, z);
            return false;
        }

        int bottomY = findPancakeBottom(level, x, surfaceY, z);
        int thickness = surfaceY - bottomY + 1;
        if (thickness < MIN_THICKNESS) {
            if (LOG_PLACEMENT) TheBeyond.LOGGER.info("[PancakeLake] bail at ({},{},{}): thickness {} < {}", x, surfaceY, z, thickness, MIN_THICKNESS);
            return false;
        }

        int horizontalExtent = probeHorizontal(level, x, surfaceY, z);
        if (horizontalExtent < MIN_HORIZONTAL_RADIUS) {
            if (LOG_PLACEMENT) TheBeyond.LOGGER.info("[PancakeLake] bail at ({},{},{}): horizontalExtent {} < {}", x, surfaceY, z, horizontalExtent, MIN_HORIZONTAL_RADIUS);
            return false;
        }

        float radius = Math.min(horizontalExtent * RADIUS_FRACTION, MAX_RADIUS);
        float depth = Math.min(thickness * DEPTH_FRACTION, MAX_DEPTH);
        if (radius < MIN_HORIZONTAL_RADIUS || depth < 2f) {
            if (LOG_PLACEMENT) TheBeyond.LOGGER.info("[PancakeLake] bail at ({},{},{}): r={} d={}", x, surfaceY, z, radius, depth);
            return false;
        }

        // Center placed slightly below surface so water rises to surface level.
        int cy = surfaceY - 1;
        carveLake(level, random, x, cy, z, radius, depth, bottomY);
        if (LOG_PLACEMENT) TheBeyond.LOGGER.info("[PancakeLake] placed at ({},{},{}) r={} d={} extent={} thick={}", x, cy, z, radius, depth, horizontalExtent, thickness);
        return true;
    }

    /** Probes ±4 Y and 4 cardinal neighbours for water — any hit means another lake
     *  already claimed this pancake area. */
    private static boolean isInsideExistingLake(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 4; dy++) {
            pos.set(x, y + dy, z);
            if (!level.getBlockState(pos).getFluidState().isEmpty()) return true;
        }
        int[] dx = {3, -3, 0, 0};
        int[] dz = {0, 0, 3, -3};
        for (int i = 0; i < 4; i++) {
            pos.set(x + dx[i], y, z + dz[i]);
            if (!level.getBlockState(pos).getFluidState().isEmpty()) return true;
            pos.set(x + dx[i], y - 1, z + dz[i]);
            if (!level.getBlockState(pos).getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** Topmost solid Y of the pancake containing {@code yHint}. If yHint is in air, walks
     *  down first to find the pancake, then walks up. {@code MIN_VALUE} if none. */
    private static int findPancakeTop(WorldGenLevel level, int x, int yHint, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, yHint, z);
        if (level.getBlockState(pos).isAir()) {
            for (int i = 0; i < MAX_VERTICAL_PROBE; i++) {
                pos.setY(yHint - i);
                if (!level.getBlockState(pos).isAir()) {
                    yHint = pos.getY();
                    break;
                }
            }
            if (level.getBlockState(pos).isAir()) return Integer.MIN_VALUE;
        }
        int top = yHint;
        for (int i = 1; i <= MAX_VERTICAL_PROBE; i++) {
            pos.setY(yHint + i);
            if (level.getBlockState(pos).isAir()) break;
            top = pos.getY();
        }
        return top;
    }

    /** Walks down from surfaceY until air; returns the lowest solid y. */
    private static int findPancakeBottom(WorldGenLevel level, int x, int surfaceY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surfaceY, z);
        int bottom = surfaceY;
        for (int i = 1; i <= MAX_VERTICAL_PROBE; i++) {
            pos.setY(surfaceY - i);
            if (level.getBlockState(pos).isAir()) break;
            bottom = pos.getY();
        }
        return bottom;
    }

    /** Min over 4 cardinal directions of contiguous-solid run length at {@code cy}. */
    private static int probeHorizontal(WorldGenLevel level, int cx, int cy, int cz) {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minExtent = MAX_HORIZONTAL_PROBE;
        for (int dir = 0; dir < 4; dir++) {
            int extent = 0;
            for (int i = 1; i <= MAX_HORIZONTAL_PROBE; i++) {
                pos.set(cx + dx[dir] * i, cy, cz + dz[dir] * i);
                if (level.getBlockState(pos).isAir()) break;
                extent = i;
            }
            if (extent < minExtent) minExtent = extent;
        }
        return minExtent;
    }

    /** Carves an ellipsoid bowl with simplex-distorted edges; sprinkles endstone mounds
     *  inside as tree platforms; never carves below {@code minBottomY}. */
    private static void carveLake(WorldGenLevel level, RandomSource random,
                                  int cx, int cy, int cz, float radius, float depth, int minBottomY) {
        SimplexNoise edgeNoise = new SimplexNoise(random);
        float aspect = radius / depth;
        int rInt = Mth.ceil(radius + radius * EDGE_NOISE_AMPLITUDE);
        int dInt = Mth.ceil(depth);
        float aspect2 = aspect * aspect;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ox = -rInt; ox <= rInt; ox++) {
            for (int oz = -rInt; oz <= rInt; oz++) {
                double noise = edgeNoise.getValue(ox * 0.12, oz * 0.12);
                float effectiveR = radius + (float) noise * radius * EDGE_NOISE_AMPLITUDE;
                float effectiveR2 = effectiveR * effectiveR;
                double horizontalDist = ox * ox + oz * oz;

                for (int oy = -dInt; oy <= dInt; oy++) {
                    int wy = cy + oy;
                    if (wy < minBottomY + 1) continue;
                    pos.set(cx + ox, wy, cz + oz);

                    double dist = horizontalDist + (oy * oy) * aspect2;
                    if (dist >= effectiveR2) continue;

                    BlockState current = level.getBlockState(pos);
                    if (!current.getFluidState().isEmpty()) continue;
                    if (current.is(Blocks.BEDROCK)) continue;

                    if (oy < 0) {
                        if (oy >= -2 && random.nextInt(40) == 0 && dist < effectiveR2 * 0.4) {
                            level.setBlock(pos, END_STONE, 2);
                        } else {
                            level.setBlock(pos, WATER, 2);
                        }
                    } else if (oy == 0) {
                        if (dist > effectiveR2 * 0.6 && random.nextInt(8) == 0) {
                            level.setBlock(pos, END_STONE, 2);
                        } else {
                            level.setBlock(pos, WATER, 2);
                        }
                    } else {
                        level.setBlock(pos, AIR, 2);
                    }
                }
            }
        }

        // Promote the lakebed top to end_stone so vegetation features whose
        // block_predicate_filters expect END_STONES find a recognised surface.
        for (int ox = -rInt; ox <= rInt; ox++) {
            for (int oz = -rInt; oz <= rInt; oz++) {
                // Walk down to the lakebed top.
                for (int oy = 0; oy >= -dInt - 2; oy--) {
                    int wy = cy + oy;
                    if (wy < minBottomY) break;
                    pos.set(cx + ox, wy, cz + oz);
                    BlockState s = level.getBlockState(pos);
                    if (!s.getFluidState().isEmpty()) continue;
                    if (s.isAir()) continue;
                    if (s.is(Blocks.BEDROCK)) break;
                    if (!s.is(Blocks.END_STONE)) {
                        level.setBlock(pos, END_STONE, 2);
                    }
                    break;
                }
            }
        }

        // Wall containment: water adjacent to void becomes end_stone so water doesn't leak
        // past pancake edges. Iterated so newly-placed end_stone reveals downstream leaks.
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        int[] sideDx = {1, -1, 0, 0, 0};
        int[] sideDy = {0, 0, 0, 0, -1};
        int[] sideDz = {0, 0, 1, -1, 0};
        for (int pass = 0; pass < 3; pass++) {
            for (int ox = -rInt; ox <= rInt; ox++) {
                for (int oz = -rInt; oz <= rInt; oz++) {
                    for (int oy = -dInt; oy <= 0; oy++) {
                        int wy = cy + oy;
                        if (wy < minBottomY) continue;
                        pos.set(cx + ox, wy, cz + oz);
                        BlockState s = level.getBlockState(pos);
                        if (!s.is(Blocks.WATER)) continue;
                        boolean leak = false;
                        for (int i = 0; i < 5; i++) {
                            neighbour.set(cx + ox + sideDx[i], wy + sideDy[i], cz + oz + sideDz[i]);
                            if (level.getBlockState(neighbour).isAir()) { leak = true; break; }
                        }
                        if (leak) level.setBlock(pos, END_STONE, 2);
                    }
                }
            }
        }

        // Clear floating land features above the lake (trees, grass, terrestrial plants).
        // Fluid/waterlogged blocks and lily pads are preserved.
        for (int ox = -rInt; ox <= rInt; ox++) {
            for (int oz = -rInt; oz <= rInt; oz++) {
                double noise = edgeNoise.getValue(ox * 0.12, oz * 0.12);
                float effectiveR = radius + (float) noise * radius * EDGE_NOISE_AMPLITUDE;
                float effectiveR2 = effectiveR * effectiveR;
                double horizontalDist = ox * ox + oz * oz;
                if (horizontalDist >= effectiveR2) continue;
                for (int oy = 1; oy <= 12; oy++) {
                    int wy = cy + oy;
                    pos.set(cx + ox, wy, cz + oz);
                    BlockState s = level.getBlockState(pos);
                    if (s.isAir()) continue;
                    if (!s.getFluidState().isEmpty()) continue;
                    if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                            && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) continue;
                    if (s.getBlock() == Blocks.LILY_PAD) continue;
                    if (s.is(net.minecraft.tags.BlockTags.LOGS)
                            || s.is(net.minecraft.tags.BlockTags.LEAVES)
                            || s.is(net.minecraft.tags.BlockTags.SAPLINGS)
                            || s.is(net.minecraft.tags.BlockTags.FLOWERS)
                            || s.is(net.minecraft.tags.BlockTags.SMALL_FLOWERS)
                            || s.is(net.minecraft.tags.BlockTags.TALL_FLOWERS)
                            || s.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES)
                            || s.is(net.minecraft.tags.BlockTags.REPLACEABLE)) {
                        level.setBlock(pos, AIR, 2);
                    }
                }
            }
        }
    }
}
