package com.thebeyond.common.worldgen.compat;

import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import com.thebeyond.util.HashSimplexNoise;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

/** Coarse 2D simplex (~75-block wavelength) over the outer ring to make
 *  {@code unusualend:warped_reef} a coherent region; pure Voronoi 3D was too sparse. */
public final class WarpedReefMacroRegion {
    private static final ResourceLocation BIOME_ID =
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_reef");
    private static final double MACRO_SCALE = 1.0 / 40.0;
    private static final double MACRO_THRESHOLD = 0.65;

    private static volatile Holder<Biome> cachedHolder = null;

    private WarpedReefMacroRegion() {}

    /** Looks up the biome holder once at server start; no-op when Unusual End is absent. */
    public static void cache(MinecraftServer server) {
        cachedHolder = server.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, BIOME_ID))
                .orElse(null);
    }

    public static void clear() {
        cachedHolder = null;
    }

    /** {@code warped_reef} if (blockX, blockZ) falls inside a macro-region patch; otherwise null. */
    public static Holder<Biome> biomeAt(int blockX, int blockZ) {
        Holder<Biome> reef = cachedHolder;
        if (reef == null) return null;
        HashSimplexNoise field = BeyondEndChunkGenerator.biomeSimplexNoise;
        if (field == null) return null;
        double n = field.getValue(blockX * MACRO_SCALE, blockZ * MACRO_SCALE);
        return n > MACRO_THRESHOLD ? reef : null;
    }
}
