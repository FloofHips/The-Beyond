package com.thebeyond.common.entity;

import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
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

public class EnatiousTotemEntity extends Mob implements RangedAttackMob, Enemy {
    public EnatiousTotemEntity(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(3, new RangedAttackGoal(this, 1.25, 40, 20.0F));

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
    public void performRangedAttack(LivingEntity livingEntity, float v) {
        this.shoot(livingEntity);
    }

    private void shoot(LivingEntity target) {
        this.lookAt(target, 10,10);
        //top
        KnockbackSeedEntity knockback = new KnockbackSeedEntity(this.level(), this.position().add(0,2.5,0), this);
        if(this.level().addFreshEntity(knockback)){
            knockback.setDeltaMovement(this.getLookAngle().add(0.1,0.2,0.1));
        }
        //mid
        PoisonSeedEntity poison = new PoisonSeedEntity(this.level(), this.position().add(0,1.5,0), this, target);
        if(this.level().addFreshEntity(poison)){
            poison.setDeltaMovement((target.position().x - poison.position().x)/20, 0.5, (target.position().z - poison.position().z)/20);
        }
        //bottom
        //this.level().addFreshEntity(new ShulkerBullet(this.level(), this, target, Direction.fromYRot(this.getYRot()).getAxis()));

        this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }
}
