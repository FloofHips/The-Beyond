package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.worldgen.ForeignStructureWrite;
import com.thebeyond.api.worldgen.SanctionedWrite;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.BeyondStructureCarver;
import com.thebeyond.common.worldgen.BeyondGenDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import com.thebeyond.mixin.StructureTemplateAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Arms the carve veto around template writes, covering a foreign {@code .nbt} placed by a feature. */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplatePlaceMixin {

    @WrapMethod(method = "placeInWorld")
    private boolean the_beyond$scopeTemplateWrites(
            ServerLevelAccessor serverLevel, BlockPos offset, BlockPos pos, StructurePlaceSettings settings,
            RandomSource random, int flags, Operation<Boolean> original) {
        if (!the_beyond$vetoApplies(serverLevel)) {
            return original.call(serverLevel, offset, pos, settings, random, flags);
        }
        // No scope here: one per template resets the ledger between pieces and re-seals End City junctions.
        StructurePlaceSettings placed =
                BeyondStructureCarver.TERRAIN_MATCHING_IS_RIGID ? the_beyond$withoutGravity(settings) : settings;
        ForeignStructureWrite.enter();
        try {
            boolean ok = original.call(serverLevel, offset, pos, placed, random, flags);
            the_beyond$probePlacement(serverLevel, offset, placed);
            return ok;
        } finally {
            ForeignStructureWrite.exit();
        }
    }

    /** Matches the veto's gates, so it never arms a Beyond flag in another dimension or another mod's End. */
    private static boolean the_beyond$vetoApplies(ServerLevelAccessor accessor) {
        if (SanctionedWrite.isSanctioned()) return false;
        try {
            ServerLevel level = accessor.getLevel();
            return level.dimension() == Level.END
                    && level.getChunkSource().getGenerator() instanceof BeyondEndChunkGenerator;
        } catch (Throwable t) {
            if (!BeyondGenDiagnostics.loggedTemplateGateError) {
                BeyondGenDiagnostics.loggedTemplateGateError = true;
                com.thebeyond.TheBeyond.LOGGER.warn(
                        "[Beyond] template carve-veto gate errored, veto left disarmed: {}", t.toString());
            }
            return false;
        }
    }

    @Unique
    private static StructurePlaceSettings the_beyond$withoutGravity(StructurePlaceSettings settings) {
        boolean any = false;
        for (StructureProcessor p : settings.getProcessors()) {
            if (p instanceof GravityProcessor) { any = true; break; }
        }
        if (!any) return settings;
        StructurePlaceSettings copy = settings.copy();
        copy.clearProcessors();
        for (StructureProcessor p : settings.getProcessors()) {
            if (!(p instanceof GravityProcessor)) copy.addProcessor(p);
        }
        if (!BeyondGenDiagnostics.loggedGravitySuppressed) {
            BeyondGenDiagnostics.loggedGravitySuppressed = true;
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[Beyond] terrain_matching gravity suppressed: pieces stay at their assembly Y so the carve"
                    + " cannot drop their blocks out of the world");
        }
        return copy;
    }

    @Unique
    private void the_beyond$probePlacement(
            ServerLevelAccessor accessor, BlockPos origin, StructurePlaceSettings settings) {
        if (BeyondGenDiagnostics.placementProbesLogged >= 60) return;
        ForeignStructureWrite.Scope scope = ForeignStructureWrite.currentScope();
        String id = scope != null ? scope.structureId : null;
        if (id == null) return;
        try {
            StructureTemplate self = (StructureTemplate) (Object) this;
            var pals = ((StructureTemplateAccessor) (Object) self).the_beyond$palettes();
            if (pals.isEmpty()) return;
            int asked = 0, present = 0, clipped = 0, replaced = 0;
            BoundingBox clip = settings.getBoundingBox();
            for (StructureTemplate.StructureBlockInfo info : pals.get(0).blocks()) {
                if (info.state().isAir()) continue;
                asked++;
                BlockPos wp = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
                if (clip != null && !clip.isInside(wp)) { clipped++; continue; }
                var now = accessor.getBlockState(wp);
                if (now.isAir()) continue;
                present++;
                if (!now.is(info.state().getBlock())) replaced++;
            }
            if (asked == 0) return;
            BeyondGenDiagnostics.placementProbesLogged++;
            com.thebeyond.TheBeyond.LOGGER.info(
                    "[Beyond] placement probe {} origin={} asked={} present={} clippedOut={} otherBlock={}"
                    + " missing={} (missing>0 means blocks were lost writing, not authored away)",
                    id, origin, asked, present, clipped, replaced, asked - clipped - present);
        } catch (Throwable ignored) {
            // A probe must never break a placement.
        }
    }
}
