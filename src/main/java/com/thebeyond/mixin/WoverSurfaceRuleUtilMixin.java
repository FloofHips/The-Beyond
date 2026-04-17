package com.thebeyond.mixin;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels Wover/BetterX's {@code SurfaceRuleUtil.injectNoiseBasedSurfaceRules} for the End
 * when Beyond owns the dimension. Wover would otherwise merge ~60 foreign biome surface rules
 * into Beyond's noise settings, causing blocks at wrong Y ranges and "unbreakable blocks".
 *
 * <p>Beyond runs its own surface rule merge via {@code SurfaceRuleMerger}.</p>
 *
 * <p>Only cancels for {@code LevelStem.END} + {@link BeyondEndBiomeSource}. Soft-targeted
 * via {@code @Pseudo} — silent no-op without WorldWeaver.</p>
 */
@Pseudo
@Mixin(targets = "org.betterx.wover.surface.impl.SurfaceRuleUtil", remap = false)
public class WoverSurfaceRuleUtilMixin {

    @Inject(
            method = "injectNoiseBasedSurfaceRules",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void the_beyond$skipEndWhenBeyondActive(
            ResourceKey<LevelStem> dimensionKey,
            Holder<NoiseGeneratorSettings> noiseSettings,
            BiomeSource loadedBiomeSource,
            CallbackInfo ci) {

        if (!LevelStem.END.equals(dimensionKey)) {
            return;
        }
        if (!(loadedBiomeSource instanceof BeyondEndBiomeSource)) {
            return;
        }

        TheBeyond.LOGGER.info(
                "[TheBeyond] Beyond owns minecraft:the_end — vetoing Wover SurfaceRuleUtil.injectNoiseBasedSurfaceRules injection (prevents foreign biome surface rules from overwriting Beyond's noise settings).");
        ci.cancel();
    }
}
