package com.thebeyond.common.registry;

import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.features.ObirootFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
public class BeyondFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(BuiltInRegistries.FEATURE, TheBeyond.MODID);

    public static final DeferredHolder<Feature<?>, ObirootFeature> OBIROOT = FEATURES.register("obiroot", () -> new ObirootFeature(NoneFeatureConfiguration.CODEC));

}
