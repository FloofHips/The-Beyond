package com.thebeyond.mixin;

import com.thebeyond.util.WorldSeedHolder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RandomState.class)
public class RandomStateMixin implements WorldSeedHolder {
    @Unique long the_Beyond$WorldSeed;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void beyond$RandomState(NoiseGeneratorSettings settings, HolderGetter noiseParametersGetter, long levelSeed, CallbackInfo ci) {
        the_Beyond$WorldSeed = levelSeed;
    }

    @Override
    public long the_Beyond$getWorldSeed() {
        return the_Beyond$WorldSeed;
    }
}
