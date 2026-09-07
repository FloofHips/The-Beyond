package com.thebeyond.common.registry;

import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.BeyondEndBiomeSource;
import com.thebeyond.common.worldgen.BeyondEndChunkGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public class BeyondBiomes {
    private static final List<ResourceKey<Biome>> ALL = new ArrayList<>();

    public static final ResourceKey<Biome> ATTRACTA_EXPANSE = register("attracta_expanse");
    public static final ResourceKey<Biome> PEER_LANDS = register("peer_lands");
    public static final ResourceKey<Biome> THE_PATHS = register("the_paths");
    public static final ResourceKey<Biome> TRUE_VOID = register("true_void");
    public static final ResourceKey<Biome> LUSTROUS_ECHOES = register("lustrous_echoes");
    public static final ResourceKey<Biome> FUMAROLE_UPLANDS = register("fumarole_uplands");
    public static final ResourceKey<Biome> CHESTRAL_HOLLOWS = register("chestral_hollows");

    public static List<ResourceKey<Biome>> all() {
        return List.copyOf(ALL);
    }

    private static ResourceKey<Biome> register(String string) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(TheBeyond.MODID, string));
        ALL.add(key);
        return key;
    }
}
