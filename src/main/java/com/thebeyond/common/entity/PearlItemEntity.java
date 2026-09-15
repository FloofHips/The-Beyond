package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PearlItemEntity extends ThrowableItemProjectile {
    public PearlItemEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
        noPhysics = false;
    }

    public PearlItemEntity(EntityType<? extends ThrowableItemProjectile> entityType, double x, double y, double z, Level level) {
        super(entityType, x, y, z, level);
    }

    @Override
    protected void onHit(HitResult result) {
        HitResult.Type type = result.getType();

        if (!this.level().isClientSide) {
            Vec3 motion = this.getDeltaMovement();

            if (type == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) result;
                if (motion.length() > 0.1) {
                    double v = (isInFluidType() ? 1 : 0.6);
                    this.setDeltaMovement(motion.multiply(-v, 0.4, -v));
                    entityHit.getEntity().setDeltaMovement(motion.multiply(v, 0.4, v));
                    this.playSound(BeyondSoundEvents.MEMOR_HIT.get(), 0.8F, 1.2F);
                } else {
                    this.setDeltaMovement(motion.multiply(-0.3, 0.1, -0.3));
                }
                this.hasImpulse = true;
            } else if (type == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) result;
                Direction face = blockHit.getDirection();

                if (face == Direction.UP) {
                    float y = Math.abs(motion.y)<0.1 ? 0 : (float) (motion.y * -0.5f);
                    this.setDeltaMovement(motion.x, y, motion.z);
                    this.setOnGround(true);
                    this.playSound(BeyondSoundEvents.MEMOR_HIT.get(), 0.5F, 1.5F);
                } else if (face == Direction.DOWN) {
                    this.setDeltaMovement(motion.x * 0.8, -Math.abs(motion.y) * 0.5, motion.z * 0.8);
                    this.playSound(BeyondSoundEvents.MEMOR_HIT.get(), 0.6F, 1.0F);
                } else {
                    double bounceX = (face.getStepX() != 0) ? -motion.x * (isInFluidType() ? 1 : 0.7) : motion.x;
                    double bounceZ = (face.getStepZ() != 0) ? -motion.z * (isInFluidType() ? 1 : 0.7) : motion.z;
                    this.setDeltaMovement(bounceX, motion.y * 0.5, bounceZ);
                    this.playSound(BeyondSoundEvents.MEMOR_HIT.get(), 0.7F, 1.0F);
                }
                this.hasImpulse = true;
            }
        }
    }

    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    @Override
    public void tick() {
        super.tick();

        if (onGround() && !this.isInFluidType() && this.getDeltaMovement().length() < 0.05f) {
            setDeltaMovement(Vec3.ZERO);
            if (level().getBlockState(getOnPos().below()).isAir()) setOnGround(false);
        }

        if (this.isInFluidType()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.1, 0, 1.1).add(0, 0.02f, 0));
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (target instanceof PearlItemEntity) {
            return true;
        }
        return super.canHitEntity(target);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected Item getDefaultItem() {
        return BeyondItems.PEARL_BEAD.asItem();
    }
}