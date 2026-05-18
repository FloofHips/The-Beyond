package com.thebeyond.api.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of {@link MacroRegionOverride}s that the Beyond End biome source queries
 *  on every solid-pool sample. First non-null wins; register in mod-load. */
@ApiStatus.Experimental
public final class BeyondMacroRegions {
    private static final List<MacroRegionOverride> OVERRIDES = new CopyOnWriteArrayList<>();

    private BeyondMacroRegions() {}

    public static void register(MacroRegionOverride override) {
        OVERRIDES.add(override);
    }

    /** Queries overrides in registration order; returns the first non-null result. */
    @Nullable
    public static Holder<Biome> queryAt(int blockX, int blockZ) {
        for (MacroRegionOverride o : OVERRIDES) {
            Holder<Biome> hit = o.biomeAt(blockX, blockZ);
            if (hit != null) return hit;
        }
        return null;
    }
}
