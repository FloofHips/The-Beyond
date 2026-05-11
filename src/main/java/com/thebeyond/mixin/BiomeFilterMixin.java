package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import com.thebeyond.common.worldgen.BeyondTerrainState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

@Mixin(BiomeFilter.class)
public abstract class BiomeFilterMixin {
    /** Floating features that resolve biome via 3D Voronoi cell (skipping the air→void
     *  mapping); otherwise they place at air and never see their target biome. */
    private static final Set<ResourceLocation> the_beyond$VORONOI_FLOATING_FEATURES = Set.of(
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_isles"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_citrine_isles"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_rock_1_feature"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_bubbles_feature"),
            ResourceLocation.fromNamespaceAndPath("unusualend", "warped_vegetation")
    );

    @Inject(method = "shouldPlace", at = @At("HEAD"), cancellable = true)
    private void the_beyond$resolveBiome(
            PlacementContext ctx, RandomSource random, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (!BeyondTerrainState.isActive()) return;
        if (!ctx.getLevel().getBlockState(pos).isAir()) return;

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(pos.getX(), 0, pos.getZ());
        boolean foundSurface = false;
        for (int dy = 1; dy <= 4; dy++) {
            probe.setY(pos.getY() - dy);
            if (!ctx.getLevel().getBlockState(probe).isAir()) {
                foundSurface = true;
                break;
            }
        }

        Holder<Biome> biome;
        if (the_beyond$isVoronoiFloatingFeature(ctx)) {
            biome = the_beyond$lookupVoronoiBiome(ctx.getLevel(), pos);
            if (biome == null) return;
        } else if (foundSurface) {
            biome = ctx.getLevel().getBiome(probe);
        } else {
            return;
        }

        final Holder<Biome> resolved = biome;
        boolean result = ctx.topFeature()
            .filter(feature -> resolved.value().getGenerationSettings().hasFeature(feature))
            .isPresent();
        cir.setReturnValue(result);
    }

    private static boolean the_beyond$isVoronoiFloatingFeature(PlacementContext ctx) {
        Optional<PlacedFeature> top = ctx.topFeature();
        if (top.isEmpty()) return false;
        ResourceLocation id = ctx.getLevel().registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey(top.get());
        return id != null && the_beyond$VORONOI_FLOATING_FEATURES.contains(id);
    }

    private static final ThreadLocal<WorldGenLevel> the_beyond$LAST_LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<BeyondEndBiomeSource> the_beyond$LAST_BEBS = new ThreadLocal<>();

    private static Holder<Biome> the_beyond$lookupVoronoiBiome(WorldGenLevel level, BlockPos pos) {
        BeyondEndBiomeSource bebs = the_beyond$resolveBiomeSource(level);
        return bebs == null ? null : bebs.voronoiCellBiomeIgnoringDensity(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BeyondEndBiomeSource the_beyond$resolveBiomeSource(WorldGenLevel level) {
        if (the_beyond$LAST_LEVEL.get() != level) {
            the_beyond$LAST_LEVEL.set(level);
            ServerLevel serverLevel = level.getLevel();
            BiomeSource source = serverLevel.getChunkSource().getGenerator().getBiomeSource();
            the_beyond$LAST_BEBS.set(source instanceof BeyondEndBiomeSource bebs ? bebs : null);
        }
        return the_beyond$LAST_BEBS.get();
    }
}
