package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.api.worldgen.BeyondTerrainState;
import com.thebeyond.api.worldgen.StructureIntegrationProfile;
import com.mojang.datafixers.util.Either;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import com.thebeyond.common.worldgen.ForeignFit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
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

            var registry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
            ResourceLocation key = registry.getKey((Structure) (Object) this);
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
            // A pedestal's seat judged it on the floor it moved to, where this test would read the sample's height.
            boolean pedestal = profile.basePedestal()
                    || BeyondForeignStructureProfiles.isBasePedestal(key, registry, (Structure) (Object) this);

            int minY = context.heightAccessor().getMinBuildHeight();
            int maxY = context.heightAccessor().getMaxBuildHeight() - 1;
            beg.computeNoisesIfNotPresent(context.randomState());

            StructurePiecesBuilder pieces = the_beyond$assemble(result.get(), cir);
            if (profile.anchor() == StructureIntegrationProfile.Anchor.SEATED && pedestal && pieces != null && !pieces.isEmpty()
                    && BeyondForeignStructureProfiles.isLayerDistributed(key, context.chunkPos().toLong())
                    && !ForeignFit.sinks(pieces.build().pieces(), context.structureTemplateManager())) return;
            boolean reject = switch (profile.anchor()) {
                case SEATED   -> ForeignFit.seatedUnfit(pos.getX(), pos.getZ(), pos.getY(), pieces, minY, profile);
                case FLOATING -> the_beyond$clipsCeiling(key, result.get(), pieces, maxY, profile);
            };
            BeyondGenDiagnostics.countDecision(key.toString(), "R", (reject ? "REJECT_" : "ACCEPT_") + profile.anchor(),
                    context.chunkPos().toLong());
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

    /** Built once and handed on, so the pieces tested are the pieces placed. */
    @org.jetbrains.annotations.Nullable
    private static StructurePiecesBuilder the_beyond$assemble(Structure.GenerationStub stub,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        try {
            StructurePiecesBuilder pieces = stub.getPiecesBuilder();
            cir.setReturnValue(Optional.of(new Structure.GenerationStub(stub.position(), Either.right(pieces))));
            return pieces;
        } catch (Throwable t) {
            if (BeyondGenDiagnostics.loggedMaskKeys.add("foreign-assemble-fail")) {
                com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] foreign fit test fell back to the profile's sizes: {}", t.toString());
            }
            return null;
        }
    }

    private static boolean the_beyond$clipsCeiling(ResourceLocation key, Structure.GenerationStub stub,
            @org.jetbrains.annotations.Nullable StructurePiecesBuilder pieces, int maxY, StructureIntegrationProfile profile) {
        boolean envelope = pieces == null || pieces.isEmpty() || ForeignFit.featureOnly(pieces);
        // A feature grows from its piece, and the stub may have been moved to the ground for the biome test.
        int from = pieces == null || pieces.isEmpty() ? stub.position().getY() : pieces.getBoundingBox().minY();
        int top = envelope ? from + profile.floatEnvelope() : pieces.getBoundingBox().maxY();
        if (BeyondGenDiagnostics.loggedAutoProfile.add("float-ceiling:" + key)) {
            com.thebeyond.TheBeyond.LOGGER.debug("[Beyond] floater {} start y={} top y={} ({}) ceiling={} -> {}",
                    key, stub.position().getY(), top, envelope ? "envelope" : "pieces", maxY,
                    top > maxY ? "REJECT (clips ceiling)" : "fits");
        }
        return top > maxY;
    }
}
