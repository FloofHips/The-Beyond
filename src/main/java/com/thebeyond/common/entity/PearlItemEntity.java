package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondItems;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;

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
//
            if (type == HitResult.Type.ENTITY) {
                EntityHitResult entityHit = (EntityHitResult) result;
                if (motion.length() > 0.1) {
                    double v = (isInFluidType() ? 1 : 0.6);
                    this.setDeltaMovement(motion.multiply(-v, 0.4, -v));
                    if (entityHit.getEntity().isPushable()) entityHit.getEntity().setDeltaMovement(motion.multiply(v, 0.4, v));
                    this.playSound(BeyondSoundEvents.MEMOR_HIT.get(), 0.8F, 1.2F);
                } else {
                    this.setDeltaMovement(motion.multiply(-0.3, 0.1, -0.3));
                }
                this.hasImpulse = true;
            } else if (this.getDeltaMovement().length() > 0.1 && type == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) result;
                Direction face = blockHit.getDirection();
                if (level().getBlockState(blockHit.getBlockPos()).is(Blocks.WATER)) return;
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
        this.baseTick();

        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        Vec3 vec3 = this.getDeltaMovement();
        FluidType fluidType = this.getMaxHeightFluidType();

        if (!fluidType.isAir() && !fluidType.isVanilla() && this.getFluidTypeHeight(fluidType) > (double)0.1F) {
            this.setUnderliquidMovement(0.99F);
        } else if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > (double)0.1F) {
            this.setUnderliquidMovement(1);
            this.setDeltaMovement(getDeltaMovement().multiply(1,0,1));
        } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > (double)0.1F) {
            this.setUnderliquidMovement(0.95F);
        } else {
            this.applyGravity();
        }

        if (this.level().isClientSide) {
            this.noPhysics = false;
        } else {
            this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7));
            if (this.noPhysics) {
                this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / (double)2.0F, this.getZ());
            }
        }

        if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > (double)1.0E-5F || (this.tickCount + this.getId()) % 4 == 0) {
            this.move(MoverType.SELF, this.getDeltaMovement());
            float f = 0.98F;
            if (this.onGround()) {
                BlockPos groundPos = this.getBlockPosBelowThatAffectsMyMovement();
                f = this.level().getBlockState(groundPos).getFriction(this.level(), groundPos, this) * 1.5F;
            }

            this.setDeltaMovement(this.getDeltaMovement().multiply((double)f, 0.98, (double)f));
            if (this.onGround()) {
                Vec3 vec31 = this.getDeltaMovement();
                if (vec31.y < (double)0.0F) {
                    this.setDeltaMovement(vec31.multiply((double)1.0F, (double)-0.5F, (double)1.0F));
                }
            }
        }

        this.hasImpulse |= this.updateInWaterStateAndDoFluidPushing();
        if (!this.level().isClientSide) {
            double d0 = this.getDeltaMovement().subtract(vec3).lengthSqr();
            if (d0 > 0.01) {
                this.hasImpulse = true;
            }
        }

        if (this.getDeltaMovement().length() < 0.01 && this.isInWater() && this.getFluidHeight(FluidTags.WATER) > (double)0.1F) {
            this.setDeltaMovement(getDeltaMovement().add(0.1 - random.nextFloat()*0.2, 0, 0.1 - random.nextFloat()*0.2));
        }

        HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitresult.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hitresult)) {
            this.hitTargetOrDeflectSelf(hitresult);
        }

        this.checkInsideBlocks();

    }

    private void setUnderliquidMovement(float horizontalMovement) {
        Vec3 vec3 = this.getDeltaMovement();
        this.setDeltaMovement(vec3.x * horizontalMovement, vec3.y + (double)(vec3.y < (double)0.06F ? 5.0E-4F : 0.0F), vec3.z * horizontalMovement);
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

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (tickCount < 20) return super.interact(player, hand);
        ItemStack pearl = this.getItem();
        player.addItem(pearl);
        this.discard();
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return getItem();
    }

    @Override
    public boolean mayInteract(Level level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity()==null) return super.hurt(source, amount);
        Vec3 push = source.getEntity().position().subtract(this.position()).normalize().scale(-amount*0.5);
        setDeltaMovement(push);
        return super.hurt(source, amount);
    }

    @Override
    public float getPickRadius() {
        return 0.1f;
    }
}