package com.thebeyond.mixin.compat.stellarity;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

/**
 * Redirects {@code SpikeFeature.place() → placeSpike()} to use vanilla absolute-Y semantics
 * for Stellarity's ring spikes in the Beyond+BetterEnd+Stellarity combo.
 *
 * <p>BetterEnd's {@code be_placeSpike} HEAD inject replaces placement with surface-relative math,
 * breaking Stellarity's absolute-Y heights. A {@code @Redirect} on the call site in {@code place()}
 * bypasses BetterEnd's injector entirely — deterministic, unlike competing HEAD injects.</p>
 *
 * <p>Raw heights are looked up from a static table (not {@code spike.getHeight()}) because
 * BetterEnd's {@code EndSpikeMixin} also intercepts that getter.</p>
 *
 * <p>Center spike (0,0) is excluded — delegated to BetterEnd / event handler.</p>
 *
 * <p>Gated on: stellarity + betterend loaded, {@link BeyondTerrainState#isActive()}.
 * Falls through to original {@code placeSpike} when gates fail.</p>
 */
@Mixin(SpikeFeature.class)
public abstract class SpikeFeaturePlacementStellarityMixin {

    /** Raw heights from Stellarity's ring.json, keyed by {@code BlockPos.asLong(x, 0, z)}. Bypasses BetterEnd's getHeight() mixin. */
    private static final Map<Long, Integer> THE_BEYOND$RAW_HEIGHTS = new HashMap<>();

    static {
        int[][] data = {
                { 63,   0, 100}, { 50,  36, 105}, { 18,  59,  94}, {-19,  59, 106},
                {-51,  36, 105}, {-63,   0,  93}, {-51, -39, 100}, {-19, -60,  96},
                { 18, -60,  87}, { 50, -39,  95}
        };
        for (int[] d : data) {
            THE_BEYOND$RAW_HEIGHTS.put(BlockPos.asLong(d[0], 0, d[1]), d[2]);
        }
    }

    /** Returns raw height for a ring spike, falling back to {@code spike.getHeight()} if not in the table. */
    private static int theBeyond$getRawHeight(SpikeFeature.EndSpike spike) {
        Integer raw = THE_BEYOND$RAW_HEIGHTS.get(
                BlockPos.asLong(spike.getCenterX(), 0, spike.getCenterZ()));
        return raw != null ? raw : spike.getHeight();
    }

    @Shadow
    private void placeSpike(ServerLevelAccessor level, RandomSource random,
                            SpikeConfiguration config, SpikeFeature.EndSpike spike) {}

    /** Redirect: substitute vanilla absolute-Y placement for ring spikes when gates pass. */
    @Redirect(method = "place",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/levelgen/feature/SpikeFeature;placeSpike(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/SpikeConfiguration;Lnet/minecraft/world/level/levelgen/feature/SpikeFeature$EndSpike;)V"))
    private void theBeyond$redirectPlaceSpike(
            SpikeFeature instance,
            ServerLevelAccessor level,
            RandomSource random,
            SpikeConfiguration config,
            SpikeFeature.EndSpike spike) {
        if (!ModList.get().isLoaded("stellarity") || !ModList.get().isLoaded("betterend")) {
            placeSpike(level, random, config, spike);
            return;
        }
        if (!BeyondTerrainState.isActive()) {
            placeSpike(level, random, config, spike);
            return;
        }

        // Center spike (0,0) → delegate to BetterEnd; event handler places the marker crystal.
        if (spike.getCenterX() == 0 && spike.getCenterZ() == 0) {
            placeSpike(level, random, config, spike);
            return;
        }

        int rawHeight = theBeyond$getRawHeight(spike);
        TheBeyond.LOGGER.info(
                "[TheBeyond] Stellarity compat: placing spike at ({}, {}) with vanilla absolute-Y semantics (rawHeight={}, interceptedHeight={}, radius={}, crystal will be at y={})",
                spike.getCenterX(), spike.getCenterZ(), rawHeight, spike.getHeight(), spike.getRadius(), rawHeight + 1);

        theBeyond$vanillaPlaceSpike(level, random, config, spike);
    }

    /** Vanilla {@code SpikeFeature.placeSpike} (1.21.1) reimplementation using raw heights. */
    private void theBeyond$vanillaPlaceSpike(
            ServerLevelAccessor level,
            RandomSource random,
            SpikeConfiguration config,
            SpikeFeature.EndSpike spike) {
        int radius = spike.getRadius();
        int centerX = spike.getCenterX();
        int centerZ = spike.getCenterZ();
        int height = theBeyond$getRawHeight(spike);

        // Obsidian column + air clear above.
        for (BlockPos blockpos : BlockPos.betweenClosed(
                new BlockPos(centerX - radius, level.getMinBuildHeight(), centerZ - radius),
                new BlockPos(centerX + radius, height + 10, centerZ + radius))) {
            if (blockpos.distToLowCornerSqr((double) centerX, (double) blockpos.getY(), (double) centerZ)
                    <= (double) (radius * radius + 1)
                    && blockpos.getY() < height) {
                level.setBlock(blockpos, Blocks.OBSIDIAN.defaultBlockState(), 3);
            } else if (blockpos.getY() > 65) {
                level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // Iron-bars cage (all Stellarity ring spikes are unguarded, but kept for parity).
        if (spike.isGuarded()) {
            BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        boolean edgeX = Mth.abs(dx) == 2;
                        boolean edgeZ = Mth.abs(dz) == 2;
                        boolean top = dy == 3;
                        if (edgeX || edgeZ || top) {
                            boolean northSouth = dx == -2 || dx == 2 || top;
                            boolean eastWest = dz == -2 || dz == 2 || top;
                            BlockState bars = Blocks.IRON_BARS.defaultBlockState()
                                    .setValue(IronBarsBlock.NORTH, northSouth && dz != -2)
                                    .setValue(IronBarsBlock.SOUTH, northSouth && dz != 2)
                                    .setValue(IronBarsBlock.WEST, eastWest && dx != -2)
                                    .setValue(IronBarsBlock.EAST, eastWest && dx != 2);
                            level.setBlock(mut.set(centerX + dx, height + dy, centerZ + dz), bars, 3);
                        }
                    }
                }
            }
        }

        // Crystal + bedrock + fire at absolute Y.
        EndCrystal crystal = EntityType.END_CRYSTAL.create(level.getLevel());
        if (crystal != null) {
            crystal.setBeamTarget(config.getCrystalBeamTarget());
            crystal.setInvulnerable(config.isCrystalInvulnerable());
            crystal.moveTo(
                    (double) centerX + 0.5D,
                    (double) (height + 1),
                    (double) centerZ + 0.5D,
                    random.nextFloat() * 360.0F,
                    0.0F);
            level.addFreshEntity(crystal);
            BlockPos crystalPos = crystal.blockPosition();
            level.setBlock(crystalPos.below(), Blocks.BEDROCK.defaultBlockState(), 3);
            level.setBlock(crystalPos, FireBlock.getState(level, crystalPos), 3);
        }
    }
}
