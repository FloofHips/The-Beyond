package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Rejects a foreign structure's placement (far-field only) when its footprint can't host it organically,
 *  rather than fabricating terrain to fit it; profiles come from addons via {@link BeyondForeignStructureProfiles}. */
@Mixin(Structure.class)
public abstract class ForeignStructureRejectionMixin {

    /** Far-field threshold² — property of the pancake terrain model, not any one structure. */
    private static final double FAR_FIELD_SQ = 650.0 * 650.0;

    @Inject(method = "findValidGenerationPoint", at = @At("RETURN"), cancellable = true)
    private void the_beyond$rejectUnfitForeign(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        try {
            Optional<Structure.GenerationStub> result = cir.getReturnValue();
            if (result == null || result.isEmpty()) return;
            if (!BeyondTerrainState.isActive()) return;
            if (!(context.chunkGenerator() instanceof BeyondEndChunkGenerator beg)) return;

            ResourceLocation key = context.registryAccess()
                    .registryOrThrow(Registries.STRUCTURE).getKey((Structure) (Object) this);
            if (key == null) return;
            String ns = key.getNamespace();
            if ("the_beyond".equals(ns) || "minecraft".equals(ns)) return;  // those own their own placement

            BlockPos pos = result.get().position();
            double d2 = (double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ();
            // Inside the far-field edge there's no valid pancake terrain, so no foreign structure belongs there.
            if (d2 < FAR_FIELD_SQ) {
                if (!com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedReject) {
                    com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedReject = true;
                    com.thebeyond.TheBeyond.LOGGER.info(
                            "[Beyond] foreign structure REJECTED (central/inner zone): {} at {}", key, pos);
                }
                cir.setReturnValue(Optional.empty());
                return;
            }

            StructureIntegrationProfile profile =
                    BeyondForeignStructureProfiles.resolve((Structure) (Object) this, key);
            if (profile == null || !profile.rejectUnfit()) return;   // not hosted → leave to vanilla

            int minY = context.heightAccessor().getMinBuildHeight();
            int maxY = context.heightAccessor().getMaxBuildHeight() - 1;
            beg.computeNoisesIfNotPresent(context.randomState());

            boolean reject = switch (profile.anchor()) {
                case SEATED   -> the_beyond$footprintUnfit(pos, minY, profile);
                case FLOATING -> the_beyond$clipsCeiling(pos, maxY, profile);
            };
            // One-shot logs so an in-game run confirms the mixin is deciding, without per-call spam.
            if (reject) {
                if (!com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedReject) {
                    com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedReject = true;
                    com.thebeyond.TheBeyond.LOGGER.info(
                            "[Beyond] foreign structure REJECTED (footprint unfit): {} at {} anchor={}", key, pos, profile.anchor());
                }
                cir.setReturnValue(Optional.empty());
            } else if (!com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedAccept) {
                com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedAccept = true;
                com.thebeyond.TheBeyond.LOGGER.info(
                        "[Beyond] foreign structure accepted (fits): {} at {} anchor={}", key, pos, profile.anchor());
            }
        } catch (Throwable t) {
            // Never break worldgen: leave vanilla's result untouched, but log once so a silent fail-open is visible.
            if (!com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedError) {
                com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedError = true;
                com.thebeyond.TheBeyond.LOGGER.warn(
                        "[Beyond] foreign structure rejection errored (left vanilla result): {}", t.toString());
            }
        }
    }

    /** SEATED: unfit if too much of the footprint has no solid ground within tolerance below the floor. */
    private static boolean the_beyond$footprintUnfit(BlockPos pos, int minY, StructureIntegrationProfile profile) {
        int floor = pos.getY();
        int radius = profile.towerRadius(), step = profile.towerStep();
        BeyondEndChunkGenerator.ColumnScratch scr = BeyondEndChunkGenerator.getColumnScratch();
        int pad = 0, total = 0;
        int lo = Math.max(minY, floor - profile.flushTolerance());
        for (int ox = -radius; ox <= radius; ox += step) {
            for (int oz = -radius; oz <= radius; oz += step) {
                total++;
                int wx = pos.getX() + ox, wz = pos.getZ() + oz;
                float dist = (float) Math.sqrt((double) wx * wx + (double) wz * wz);
                BeyondEndChunkGenerator.initColumnScratch(wx, wz, dist, scr);
                boolean flush = false;
                for (int y = floor - 1; y >= lo; y--) {
                    if (BeyondEndChunkGenerator.isSolidTerrainScratch(y, scr)) { flush = true; break; }
                }
                if (!flush) pad++;
            }
        }
        return total > 0 && (double) pad / total > profile.padRejectFraction();
    }

    /** FLOATING: rejected only if the body envelope would clip the build ceiling; intrusions are cleared elsewhere. */
    private static boolean the_beyond$clipsCeiling(BlockPos pos, int maxY, StructureIntegrationProfile profile) {
        return pos.getY() + profile.floatEnvelope() > maxY;
    }
}
