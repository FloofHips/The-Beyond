package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thebeyond.api.worldgen.BeyondTerrain;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wraps the heightmap projection in {@code JigsawPlacement.addPieces} so End jigsaws with
 *  {@code project_start_to_heightmap} distribute across pancakes, not just the column top.
 *  Beyond's own jigsaws bypass this via {@code JigsawStructureMixin}. */
@Mixin(JigsawPlacement.class)
public abstract class JigsawPlacementMixin {
    private static final TagKey<Biome> PRISTINE_TAG = TagKey.create(
            Registries.BIOME, ResourceLocation.withDefaultNamespace("is_end"));
    private static final long SEED_P1 = 341873128712L;
    private static final long SEED_P2 = 132897987541L;

    @WrapOperation(
        method = "addPieces",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getFirstFreeHeight(IILnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)I")
    )
    private static int the_beyond$randomPancakeForJigsaw(
            ChunkGenerator gen, int x, int z, Heightmap.Types type,
            LevelHeightAccessor level, RandomState random,
            Operation<Integer> op) {
        int original = op.call(gen, x, z, type, level, random);
        if (!BeyondTerrainState.isActive()) return original;
        int result = pickRandomEndPancakeY(gen, x, z, level, random, original);
        return result;
    }

    private static int pickRandomEndPancakeY(ChunkGenerator gen, int x, int z,
                                              LevelHeightAccessor level, RandomState rs, int fallback) {
        // Fires for every dimension's jigsaws (isActive() is world-global), but
        // streamPancakeTops touches End-only static noise (NPE off-End), so reroute
        // only the Beyond End generator; other dimensions keep vanilla.
        if (!(gen instanceof BeyondEndChunkGenerator beg)) return fallback;
        try {
            beg.computeNoisesIfNotPresent(rs);   // prime, as getBaseHeight/getFirstFreeHeight do
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            int[] tops = new int[32];
            int count = 0;
            // Scan Beyond's real terrain via streamPancakeTops, not the vanilla noise
            // column — that is phantom End geometry and lands jigsaws on non-existent
            // islands instead of real pancakes (incl. negative Y).
            java.util.PrimitiveIterator.OfInt it =
                    BeyondTerrain.streamPancakeTops(x, z, minY, maxY).iterator();
            while (it.hasNext() && count < tops.length) {
                int topY = it.nextInt();
                Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(
                        x >> 2, topY >> 2, z >> 2, rs.sampler());
                if (biome.is(PRISTINE_TAG)) {
                    tops[count++] = topY;
                }
            }
            if (count == 0) return fallback;
            long seed = ((long) (x >> 4)) * SEED_P1 ^ ((long) (z >> 4)) * SEED_P2;
            return tops[(int) Math.floorMod(seed, count)];
        } catch (Throwable t) {
            return fallback;   // never crash worldgen
        }
    }
}
