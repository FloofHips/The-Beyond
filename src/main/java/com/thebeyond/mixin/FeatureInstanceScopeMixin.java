package com.thebeyond.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thebeyond.api.worldgen.FeatureGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ConfiguredFeature.class)
public abstract class FeatureInstanceScopeMixin {

    @WrapMethod(method = "place")
    private boolean the_beyond$scopeFeatureInstance(WorldGenLevel reader, ChunkGenerator generator,
            RandomSource random, BlockPos pos, Operation<Boolean> original) {
        FeatureGuard.enterFeature();
        try {
            return original.call(reader, generator, random, pos);
        } finally {
            FeatureGuard.exitFeature();
        }
    }
}
