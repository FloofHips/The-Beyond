package com.thebeyond.common.entity;

import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class EnatiousTotemEntity extends Mob implements Enemy {

    private static final int MAX_COOLDOWN = 80;
    private static final EntityDataAccessor<Integer> DATA_COOLDOWN = SynchedEntityData.defineId(EnatiousTotemEntity.class, EntityDataSerializers.INT);

    public EnatiousTotemEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        compound.putShort("cooldown", (short)this.getCooldown());
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        this.setCooldown(compound.getShort("cooldown"));
    }

    public int getCooldown() {
        return this.entityData.get(DATA_COOLDOWN);
    }
    public void setCooldown(int cooldown) {
        this.entityData.set(DATA_COOLDOWN, cooldown);
    }
    public int getMaxCooldown() {
        return MAX_COOLDOWN;
    }
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COOLDOWN, MAX_COOLDOWN);
    }
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(3, new EnatiousTotemAttackGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Vec3 vector) {
        super.push(vector);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override
    public void tick() {
        super.tick();

        if (getCooldown() < MAX_COOLDOWN){
            setCooldown(getCooldown() + 1);
            this.getLookControl().setLookAt(this.getLookAngle());
            this.setYRot(this.yRotO);
            this.setYBodyRot(this.yBodyRotO);
        }

        if (getCooldown() < MAX_COOLDOWN / 2)
            this.playSound(SoundEvents.WOOD_BREAK, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);

        if (getCooldown() == MAX_COOLDOWN / 2)
            this.playSound(SoundEvents.BREEZE_DEFLECT, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);

        if (getCooldown() == MAX_COOLDOWN - 1)
            this.playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
    }


    private void shoot(LivingEntity target, int i) {
        this.playSound(BeyondSoundEvents.END_STONE_BREAK.get(), 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        this.lookAt(target, 10,10);

        //top
        if (i == 1) {
            KnockbackSeedEntity knockback = new KnockbackSeedEntity(this.level(), this.position().add(0,2.5,0), this);
            if(this.level().addFreshEntity(knockback)){
                knockback.setDeltaMovement((target.position().x - knockback.position().x)/20, 0.2, (target.position().z - knockback.position().z)/20);
            }
        }

        //mid
        if (i == 2) {
            PoisonSeedEntity poison = new PoisonSeedEntity(this.level(), this.position().add(0,1.5,0), this, target);
            if(this.level().addFreshEntity(poison)){
                poison.setDeltaMovement((target.position().x - poison.position().x)/20, 0.5, (target.position().z - poison.position().z)/20);
            }
        }

        //bottom

        if (i == 3) {
            UnstableSeedEntity unstable = new UnstableSeedEntity(this.level(), this.position().add(0,0.5,0), this, target);
            if(this.level().addFreshEntity(unstable)){
                unstable.setDeltaMovement((target.position().x - unstable.position().x)/20, 0.5, (target.position().z - unstable.position().z)/20);
            }
        }

        this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        this.setDeltaMovement(0, 0.1, 0);
    }

    class EnatiousTotemAttackGoal extends Goal {

        private EnatiousTotemEntity mob;
        private Entity target;
        private int counter;
        public EnatiousTotemAttackGoal(EnatiousTotemEntity rangedAttackMob) {
            this.mob = rangedAttackMob;
            this.target = null;
        }
        @Override
        public boolean canUse() {
            if (mob.getCooldown() < MAX_COOLDOWN){
                return false;
            }

            LivingEntity livingentity = this.mob.getTarget();
            if (livingentity != null && livingentity.isAlive()) {
                this.target = livingentity;
                return true;
            } else {
                return false;
            }
        }
        public boolean canContinueToUse() {
            return this.canUse();
        }
        @Override
        public void start() {
            super.start();
            counter = 0;
            this.mob.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (this.mob.random.nextFloat()) * 2);
            this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
        }

        @Override
        public void tick() {
            super.tick();

            counter++;
            if (counter > 25) stop();
            this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            if (counter==10){
                this.mob.playSound(SoundEvents.ITEM_BREAK, 2.0F, 0.5f);
                this.mob.shoot((LivingEntity) this.target, 1);
            }
            if (counter==15){
                this.mob.playSound(SoundEvents.ITEM_BREAK, 2.0F, 1);
                this.mob.shoot((LivingEntity) this.target, 2);
            }
            if (counter==20){
                this.mob.playSound(SoundEvents.ITEM_BREAK, 2.0F, 2);
                this.mob.shoot((LivingEntity) this.target, 3);
            }
        }

        @Override
        public void stop() {
            super.stop();
            this.mob.setCooldown(0);
            this.counter = 0;
            this.mob.playSound(SoundEvents.CONDUIT_DEACTIVATE, 2.0F, (this.mob.random.nextFloat()) * 2);
        }
    }
}
