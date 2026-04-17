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
 * Gates all {@code the_beyond:*} jigsaw structures based on whether Beyond's chunk generator
 * actually owns the End this session. When Beyond does NOT own the End (the player opted out
 * of the {@code beyond_terrain} datapack and a foreign generator is producing the terrain),
 * every Beyond jigsaw structure except {@code fountain/fountain} is canceled so it doesn't
 * leak onto foreign terrain.
 *
 * <h2>Why this is needed</h2>
 *
 * Beyond's structures use {@code project_start_to_heightmap = MOTION_BLOCKING} on top of a
 * low {@code start_height.absolute} (or a {@code uniform} range calibrated to Beyond's
 * island heights). This assumes Beyond's chunk generator is producing its central island
 * at Y=20–60, outer FBM noise terrain, and a deterministic auroracite floor at the dim
 * {@code min_y}.
 *
 * <p>In "soup" mode (Beyond datapack disabled), a foreign generator produces floating islands
 * at ~Y=50–70 and empty void chunks. Beyond's biome tags ({@code has_remains},
 * {@code has_bridges}, {@code has_sparse_platforms}, {@code has_platforms}) include vanilla
 * End biomes by design — they're there because Beyond's own tainted_end pool mixes vanilla
 * biomes into outer FBM chunks. But those same vanilla biomes are exactly what BetterX's
 * pickers fall back to, so in soup mode the structures become eligible on foreign terrain
 * and land either on random BetterX island peaks or on the auroracite floor in void chunks
 * (now visible thanks to the auroracite biome_modifier in {@code #wover:is_end/*} tags).
 *
 * <h2>Detection signal</h2>
 *
 * <p>Pre-2026-04-14 this mixin used {@code dimMinY == 0} as a proxy for "Beyond chunk gen is
 * running here" — the idea being that Beyond's {@code beyond_terrain} subpack forces
 * {@code min_y=0} while foreign End replacements ship {@code min_y=-64}. That proxy broke
 * in the Beyond+Enderscape combo: Beyond's {@code beyond_enderscape_bounds} sidecar pack
 * (registered from {@link com.thebeyond.TheBeyond#addBuiltinPacks}) is an authorized
 * Beyond-owned pack that extends y-bounds to {@code -64..384} so auroracite can generate
 * at {@code y=-64}, while Beyond's chunk generator still runs. In that configuration
 * {@code dimMinY=-64} but Beyond DOES own the terrain, so the old gate wrongly canceled
 * every Beyond jigsaw structure (including {@code jump_platform_island} on the central
 * island, reported by Reda on 2026-04-14).
 *
 * <p>The real signal is {@link BeyondTerrainState#isActive()}, which is set in
 * {@link com.thebeyond.common.worldgen.BeyondEndBiomeSource}'s constructor — Beyond's biome
 * source codec only decodes when Beyond's dimension JSON is the one the datapack stack
 * selected, which means Beyond's chunk gen is running.
 *
 * <h2>Per-structure behavior</h2>
 * <ul>
 *   <li><b>Beyond active, {@code dimMinY == 0}</b> (Beyond-só, or Beyond + Nullscape /
 *       BetterEnd / Stellarity without Enderscape installed) — no-op. Vanilla
 *       {@link JigsawStructure#findGenerationPoint} runs untouched against the start_height
 *       in each structure JSON.</li>
 *   <li><b>Beyond active, {@code dimMinY != 0}</b> (Beyond + Enderscape combo, auroracite
 *       relocated to {@code y=-64}) — also no-op for every structure EXCEPT
 *       {@code fountain/fountain}, which is relocated to {@code dimMinY+2} because its
 *       {@code start_height.absolute=2} is calibrated to the Beyond-só floor. Beyond's
 *       central island (y~20-60) and outer FBM islands are still Beyond-placed, so
 *       {@code jump_platform_island}, {@code arch}, {@code bonfire}, etc. land correctly.</li>
 *   <li><b>Beyond NOT active</b> (soup mode, player disabled {@code beyond_terrain}) —
 *       {@code fountain/fountain} relocated to {@code dimMinY+2} so it remains locatable
 *       on top of the auroracite floor placed by the fallback injection path. All other
 *       {@code the_beyond:*} pools (aberrant_remains, arch, bonfire, bridge, jump_platform,
 *       ...) canceled by returning {@link Optional#empty()}. Beyond ambiance isn't allowed
 *       to bleed into foreign terrain.</li>
 * </ul>
 *
 * <h2>Scope</h2>
 * Detection is scoped by the {@code start_pool} registry namespace ({@code the_beyond}) —
 * other mods' jigsaw structures run untouched.
 *
 * <h2>Fields</h2>
 * Shadow field list mirrors the arguments {@link JigsawStructure#findGenerationPoint}
 * forwards to {@link JigsawPlacement#addPieces}; {@code startHeight} and
 * {@code projectStartToHeightmap} are intentionally NOT shadowed because the fountain
 * override replaces them with a hardcoded Y and {@link Optional#empty()}.
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

        // Only intervene for Beyond's structures — scoped by start_pool namespace.
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

        // Fast path: Beyond is running and the dim floor is y=0 (Beyond-só, or Beyond with
        // any End-modifying mod OTHER than Enderscape). Vanilla findGenerationPoint runs
        // against the structure JSON's own start_height and everything lands on Beyond terrain.
        if (beyondActive && dimMinY == 0) {
            return;
        }

        // Fountain: relocate to dimMinY+2 (on top of the auroracite layer) with
        // projection disabled. Runs in two configurations:
        //   (a) Beyond + Enderscape combo — Beyond places auroracite at y=-64, fountain
        //       needs to sit at y=-62 (the structure JSON's start_height.absolute=2 is
        //       calibrated to dimMinY=0, so it would spawn at y=2 inside bedrock).
        //   (b) Soup mode — the fallback auroracite injection path places a floor at
        //       dimMinY, fountain sits on top of it so players can still return from End.
        // Fountain must remain locatable and stand-able in both, never canceled.
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

        // Beyond + Enderscape combo (Beyond owns the terrain, dim floor is y=-64): let every
        // other Beyond jigsaw structure run on its configured start_height. Central island
        // (y~20-60) and outer FBM islands are Beyond-placed, same as Beyond-só — only the
        // auroracite floor moved. jump_platform_island, arch, bonfire, etc. stay eligible.
        if (beyondActive) {
            return;
        }

        // Soup mode (Beyond inactive): cancel every remaining Beyond jigsaw structure.
        // They would otherwise land on the auroracite floor (void chunks) or on random
        // foreign-terrain heightmaps (floating island chunks), creating visible Beyond
        // debris in foreign terrain.
        cir.setReturnValue(Optional.empty());
    }
}
