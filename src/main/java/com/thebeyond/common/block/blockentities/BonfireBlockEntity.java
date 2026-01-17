package com.thebeyond.common.block.blockentities;

import com.thebeyond.common.registry.BeyondBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BonfireBlockEntity extends BlockEntity {
    public BonfireBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
    public BonfireBlockEntity(BlockPos pos, BlockState blockState) {
        this(BeyondBlockEntities.BONFIRE.get(), pos, blockState);
    }
}
