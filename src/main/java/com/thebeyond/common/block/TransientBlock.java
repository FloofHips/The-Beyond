package com.thebeyond.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Marker for blocks that self-mutate to another state after a tick delay. */
public interface TransientBlock {

    /** Server ticks before {@link #getNext} is consulted. {@code Integer.MAX_VALUE} = no
     *  schedule (block transitions on environmental triggers, not time). */
    int getLifetimeTicks(BlockState state);

    /** Replacement state, or {@code null} to remove (caller sets {@code Blocks.AIR}). Must
     *  be side-effect-free — callers may invoke speculatively. */
    BlockState getNext(BlockState current, ServerLevel level, BlockPos pos);
}
