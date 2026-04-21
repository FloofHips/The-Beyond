package com.thebeyond.mixin.compat.compass;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thebeyond.common.knowledge.HiddenContentFilter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Per-player filter on Nature's Compass. Single wrap suffices — downstream xpLevels +
 * dimensionKeys derive from {@code getAllowedBiomeKeys}. Descriptor is {@code Level}
 * (not {@code ServerLevel}) to match what {@code BiomeUtils} declares.
 *
 * <p>{@code @Pseudo}/{@code require=0}/{@code remap=false} — inert when the mod is
 * absent or the injection point moves.
 */
@Pseudo
@Mixin(targets = "com.chaosthedude.naturescompass.items.NaturesCompassItem", remap = false)
public abstract class NaturesCompassItemMixin {

    @WrapOperation(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/chaosthedude/naturescompass/util/BiomeUtils;getAllowedBiomeKeys(Lnet/minecraft/world/level/Level;)Ljava/util/List;"
            ),
            require = 0,
            remap = false
    )
    private List<ResourceLocation> theBeyond$filterAllowedBiomeKeys(
            Level level,
            Operation<List<ResourceLocation>> original,
            @Local(argsOnly = true) Player player) {
        List<ResourceLocation> all = original.call(level);
        if (!(player instanceof ServerPlayer sp)) return all;
        return HiddenContentFilter.filterBiomeKeys(all, sp);
    }
}
