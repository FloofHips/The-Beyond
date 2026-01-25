package com.thebeyond.common.registry;

import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.biome_modifiers.FogColorModifier;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class BeyondBiomeModifiers {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, TheBeyond.MODID);

    static {
        BIOME_MODIFIERS.register("fog_color", () -> FogColorModifier.CODEC);
    }
}
