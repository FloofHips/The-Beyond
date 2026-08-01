package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.FeatureGuard;
import com.thebeyond.api.worldgen.ForeignStructureWrite;
import com.thebeyond.api.worldgen.SanctionedWrite;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.FloatingFeatureGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vetoes two kinds of writes on Beyond's own End generator: features rooting inside a foreign structure's
 *  carved volume ({@link FeatureGuard}), and foreign template air carving solid island terrain. {@link SanctionedWrite} bypasses both. */
@Mixin(WorldGenRegion.class)
public abstract class IslandCarveProtectionMixin {

    private static volatile boolean the_beyond$loggedFirstVeto = false;
    private static volatile boolean the_beyond$loggedFirstFeatureVeto = false;
    private static volatile boolean the_beyond$loggedFirstCrystalAllow = false;
    private static volatile boolean the_beyond$loggedFirstGellidVeto = false;

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
            if (!the_beyond$loggedFirstGellidVeto) {
                the_beyond$loggedFirstGellidVeto = true;
                com.thebeyond.TheBeyond.LOGGER.info("[FeatureGuard] first gellid-in-structure veto at {}", pos);
            }
            return;
        }
        // Feature-piercing veto. Guard is disarmed during the structure's own placement, so it still builds.
        if (FeatureGuard.blocksFeatureAt(pos.getX(), pos.getY(), pos.getZ())) {
            // Void crystals only ever write into air (allowed_placement #air), so letting them through here
            // just lets Beyond's own decoration hang through the volume without defacing solid blocks.
            if (state.is(BeyondBlocks.VOID_CRYSTAL.get())) {
                if (!the_beyond$loggedFirstCrystalAllow) {
                    the_beyond$loggedFirstCrystalAllow = true;
                    com.thebeyond.TheBeyond.LOGGER.info(
                            "[FeatureGuard] void-crystal allowlisted (hangs full) at {}", pos);
                }
                return;   // allow
            }
            cir.setReturnValue(false);
            if (!the_beyond$loggedFirstFeatureVeto) {
                the_beyond$loggedFirstFeatureVeto = true;
                com.thebeyond.TheBeyond.LOGGER.info(
                        "[FeatureGuard] first feature-in-volume veto at {} (state={})", pos, state);
            }
            return;
        }
        // Record decoration blocks near a carve structure; the post-decoration sweep drops any whose support was severed above. Generic — no per-mod list.
        if (FeatureGuard.isArmed() && !FeatureGuard.inStructure() && !state.isAir()) {
            FloatingFeatureGuard.record(pos.asLong());
        }
        // Carve veto: foreign template AIR over solid island terrain.
        if (!state.isAir()) return;                  // only template AIR can carve
        if (!ForeignStructureWrite.isActive()) return;
        BlockState existing = self.getBlockState(pos);
        if (existing.isAir()) return;                // air-over-air: hollowing above islands → allow
        if (!existing.getFluidState().isEmpty()) return; // don't fight fluids
        cir.setReturnValue(false);                   // veto: would carve solid island terrain
        if (!the_beyond$loggedFirstVeto) {
            the_beyond$loggedFirstVeto = true;
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[IslandCarveProtection] first air-over-solid veto at {} (existing={})", pos, existing);
        }
    }
}
