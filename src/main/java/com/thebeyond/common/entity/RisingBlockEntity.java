package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.mixin.FallingBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class RisingBlockEntity extends FallingBlockEntity {
    public RisingBlockEntity(EntityType<? extends RisingBlockEntity> entityType, Level level) {
        super(entityType, level);
    }
    private RisingBlockEntity(Level level, double x, double y, double z, BlockState state) {
        this(BeyondEntityTypes.RISING_BLOCK.get(), level);
        this.blocksBuilding = true;
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setStartPos(this.blockPosition());

        ((FallingBlockEntityAccessor) this).setBlockState(state);
    }
    protected double getDefaultGravity() {
        return -0.04;
    }

    @Override
    public void tick() {
        super.tick();
    }


    @Override
    public boolean onGround() {
        if (this.blockPosition().above().getY() > level().getMaxBuildHeight()) return true;
        if (!level().getBlockState(this.blockPosition().above()).isAir()) return true;
        return super.onGround();
    }

    public static RisingBlockEntity rise(Level level, BlockPos pos, BlockState blockState) {
        RisingBlockEntity risingBlockEntity = new RisingBlockEntity(level, (double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5, blockState.hasProperty(BlockStateProperties.WATERLOGGED) ? (BlockState)blockState.setValue(BlockStateProperties.WATERLOGGED, false) : blockState);
        level.setBlock(pos, blockState.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(risingBlockEntity);
        return risingBlockEntity;
    }
}
