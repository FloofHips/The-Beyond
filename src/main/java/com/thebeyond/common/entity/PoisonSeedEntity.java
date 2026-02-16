package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.util.ColorUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.UUID;

public class PoisonSeedEntity extends AbstractSeedEntity {
    private static final EntityDataAccessor<Integer> DATA_JUMP_ID = SynchedEntityData.defineId(PoisonSeedEntity.class, EntityDataSerializers.INT);

    public PoisonSeedEntity(EntityType<PoisonSeedEntity> entityType, Level level) {
        super(entityType, level);
    }
    public PoisonSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner, Entity target) {
        super(level, pos, owner, target, 1);
    }

    protected double getDefaultGravity() {
        return 0.02;
    }


    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putShort("jumps", (short)this.getJumps());
    }

    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
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
        areaeffectcloud.addEffect(new MobEffectInstance(MobEffects.POISON, 300));

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

        if (this.finalTarget != null && this.getJumps() > 0 && this.tickCount % 10 == 0) {
            Jump(finalTarget);
        }
    }

    @Override
    protected ParticleOptions getParticleType() {
        return ColorUtils.pixelPoisonOptions;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ColorUtils.poisonOptions, this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(10, 20), 0.02,0.2,0.02,0.04);
        }

        SpawnCloud();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ColorUtils.poisonOptions, this.getX() + 0.5, this.getY(), this.getZ() + 0.5, level().random.nextInt(10, 20), 0.02,0.2,0.02,0.04);
        }

        SpawnCloud();
    }
}
