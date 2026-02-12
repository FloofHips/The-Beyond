package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

public class UnstableSeedEntity extends AbstractSeedEntity {

    public UnstableSeedEntity(EntityType<UnstableSeedEntity> entityType, Level level) {
        super(entityType, level);
    }
    public UnstableSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner, Entity target) {
        super(level, pos, owner, target);
    }

    protected double getDefaultGravity() {
        return 0.006;
    }

    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
    }

    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void tick() {
        super.tick();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.finalTarget != null) {
            if (this.tickCount % 2 == 0)
                this.playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, (this.random.nextFloat()) * 1.5F + 0.5f);
            this.setDeltaMovement((finalTarget.position().x - this.position().x)/10, this.getDeltaMovement().y, (finalTarget.position().z - this.position().z)/10);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        if (entity instanceof LivingEntity){
            DamageSource damagesource = this.damageSources().mobProjectile(this, (LivingEntity) getOwner());
            entity.hurt(damagesource,1);
            entity.hurtMarked = true;
            this.playSound(SoundEvents.SHROOMLIGHT_BREAK, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            ((LivingEntity) entity).addEffect(new MobEffectInstance(BeyondEffects.UNSTABLE, 200));
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ColorUtils.voidOptions, this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(10, 20), 0.2,0.2,0.2,0.05);
            }
        } else {
            this.playSound(SoundEvents.LAVA_EXTINGUISH, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ColorUtils.pixelWhiteOptions, this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(30, 50), 1,1,1,0.1);
            }
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        this.playSound(SoundEvents.LAVA_EXTINGUISH, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ColorUtils.pixelWhiteOptions, this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(30, 50), 1,1,1,0.1);
        }
    }
}
