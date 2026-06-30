package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import com.thebeyond.common.awareness.BeyondAwareness;
import com.thebeyond.common.awareness.HiddenContentFilter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Makes /locate skip biomes and structures you haven't discovered yet, so the game just says
 *  "not found" like it would for anything far away. Only players get filtered. */
@Mixin(LocateCommand.class)
public abstract class LocateCommandMixin {

    /** Drops hidden biomes from the search; if nothing's left the game reports "not found". */
    @WrapOperation(
        method = "locateBiome",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;findClosestBiome3d(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;III)Lcom/mojang/datafixers/util/Pair;"))
    private static Pair<BlockPos, Holder<Biome>> the_beyond$gateBiome(
            ServerLevel level, Predicate<Holder<Biome>> original,
            BlockPos pos, int radius, int hStep, int vStep,
            Operation<Pair<BlockPos, Holder<Biome>>> op,
            @Local(argsOnly = true) CommandSourceStack source) {
        if (!BeyondAwareness.gateEnabled() || !(source.getEntity() instanceof ServerPlayer sp)) {
            return op.call(level, original, pos, radius, hStep, vStep);
        }
        // Build the hidden-set once — findClosestBiome3d tests the predicate per sampled point.
        Predicate<ResourceLocation> biomeHidden = HiddenContentFilter.biomeHiddenTest(sp);
        Predicate<Holder<Biome>> gated = h -> original.test(h)
                && h.unwrapKey().map(k -> !biomeHidden.test(k.location())).orElse(true);
        return op.call(level, gated, pos, radius, hStep, vStep);
    }

    /** Same idea for structures: strip out hidden ones before searching. */
    @WrapOperation(
        method = "locateStructure",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"))
    private static Pair<BlockPos, Holder<Structure>> the_beyond$gateStructure(
            ChunkGenerator gen, ServerLevel sl, HolderSet<Structure> set,
            BlockPos pos, int radius, boolean skipKnown,
            Operation<Pair<BlockPos, Holder<Structure>>> op,
            @Local(argsOnly = true) CommandSourceStack source) {
        if (!BeyondAwareness.gateEnabled() || !(source.getEntity() instanceof ServerPlayer sp)) {
            return op.call(gen, sl, set, pos, radius, skipKnown);
        }
        Predicate<ResourceLocation> structureHidden = HiddenContentFilter.structureHiddenTest(sp);
        List<Holder<Structure>> kept = new ArrayList<>();
        for (Holder<Structure> h : set) {
            ResourceLocation id = h.unwrapKey().map(ResourceKey::location).orElse(null);
            if (id == null || !structureHidden.test(id)) {
                kept.add(h);
            }
        }
        return op.call(gen, sl, HolderSet.direct(kept), pos, radius, skipKnown);
    }
}
