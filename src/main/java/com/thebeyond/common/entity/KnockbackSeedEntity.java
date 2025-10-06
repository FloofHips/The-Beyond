package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class KnockbackSeedEntity extends AbstractSeedEntity {
    private static final EntityDataAccessor<Integer> DATA_FUSE_ID = SynchedEntityData.defineId(KnockbackSeedEntity.class, EntityDataSerializers.INT);

    public KnockbackSeedEntity(Level level, Vec3 pos, @Nullable LivingEntity owner) {
        super(level, pos, owner, null, "");
        this.setFuse(40);
    }

    public KnockbackSeedEntity(EntityType<KnockbackSeedEntity> entityType, Level level) {
        super(entityType, level);
    }

    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putShort("fuse", (short)this.getFuse());

    }

    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setFuse(compound.getShort("fuse"));

    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE_ID, 80);
    }

    protected double getDefaultGravity() {
        return 0.04;
    }
    protected void explode() {
        this.level().explode(this, (DamageSource)null, new SimpleExplosionDamageCalculator(true, false, Optional.of(3F), BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()))
                , this.getX(), this.getY(0.0625), this.getZ(), 2F, false, Level.ExplosionInteraction.TRIGGER, ParticleTypes.GUST_EMITTER_SMALL, ParticleTypes.GUST_EMITTER_LARGE, SoundEvents.WIND_CHARGE_BURST);
    }
    public void setFuse(int life) {
        this.entityData.set(DATA_FUSE_ID, life);
    }

    public int getFuse() {
        return (Integer)this.entityData.get(DATA_FUSE_ID);
    }
    public void tick() {
        super.tick();
        this.handlePortal();
        this.applyGravity();
        this.move(MoverType.SELF, this.getDeltaMovement());

        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            int i = this.getFuse() - 1;
            this.setFuse(i);
            if (i <= 0) {
                this.discard();
                if (!this.level().isClientSide) {
                    this.explode();
                }
            } else {
                this.updateInWaterStateAndDoFluidPushing();
                if (this.level().isClientSide) {
                    this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
                }
            }
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockState blockstate = this.level().getBlockState(result.getBlockPos());
        blockstate.onProjectileHit(this.level(), blockstate, result, this);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
    }
}
