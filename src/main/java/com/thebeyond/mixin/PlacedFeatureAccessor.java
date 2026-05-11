package com.thebeyond.mixin;

import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Mutable setter for {@code PlacedFeature.placement} so {@code EnderscapePlacedFeatureRewriter}
 * can swap modifier lists in-place at server start without unfreezing the registry.
 */
@Mixin(PlacedFeature.class)
public interface PlacedFeatureAccessor {
    @Mutable
    @Accessor("placement")
    void the_beyond$setPlacement(List<PlacementModifier> placement);
}
