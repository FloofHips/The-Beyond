package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.api.worldgen.ForeignStructureWrite;
import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.SanctionedWrite;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import com.thebeyond.common.worldgen.FloatingFeatureGuard;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vetoes two kinds of writes on Beyond's own End generator: features rooting inside a foreign structure's
 *  carved volume ({@link FeatureGuard}), and foreign template air carving solid island terrain. {@link SanctionedWrite} bypasses both. */
@Mixin(WorldGenRegion.class)
public abstract class IslandCarveProtectionMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void the_beyond$protectIslandsFromTemplateAir(
            BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.isCancelled()) return;
        if (SanctionedWrite.isSanctioned()) return;  // sanctioned / Beyond's own writes pass
        WorldGenRegion self = (WorldGenRegion) (Object) this;
        if (self.getLevel().dimension() != Level.END) return;   // cheap early-out before the generator lookup
        // Only act where Beyond's own chunkgen owns the terrain — other End generators are left intact.
        if (!(self.getLevel().getChunkSource().getGenerator() instanceof BeyondEndChunkGenerator)) return;
        // Bar the gellid-void pool from a carve structure's footprint — floor crystals then have no gellid
        // to grow from; ceiling crystals (independent) stay.
        if (state.is(BeyondBlocks.GELLID_VOID.get())
                && FeatureGuard.insideStructureVolume(pos.getX(), pos.getY(), pos.getZ())) {
            cir.setReturnValue(false);
            if (!BeyondGenDiagnostics.loggedGellidVeto) {
                BeyondGenDiagnostics.loggedGellidVeto = true;
                com.thebeyond.TheBeyond.LOGGER.info("[FeatureGuard] first gellid-in-structure veto at {}", pos);
            }
            return;
        }
        // Feature-piercing veto. Guard is disarmed during the structure's own placement, so it still builds.
        if (FeatureGuard.blocksFeatureAt(pos.getX(), pos.getY(), pos.getZ())) {
            // Void crystals only ever write into air (allowed_placement #air), so letting them through here
            // just lets Beyond's own decoration hang through the volume without defacing solid blocks.
            if (state.is(BeyondBlocks.VOID_CRYSTAL.get())) {
                if (!BeyondGenDiagnostics.loggedCrystalAllow) {
                    BeyondGenDiagnostics.loggedCrystalAllow = true;
                    com.thebeyond.TheBeyond.LOGGER.info(
                            "[FeatureGuard] void-crystal allowlisted (hangs full) at {}", pos);
                }
                return;   // allow
            }
            cir.setReturnValue(false);
            ForeignStructureWrite.Scope hit = ForeignStructureWrite.currentScope();
            if (hit != null) hit.featureVeto++;
            if (!BeyondGenDiagnostics.loggedFeatureVeto) {
                BeyondGenDiagnostics.loggedFeatureVeto = true;
                com.thebeyond.TheBeyond.LOGGER.info(
                        "[FeatureGuard] first feature-in-volume veto at {} (state={})", pos, state);
            }
            return;
        }
        // A feature may not replace what a piece stands on, its writes into air are kept.
        if (FeatureGuard.isArmed() && !FeatureGuard.inStructure() && !state.isAir()
                && FeatureGuard.insidePieceVolume(pos.getX(), pos.getY(), pos.getZ())) {
            BlockState there = self.getBlockState(pos);
            if (!there.isAir() && !there.is(Blocks.END_STONE)) {
                cir.setReturnValue(false);
                return;
            }
        }
        // Record decoration blocks near a carve structure; the post-decoration sweep drops any whose support was severed above. Generic — no per-mod list.
        if (FeatureGuard.isArmed() && !FeatureGuard.inStructure() && !state.isAir()) {
            FloatingFeatureGuard.record(pos.asLong());
        }
        // Carve veto: foreign template AIR over solid island terrain.
        ForeignStructureWrite.Scope scope = ForeignStructureWrite.currentScope();
        if (!state.isAir()) {
            if (scope != null) scope.recordWrite(pos.asLong());   // the structure owns this cell from here on
            return;                                              // only template AIR can carve
        }
        if (!ForeignStructureWrite.isActive()) return;
        // A cell this structure just filled is its own geometry, vanilla cuts End City doorways with later air.
        if (scope != null && scope.wroteHere(pos.asLong())) {
            scope.selfOverwrite++;
            return;
        }
        // A degenerate carve mask (1x1x1 feature pool box) cannot clear, so authored air must open it.
        if (scope != null && the_beyond$selfHollowing(scope.structureId)) return;
        BlockState existing = self.getBlockState(pos);
        if (existing.isAir()) return;                // air-over-air: hollowing above islands → allow
        if (!existing.getFluidState().isEmpty()) return; // don't fight fluids
        cir.setReturnValue(false);                   // veto: would carve solid island terrain
        String id = scope != null ? scope.structureId : "<no scope>";
        if (scope != null) scope.terrainVeto++;
        // Probe the diagnostic set once per scope, not once per vetoed block.
        if ((scope == null || scope.terrainVeto == 1) && BeyondGenDiagnostics.loggedCarveVeto.add(id)) {
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[IslandCarveProtection] {} first air-over-solid veto at {} (existing={})", id, pos, existing);
        }
    }

    @Unique
    private static boolean the_beyond$selfHollowing(String id) {
        if (id == null) return false;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) return false;
        StructureIntegrationProfile p = BeyondForeignStructureProfiles.get(key);
        return p != null && p.selfHollowing();
    }
}
