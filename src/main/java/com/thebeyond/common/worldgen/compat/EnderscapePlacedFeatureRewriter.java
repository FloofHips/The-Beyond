package com.thebeyond.common.worldgen.compat;

import com.thebeyond.TheBeyond;
import com.thebeyond.mixin.HeightRangePlacementAccessor;
import com.thebeyond.mixin.PlacedFeatureAccessor;
import com.thebeyond.mixin.UniformHeightAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;

/** Rewrites Enderscape's hardcoded {@code absolute >= 0} lower bounds in {@code height_range}
 *  modifiers to {@code above_bottom: 0} so features cover the combo dim's {@code [-64, 0]}
 *  range that ships skipped. */
public final class EnderscapePlacedFeatureRewriter {
    private EnderscapePlacedFeatureRewriter() {}

    public static void rewrite(MinecraftServer server) {
        Registry<PlacedFeature> registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        int total = 0;
        for (PlacedFeature pf : registry) {
            ResourceLocation id = registry.getKey(pf);
            if (id == null || !"enderscape".equals(id.getNamespace())) continue;
            if (rewritePlacedFeature(pf)) total++;
        }
        if (total > 0) {
            TheBeyond.LOGGER.info("[TheBeyond] Rewrote {} Enderscape placed_features for Y<0 coverage", total);
        }
    }

    private static boolean rewritePlacedFeature(PlacedFeature pf) {
        List<PlacementModifier> original = pf.placement();
        List<PlacementModifier> modified = null;
        for (int i = 0; i < original.size(); i++) {
            PlacementModifier mod = original.get(i);
            PlacementModifier rewritten = rewriteModifier(mod);
            if (rewritten != mod) {
                if (modified == null) modified = new ArrayList<>(original);
                modified.set(i, rewritten);
            }
        }
        if (modified == null) return false;
        ((PlacedFeatureAccessor) (Object) pf).the_beyond$setPlacement(List.copyOf(modified));
        return true;
    }

    private static PlacementModifier rewriteModifier(PlacementModifier mod) {
        if (mod instanceof HeightRangePlacement hrp) {
            HeightProvider hp = ((HeightRangePlacementAccessor) (Object) hrp).the_beyond$getHeight();
            HeightProvider rewrittenHp = rewriteHeightProvider(hp);
            if (rewrittenHp != hp) {
                return HeightRangePlacement.of(rewrittenHp);
            }
        }
        return mod;
    }

    private static HeightProvider rewriteHeightProvider(HeightProvider hp) {
        if (hp instanceof UniformHeight uh) {
            UniformHeightAccessor acc = (UniformHeightAccessor) (Object) uh;
            VerticalAnchor min = acc.the_beyond$getMinInclusive();
            VerticalAnchor newMin = rewriteLowerAnchor(min);
            if (newMin != min) {
                return UniformHeight.of(newMin, acc.the_beyond$getMaxInclusive());
            }
        }
        return hp;
    }

    private static VerticalAnchor rewriteLowerAnchor(VerticalAnchor anchor) {
        if (anchor instanceof VerticalAnchor.Absolute abs && abs.y() >= 0) {
            return VerticalAnchor.aboveBottom(0);
        }
        return anchor;
    }
}
