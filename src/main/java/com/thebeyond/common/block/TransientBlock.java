package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Marker for blocks that self-mutate to another state after a tick delay. */
public interface TransientBlock {

    int getLifetimeTicks(BlockState state);

    BlockState getNext(BlockState current, ServerLevel level, BlockPos pos);
}
