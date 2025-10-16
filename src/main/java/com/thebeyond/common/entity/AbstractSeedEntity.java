package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class AbstractSeedEntity extends Projectile {
    @Nullable
    protected Entity finalTarget;
    @Nullable
    protected UUID targetId;

    protected AbstractSeedEntity(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }
    public AbstractSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner, Entity target) {
        this(BeyondEntityTypes.UNSTABLE_SEED.get(), level);

        this.setPos(pos.x, pos.y, pos.z);
        this.xo = pos.x;
        this.yo = pos.y;
        this.zo = pos.z;
        this.setOwner(owner);
        this.finalTarget = target;
    }
    public AbstractSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner, Entity target, String s) {
        this(BeyondEntityTypes.KNOCKBACK_SEED.get(), level);

        this.setPos(pos.x, pos.y, pos.z);
        this.xo = pos.x;
        this.yo = pos.y;
        this.zo = pos.z;
        this.setOwner(owner);
        this.finalTarget = target;
    }
    public AbstractSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner, Entity target, int i) {
        this(BeyondEntityTypes.POISON_SEED.get(), level);

        this.setPos(pos.x, pos.y, pos.z);
        this.xo = pos.x;
        this.yo = pos.y;
        this.zo = pos.z;
        this.setOwner(owner);
        this.finalTarget = target;
    }
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        if (this.finalTarget != null) {
            compound.putUUID("Target", this.finalTarget.getUUID());
        }
    }

    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID("Target")) {
            this.targetId = compound.getUUID("Target");
        }
    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.finalTarget == null && this.targetId != null) {
                this.finalTarget = ((ServerLevel)this.level()).getEntity(this.targetId);
                if (this.finalTarget == null) {
                    this.targetId = null;
                }
            }
        } else this.level().addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.15, this.getZ(), 0.0, 0.0, 0.0);

        HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitresult.getType() != HitResult.Type.MISS) {
            this.onHit(hitresult);
        }
    }

    protected boolean canHitEntity(Entity target) {
        if (target instanceof UnstableSeedEntity || target instanceof PoisonSeedEntity || target instanceof KnockbackSeedEntity || target instanceof EnatiousTotemEntity || target instanceof EnadrakeEntity) {
            return false;
        } else {
            return super.canHitEntity(target);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.playSound(SoundEvents.SHROOMLIGHT_BREAK, 2.0F, (this.random.nextFloat()) * 0.2F + 1.0F);
        discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        this.playSound(SoundEvents.SHROOMLIGHT_BREAK, 2.0F, (this.random.nextFloat()) * 0.2F + 1.0F);
        discard();
    }
}
