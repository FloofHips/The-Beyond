package com.thebeyond.mixin;

import com.thebeyond.common.worldgen.BeyondStructureArbiter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public abstract class StructureStartPlacementMixin {

    @Inject(method = "placeInChunk", at = @At("HEAD"), cancellable = true)
    private void the_beyond$skipVetoedStart(WorldGenLevel level, StructureManager structureManager,
            ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, CallbackInfo ci) {
        if (BeyondStructureArbiter.isVetoed((StructureStart) (Object) this)) ci.cancel();
    }
}
