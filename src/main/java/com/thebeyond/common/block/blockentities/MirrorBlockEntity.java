package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MirrorBlockEntity extends BlockEntity {
    /** Client-side registry of loaded mirrors, used by the reflection pass to locate mirror planes. */
    public static final Set<MirrorBlockEntity> LOADED = ConcurrentHashMap.newKeySet();

    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(BeyondBlockEntities.MIRROR.get(), pos, state);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level != null && this.level.isClientSide) {
            LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }
}
