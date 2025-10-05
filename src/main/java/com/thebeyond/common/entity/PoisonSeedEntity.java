package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.UUID;

public class PoisonSeedEntity extends Projectile {
    @Nullable
    private Entity finalTarget;
    @Nullable
    private UUID targetId;
    private static final EntityDataAccessor<Integer> DATA_JUMP_ID = SynchedEntityData.defineId(PoisonSeedEntity.class, EntityDataSerializers.INT);

    public PoisonSeedEntity(EntityType<PoisonSeedEntity> entityType, Level level) {
        super(entityType, level);
    }
    public PoisonSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner, Entity target) {
        this(BeyondEntityTypes.POISON_SEED.get(), level);

        this.setPos(pos.x, pos.y, pos.z);
        this.xo = pos.x;
        this.yo = pos.y;
        this.zo = pos.z;
        this.setOwner(owner);
        this.finalTarget = target;
    }

    protected double getDefaultGravity() {
        return 0.02;
    }

    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.finalTarget != null) {
            compound.putUUID("Target", this.finalTarget.getUUID());
        }
        compound.putShort("jumps", (short)this.getJumps());
    }

    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID("Target")) {
            this.targetId = compound.getUUID("Target");
        }
        this.setJumps(compound.getShort("jumps"));
    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_JUMP_ID, 4);
    }

    public int getJumps() {
        return this.entityData.get(DATA_JUMP_ID);
    }
    public void setJumps(int jumps) {
        this.entityData.set(DATA_JUMP_ID, jumps);
    }
    
    public void Jump(Entity entity) {
        this.setJumps(getJumps() - 1);
        Vec3 targetPos = entity.position();
        SpawnCloud();
        this.setDeltaMovement((targetPos.x - this.position().x)/20, 0.1 * getJumps(), (targetPos.z - this.position().z)/20);
    }

    public void SpawnCloud() {
        this.playSound(SoundEvents.ALLAY_HURT, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        areaeffectcloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100));

        areaeffectcloud.setRadius(1F);
        areaeffectcloud.setRadiusOnUse(-0.5F);
        areaeffectcloud.setWaitTime(10);
        areaeffectcloud.setDuration(areaeffectcloud.getDuration() / 2);
        areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / (float)areaeffectcloud.getDuration());
        
        this.level().addFreshEntity(areaeffectcloud);
    }
    
    public void tick() {
        super.tick();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (!this.level().isClientSide) {
            if (this.finalTarget == null && this.targetId != null) {
                this.finalTarget = ((ServerLevel)this.level()).getEntity(this.targetId);
                if (this.finalTarget == null) {
                    this.targetId = null;
                }
            }
        }
        if (this.finalTarget != null && this.getJumps() > 0 && this.tickCount % 20 == 0) {
            Jump(finalTarget);
        }

        if (this.onGround()){
            SpawnCloud();
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        SpawnCloud();
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        SpawnCloud();
        discard();
    }
}
