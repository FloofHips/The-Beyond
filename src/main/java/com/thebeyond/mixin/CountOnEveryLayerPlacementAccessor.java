package com.thebeyond.mixin;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.placement.CountOnEveryLayerPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CountOnEveryLayerPlacement.class)
public interface CountOnEveryLayerPlacementAccessor {
    @Accessor("count")
    IntProvider the_beyond$count();
}
