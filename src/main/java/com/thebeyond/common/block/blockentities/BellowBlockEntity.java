package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.block.BellowBlock;
import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Drives the Bellow: the server ticker polls redstone, syncs {@code STRENGTH} and pushes entities; the client ticker
 * emits the smoke jet locally. Stateless — the gust is purely transient.
 */
public class BellowBlockEntity extends BlockEntity {
    public BellowBlockEntity(BlockPos pos, BlockState state) {
        super(BeyondBlockEntities.BELLOW.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BellowBlockEntity be) {
        int signal = BellowBlock.inputSignal(level, pos, state);
        int strength = BellowBlock.computeLevel(level, pos, state, signal);
        if (strength != state.getValue(BellowBlock.STRENGTH)) {
            level.setBlock(pos, state.setValue(BellowBlock.STRENGTH, strength), Block.UPDATE_CLIENTS);
        }
        if (strength > 0 && level instanceof ServerLevel serverLevel) {
            BellowBlock.serverPush(serverLevel, pos, state, signal, strength);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, BellowBlockEntity be) {
        int strength = state.getValue(BellowBlock.STRENGTH);
        if (strength > 0) {
            BellowBlock.clientJet(level, pos, state, strength);
        }
    }
}
