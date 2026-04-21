package com.thebeyond.mixin.compat.compass;

import com.google.common.collect.ListMultimap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.knowledge.HiddenContentFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

/**
 * Per-player filter on Explorer's Compass. Wraps all three {@code StructureUtils}
 * queries in {@code use()} — filtering only the list leaves hidden names leaking
 * through the group maps (client uses the forward multimap for group search).
 *
 * <p>{@code @Pseudo}/{@code require=0}/{@code remap=false} — inert when the mod is
 * absent or the injection point moves. Empty tags → {@link HiddenContentFilter} short-circuits.
 */
@Pseudo
@Mixin(targets = "com.chaosthedude.explorerscompass.items.ExplorersCompassItem", remap = false)
public abstract class ExplorersCompassItemMixin {

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/chaosthedude/explorerscompass/util/StructureUtils;getAllowedStructureKeys(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/List;"
            ),
            require = 0,
            remap = false
    )
    private List<ResourceLocation> theBeyond$filterAllowedStructureKeys(
            ServerLevel level,
            Operation<List<ResourceLocation>> original,
            @Local(argsOnly = true) Player player) {
        List<ResourceLocation> all = original.call(level);
        if (!(player instanceof ServerPlayer sp)) return all;
        return HiddenContentFilter.filterStructureKeys(all, sp);
    }

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/chaosthedude/explorerscompass/util/StructureUtils;getStructureKeysToTypeKeys(Lnet/minecraft/server/level/ServerLevel;)Ljava/util/Map;"
            ),
            require = 0,
            remap = false
    )
    private Map<ResourceLocation, ResourceLocation> theBeyond$filterStructureKeysToTypeKeys(
            ServerLevel level,
            Operation<Map<ResourceLocation, ResourceLocation>> original,
            @Local(argsOnly = true) Player player) {
        Map<ResourceLocation, ResourceLocation> all = original.call(level);
        if (!(player instanceof ServerPlayer sp)) return all;
        return HiddenContentFilter.filterStructureKeysToTypeKeys(all, sp);
    }

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/chaosthedude/explorerscompass/util/StructureUtils;getTypeKeysToStructureKeys(Lnet/minecraft/server/level/ServerLevel;)Lcom/google/common/collect/ListMultimap;"
            ),
            require = 0,
            remap = false
    )
    private ListMultimap<ResourceLocation, ResourceLocation> theBeyond$filterTypeKeysToStructureKeys(
            ServerLevel level,
            Operation<ListMultimap<ResourceLocation, ResourceLocation>> original,
            @Local(argsOnly = true) Player player) {
        ListMultimap<ResourceLocation, ResourceLocation> all = original.call(level);
        if (!(player instanceof ServerPlayer sp)) return all;
        return HiddenContentFilter.filterTypeKeysToStructureKeys(all, sp);
    }
}
