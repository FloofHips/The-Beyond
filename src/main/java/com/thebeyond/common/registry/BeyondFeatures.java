package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.features.*;
import com.thebeyond.common.worldgen.features.compat.AuroraCrystalClusterFeature;
import com.thebeyond.common.worldgen.features.compat.PancakeLakeFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
public class BeyondFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, TheBeyond.MODID);
    public static final DeferredHolder<Feature<?>, ObirootFeature> OBIROOT = FEATURES.register("obiroot", () -> new ObirootFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, EnadrakeVillageFeature> ENADRAKE_VILLAGE = FEATURES.register("enadrake_village", () -> new EnadrakeVillageFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, AuroraciteLayerFeature> AURORACITE_LAYER = FEATURES.register("auroracite_layer", () -> new AuroraciteLayerFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, AuroraciteLayerDTFeature> AURORACITE_LAYER_DT = FEATURES.register("auroracite_layer_dt", () -> new AuroraciteLayerDTFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, PancakeLakeFeature> PANCAKE_LAKE = FEATURES.register("compat/pancake_lake", () -> new PancakeLakeFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, AuroraCrystalClusterFeature> AURORA_CRYSTAL_CLUSTER = FEATURES.register("compat/aurora_crystal_cluster", () -> new AuroraCrystalClusterFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, GaussGeyserFeature> GAUSS_GEYSER = FEATURES.register("gauss_geyser", () -> new GaussGeyserFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, PearlPoolFeature> PEARL_POOL = FEATURES.register("pearl_pool", () -> new PearlPoolFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, PearlPillarFeature> PEARL_PILLAR = FEATURES.register("pearl_pillar", () -> new PearlPillarFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, PerkaStalkFeature> PERKA_STALK = FEATURES.register("perka_stalk", () -> new PerkaStalkFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, BleedingThornsFeature> BLEEDING_THORNS = FEATURES.register("bleeding_thorns", () -> new BleedingThornsFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, BlindingThornsFeature> BLINDING_THORNS = FEATURES.register("blinding_thorns", () -> new BlindingThornsFeature(NoneFeatureConfiguration.CODEC));
}
