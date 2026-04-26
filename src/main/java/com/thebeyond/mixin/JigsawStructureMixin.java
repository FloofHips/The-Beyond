package com.thebeyond.mixin;

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

import java.util.List;
import java.util.Optional;

/**
 * Gates {@code the_beyond:*} jigsaw structures based on whether Beyond owns
 * the End this session. Scoped by {@code start_pool} namespace so other mods'
 * structures run untouched.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li><b>Beyond active, dimMinY == 0</b> — no-op (Beyond-só or Beyond with
 *       any non-Enderscape End mod). Vanilla findGenerationPoint runs.</li>
 *   <li><b>Beyond active, dimMinY != 0</b> — Beyond+Enderscape combo.
 *       Fountain relocated to {@code dimMinY+2} (its start_height=2 was
 *       calibrated to y=0 floor); every other Beyond structure no-op.</li>
 *   <li><b>Beyond inactive</b> — soup mode. Fountain relocated to {@code dimMinY+2}
 *       so it stays locatable on the fallback auroracite floor; all other
 *       Beyond structures canceled so they don't leak onto foreign terrain.</li>
 * </ul>
 *
 * Detection signal is {@link BeyondTerrainState#isActive()} (set from LEVEL_STEM
 * at ServerAboutToStartEvent), not {@code dimMinY} — the Enderscape sidecar
 * moves dimMinY to -64 while Beyond still owns terrain.
 *
 * <p>{@code startHeight} and {@code projectStartToHeightmap} are not shadowed
 * because the fountain override replaces them with hardcoded values.
 */
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
    private void the_beyond$gateBeyondStructuresInComboMode(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {

        // Scope to Beyond's pools only.
        ResourceKey<StructureTemplatePool> poolKey = this.startPool.unwrapKey().orElse(null);
        if (poolKey == null) {
            return;
        }
        ResourceLocation poolLoc = poolKey.location();
        if (!"the_beyond".equals(poolLoc.getNamespace())) {
            return;
        }

        boolean beyondActive = BeyondTerrainState.isActive();
        int dimMinY = context.heightAccessor().getMinBuildHeight();

        // Fast path: Beyond active and dim floor at y=0 (no Enderscape).
        if (beyondActive && dimMinY == 0) {
            return;
        }

        // Fountain always placed at dimMinY+2 with projection disabled so it
        // stays locatable on both the Enderscape-lowered auroracite floor
        // (Beyond+Enderscape combo) and the fallback auroracite floor (soup mode).
        if ("fountain/fountain".equals(poolLoc.getPath())) {
            ChunkPos chunkPos = context.chunkPos();
            BlockPos pos = new BlockPos(chunkPos.getMinBlockX(), dimMinY + 2, chunkPos.getMinBlockZ());

            Optional<Structure.GenerationStub> result = JigsawPlacement.addPieces(
                    context,
                    this.startPool,
                    this.startJigsawName,
                    this.maxDepth,
                    pos,
                    this.useExpansionHack,
                    Optional.empty(), // skip project_start_to_heightmap so Y stays at dimMinY+2
                    this.maxDistanceFromCenter,
                    PoolAliasLookup.create(this.poolAliases, pos, context.seed()),
                    this.dimensionPadding,
                    this.liquidSettings
            );

            cir.setReturnValue(result);
            return;
        }

        // Beyond+Enderscape combo: Beyond still owns terrain, so let every
        // other structure run on its configured start_height.
        if (beyondActive) {
            return;
        }

        // Soup mode: cancel the rest so they don't leak onto foreign terrain.
        cir.setReturnValue(Optional.empty());
    }
}
