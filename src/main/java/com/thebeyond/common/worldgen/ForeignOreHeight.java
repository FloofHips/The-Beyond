package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

import java.util.Optional;

/** Other mods tune End ores to vanilla's terrain (noise y 0..127): their height range is moved, shape kept, onto our islands. */
public final class ForeignOreHeight {
    private static final int VANILLA_END_MIN_Y = 0;
    private static final int VANILLA_END_DEPTH = 128;

    private ForeignOreHeight() {}

    public static boolean applies(PlacementContext ctx) {
        if (!(ctx.generator() instanceof BeyondEndChunkGenerator)) return false;
        Optional<PlacedFeature> top = ctx.topFeature();
        if (top.isEmpty()) return false;
        Feature<?> feature = top.get().feature().value().feature();
        if (!(feature instanceof OreFeature || feature instanceof ScatteredOreFeature)) return false;
        ResourceLocation id = ctx.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(top.get());
        return id != null && !TheBeyond.MODID.equals(id.getNamespace());
    }

    public static int sample(HeightProvider height, RandomSource random, PlacementContext ctx) {
        int vanillaY = height.sample(random, new VanillaEndFrame(ctx));
        int bottom = BeyondEndChunkGenerator.islandBandBottom(ctx.getMinBuildHeight());
        int top = BeyondEndChunkGenerator.islandBandTop(ctx.getLevel().getMaxBuildHeight());
        double t = (vanillaY - VANILLA_END_MIN_Y + random.nextDouble()) / VANILLA_END_DEPTH;
        return bottom + Mth.floor(t * (top - bottom + 1));
    }

    private static final class VanillaEndFrame extends WorldGenerationContext {
        VanillaEndFrame(PlacementContext ctx) { super(ctx.generator(), ctx.getLevel()); }

        @Override
        public int getMinGenY() { return VANILLA_END_MIN_Y; }

        @Override
        public int getGenDepth() { return VANILLA_END_DEPTH; }
    }
}
