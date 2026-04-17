package com.thebeyond.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sanitizes BiomeSource.possibleBiomes() to remove non-Holder objects.
 * Some mods inject ResourceKey objects via generic erasure contamination.
 * Priority 1100 ensures we run after UnusualEnd (800) and Phantasm (900).
 */
@Mixin(value = BiomeSource.class, priority = 1100)
public class BiomeSourceSanitizerMixin {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    private void the_beyond$sanitizeBiomes(CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        Set<?> raw = (Set<?>) (Set) cir.getReturnValue();
        boolean contaminated = false;

        for (Object obj : raw) {
            if (!(obj instanceof Holder<?>)) {
                contaminated = true;
                break;
            }
        }

        if (contaminated) {
            Set<Holder<Biome>> clean = new LinkedHashSet<>();
            for (Object obj : raw) {
                if (obj instanceof Holder<?> holder) {
                    clean.add((Holder<Biome>) holder);
                }
            }
            cir.setReturnValue(clean);
        }
    }
}
