package com.thebeyond.common.registry;

import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.worldgen.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondChunkGenerators {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENS = DeferredRegister.create(
            BuiltInRegistries.CHUNK_GENERATOR,
            TheBeyond.MODID
    );
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(
            BuiltInRegistries.BIOME_SOURCE,
            TheBeyond.MODID
    );
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>,
                MapCodec<BeyondEndChunkGenerator>> BEYOND_END_CHUNK_GENERATOR = CHUNK_GENS.register("the_end", () -> BeyondEndChunkGenerator.CODEC
    );
    public static final DeferredHolder<MapCodec<? extends BiomeSource>,
            MapCodec<BeyondEndBiomeSource>> BEYOND_END_BIOME_SOURCE = BIOME_SOURCES.register("the_end", () -> BeyondEndBiomeSource.CODEC
    );
}
