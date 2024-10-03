package com.thebeyond.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public interface IMagneticReceiver {
    void receiveSignal(BlockPos pos, BlockState state, Level level, @Nullable BlockState senderState);
}
