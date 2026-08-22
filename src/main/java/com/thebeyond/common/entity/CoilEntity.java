package com.thebeyond.common.entity;

import com.thebeyond.client.particle.SmokeColorTransitionOptions;
import com.thebeyond.common.registry.BeyondBlocks;
import com.thebeyond.common.registry.BeyondItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CoilEntity extends ThrowableItemProjectile {

    int counter = 0;
    boolean startBuilding = false;
    Direction direction = Direction.UP;
    BlockPos pos = null;

    public CoilEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level, Direction dir, BlockPos pos) {
        super(entityType, level);
        this.direction = dir;
        this.counter = 0;
        this.pos = pos;
    }
    public CoilEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (pos == null) pos = result.getBlockPos();
        startBuilding = true;
    }

    @Override
    public void tick() {
        if (counter >= 32 || counter == -1) this.discard();
        if (this.tickCount > 200) this.discard();
        if (this.tickCount%3==0 && startBuilding && counter < 32) {
            placeVerterbrae();
            setDeltaMovement(Vec3.ZERO);
        }
        super.tick();
    }

    private void placeVerterbrae() {
        BlockPos offset = pos.offset(direction.getStepX() * counter, direction.getStepY() * counter, direction.getStepZ() * counter);

        if (level().isEmptyBlock(offset)) {
            level().playSound(null, offset, SoundEvents.BONE_BLOCK_PLACE, SoundSource.BLOCKS, 1,1);
            level().setBlock(offset, BeyondBlocks.COIL_VERTEBRAE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, direction.getAxis()),3);
            counter++;
        } else {
            startBuilding = false;
            counter = -1;
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0f;
    }

    @Override
    protected Item getDefaultItem() {
        return BeyondItems.COILED_STALK.asItem();
    }
}
