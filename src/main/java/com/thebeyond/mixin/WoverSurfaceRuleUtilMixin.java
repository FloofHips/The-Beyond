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

/** Skips Wover's End surface-rule injection when Beyond owns the dim — Beyond's own
 *  {@code SurfaceRuleMerger} runs instead. {@code @Pseudo} → no-op without Wover. */
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
