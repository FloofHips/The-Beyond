package com.thebeyond.common.entity;

import com.thebeyond.client.event.ModClientEvents;
import com.thebeyond.common.entity.util.livingblock.LivingBlock;
import com.thebeyond.common.registry.BeyondEffects;
import com.thebeyond.common.registry.BeyondEntityTypes;
import com.thebeyond.common.registry.BeyondSoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SiblingEntity extends PathfinderMob {
    protected static final EntityDataAccessor<Boolean> DATA_BIRTH = SynchedEntityData.defineId(SiblingEntity.class, EntityDataSerializers.BOOLEAN);
    public static final byte BIRTH = 67;
    public final AnimationState birthAnimationState = new AnimationState();
    public SiblingEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
    }

    public boolean getBirth() {return this.entityData.get(DATA_BIRTH);}
    public void setBirth(boolean birth) {this.entityData.set(DATA_BIRTH, birth);}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_BIRTH, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (getBirth()) {
            navigation.stop();
            setPos(this.position().multiply(0,1,0).add(xo, 0, zo));
            setXRot(xRotO);
            setYRot(yRotO);
        }

        if (tickCount%10==0 && getTarget()!=null) {
            LivingEntity target = getTarget();
            if (target instanceof Player player) {
                if (player.isCrouching() && !isCrouching()) this.setPose(Pose.CROUCHING);
                if (!player.isCrouching() && isCrouching()) this.setPose(Pose.STANDING);
            }
        }
        if (getBirth() && tickCount == 50) setBirth(false);
    }

    public void registerGoals() {
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(2, new FollowMobGoal(this,1, 1, 16));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, (double)1.0F));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, BaubleEntity.class, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, TrinketEntity.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, (double)35.0F).add(Attributes.MOVEMENT_SPEED, (double)0.23F).add(Attributes.ATTACK_DAMAGE, (double)3.0F).add(Attributes.ARMOR, (double)2.0F);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (this.level().isClientSide) {
            if (id == BIRTH) {
                this.birthAnimationState.start(this.tickCount);
                return;
            }
        }
        super.handleEntityEvent(id);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null && source.getEntity() instanceof LivingEntity livingEntity) {
            if (!livingEntity.hasEffect(BeyondEffects.EMPATHY)) livingEntity.addEffect(new MobEffectInstance(BeyondEffects.EMPATHY, 600,1));
            if (livingEntity instanceof Player player && player.level().isClientSide) {
                ModClientEvents.empathy = 1;
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(), BeyondSoundEvents.ABYSSAL_NOMAD_NOD.get(), SoundSource.NEUTRAL);
            }
        }

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource damageSource) {
        byte[][] SILHOUETTES = {
                {8, 12, 4},

                {4, 12, 4},
                {4, 12, 4},
                {4, 12, 4},
                {4, 12, 4},

                {8, 8, 8}
        };
        for (byte[] bytes : SILHOUETTES) {
            BaubleEntity bauble = new BaubleEntity(BeyondEntityTypes.BAUBLE.get(), level());
            bauble.setDepth(bytes[0]);
            bauble.setWidth(bytes[2]);
            bauble.setHeight(bytes[1]);
            bauble.setPos(this.position());
            bauble.setDeltaMovement(new Vec3(0.1f - random.nextFloat()*0.2, random.nextFloat()*0.2f, 0.1f - random.nextFloat()*0.2));
            level().addFreshEntity(bauble);
        }

        super.die(damageSource);
    }

    public boolean doHurtTarget(Entity entity) {
        return false;
    }
}
