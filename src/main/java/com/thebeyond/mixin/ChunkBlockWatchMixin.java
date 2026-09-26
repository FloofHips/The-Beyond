package com.thebeyond.mixin;

import com.thebeyond.common.debug.BlockWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ProtoChunk.class, LevelChunk.class})
public abstract class ChunkBlockWatchMixin {

    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void the_beyond$watch(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        if (BlockWatch.ACTIVE) BlockWatch.note((ChunkAccess) (Object) this, pos, state);
    }
}
