package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Reroutes {@code the_beyond:*} jigsaw Y: fountain → dim floor, others → random pancake top.
 *  Soup mode cancels all except fountain to keep them off foreign terrain. */
@Mixin(JigsawStructure.class)
public abstract class JigsawStructureMixin {

    @Shadow @Final private Holder<StructureTemplatePool> startPool;
    @Shadow @Final private Optional<ResourceLocation> startJigsawName;
    @Shadow @Final private int maxDepth;
    @Shadow @Final private boolean useExpansionHack;
    @Shadow @Final private int maxDistanceFromCenter;
    @Shadow @Final private List<PoolAliasBinding> poolAliases;
    @Shadow @Final private DimensionPadding dimensionPadding;
    @Shadow @Final private LiquidSettings liquidSettings;

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void the_beyond$rerouteBeyondStructures(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {

        ResourceKey<StructureTemplatePool> poolKey = this.startPool.unwrapKey().orElse(null);
        if (poolKey == null) return;
        ResourceLocation poolLoc = poolKey.location();
        if (!"the_beyond".equals(poolLoc.getNamespace())) return;

        boolean beyondActive = BeyondTerrainState.isActive();
        int dimMinY = context.heightAccessor().getMinBuildHeight();
        String path = poolLoc.getPath();
        ChunkPos chunkPos = context.chunkPos();

        // Fountain: dim floor anchor across all modes (Beyond-só, Beyond+combo, soup).
        if ("fountain/fountain".equals(path)) {
            BlockPos pos = new BlockPos(chunkPos.getMinBlockX(), dimMinY + 2, chunkPos.getMinBlockZ());
            cir.setReturnValue(the_beyond$addPiecesAt(context, pos));
            return;
        }

        if (!beyondActive) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        // jump_platform: floats midpoint between pancakes (small footprint waypoint).
        if ("misc/jump_platform".equals(path)) {
            int centerX = chunkPos.getMinBlockX();
            int centerZ = chunkPos.getMinBlockZ();
            int dimMaxY = context.heightAccessor().getMaxBuildHeight() - 1;

            List<Integer> floatYs = the_beyond$floatingPlatformYs(centerX, centerZ, dimMinY, dimMaxY);
            if (floatYs.isEmpty()) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            long mix = ChunkPos.asLong(chunkPos.x, chunkPos.z) ^ context.seed();
            int chosenY = floatYs.get(new Random(mix).nextInt(floatYs.size()));
            cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(centerX, chosenY, centerZ)));
            return;
        }

        // bridge: anchors above the highest pancake within its radial reach (max_distance 116),
        // so the deck and the descending pillar chain (size 20: ~62-block worst case) never touch islands.
        if (path.startsWith("bridge/")) {
            int centerX = chunkPos.getMinBlockX();
            int centerZ = chunkPos.getMinBlockZ();
            int dimMaxY = context.heightAccessor().getMaxBuildHeight() - 1;

            int highest = Integer.MIN_VALUE;
            int[][] offsets = {{0,0},{96,0},{-96,0},{0,96},{0,-96},{64,64},{-64,-64},{64,-64},{-64,64}};
            for (int[] o : offsets) {
                List<Integer> ts = the_beyond$pancakeTops(centerX + o[0], centerZ + o[1], dimMinY, dimMaxY);
                if (!ts.isEmpty() && ts.get(0) > highest) highest = ts.get(0);
            }
            if (highest == Integer.MIN_VALUE) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            int chosenY = highest + 40;
            if (chosenY + 30 > dimMaxY) {
                cir.setReturnValue(Optional.empty());
                return;
            }
            cir.setReturnValue(the_beyond$addPiecesAt(context, new BlockPos(centerX, chosenY, centerZ)));
            return;
        }

        // Pancake-aware Y picker — landmarks must land on a real pancake, distributed across
        // all layers (not just topmost). Different chunks pick different pancakes by salt.
        if (path.startsWith("bonfire/") || path.startsWith("aberrant_remains/") || "misc/arch".equals(path)) {
            int centerX = chunkPos.getMinBlockX();
            int centerZ = chunkPos.getMinBlockZ();
            int dimMaxY = context.heightAccessor().getMaxBuildHeight() - 1;

            List<Integer> tops = the_beyond$pancakeTops(centerX, centerZ, dimMinY, dimMaxY);
            if (tops.isEmpty()) {
                cir.setReturnValue(Optional.empty());
                return;
            }

            long mix = ChunkPos.asLong(chunkPos.x, chunkPos.z) ^ context.seed();
            int chosenY = tops.get(new Random(mix).nextInt(tops.size()));
            BlockPos pos = new BlockPos(centerX, chosenY, centerZ);
            cir.setReturnValue(the_beyond$addPiecesAt(context, pos));
            return;
        }
    }

    private Optional<Structure.GenerationStub> the_beyond$addPiecesAt(
            Structure.GenerationContext context, BlockPos pos) {
        return JigsawPlacement.addPieces(
                context, this.startPool, this.startJigsawName, this.maxDepth,
                pos, this.useExpansionHack,
                Optional.empty(),
                this.maxDistanceFromCenter,
                PoolAliasLookup.create(this.poolAliases, pos, context.seed()),
                this.dimensionPadding,
                this.liquidSettings
        );
    }

    private static List<Integer> the_beyond$floatingPlatformYs(int x, int z, int minY, int maxY) {
        List<Integer> tops = the_beyond$pancakeTops(x, z, minY, maxY);
        if (tops.isEmpty()) return tops;
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < tops.size(); i++) {
            int currTop = tops.get(i);
            int upperLimit = (i > 0) ? tops.get(i - 1) : Math.min(maxY, currTop + 30);
            int gap = upperLimit - currTop;
            if (gap >= 8) result.add(currTop + gap / 2);
        }
        return result;
    }

    private static List<Integer> the_beyond$pancakeTops(int x, int z, int minY, int maxY) {
        List<Integer> tops = new ArrayList<>();
        float distance = (float) Math.sqrt((double) (x * x + z * z));
        BeyondEndChunkGenerator.ColumnScratch scratch = BeyondEndChunkGenerator.getColumnScratch();
        BeyondEndChunkGenerator.initColumnScratch(x, z, distance, scratch);
        boolean prevSolid = false;
        for (int y = maxY; y >= minY; y--) {
            boolean solid = BeyondEndChunkGenerator.isSolidTerrainScratch(y, scratch);
            if (!prevSolid && solid) tops.add(y + 1);
            prevSolid = solid;
        }
        return tops;
    }
}
