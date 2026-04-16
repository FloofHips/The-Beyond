package com.thebeyond.mixin;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseGeneratorSettings.class)
public interface NoiseGeneratorSettingsAccessor {
    @Accessor("surfaceRule")
    SurfaceRules.RuleSource the_beyond$getSurfaceRule();

    @Mutable
    @Accessor("surfaceRule")
    void the_beyond$setSurfaceRule(SurfaceRules.RuleSource rule);
}
