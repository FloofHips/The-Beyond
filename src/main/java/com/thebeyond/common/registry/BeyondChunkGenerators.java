package com.thebeyond.common.registry;

import com.mojang.serialization.MapCodec;
import com.thebeyond.TheBeyond;
import com.thebeyond.common.chunk_generators.BeyondEndChunkGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BeyondChunkGenerators {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENS = DeferredRegister.create(
            BuiltInRegistries.CHUNK_GENERATOR,
            TheBeyond.MODID
    );
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>,
                MapCodec<BeyondEndChunkGenerator>> BEYOND_END_CHUNK_GENERATOR = CHUNK_GENS.register("beyond_end", () -> BeyondEndChunkGenerator.CODEC
    );
}
