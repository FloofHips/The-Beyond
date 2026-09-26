package com.thebeyond.mixin;

import com.thebeyond.api.worldgen.BeyondForeignStructureProfiles;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.common.worldgen.ForeignFit;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Leaves pedestal starts out of the beard, whose job their ground plan does, unless they sink into the island. */
@Mixin(StructureManager.class)
public abstract class StructureManagerPedestalBeardMixin {

    @Inject(method = "startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private void the_beyond$withoutPedestals(ChunkPos pos, Predicate<Structure> test,
            CallbackInfoReturnable<List<StructureStart>> cir) {
        if (!BeyondEndChunkGenerator.buildingBeard()) return;
        List<StructureStart> all = cir.getReturnValue();
        if (all == null || all.isEmpty()) return;
        var reg = ((StructureManager) (Object) this).registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<StructureStart> kept = new ArrayList<>(all.size());
        for (StructureStart s : all) {
            Structure st = s.getStructure();
            var key = reg.getKey(st);
            if (BeyondForeignStructureProfiles.isBasePedestal(key, reg, st) && !ForeignFit.sinks(s.getPieces(), null)) {
                var profile = BeyondForeignStructureProfiles.resolve(st, key);
                if (profile != null && profile.carve()) continue;
                if (com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedBeardKept.size() < 2000
                        && com.thebeyond.common.worldgen.BeyondGenDiagnostics.loggedBeardKept.add(key + "@" + s.getChunkPos().toLong())) {
                    com.thebeyond.TheBeyond.LOGGER.warn("[Beyond] pedestal {} start@[{},{}] has no carve profile, so it keeps its beard",
                            key, s.getChunkPos().x, s.getChunkPos().z);
                }
            }
            kept.add(s);
        }
        if (kept.size() != all.size()) cir.setReturnValue(kept);
    }
}
